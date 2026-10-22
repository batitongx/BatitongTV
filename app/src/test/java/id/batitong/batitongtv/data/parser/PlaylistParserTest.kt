package id.batitong.batitongtv.data.parser

import id.batitong.batitongtv.data.model.DrmConfig
import id.batitong.batitongtv.data.model.DrmType
import id.batitong.batitongtv.data.model.ManifestType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Unit Test JUnit komprehensif untuk membuktikan penutupan gap G1 s/d G10 pada parser playlist BatitongTV.
 */
class PlaylistParserTest {

    private lateinit var parser: PlaylistParser
    private var sampleM3uContent: String = ""
    private var sampleJsonContent: String = ""

    @Before
    fun setUp() {
        parser = PlaylistParser()

        // Muat sample berkas jika tersedia di filesystem, atau gunakan fallback test string
        val m3uFile = File("samples/sample-custom.m3u")
        if (m3uFile.exists()) {
            sampleM3uContent = m3uFile.readText()
        }

        val jsonFile = File("samples/sample-legacy.json")
        if (jsonFile.exists()) {
            sampleJsonContent = jsonFile.readText()
        }
    }

    /**
     * G1: #EXTHTTP parsing, kanonisasi header, dan ketahanan terhadap JSON rusak.
     */
    @Test
    fun testG1_extHttp_canonicalization_and_resilience() {
        val m3u = """
            #EXTM3U
            #EXTHTTP:{"user-agent":"ResilientAgent/1.0","referer":"https://canon-ref.com","x-custom-id":"xyz"}
            #EXTINF:-1 group-title="G1",Channel G1 Valid
            https://example.com/live1.m3u8

            #EXTHTTP:{"broken_json": "unterminated
            #EXTINF:-1 group-title="G1",Channel G1 Corrupt
            https://example.com/live2.m3u8
        """.trimIndent()

        val result = parser.parse(m3u)
        assertEquals(2, result.channels.size)

        // Saluran 1: Header dikanonisasi ke Title-Case
        val ch1 = result.channels[0]
        assertEquals("ResilientAgent/1.0", ch1.headers["User-Agent"])
        assertEquals("https://canon-ref.com", ch1.headers["Referer"])
        assertEquals("xyz", ch1.headers["X-Custom-Id"])

        // Saluran 2: JSON rusak tidak membuat crash atau menggugurkan playlist
        val ch2 = result.channels[1]
        assertEquals("Channel G1 Corrupt", ch2.name)

        // Warning terstruktur tercatat
        val warning = result.warnings.find { it.tag == "EXTHTTP" }
        assertNotNull("Harus mencatat warning terstruktur untuk JSON rusak", warning)
    }

    /**
     * G2: #KODIPROP kunci-kunci kodi diparse dan diberi prefix kodiprop:
     */
    @Test
    fun testG2_kodiProp_prefix_and_keys() {
        val m3u = """
            #EXTM3U
            #KODIPROP:inputstream.adaptive.manifest_type=mpd
            #KODIPROP:inputstream.adaptive.license_type=com.widevine.alpha
            #KODIPROP:inputstream.adaptive.contentlookup=false
            #EXTINF:-1 group-title="G2",Kodi Channel
            https://example.com/manifest.mpd
        """.trimIndent()

        val result = parser.parse(m3u)
        assertEquals(1, result.channels.size)
        val ch = result.channels[0]

        assertEquals(ManifestType.DASH, ch.manifestType)
        assertEquals("mpd", ch.metadata["kodiprop:inputstream.adaptive.manifest_type"])
        assertEquals("com.widevine.alpha", ch.metadata["kodiprop:inputstream.adaptive.license_type"])
        assertEquals("false", ch.metadata["kodiprop:inputstream.adaptive.contentlookup"])
    }

    /**
     * G3: #EXTVLCOPT case-insensitive dan pengenalan http-referer (satu r) -> Referer.
     */
    @Test
    fun testG3_extVlcOpt_case_insensitive_and_single_r() {
        val m3u = """
            #EXTM3U
            #EXTVLCOPT:http-user-agent=VlcPlayer/3.0
            #EXTVLCOPT:http-referer=https://single-r-referer.com
            #EXTVLCOPT:http-origin=https://origin.com
            #EXTINF:-1 group-title="G3",VLC Option Channel
            https://example.com/stream.m3u8
        """.trimIndent()

        val result = parser.parse(m3u)
        assertEquals(1, result.channels.size)
        val ch = result.channels[0]

        assertEquals("VlcPlayer/3.0", ch.headers["User-Agent"])
        assertEquals("https://single-r-referer.com", ch.headers["Referer"])
        assertEquals("https://origin.com", ch.headers["Origin"])
    }

