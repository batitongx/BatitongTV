package id.batitong.batitongtv.security

import android.os.Debug
import java.io.File

/**
 * Pengumpul sinyal risiko runtime sesuai spesifikasi §7-C5.
 * Mengumpulkan heuristik (root, debugger, Frida, Xposed) untuk dikirim ke gateway.
 * TIDAK memblokir atau melakukan crash di sisi lokal.
 */
data class RiskSignals(
    val isRooted: Boolean,
    val hasFrida: Boolean,
    val hasXposed: Boolean,
    val isDebuggerAttached: Boolean,
    val isEmulator: Boolean
)

object RiskSignalCollector {

    fun collectSignals(): RiskSignals {
        return RiskSignals(
            isRooted = checkRootHeuristics(),
            hasFrida = checkFridaArtifacts(),
            hasXposed = checkXposedArtifacts(),
            isDebuggerAttached = Debug.isDebuggerConnected() || Debug.waitingForDebugger(),
            isEmulator = checkEmulatorHeuristics()
        )
    }

    private fun checkRootHeuristics(): Boolean {
        val rootPaths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su"
        )
        for (path in rootPaths) {
            try {
                if (File(path).exists()) return true
            } catch (e: Exception) {
                // Ignore permission error
            }
        }
        return false
    }

    private fun checkFridaArtifacts(): Boolean {
        val fridaPaths = arrayOf(
            "/data/local/tmp/frida-server",
            "/data/local/tmp/re.frida.server"
        )
        for (path in fridaPaths) {
            try {
                if (File(path).exists()) return true
            } catch (e: Exception) {
                // Ignore
            }
        }
        return false
    }

    private fun checkXposedArtifacts(): Boolean {
        return try {
            ClassLoader.getSystemClassLoader().loadClass("de.robv.android.xposed.XposedBridge")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }

    private fun checkEmulatorHeuristics(): Boolean {
        val fingerprint = android.os.Build.FINGERPRINT.lowercase()
        val model = android.os.Build.MODEL.lowercase()
        val manufacturer = android.os.Build.MANUFACTURER.lowercase()
        return fingerprint.contains("generic") ||
                fingerprint.contains("vbox") ||
                model.contains("google_sdk") ||
                model.contains("emulator") ||
                manufacturer.contains("genymotion")
    }
}
