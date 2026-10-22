# BatitongTV

Aplikasi pemutar siaran televisi modern untuk **Android TV & STB** yang dibangun dengan **AndroidX Media3**, parser playlist komprehensif (menutup seluruh gap format M3U/JSON), antarmuka 3-panel Material 3 Dark khusus TV, dan arsitektur pengamanan playlist berbayar berlapis.

---

## 1. Ringkasan Perubahan & Fitur

### A. Fondasi & Toolchain Terkunci
- **Namespace & ID Aplikasi:** `id.batitong.batitongtv`.
- **Target Platform:** Android 15 (API 35), `targetSdk = 35`, `compileSdk = 35`, `minSdk = 23`, JDK 17.
- **Player Engine:** Migrasi penuh ke **AndroidX Media3 1.5.1** (`media3-exoplayer`, `media3-exoplayer-dash`, `media3-exoplayer-hls`, `media3-exoplayer-rtmp`, `media3-ui`, `media3-datasource-okhttp`).
- **UI Architecture:** Kotlin murni + XML Layout klasik + **ViewBinding saja** (`dataBinding` dinonaktifkan).
- **Native Security Bridge:** Modul C++ (CMake 3.22.1) untuk penyimpanan rahasia terpecah dan ekstraksi hash sertifikat APK.

