@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"

if not exist "%LOCALAPPDATA%\Nova" mkdir "%LOCALAPPDATA%\Nova"
set "LOG=%LOCALAPPDATA%\Nova\setup.log"
set "LOG2=%~dp0setup.log"

call :log "========================================"
call :log "Nova setup started %DATE% %TIME%"
call :log "Folder: %CD%"

set "PYTHONPATH=%CD%;%PYTHONPATH%"
set "PY="
set "PY_VER=3.12.10"

REM Prefer the Python 3.12 launcher, then any already-installed 3.12, then 3.10+.
call :find_python
if not defined PY (
  call :install_python
  call :find_python
)

if not defined PY (
  call :log "ERROR: Nova needs Python 3.10 or newer, and prefers 3.12."
  echo.
  echo Nova could not find or install Python.
  echo You can install Python 3.12 from https://www.python.org/downloads/windows/
  echo Tick "Add python.exe to PATH", then double-click setup.bat again.
  echo A log is at:
  echo   %LOG%
  pause
  exit /b 1
)

call :log "Using Python: %PY%"
%PY% -c "import sys; print('Python', sys.version)" >>"%LOG%" 2>&1
%PY% -c "import sys; print('Python', sys.version)" >>"%LOG2%" 2>&1

if not exist ".venv\Scripts\python.exe" (
  call :log "Creating a local virtual environment..."
  %PY% -m venv .venv
  if errorlevel 1 (
    call :log "ERROR: Could not create .venv"
    echo Could not create a local environment. Is Python 3.10+ installed?
    echo Log: %LOG%
    pause
    exit /b 1
  )
) else (
  call :log "Virtual environment already exists — keeping it."
)

call :log "Installing Python packages (PySide6)..."
".venv\Scripts\python.exe" -m pip install --upgrade pip >>"%LOG%" 2>&1
if errorlevel 1 (
  call :log "WARNING: pip upgrade failed, continuing"
)
".venv\Scripts\python.exe" -m pip install -r requirements.txt
if errorlevel 1 (
  call :log "ERROR: pip install -r requirements.txt failed"
  echo Could not install Nova's Python packages. Check your network and try again.
  echo Log: %LOG%
  pause
  exit /b 1
)

call :log "Creating Desktop and Start Menu shortcuts..."
".venv\Scripts\python.exe" -c "from pathlib import Path; from nova.install import write_windows_shortcuts; paths = write_windows_shortcuts(Path('.').resolve()); print('Shortcuts:'); [print(' ', p) for p in paths]"
if errorlevel 1 (
  call :log "WARNING: Python shortcut helper failed, trying PowerShell fallback"
  call :shortcuts_fallback
)

call :log "Setup of Python and packages is done. Opening Nova Setup for voices and models..."
echo.
echo Python and Nova's app files are ready.
echo Next, Nova Setup can download her voices and models (a few GB, one time).
echo Shortcuts were added to the Desktop and the Start Menu.
echo Log: %LOG%
echo.

if exist ".venv\Scripts\pythonw.exe" (
  start "" ".venv\Scripts\pythonw.exe" -m nova --setup %*
) else (
  ".venv\Scripts\python.exe" -m nova --setup %*
)
exit /b 0

REM -------------------- helpers --------------------

:log
echo %~1
>>"%LOG%" echo [%DATE% %TIME%] %~1
>>"%LOG2%" echo [%DATE% %TIME%] %~1
goto :eof

:find_python
set "PY="
where py >nul 2>&1 && (
  py -3.12 -c "import sys; raise SystemExit(0 if sys.version_info >= (3, 12) else 1)" >nul 2>&1
  if not errorlevel 1 (
    set "PY=py -3.12"
    goto :eof
  )
)
if exist "%LOCALAPPDATA%\Programs\Python\Python312\python.exe" (
  set "PY=%LOCALAPPDATA%\Programs\Python\Python312\python.exe"
  goto :eof
)
if exist "%LOCALAPPDATA%\Programs\Python\Python312-arm64\python.exe" (
  set "PY=%LOCALAPPDATA%\Programs\Python\Python312-arm64\python.exe"
  goto :eof
)
where py >nul 2>&1 && (
  py -3 -c "import sys; raise SystemExit(0 if sys.version_info >= (3, 10) else 1)" >nul 2>&1
  if not errorlevel 1 (
    set "PY=py -3"
    goto :eof
  )
)
where python >nul 2>&1 && (
  python -c "import sys; raise SystemExit(0 if sys.version_info >= (3, 10) else 1)" >nul 2>&1
  if not errorlevel 1 (
    set "PY=python"
    goto :eof
  )
)
goto :eof

:install_python
call :log "Python 3.12 was not found. Trying to install it for this user..."
where winget >nul 2>&1
if not errorlevel 1 (
  call :log "Installing Python 3.12 with winget (per-user)..."
  winget install -e --id Python.Python.3.12 --scope user --accept-package-agreements --accept-source-agreements --disable-interactivity
  if not errorlevel 1 (
    call :log "winget finished."
    REM The py launcher is often available immediately after a per-user install.
    goto :eof
  )
  call :log "winget did not install Python 3.12. Falling back to the official installer."
) else (
  call :log "winget is not available. Falling back to the official installer."
)

set "INST=%TEMP%\nova-python-3.12.exe"
if /I "%PROCESSOR_ARCHITECTURE%"=="ARM64" (
  set "PY_URL=https://www.python.org/ftp/python/%PY_VER%/python-%PY_VER%-arm64.exe"
) else (
  set "PY_URL=https://www.python.org/ftp/python/%PY_VER%/python-%PY_VER%-amd64.exe"
)
call :log "Downloading %PY_URL%"
powershell -NoProfile -ExecutionPolicy Bypass -Command "try { Invoke-WebRequest -UseBasicParsing -Uri '%PY_URL%' -OutFile '%INST%'; exit 0 } catch { Write-Error $_; exit 1 }"
if errorlevel 1 (
  call :log "ERROR: Download of the Python installer failed."
  goto :eof
)
call :log "Running the official Python installer silently (this user only, with PATH and the py launcher)..."
"%INST%" /quiet InstallAllUsers=0 PrependPath=1 Include_launcher=1 Include_pip=1 Include_test=0 SimpleInstall=1 Shortcuts=0
if errorlevel 1 (
  call :log "ERROR: The official Python installer returned an error."
  goto :eof
)
call :log "Official Python installer finished."
goto :eof

:shortcuts_fallback
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$root = (Resolve-Path -LiteralPath '%CD%').Path; $pyw = Join-Path $root '.venv\Scripts\pythonw.exe'; $setup = Join-Path $root 'setup.bat'; $desk = [Environment]::GetFolderPath('Desktop'); $start = Join-Path $env:APPDATA 'Microsoft\Windows\Start Menu\Programs\Nova'; New-Item -ItemType Directory -Force -Path $start | Out-Null; foreach ($folder in @($desk,$start)) { $s = New-Object -ComObject WScript.Shell; $l = $s.CreateShortcut((Join-Path $folder 'Nova.lnk')); $l.TargetPath = $pyw; $l.Arguments = '-m nova'; $l.WorkingDirectory = $root; $l.Save(); $l2 = $s.CreateShortcut((Join-Path $folder 'Nova Setup.lnk')); $l2.TargetPath = $setup; $l2.WorkingDirectory = $root; $l2.Save() }; Write-Output 'Shortcuts created (PowerShell fallback).'"
goto :eof
