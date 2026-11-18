"""Nova's Linux desktop app: corner bubble, full-screen chat, video prompts, settings."""
from __future__ import annotations

import argparse
import os
import sys
from pathlib import Path

from . import __version__, paths


def _ensure_qt_plugins() -> None:
    # Some venvs need a hint so Qt can find its platform plugin.
    try:
        import PySide6
        root = Path(PySide6.__file__).resolve().parent
        plug = root / "Qt" / "plugins" or root / "qt6" / "plugins"
        for cand in (root / "Qt" / "plugins", root / "qt6" / "plugins"):
            if cand.is_dir():
                os.environ.setdefault("QT_PLUGIN_PATH", str(cand))
                break
    except Exception:
        pass


def _parse(argv: list[str] | None) -> argparse.Namespace:
    p = argparse.ArgumentParser(prog="nova", description="Nova — local talking assistant")
    p.add_argument("--setup", action="store_true", help="Open the GUI installer / updater")
    p.add_argument("--version", action="version", version=f"Nova {__version__}")
    return p.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = _parse(argv)
    _ensure_qt_plugins()
    # Must be set before QGuiApplication for a transparent bubble.
    os.environ.setdefault("QT_QUICK_CONTROLS_STYLE", "Fusion")

    from PySide6.QtCore import QTimer, Qt, QUrl
    from PySide6.QtGui import QAction, QGuiApplication, QIcon, QImage, QPixmap
    from PySide6.QtQml import QQmlApplicationEngine
    from PySide6.QtWidgets import QApplication, QMenu, QSystemTrayIcon

    from .backend import Backend
    from .hotkey import GlobalHotkey, wayland_session
    from . import install

    qapp = QApplication.instance() or QApplication(sys.argv)
    qapp.setApplicationName("Nova")
    qapp.setApplicationVersion(__version__)
    qapp.setQuitOnLastWindowClosed(False)

    backend = Backend()
    if args.setup:
        backend.setPage("setup")
        backend.setExpanded(True)

    engine = QQmlApplicationEngine()
    engine.rootContext().setContextProperty("nova", backend)
    qml = paths.qml_dir() / "App.qml"
    engine.load(QUrl.fromLocalFile(str(qml)))
    if not engine.rootObjects():
        print("Nova failed to load its window (App.qml).", file=sys.stderr)
        return 1

    from PySide6.QtCore import QObject as _QO

    def _named(name: str):
        for root in engine.rootObjects():
            if str(root.objectName() or "") == name:
                return root
            found = root.findChild(_QO, name)
            if found is not None:
                return found
        return None

    _root = engine.rootObjects()[0]
    bubble = _named("bubbleWindow")
    main_win = _named("mainWindow")
    # Keep Python references so the windows are never garbage-collected.
    qapp._nova_windows = (_root, bubble, main_win)

    BUBBLE = 176
    MARGIN = 14

    def screen_geo():
        screen = qapp.primaryScreen()
        return screen.availableGeometry() if screen else None

    def place_bubble() -> None:
        if bubble is None:
            return
        geo = screen_geo()
        if geo is None:
            return
        x = backend._cfg.get("bubble_x")
        y = backend._cfg.get("bubble_y")
        if isinstance(x, int) and isinstance(y, int) and x >= 0 and y >= 0:
            bubble.setX(x)
            bubble.setY(y)
            return
        corner = backend._cfg.get("corner") or "bottom-right"
        if corner == "top-left":
            bx, by = geo.x() + MARGIN, geo.y() + MARGIN
        elif corner == "top-right":
            bx, by = geo.x() + geo.width() - BUBBLE - MARGIN, geo.y() + MARGIN
        elif corner == "bottom-left":
            bx, by = geo.x() + MARGIN, geo.y() + geo.height() - BUBBLE - MARGIN
        else:
            bx, by = geo.x() + geo.width() - BUBBLE - MARGIN, geo.y() + geo.height() - BUBBLE - MARGIN
        bubble.setX(int(bx))
        bubble.setY(int(by))

    def apply_flags() -> None:
        if bubble is None:
            return
        # Click-through only on the bubble, and only when it is visible.
        try:
            bubble.setFlag(Qt.WindowType.WindowTransparentForInput, bool(backend._cfg.get("click_through")) and not backend._expanded)
        except Exception:
            pass

    def apply_visibility() -> None:
        if backend._expanded:
            if bubble is not None:
                bubble.hide()
            if main_win is not None:
                main_win.show()
                try:
                    main_win.showFullScreen()
                    main_win.raise_()
                    main_win.requestActivate()
                except Exception:
                    pass
        else:
            if main_win is not None:
                main_win.hide()
            if bubble is not None:
                place_bubble()
                apply_flags()
                bubble.show()
                bubble.raise_()

    backend.expandedChanged.connect(apply_visibility)
    backend.configChanged.connect(lambda: (place_bubble(), apply_flags()))

    # Tray
    icon = QIcon()
    face = paths.face_image()
    if face.is_file():
        pix = QPixmap(str(face))
        if not pix.isNull():
            icon = QIcon(pix.copy(pix.width() * 18 // 100, pix.height() * 2 // 100, pix.width() * 66 // 100, pix.height() * 66 // 100))
    tray = QSystemTrayIcon(icon if not icon.isNull() else qapp.windowIcon(), qapp)
    tray.setToolTip("Nova")
    menu = QMenu()
    act_toggle = QAction("Expand / shrink", menu)
    act_toggle.triggered.connect(backend.toggleExpanded)
    menu.addAction(act_toggle)
    act_chat = QAction("Chat", menu)
    act_chat.triggered.connect(lambda: backend.setPage("chat"))
    menu.addAction(act_chat)
    act_video = QAction("Video prompt", menu)
    act_video.triggered.connect(lambda: backend.setPage("video"))
    menu.addAction(act_video)
    act_mem = QAction("Memory", menu)
    act_mem.triggered.connect(lambda: backend.setPage("settings"))
    menu.addAction(act_mem)
    act_setup = QAction("Setup / update", menu)
    act_setup.triggered.connect(lambda: backend.setPage("setup"))
    menu.addAction(act_setup)
    menu.addSeparator()
    act_click = QAction("Click-through bubble", menu)
    act_click.setCheckable(True)
    act_click.setChecked(bool(backend._cfg.get("click_through")))
    act_click.toggled.connect(lambda v: backend._set_click_through(v) if False else setattr(backend, "clickThrough", v))
    menu.addAction(act_click)
    menu.addSeparator()
    act_quit = QAction("Quit Nova", menu)
    act_quit.triggered.connect(qapp.quit)
    menu.addAction(act_quit)
    tray.setContextMenu(menu)
    tray.activated.connect(lambda reason: backend.toggleExpanded() if reason == QSystemTrayIcon.Trigger else None)
    tray.show()

    def ptt_fired() -> None:
        # If the bubble is click-through or a game is focused, the hotkey still works.
        if not backend._expanded:
            backend.setExpanded(True)
        if backend._page == "chat":
            backend.startTalk()
        else:
            backend.setPage("chat")

    grab = GlobalHotkey(backend._cfg.get("ptt") or "Ctrl+Alt+N", lambda: QTimer.singleShot(0, ptt_fired))
    if not grab.start() and grab.error:
        backend.set_hotkey_warning(grab.error)
    elif wayland_session():
        backend.set_hotkey_warning(
            "This is a Wayland session, so a system-wide hotkey may not work. "
            "Use the bubble, the tray icon, or a shortcut inside Nova's window."
        )

    QTimer.singleShot(0, apply_visibility)

    # First-run: if setup is needed and they didn't already open it, stay on the bubble
    # but leave a status so expanding shows Setup.
    if backend._setup_needed and args.setup is False:
        backend._set_status("Setup isn't finished yet. Open Setup from the tray or the full window to download models.")

    code = qapp.exec()
    grab.stop()
    try:
        from . import engine as eng
        eng.unload(backend._cfg)
    except Exception:
        pass
    return int(code)


# Re-export for python -m nova
if __name__ == "__main__":
    raise SystemExit(main())
