# Android v1.1.3 → v1.1.4

- `.github/workflows/main.yml`: SDK `tools` paket hatası çıkaran `android-actions/setup-android@v3` adımı kaldırıldı. Runner'ın Android SDK'sı doğrudan kullanılır, yalnız eksik hedef paketler yüklenir.
- `app/build.gradle`: versionCode = 6, versionName = 1.1.4.
- `README.md`: GitHub'a kaynak klasörlerinin açılarak ve depo köküne yüklenmesi ile yeni workflow çalıştırılması adımları açıklandı.

**Depoda app/ veya tools/ yoksa güncelleme paketi tek başına yetmez. TAM-KAYNAK.zip içindeki bütün klasör ve dosyaları yükleyin.**

Gerçek GitHub Actions APK derlemesi henüz bu ortamdan test edilemedi.
