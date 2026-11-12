"""Embedded TuneVault yt-dlp runner.

The app ships CPython 3.14, yt-dlp and yt-dlp-ejs. FFmpeg/FFprobe and
QuickJS are native Android executables configured by the Kotlin host.
"""
import contextlib
import importlib
import importlib.metadata
import importlib.util
import io
import json
import os
import platform
import re
import shlex
import shutil
import sys
import traceback

_FFMPEG_DIR = None
_QUICKJS_PATH = None
_CACHE_DIR = None
_USER_SITE_DIR = None
_BLOCKED_OPTIONS = {
    "--exec",
    "--exec-before-download",
    "--exec-after-download",
    "--plugin-dirs",
    "--config-location",
    "--config-locations",
    "--update-to",
}


def configure(ffmpeg_dir, quickjs_path, cache_dir, user_site_dir):
    global _FFMPEG_DIR, _QUICKJS_PATH, _CACHE_DIR, _USER_SITE_DIR
    _FFMPEG_DIR = ffmpeg_dir
    _QUICKJS_PATH = quickjs_path
    _CACHE_DIR = cache_dir
    _USER_SITE_DIR = user_site_dir
    os.makedirs(_CACHE_DIR, exist_ok=True)
    if _USER_SITE_DIR and os.path.isdir(_USER_SITE_DIR):
        while _USER_SITE_DIR in sys.path:
            sys.path.remove(_USER_SITE_DIR)
        sys.path.insert(0, _USER_SITE_DIR)


def _yt_dlp():
    return importlib.import_module("yt_dlp")


def _emit(callback, level, message):
    if callback is None:
        return
    try:
        callback.onLog(str(level), str(message))
    except Exception:
        pass


class _Logger:
    def __init__(self, callback):
        self.callback = callback

    def debug(self, message):
        _emit(self.callback, "DEBUG", message)

    def info(self, message):
        _emit(self.callback, "INFO", message)

    def warning(self, message):
        _emit(self.callback, "WARNING", message)

    def error(self, message):
        _emit(self.callback, "ERROR", message)


def _progress_hook(callback):
    def hook(data):
        if callback is None:
            return
        try:
            if callback.isCancelled():
                raise RuntimeError("TUNEVault_CANCELLED")
            if data.get("status") != "downloading":
                return
            raw_percent = str(data.get("_percent_str") or "0").replace("%", "")
            raw_percent = re.sub(r"\x1b\[[0-9;]*m", "", raw_percent).strip()
            percent = float(raw_percent or 0)
            downloaded = int(data.get("downloaded_bytes") or 0)
            total = int(data.get("total_bytes") or data.get("total_bytes_estimate") or 0)
            speed = str(data.get("_speed_str") or "")
            eta = int(data.get("eta") or 0)
            callback.onProgress(percent, downloaded, total, speed, eta)
        except RuntimeError:
            raise
        except Exception:
            pass
    return hook


def _check_cancel(callback):
    if callback is not None and callback.isCancelled():
        raise RuntimeError("TUNEVault_CANCELLED")


def _runtime_options():
    return {
        "ffmpeg_location": _FFMPEG_DIR,
        "js_runtimes": {"quickjs": {"path": _QUICKJS_PATH}},
        "remote_components": {"ejs:github"},
        "cachedir": _CACHE_DIR,
    }


def versions():
    ytdlp = _yt_dlp()
    try:
        ejs_version = importlib.metadata.version("yt-dlp-ejs")
    except Exception:
        ejs_version = "not installed"
    return json.dumps({
        "python": sys.version.split()[0],
        "yt_dlp": getattr(ytdlp, "__version__", "unknown"),
        "ejs": ejs_version,
        "curl_cffi": importlib.util.find_spec("curl_cffi") is not None,
        "extractors": len(ytdlp.list_extractors()),
        "architecture": platform.machine(),
    })


def _prepare_args(argv):
    if isinstance(argv, str):
        args = shlex.split(argv)
    else:
        args = [str(x) for x in (argv or [])]
    if args and args[0] in ("yt-dlp", "yt_dlp"):
        args = args[1:]
    for token in args:
        option = token.split("=", 1)[0]
        if option in _BLOCKED_OPTIONS:
            raise ValueError("The Playground blocks shell execution and external plugin/config loading: " + option)
    return args


