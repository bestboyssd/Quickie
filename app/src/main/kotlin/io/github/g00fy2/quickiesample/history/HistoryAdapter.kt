package io.github.g00fy2.quickiesample.history

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.g00fy2.quickiesample.R
import io.github.g00fy2.quickiesample.data.ScanHistoryItem
import io.github.g00fy2.quickiesample.databinding.ItemHistoryScanBinding

class HistoryAdapter(
  private val onItemClick: (ScanHistoryItem) -> Unit,
  private val onDeleteClick: (ScanHistoryItem) -> Unit
) : ListAdapter<ScanHistoryItem, HistoryAdapter.HistoryViewHolder>(DiffCallback) {

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
    val binding = ItemHistoryScanBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    return HistoryViewHolder(binding)
  }

  override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
    holder.bind(getItem(position))
  }

  inner class HistoryViewHolder(private val binding: ItemHistoryScanBinding) :
    RecyclerView.ViewHolder(binding.root) {

    fun bind(item: ScanHistoryItem) {
      binding.tvItemTitle.text = item.title
      binding.tvItemContent.text = item.content

      val relativeTime = DateUtils.getRelativeTimeSpanString(
        item.timestamp,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE
      )
      binding.tvItemTime.text = relativeTime

      val iconRes = when (item.type) {
        "WIFI" -> R.drawable.ic_wifi_large
        "URL" -> R.drawable.ic_open_in_new
        else -> R.drawable.ic_qs_qrcode
      }
      binding.ivTypeIcon.setImageResource(iconRes)

      binding.root.setOnClickListener { onItemClick(item) }
      binding.btnDeleteItem.setOnClickListener { onDeleteClick(item) }
    }
  }

  companion object DiffCallback : DiffUtil.ItemCallback<ScanHistoryItem>() {
    override fun areItemsTheSame(oldItem: ScanHistoryItem, newItem: ScanHistoryItem): Boolean =
      oldItem.id == newItem.id

    override fun areContentsTheSame(oldItem: ScanHistoryItem, newItem: ScanHistoryItem): Boolean =
      oldItem == newItem
  }
}