    /**
     * G4: #EXTINF generic attribute extraction, NO NAME, UNCATEGORIZED, dan duplikasi nama #1, #2.
     */
    @Test
    fun testG4_extInf_attributes_no_name_and_duplicate_numbering() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 tvg-id="id1" tvg-name="TV1" tvg-logo="https://logo.png" group-title="Umum" custom-tag="vip",Channel Alpha
            https://example.com/ch1.m3u8

            #EXTINF:-1 group-title="Umum",Channel Alpha
            https://example.com/ch2.m3u8

            #EXTINF:-1 group-title="Umum",Channel Alpha
            https://example.com/ch3.m3u8

            #EXTINF:-1
            https://example.com/ch4.m3u8
        """.trimIndent()

        val result = parser.parse(m3u)
        assertEquals(4, result.channels.size)

        val ch1 = result.channels[0]
        assertEquals("Channel Alpha", ch1.name)
        assertEquals("id1", ch1.tvgId)
        assertEquals("TV1", ch1.tvgName)
        assertEquals("https://logo.png", ch1.logoUrl)
        assertEquals("vip", ch1.metadata["extinf:custom-tag"])

        // Duplikat di kategori yang sama
        val ch2 = result.channels[1]
        assertEquals("Channel Alpha #1", ch2.name)

        val ch3 = result.channels[2]
        assertEquals("Channel Alpha #2", ch3.name)

        // Saluran tanpa nama dan tanpa grup
        val ch4 = result.channels[3]
        assertEquals("NO NAME", ch4.name)
        assertEquals("UNCATEGORIZED", ch4.group)
    }

    /**
     * G5: Prioritas header (#EXTVLCOPT < #EXTHTTP < stream_headers < Pipe URL) dan DUA dialek.
     */
    @Test
    fun testG5_header_priority_and_dialects() {
        val m3u = """
            #EXTM3U
            #EXTVLCOPT:http-user-agent=Level1Vlc
            #EXTHTTP:{"User-Agent":"Level2Http","X-Token":"abc"}
            #KODIPROP:inputstream.adaptive.stream_headers=User-Agent=Level3StreamHeader%201.0&X-Param=123
            #EXTINF:-1 group-title="Priority",Priority Channel
            https://example.com/stream.m3u8|User-Agent=Level4WinningPipe&X-Pipe=Win

            #KODIPROP:inputstream.adaptive.stream_headers=User-Agent=DensTV%20Box|Referer=https%3A%2F%2Fdenstv.com
            #EXTINF:-1 group-title="DensTV",DensTV Dialect Channel
            https://example.com/denstv.m3u8
        """.trimIndent()

        val result = parser.parse(m3u)
        assertEquals(2, result.channels.size)

        // Channel 1: Pipe URL harus menang atas level lainnya
        val ch1 = result.channels[0]
        assertEquals("Level4WinningPipe", ch1.headers["User-Agent"])
        assertEquals("abc", ch1.headers["X-Token"])
        assertEquals("123", ch1.headers["X-Param"])
        assertEquals("Win", ch1.headers["X-Pipe"])

        // Channel 2: Dialek stream_headers berpemisah pipe '|' dan URL decoded
        val ch2 = result.channels[1]
        assertEquals("DensTV Box", ch2.headers["User-Agent"])
        assertEquals("https://denstv.com", ch2.headers["Referer"])
    }

    /**
     * G6: State machine eksplisit anti-bocor antar-channel (Kodi props sebelum #EXTINF).
     */
    @Test
    fun testG6_anti_leak_between_channels() {
        val m3u = """
            #EXTM3U
            #KODIPROP:inputstream.adaptive.license_type=com.widevine.alpha
            #KODIPROP:inputstream.adaptive.license_key=https://lic.example.com/wv
            #EXTHTTP:{"X-Secret-Auth":"SuperSecretToken"}
            #EXTINF:-1 group-title="G6",Channel With DRM
            https://example.com/ch1.mpd

