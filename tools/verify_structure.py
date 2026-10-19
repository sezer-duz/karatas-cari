#!/usr/bin/env python3
"""No-SDK structural validation for GitHub source uploads."""
from pathlib import Path
from xml.etree import ElementTree
root = Path(__file__).resolve().parents[1]
required = [
    '.github/workflows/main.yml', 'build.gradle', 'settings.gradle', 'gradle.properties',
    'app/build.gradle', 'app/src/main/AndroidManifest.xml',
    'app/src/main/java/com/cari/ambalajlar/MainActivity.java',
    'app/src/main/java/com/cari/ambalajlar/BarcodeScannerActivity.java',
    'gradle/wrapper/gradle-wrapper.properties', 'gradlew', 'gradlew.bat',
]
for f in required:
    assert (root / f).is_file(), f'Eksik dosya: {f}'
assert not (root / 'app/settings.gradle').exists(), 'app/settings.gradle kaldirilmali'
assert not (root / '.github/workflows/build.yml').exists(), 'Eski build.yml dosyasini silin'
assert (root / '.github/workflows/main.yml').stat().st_size > 400, 'main.yml bos'
assert 'android.useAndroidX=true' in (root / 'gradle.properties').read_text(), 'AndroidX kapali'
assert "include ':app'" in (root / 'settings.gradle').read_text(), 'app projesi tanimsiz'
ElementTree.parse(root / 'app/src/main/AndroidManifest.xml')
print('Dosya yapisi, AndroidX ve AndroidManifest kontrolleri: OK')
