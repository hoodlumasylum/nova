"""GUI installer / updater. Downloads official runtimes and models into ~/.local/share/nova/. Nothing is bundled in the repo."""
from __future__ import annotations

import io
import os
import platform
import shutil
import subprocess
import sys
import tarfile
import zipfile
import tempfile
import time
import urllib.request
from pathlib import Path

from . import paths
from . import __version__
from .engine import DEFAULT_MODEL, LOW_POWER_MODEL, ollama_bin, start_server, wait_up, _env, has_model, model_for
from .speech import VOICES, piper_bin, whisper_bin, whisper_model, voice_files

# Official published archives — always fetched at install time, never shipped in git.
# Keys are (os, arch) with arch normalized to x86_64 or arm64.
OLLAMA_URL = {
    ("linux", "x86_64"): "https://ollama.com/download/ollama-linux-amd64.tar.zst",
    ("linux", "arm64"): "https://ollama.com/download/ollama-linux-arm64.tar.zst",
    ("windows", "x86_64"): "https://github.com/ollama/ollama/releases/latest/download/ollama-windows-amd64.zip",
    ("windows", "arm64"): "https://github.com/ollama/ollama/releases/latest/download/ollama-windows-arm64.zip",
}
PIPER_URL = {
    ("linux", "x86_64"): "https://github.com/rhasspy/piper/releases/download/2023.11.14-2/piper_linux_x86_64.tar.gz",
    ("linux", "arm64"): "https://github.com/rhasspy/piper/releases/download/2023.11.14-2/piper_linux_aarch64.tar.gz",
    ("windows", "x86_64"): "https://github.com/rhasspy/piper/releases/download/2023.11.14-2/piper_windows_amd64.zip",
}
WHISPER_URL = {
    ("linux", "x86_64"): "https://github.com/ggml-org/whisper.cpp/releases/download/v1.9.5/whisper-bin-ubuntu-x64.tar.gz",
    ("linux", "arm64"): "https://github.com/ggml-org/whisper.cpp/releases/download/v1.9.5/whisper-bin-ubuntu-arm64.tar.gz",
    ("windows", "x86_64"): "https://github.com/ggml-org/whisper.cpp/releases/download/v1.9.5/whisper-bin-x64.zip",
}
WHISPER_MODEL_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.en.bin"

# Official Piper voice files on Hugging Face (rhasspy/piper-voices).
VOICE_URLS = {
    "codeaque": (
        "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/amy/medium/en_US-amy-medium.onnx",
        "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/amy/medium/en_US-amy-medium.onnx.json",
    ),
    "lessac": (
        "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/lessac/medium/en_US-lessac-medium.onnx",
        "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/lessac/medium/en_US-lessac-medium.onnx.json",
    ),
    "hfc_female": (
        "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/hfc_female/medium/en_US-hfc_female-medium.onnx",
        "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/hfc_female/medium/en_US-hfc_female-medium.onnx.json",
    ),
    "alba": (
        "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_GB/alba/medium/en_GB-alba-medium.onnx",
        "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_GB/alba/medium/en_GB-alba-medium.onnx.json",
    ),
    "jenny": (
        "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_GB/jenny_dioco/medium/en_GB-jenny_dioco-medium.onnx",
        "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_GB/jenny_dioco/medium/en_GB-jenny_dioco-medium.onnx.json",
    ),
}

# Rough sizes for the progress UI and the honest "this will take a bit" copy.
OLLAMA_SIZE_HINT = 1.5 * 1024**3
MODEL_SIZE_HINT = {
    DEFAULT_MODEL: 2.0 * 1024**3,
    LOW_POWER_MODEL: 1.0 * 1024**3,
}


def arch() -> str:
    a = platform.machine().lower()
    if a in ("x86_64", "amd64", "x64"):
        return "x86_64"
    if a in ("aarch64", "arm64"):
        return "arm64"
    return a


def os_key() -> str:
    return "windows" if os.name == "nt" else "linux"


