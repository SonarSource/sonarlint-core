@if "%DEBUG%"=="" @echo off

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
set APP_HOME=%DIRNAME%..

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

:loop
IF NOT "%1"=="" (
    IF "%1"=="-j" (
        SET JAVA_HOME_ARG=%~2
        SHIFT
    ) ELSE (
        SET args=%args% %1
    )
    SHIFT
    GOTO :loop
)

if defined JAVA_HOME_ARG (
    set JAVA_EXE=%JAVA_HOME_ARG%\bin\java.exe
) else (
    set JAVA_EXE=%APP_HOME%\jre\bin\java.exe
)

set CLASSPATH=%APP_HOME%\lib\*;

@rem Execute SLCORE
"%JAVA_EXE%" ^
   %SONARLINT_JVM_OPTS% ^
   -classpath "%CLASSPATH%" ^
   org.sonarsource.sonarlint.core.backend.cli.SonarLintServerCli

@rem End local scope for the variables with windows NT shell
if %ERRORLEVEL% equ 0 goto mainEnd

:fail
rem Set variable SLCORE_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd.exe /c_ return code!
set EXIT_CODE=%ERRORLEVEL%
if %EXIT_CODE% equ 0 set EXIT_CODE=1
if not ""=="%SLCORE_EXIT_CONSOLE%" exit %EXIT_CODE%
exit /b %EXIT_CODE%

:mainEnd
if "%OS%"=="Windows_NT" endlocal
