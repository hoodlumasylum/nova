"""Pick a face expression from a tagged model reply, or from simple sentiment."""
from __future__ import annotations

import re

EXPRESSIONS = ("idle", "happy", "thinking", "surprised", "laughing", "listening", "sad")
_TAG = re.compile(r"^\s*\[\[\s*(happy|thinking|surprised|laughing|listening|sad)\s*\]\]\s*", re.I)

# Loose word cues used only when the model did not tag the reply.
_CUES = {
    "laughing": ("haha", "hahaha", "lol", "lmao", "that's hilarious", "cracked me up"),
    "sad": ("i'm sorry", "that's tough", "that's hard", "sad to hear", "condolences"),
    "surprised": ("whoa", "no way", "wait, what", "that's wild", "unbelievable"),
    "happy": ("nice!", "awesome", "love that", "glad", "woo", "let's go", "gg"),
    "thinking": ("let me think", "good question", "hmm", "let's see"),
}


def split_tag(text: str) -> tuple[str, str]:
    """Return (expression, spoken_text). Default expression is idle if none is found."""
    if not text:
        return "idle", ""
    m = _TAG.match(text)
    if m:
        return m.group(1).lower(), text[m.end():].lstrip()
    low = text.lower()
    for name, cues in _CUES.items():
        if any(c in low for c in cues):
            return name, text
    if "?" in text[:120] and len(text) < 280:
        return "thinking", text
    if "!" in text[:80]:
        return "happy", text
    return "idle", text
