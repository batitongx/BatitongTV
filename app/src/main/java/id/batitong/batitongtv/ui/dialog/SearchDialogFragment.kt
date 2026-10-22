package id.batitong.batitongtv.ui.dialog

import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import id.batitong.batitongtv.data.model.Channel
import id.batitong.batitongtv.databinding.DialogSearchTvBinding
import id.batitong.batitongtv.ui.adapter.ChannelAdapter

/**
 * Dialog Pencarian Saluran TV dengan input teks dan hasil interaktif (§8 & §9).
 */
class SearchDialogFragment(
    private val onSearchQuery: (String) -> List<Channel>,
    private val onChannelSelected: (Channel) -> Unit
) : DialogFragment() {

    private var _binding: DialogSearchTvBinding? = null
    private val binding get() = _binding!!

    private lateinit var channelAdapter: ChannelAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogSearchTvBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        channelAdapter = ChannelAdapter(
            channels = emptyList(),
            favoriteChannelIds = emptySet(),
            onChannelClick = { channel ->
                onChannelSelected(channel)
                dismiss()
            },
            onChannelLongClick = {}
        )

        binding.rvSearchResults.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSearchResults.adapter = channelAdapter

        binding.etSearchQuery.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString() ?: ""
                val results = onSearchQuery(query)
                channelAdapter.updateChannels(results, emptySet())
                binding.tvSearchEmpty.visibility = if (results.isEmpty() && query.isNotBlank()) View.VISIBLE else View.GONE
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnSearchClose.setOnClickListener {
            dismiss()
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
