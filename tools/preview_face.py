#!/usr/bin/env python3
"""Render short off-screen previews of Nova's face (PNG frames, GIF, MP4)."""
from __future__ import annotations

import math
import os
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "preview"
FACE = ROOT / "nova" / "assets" / "nova-face.jpg"
QML = ROOT / "tools" / "preview_face.qml"


def _stub_qml() -> str:
    return """
import QtQuick 2.15
import QtQuick.Window 2.15
import "../nova/qml" as NovaUi

Window {
    id: win
    objectName: "previewWindow"
    width: 720
    height: 900
    visible: true
    color: "#0E0C0A"
    title: "Nova preview"

    Rectangle {
        anchors.fill: parent
        color: "#0E0C0A"
        radius: 0

        Face {
            id: face
            objectName: "previewFace"
            anchors.fill: parent
            anchors.margins: 24
            cropToHead: true
            previewDemo: true
            trackCursor: false
            expression: "idle"
            mouthOpen: 0
        }
    }
}
""".replace("import \"../nova/qml\" as NovaUi", "").replace("Face {", "/* loaded via qmldir path */ Face {")


def render_qt(out_dir: Path) -> list[Path]:
    os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")
    os.environ.setdefault("QT_QUICK_BACKEND", "software")
    os.environ.setdefault("LIBGL_ALWAYS_SOFTWARE", "1")
    os.environ.setdefault("QT_SCALE_FACTOR", "1")
    os.environ.setdefault("QT_AUTO_SCREEN_SCALE_FACTOR", "0")

    from PySide6.QtCore import QObject, QTimer, QUrl, Property, Qt
    from PySide6.QtGui import QGuiApplication, QImage
    from PySide6.QtQml import QQmlApplicationEngine
    from PySide6.QtQuick import QQuickItem

    app = QGuiApplication.instance() or QGuiApplication(sys.argv)

    class Stub(QObject):
        def __init__(self):
            super().__init__()
            self._face = Path(FACE).resolve().as_uri()

        faceImage = Property(str, lambda self: self._face, constant=True)
        mouthOpen = Property(float, lambda self: 0.0, constant=True)
        expression = Property(str, lambda self: "idle", constant=True)
        listening = Property(bool, lambda self: False, constant=True)
        thinking = Property(bool, lambda self: False, constant=True)
        talking = Property(bool, lambda self: False, constant=True)
        cursorX = Property(int, lambda self: 0, constant=True)
        cursorY = Property(int, lambda self: 0, constant=True)

    qml_path = ROOT / "tools" / "preview_window.qml"
    qml_path.write_text(
        """
import QtQuick 2.15
import QtQuick.Window 2.15

Window {
    id: win
    objectName: "previewWindow"
    width: 720
    height: 900
    visible: true
    color: "#0E0C0A"
    title: "Nova preview"

    Face {
        id: face
        objectName: "previewFace"
        anchors.fill: parent
        anchors.margins: 20
        cropToHead: true
        previewDemo: false
        trackCursor: false
        autoGaze: false
        expression: "idle"
        mouthOpen: 0
    }
}
""".strip()
        + "\n",
        encoding="utf-8",
    )

    engine = QQmlApplicationEngine()
    engine.addImportPath(str(ROOT / "nova" / "qml"))
    # Face.qml is in the same folder we import; also set the import path to that folder
    # and load a wrapper that lives next to Face.qml so the component resolves.
    wrapper = ROOT / "nova" / "qml" / "_preview_window.qml"
    wrapper.write_text(
        """
import QtQuick 2.15
import QtQuick.Window 2.15

Window {
    id: win
    objectName: "previewWindow"
    width: 720
    height: 900
    visible: true
    color: "#0E0C0A"
    title: "Nova preview"

    Face {
        id: face
        objectName: "previewFace"
        anchors.fill: parent
        anchors.margins: 20
        cropToHead: true
        previewDemo: false
        trackCursor: false
        autoGaze: false
        expression: "idle"
        mouthOpen: 0
    }
}
""".strip()
        + "\n",
        encoding="utf-8",
    )

    stub = Stub()
    engine.rootContext().setContextProperty("nova", stub)
    engine.load(QUrl.fromLocalFile(str(wrapper)))
    if not engine.rootObjects():
        raise RuntimeError("QML failed to load the preview window")

    win = engine.rootObjects()[0]
    face = win.findChild(QQuickItem, "previewFace")
    if face is None:
        raise RuntimeError("previewFace not found")

    shots: list[tuple[str, dict]] = [
        ("nova-idle", {"gazeX": 0.08, "gazeY": 0.04, "blink": 0.0, "expression": "idle"}),
        ("nova-look-left", {"gazeX": -0.85, "gazeY": 0.05, "blink": 0.0, "expression": "idle"}),
        ("nova-look-right", {"gazeX": 0.9, "gazeY": -0.05, "blink": 0.0, "expression": "happy"}),
        ("nova-blink", {"gazeX": 0.1, "gazeY": 0.0, "blink": 0.92, "expression": "idle"}),
        ("nova-listening", {"gazeX": 0.15, "gazeY": -0.1, "blink": 0.05, "expression": "listening"}),
    ]

    frames: list[Path] = []

    def grab(name: str) -> Path:
        img: QImage = win.grabWindow()
        path = out_dir / f"{name}.png"
        if img.isNull() or not img.save(str(path)):
            raise RuntimeError(f"grab failed for {name}")
        return path

    # Let the first layout happen.
    app.processEvents()
    QTimer.singleShot(0, lambda: None)
    app.processEvents()

    # Animate a short clip while grabbing frames for a GIF/MP4.
    clip_dir = out_dir / "frames"
    if clip_dir.exists():
        shutil.rmtree(clip_dir)
    clip_dir.mkdir(parents=True, exist_ok=True)

    # Posed stills first.
    def pump(ms=80):
        from PySide6.QtCore import QEventLoop, QTimer
        loop = QEventLoop()
        QTimer.singleShot(ms, loop.quit)
        loop.exec()

    for name, props in shots:
        for key, val in props.items():
            face.setProperty(key, val)
        pump(250)
        frames.append(grab(name))

    # Motion: look left, centre, right, a blink, a little breathe / listen.
    def ease(a, b, t):
        s = t * t * (3 - 2 * t)
        return a + (b - a) * s

    steps = 72  # ~3s at 24fps for the clip (we also write 24fps)
    for i in range(steps):
        t = i / (steps - 1)
        if t < 0.28:
            u = t / 0.28
            gx, gy, bl, expr = ease(0.1, -0.85, u), ease(0.0, 0.08, u), 0.0, "idle"
        elif t < 0.50:
            u = (t - 0.28) / 0.22
            gx, gy, bl, expr = ease(-0.85, 0.9, u), ease(0.08, -0.06, u), 0.0, "idle"
        elif t < 0.62:
            u = (t - 0.50) / 0.12
            gx, gy = 0.9, -0.06
            bl = ease(0.0, 1.0, u) if u < 0.55 else ease(1.0, 0.0, (u - 0.55) / 0.45)
            expr = "idle"
        elif t < 0.82:
            u = (t - 0.62) / 0.20
            gx, gy, bl, expr = ease(0.9, 0.12, u), ease(-0.06, -0.08, u), 0.0, "listening"
        else:
            u = (t - 0.82) / 0.18
            gx, gy, bl, expr = ease(0.12, 0.08, u), ease(-0.08, 0.02, u), 0.0, "happy"
        face.setProperty("gazeX", gx)
        face.setProperty("gazeY", gy)
        face.setProperty("blink", bl)
        face.setProperty("expression", expr)
        app.processEvents()
        path = clip_dir / f"frame-{i:03d}.png"
        img = win.grabWindow()
        img.save(str(path))

    # Clean the private wrapper so it isn't mistaken for app UI.
    try:
        wrapper.unlink()
    except OSError:
        pass
    return frames, clip_dir


