# SP COLLECTOR GACHA OPTIMIZER

Aplikasi Android (Kotlin, Jetpack Compose, Material 3, Room, ML Kit OCR) untuk menganalisis riwayat gacha
Sage Path Convergence. Aplikasi pihak ketiga: tidak menyentuh APK/server/token game, tidak ada login,
semua data lokal dan fitur utama berjalan offline.

## Build
1. Buka folder ini di Android Studio (Ladybug atau lebih baru, JDK 17). Biarkan Gradle sync.
   Jika diminta membuat Gradle wrapper, terima. Versi plugin ada di `build.gradle.kts` dan boleh dinaikkan.
2. Jalankan unit test: `./gradlew testDebugUnitTest` (mesin probabilitas, Monte Carlo, statistik, parser).
3. APK debug: `./gradlew assembleDebug` -> `app/build/outputs/apk/debug/app-debug.apk`.
   Untuk APK rilis, buat keystore lalu pakai Build > Generate Signed Bundle / APK.
4. Pasang ke ponsel (Android 8.0+): `adb install app-debug.apk`.

## Cara pakai
1. **Settings**: isi parameter dari informasi resmi game. Semua kosong secara default. Tanpa
   drop rate SP dan peluang target, kalkulator menampilkan CONFIGURATION REQUIRED (tidak ada angka tebakan).
   - Drop rate SP = peluang rarity SP per pull. Peluang target = P(karakter target | rarity SP). Keduanya berbeda.
   - Guarantee: jika aktif, setelah SP non-target, SP berikutnya pasti target.
2. **Tracker**: tambah karakter (tandai target) dan banner. Nama karakter yang sudah ada dipakai untuk fuzzy match OCR.
3. **Resources**: isi diamond, tiket, pity (pull sejak SP terakhir) dan status guarantee.
4. **Scanner**: pilih screenshot -> **OCR Verification**: periksa/koreksi nama, rarity, nomor pull -> konfirmasi.
   Baris yang tidak terbaca ditandai UNKNOWN. Nomor pull hanya diisi bila tertulis eksplisit (#12 / No.12).
5. **Calculator / Monte Carlo / RNG Analyzer / Recommendation** memakai parameter di atas.

## Model
- Analitik: rantai Markov state (pity, guaranteed). Tanpa pity hasilnya sama dengan 1-(1-p)^n (diuji).
- Monte Carlo: 10rb-1jt simulasi paralel, divalidasi terhadap CDF analitik (toleransi 5 sigma).
- RNG Analyzer: CI Wilson, chi-square (INSUFFICIENT DATA bila n < 30 atau expected < 5).
- Rekomendasi: hanya pity, guarantee, resource, sisa durasi; tidak pernah karena hasil buruk sebelumnya.

## Batasan
- Akurasi OCR bergantung tata letak screenshot; parser berbasis baris + fuzzy match, selalu perlu verifikasi.
- Duplikat: hash SHA-256 screenshot (identik persis) dan kunci banner|nomor|nama|rarity bila ada nomor pull.
  Screenshot tumpang tindih tanpa nomor pull bisa menghasilkan duplikat yang perlu dihapus manual.
- Grafik memakai Canvas Compose (bukan Vico) untuk menghindari ketergantungan API yang sering berubah.
