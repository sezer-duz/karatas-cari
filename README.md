# Karataş Cari Android v1.1.3 — SDK / GitHub Actions düzeltmesi

# Karataş Cari Android v1.1.3 — GitHub Actions SDK düzeltmesi

## GitHub hatası
Gönderilen logda hata `Android SDK kur` aşamasında oluşuyor. `Wrong version in preinstalled sdkmanager` satırı tek başına son hata değil; önceki iş akışındaki `android-actions/setup-android@v3` runner'ın mevcut SDK araçlarını kullanmak yerine farklı komut satırı araçlarını indiriyor. Yeni iş akışı önceden kurulu Android SDK'yı kullanır ve paketleri yalnız gerekirse indirir.

## Ayrıca önemli: GitHub'a eksik dosya yüklenmiş
Kullanıcının görülebilen herkese açık GitHub deposunun ana sayfasında `app/` ve `tools/` klasörleri görünmüyordu. Bunlar olmadan APK oluşturmak mümkün değildir. İlgili klasörler bu tam kaynak paketinin içindedir.

## Nasıl yüklenir?
1. Tam kaynak ZIP'ini bilgisayarda çıkartın.
2. Tercihen GitHub Desktop ile mevcut `karatas-cari` deposunu bilgisayarınıza klonlayın.
3. ZIP içindeki **tüm dosya ve klasörleri** depo köküne kopyalayın; eski dosyalar üzerine yazılabilir.
4. Ana dizinde `.github/`, `app/`, `tools/`, `build.gradle`, `gradle.properties`, `settings.gradle` görünmeli.
5. GitHub Desktop üzerinden commit ve push yapın. Böylece boş dizin veya atlanan Java kaynakları kalmaz.
6. GitHub > Actions > Karatas Cari Android - APK Derleme > Run workflow ile çalıştırın.
7. Derleme başarılı olursa `karatas-cari-android-debug-apk` çıktısından debug APK'yi indirin.

## Yalnız GitHub Actions dosyasını değiştirmek
İsterseniz güncelleme paketindeki `.github/workflows/main.yml` dosyasını önce yükleyebilirsiniz. Ancak `app/` ve `tools/` eksikse bu tek başına derleme sağlamaz. Tam kaynak paketini yükleyin.

## Test sınırı
YAML ayrıştırması, kaynak dosya listesi ve ZIP bütünlüğü kontrol edilir. SDK/Gradle ve fiziksel Android cihaz bulunmadığından bu ortamda gerçek APK build testi yapılmadı. Hata devam ederse başarısız *ilk* adımın logunun **son 20 satırını** paylaşın.

