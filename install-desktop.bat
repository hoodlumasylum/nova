@echo off
setlocal
cd /d "%~dp0"
set "PYTHONPATH=%CD%;%PYTHONPATH%"
if exist ".venv\Scripts\python.exe" (
  ".venv\Scripts\python.exe" -c "from pathlib import Path; from nova.install import write_windows_shortcuts; write_windows_shortcuts(Path('.').resolve()); print('Shortcuts created on the Desktop and Start Menu.')"
  goto :done
)
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$root = (Resolve-Path -LiteralPath '%CD%').Path; $nova = Join-Path $root 'Nova.exe'; if (-not (Test-Path $nova)) { $nova = Join-Path $root 'run.bat' }; $setup = Join-Path $root 'Nova-Setup.exe'; if (-not (Test-Path $setup)) { $setup = Join-Path $root 'setup.bat' }; $desk = [Environment]::GetFolderPath('Desktop'); $start = Join-Path $env:APPDATA 'Microsoft\Windows\Start Menu\Programs\Nova'; New-Item -ItemType Directory -Force -Path $start | Out-Null; foreach ($pair in @(@('Nova.lnk',$nova), @('Nova Setup.lnk',$setup))) { foreach ($folder in @($desk,$start)) { $s = New-Object -ComObject WScript.Shell; $l = $s.CreateShortcut((Join-Path $folder $pair[0])); $l.TargetPath = $pair[1]; $l.WorkingDirectory = $root; $l.Save() } }; Write-Output 'Shortcuts created on the Desktop and Start Menu.'"
:done
echo On Windows 11, if Windows blocks the .exe, use setup.bat / run.bat, or right-click the exe and choose Unblock.
pause
