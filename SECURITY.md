# Dokumentasi Keamanan & Model Ancaman BatitongTV

Dokumen ini mendefinisikan model ancaman, mekanisme mitigasi, dan batasan pertahanan keamanan berlapis pada aplikasi **BatitongTV** dan **Playlist Gateway** sesuai spesifikasi §7 (C1–C8).

---

## 1. Sasaran Keamanan & Klaim Resmi

> **Pernyataan Resmi Arsitektur:**
> *Sniffing pasif gagal total; ekstraksi statis APK tidak menghasilkan aset yang berguna; kebocoran apa pun kedaluwarsa dalam hitungan menit dan dapat dicabut seketika.*

Proyek ini secara sadar **tidak mengklaim** "100% tidak bisa dibobol": penyerang runtime yang gigih pada perangkat berakar (root) miliknya sendiri pada akhirnya dapat membaca memori aplikasi apa pun. Pertahanan proyek ini bertumpu pada arsitektur server-authoritative: **rahasia utama tidak pernah ada di klien, token berumur sangat pendek (≤ 10 menit), URL stream bertanda tangan sementara, dan kemampuan pencabutan instan dari server.**

---

## 2. Model Ancaman yang Ditangani

| Vektor Ancaman | Skenario Penyerang | Dampak Tanpa Pertahanan | Mitigasi BatitongTV |
|---|---|---|---|
| **T1: Sniffing Jaringan & MITM** | Menggunakan proxy MITM (HTTP Canary, Burp Suite, Charles) dengan memasang CA kustom ke trust store pengguna atau system store lewat root. | Penyerang merekam lalu lintas HTTP dan mencuri URL playlist/stream. | **C4: Certificate Pinning (SPKI)** pada OkHttp dan Media3 DataSource untuk domain first-party (aktif + cadangan). CA asing ditolak mentah-mentah. |
| **T2: Dekompilasi & Ekstraksi Statis APK** | Membongkar APK menggunakan Jadx, Apktool, atau Smali untuk mencari URL playlist, master key, atau endpoint. | Penyerang menemukan URL playlist mentah atau kredensial statis. | **C2: Nol Rahasia Plaintext.** Tidak ada URL tujuan akhir di APK. Endpoint disimpan terenkripsi, kunci dipecah dalam biner native C++ (JNI). R8 Full Mode mengaburkan simbol dan menghapus seluruh pemanggilan `Log.*`. |
| **T3: Modifikasi & Re-Sign APK** | Mengubah logika validasi aplikasi dan menandatangani ulang APK menggunakan sertifikat uji sendiri. | Aplikasi bajakan dapat digunakan bebas atau mendistribusikan stream curian. | **C3: Gerbang Integritas.** Hash SHA-256 sertifikat penandatangan dihitung di tingkat native (C++) dan divalidasi server-side oleh gateway. APK hasil re-sign ditolak menerima token sesi. |
| **T4: Instrumentasi Runtime (Frida/Xposed)** | Memasang hook pada fungsi Java untuk memotong data dekripsi atau membocorkan token. | Data memori diekstraksi saat runtime. | **C5: Sinyal Risiko & Token Berumur Pendek.** Indikator hooking dilaporkan sebagai sinyal risiko ke `POST /auth`. Gateway mempersingkat TTL sesi dan dapat mencabut akses `installId` yang terdeteksi anomali. |
| **T5: Kebocoran & Pembagian Dokumen Playlist** | Pengguna menyalin dokumen playlist atau membagikan tautan stream ke pihak luar. | Akses streaming dibajak massal oleh non-pelanggan. | **C1: Gateway Enkripsi & Signed Stream.** Dokumen playlist dienkripsi AES-GCM dengan kunci sesi unik per perangkat. URL stream first-party diberi tanda tangan HMAC dengan batas waktu kedaluwarsa ≤ 30 menit. |

---

## 3. Komponen Pertahanan Berlapis (C1–C6)

### C1. Playlist Gateway (Cloudflare Workers)
- Gateway adalah satu-satunya entitas yang me-resolve shortlink `https://lc.cx/-jNfCc` dan mengetahui URL tujuan akhir. URL tujuan akhir tidak pernah dikirim ke aplikasi klien.
- `POST /auth` menerbitkan JWT session token (berlaku ≤ 10 menit) dan session key acak AES-256 (256-bit).
- `GET /playlist` memverifikasi token sesi, mengunduh playlist secara server-side, menandatangani URL stream milik pemilik dengan HMAC (≤ 30 menit), dan mengenkripsi seluruh body playlist dengan AES-GCM menggunakan session key.
- Diterapkan rate-limiting per identitas instalasi & IP serta daftar pencabutan akses (*revocation list*).

### C2. Bebas Rahasia Plaintext di Klien
- URL gateway disimpan sebagai ciphertext AES-GCM.
- Potongan kunci disimpan terpecah dan diobfuskasi di modul C++ native (`app/src/main/cpp/security_bridge.cpp`) dan hanya dirangkai di memori runtime.
- Playlist plaintext hanya berada di RAM saat diputar. Satu-satunya salinan persisten adalah **cache ciphertext terenkripsi** di Android Keystore dengan TTL maksimum **24 jam** untuk mode offline.

### C3. Gerbang Integritas & Degradasi STB Non-GMS
- Aplikasi memverifikasi keaslian biner menggunakan hash sertifikat SHA-256 yang diekstraksi dari native code.
- Jika Google Play Services tersedia: memanfaatkan Google Play Integrity API.
- **Jalur Degradasi Android TV/STB Non-GMS:** Menggunakan Android Key Attestation (kunci Keystore hardware-backed dengan rantai sertifikat) bersama validasi hash penandatangan resmi. Perangkat TV Box tanpa GMS tetap didukung secara aman.

### C4. Anti-Sniffing Tanpa Pemblokiran VPN
- **Deteksi VPN Dilarang Sebagai Gerbang:** Pengguna tidak diblokir hanya karena memakai VPN atau proxy legal.
- **SPKI Certificate Pinning:** Mengikat pin kunci publik server (Primary + Backup) pada OkHttpClient dan DataSource Media3. Serangan MITM dengan sertifikat self-signed otomatis gagal pada tingkat handshake TLS.

### C5. Sinyal Risiko Terukur
- Indikator keberadaan binary `su`, paket debugging, Frida hook, atau Xposed dikumpulkan sebagai data diagnostik dan dikirim ke gateway. Keputusan ada di server, bukan crash lokal.

### C6. Minimalisasi Permukaan Serangan UI
- Tidak ada opsi "Ekspor Playlist", "Salin URL", atau WebView.
- Pesan error hanya menampilkan kalimat generik ("Gagal memuat saluran", "Sesi berakhir") tanpa rincian URL, token, atau session key.

---

## 4. Risiko Sisa yang Diakui Secara Eksplisit

Aplikasi klien berjalan di lingkungan fisik di luar kendali kita. Seseorang dengan akses root penuh, kernel custom, dan debugger hardware pada akhirnya dapat membongkar memori proses. Karena itu, mitigasi pamungkas bertumpu pada:
1. Playlist tidak pernah dapat diakses langsung tanpa autentikasi gateway aktif.
2. Token sesi kedaluwarsa setiap 10 menit.
3. URL segmen video bertanda tangan kedaluwarsa dalam 30 menit.
4. Identitas instalasi yang bocor dapat dicabut seketika dari gateway tanpa merilis pembaruan aplikasi.