def encode_clip(clip_dir: Path, out_dir: Path) -> dict[str, Path]:
    paths = {}
    ffmpeg = shutil.which("ffmpeg")
    if not ffmpeg:
        return paths
    mp4 = out_dir / "nova-eyes.mp4"
    gif = out_dir / "nova-eyes.gif"
    pattern = str(clip_dir / "frame-%03d.png")
    subprocess.check_call(
        [ffmpeg, "-y", "-framerate", "24", "-i", pattern,
         "-pix_fmt", "yuv420p", "-vf", "scale=720:-2", str(mp4)],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    paths["mp4"] = mp4
    palette = out_dir / "_palette.png"
    subprocess.check_call(
        [ffmpeg, "-y", "-framerate", "24", "-i", pattern,
         "-vf", "scale=480:-1:flags=lanczos,palettegen=max_colors=128", str(palette)],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    subprocess.check_call(
        [ffmpeg, "-y", "-framerate", "24", "-i", pattern, "-i", str(palette),
         "-lavfi", "scale=480:-1:flags=lanczos[x];[x][1:v]paletteuse=dither=bayer",
         str(gif)],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    palette.unlink(missing_ok=True)
    paths["gif"] = gif
    return paths


def main() -> int:
    sys.path.insert(0, str(ROOT))
    out = OUT
    out.mkdir(parents=True, exist_ok=True)
    print("Rendering QML off-screen…")
    stills, clip_dir = render_qt(out)
    print("Wrote stills:")
    for p in stills:
        print(" ", p, "bytes", p.stat().st_size)
    encoded = encode_clip(clip_dir, out)
    for k, p in encoded.items():
        print(f"  {k}: {p} ({p.stat().st_size} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