def _url_for(table: dict, what: str) -> str:
    key = (os_key(), arch())
    if key not in table:
        raise RuntimeError(
            f"No official {what} build for this system ({os_key()} {arch()}). "
            "Nova's installer supports 64-bit Linux (x86_64 and arm64) and 64-bit Windows (x86_64)."
        )
    return table[key]


def _archive_name(url: str, stem: str) -> str:
    if url.endswith(".zip"):
        return stem + ".zip"
    if url.endswith(".tar.gz"):
        return stem + ".tar.gz"
    return stem + ".tar.zst"


class Progress:
    def __init__(self, emit):
        self.emit = emit or (lambda *_a, **_k: None)

    def status(self, text: str, fraction: float | None = None) -> None:
        self.emit(text, fraction)


def download(url: str, dest: Path, progress: Progress, label: str, size_hint: float | None = None) -> Path:
    dest.parent.mkdir(parents=True, exist_ok=True)
    tmp = dest.with_suffix(dest.suffix + ".part")
    req = urllib.request.Request(url, headers={"User-Agent": f"Nova/{__version__}"})
    with urllib.request.urlopen(req, timeout=60) as resp:
        total = int(resp.headers.get("Content-Length") or 0) or int(size_hint or 0)
        got = 0
        t0 = time.time()
        with tmp.open("wb") as out:
            while True:
                chunk = resp.read(1024 * 256)
                if not chunk:
                    break
                out.write(chunk)
                got += len(chunk)
                if total:
                    frac = min(0.99, got / total)
                    speed = got / max(0.2, time.time() - t0)
                    progress.status(
                        f"{label}: { _fmt(got) } / { _fmt(total) }  ({_fmt(speed)}/s)",
                        frac,
                    )
                else:
                    progress.status(f"{label}: { _fmt(got) } downloaded", None)
    tmp.replace(dest)
    progress.status(f"{label}: done ({_fmt(dest.stat().st_size)})", 1.0)
    return dest


def _fmt(n: float) -> str:
    for unit, size in (("GB", 1024**3), ("MB", 1024**2), ("KB", 1024)):
        if n >= size:
            return f"{n / size:.1f} {unit}"
    return f"{int(n)} B"


def _extract_tar(archive: Path, dest: Path) -> None:
    dest.mkdir(parents=True, exist_ok=True)
    if archive.name.endswith(".zip"):
        with zipfile.ZipFile(archive) as zf:
            zf.extractall(dest)
        return
    if archive.suffix == ".zst" or archive.name.endswith(".tar.zst"):
        _extract_tar_zst(archive, dest)
        return
    with tarfile.open(archive, "r:*") as tf:
        tf.extractall(dest)


def _extract_tar_zst(archive: Path, dest: Path) -> None:
    dest.mkdir(parents=True, exist_ok=True)
    # Official Ollama packages are .tar.zst. Prefer the zstd tool, then Python's zstandard.
    if shutil.which("tar") and (shutil.which("zstd") or shutil.which("unzstd")):
        subprocess.run(["tar", "--zstd", "-xf", str(archive), "-C", str(dest)], check=True)
        return
    try:
        import zstandard  # type: ignore
    except ImportError:
        # Last resort: try pip-less stdlib isn't enough. Ask the user clearly.
        raise RuntimeError(
            "Need zstd (or the Python package 'zstandard') to unpack Ollama's official .tar.zst. "
            "Install the 'zstd' package from your distro, then run Setup again."
        )
    dctx = zstandard.ZstdDecompressor()
    with archive.open("rb") as fh, dctx.stream_reader(fh) as reader:
        with tarfile.open(fileobj=reader, mode="r|") as tf:  # type: ignore[arg-type]
            tf.extractall(dest)


def _find_exe(root: Path, names: tuple[str, ...]) -> Path | None:
    for dirpath, _dirs, files in os.walk(root):
        lower = {f.lower(): f for f in files}
        for n in names:
            real = lower.get(n.lower())
            if not real:
                continue
            p = Path(dirpath) / real
            if p.is_file() and (os.name == "nt" or os.access(p, os.X_OK)):
                return p
    return None


