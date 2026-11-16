#!/usr/bin/env bash
# Write Nova and Nova Setup launchers into this folder and ~/.local/share/applications.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
chmod +x "$ROOT/run.sh" "$ROOT/setup.sh"
for name in Nova.desktop Nova-Setup.desktop; do
  src="$ROOT/packaging/$name"
  out="$ROOT/$name"
  sed "s|PLACEHOLDER|$ROOT|g" "$src" > "$out"
  chmod +x "$out"
done
mkdir -p "$HOME/.local/share/applications"
cp "$ROOT/Nova.desktop" "$HOME/.local/share/applications/nova.desktop"
cp "$ROOT/Nova-Setup.desktop" "$HOME/.local/share/applications/nova-setup.desktop"
echo "Desktop launchers written. On Linux Mint, right-click Nova-Setup.desktop and Allow Launching, then open it."
