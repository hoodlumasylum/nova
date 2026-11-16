"""Experimental “Hey Nova” listener. Off by default. Local mic only — no cloud."""
from __future__ import annotations

import shutil
import threading
import time
from pathlib import Path

from . import paths, speech


def available() -> bool:
    return bool(shutil.which("arecord") or shutil.which("ffmpeg")) and speech.whisper_bin().is_file() and speech.whisper_model().is_file()


class WakeListener:
    def __init__(self, callback, phrase: str = "hey nova"):
        self.callback = callback
        self.phrase = phrase.lower()
        self._stop = threading.Event()
        self._thread: threading.Thread | None = None
        self.error = ""

    def start(self) -> bool:
        if not available():
            self.error = (
                "“Hey Nova” needs a microphone (arecord or ffmpeg) and whisper.cpp from Setup. "
                "Until then, use the bubble or push-to-talk."
            )
            return False
        self._stop.clear()
        self._thread = threading.Thread(target=self._run, daemon=True)
        self._thread.start()
        return True

    def stop(self) -> None:
        self._stop.set()

    def _run(self) -> None:
        dest = paths.tmp_dir() / "wake.wav"
        while not self._stop.is_set():
            try:
                # Short window; a simple energy gate inside record is not applied —
                # we transcribe only if the clip is not near-silent.
                speech.record_wav(dest, seconds=2)
                if self._stop.is_set():
                    return
                if _rms(dest) < 0.02:
                    continue
                text = speech.transcribe(dest).lower()
                if self.phrase in text or text.strip() in ("nova", "hey nova", "hi nova", "hello nova"):
                    self.callback()
                    time.sleep(1.5)
            except Exception:
                time.sleep(1.0)


def _rms(path: Path) -> float:
    import struct
    import wave
    try:
        with wave.open(str(path), "rb") as w:
            raw = w.readframes(w.getnframes())
            sw = w.getsampwidth()
        if sw != 2 or not raw:
            return 0.0
        n = len(raw) // 2
        samples = struct.unpack("<" + "h" * n, raw[: n * 2])
        acc = 0.0
        for s in samples:
            acc += (s / 32768.0) ** 2
        return (acc / max(1, len(samples))) ** 0.5
    except Exception:
        return 0.0
