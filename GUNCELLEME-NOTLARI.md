# Android v1.1.4 — Derleme logu tanılama düzeltmesi

Bu paket, **uygulama kodunu değiştirmeden** GitHub Actions'ın tam Gradle konsol çıktısını kaydetmesini sağlar. Gönderilen önceki `android-derleme-hata-raporlari.zip` yalnızca `manifest-merger-debug-report.txt` içerdiğinden derlemeyi durduran asıl hata teşhis edilemedi.

- `.github/workflows/main.yml` içinde `:app:assembleDebug` çıktısı `build/diagnostics/gradle-build.log` dosyasına kaydedilir.
- `tools/extract_gradle_error.py`, varsa `* What went wrong:` bölümünü `error-summary.txt` dosyasına çıkarır.
- Build başarısızlığında hata artifact'i artık **iki yeni log dosyasını da** içerir.
- Gradle başarısızsa işlem kırmızı kalır; hatayı gizleyen sahte başarılı çalışma üretilmez.
- Mevcut Android uygulamasının `versionName` değeri **1.1.4** olarak korunur. Barkod okuyucu kodu değişmedi.

## Yükleme

**Mevcut GitHub deposuna yalnız şu dosyaları kopyalayın:**

1. `.github/workflows/main.yml`
2. `tools/extract_gradle_error.py`

`main` dalına commit edin ve yeni bir Actions çalışması başlatın. Başarısızsa **Artifacts > android-derleme-hata-raporlari** dosyasını indirin ve içindeki `gradle-build.log` dosyasını yükleyin. Mevcut rapor ZIP'inde bu dosya bulunmuyor.

## Sınırlama

Burada Android SDK/Gradle ortamı bulunmadığı ve önceki artifact asıl build logunu içermediği için APK'nın neden derlenemediği henüz kesin olarak bilinmiyor. Bu paket esas neden için güvenilir hata toplar; **başarılı APK üretimini garanti etmez**.

---

# Android v1.1.3 → v1.1.4

- `.github/workflows/main.yml`: SDK `tools` paket hatası çıkaran `android-actions/setup-android@v3` adımı kaldırıldı. Runner'ın Android SDK'sı doğrudan kullanılır, yalnız eksik hedef paketler yüklenir.
- `app/build.gradle`: versionCode = 6, versionName = 1.1.4.
- `README.md`: GitHub'a kaynak klasörlerinin açılarak ve depo köküne yüklenmesi ile yeni workflow çalıştırılması adımları açıklandı.

**Depoda app/ veya tools/ yoksa güncelleme paketi tek başına yetmez. TAM-KAYNAK.zip içindeki bütün klasör ve dosyaları yükleyin.**

Gerçek GitHub Actions APK derlemesi henüz bu ortamdan test edilemedi.