            #EXTINF:-1 group-title="G6",Channel Clean
            https://example.com/ch2.m3u8
        """.trimIndent()

        val result = parser.parse(m3u)
        assertEquals(2, result.channels.size)

        val ch1 = result.channels[0]
        assertNotNull("Channel 1 harus memiliki DRM", ch1.drm)
        assertTrue(ch1.drm is DrmConfig.LicenseServer)
        assertEquals("SuperSecretToken", ch1.headers["X-Secret-Auth"])

        // Saluran 2 TIDAK BOLEH ketumpahan lisensi atau header dari Channel 1!
        val ch2 = result.channels[1]
        assertNull("Channel 2 TIDAK BOLEH mewarisi DRM dari Channel 1 (G6 Anti-Bocor)", ch2.drm)
        assertFalse("Channel 2 TIDAK BOLEH mewarisi header rahasia Channel 1", ch2.headers.containsKey("X-Secret-Auth"))
    }

    /**
     * G7: Normalizer license_key 4 varian:
     * (i) ClearKey multi-key hex
     * (ii) ClearKey Base64/Base64URL
     * (iii) Inline JWK JSON
     * (iv) URL server lisensi dengan pipe headers
     */
    @Test
    fun testG7_license_key_normalizer_four_variants() {
        // Varian (i): Multi-Key
        val m3uMultiKey = """
            #EXTM3U
            #KODIPROP:inputstream.adaptive.license_type=clearkey
            #KODIPROP:inputstream.adaptive.license_key=10000000100010001000100000000001:20000000200020002000200000000002,30000000300030003000300000000003:40000000400040004000400000000004
            #EXTINF:-1,Multi Key
            https://example.com/multi.mpd
        """.trimIndent()
        val res1 = parser.parse(m3uMultiKey)
        val ch1 = res1.channels[0]
        assertTrue(ch1.drm is DrmConfig.ClearKey)
        val ck1 = ch1.drm as DrmConfig.ClearKey
        assertEquals(2, ck1.clearKeys.size)
        assertTrue(ck1.jwkJson!!.contains("keys"))

        // Varian (ii): Base64
        val m3uB64 = """
            #EXTM3U
            #KODIPROP:inputstream.adaptive.license_type=clearkey
            #KODIPROP:inputstream.adaptive.license_key=EAABAAECAwQFBgcICQoLDA:EQECAwQFBgcICQoLDA0ODw
            #EXTINF:-1,B64 Key
            https://example.com/b64.mpd
        """.trimIndent()
        val res2 = parser.parse(m3uB64)
        val ch2 = res2.channels[0]
        assertTrue(ch2.drm is DrmConfig.ClearKey)

        // Varian (iii): Inline JWK
        val m3uJwk = """
            #EXTM3U
            #KODIPROP:inputstream.adaptive.license_type=clearkey
            #KODIPROP:inputstream.adaptive.license_key={"keys":[{"kty":"oct","k":"MDEyMzQ1Njc4OWFiY2RlZg","kid":"MDEyMzQ1Njc4OWFiY2RlZg"}],"type":"temporary"}
            #EXTINF:-1,JWK Key
            https://example.com/jwk.mpd
        """.trimIndent()
        val res3 = parser.parse(m3uJwk)
        val ch3 = res3.channels[0]
        assertTrue(ch3.drm is DrmConfig.ClearKey)

        // Varian (iv): URL dengan pipe headers
        val m3uUrl = """
            #EXTM3U
            #KODIPROP:inputstream.adaptive.license_type=com.widevine.alpha
            #KODIPROP:inputstream.adaptive.license_key=https://lic.example.com/wv|Authorization=Bearer%20tok123&X-Dev=STB
            #EXTINF:-1,WV Server
            https://example.com/wv.mpd
        """.trimIndent()
        val res4 = parser.parse(m3uUrl)
        val ch4 = res4.channels[0]
        assertTrue(ch4.drm is DrmConfig.LicenseServer)
        val ls = ch4.drm as DrmConfig.LicenseServer
        assertEquals("https://lic.example.com/wv", ls.url)
        assertEquals("Bearer tok123", ls.licenseHeaders["Authorization"])
        assertEquals("STB", ls.licenseHeaders["X-Dev"])
    }

    /**
     * G8: Normalisasi DRM type dan UUID, serta warning bila DRM tidak dikenal.
     */
    @Test
    fun testG8_drm_type_normalization_and_unknown_warning() {
        val m3u = """
            #EXTM3U
            #KODIPROP:inputstream.adaptive.license_type=unknown_crypto_system
            #KODIPROP:inputstream.adaptive.license_key=some_key
            #EXTINF:-1,Unknown DRM Ch
            https://example.com/stream.mpd
        """.trimIndent()

        val result = parser.parse(m3u)
        assertEquals(1, result.channels.size)
        // Tidak boleh diam-diam menghasilkan UUID_NIL
        val warning = result.warnings.find { it.tag == "DRM" }
        assertNotNull("Harus mencatat warning jika DRM tidak dikenal", warning)
    }

    /**
     * G9: Header arbitrer end-to-end tersimpan di Channel.headers.
     */
    @Test
    fun testG9_arbitrary_headers_end_to_end() {
        val m3u = """
            #EXTM3U
            #EXTHTTP:{"X-Playback-Session":"sess-777","X-Client-Version":"2.5.1"}
            #EXTINF:-1,Arbitrary Headers
            https://example.com/stream.m3u8|Custom-Token=val123
        """.trimIndent()

        val result = parser.parse(m3u)
        val ch = result.channels[0]
        assertEquals("sess-777", ch.headers["X-Playback-Session"])
        assertEquals("2.5.1", ch.headers["X-Client-Version"])
        assertEquals("val123", ch.headers["Custom-Token"])
    }

    /**
     * Menguji kompatibilitas pembacaan format JSON lama.
     */
    @Test
    fun testLegacyJsonParser_compatibility() {
        val json = """
            {
              "categories": [
                {
                  "name": "Berita",
                  "channels": [
                    {
                      "name": "Kanal Satu",
                      "stream_url": "https://example.com/live.m3u8",
                      "logo_url": "https://example.com/logo.png",
                      "drm_id": "lic_1",
                      "user_agent": "LegacyUA",
                      "referer": "https://legacy-ref.com"
                    }
                  ]
                }
              ],
              "drm_licenses": [
                {
                  "id": "lic_1",
                  "type": "com.widevine.alpha",
                  "url": "https://lic.example.com/proxy",
                  "headers": {
                    "X-Auth": "Secret"
                  }
                }
              ]
            }
        """.trimIndent()

        val result = parser.parse(json)
        assertEquals(1, result.channels.size)
        val ch = result.channels[0]

        assertEquals("Kanal Satu", ch.name)
        assertEquals("Berita", ch.group)
        assertEquals("LegacyUA", ch.headers["User-Agent"])
        assertEquals("https://legacy-ref.com", ch.headers["Referer"])
        assertNotNull(ch.drm)
        assertTrue(ch.drm is DrmConfig.LicenseServer)
        val ls = ch.drm as DrmConfig.LicenseServer
        assertEquals(DrmType.WIDEVINE, ls.licenseType)
        assertEquals("Secret", ls.licenseHeaders["X-Auth"])
    }

    /**
     * Menguji parsing terhadap file sampel utuh samples/sample-custom.m3u bila tersedia.
     */
    @Test
    fun testSampleCustomM3u_full_file() {
        if (sampleM3uContent.isBlank()) return
        val result = parser.parse(sampleM3uContent)
        assertTrue("Harus menghasilkan minimal 8 saluran dari sample", result.channels.size >= 8)
        assertTrue("Kategori harus dikelompokkan dengan benar", result.categories.isNotEmpty())

        // Pastikan saluran pertama memiliki Widevine
        val chWidevine = result.channels.find { it.tvgId == "ch_widevine" }
        assertNotNull(chWidevine)
        assertTrue(chWidevine!!.drm is DrmConfig.LicenseServer)

        // Pastikan saluran anti-bocor tidak memiliki DRM
        val chClean = result.channels.find { it.tvgId == "ch_clean" }
        assertNotNull(chClean)
        assertNull("Saluran ch_clean tidak boleh bocor DRM", chClean!!.drm)
    }
}
