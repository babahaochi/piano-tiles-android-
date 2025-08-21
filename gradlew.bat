@echo off
rem Gradle wrapper script for Windows

set DIR=%~dp0
if "%DIR%"=="" set DIR=.

set GRADLE_HOME=%DIR%gradle\wrapper\gradle-wrapper.properties
if not exist "%GRADLE_HOME%" (
    echo "Gradle wrapper properties file not found."
    exit /b 1
)

set WRAPPER_JAR=%DIR%gradle\wrapper\gradle-wrapper.jar
if not exist "%WRAPPER_JAR%" (
    echo "Gradle wrapper jar file not found."
    exit /b 1
)

java -jar "%WRAPPER_JAR%" %*