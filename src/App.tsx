/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState } from 'react';
import { Tv, ShieldCheck, FileCode, CheckCircle2, Terminal, Cpu, Lock, Radio } from 'lucide-react';

export default function App() {
  const [activeTab, setActiveTab] = useState<'overview' | 'stages' | 'security'>('overview');

  return (
    <div className="min-h-screen bg-[#0B0F19] text-[#F8FAFC] font-sans selection:bg-[#00D285]/30">
      {/* Top Header */}
      <header className="border-b border-[#1E293B] bg-[#131B2E]/80 backdrop-blur px-8 py-5 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-lg bg-gradient-to-tr from-[#00A868] to-[#00E599] flex items-center justify-center shadow-lg shadow-[#00D285]/20">
            <Tv className="w-6 h-6 text-[#0B0F19] stroke-[2.5]" />
          </div>
          <div>
            <h1 className="text-xl font-bold tracking-tight text-white flex items-center gap-2">
              BatitongTV
              <span className="text-xs px-2 py-0.5 rounded-full bg-[#00D285]/20 text-[#00E599] border border-[#00E599]/30 font-medium">
                Android TV &middot; Media3 &middot; Gateway
              </span>
            </h1>
            <p className="text-xs text-[#94A3B8]">id.batitong.batitongtv &bull; compileSdk 35 &bull; AGP 8.7.3 &bull; Media3 1.5.1</p>
          </div>
        </div>

        <nav className="flex items-center gap-2">
          <button
            onClick={() => setActiveTab('overview')}
            className={`px-3 py-1.5 rounded-md text-xs font-medium transition ${
              activeTab === 'overview'
                ? 'bg-[#00D285] text-[#0B0F19] font-semibold'
                : 'text-[#94A3B8] hover:text-white hover:bg-[#1E293B]'
            }`}
          >
            Arsitektur Proyek
          </button>
          <button
            onClick={() => setActiveTab('stages')}
            className={`px-3 py-1.5 rounded-md text-xs font-medium transition ${
              activeTab === 'stages'
                ? 'bg-[#00D285] text-[#0B0F19] font-semibold'
                : 'text-[#94A3B8] hover:text-white hover:bg-[#1E293B]'
            }`}
          >
            Tahap Penyerahan (A–E)
          </button>
          <button
            onClick={() => setActiveTab('security')}
            className={`px-3 py-1.5 rounded-md text-xs font-medium transition ${
              activeTab === 'security'
                ? 'bg-[#00D285] text-[#0B0F19] font-semibold'
                : 'text-[#94A3B8] hover:text-white hover:bg-[#1E293B]'
            }`}
          >
            Benteng Keamanan (C1–C8)
          </button>
        </nav>
      </header>

      {/* Main Content */}
      <main className="max-w-6xl mx-auto p-8 space-y-6">
        {activeTab === 'overview' && (
          <div className="space-y-6">
            {/* Status Banner */}
            <div className="p-5 rounded-xl bg-gradient-to-r from-[#131B2E] via-[#162238] to-[#131B2E] border border-[#00E599]/30 relative overflow-hidden">
              <div className="flex items-start justify-between">
                <div className="space-y-1">
                  <span className="text-xs font-semibold uppercase tracking-wider text-[#00E599]">
                    Status Proyek: Selesai 100% (Tahap A s/d E) &bull; Audit Grep Bersih
                  </span>
                  <h2 className="text-lg font-bold text-white">Rebuild BatitongTV, Parser G1–G10, Media3 1.5.1 &amp; Gateway Sukses</h2>
                  <p className="text-sm text-[#94A3B8] max-w-3xl leading-relaxed">
                    Repository Android <span className="text-[#F8FAFC] font-semibold">BatitongTV</span> telah selesai dibangun sepenuhnya dari Tahap A hingga Tahap E: fondasi Gradle terpin, parser M3U/JSON anti-bocor, Media3 player, benteng keamanan gateway C1–C8, UI TV 3-panel modern, CI GitHub Actions, unit tests, dan dokumentasi lengkap.
                  </p>
                </div>
                <div className="hidden sm:flex flex-col items-end text-xs text-[#64748B]">
                  <span className="font-mono text-[#00E599]">namespace: id.batitong.batitongtv</span>
                  <span>minSdk 23 &bull; targetSdk 35 &bull; JDK 17</span>
                </div>
              </div>
            </div>

            {/* Grid Kartu Fitur Fondasi */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              <div className="p-5 rounded-xl bg-[#131B2E] border border-[#1E293B] space-y-3">
                <div className="w-9 h-9 rounded-lg bg-[#00D285]/10 flex items-center justify-center text-[#00E599]">
                  <Cpu className="w-5 h-5" />
                </div>
                <h3 className="font-semibold text-white text-sm">Toolchain Terkunci</h3>
                <ul className="text-xs text-[#94A3B8] space-y-1.5 list-disc list-inside">
                  <li>AGP 8.7.3 &bull; Kotlin 2.0.21 &bull; Gradle 8.9</li>
                  <li>compileSdk 35 &bull; targetSdk 35 &bull; minSdk 23</li>
                  <li>JDK Target 17 &bull; ViewBinding murni</li>
                  <li>Media3 1.5.1 eksklusif (HAPUS ExoPlayer 2.15)</li>
                </ul>
              </div>

              <div className="p-5 rounded-xl bg-[#131B2E] border border-[#1E293B] space-y-3">
                <div className="w-9 h-9 rounded-lg bg-[#38BDF8]/10 flex items-center justify-center text-[#38BDF8]">
                  <ShieldCheck className="w-5 h-5" />
                </div>
                <h3 className="font-semibold text-white text-sm">Native JNI &amp; R8 Obfuskasi</h3>
                <ul className="text-xs text-[#94A3B8] space-y-1.5 list-disc list-inside">
                  <li>Modul JNI C++ (CMake 3.22.1) untuk secret chunks</li>
                  <li>Perhitungan SHA-256 sertifikat langsung di C++</li>
                  <li>R8 Full Mode &bull; resource shrinking aktif</li>
                  <li>Seluruh pemanggilan Log.* distrip pada rilis</li>
                </ul>
              </div>

              <div className="p-5 rounded-xl bg-[#131B2E] border border-[#1E293B] space-y-3">
                <div className="w-9 h-9 rounded-lg bg-[#F59E0B]/10 flex items-center justify-center text-[#F59E0B]">
                  <Terminal className="w-5 h-5" />
                </div>
                <h3 className="font-semibold text-white text-sm">CI GitHub Actions Otomatis</h3>
                <ul className="text-xs text-[#94A3B8] space-y-1.5 list-disc list-inside">
                  <li><code className="text-[#00E599]">node --check</code> untuk gateway Cloudflare</li>
                  <li>Setup JDK 17 &bull; Android SDK &bull; NDK 26.1</li>
                  <li>Otomatisasi <code className="text-[#00E599]">./gradlew testDebugUnitTest</code></li>
                  <li>Build APK &amp; upload artifact resmi</li>
                </ul>
              </div>
            </div>

            {/* Tree File Struktur Proyek Tahap A */}
            <div className="p-5 rounded-xl bg-[#131B2E] border border-[#1E293B] space-y-3">
              <h3 className="font-semibold text-white text-sm flex items-center gap-2">
                <FileCode className="w-4 h-4 text-[#00E599]" />
                Pohon Berkas Fondasi BatitongTV (Tahap A)
              </h3>
              <pre className="p-4 rounded-lg bg-[#0B0F19] text-[#94A3B8] text-xs font-mono overflow-x-auto leading-relaxed border border-[#1E293B]">
{`├── settings.gradle.kts                   (Project name BatitongTV, fail-on-project-repos)
├── build.gradle.kts                      (AGP 8.7.3, Kotlin 2.0.21)
├── gradle.properties                     (R8 Full Mode, JVM args 2048M, nonTransitiveRClass)
├── gradle/wrapper/gradle-wrapper.properties (Gradle 8.9)
├── .github/workflows/android.yml         (CI: JDK 17, node --check, unit test, assembleDebug)
├── NOTICE                                (Atribusi lisensi upstream non-branding §2.3)
└── app/
    ├── build.gradle.kts                  (namespace id.batitong.batitongtv, Media3 1.5.1, OkHttp 4.12.0)
    ├── proguard-rules.pro                (R8 full mode, strip Log.*, keep models & JNI)
    └── src/main/
        ├── AndroidManifest.xml           (Leanback launcher, banner, secure cleartext=false)
        ├── cpp/
        │   ├── CMakeLists.txt            (CMake 3.22.1 target batitongtv_security)
        │   └── security_bridge.cpp       (Split secret chunks, native cert SHA-256)
        ├── java/id/batitong/batitongtv/
        │   └── BatitongApp.kt            (Application class, memory-optimized Coil loader)
        └── res/
            ├── drawable/
            │   ├── banner_tv.xml         (Vector TV Banner 16:9 BatitongTV)
            │   ├── ic_launcher.xml       (Adaptive TV icon BatitongTV)
            │   ├── selector_item_tv.xml  (Focus state stroke + highlight)
            │   └── selector_category_tv.xml
            └── values/
                ├── colors.xml            (Material 3 TV dark palette)
                ├── dimens.xml            (Overscan safe padding 48x27dp)
                ├── strings.xml           (100% bebas dari nama lama, pesan ramah pengguna)
                └── themes.xml            (Theme.BatitongTV Material 3 Dark)`}
              </pre>
            </div>
          </div>
        )}

        {activeTab === 'stages' && (
          <div className="space-y-4">
            <h2 className="text-base font-bold text-white">Rencana Eksekusi Berurutan (Instruksi §10)</h2>
            <div className="space-y-3">
              {[
                {
                  code: 'Tahap A',
                  title: 'Fondasi Build & Toolchain',
                  status: 'SELESAI',
                  desc: 'Gradle 8 terpin, compileSdk 35, Manifest, rebrand BatitongTV, tema Material 3 Dark TV, native JNI C++, GitHub Actions workflow dengan node --check gateway.',
                  active: true,
                },
                {
                  code: 'Tahap B',
                  title: 'Model Data + Parser Generasi Baru (G1–G10)',
                  status: 'SELESAI',
                  desc: 'Parser M3U & JSON multi-source ditutup total (G1-G10) + unit test JUnit & samples komprehensif.',
                  active: true,
                },
                {
                  code: 'Tahap C',
                  title: 'Player Media3 + Benteng Keamanan (C1–C8) + Gateway Cloudflare',
                  status: 'SELESAI',
                  desc: 'Migrasi AndroidX Media3 1.5.1, OkHttp CertificatePinner (SPKI) first-party, gateway/worker.js Cloudflare (auth JWT + AES-GCM playlist), cache terenkripsi Keystore, Play Integrity, SECURITY.md.',
                  active: true,
                },
                {
                  code: 'Tahap D',
                  title: 'Perombakan UI Layar TV 3-Panel & Kontrol Modern',
                  status: 'SELESAI',
                  desc: 'Panel Kategori (kiri) + List Saluran (tengah) + Info/Preview (kanan), D-Pad focus state nyata, OSD player transparan, pemetaan tombol fisik D-Pad, dialog track/search/settings Material 3.',
                  active: true,
                },
                {
                  code: 'Tahap E',
                  title: 'Dokumentasi, Panduan Build & Audit Grep Nol Nama Lama',
                  status: 'SELESAI',
                  desc: 'README cara build di Android Studio & CI, pemetaan fitur upstream, dan bukti hasil audit pencarian (grep) nol kemunculan nama lama.',
                  active: true,
                },
              ].map((stage, idx) => (
                <div
                  key={idx}
                  className={`p-4 rounded-xl border ${
                    stage.active
                      ? 'bg-[#131B2E] border-[#00E599]/40'
                      : 'bg-[#131B2E]/60 border-[#1E293B]'
                  } flex items-start gap-4`}
                >
                  <div
                    className={`px-2.5 py-1 rounded text-xs font-mono font-bold ${
                      stage.active
                        ? 'bg-[#00D285] text-[#0B0F19]'
                        : 'bg-[#1E293B] text-[#94A3B8]'
                    }`}
                  >
                    {stage.code}
                  </div>
                  <div className="flex-1 space-y-1">
                    <div className="flex items-center justify-between">
                      <h4 className="font-semibold text-white text-sm">{stage.title}</h4>
                      <span
                        className={`text-xs font-semibold ${
                          stage.active ? 'text-[#00E599]' : 'text-[#64748B]'
                        }`}
                      >
                        {stage.status}
                      </span>
                    </div>
                    <p className="text-xs text-[#94A3B8]">{stage.desc}</p>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {activeTab === 'security' && (
          <div className="space-y-4">
            <h2 className="text-base font-bold text-white">Arsitektur Pengamanan Playlist Berbayar (§7)</h2>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div className="p-4 rounded-xl bg-[#131B2E] border border-[#1E293B] space-y-2">
                <div className="flex items-center gap-2 text-[#00E599] font-semibold text-sm">
                  <Lock className="w-4 h-4" />
                  C1: Playlist Gateway (Cloudflare Worker)
                </div>
                <p className="text-xs text-[#94A3B8] leading-relaxed">
                  Aplikasi tidak pernah meminta URL tujuan akhir playlist secara langsung. Gateway me-resolve shortlink, memverifikasi token sesi JWT (&le;10 mnt), dan mengenkripsi dokumen playlist dengan AES-GCM 256-bit.
                </p>
              </div>

              <div className="p-4 rounded-xl bg-[#131B2E] border border-[#1E293B] space-y-2">
                <div className="flex items-center gap-2 text-[#00E599] font-semibold text-sm">
                  <ShieldCheck className="w-4 h-4" />
                  C2: Nol Rahasia Plaintext di APK
                </div>
                <p className="text-xs text-[#94A3B8] leading-relaxed">
                  Endpoint tersimpan terenkripsi. Kunci didekripsi hanya di memori via JNI C++. R8 full mode mengaburkan kode dan menghapus seluruh log debug. Playlist tidak pernah disimpan dalam format teks di disk.
                </p>
              </div>

              <div className="p-4 rounded-xl bg-[#131B2E] border border-[#1E293B] space-y-2">
                <div className="flex items-center gap-2 text-[#00E599] font-semibold text-sm">
                  <CheckCircle2 className="w-4 h-4" />
                  C3: Gerbang Integritas &amp; Degradasi STB
                </div>
                <p className="text-xs text-[#94A3B8] leading-relaxed">
                  Hash sertifikat penandatangan dihitung dari native code. Mendukung Play Integrity untuk perangkat GMS, serta jalur degradasi Key Attestation untuk Android TV Box / STB non-GMS.
                </p>
              </div>

              <div className="p-4 rounded-xl bg-[#131B2E] border border-[#1E293B] space-y-2">
                <div className="flex items-center gap-2 text-[#00E599] font-semibold text-sm">
                  <Radio className="w-4 h-4" />
                  C4: Anti-Sniffing (Certificate Pinning)
                </div>
                <p className="text-xs text-[#94A3B8] leading-relaxed">
                  Tidak memblokir VPN (mencegah false-positive). Gantinya memakai SPKI Certificate Pinning (aktif + cadangan) pada OkHttpClient dan DataSource Media3 untuk menangkal MITM bahkan di perangkat root.
                </p>
              </div>
            </div>
          </div>
        )}
      </main>
    </div>
  );
}
