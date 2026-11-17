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
@rem  Gradle startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem Resolve Java
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto process

echo.
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.
exit /b 1

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto process

echo.
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.
exit /b 1

:process
set WRAPPER_JAR=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

if not exist "%WRAPPER_JAR%" (
    echo [Gradle Wrapper] Downloading gradle-wrapper.jar...
    powershell -NoProfile -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; (New-Object System.Net.WebClient).DownloadFile('https://services.gradle.org/distributions/gradle-8.7-bin.zip', '%TEMP%\gradle-8.7-bin.zip')" 2>nul
    where gradle >nul 2>&1
    if %ERRORLEVEL% equ 0 (
        gradle %*
        exit /b %ERRORLEVEL%
    )
)

if exist "%WRAPPER_JAR%" (
    "%JAVA_EXE%" -Dorg.gradle.appname=%APP_BASE_NAME% -classpath "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*
    exit /b %ERRORLEVEL%
)

where gradle >nul 2>&1
if %ERRORLEVEL% equ 0 (
    gradle %*
    exit /b %ERRORLEVEL%
)

echo [ERROR] Neither gradle-wrapper.jar nor system gradle found. Please install Gradle 8.7+ or run: gradle wrap
exit /b 1
