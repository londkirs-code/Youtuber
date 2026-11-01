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
        "architecture": getattr(ytdlp, "version", None).__class__.__name__,
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


def clear_cache():
    if _CACHE_DIR and os.path.isdir(_CACHE_DIR):
        shutil.rmtree(_CACHE_DIR, ignore_errors=True)
    if _CACHE_DIR:
        os.makedirs(_CACHE_DIR, exist_ok=True)
    return True
