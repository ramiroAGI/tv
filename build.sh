#!/bin/bash
# Compila la app y deja el instalador en salida/Inicio.apk
set -e
cd "$(dirname "$0")"

JAVA_HOME="$(ls -d "/c/Program Files/Microsoft/"jdk-17* | head -1)"
SDK="$LOCALAPPDATA/Android/Sdk"
BT="$SDK/build-tools/34.0.0"
ANDROID_JAR="$SDK/platforms/android-34/android.jar"
export PATH="$JAVA_HOME/bin:$PATH"

rm -rf obj salida
mkdir -p obj/res obj/gen obj/classes salida

echo "1/5 Recursos"
"$BT/aapt2.exe" compile --dir res -o obj/res
"$BT/aapt2.exe" link obj/res/*.flat -I "$ANDROID_JAR" --manifest AndroidManifest.xml \
    --min-sdk-version 23 --target-sdk-version 34 --java obj/gen -o obj/base.apk

echo "2/5 Código"
javac --release 8 -classpath "$ANDROID_JAR" -encoding UTF-8 -nowarn -Xlint:-options \
    -d obj/classes $(find src obj/gen -name '*.java')

echo "3/5 Dex"
"$BT/d8.bat" --release --lib "$ANDROID_JAR" --min-api 23 --output obj $(find obj/classes -name '*.class')

echo "4/5 Empaquetar"
(cd obj && "$BT/aapt.exe" add base.apk classes.dex > /dev/null)
"$BT/zipalign.exe" -f 4 obj/base.apk obj/alineado.apk

echo "5/5 Firmar"
if [ ! -f firma.jks ]; then
    keytool -genkeypair -keystore firma.jks -alias inicio -keyalg RSA -keysize 2048 -validity 10000 \
        -storepass inicio123 -keypass inicio123 -dname "CN=Inicio TV" > /dev/null 2>&1
fi
"$BT/apksigner.bat" sign --ks firma.jks --ks-pass pass:inicio123 --out salida/Inicio.apk obj/alineado.apk

ls -la salida/Inicio.apk
