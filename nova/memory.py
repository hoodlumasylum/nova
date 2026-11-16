"""Local notes Nova can reread. This is a file on this PC, not training or retraining."""
from __future__ import annotations

import json
import re
import time
import uuid
from typing import Any

from . import paths

# Facts the user wants kept (name, likes, games, …) and prompts they saved.
# Nothing here is uploaded or used to train a model.
MAX_FACTS = 80
MAX_PROMPTS = 40
MAX_FACT_LEN = 400

_REMEMBER = re.compile(r"\[\[\s*remember\s*:\s*(.+?)\]\]", re.I | re.S)


def _empty() -> dict:
    return {"facts": [], "saved_prompts": []}


def load() -> dict:
    dest = paths.memory_file()
    try:
        if dest.is_file():
            raw = json.loads(dest.read_text(encoding="utf-8"))
            if isinstance(raw, dict):
                facts = raw.get("facts") if isinstance(raw.get("facts"), list) else []
                prompts = raw.get("saved_prompts") if isinstance(raw.get("saved_prompts"), list) else []
                return {
                    "facts": [f for f in facts if isinstance(f, dict) and f.get("text")],
                    "saved_prompts": [p for p in prompts if isinstance(p, dict) and p.get("prompt")],
                }
    except (OSError, ValueError):
        pass
    return _empty()


def save(data: dict) -> None:
    dest = paths.memory_file()
    dest.parent.mkdir(parents=True, exist_ok=True)
    out = {
        "facts": list(data.get("facts") or [])[-MAX_FACTS:],
        "saved_prompts": list(data.get("saved_prompts") or [])[-MAX_PROMPTS:],
    }
    tmp = dest.with_suffix(".json.tmp")
    tmp.write_text(json.dumps(out, indent=2), encoding="utf-8")
    tmp.replace(dest)


def _new_id() -> str:
    return uuid.uuid4().hex[:12]


def add_fact(text: str) -> dict | None:
    clean = " ".join((text or "").strip().split())
    if not clean:
        return None
    clean = clean[:MAX_FACT_LEN]
    data = load()
    for existing in data["facts"]:
        if (existing.get("text") or "").strip().lower() == clean.lower():
            return existing
    item = {"id": _new_id(), "text": clean, "added": int(time.time())}
    data["facts"].append(item)
    save(data)
    return item


def update_fact(fact_id: str, text: str) -> bool:
    clean = " ".join((text or "").strip().split())[:MAX_FACT_LEN]
    if not clean:
        return delete_fact(fact_id)
    data = load()
    for item in data["facts"]:
        if item.get("id") == fact_id:
            item["text"] = clean
            save(data)
            return True
    return False


def delete_fact(fact_id: str) -> bool:
    data = load()
    before = len(data["facts"])
    data["facts"] = [f for f in data["facts"] if f.get("id") != fact_id]
    if len(data["facts"]) == before:
        return False
    save(data)
    return True


def add_saved_prompt(prompt: str, idea: str = "", title: str = "", image_name: str = "") -> dict | None:
    body = (prompt or "").strip()
    if not body:
        return None
    data = load()
    item = {
        "id": _new_id(),
        "title": (title or idea or "Saved prompt").strip()[:80],
        "idea": (idea or "").strip()[:400],
        "prompt": body[:8000],
        "image_name": (image_name or "").strip()[:200],
        "added": int(time.time()),
    }
    data["saved_prompts"].append(item)
    save(data)
    return item


def delete_saved_prompt(prompt_id: str) -> bool:
    data = load()
    before = len(data["saved_prompts"])
    data["saved_prompts"] = [p for p in data["saved_prompts"] if p.get("id") != prompt_id]
    if len(data["saved_prompts"]) == before:
        return False
    save(data)
    return True


def forget_all() -> None:
    """Wipe the local notes file. This does not retrain or 'unlearn' a model — it just deletes the file."""
    save(_empty())
    dest = paths.memory_file()
    try:
        if dest.is_file() and dest.stat().st_size > 0:
            # already overwritten with empty lists; that's enough
            pass
    except OSError:
        pass


def consume_remember_tags(text: str) -> tuple[str, list[str]]:
    """Strip [[remember: …]] tags from a model reply and return (spoken_text, new facts)."""
    if not text:
        return "", []
    found: list[str] = []

    def _keep(m: re.Match) -> str:
        fact = " ".join(m.group(1).strip().split())
        if fact:
            found.append(fact[:MAX_FACT_LEN])
        return " "

    cleaned = _REMEMBER.sub(_keep, text)
    cleaned = re.sub(r"\n{3,}", "\n\n", cleaned).strip()
    return cleaned, found


def apply_remembered(text: str) -> tuple[str, list[dict]]:
    spoken, facts = consume_remember_tags(text)
    stored = []
    for fact in facts:
        item = add_fact(fact)
        if item:
            stored.append(item)
    return spoken, stored


def context_block(mode: str = "chat") -> str:
    """Text added to the system prompt. Honest: these are notes, not a trained memory."""
    data = load()
    facts = [str(f.get("text") or "").strip() for f in data["facts"] if f.get("text")]
    prompts = data["saved_prompts"]
    if not facts and not (mode == "video" and prompts):
        return (
            "Local memory: the user has no saved notes yet. "
            "If they share a lasting fact (name, games they play, things they like), "
            "you may append [[remember: short fact]] at the end of your reply. "
            "This only updates a notes file on this PC. It is not training or retraining."
        )
    parts = [
        "Local memory (a notes file on this PC only. This is not training or retraining; "
        "you just read these notes when you reply):"
    ]
    if facts:
        parts.append("Facts about the user:")
        for i, fact in enumerate(facts, 1):
            parts.append(f"  {i}. {fact}")
    if mode == "video" and prompts:
        parts.append("Prompts the user saved earlier (reuse style or details if they ask):")
        for i, item in enumerate(prompts[-8:], 1):
            title = item.get("title") or "Saved prompt"
            body = (item.get("prompt") or "")[:600]
            parts.append(f"  {i}. {title}: {body}")
    parts.append(
        "Use these notes when they help. If a note is wrong, tell the user they can edit or "
        "forget it in Settings → Memory. Never claim you were trained or retrained on them."
    )
    return "\n".join(parts)