def ensure_ollama(progress: Progress, force: bool = False) -> Path:
    exe = ollama_bin()
    if exe.is_file() and not force:
        progress.status("Ollama is already installed.", 1.0)
        return exe
    url = _url_for(OLLAMA_URL, "Ollama")
    kind = "Windows" if os.name == "nt" else "Linux"
    progress.status(f"Downloading Ollama (official {kind} build, about 1.5 GB — includes GPU libraries)…", 0.0)
    archive = paths.tmp_dir() / _archive_name(url, "ollama")
    download(url, archive, progress, "Ollama", OLLAMA_SIZE_HINT)
    dest = paths.runtime_dir() / "ollama"
    if dest.exists():
        shutil.rmtree(dest)
    dest.mkdir(parents=True, exist_ok=True)
    progress.status("Unpacking Ollama…", None)
    _extract_tar(archive, dest)
    bin_name = "ollama.exe" if os.name == "nt" else "ollama"
    found = _find_exe(dest, (bin_name, "ollama.exe", "ollama"))
    if not found:
        raise RuntimeError("The Ollama archive unpacked, but the ollama binary was not in it.")
    # Keep a stable path at runtime/ollama/bin/ollama[.exe]
    target = dest / "bin" / bin_name
    if found.resolve() != target.resolve():
        target.parent.mkdir(parents=True, exist_ok=True)
        if not target.exists():
            try:
                target.symlink_to(found)
            except OSError:
                shutil.copy2(found, target)
    if os.name != "nt":
        target.chmod(0o755)
    try:
        archive.unlink()
    except OSError:
        pass
    progress.status("Ollama installed.", 1.0)
    return target


def ensure_piper(progress: Progress, force: bool = False) -> Path:
    exe = piper_bin()
    if exe.is_file() and not force:
        progress.status("Piper is already installed.", 1.0)
        return exe
    url = _url_for(PIPER_URL, "Piper")
    progress.status("Downloading Piper (official speech voice engine)…", 0.0)
    archive = paths.tmp_dir() / _archive_name(url, "piper")
    download(url, archive, progress, "Piper")
    dest = paths.runtime_dir() / "piper"
    if dest.exists():
        shutil.rmtree(dest)
    staging = paths.tmp_dir() / "piper-extract"
    if staging.exists():
        shutil.rmtree(staging)
    _extract_tar(archive, staging)
    found = _find_exe(staging, ("piper.exe", "piper"))
    if not found:
        raise RuntimeError("The Piper archive unpacked, but the piper binary was not in it.")
    # The tarball usually contains a piper/ folder with the binary and its libs.
    src = found.parent
    shutil.copytree(src, dest)
    exe = dest / "piper"
    exe.chmod(0o755)
    try:
        archive.unlink()
        shutil.rmtree(staging)
    except OSError:
        pass
    progress.status("Piper installed.", 1.0)
    return exe


def ensure_whisper(progress: Progress, force: bool = False) -> Path:
    exe = whisper_bin()
    model = whisper_model()
    if exe.is_file() and model.is_file() and not force:
        progress.status("Speech recognition is already installed.", 1.0)
        return exe
    if not exe.is_file() or force:
        url = _url_for(WHISPER_URL, "whisper.cpp")
        progress.status("Downloading whisper.cpp (official speech recognition build)…", 0.0)
        archive = paths.tmp_dir() / _archive_name(url, "whisper")
        download(url, archive, progress, "whisper.cpp")
        dest = paths.runtime_dir() / "whisper"
        if dest.exists():
            shutil.rmtree(dest)
        staging = paths.tmp_dir() / "whisper-extract"
        if staging.exists():
            shutil.rmtree(staging)
        _extract_tar(archive, staging)
        found = _find_exe(staging, ("whisper-cli.exe", "whisper-cli", "main.exe", "main", "whisper.exe", "whisper"))
        if not found:
            raise RuntimeError("The whisper.cpp archive unpacked, but no whisper-cli binary was in it.")
        dest.mkdir(parents=True, exist_ok=True)
        # Copy the folder that holds the binary (libs may sit next to it).
        for item in found.parent.iterdir():
            target = dest / item.name
            if item.is_dir():
                shutil.copytree(item, target, dirs_exist_ok=True)
            else:
                shutil.copy2(item, target)
        exe = dest / found.name
        if not exe.is_file():
            shutil.copy2(found, exe)
        exe.chmod(0o755)
        try:
            archive.unlink()
            shutil.rmtree(staging)
        except OSError:
            pass
    if not model.is_file() or force:
        progress.status("Downloading the small English speech model (whisper base.en)…", 0.0)
        download(WHISPER_MODEL_URL, model, progress, "whisper model")
    progress.status("Speech recognition installed.", 1.0)
    return exe


