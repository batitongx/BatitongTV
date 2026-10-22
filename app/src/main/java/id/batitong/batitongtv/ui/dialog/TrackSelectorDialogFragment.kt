package id.batitong.batitongtv.ui.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import id.batitong.batitongtv.R
import id.batitong.batitongtv.databinding.DialogTrackSelectorTvBinding
import id.batitong.batitongtv.player.Media3PlayerManager

/**
 * Dialog Pemilihan Trek Audio & Subtitle (§8 & §9).
 */
class TrackSelectorDialogFragment(
    private val tracks: List<Media3PlayerManager.TrackItem>,
    private val onTrackSelected: (Media3PlayerManager.TrackItem) -> Unit
) : DialogFragment() {

    private var _binding: DialogTrackSelectorTvBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogTrackSelectorTvBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvAudioTracks.layoutManager = LinearLayoutManager(requireContext())
        binding.rvAudioTracks.adapter = object : RecyclerView.Adapter<TrackViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackViewHolder {
                val tv = LayoutInflater.from(parent.context).inflate(
                    R.layout.item_category_tv,
                    parent,
                    false
                ) as TextView
                return TrackViewHolder(tv)
            }

            override fun onBindViewHolder(holder: TrackViewHolder, position: Int) {
                val item = tracks[position]
                holder.tv.text = if (item.isSelected) "${item.name} ✓" else item.name
                holder.tv.isSelected = item.isSelected
                holder.tv.setOnClickListener {
                    onTrackSelected(item)
                    dismiss()
                }
            }

            override fun getItemCount(): Int = tracks.size
        }

        binding.btnTrackClose.setOnClickListener {
            dismiss()
        }
    }

    class TrackViewHolder(val tv: TextView) : RecyclerView.ViewHolder(tv)

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
