package id.batitong.batitongtv

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

/**
 * BatitongApp: Entry point Application class untuk BatitongTV.
 * Memuat library native JNI keamanan dan mengonfigurasi ImageLoader hemat memori.
 */
class BatitongApp : Application(), ImageLoaderFactory {

    companion object {
        lateinit var appContext: Context
            private set
    }

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext

        // Memuat modul JNI native bridge keamanan (§7-C2)
        try {
            System.loadLibrary("batitongtv_security")
        } catch (e: UnsatisfiedLinkError) {
            // Ditangani secara aman di environment JVM atau perangkat tanpa binary native yang cocok
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.15) // Hemat alokasi memori heap untuk TV hardware terbatas
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(20L * 1024 * 1024) // 20 MB disk cache untuk logo saluran
                    .build()
            }
            .crossfade(true)
            .build()
    }
}
