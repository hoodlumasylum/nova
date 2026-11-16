"""Build a mouth-open envelope from a WAV so Nova's mouth can lip-sync while she talks."""
from __future__ import annotations

import struct
import wave
from pathlib import Path


def mouth_envelope(path: str | Path, frame_ms: int = 30) -> list[tuple[float, float]]:
    """Return a list of (time_seconds, mouth_open 0..1) from a mono or stereo 16-bit WAV."""
    p = Path(path)
    if not p.is_file():
        return []
    with wave.open(str(p), "rb") as w:
        ch = w.getnchannels()
        sw = w.getsampwidth()
        rate = w.getframerate()
        n = w.getnframes()
        raw = w.readframes(n)
    if sw != 2 or rate <= 0 or not raw:
        return []
    count = len(raw) // 2
    samples = struct.unpack("<" + "h" * count, raw[: count * 2])
    if ch > 1:
        samples = samples[0::ch]
    step = max(1, int(rate * (frame_ms / 1000.0)))
    # Normalise against a high percentile so quiet speech still moves the mouth.
    windows: list[tuple[float, float]] = []
    peaks: list[float] = []
    for i in range(0, len(samples), step):
        chunk = samples[i : i + step]
        if not chunk:
            break
        acc = 0.0
        for s in chunk:
            acc += (s / 32768.0) ** 2
        rms = (acc / len(chunk)) ** 0.5
        peaks.append(rms)
        t = i / float(rate)
        windows.append((t, rms))
    if not windows:
        return []
    ordered = sorted(peaks)
    scale = ordered[int(len(ordered) * 0.9)] or 0.05
    out = []
    for t, rms in windows:
        # Soft gate plus a little shape so it isn't a flat open mouth.
        v = max(0.0, min(1.0, (rms / scale) ** 0.7))
        if v < 0.06:
            v = 0.0
        out.append((t, v))
    return out


def mouth_at(envelope: list[tuple[float, float]], t: float) -> float:
    if not envelope:
        return 0.0
    if t <= envelope[0][0]:
        return envelope[0][1]
    if t >= envelope[-1][0]:
        return 0.0
    lo, hi = 0, len(envelope) - 1
    while lo + 1 < hi:
        mid = (lo + hi) // 2
        if envelope[mid][0] <= t:
            lo = mid
        else:
            hi = mid
    t0, v0 = envelope[lo]
    t1, v1 = envelope[hi]
    if t1 <= t0:
        return v0
    f = (t - t0) / (t1 - t0)
    return v0 + (v1 - v0) * f