def run_cli(argv, callback=None):
    """Run yt-dlp CLI options through its official Python option parser."""
    ytdlp = _yt_dlp()
    args = _prepare_args(argv)

    if "--version" in args:
        _emit(callback, "INFO", "yt-dlp " + str(ytdlp.__version__))
        return 0

    if "--list-extractors" in args:
        names = [item.IE_NAME for item in ytdlp.list_extractors()]
        _emit(callback, "INFO", "Supported extractors: " + str(len(names)))
        for name in names:
            _emit(callback, "INFO", name)
        return 0

    if any(arg in ("-U", "--update") or arg.startswith("--update=") for arg in args):
        _emit(callback, "WARNING", "Use the TuneVault engine update control to update yt-dlp safely.")
        return 2

    if "--ignore-config" not in args:
        args.append("--ignore-config")
    if "--no-colors" not in args:
        args.append("--no-colors")

    output = io.StringIO()
    try:
        with contextlib.redirect_stdout(output), contextlib.redirect_stderr(output):
            _, _, urls, ydl_options = ytdlp.parse_options(args)
            ydl_options.update(_runtime_options())
            ydl_options["logger"] = _Logger(callback)
            ydl_options["progress_hooks"] = [_progress_hook(callback)]
            with ytdlp.YoutubeDL(ydl_options) as downloader:
                _check_cancel(callback)
                exit_code = downloader.download(urls)
        for line in output.getvalue().splitlines():
            _emit(callback, "INFO", line)
        _emit(callback, "INFO", "yt-dlp exit code: " + str(exit_code))
        return int(exit_code)
    except SystemExit as error:
        for line in output.getvalue().splitlines():
            _emit(callback, "INFO", line)
        return int(error.code or 0)
    except Exception as error:
        for line in output.getvalue().splitlines():
            _emit(callback, "INFO", line)
        _emit(callback, "ERROR", traceback.format_exc())
        raise error


def run_cli_text(command, callback=None):
    return run_cli(command, callback)


def inspect_url(url, player_client="default", callback=None):
    ytdlp = _yt_dlp()
    options = _runtime_options()
    options.update({
        "quiet": True,
        "no_warnings": False,
        "noplaylist": True,
        "extract_flat": False,
        "logger": _Logger(callback),
        "extractor_args": {"youtube": {"player_client": [player_client]}},
    })
    with ytdlp.YoutubeDL(options) as downloader:
        _check_cancel(callback)
        info = downloader.extract_info(url, download=False)
    if not info:
        raise RuntimeError("yt-dlp returned no video information")
    result = {
        "id": info.get("id"),
        "title": info.get("title"),
        "extractor": info.get("extractor"),
        "duration": info.get("duration"),
        "webpage_url": info.get("webpage_url"),
        "format_count": len(info.get("formats") or []),
        "formats": [
            {
                "format_id": item.get("format_id"),
                "ext": item.get("ext"),
                "resolution": item.get("resolution"),
                "acodec": item.get("acodec"),
                "vcodec": item.get("vcodec"),
                "filesize": item.get("filesize") or item.get("filesize_approx"),
            }
            for item in (info.get("formats") or [])[:30]
        ],
    }
    _emit(callback, "INFO", "Extracted: " + str(result.get("title") or result.get("id")))
    return json.dumps(result, ensure_ascii=False)


def update_packages(target_dir, callback=None):
    """Install fresh pure-Python yt-dlp and EJS wheels into an isolated writable directory."""
    from pip._internal.cli.main import main as pip_main

    os.makedirs(target_dir, exist_ok=True)
    output = io.StringIO()
    args = [
        "install",
        "--disable-pip-version-check",
        "--no-input",
        "--no-warn-script-location",
        "--no-deps",
        "--upgrade",
        "--target",
        target_dir,
        "yt-dlp",
        "yt-dlp-ejs",
    ]
    with contextlib.redirect_stdout(output), contextlib.redirect_stderr(output):
        result = pip_main(args)
    for line in output.getvalue().splitlines():
        _emit(callback, "INFO", line)
    return int(result or 0)



def activate_packages(package_dir):
    """Activate newly installed yt-dlp/EJS packages in the running Python process."""
    global _USER_SITE_DIR
    package_dir = os.path.abspath(package_dir)
    if not os.path.isdir(package_dir):
        raise RuntimeError("Updated Python package directory is missing")
    for path in (package_dir, _USER_SITE_DIR):
        if path and path in sys.path:
            while path in sys.path:
                sys.path.remove(path)
    sys.path.insert(0, package_dir)
    _USER_SITE_DIR = package_dir

    # Discard already-imported modules so subsequent calls load the new package.
    for name in list(sys.modules):
        if (name == "yt_dlp" or name.startswith("yt_dlp.") or
                name == "yt_dlp_ejs" or name.startswith("yt_dlp_ejs.")):
            del sys.modules[name]
    importlib.invalidate_caches()

    ytdlp = importlib.import_module("yt_dlp")
    ejs_version = importlib.metadata.version("yt-dlp-ejs")
    return json.dumps({
        "yt_dlp": getattr(ytdlp, "__version__", "unknown"),
        "ejs": ejs_version,
        "extractors": len(ytdlp.list_extractors()),
    })


