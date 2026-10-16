@echo off
setlocal
set "PROJECT_DIR=%~dp0"
set "MAVEN_VERSION=3.9.16"
set "MAVEN_BASE=%PROJECT_DIR%.mvn\wrapper\dists"
set "MAVEN_HOME=%MAVEN_BASE%\apache-maven-%MAVEN_VERSION%"
set "MAVEN_ZIP=%MAVEN_BASE%\apache-maven-%MAVEN_VERSION%-bin.zip"
set "MAVEN_URL=https://dlcdn.apache.org/maven/maven-3/%MAVEN_VERSION%/binaries/apache-maven-%MAVEN_VERSION%-bin.zip"

if not exist "%MAVEN_HOME%\bin\mvn.cmd" (
    if not exist "%MAVEN_BASE%" mkdir "%MAVEN_BASE%"
    if not exist "%MAVEN_ZIP%" (
        echo Downloading Apache Maven %MAVEN_VERSION% for this project...
        powershell -NoProfile -Command "$ErrorActionPreference='Stop'; Invoke-WebRequest -UseBasicParsing -Uri '%MAVEN_URL%' -OutFile '%MAVEN_ZIP%'"
        if errorlevel 1 exit /b 1
    )
    powershell -NoProfile -Command "$ErrorActionPreference='Stop'; Expand-Archive -LiteralPath '%MAVEN_ZIP%' -DestinationPath '%MAVEN_BASE%' -Force"
    if errorlevel 1 exit /b 1
)

if not exist "%MAVEN_HOME%\bin\mvn.cmd" (
    echo Maven was not found after extracting the configured distribution.
    exit /b 1
)

echo Using project-local Apache Maven %MAVEN_VERSION%.
if not exist "%PROJECT_DIR%.mvn\settings.xml" (
    echo Project Maven settings file is missing: "%PROJECT_DIR%.mvn\settings.xml"
    exit /b 1
)
echo Using project Maven settings: "%PROJECT_DIR%.mvn\settings.xml"
set "MAVEN_ARGS="
"%MAVEN_HOME%\bin\mvn.cmd" --global-settings "%PROJECT_DIR%.mvn\settings.xml" --settings "%PROJECT_DIR%.mvn\settings.xml" -Dmaven.repo.local="%PROJECT_DIR%.m2\repository" %*
exit /b %ERRORLEVEL%
