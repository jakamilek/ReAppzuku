@echo off
setlocal EnableExtensions DisableDelayedExpansion
set "REAPP_BUILD_EXIT=1"
set "REAPP_JAVA_EXE="
set "REAPP_TEST_CREATED="
set "REAPP_TEST_CLASSES="
pushd "%~dp0"

if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "REAPP_JAVA_EXE=%JAVA_HOME%\bin\java.exe"
if not defined REAPP_JAVA_EXE if exist "%ProgramFiles%\Android\Android Studio\jbr\bin\java.exe" (
    set "JAVA_HOME=%ProgramFiles%\Android\Android Studio\jbr"
    set "REAPP_JAVA_EXE=%ProgramFiles%\Android\Android Studio\jbr\bin\java.exe"
)
if not defined REAPP_JAVA_EXE (
    where java.exe >nul 2>nul
    if errorlevel 1 (
        echo Brak JDK. Zainstaluj Android Studio albo wskaz JDK 17 lub 21 przez JAVA_HOME.
        goto finish
    )
    set "REAPP_JAVA_EXE=java.exe"
)

"%REAPP_JAVA_EXE%" com.sun.tools.javac.Main -version
if errorlevel 1 (
    echo Potrzebny jest JDK z kompilatorem Java, nie samo JRE.
    goto finish
)

set "REAPP_TEST_CLASSES=%TEMP%\reappzuku-tests-%RANDOM%-%RANDOM%"
mkdir "%REAPP_TEST_CLASSES%"
if errorlevel 1 goto finish
set "REAPP_TEST_CREATED=1"
"%REAPP_JAVA_EXE%" com.sun.tools.javac.Main -encoding UTF-8 -d "%REAPP_TEST_CLASSES%" "app\src\main\java\com\gree1d\reappzuku\manager\ForegroundAppDetector.java" "tools\tests\com\gree1d\reappzuku\manager\ForegroundAppDetectorTest.java"
if errorlevel 1 goto finish
"%REAPP_JAVA_EXE%" -ea -cp "%REAPP_TEST_CLASSES%" com.gree1d.reappzuku.manager.ForegroundAppDetectorTest
if errorlevel 1 goto finish

if not defined ANDROID_HOME if defined ANDROID_SDK_ROOT set "ANDROID_HOME=%ANDROID_SDK_ROOT%"
if not defined ANDROID_HOME if exist "%LOCALAPPDATA%\Android\Sdk" set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
if not defined ANDROID_HOME (
    echo Brak Android SDK. W Android Studio otworz SDK Manager i zainstaluj platforme API 36.
    goto finish
)
if not exist "%ANDROID_HOME%\platforms\android-36\android.jar" (
    echo Brak platformy Android API 36 w SDK. Zainstaluj ja w SDK Manager.
    goto finish
)

echo Budowanie wariantu ReAppzuku Fix. Pierwsze uruchomienie pobiera zaleznosci projektu.
call gradlew.bat --no-daemon --console=plain -Dorg.gradle.internal.http.connectionTimeout=30000 -Dorg.gradle.internal.http.socketTimeout=120000 :app:assembleFixed
if errorlevel 1 goto finish
if not exist "app\build\outputs\apk\fixed\app-fixed.apk" (
    echo Kompilacja nie utworzyla oczekiwanego APK.
    goto finish
)
copy /y "app\build\outputs\apk\fixed\app-fixed.apk" "ReAppzuku-Fix-1.8.7.apk" >nul
if errorlevel 1 goto finish
echo Gotowe: ReAppzuku-Fix-1.8.7.apk
certutil -hashfile "ReAppzuku-Fix-1.8.7.apk" SHA256
set "REAPP_BUILD_EXIT=0"

:finish
if defined REAPP_TEST_CREATED if exist "%REAPP_TEST_CLASSES%" rmdir /s /q "%REAPP_TEST_CLASSES%"
if not "%REAPP_BUILD_EXIT%"=="0" echo Nie powstal gotowy APK. Zachowaj komunikat bledu widoczny powyzej.
popd
pause
endlocal & exit /b %REAPP_BUILD_EXIT%
