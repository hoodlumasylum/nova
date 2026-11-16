"""Chat history saved only on this PC."""
from __future__ import annotations

import json
from pathlib import Path

from . import paths

MAX_TURNS = 200


def load() -> list[dict]:
    dest = paths.history_file()
    try:
        if dest.is_file():
            raw = json.loads(dest.read_text(encoding="utf-8"))
            if isinstance(raw, list):
                return [m for m in raw if isinstance(m, dict) and "role" in m and "content" in m][-MAX_TURNS:]
    except (OSError, ValueError):
        pass
    return []


def save(messages: list[dict]) -> None:
    dest = paths.history_file()
    dest.parent.mkdir(parents=True, exist_ok=True)
    slim = [{"role": str(m.get("role", "user")), "content": str(m.get("content", ""))[:8000]} for m in messages[-MAX_TURNS:]]
    tmp = dest.with_suffix(".json.tmp")
    tmp.write_text(json.dumps(slim, indent=2), encoding="utf-8")
    tmp.replace(dest)


def clear() -> None:
    dest = paths.history_file()
    try:
        dest.unlink(missing_ok=True)
    except OSError:
        pass
