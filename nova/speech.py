"""Speech in (whisper.cpp) and speech out (Piper). Voices are downloaded at install time."""
from __future__ import annotations

import os
import shutil
import subprocess
import wave
from pathlib import Path

from . import paths

# Official Piper ONNX voices (no celebrity / real-person cloning).
# "codeaque" is the labelled default: a warm young American female voice.
VOICES = {
    "codeaque": {
        "title": "CodeAque voice",
        "subtitle": "Warm young American female (Piper amy) — default",
        "id": "en_US-amy-medium",
        "onnx": "en_US-amy-medium.onnx",
        "json": "en_US-amy-medium.onnx.json",
    },
    "lessac": {
        "title": "Lessac",
        "subtitle": "Clear American female (Piper lessac)",
        "id": "en_US-lessac-medium",
        "onnx": "en_US-lessac-medium.onnx",
        "json": "en_US-lessac-medium.onnx.json",
    },
    "hfc_female": {
        "title": "HFC Female",
        "subtitle": "American female (Piper hfc_female)",
        "id": "en_US-hfc_female-medium",
        "onnx": "en_US-hfc_female-medium.onnx",
        "json": "en_US-hfc_female-medium.onnx.json",
    },
    "alba": {
        "title": "Alba",
        "subtitle": "Scottish English female (Piper alba)",
        "id": "en_GB-alba-medium",
        "onnx": "en_GB-alba-medium.onnx",
        "json": "en_GB-alba-medium.onnx.json",
    },
    "jenny": {
        "title": "Jenny",
        "subtitle": "British English female (Piper jenny_dioco)",
        "id": "en_GB-jenny_dioco-medium",
        "onnx": "en_GB-jenny_dioco-medium.onnx",
        "json": "en_GB-jenny_dioco-medium.onnx.json",
    },
    "myvoice": {
        "title": "My voice (Beta)",
        "subtitle": "Record only your own voice. Cloning is a work in progress and may not be ready.",
        "id": "myvoice",
        "onnx": "",
        "json": "",
    },
}

CONSENT_TEXT = (
    "My voice is for recording YOUR OWN voice only. Do not record anyone else, "
    "and do not try to copy a celebrity, streamer, or friend. Nova never lets you "
    "import someone else's clips or files. The recording stays on this PC."
)

WHISPER_MODEL = "ggml-base.en.bin"


def piper_bin() -> Path:
    names = ("piper.exe", "piper") if os.name == "nt" else ("piper",)
    for name in names:
        p = paths.runtime_dir() / "piper" / name
        if p.is_file():
            return p
        w = shutil.which(name)
        if w:
            return Path(w)
    return Path("/nonexistent")


def whisper_bin() -> Path:
    names = ("whisper-cli.exe", "whisper-cli", "main.exe", "main", "whisper.exe")
    found = []
    root = paths.runtime_dir() / "whisper"
    for name in names:
        found.append(root / name)
    for name in ("whisper-cli", "whisper-cli.exe", "whisper-cpp", "whisper"):
        w = shutil.which(name)
        if w:
            found.append(Path(w))
    for c in found:
        if c.is_file():
            return c
    return Path("/nonexistent")


def whisper_model() -> Path:
    return paths.models_dir() / "whisper" / WHISPER_MODEL


def voice_files(key: str) -> tuple[Path, Path] | None:
    meta = VOICES.get(key) or VOICES["codeaque"]
    if key == "myvoice":
        onnx = paths.my_voice_dir() / "voice.onnx"
        js = paths.my_voice_dir() / "voice.onnx.json"
        if onnx.is_file() and js.is_file():
            return onnx, js
        return None
    onnx = paths.voices_dir() / meta["onnx"]
    js = paths.voices_dir() / meta["json"]
    if onnx.is_file() and js.is_file():
        return onnx, js
    return None


def my_voice_ready() -> bool:
    return voice_files("myvoice") is not None


def resolve_voice(cfg: dict) -> str:
    key = cfg.get("voice") or "codeaque"
    if key == "myvoice" and not my_voice_ready():
        return "codeaque"
    if key not in VOICES:
        return "codeaque"
    return key


