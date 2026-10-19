# Karataş Cari Android 1.1.1 — Derleme düzeltmesi

Bu kaynak kod **depo köküne** açılmalıdır. GitHub depo kökünde `.github/`, `app/`, `build.gradle`, `settings.gradle`, `gradle.properties` ve `gradlew` görünmelidir; `karatas-cari-main/` adlı ekstra klasör **olmamalıdır**.

## Düzeltilenler

- Eksik `gradle.properties` eklendi ve AndroidX etkinleştirildi (`android.useAndroidX=true`). AGP 8.6.x için gereklidir.
- Gereksiz `app/settings.gradle` kaldırıldı; yalnız proje kökünde tek `settings.gradle` kaldı.
- Java kaynak/derleme hedefi 17 olarak sabitlendi.
- ZIP'in fazladan dış klasörü kaldırıldı; GitHub Actions `.github/workflows/build.yml` dosyasını doğru konumda bulur.
- CI Gradle 8.7 ve Android SDK 35 kullanacak şekilde düzenlendi; derlenen debug APK Actions > Artifacts altında sunulur.
- Linux/macOS ve Windows için `gradlew` başlatıcı dosyaları eklendi. Resmi Gradle 8.7 Wrapper JAR'i ilk çalıştırmada indirip resmi SHA-256 ile doğrularlar; JAR dosyası bu ZIP içinde yer almaz. `gradle/wrapper/gradle-wrapper.properties` Gradle dağıtım SHA-256 değerini de içerir.

## GitHub'a yükleme

1. Eski depo dosyalarını güncellenmiş kaynakla değiştir. `app/settings.gradle` dosyasını sil.
2. ZIP içindeki **dosya ve klasörleri**, ZIP'i tek dosya olarak değil, GitHub deposunun **kök dizinine** aktar.
3. `.github/workflows/build.yml` dosyasının gerçekten bu yolda bulunduğunu doğrula.
4. GitHub > **Actions** > **Android APK Derleme** > **Run workflow** (veya `push` sonrası tetiklenmesini bekle).
5. Başarılı derleme sonrasında workflow sayfasındaki **Artifacts** bölümünden `karatas-cari-v1.1.1-debug-apk` içindeki APK'yi al.

## Yerel derleme

JDK 17, Android SDK 35 ve internete erişim gereklidir. Linux/macOS:

```sh
./gradlew :app:assembleDebug
```

Windows:

```bat
gradlew.bat :app:assembleDebug
```

Gradle Wrapper'ın resmi JAR'ı ilk çalıştırmada indirilip SHA-256 kontrolünden geçirilir. Android Studio ile açmadan önce bu komutlardan birini bir kez çalıştırmak wrapper JAR'ını yerel klasöre ekler.

## Barkod tarama / sunucu eşleştirmesi

WebView barkod tarayıcı kodu korunmuştur: ML Kit ve CameraX barkodu çözer, güvenilir WebView mesaj kanalıyla web formuna iletir. `app/src/main/java/com/cari/ambalajlar/MainActivity.java` içindeki `SITE` sabiti doğru HTTPS site adresi olmalıdır. Esnaf Cari web uygulaması v1.35.8 veya ilgili `assets/scanner.js` güncellemesini içeren sonraki sürüm gereklidir.

## Doğrulama sınırı

Kaynak dosya/söz dizimi, YAML ve ZIP doğrulamaları yapıldı. Bu çalışma ortamında Android SDK, Gradle dağıtımı ve internete erişim bulunmadığından gerçek `assembleDebug` derlemesi çalıştırılamadı. **Github Actions üzerinde başarılı APK üretimi henüz doğrulanmış değildir.** Hata devam ederse Actions > failed run > `Debug APK derle` adımındaki `* What went wrong` ve ilk `error:` satırlarını paylaş.
