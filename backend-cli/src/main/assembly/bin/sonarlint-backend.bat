@rem
@rem Copyright 2015 the original author or authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem      https://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.
@rem

@if "%DEBUG%"=="" @echo off
@rem ##########################################################################
@rem
@rem  SLCORE startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
@rem This is normally unused
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%..
@rem set DEFAULT_JVM_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=1044"
set DEFAULT_JVM_OPTS=

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

set args=

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
    echo Using Java from %JAVA_HOME_ARG%
    set JAVA_EXE=%JAVA_HOME_ARG%\bin\java.exe
) else (
    echo Using bundled jre
    set JAVA_EXE=%APP_HOME%\jre\bin\java.exe
)

set CLASSPATH=%APP_HOME%\lib\*;

@rem Execute SLCORE
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% -classpath "%CLASSPATH%" org.sonarsource.sonarlint.core.backend.cli.SonarLintServerCli %args%

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
