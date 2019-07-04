@REM SonarLint Daemon Startup Script for Windows
@REM
@REM Required ENV vars:
@REM   JAVA_HOME - location of a JDK home dir
@REM
@REM Optional ENV vars:
@REM   SONARLINT_DAEMON_OPTS - parameters passed to the Java VM when running SonarLint

@echo off

set ERROR_CODE=0

@REM set local scope for the variables with windows NT shell
@setlocal

set SONARLINT_DAEMON_HOME=%~dp0..


@REM ==== START VALIDATION ====
@REM *** JAVA EXEC VALIDATION ***

set use_embedded_jre=${use_embedded_jre}
if "%use_embedded_jre%" == "true" (
  set JAVA_HOME="%SONARLINT_DAEMON_HOME%\jre"
)

if not "%JAVA_HOME%" == "" goto foundJavaHome

for %%i in (java.exe) do set JAVA_EXEC=%%~$PATH:i

if not "%JAVA_EXEC%" == "" (
  set JAVA_EXEC="%JAVA_EXEC%"
  goto OkJava
)

if not "%JAVA_EXEC%" == "" goto OkJava

echo.
echo ERROR: JAVA_HOME not found in your environment, and no Java
echo        executable present in the PATH.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation, or add "java.exe" to the PATH
echo.
goto error

:foundJavaHome
if EXIST "%JAVA_HOME%\bin\java.exe" goto foundJavaExeFromJavaHome

echo.
echo ERROR: JAVA_HOME exists but does not point to a valid Java home
echo        folder. No "\bin\java.exe" file can be found there.
echo %JAVA_HOME%
echo.
goto error

:foundJavaExeFromJavaHome
set JAVA_EXEC="%JAVA_HOME%\bin\java.exe"

@REM *** SONARLINT HOME VALIDATION ***
:OkJava

@REM Check if the provided SONARLINT_DAEMON_HOME is a valid install dir
IF EXIST "%SONARLINT_DAEMON_HOME%\lib\sonarlint-daemon-${project.version}.jar" goto run

echo.
echo ERROR: SONARLINT_DAEMON_HOME exists but does not point to a valid install
echo        directory: %SONARLINT_DAEMON_HOME%
echo.
goto error



@REM ==== START RUN ====
:run
echo %SONARLINT_DAEMON_HOME%

set PROJECT_HOME=%CD%

%JAVA_EXEC% -Djava.awt.headless=true %SONARLINT_DAEMON_OPTS% -cp "%SONARLINT_DAEMON_HOME%\lib\sonarlint-daemon-${project.version}.jar" "-Dsonarlint.home=%SONARLINT_DAEMON_HOME%" org.sonarlint.daemon.Daemon %*
if ERRORLEVEL 1 goto error
goto end

:error
set ERROR_CODE=1

@REM ==== END EXECUTION ====

:end
@REM set local scope for the variables with windows NT shell
@endlocal & set ERROR_CODE=%ERROR_CODE%

@REM see http://code-bear.com/bearlog/2007/06/01/getting-the-exit-code-from-a-batch-file-that-is-run-from-a-python-program/
goto exit

:returncode
exit /B %1

:exit
call :returncode %ERROR_CODE%
