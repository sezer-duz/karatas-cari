# Karataş Cari — iOS 1.0.0

Bu proje mevcut https://cari.ambalajlar.net PHP/MySQL sistemini iPhone/iPad'de WKWebView olarak açar. Stok sayımı dahil sayfalardaki **Barkod Okut** düğmeleri iPhone kamerası ile AVFoundation aracılığıyla çalışır. Personel Mesajları altına Çıkış Yap eklenir; oturum sunucuya mevcut CSRF korumalı POST ile kapatılır.

**iOS 15 veya üstü** gerekir. Uygulamanın Bundle ID'si com.cari.ambalajlar.ios olarak ayarlanmıştır. Apple Developer Portal'da bu kimliğe uygun uygulama kimliği oluşturulmalıdır.

## Derleme

Mac/Xcode ortamında:

1. Xcode ve XcodeGen kurun: https://github.com/yonaskolb/XcodeGen (brew install xcodegen).
2. ios dizininde xcodegen generate --spec project.yml çalıştırın.
3. KaratasCari.xcodeproj dosyasını Xcode'da açın.
4. **Signing & Capabilities** bölümünden Apple Developer takımınızı (Team) seçin; gerekirse Bundle Identifier'ı Apple hesabınızdaki kimlikle uyumlu ayarlayın.
5. iPhone testinde cihazınızı seçip Run ile başlayın. Dağıtım için **Product → Archive → Distribute App** üzerinden Apple'ın imzalama ve dağıtım akışını tamamlayın.

GitHub Actions ios-build.yml, macOS'ta Swift/iOS derlemesini doğrular ve **imzasız** bir IPA üretir. Bu dosya iPhone'a kurulabilir bir dağıtım paketi **değildir**; Apple Development/Distribution sertifikası ve provisioning profile gereklidir. İmzalama belgelerini GitHub deposuna veya mesajlara koymayın.

## Güvenlik ve sınırlamalar

- Uygulama yalnızca https://cari.ambalajlar.net kaynağını kendi WebView'ında açar. Başka bağlantılar Safari/uygun uygulamada açılır.
- Yerel barkod köprüsü yalnız güvenilen HTTPS ana sayfadan çağrılabilir. HTML form ve kullanıcı yetkilendirmesi sunucuda kalır.
- Kullanıcı giriş oturumu ve CSRF kontrolü PHP sunucusunda yönetilir.
- Kamera izni barkod okutma açıldığında istenir.
- Dosya seçimi ve yükleme WKWebView'ın sağladığı yerel yetenekleri kullanır.
- **Arka planda WhatsApp benzeri bildirimler bu ilk iOS sürümünde yoktur.** iOS, Android'deki uzun süreli foreground 15 saniyelik polling servisini desteklemez. Uygulama kapalıyken bildirim almak için **Apple Push Notification service (APNs)** ve uygun Apple Developer entitlements/bildirim sunucusu gerekir. Firebase zorunlu değildir.
- Burada fiziksel iPhone üzerinde gerçek sunucu entegrasyon ve App Store/TestFlight imzalama testi yapılmamıştır.
