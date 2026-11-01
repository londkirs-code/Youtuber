package com.example.network

import android.util.Log
import com.example.data.model.SearchResultItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class StreamInfo(
    val videoId: String,
    val title: String,
    val channel: String,
    val durationMs: Long,
    val durationText: String,
    val thumbnailUrl: String,
    val streamUrl: String,
    val isLive: Boolean = false,
    val approxSizeBytes: Long = 0L,
    val audioBitrateKbps: Int = 160
)

object YouTubeEngine {
    private const val TAG = "YouTubeEngine"

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private val VIDEO_ID_PATTERNS = listOf(
        Pattern.compile("(?:v=|/v/|youtu\\.be/|/embed/|/shorts/|/live/)([a-zA-Z0-9_-]{11})"),
        Pattern.compile("^([a-zA-Z0-9_-]{11})$")
    )

    fun extractVideoId(input: String): String? {
        val trimmed = input.trim()
        for (pattern in VIDEO_ID_PATTERNS) {
            val matcher = pattern.matcher(trimmed)
            if (matcher.find()) {
                return matcher.group(1)
            }
        }
        return null
    }

    fun isPlaylistUrl(input: String): Boolean {
        return input.contains("list=")
    }

    fun extractPlaylistId(input: String): String? {
        val pattern = Pattern.compile("list=([a-zA-Z0-9_-]+)")
        val matcher = pattern.matcher(input)
        return if (matcher.find()) matcher.group(1) else null
    }

