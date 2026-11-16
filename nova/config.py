"""Nova settings, stored locally. No accounts or keys."""
from __future__ import annotations

import json
from pathlib import Path

from . import paths

# Personality presets feed the system prompt. The user can also write a short description.
PERSONALITY_PRESETS = {
    "nova": {
        "title": "Nova (default)",
        "blurb": "Fun, warm, gamer banter. Kind and supportive. Honest that she is an AI.",
    },
    "hype": {
        "title": "Hype buddy",
        "blurb": "High energy co-op teammate. Cheers wins, trash-talks games (never the player), keeps things moving.",
    },
    "chill": {
        "title": "Chill",
        "blurb": "Low-key, calm, and easy to talk to. Short answers, soft humour, no fuss.",
    },
    "coach": {
        "title": "Coach",
        "blurb": "Patient and encouraging. Helps you think through a game or a CodeAque problem one step at a time.",
    },
}

DEFAULTS = {
    "corner": "bottom-right",          # top-left, top-right, bottom-left, bottom-right
    "bubble_x": None,                  # if set, last free-drag position; snap still offered
    "bubble_y": None,
    "click_through": False,
    "voice": "codeaque",
    "wake_word": False,                # "Hey Nova", off by default
    "low_power": False,
    "ptt": "Ctrl+Alt+N",
    "pad_combo": "Select+Y",
    "personality": "nova",
    "personality_text": "",
    "num_threads": 0,                  # 0 = auto; low-power caps this
    "my_voice_consent": False,
    "model": "",                       # blank = default or low-power default
    "speed_note_shown": False,
}


def load() -> dict:
    dest = paths.config_file()
    data = dict(DEFAULTS)
    try:
        if dest.is_file():
            raw = json.loads(dest.read_text(encoding="utf-8"))
            if isinstance(raw, dict):
                for k, v in raw.items():
                    if k in DEFAULTS:
                        data[k] = v
    except (OSError, ValueError):
        pass
    return data


def save(data: dict) -> None:
    dest = paths.config_file()
    dest.parent.mkdir(parents=True, exist_ok=True)
    out = dict(DEFAULTS)
    out.update({k: data[k] for k in DEFAULTS if k in data})
    tmp = dest.with_suffix(".json.tmp")
    tmp.write_text(json.dumps(out, indent=2), encoding="utf-8")
    tmp.replace(dest)


def personality_prompt(cfg: dict) -> str:
    key = cfg.get("personality") or "nova"
    preset = PERSONALITY_PRESETS.get(key) or PERSONALITY_PRESETS["nova"]
    custom = (cfg.get("personality_text") or "").strip()
    text = (
        "You are Nova, a standalone talking assistant on the user's Linux PC. "
        "You are a small on-device AI, not a person and not a cloud service — say so honestly if asked, "
        "and never pretend you have a body, memories of real life, or access to the internet after setup. "
        "You can be wrong; if you are unsure, say so. "
        "Be kind and supportive. Help with games, PCs, and CodeAque when asked. "
        "Never ask for or store passwords, keys, or logins. "
        "Never suggest piracy, ROM sites, or unlicensed copies. "
        "Never offer to clone or imitate another person's voice.\n"
        f"Personality preset ({preset['title']}): {preset['blurb']}\n"
    )
    if custom:
        text += "The user also described how they want you to sound: " + custom[:500] + "\n"
    text += (
        "Keep replies conversational and not too long. "
        "You may start a reply with one expression tag on its own first line, "
        "one of: [[happy]] [[thinking]] [[surprised]] [[laughing]] [[listening]] [[sad]], "
        "then the words you want spoken. If none fits, omit the tag.\n"
        "You may also append [[remember: a short fact]] at the end of a reply (not spoken) "
        "when the user clearly shares a lasting fact about themselves — their name, games they play, "
        "or things they like. Never store passwords, keys, or other people's private information. "
        "This only updates a local notes file on this PC. It is not training or retraining a model. "
        "If they want something forgotten, tell them they can view, edit, or forget it in Settings → Memory.\n"
        "When they ask for a video prompt, write paste-ready image-to-video text only. "
        "Never claim you called Seedance or any online API."
    )
    return text
