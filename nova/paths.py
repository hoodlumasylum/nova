"""Local data paths. Models and voices live under the user data dir, never in the repo."""
from __future__ import annotations

import os
from pathlib import Path


def is_windows() -> bool:
    return os.name == "nt"


def data_dir() -> Path:
    override = os.environ.get("NOVA_DATA_DIR")
    if override:
        return Path(override)
    if is_windows():
        base = os.environ.get("LOCALAPPDATA") or str(Path.home() / "AppData" / "Local")
        return Path(base) / "Nova"
    base = os.environ.get("XDG_DATA_HOME") or str(Path.home() / ".local" / "share")
    return Path(base) / "nova"


def config_dir() -> Path:
    override = os.environ.get("NOVA_CONFIG_DIR")
    if override:
        return Path(override)
    if is_windows():
        base = os.environ.get("APPDATA") or str(Path.home() / "AppData" / "Roaming")
        return Path(base) / "Nova"
    base = os.environ.get("XDG_CONFIG_HOME") or str(Path.home() / ".config")
    return Path(base) / "nova"


def config_file() -> Path:
    return config_dir() / "config.json"


def history_file() -> Path:
    return data_dir() / "chat" / "history.json"


def runtime_dir() -> Path:
    return data_dir() / "runtime"


def models_dir() -> Path:
    return data_dir() / "models"


def voices_dir() -> Path:
    return data_dir() / "voices"


def my_voice_dir() -> Path:
    return data_dir() / "my-voice"


def tmp_dir() -> Path:
    p = data_dir() / "tmp"
    p.mkdir(parents=True, exist_ok=True)
    return p


def memory_file() -> Path:
    return data_dir() / "memory.json"


def assets_dir() -> Path:
    return Path(__file__).resolve().parent / "assets"


def face_image() -> Path:
    return assets_dir() / "nova-face.jpg"


def qml_dir() -> Path:
    return Path(__file__).resolve().parent / "qml"