    /**
     * Fetches search suggestions matching YouTube autocomplete.
     */
    suspend fun getSearchSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://suggestqueries.google.com/complete/search?client=firefox&ds=yt&q=$encoded"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val jsonArray = JSONArray(body)
                val suggestionsArray = jsonArray.optJSONArray(1) ?: return@withContext emptyList()
                val results = mutableListOf<String>()
                for (i in 0 until suggestionsArray.length()) {
                    results.add(suggestionsArray.getString(i))
                }
                results
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching suggestions", e)
            emptyList()
        }
    }

    /**
     * Executes YouTube search returning rich video cards.
     */
    suspend fun searchVideos(query: String): List<SearchResultItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        // If the query is directly a YouTube URL or video ID, resolve it directly
        val directId = extractVideoId(trimmed)
        if (directId != null) {
            val basic = resolveBasicInfo(directId)
            if (basic != null) {
                return@withContext listOf(basic)
            }
        }

        // Search via YouTube Innertube public endpoint
        try {
            val endpoint = "https://www.youtube.com/youtubei/v1/search?prettyPrint=false"
            val jsonPayload = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB")
                        put("clientVersion", "2.20230526.09.00")
                        put("hl", "iw")
                        put("gl", "IL")
                    })
                })
                put("query", trimmed)
            }

            val request = Request.Builder()
                .url(endpoint)
                .post(jsonPayload.toString().toRequestBody("application/json".toMediaType()))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/113.0.0.0 Safari/537.36")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    val items = parseSearchResponse(bodyString)
                    if (items.isNotEmpty()) {
                        return@withContext items
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Innertube search failed, falling back", e)
        }

        // Secondary fallback search via Invidious public instance
        try {
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            val invidiousUrl = "https://invidious.nerdvpn.de/api/v1/search?q=$encoded&type=video"
            val request = Request.Builder()
                .url(invidiousUrl)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    val jsonArray = JSONArray(bodyString)
                    val list = mutableListOf<SearchResultItem>()
                    for (i in 0 until jsonArray.length().coerceAtMost(25)) {
                        val obj = jsonArray.getJSONObject(i)
                        val id = obj.optString("videoId")
                        if (id.isNotBlank()) {
                            val title = obj.optString("title", "ללא כותרת")
                            val author = obj.optString("author", "ערוץ YouTube")
                            val lengthSeconds = obj.optLong("lengthSeconds", 0L)
                            val isLive = obj.optBoolean("liveNow", false)
                            val durationText = if (isLive) "LIVE" else formatDuration(lengthSeconds * 1000L)
                            val views = obj.optLong("viewCount", 0L)
                            val viewsFormatted = if (views > 0) formatViews(views) else "צפיות"
                            val publishedText = obj.optString("publishedText", "לאחרונה")
                            val thumb = "https://i.ytimg.com/vi/$id/hqdefault.jpg"

                            list.add(
                                SearchResultItem(
                                    id = id,
                                    title = title,
                                    channelTitle = author,
                                    durationText = durationText,
                                    durationMs = lengthSeconds * 1000L,
                                    thumbnailUrl = thumb,
                                    viewsText = viewsFormatted,
                                    uploadedText = publishedText,
                                    isLive = isLive,
                                    channelId = obj.optString("authorId", "")
                                )
                            )
                        }
                    }
                    if (list.isNotEmpty()) return@withContext list
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Secondary search fallback failed", e)
        }

        emptyList()
    }

    private fun parseSearchResponse(jsonString: String): List<SearchResultItem> {
        val list = mutableListOf<SearchResultItem>()
        try {
            val root = JSONObject(jsonString)
            val contents = root.optJSONObject("contents")
                ?.optJSONObject("twoColumnSearchResultsRenderer")
                ?.optJSONObject("primaryContents")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents") ?: return list

            for (i in 0 until contents.length()) {
                val itemSection = contents.optJSONObject(i)?.optJSONObject("itemSectionRenderer")
                val sectionContents = itemSection?.optJSONArray("contents") ?: continue

                for (j in 0 until sectionContents.length()) {
                    val videoRenderer = sectionContents.optJSONObject(j)?.optJSONObject("videoRenderer") ?: continue
                    val videoId = videoRenderer.optString("videoId")
                    if (videoId.isBlank()) continue

                    val title = videoRenderer.optJSONObject("title")
                        ?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                        ?: videoRenderer.optJSONObject("title")?.optString("simpleText", "סרטון") ?: "סרטון"

                    val channel = videoRenderer.optJSONObject("ownerText")
                        ?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                        ?: videoRenderer.optJSONObject("shortBylineText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                        ?: "ערוץ יוטיוב"

                    val channelId = videoRenderer.optJSONObject("ownerText")
                        ?.optJSONArray("runs")?.optJSONObject(0)
                        ?.optJSONObject("navigationEndpoint")
                        ?.optJSONObject("browseEndpoint")
                        ?.optString("browseId").orEmpty()

                    val isLive = videoRenderer.optJSONArray("badges")?.let { badges ->
                        var live = false
                        for (b in 0 until badges.length()) {
                            val label = badges.optJSONObject(b)?.optJSONObject("metadataBadgeRenderer")?.optString("label") ?: ""
                            if (label.contains("LIVE", ignoreCase = true) || label.contains("חי")) {
                                live = true
                            }
                        }
                        live
                    } ?: false

                    val durationText = if (isLive) "LIVE" else {
                        videoRenderer.optJSONObject("lengthText")?.optString("simpleText") ?: "00:00"
                    }
                    val durationMs = parseDurationTextToMs(durationText)

                    val viewsText = videoRenderer.optJSONObject("viewCountText")?.optString("simpleText")
                        ?: videoRenderer.optJSONObject("shortViewCountText")?.optString("simpleText") ?: ""

                    val publishedText = videoRenderer.optJSONObject("publishedTimeText")?.optString("simpleText") ?: ""
                    val thumb = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

                    list.add(
                        SearchResultItem(
                            id = videoId,
                            title = title,
                            channelTitle = channel,
                            durationText = durationText,
                            durationMs = durationMs,
                            thumbnailUrl = thumb,
                            viewsText = viewsText,
                            uploadedText = publishedText,
                            isLive = isLive,
                            channelId = channelId
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing innertube search json", e)
        }
        return list
    }

    /**
     * Instant metadata for a pasted link. Uses YouTube oEmbed (one tiny request, no tokens needed),
     * so the card appears immediately. Duration is unknown at this stage.
     */
    suspend fun resolveBasicInfo(videoId: String): SearchResultItem? = withContext(Dispatchers.IO) {
        try {
            val url = "https://www.youtube.com/oembed?format=json&url=" +
                URLEncoder.encode("https://www.youtube.com/watch?v=$videoId", "UTF-8")
            val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val json = JSONObject(response.body?.string() ?: return@withContext null)
                SearchResultItem(
                    id = videoId,
                    title = json.optString("title", "סרטון"),
                    channelTitle = json.optString("author_name", "ערוץ יוטיוב"),
                    durationText = "--:--",
                    durationMs = 0L,
                    thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                    viewsText = "קישור ישיר",
                    uploadedText = "יוטיוב",
                    isLive = false
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "oEmbed failed", e)
            null
        }
    }

    data class FeedEntry(val videoId: String, val title: String, val published: String)

    private val CHANNEL_ID_REGEXES = listOf(
        Pattern.compile("/channel/(UC[0-9A-Za-z_-]{22})"),
        Pattern.compile("\"externalId\":\"(UC[0-9A-Za-z_-]{22})\""),
        Pattern.compile("\"channelId\":\"(UC[0-9A-Za-z_-]{22})\""),
        Pattern.compile("channel_id=(UC[0-9A-Za-z_-]{22})")
    )

    /** video -> oEmbed author_url -> channel page -> channelId (UC...). */
    suspend fun resolveChannelIdFromVideo(videoId: String): String? = withContext(Dispatchers.IO) {
        try {
            val oembed = "https://www.youtube.com/oembed?format=json&url=" +
                URLEncoder.encode("https://www.youtube.com/watch?v=$videoId", "UTF-8")
            val authorUrl = client.newCall(
                Request.Builder().url(oembed).header("User-Agent", "Mozilla/5.0").build()
            ).execute().use { r ->
                if (!r.isSuccessful) return@use null
                JSONObject(r.body?.string() ?: return@use null).optString("author_url", "")
            }
            if (authorUrl.isNullOrBlank()) return@withContext null

            // Already a /channel/UC... url
            for (p in CHANNEL_ID_REGEXES.take(1)) {
                val m = p.matcher(authorUrl)
                if (m.find()) return@withContext m.group(1)
            }

            // @handle url -> read the page
            val html = client.newCall(
                Request.Builder().url(authorUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0 Safari/537.36")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Cookie", "CONSENT=YES+1; SOCS=CAI")
                    .build()
            ).execute().use { r ->
                if (!r.isSuccessful) return@use ""
                r.body?.string().orEmpty()
            }
            for (p in CHANNEL_ID_REGEXES) {
                val m = p.matcher(html)
                if (m.find()) return@withContext m.group(1)
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "resolveChannelIdFromVideo failed", e)
            null
        }
    }

    /** Latest ~15 uploads of a channel from the public RSS feed (newest first). */
    suspend fun fetchChannelFeed(channelId: String): List<FeedEntry> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://www.youtube.com/feeds/videos.xml?channel_id=$channelId")
                .header("User-Agent", "Mozilla/5.0")
                .build()
            val xml = client.newCall(request).execute().use { r ->
                if (!r.isSuccessful) return@withContext emptyList()
                r.body?.string().orEmpty()
            }
            val entryPattern = Pattern.compile(
                "<entry>.*?<yt:videoId>(.+?)</yt:videoId>.*?<title>(.*?)</title>.*?<published>(.+?)</published>",
                Pattern.DOTALL
            )
            val m = entryPattern.matcher(xml)
            val list = mutableListOf<FeedEntry>()
            while (m.find()) {
                list.add(FeedEntry(m.group(1)!!.trim(), decodeXml(m.group(2)!!.trim()), m.group(3)!!.trim()))
            }
            list
        } catch (e: Exception) {
            Log.w(TAG, "fetchChannelFeed failed", e)
            emptyList()
        }
    }

    private fun decodeXml(s: String): String = s
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")

    /**
     * Resolves metadata and direct audio stream info for a video ID or live stream.
     */
    suspend fun resolveVideoDetails(videoId: String): StreamInfo? = withContext(Dispatchers.IO) {
        try {
            // First attempt: YouTube Innertube player endpoint
            val playerUrl = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false"
            val bodyJson = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "ANDROID")
                        put("clientVersion", "18.19.35")
                        put("hl", "iw")
                        put("gl", "IL")
                    })
                })
                put("videoId", videoId)
            }

            val request = Request.Builder()
                .url(playerUrl)
                .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
                .header("User-Agent", "com.google.android.youtube/18.19.35 (Linux; U; Android 13)")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val parsed = parsePlayerResponse(videoId, body)
                    if (parsed != null) return@withContext parsed
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Innertube player call failed", e)
        }

        // Secondary resolver: Invidious audio stream endpoint
        try {
            val invidiousUrl = "https://invidious.nerdvpn.de/api/v1/videos/$videoId"
            val req = Request.Builder()
                .url(invidiousUrl)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            client.newCall(req).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val root = JSONObject(body)
                    val title = root.optString("title", "שיר מיוטיוב")
                    val author = root.optString("author", "אמן")
                    val lengthSeconds = root.optLong("lengthSeconds", 0L)
                    val isLive = root.optBoolean("liveNow", false)

                    // Find M4A adaptive audio stream
                    val adaptiveFormats = root.optJSONArray("adaptiveFormats")
                    var bestAudioUrl: String? = null
                    var bestBitrate = 128
                    var contentLen = 0L

                    if (adaptiveFormats != null) {
                        for (i in 0 until adaptiveFormats.length()) {
                            val format = adaptiveFormats.getJSONObject(i)
                            val type = format.optString("type", "")
                            if (type.contains("audio/mp4") || format.optString("container") == "m4a") {
                                val url = format.optString("url")
                                val bitrate = format.optInt("bitrate", 0) / 1000
                                if (url.isNotBlank() && (bestAudioUrl == null || bitrate >= bestBitrate)) {
                                    bestAudioUrl = url
                                    bestBitrate = bitrate.coerceAtLeast(128)
                                    contentLen = format.optLong("clen", 0L)
                                }
                            }
                        }
                    }

                    // Fallback to HLS playlist for live streams
                    if (isLive && bestAudioUrl == null) {
                        bestAudioUrl = root.optString("hlsUrl")
                    }

                    if (!bestAudioUrl.isNullOrBlank()) {
                        return@withContext StreamInfo(
                            videoId = videoId,
                            title = title,
                            channel = author,
                            durationMs = lengthSeconds * 1000L,
                            durationText = if (isLive) "LIVE" else formatDuration(lengthSeconds * 1000L),
                            thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                            streamUrl = bestAudioUrl,
                            isLive = isLive,
                            approxSizeBytes = contentLen,
                            audioBitrateKbps = bestBitrate
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Invidious stream resolver failed", e)
        }

        null
    }

    private fun parsePlayerResponse(videoId: String, jsonString: String): StreamInfo? {
        return try {
            val root = JSONObject(jsonString)
            val videoDetails = root.optJSONObject("videoDetails") ?: return null
            val title = videoDetails.optString("title", "רצועת שמע")
            val author = videoDetails.optString("author", "יוצר יוטיוב")
            val lengthSeconds = videoDetails.optLong("lengthSeconds", 0L)
            val isLive = videoDetails.optBoolean("isLive", false) || videoDetails.optBoolean("isLiveContent", false)

            val streamingData = root.optJSONObject("streamingData") ?: return null

            // Look for audio/mp4 (M4A) streams in adaptiveFormats
            val adaptiveFormats = streamingData.optJSONArray("adaptiveFormats")
            var selectedUrl: String? = null
            var selectedBitrate = 160
            var approxSize = 0L

            if (adaptiveFormats != null) {
                for (i in 0 until adaptiveFormats.length()) {
                    val format = adaptiveFormats.getJSONObject(i)
                    val mimeType = format.optString("mimeType", "")
                    if (mimeType.contains("audio/mp4")) {
                        val url = format.optString("url")
                        val bitrate = format.optInt("bitrate", 0) / 1000
                        val contentLength = format.optLong("contentLength", 0L)
                        if (url.isNotBlank()) {
                            if (selectedUrl == null || bitrate >= selectedBitrate) {
                                selectedUrl = url
                                selectedBitrate = bitrate.coerceAtLeast(128)
                                approxSize = contentLength
                            }
                        }
                    }
                }
            }

            // If live stream, look for HLS manifest URL
            if (isLive && selectedUrl == null) {
                selectedUrl = streamingData.optString("hlsManifestUrl")
            }

            if (selectedUrl.isNullOrBlank()) return null

            StreamInfo(
                videoId = videoId,
                title = title,
                channel = author,
                durationMs = lengthSeconds * 1000L,
                durationText = if (isLive) "LIVE" else formatDuration(lengthSeconds * 1000L),
                thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                streamUrl = selectedUrl,
                isLive = isLive,
                approxSizeBytes = approxSize,
                audioBitrateKbps = selectedBitrate
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing player response", e)
            null
        }
    }

    private fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        val hours = min / 60
        val remainingMin = min % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, remainingMin, sec)
        } else {
            String.format("%02d:%02d", min, sec)
        }
    }

    private fun parseDurationTextToMs(text: String): Long {
        if (text.isBlank() || text.equals("LIVE", ignoreCase = true)) return 0L
        val parts = text.split(":").mapNotNull { it.trim().toLongOrNull() }
        return when (parts.size) {
            3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000L
            2 -> (parts[0] * 60 + parts[1]) * 1000L
            1 -> parts[0] * 1000L
            else -> 0L
        }
    }

    private fun formatViews(views: Long): String {
        return when {
            views >= 1_000_000 -> String.format("%.1fM צפיות", views / 1_000_000f)
            views >= 1_000 -> String.format("%.1fK צפיות", views / 1_000f)
            else -> "$views צפיות"
        }
    }
}
