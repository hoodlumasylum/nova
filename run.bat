@echo off
setlocal
cd /d "%~dp0"
set "PYTHONPATH=%CD%;%PYTHONPATH%"
if exist ".venv\Scripts\pythonw.exe" (
  start "" ".venv\Scripts\pythonw.exe" -m nova %*
  exit /b 0
)
if exist ".venv\Scripts\python.exe" (
  ".venv\Scripts\python.exe" -m nova %*
  exit /b %ERRORLEVEL%
)
echo Nova is not set up yet. Double-click Nova-Setup.exe or setup.bat first.
pause
exit /b 1