### B. Parser Playlist Generasi Baru (Menutup Celah G1–G10)
- **G1 (#EXTHTTP):** Parsing JSON terstruktur, kanonisasi header ke Title-Case, dan pencatatan warning tanpa crash bila JSON rusak.
- **G2 (#KODIPROP):** Parsing lengkap seluruh properti kodi (`inputstream.adaptive.*`, dll.) dengan prefix `kodiprop:`.
- **G3 (#EXTVLCOPT):** Case-insensitive untuk `http-user-agent`, `http-referrer`, `http-referer` (satu r), dan `http-origin`.
- **G4 (#EXTINF):** Ekstraksi regex generik atribut `([A-Za-z0-9_-]+)="([^"]*)"`, penanganan `NO NAME`, `UNCATEGORIZED`, dan penomoran otomatis duplikat (`#1`, `#2`).
- **G5 (Dialek Pipe & stream_headers):** Mendukung dialek lama `url|k=v|k=v` dan Kodi `url|K=V&K2=V2`; mendukung `stream_headers` dengan pemisah `&` maupun `|` (dialek DensTV); decoding URL; hierarki prioritas header eksplisit.
- **G6 (Anti-Bocor State Machine):** Menggunakan akumulator penampung tag sebelum dan sesudah `#EXTINF`; di-commit dan di-reset saat baris URL ditemukan sehingga mencegah kebocoran antar-saluran.
- **G7 (Normalizer Lisensi 4 Varian):** Mendukung ClearKey multi-key, Base64/Base64URL ke hex, inline JWK JSON, dan URL server lisensi dengan pipe headers.
- **G8 (Normalisasi DRM):** Pemetaan UUID standar Media3 (`C.CLEARKEY_UUID`, `C.WIDEVINE_UUID`, `C.PLAYREADY_UUID`).
- **G9 (Header Arbitrer):** Seluruh header HTTP disimpan di `Channel.headers` dan diteruskan utuh ke player.
- **G10 (TLS Aman):** Bypass SSL warisan dan trust-all manager dihapus total; digantikan trust manager resmi sistem dan certificate pinning first-party.

### C. Arsitektur Pengamanan Playlist Berbayar (§7)
- **C1 (Playlist Gateway):** Cloudflare Worker (`gateway/worker.js`) tanpa dependensi eksternal. Satu-satunya entitas yang me-resolve shortlink server-side. Menerbitkan session token JWT (≤ 10 menit), session key acak AES-256, dan URL stream bertanda tangan HMAC (≤ 30 menit). Dokumen playlist dienkripsi AES-GCM.
- **C2 (Nol Rahasia Plaintext di Klien):** Endpoint gateway terenkripsi dengan kunci dipecah di C++ JNI. Dokumen playlist hanya didekripsi di RAM. Satu-satunya salinan persisten adalah cache ciphertext AES-GCM (Android Keystore) dengan TTL 24 jam untuk mode offline.
- **C3 (Gerbang Integritas):** Hash sertifikat penandatangan dihitung dari native C++. Mendukung Play Integrity dan jalur degradasi **Android Key Attestation** hardware-backed untuk STB/TV Box non-GMS.
- **C4 (Anti-Sniffing Tanpa Pemblokiran VPN):** Pengguna tidak diblokir karena VPN. Menggunakan SPKI Certificate Pinning (aktif + cadangan) pada host first-party.
- **C5 (Sinyal Risiko):** Indikator root, debugger, dan Frida dikirim sebagai sinyal diagnostik ke gateway tanpa crash lokal.
- **C6 (Minimalisasi Permukaan UI):** Tidak ada WebView, tidak ada opsi salin URL, dan pesan error generik tanpa rincian teknis/token.

### D. Tampilan Modern Android TV (§8)
- Struktur 3-panel: **Panel Kategori (kiri) + Panel Daftar Saluran (tengah) + Panel Info/Preview Saluran (kanan)**.
- OSD Pemutar Video Transparan dengan kontrol lengkap (play/pause, prev/next, rasio aspek 5 mode, audio track, PiP) dan auto-hide 5 detik.
- Indikator focus state D-Pad yang tegas (border aksen hijau `#00E599` + elevasi) konsisten di semua layar.

---

## 2. Pemetaan Status Fitur

| Fitur | Status | Keterangan Pelaksana |
|---|---|---|
| Favorit Pin/Unpin | Dipertahankan | Tahan tombol OK / Enter pada remote TV (`ChannelAdapter` ↔ `MainViewModel`) |
| Kategori Favorit Teratas | Dipertahankan | Selalu ditempatkan pada indeks 0 (`MainViewModel.rebuildCategoriesWithFavorites`) |
| Putar Saluran Terakhir | Dipertahankan | Memutar saluran terakhir saat startup (`PreferenceManager` ↔ `MainActivity`) |
| Otomatis Buka Saat Booting | Dipertahankan | Ditangani oleh `BootReceiver` via `ACTION_BOOT_COMPLETED` |
| Pencarian Saluran | Dipertahankan | Dialog pencarian cepat Material 3 (`SearchDialogFragment`) |
| Pengurutan Saluran | Dipertahankan | Pengurutan default playlist atau alfabetis A-Z (`SettingsDialogFragment`) |
| Track Selector Audio/Subtitle | Dipertahankan | Dialog pemilihan trek audio dinamis (`TrackSelectorDialogFragment`) |
| 5 Mode Aspek Rasio | Dipertahankan | FIT, FILL, ZOOM, FIXED_WIDTH, FIXED_HEIGHT (`ResizeMode` ↔ `Media3PlayerManager`) |
| Picture-in-Picture (PiP) | Dipertahankan | Didukung lewat tombol OSD dan otomatis saat tekan tombol Home (`onUserLeaveHint`) |
| Auto-Retry Playback | Dipertahankan | Percobaan ulang otomatis hingga 3 kali saat terjadi gangguan streaming |
| Dukungan RTMP & DRM | Dipertahankan | `RtmpDataSource` dan `DrmSessionManagerFactory` (Widevine, ClearKey, PlayReady) |
| Manajemen Playlist Multi-Source | **DIHAPUS** | Seluruh UI & logika custom source reader dihapus total (§4) |
| FilePicker JitPack | **DIHAPUS** | Dihapus dari dependensi dan kode (§3) |
| com.facebook.shimmer | **DIHAPUS** | Digantikan placeholder hemat memori bertema Material 3 (§3) |
| AAR FFmpeg Warisan | **DIHAPUS** | Digantikan modul resmi AndroidX Media3 (§3) |
| TLS Trust-All / Bypass SSL | **DIHAPUS** | Dihapus total; diganti verifikasi sistem & Certificate Pinning (§5-G10) |
| dataBinding | **DIHAPUS** | Ditiadakan; beralih murni ke ViewBinding (§1) |

---

## 3. Cara Build di Android Studio

### Prasyarat Lingkungan
1. **Android Studio:** Versi Koala (2024.1) atau Ladybug (2024.2+).
2. **JDK:** OpenJDK 17 (disetel pada *Settings > Build, Execution, Deployment > Build Tools > Gradle > Gradle JDK*).
3. **Android SDK:** SDK Platform 35 (Android 15) dan SDK Build-Tools 35.0.0.
4. **NDK & CMake:** NDK versi `26.1.10909125` dan CMake versi `3.22.1` (dapat diinstal melalui SDK Manager).

### Langkah-Langkah Build
1. Buka folder proyek ini di Android Studio (*File > Open...*).
2. Tunggu Gradle selesai melakukan *sync dependencies* dan konfigurasi CMake.
3. Pilih build variant yang diinginkan (*Build > Select Build Variant...*):
   - `debug`: Untuk pengujian dengan logging aktif dan suffix paket `.debug`.
   - `release`: Untuk produksi dengan R8 Full Mode, obfuscation, resource shrinking, dan penghapusan seluruh log debug.
4. Hubungkan perangkat Android TV, Google TV, atau emulator Android TV (resolusi 1080p).
5. Klik **Run 'app'** (tombol Play hijau) atau jalankan perintah Gradle berikut di terminal:
   ```bash
   ./gradlew assembleDebug
   ```
   Berkas APK hasil build akan berada di `app/build/outputs/apk/debug/app-debug.apk`.

---

## 4. Cara Mendapatkan APK lewat CI (GitHub Actions)

Repositori ini telah dilengkapi pipeline otomatis resmi di `.github/workflows/android.yml`. Setiap *push* atau *pull request* ke branch utama akan memicu build otomatis di cloud:

1. **Tahap Verifikasi Gateway:** Menjalankan `node --check gateway/worker.js` untuk memastikan tidak ada kesalahan sintaksis pada skrip Cloudflare Worker.
2. **Tahap Setup:** Menyiapkan JDK 17, Android SDK Platform 35, CMake 3.22.1, dan NDK 26.1.
3. **Tahap Pengujian:** Menjalankan seluruh pengujian unit JUnit JVM via `./gradlew testDebugUnitTest`.
4. **Tahap Perakitan:** Mengompilasi APK debug via `./gradlew assembleDebug`.
5. **Unduh APK:**
   - Masuk ke tab **Actions** di repositori GitHub Anda.
   - Klik alur kerja terbaru (*BatitongTV CI Build & Test*).
   - Pada bagian **Artifacts** di bawah ringkasan run, unduh paket **`BatitongTV-debug-apk`**.

---

## 5. Deployment Gateway (Cloudflare Workers)

Playlist Gateway bertindak sebagai proksi autentikasi cerdas antara aplikasi BatitongTV dan penyedia playlist:

1. Masuk ke direktori gateway dan pasang Wrangler CLI:
   ```bash
   npm install -g wrangler
   ```
2. Konfigurasikan secret environment gateway:
   ```bash
   wrangler secret put GATEWAY_SECRET
   wrangler secret put OFFICIAL_CERT_SHA256
   wrangler secret put UPSTREAM_SHORTLINK
   ```
3. Deploy ke Cloudflare Edge:
   ```bash
   wrangler deploy gateway/worker.js --name batitongtv-gateway
   ```

---

## 6. Lisensi & Pernyataan Non-Komersialisasi APK

Proyek aplikasi **BatitongTV** didistribusikan di bawah lisensi resmi **Apache License 2.0**. Berkas APK dan kode sumber aplikasi ini **tidak untuk diperjualbelikan**. Biaya atau langganan yang mungkin timbul merupakan pembiayaan akses layanan playlist/gateway pihak ketiga milik pemilik, bukan penjualan biner aplikasi. Rincian atribusi upstream tersedia pada berkas `NOTICE`.