def clear_cache():
    if _CACHE_DIR and os.path.isdir(_CACHE_DIR):
        shutil.rmtree(_CACHE_DIR, ignore_errors=True)
    if _CACHE_DIR:
        os.makedirs(_CACHE_DIR, exist_ok=True)
    return True


def _patch_init_segment_duration(data):
    """Patch mvhd, tkhd, and mehd in fMP4 init segment so local media players do not cap playback at 5 seconds."""
    try:
        ba = bytearray(data)
        # Search for 'mvhd' (movie header)
        idx = ba.find(b"mvhd")
        if idx != -1 and idx + 28 <= len(ba):
            version = ba[idx + 8]
            if version == 0 and idx + 28 <= len(ba):
                ba[idx + 24 : idx + 28] = b"\x7f\xff\xff\xff"
            elif version == 1 and idx + 40 <= len(ba):
                ba[idx + 32 : idx + 40] = b"\x00\x00\x00\x00\x7f\xff\xff\xff"

        # Search for 'tkhd' (track header)
        idx_t = ba.find(b"tkhd")
        if idx_t != -1 and idx_t + 32 <= len(ba):
            version_t = ba[idx_t + 8]
            if version_t == 0 and idx_t + 32 <= len(ba):
                ba[idx_t + 28 : idx_t + 32] = b"\x7f\xff\xff\xff"
            elif version_t == 1 and idx_t + 44 <= len(ba):
                ba[idx_t + 36 : idx_t + 44] = b"\x00\x00\x00\x00\x7f\xff\xff\xff"

        # Search for 'mehd' (movie extends header box)
        idx_m = ba.find(b"mehd")
        if idx_m != -1 and idx_m + 16 <= len(ba):
            version_m = ba[idx_m + 8]
            if version_m == 0 and idx_m + 16 <= len(ba):
                ba[idx_m + 12 : idx_m + 16] = b"\x7f\xff\xff\xff"
            elif version_m == 1 and idx_m + 24 <= len(ba):
                ba[idx_m + 12 : idx_m + 20] = b"\x00\x00\x00\x00\x7f\xff\xff\xff"

        return bytes(ba)
    except Exception:
        return data


