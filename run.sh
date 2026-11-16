#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
export PYTHONPATH="$ROOT${PYTHONPATH:+:$PYTHONPATH}"
cd "$ROOT"

pick_python() {
  if [[ -x "$ROOT/.venv/bin/python" ]]; then
    echo "$ROOT/.venv/bin/python"
    return
  fi
  # Dev box fallback only — the installer creates $ROOT/.venv for real use.
  if [[ -x /workspace/codeaque-test-venv/bin/python ]] && /workspace/codeaque-test-venv/bin/python -c "import PySide6" 2>/dev/null; then
    echo /workspace/codeaque-test-venv/bin/python
    return
  fi
  if command -v python3 >/dev/null; then
    echo python3
    return
  fi
  echo "Nova needs Python 3. Run setup.sh first." >&2
  exit 1
}

PY="$(pick_python)"
if ! "$PY" -c "import PySide6" 2>/dev/null; then
  echo "PySide6 is not installed in this Python. Run setup.sh (Nova Setup) first — no terminal commands needed after that." >&2
  exit 1
fi
exec "$PY" -m nova "$@"
