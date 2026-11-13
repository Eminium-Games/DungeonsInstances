@echo off
setlocal EnableDelayedExpansion
REM ============================================================
REM  Serveur de test local Paper 1.20.1 + plugin DungeonInstances
REM  Usage : double-clic ou .\run-test-server.bat
REM  - build le plugin en Java 17
REM  - telecharge paper.jar si manquant
REM  - copie le jar dans test-server\plugins
REM  - lance le serveur sur localhost:25565
REM ============================================================

set "ROOT=%~dp0"
cd /d "%ROOT%"

REM --- 1. Forcer Java 17 si present a l'emplacement connu ---
if exist "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot\bin\java.exe" (
    set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
    set "PATH=!JAVA_HOME!\bin;!PATH!"
)

java -version 2>&1 | findstr /C:"17\." >nul
if errorlevel 1 (
    echo [WARN] Java 17 non detecte. Le serveur 1.20.1 exige Java 17.
    echo        JAVA_HOME actuel : %JAVA_HOME%
    java -version
)

REM --- 2. Maven (auto-install si absent) ---
set "MVN_CMD=mvn"
where mvn >nul 2>&1
if errorlevel 1 (
    if exist "test-server\.tools\maven\apache-maven-3.9.9\bin\mvn.cmd" (
        set "MVN_CMD=test-server\.tools\maven\apache-maven-3.9.9\bin\mvn.cmd"
        echo [INFO] Maven local detecte : !MVN_CMD!
    ) else (
        echo [INFO] Maven introuvable, telechargement de Maven 3.9.9 en local...
        if not exist "test-server\.tools\maven" mkdir "test-server\.tools\maven"
        powershell -Command "Invoke-WebRequest -Uri 'https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.zip' -OutFile 'test-server\.tools\maven.zip'"
        if errorlevel 1 (
            echo [ERREUR] Echec du telechargement de Maven.
            echo Installez Maven 3.9+ manuellement : https://maven.apache.org/download.cgi
            pause
            exit /b 1
        )
        powershell -Command "Expand-Archive -Path 'test-server\.tools\maven.zip' -DestinationPath 'test-server\.tools\maven' -Force"
        del "test-server\.tools\maven.zip"
        set "MVN_CMD=test-server\.tools\maven\apache-maven-3.9.9\bin\mvn.cmd"
    )
)

REM --- 3. Telecharger Paper 1.20.1 (build 196) si manquant ---
if not exist "test-server\paper.jar" (
    echo [INFO] Telechargement de Paper 1.20.1 build 196...
    powershell -Command "Invoke-WebRequest -Uri 'https://fill-data.papermc.io/v1/objects/234a9b32098100c6fc116664d64e36ccdb58b5b649af0f80bcccb08b0255eaea/paper-1.20.1-196.jar' -OutFile 'test-server\paper.jar'"
    if errorlevel 1 (
        echo [ERREUR] Echec du telechargement de paper.jar
        pause
        exit /b 1
    )
)

REM --- 4. Build du plugin ---
echo [INFO] Build du plugin avec Java 17...
call "%MVN_CMD%" clean package -DskipTests
if errorlevel 1 (
    echo [ERREUR] Le build a echoue.
    pause
    exit /b 1
)

REM --- 5. Copie vers test-server\plugins ---
if not exist "test-server\plugins" mkdir "test-server\plugins"
del /Q "test-server\plugins\smp-position-saver-*.jar" 2>nul
del /Q "test-server\plugins\DungeonInstances*.jar" 2>nul
for %%F in (target\*.jar) do (
    if not "%%~nxF"=="original-*.jar" (
        copy /Y "%%F" "test-server\plugins\" >nul
    )
)
echo [INFO] Plugin copie dans test-server\plugins :
dir /B "test-server\plugins\*.jar"

REM --- 6. Lancement serveur ---
echo.
echo ============================================================
echo  Serveur de test : localhost:25565 (version 1.20.1)
echo  Client : lancez Minecraft 1.20.1, ajoutez le serveur
echo           "localhost:25565", mode hors-ligne accepte
echo  Comptes de test deja OP : Fourty3000 (client 1), ItsRealNocturne (client 2)
echo  Arret propre : tapez 'stop' dans cette console
echo ============================================================
echo.
cd /d "%ROOT%test-server"
java -Xms1G -Xmx2G -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch -jar paper.jar nogui