def ensure_voice(key: str, progress: Progress, force: bool = False) -> None:
    if key == "myvoice":
        return
    if voice_files(key) and not force:
        progress.status(f"Voice '{key}' is already installed.", 1.0)
        return
    urls = VOICE_URLS.get(key)
    meta = VOICES.get(key)
    if not urls or not meta:
        raise RuntimeError(f"Unknown voice: {key}")
    onnx = paths.voices_dir() / meta["onnx"]
    js = paths.voices_dir() / meta["json"]
    progress.status(f"Downloading voice: {meta['title']}…", 0.0)
    download(urls[0], onnx, progress, meta["title"])
    download(urls[1], js, progress, meta["title"] + " config")


def ensure_model(cfg: dict, progress: Progress, name: str | None = None) -> None:
    from . import engine
    exe = ollama_bin()
    if not exe.is_file():
        raise RuntimeError("Install Ollama first.")
    model = name or model_for(cfg)
    engine.start_server(cfg)
    if not engine.wait_up(30):
        raise RuntimeError("Ollama started but did not come up in time.")
    if engine.has_model(model):
        progress.status(f"Model {model} is already on this PC.", 1.0)
        return
    progress.status(
        f"Downloading {model} — this is the local brain, a few GB, and it stays on this PC. "
        "Replies will be slower on a laptop or handheld than on a gaming desktop.",
        0.0,
    )
    env = engine._env(cfg)
    proc = subprocess.Popen(
        [str(exe), "pull", model],
        env=env,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        bufsize=1,
    )
    assert proc.stdout is not None
    for line in proc.stdout:
        line = line.strip()
        if not line:
            continue
        # ollama prints progress lines like "pulling sha256:  47%"
        frac = None
        if "%" in line:
            try:
                frac = int(line.split("%")[0].split()[-1]) / 100.0
            except ValueError:
                frac = None
        progress.status(f"{model}: {line[:120]}", frac)
    rc = proc.wait()
    if rc != 0:
        raise RuntimeError(f"Ollama could not download {model}. Check the network and try again.")
    progress.status(f"Model {model} is ready.", 1.0)


def _write_windows_shortcut(dest: Path, target: Path, workdir: Path) -> None:
    dest.parent.mkdir(parents=True, exist_ok=True)
    def q(s: str) -> str:
        return s.replace("'", "''")
    script = (
        "$s = New-Object -ComObject WScript.Shell; "
        f"$l = $s.CreateShortcut('{q(str(dest))}'); "
        f"$l.TargetPath = '{q(str(target))}'; "
        f"$l.WorkingDirectory = '{q(str(workdir))}'; "
        "$l.Save()"
    )
    subprocess.run(
        ["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", script],
        check=True,
        timeout=30,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.PIPE,
    )


def write_windows_shortcuts(app_root: Path) -> list[Path]:
    """Desktop + Start Menu shortcuts to Nova.exe (or run.bat) and Setup."""
    app_root = Path(app_root).resolve()
    nova = app_root / "Nova.exe"
    setup = app_root / "Nova-Setup.exe"
    if not nova.is_file():
        nova = app_root / "run.bat"
    if not setup.is_file():
        setup = app_root / "setup.bat"
    desktop = Path.home() / "Desktop"
    start = Path(os.environ.get("APPDATA") or (Path.home() / "AppData" / "Roaming")) / "Microsoft" / "Windows" / "Start Menu" / "Programs" / "Nova"
    out: list[Path] = []
    for folder in (desktop, start):
        for name, target in (("Nova.lnk", nova), ("Nova Setup.lnk", setup)):
            dest = folder / name
            _write_windows_shortcut(dest, target, app_root)
            out.append(dest)
    return out


