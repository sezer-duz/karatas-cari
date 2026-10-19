# Karataş Cari Android — yerel barkod okuma (kaynak kod)

Bu klasör kullanıcının `karatas-cari-main.zip` Android projesinin geliştirilmiş kaynak kodudur.
Google ML Kit **bundled** (`com.google.mlkit:barcode-scanning:17.3.0`) modeli ve CameraX ile EAN-13, EAN-8, UPC-A/E, CODE-128, CODE-39, CODE-93, ITF, QR, Data Matrix, PDF417, Aztec ve Codabar okunur. ML Kit barkod modeli cihaz içinde paketlenir; taramak için Google Play Hizmetleri veya ağdan model indirilmesi gerekmez.

## Sunucu eşlemesi

`app/src/main/java/com/cari/ambalajlar/MainActivity.java` içindeki `SITE` sabitini uygulamanın açacağı **tam HTTPS origin** adresiyle (`https://ornek.com`) eşleştirin. Başka sunucu için APK oluştururken aynı adresin hem ana WebView URL'sinde hem güvenli köprü izin listesinde kullanılması gerekir. Yalnız bu adrese ait üst çerçeve barkod taramayı tetikleyebilir. Herhangi bir alan adına açık köprü kullanılmaz.

Android APK'nin içine web uygulaması paketlenmez: sunucudaki **Esnaf Cari v1.35.8** sürümü ayrıca güncellenmiş olmalıdır; barkod düğmesi `assets/scanner.js` ile haberleşir.

## APK üretimi

Android Studio (Android SDK 35, JDK 17) ile `ZIP içindeki kök proje klasörünü açın ve `Build > Build APK(s)` kullanın. Alternatif: GitHub'a kaynak kodunu yüklerseniz `.github/workflows/build.yml` debug APK'yi Actions artefaktı olarak üretir. Güncel uygulamayı aynı imzayla yükseltmek için önceki APK'nin imza anahtarı gerekir. Farklı imzalı APK üzerine doğrudan kurulum yapılamaz; verileri korumak için eski uygulamayı kaldırmadan önce imza stratejinizi doğrulayın.

## Davranış

1. WebView'de ürün kartı / POS / depo ekranındaki `Barkod Okut` tuşuna basılır.
2. Android ilk kullanımda kamera izni ister.
3. `BarcodeScannerActivity` arka kamerayı CameraX ile açar, ML Kit barkodu çözümleyince tek bir değer döndürür.
4. AndroidX WebKit `WebMessageListener` güvenli mesaj kanalından barkod ilgili web inputuna aktarılır.
5. Depo hazırlama formu tek okutma için sunucuya **tek** POST gönderir. Örneğin 10 adet isteniyorsa kamera tekrar açılarak 10 okutma gerekir; adet kontrolü sunucudadır.

Kullanıcı izin vermezse veya barkod bulunamazsa manuel giriş açık kalır. Kullanıcı kamera ekranını kapatırsa form gönderilmez. Cihazın donanım kamerasının, Android System WebView sürümünün ve izinlerin gerçek cihazda test edilmesi gerekir.

## Güvenlik

Güvenilir origin `WebViewCompat.addWebMessageListener` ile sınırlandırılır; `addJavascriptInterface` kullanılmaz. Mesaj için ayrıca `isMainFrame` ve HTTPS/host kontrolü vardır. İletişim ancak sayfadaki barkod tuşu tıklanınca tetiklenir. URL değişince bekleyen barkod cevabı geçersiz sayılır. WebView'de mixed-content ve cleartext trafik yasaktır.

## Test kısıtı

Bu çalışma ortamında Android SDK/Gradle ve fiziksel Android cihaz mevcut olmadığından APK derlemesi veya cihaz üzerinde kamera taraması **gerçekleştirilmedi**. GitHub Actions iş akışı derlenebilirlik kontrolü amacıyla eklenmiştir, henüz çalıştırılmamıştır.

**Minimum Android sürümü: Android 6.0 (API 23).** Bundled ML Kit Barkod API bu sürümü gerektirir.


**Derleme düzeltmeleri ve güncel yönergeler için proje kökündeki `README.md` dosyasına bakın.**
