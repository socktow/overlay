@echo off
set PATH=%PATH%;%cd%\..\libs\lib\win32
set PREV_DIR=%CD%
cd /d "%~dp0"
cd ..
call gradlew runW32
cd %PREV_DIR%