def download_live_hls(url, player_client="default", out_base="", callback=None):
    """Download HLS live stream segments directly in Python without FFmpeg network dependency."""
    import time
    from urllib.parse import urljoin
    from yt_dlp.networking import Request

    ytdlp = _yt_dlp()
    options = _runtime_options()
    options.update({
        "quiet": True,
        "no_warnings": False,
        "noplaylist": True,
        "check_formats": False,
        "logger": _Logger(callback),
        "extractor_args": {"youtube": {"player_client": [player_client]}},
    })

    _emit(callback, "INFO", f"[live-hls] Extracting live stream info using player_client={player_client}...")
    with ytdlp.YoutubeDL(options) as downloader:
        _check_cancel(callback)
        info = downloader.extract_info(url, download=False)
        if not info:
            raise RuntimeError("yt-dlp returned no stream information")

        m3u8_formats = [
            f for f in (info.get("formats") or [])
            if str(f.get("protocol") or "").startswith("m3u8")
        ]
        available_ids = [str(f.get("format_id")) for f in m3u8_formats]
        _emit(callback, "INFO", f"[live-hls] Available m3u8 formats: {', '.join(available_ids) if available_ids else 'none'}")

        chosen_fmt = None
        out_ext = "m4a"

        # 1. Priority 1: format_id == "234" (audio only) -> .m4a
        for f in m3u8_formats:
            if str(f.get("format_id")) == "234":
                chosen_fmt = f
                out_ext = "m4a"
                break

        # 2. Priority 2: 91, then 92-96 or 95 (video+audio) -> .mp3
        if not chosen_fmt:
            for fid in ["91", "92", "93", "94", "95", "96"]:
                for f in m3u8_formats:
                    if str(f.get("format_id")) == fid:
                        chosen_fmt = f
                        out_ext = "mp3"
                        break
                if chosen_fmt:
                    break

        # 3. Priority 3: any m3u8 format with audio -> .mp3
        if not chosen_fmt:
            for f in m3u8_formats:
                if f.get("acodec") != "none":
                    chosen_fmt = f
                    out_ext = "mp3"
                    break

        if not chosen_fmt and m3u8_formats:
            chosen_fmt = m3u8_formats[0]
            out_ext = "mp3"

        if not chosen_fmt:
            raise RuntimeError(f"No m3u8 live formats found among: {available_ids}")

        _emit(callback, "INFO", f"[live-hls] Selected format {chosen_fmt.get('format_id')} -> saving as .{out_ext}")

        m3u8_url = chosen_fmt.get("url")
        http_headers = chosen_fmt.get("http_headers") or {}

        def _fetch(req_url):
            try:
                req = Request(req_url, headers=http_headers)
                res = downloader.urlopen(req)
                return res.read()
            except Exception as e:
                status_code = getattr(e, "code", None) or getattr(e, "status", None)
                err_detail = ""
                try:
                    if hasattr(e, "read"):
                        err_detail = e.read().decode("utf-8", errors="replace")[:400]
                    elif hasattr(e, "response") and hasattr(e.response, "read"):
                        err_detail = e.response.read().decode("utf-8", errors="replace")[:400]
                except Exception:
                    pass
                msg = f"HTTP {status_code}: {e}" if status_code else str(e)
                if err_detail:
                    msg += f" | Body: {err_detail.strip()}"
                _emit(callback, "WARNING", f"[live-hls] Network fetch failed for {req_url.split('?')[0]}: {msg}")
                raise

        # Check initial playlist
        clean_url = m3u8_url.split("?")[0] if m3u8_url else ""
        _emit(callback, "INFO", f"[live-hls] Initial playlist: {clean_url}")

        raw_playlist = _fetch(m3u8_url)
        playlist_text = raw_playlist.decode("utf-8", errors="replace")
        preview_lines = [l.strip() for l in playlist_text.splitlines() if l.strip()][:15]
        if preview_lines:
            _emit(callback, "INFO", f"[live-hls] Playlist preview (15 lines):\n" + "\n".join(preview_lines))
        else:
            _emit(callback, "WARNING", "[live-hls] Initial playlist returned empty content!")

        # Check if master playlist (#EXT-X-STREAM-INF)
        if "#EXT-X-STREAM-INF" in playlist_text:
            lines = [l.strip() for l in playlist_text.splitlines()]
            variant_url = None
            for i, line in enumerate(lines):
                if line.startswith("#EXT-X-STREAM-INF"):
                    for next_line in lines[i + 1:]:
                        if next_line and not next_line.startswith("#"):
                            variant_url = urljoin(m3u8_url, next_line)
                            break
                    if variant_url:
                        break
            if variant_url:
                m3u8_url = variant_url
                _emit(callback, "INFO", f"[live-hls] Selected master variant: {m3u8_url.split('?')[0]}")

        _emit(callback, "INFO", f"[live-hls] STREAM_URL={m3u8_url}")

        out_path = f"{out_base}.{out_ext}"
        if os.path.exists(out_path):
            try:
                os.remove(out_path)
            except Exception:
                pass

        out_file = open(out_path, "ab")
        last_written_seq = -1
        last_init_uri = None
        total_bytes_written = 0
        total_segments_written = 0
        total_audio_duration_sec = 0.0
        consecutive_playlist_errors = 0

        try:
            while True:
                _check_cancel(callback)

                # Fetch media playlist
                try:
                    p_bytes = _fetch(m3u8_url)
                    p_text = p_bytes.decode("utf-8", errors="replace")
                    consecutive_playlist_errors = 0
                except Exception as err:
                    consecutive_playlist_errors += 1
                    _emit(callback, "WARNING", f"[live-hls] Playlist refresh failed ({consecutive_playlist_errors}/5): {err}")
                    if consecutive_playlist_errors >= 5:
                        if total_bytes_written > 0:
                            _emit(callback, "INFO", f"[live-hls] Playlist unavailable 5 times; finishing with {total_bytes_written} bytes.")
                            break
                        raise RuntimeError(f"Failed to fetch live playlist 5 times in a row: {err}")
                    time.sleep(1.0)
                    continue

                target_duration = 2.0
                media_seq = 0
                init_uri = None
                p_lines = [l.strip() for l in p_text.splitlines()]
                is_endlist = False

                for line in p_lines:
                    if line.startswith("#EXT-X-TARGETDURATION:"):
                        try:
                            target_duration = float(line.split(":")[1])
                        except Exception:
                            pass
                    elif line.startswith("#EXT-X-MEDIA-SEQUENCE:"):
                        try:
                            media_seq = int(line.split(":")[1])
                        except Exception:
                            pass
                    elif line.startswith("#EXT-X-MAP:"):
                        m = re.search(r'URI="?([^",]+)"?', line)
                        if m:
                            init_uri = m.group(1)
                    elif line.startswith("#EXT-X-ENDLIST"):
                        is_endlist = True

                # Download init segment if present and changed/new
                if init_uri and init_uri != last_init_uri:
                    full_init_url = urljoin(m3u8_url, init_uri)
                    _emit(callback, "INFO", f"[live-hls] Fetching init segment: {full_init_url.split('?')[0]}")
                    try:
                        init_bytes = _fetch(full_init_url)
                        if init_bytes:
                            init_bytes = _patch_init_segment_duration(init_bytes)
                            out_file.write(init_bytes)
                            out_file.flush()
                            total_bytes_written += len(init_bytes)
                            last_init_uri = init_uri
                    except Exception as e:
                        _emit(callback, "WARNING", f"[live-hls] Failed fetching init segment: {e}")

                # Extract segments with individual #EXTINF durations
                curr_seq = media_seq
                segments = []
                for i, line in enumerate(p_lines):
                    if line.startswith("#EXTINF:"):
                        seg_dur = target_duration
                        try:
                            raw_dur = line[8:].split(",")[0].strip()
                            seg_dur = float(raw_dur)
                        except Exception:
                            seg_dur = target_duration

                        for next_line in p_lines[i + 1:]:
                            if next_line and not next_line.startswith("#"):
                                segments.append((curr_seq, next_line, seg_dur))
                                curr_seq += 1
                                break

                # On first cycle: start from the live edge (now), not from the beginning of DVR broadcast
                if last_written_seq == -1:
                    live_start_index = max(0, len(segments) - 2)
                    new_segments = segments[live_start_index:]
                    if live_start_index > 0:
                        _emit(callback, "INFO", f"[live-hls] Starting at live edge (skipped {live_start_index} earlier DVR segments).")
                else:
                    new_segments = [s for s in segments if s[0] > last_written_seq]

                for seg_seq, seg_rel_url, seg_dur in new_segments:
                    _check_cancel(callback)
                    full_seg_url = urljoin(m3u8_url, seg_rel_url)
                    seg_bytes = None
                    for attempt in range(3):
                        _check_cancel(callback)
                        try:
                            seg_bytes = _fetch(full_seg_url)
                            if seg_bytes:
                                break
                        except Exception as e:
                            _emit(callback, "WARNING", f"[live-hls] Segment {seg_seq} failed (attempt {attempt + 1}/3): {e}")
                            time.sleep(0.5)

                    if seg_bytes:
                        out_file.write(seg_bytes)
                        out_file.flush()
                        total_bytes_written += len(seg_bytes)
                        total_segments_written += 1
                        total_audio_duration_sec += seg_dur
                        last_written_seq = seg_seq

                        if callback:
                            try:
                                audio_sec_int = int(total_audio_duration_sec)
                                callback.onProgress(0.0, total_bytes_written, 0, f"{audio_sec_int}s", audio_sec_int)
                            except Exception:
                                pass

                        if total_segments_written % 5 == 0 or total_segments_written <= 3:
                            _emit(callback, "INFO", f"[live-hls] Audio: {int(total_audio_duration_sec)}s ({total_segments_written} segments), Size: {total_bytes_written // 1024} KB")

                if is_endlist:
                    _emit(callback, "INFO", "[live-hls] Stream ended (#EXT-X-ENDLIST). Finishing recording.")
                    break

                # Sleep half of target duration (max 2s), in 0.5s ticks checking cancel
                sleep_sec = min(2.0, max(0.5, target_duration / 2.0))
                elapsed = 0.0
                while elapsed < sleep_sec:
                    _check_cancel(callback)
                    tick = min(0.5, sleep_sec - elapsed)
                    time.sleep(tick)
                    elapsed += tick

        except RuntimeError as e:
            if "TUNEVault_CANCELLED" in str(e):
                _emit(callback, "INFO", f"[live-hls] Recording stopped by user. Total audio: {int(total_audio_duration_sec)}s ({total_segments_written} segments), {total_bytes_written} bytes.")
                raise
            raise
        finally:
            try:
                out_file.flush()
                out_file.close()
            except Exception:
                pass

        _emit(callback, "INFO", f"[live-hls] Recording finished: {out_path} ({int(total_audio_duration_sec)}s audio, {total_bytes_written} bytes)")
        return json.dumps({
            "path": out_path,
            "format_id": chosen_fmt.get("format_id"),
            "bytes": total_bytes_written,
            "segments": total_segments_written,
            "duration_sec": total_audio_duration_sec
        })

