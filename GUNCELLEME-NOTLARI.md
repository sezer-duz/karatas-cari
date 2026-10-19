# Android v1.1.2 — GitHub Actions main.yml duzeltmesi

**Sorun:** Onceki kaynak ZIP dosyasinda yalniz `.github/workflows/build.yml` bulunuyordu; `.github/workflows/main.yml` yoktu. Ayrica repo yuklenirken olusan bos `main.yml` GitHub Actions derlemesi baslatmiyordu.

**Yapilanlar:**
- `.github/workflows/main.yml` olusturuldu ve gerekli APK derleme adimlari eklendi.
- Eski `build.yml` tam kaynak paketinden kaldirildi; tek bir workflow kaldi.
- Java 17, Android SDK 35, Gradle 8.7 ile `:app:assembleDebug` komutu tanimlandi.
- APK cikti dosyasi `Artifacts` altinda yayinlanacak sekilde eklendi.
- `tools/verify_structure.py` yeni ismi dogruluyor.
- Android uygulama surumu 1.1.2 (versionCode 4) yapildi.

## GitHub'a uygulama

1. **Tam kaynak:** ZIP'in **icindekileri** repo kokune aktar. `.github` gizli klasorunun da yuklendiginden emin ol. Repo kokunde `.github/workflows/main.yml` gorunmeli.
2. **Guncelleme:** ZIP'deki yeni dosyalari ayni yollara kopyala. Eski `.github/workflows/build.yml` dosyasini GitHub'dan **ayrica sil** (aksi halde iki derleme birden baslayabilir).
3. GitHub'da `Actions` bolumunden `Karatas Cari Android - APK Derleme` is akisini ac ve `Run workflow` calistir.
4. Basarili derlemenin APK'si ilgili calismanin `Artifacts` bolumunde `karatas-cari-android-debug-apk` adiyla gorunur.
5. Hata devam ederse ilgili Actions calismasinin `Debug APK olustur` adimindaki hata metnini paylas.

**Uyari:** Bu ortamda Android SDK / Gradle calistirilarak gercek APK derlemesi yapilamadi. YAML yapisi, dosya yollarinin varligi ve ZIP butunlugu dogrulandi; GitHub sunucusunda derleme basarisini garanti edemiyoruz.
