@echo off
setlocal
set "SCRIPT_DIR=%~dp0"
set "JAR_PATH=%SCRIPT_DIR%oryxos-boot\target\oryxos-boot-0.1.0-SNAPSHOT.jar"

set "JAVA_CMD=java"
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" (
        set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
    )
)

if exist "%JAR_PATH%" (
    "%JAVA_CMD%" "-Dloader.main=com.oryxos.cli.OryxOsCli" -cp "%JAR_PATH%" org.springframework.boot.loader.launch.PropertiesLauncher %*
) else (
    mvn spring-boot:run -pl oryxos-boot "-Dspring-boot.run.main-class=com.oryxos.cli.OryxOsCli" "-Dspring-boot.run.arguments=%*"
)
endlocal
