package com.example.download

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.nio.ByteBuffer

object AudioTrimmer {
    private const val TAG = "AudioTrimmer"

    /**
     * Losslessly trims an M4A audio file between startMs and endMs without re-encoding.
     */
    fun trimM4A(
        sourceFile: File,
        destinationFile: File,
        startMs: Long,
        endMs: Long,
        onProgress: ((Int) -> Unit)? = null
    ): Boolean {
        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null

        return try {
            extractor = MediaExtractor().apply {
                setDataSource(sourceFile.absolutePath)
            }

            var audioTrackIndex = -1
            var trackFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    trackFormat = format
                    break
                }
            }

            if (audioTrackIndex < 0 || trackFormat == null) {
                Log.e(TAG, "No audio track found in source file")
                return false
            }

            extractor.selectTrack(audioTrackIndex)

            val startUs = startMs * 1000L
            val endUs = endMs * 1000L

            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            muxer = MediaMuxer(destinationFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerAudioTrack = muxer.addTrack(trackFormat)
            muxer.start()

            val maxBufferSize = if (trackFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                trackFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
            } else {
                128 * 1024
            }

            val buffer = ByteBuffer.allocate(maxBufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            var presentationTimeOffsetUs = -1L
            val totalTrimDurationUs = (endUs - startUs).coerceAtLeast(1L)

            while (true) {
                bufferInfo.size = extractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) {
                    break
                }

                val sampleTime = extractor.sampleTime
                if (sampleTime > endUs) {
                    break
                }

                if (sampleTime >= startUs) {
                    if (presentationTimeOffsetUs < 0) {
                        presentationTimeOffsetUs = sampleTime
                    }

                    bufferInfo.presentationTimeUs = sampleTime - presentationTimeOffsetUs
                    bufferInfo.flags = extractor.sampleFlags
                    bufferInfo.offset = 0

                    muxer.writeSampleData(muxerAudioTrack, buffer, bufferInfo)

                    val progress = (((sampleTime - startUs).toFloat() / totalTrimDurationUs) * 100).toInt()
                    onProgress?.invoke(progress.coerceIn(0, 100))
                }

                extractor.advance()
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error trimming audio file", e)
            false
        } finally {
            try {
                extractor?.release()
                muxer?.stop()
                muxer?.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing media resources", e)
            }
        }
    }
}
