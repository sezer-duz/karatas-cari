@echo off
setlocal
rem First run downloads the official Gradle 8.7 wrapper JAR and verifies SHA-256.
set "APP_HOME=%~dp0"
set "WRAPPER_JAR=%APP_HOME%gradle\wrapper\gradle-wrapper.jar"
set "WRAPPER_SHA=cb0da6751c2b753a16ac168bb354870ebb1e162e9083f116729cec9c781156b8"
if not exist "%WRAPPER_JAR%" (
  echo Resmi Gradle Wrapper JAR indiriliyor...
  powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $target=$env:WRAPPER_JAR; $tmp=$target+'.part'; try { Invoke-WebRequest 'https://services.gradle.org/distributions/gradle-8.7-wrapper.jar' -OutFile $tmp; if ((Get-FileHash $tmp -Algorithm SHA256).Hash.ToLower() -ne $env:WRAPPER_SHA) { throw 'SHA256 dogrulanamadi' }; Move-Item -Force $tmp $target } finally { Remove-Item $tmp -ErrorAction SilentlyContinue }"
  if errorlevel 1 exit /b 1
)
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "if ((Get-FileHash $env:WRAPPER_JAR -Algorithm SHA256).Hash.ToLower() -ne $env:WRAPPER_SHA) { throw 'Gradle Wrapper JAR guvenlik kontrolu basarisiz' }"
if errorlevel 1 exit /b 1
if defined JAVA_HOME (
  set "JAVA_BIN=%JAVA_HOME%\bin\java.exe"
) else (
  set "JAVA_BIN=java.exe"
)
"%JAVA_BIN%" -classpath "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*
exit /b %ERRORLEVEL%
