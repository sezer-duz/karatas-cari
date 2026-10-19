#!/bin/sh
# Minimal Gradle Wrapper bootstrap. The official JAR is downloaded and SHA-256 checked
# at first run because the archived source did not include a wrapper JAR binary.
set -eu
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
EXPECTED="cb0da6751c2b753a16ac168bb354870ebb1e162e9083f116729cec9c781156b8"
checksum() {
  if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1" | cut -d ' ' -f 1;
  elif command -v shasum >/dev/null 2>&1; then shasum -a 256 "$1" | cut -d ' ' -f 1;
  else echo 'SHA-256 araci bulunamadi' >&2; exit 1; fi
}
if [ ! -f "$JAR" ]; then
  echo 'Gradle 8.7 resmi Wrapper JAR dosyasi indiriliyor ve dogrulaniyor...'
  command -v curl >/dev/null 2>&1 || { echo 'curl gerekli' >&2; exit 1; }
  TMP="$JAR.tmp.$$"
  trap 'rm -f "$TMP"' EXIT HUP INT TERM
  curl --fail --location --retry 3 --connect-timeout 20 --output "$TMP" \
    'https://services.gradle.org/distributions/gradle-8.7-wrapper.jar'
  [ "$(checksum "$TMP")" = "$EXPECTED" ] || { echo 'Resmi Gradle Wrapper SHA-256 dogrulamasi BASARISIZ' >&2; exit 1; }
  mv "$TMP" "$JAR"
  trap - EXIT HUP INT TERM
fi
[ "$(checksum "$JAR")" = "$EXPECTED" ] || { echo 'Gradle Wrapper JAR SHA-256 dogrulamasi BASARISIZ' >&2; exit 1; }
if [ -n "${JAVA_HOME:-}" ]; then JAVA="$JAVA_HOME/bin/java"; else JAVA=java; fi
exec "$JAVA" -classpath "$JAR" org.gradle.wrapper.GradleWrapperMain "$@"
