@echo off
setlocal
cd /d "%~dp0"
set "PYTHONPATH=%CD%;%PYTHONPATH%"

where py >nul 2>&1 && set "PY=py -3"
if not defined PY (
  where python >nul 2>&1 && set "PY=python"
)
if not exist ".venv\Scripts\python.exe" (
  if not defined PY (
    echo Nova needs Python 3.10 or newer.
    echo Install it from https://www.python.org/downloads/windows/
    echo Tick "Add python.exe to PATH", then run this again.
    pause
    exit /b 1
  )
  echo Creating a local virtual environment...
  %PY% -m venv .venv
  if errorlevel 1 (
    echo Could not create .venv. Is Python 3.10+ installed and on PATH?
    pause
    exit /b 1
  )
  ".venv\Scripts\python.exe" -m pip install --upgrade pip
  ".venv\Scripts\python.exe" -m pip install -r requirements.txt
  if errorlevel 1 (
    echo Could not install PySide6. Check the network and try again.
    pause
    exit /b 1
  )
)
".venv\Scripts\python.exe" -m nova --setup %*