def write_desktop_entries(app_root: Path) -> list[Path]:
    """Install desktop / Start Menu launchers for this OS."""
    if os.name == "nt":
        return write_windows_shortcuts(app_root)
    apps = Path.home() / ".local" / "share" / "applications"
    apps.mkdir(parents=True, exist_ok=True)
    run = app_root / "run.sh"
    setup = app_root / "setup.sh"
    out = []
    nova = apps / "nova.desktop"
    nova.write_text(
        "\n".join(
            [
                "[Desktop Entry]",
                "Type=Application",
                "Name=Nova",
                "Comment=Offline talking assistant — corner bubble, chat, and voice",
                f"Exec={run}",
                "Terminal=false",
                "Categories=Utility;",
                "StartupNotify=true",
                "",
            ]
        ),
        encoding="utf-8",
    )
    nova.chmod(0o644)
    out.append(nova)
    inst = apps / "nova-setup.desktop"
    inst.write_text(
        "\n".join(
            [
                "[Desktop Entry]",
                "Type=Application",
                "Name=Nova Setup",
                "Comment=Install or update Nova's local voices and models",
                f"Exec={setup}",
                "Terminal=false",
                "Categories=Utility;",
                "",
            ]
        ),
        encoding="utf-8",
    )
    inst.chmod(0o644)
    out.append(inst)
    return out


def setup_needed(cfg: dict) -> list[str]:
    missing = []
    if not ollama_bin().is_file():
        missing.append("Ollama (local model server)")
    if not piper_bin().is_file():
        missing.append("Piper (speech out)")
    if not whisper_bin().is_file() or not whisper_model().is_file():
        missing.append("whisper.cpp (speech in)")
    if not voice_files("codeaque"):
        missing.append("CodeAque voice")
    # Model check only if ollama is up or the blobs dir exists
    blobs = paths.models_dir()
    if not any(blobs.rglob("*") if blobs.is_dir() else []):
        missing.append(f"A local model ({model_for(cfg)})")
    return missing


def preflight_notes() -> list[str]:
    notes = []
    # Memory
    mem = 0
    try:
        for line in Path("/proc/meminfo").read_text().splitlines():
            if line.startswith("MemTotal:"):
                mem = int(line.split()[1]) * 1024
                break
    except (OSError, ValueError):
        pass
    if mem and mem < 6 * 1024**3:
        notes.append(
            "This PC has under 6 GB of RAM. Pick Low power in Settings (1.5B model, fewer CPU threads). "
            "The default 3B model may not fit."
        )
    elif mem and mem < 10 * 1024**3:
        notes.append(
            "This PC has under 10 GB of RAM. The default 3B model may be tight if other apps are open. "
            "Low power (1.5B) is the safer pick."
        )
    # Disk
    try:
        free = shutil.disk_usage(Path.home()).free
        if free < 8 * 1024**3:
            notes.append(
                f"Only about {_fmt(free)} free on this home disk. Setup needs roughly 6–8 GB "
                "(Ollama ~1.5 GB, a 3B model ~2 GB, voices and speech a few hundred MB)."
            )
    except OSError:
        pass
    notes.append(
        "After setup, Nova stays on this PC and does not need an account or an internet key. "
        "Replies on a laptop or handheld will be slower than a big desktop — that's normal."
    )
    return notes


def run_full_setup(cfg: dict, progress: Progress, voices: list[str] | None = None, force: bool = False) -> None:
    paths.data_dir().mkdir(parents=True, exist_ok=True)
    ensure_ollama(progress, force=force)
    ensure_piper(progress, force=force)
    ensure_whisper(progress, force=force)
    for key in voices or ["codeaque"]:
        if key != "myvoice":
            ensure_voice(key, progress, force=force)
    ensure_model(cfg, progress)
    progress.status("Nova is ready. You can close Setup and start chatting.", 1.0)
