package id.batitong.batitongtv.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import id.batitong.batitongtv.data.pref.PreferenceManager
import id.batitong.batitongtv.ui.MainActivity

/**
 * Receiver sistem untuk memicu pemutaran saluran atau peluncuran BatitongTV
 * saat perangkat TV / STB selesai booting (§9).
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefManager = PreferenceManager(context)
            if (prefManager.isBootStartupEnabled) {
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra("EXTRA_BOOT_STARTUP", true)
                }
                context.startActivity(launchIntent)
            }
        }
    }
}