def synthesize(text: str, cfg: dict, out_wav: Path) -> Path:
    """Write a WAV of the spoken reply with Piper. Raises if Piper or the voice is missing."""
    key = resolve_voice(cfg)
    files = voice_files(key)
    exe = piper_bin()
    if not exe.is_file():
        raise RuntimeError("Piper is not installed yet. Open Setup to finish installing Nova.")
    if not files:
        raise RuntimeError("That voice isn't downloaded yet. Open Setup, or pick another voice.")
    onnx, js = files
    out_wav.parent.mkdir(parents=True, exist_ok=True)
    env = os.environ.copy()
    libdir = exe.parent / "lib"
    if libdir.is_dir():
        env["LD_LIBRARY_PATH"] = str(libdir) + ((":" + env["LD_LIBRARY_PATH"]) if env.get("LD_LIBRARY_PATH") else "")
    proc = subprocess.run(
        [str(exe), "--model", str(onnx), "--config", str(js), "--output_file", str(out_wav)],
        input=text.encode("utf-8"),
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        env=env,
        timeout=120,
        check=False,
    )
    if proc.returncode != 0 or not out_wav.is_file():
        err = (proc.stderr or b"").decode("utf-8", "replace")[-400:]
        raise RuntimeError("Piper could not speak that reply. " + err)
    return out_wav


def play_wav(path: Path) -> subprocess.Popen:
    """Start playback and return the process so the UI can drive lip-sync until it ends."""
    if os.name == "nt":
        # Media.SoundPlayer is built in on Windows. PlaySync so the process ends with the sound.
        ps = (
            "$p = New-Object System.Media.SoundPlayer -ArgumentList @($args[0]); "
            "$p.PlaySync()"
        )
        return subprocess.Popen(
            ["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", ps, str(path)],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
    for cmd in (
        ["paplay", str(path)],
        ["aplay", "-q", str(path)],
        ["ffplay", "-nodisp", "-autoexit", "-loglevel", "quiet", str(path)],
    ):
        if shutil.which(cmd[0]):
            return subprocess.Popen(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    raise RuntimeError("No audio player found (need paplay, aplay, or ffplay).")


def record_wav(dest: Path, seconds: int = 8, rate: int = 16000) -> Path:
    dest.parent.mkdir(parents=True, exist_ok=True)
    if os.name == "nt":
        ffmpeg = shutil.which("ffmpeg")
        if not ffmpeg:
            raise RuntimeError(
                "Need ffmpeg on PATH to use the microphone on Windows. "
                "Install it (for example from https://www.gyan.dev/ffmpeg/builds/) and try again."
            )
        # Prefer WASAPI default capture; fall back to DirectShow if this ffmpeg is older.
        attempts = [
            [ffmpeg, "-y", "-f", "wasapi", "-i", "default", "-t", str(seconds), "-ac", "1", "-ar", str(rate), str(dest)],
            [ffmpeg, "-y", "-f", "dshow", "-i", "audio=default", "-t", str(seconds), "-ac", "1", "-ar", str(rate), str(dest)],
        ]
        last = None
        for cmd in attempts:
            last = subprocess.run(cmd, timeout=seconds + 15, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
            if last.returncode == 0 and dest.is_file():
                return dest
        err = (last.stderr or b"").decode("utf-8", "replace")[-300:] if last else ""
        raise RuntimeError("Could not record from the default microphone. " + err)
    if shutil.which("arecord"):
        subprocess.run(
            ["arecord", "-q", "-d", str(seconds), "-f", "S16_LE", "-r", str(rate), "-c", "1", str(dest)],
            check=True,
            timeout=seconds + 5,
        )
    elif shutil.which("ffmpeg"):
        subprocess.run(
            [
                "ffmpeg", "-y", "-f", "pulse", "-i", "default",
                "-t", str(seconds), "-ac", "1", "-ar", str(rate), str(dest),
            ],
            check=True,
            timeout=seconds + 10,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
    else:
        raise RuntimeError("Need arecord or ffmpeg to use the microphone.")
    return dest


def transcribe(wav: Path) -> str:
    exe = whisper_bin()
    model = whisper_model()
    if not exe.is_file() or not model.is_file():
        raise RuntimeError("Speech recognition isn't installed yet. Open Setup to finish installing Nova.")
    out_base = wav.with_suffix("")
    subprocess.run(
        [str(exe), "-m", str(model), "-f", str(wav), "-otxt", "-of", str(out_base), "-nt"],
        check=True,
        timeout=180,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    txt = Path(str(out_base) + ".txt")
    if not txt.is_file():
        raise RuntimeError("Whisper did not write a transcript.")
    return txt.read_text(encoding="utf-8").strip()


def wav_duration(path: Path) -> float:
    try:
        with wave.open(str(path), "rb") as w:
            if w.getframerate() <= 0:
                return 0.0
            return w.getnframes() / float(w.getframerate())
    except Exception:
        return 0.0
