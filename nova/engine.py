"""Talk to a local Ollama server. After setup, Nova stays offline — no accounts or keys."""
from __future__ import annotations

import json
import os
import socket
import subprocess
import time
import urllib.error
import urllib.request
from pathlib import Path

from . import paths
from .config import personality_prompt

DEFAULT_MODEL = "llama3.2:3b"
LOW_POWER_MODEL = "qwen2.5:1.5b"
OLLAMA_HOST = "127.0.0.1"
OLLAMA_PORT = 11435  # own port so we never collide with CodeAque's 11434
IDLE_UNLOAD_SEC = 8 * 60


def model_for(cfg: dict) -> str:
    if (cfg.get("model") or "").strip():
        return cfg["model"].strip()
    return LOW_POWER_MODEL if cfg.get("low_power") else DEFAULT_MODEL


def thread_cap(cfg: dict) -> int | None:
    n = int(cfg.get("num_threads") or 0)
    if n > 0:
        return n
    cpu = os.cpu_count() or 4
    if cfg.get("low_power"):
        return max(1, min(2, cpu // 2 or 1))
    return None


def ollama_bin() -> Path:
    name = "ollama.exe" if os.name == "nt" else "ollama"
    return paths.runtime_dir() / "ollama" / "bin" / name


def base_url() -> str:
    return f"http://{OLLAMA_HOST}:{OLLAMA_PORT}"


def _env(cfg: dict) -> dict:
    env = os.environ.copy()
    env["OLLAMA_HOST"] = f"{OLLAMA_HOST}:{OLLAMA_PORT}"
    env["OLLAMA_MODELS"] = str(paths.models_dir())
    cap = thread_cap(cfg)
    if cap:
        env["OLLAMA_NUM_THREADS"] = str(cap)
        env["OLLAMA_NUM_PARALLEL"] = "1"
    return env


def is_up() -> bool:
    try:
        with socket.create_connection((OLLAMA_HOST, OLLAMA_PORT), timeout=0.4):
            return True
    except OSError:
        return False


def start_server(cfg: dict) -> subprocess.Popen | None:
    exe = ollama_bin()
    if not exe.is_file():
        return None
    if is_up():
        return None
    paths.models_dir().mkdir(parents=True, exist_ok=True)
    log = (paths.data_dir() / "ollama.log").open("ab")
    return subprocess.Popen(
        [str(exe), "serve"],
        env=_env(cfg),
        stdout=log,
        stderr=subprocess.STDOUT,
        start_new_session=True,
    )


def wait_up(timeout: float = 20.0) -> bool:
    deadline = time.time() + timeout
    while time.time() < deadline:
        if is_up():
            return True
        time.sleep(0.2)
    return False


def unload(cfg: dict, model: str | None = None) -> None:
    """Free the model from RAM when Nova has been idle."""
    name = model or model_for(cfg)
    try:
        _post("/api/generate", {"model": name, "keep_alive": 0, "prompt": ""}, timeout=8)
    except Exception:
        pass


def _post(path: str, body: dict, timeout: float = 180) -> dict:
    raw = json.dumps(body).encode("utf-8")
    req = urllib.request.Request(
        base_url() + path,
        data=raw,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8"))


def has_model(name: str) -> bool:
    try:
        req = urllib.request.Request(base_url() + "/api/tags")
        with urllib.request.urlopen(req, timeout=8) as resp:
            data = json.loads(resp.read().decode("utf-8"))
        for m in data.get("models") or []:
            if (m.get("name") or "").split(":")[0] == name.split(":")[0] and (
                ":" not in name or name in (m.get("name") or "") or name + "-" in (m.get("name") or "")
            ):
                return True
            if m.get("name") == name or (m.get("name") or "").startswith(name):
                return True
    except Exception:
        return False
    return False


def _ensure_server(cfg: dict) -> str:
    if not is_up():
        start_server(cfg)
        if not wait_up():
            raise RuntimeError("Nova's local model server did not start. Open Setup to install it.")
    return model_for(cfg)


def _chat_payload(cfg: dict, payload_messages: list[dict]) -> str:
    name = model_for(cfg)
    body = {
        "model": name,
        "messages": payload_messages,
        "stream": False,
        "keep_alive": "10m",
        "options": {},
    }
    cap = thread_cap(cfg)
    if cap:
        body["options"]["num_thread"] = cap
    try:
        data = _post("/api/chat", body, timeout=240)
    except urllib.error.URLError as e:
        raise RuntimeError("Could not reach the local model. Is setup finished?") from e
    return ((data.get("message") or {}).get("content") or "").strip()


def chat(cfg: dict, messages: list[dict], on_token=None, system_extra: str = "") -> str:
    """Send a chat turn. Returns the assistant text (may include an [[expression]] tag)."""
    _ensure_server(cfg)
    system = personality_prompt(cfg)
    if system_extra:
        system = system + "\n" + system_extra.strip()
    payload_messages = [{"role": "system", "content": system}]
    for m in messages[-24:]:
        role = m.get("role")
        if role in ("user", "assistant") and m.get("content"):
            payload_messages.append({"role": role, "content": str(m["content"])})
    return _chat_payload(cfg, payload_messages)


def complete(cfg: dict, system: str, user: str, system_extra: str = "") -> str:
    """One-shot reply for modes such as Video prompt. Still local-only."""
    _ensure_server(cfg)
    sys_text = system.strip()
    if system_extra:
        sys_text = sys_text + "\n" + system_extra.strip()
    return _chat_payload(
        cfg,
        [
            {"role": "system", "content": sys_text},
            {"role": "user", "content": user},
        ],
    )
