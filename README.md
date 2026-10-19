# Karataş Cari Android — v1.1.4

Bu proje Android Studio kaynak kodlarıdır. WebView ve ML Kit / CameraX yerel barkod tarayıcıyı içerir.

## GitHub'da görülen SDK hatasının çözümü

Önceki GitHub Actions akışındaki `android-actions/setup-android@v3`, Android SDK paket listesinde bulunmayan `tools` paketine başvurduğunda `Failed to find package 'tools'` hatası oluşturuyordu. v1.1.4 iş akışı bu action'ı ÇAĞIRMAZ. GitHub Ubuntu runner'ın kurulu Android SDK'sını kullanır, yalnız eksikse `platforms;android-35` ve `build-tools;35.0.0` kurar.

## GitHub'a yükleme (önemli)

1. `TAM-KAYNAK.zip` dosyasını bilgisayarında aç. ZIP'in kendisini depoya yükleme.
2. Şu klasör ve dosyaları GitHub depo **köküne** kopyala: `.github/workflows/main.yml`, `app/`, `tools/`, `gradle/`, `build.gradle`, `settings.gradle`, `gradle.properties`, `gradlew`, `gradlew.bat` ve belgeler.
3. Varsa eski `.github/workflows/build.yml` dosyasını ve `app/settings.gradle` dosyasını sil.
4. Değişiklikleri `main` dalına commit/push et.
5. GitHub'da `.github/workflows/main.yml` içeriğini aç ve `uses: android-actions/setup-android@v3` satırının **bulunmadığını** doğrula. Yorum satırında `setup-android@v3` ifadesinin bulunması işlem için önemli değildir.
6. **Actions** > **Karatas Cari Android - APK Derleme** > **Run workflow** ile *yeni* bir çalıştırma başlat. Daha önce başlatılmış kırmızı işlem yeni kodu kullanmaz.
7. Derleme başarılıysa **Artifacts** bölümünde `karatas-cari-android-debug-apk` görünür.

## Gereksinimler ve sınırlar

JDK 17, Android SDK 35, Gradle 8.7 ve Android Gradle Plugin 8.6.1 kullanılır. Debug APK test içindir. Uygulamanın bağlandığı HTTPS adresi `app/src/main/java/com/cari/ambalajlar/MainActivity.java` içindeki `SITE` sabitinden belirlenir. Barkod işlemlerinin sunucuyla uyumu için web uygulamasında ilgili `assets/scanner.js` entegrasyonu gerekir.

Bu paketin kaynak yapısı/YAML/ZIP kontrolleri gerçekleştirildi; bu çalışma ortamında Android SDK ve Gradle dağıtımı mevcut olmadığı için gerçek APK derlemesi doğrulanamadı. GitHub Actions hata verirse en alttaki `* What went wrong` bölümünü ve ilk `error:` satırını paylaşın.


## Derleme hatası inceleme

Bu pakette `build/diagnostics/gradle-build.log` dosyasını yükleyen GitHub Actions iş akışı vardır. Başarısız bir derlemede `android-derleme-hata-raporlari` artifact'ini indirip `gradle-build.log` ve `error-summary.txt` dosyalarını inceleyin.
