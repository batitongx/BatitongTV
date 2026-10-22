package id.batitong.batitongtv.ui.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import id.batitong.batitongtv.R
import id.batitong.batitongtv.data.pref.PreferenceManager
import id.batitong.batitongtv.databinding.DialogSettingsTvBinding

/**
 * Dialog Pengaturan BatitongTV (§8 & §9).
 * TIDAK MEMILIKI antarmuka manajemen sumber playlist (§4).
 */
class SettingsDialogFragment(
    private val onSortChanged: (Boolean) -> Unit,
    private val onClearCacheRequested: () -> Unit
) : DialogFragment() {

    private var _binding: DialogSettingsTvBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogSettingsTvBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val prefManager = PreferenceManager(requireContext())

        // 1. Autoplay Saluran Terakhir
        binding.switchAutoplayLast.isChecked = prefManager.isAutoplayLastChannelEnabled
        binding.rowAutoplayLast.setOnClickListener {
            val newState = !binding.switchAutoplayLast.isChecked
            binding.switchAutoplayLast.isChecked = newState
            prefManager.isAutoplayLastChannelEnabled = newState
        }

        // 2. Buka Saat Booting
        binding.switchBootStartup.isChecked = prefManager.isBootStartupEnabled
        binding.rowBootStartup.setOnClickListener {
            val newState = !binding.switchBootStartup.isChecked
            binding.switchBootStartup.isChecked = newState
            prefManager.isBootStartupEnabled = newState
        }

        // 3. Pengurutan Saluran (Default vs Alfabetis)
        updateSortText(prefManager.isSortAlphabetical)
        binding.rowSortAlphabetical.setOnClickListener {
            val newSort = !prefManager.isSortAlphabetical
            prefManager.isSortAlphabetical = newSort
            updateSortText(newSort)
            onSortChanged(newSort)
        }

        // 4. Bersihkan Cache
        binding.btnClearCache.setOnClickListener {
            onClearCacheRequested()
            Toast.makeText(
                requireContext(),
                R.string.settings_clear_cache_success,
                Toast.LENGTH_SHORT
            ).show()
        }

        // 5. Buka Lisensi Open Source
        binding.btnOpenLicenses.setOnClickListener {
            LicensesDialogFragment().show(parentFragmentManager, "licenses_dialog")
        }
    }

    private fun updateSortText(isAlpha: Boolean) {
        binding.tvCurrentSortMode.text = if (isAlpha) {
            getString(R.string.settings_sort_alphabetical)
        } else {
            getString(R.string.settings_sort_default)
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
