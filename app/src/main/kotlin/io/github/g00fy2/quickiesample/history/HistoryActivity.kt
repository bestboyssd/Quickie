package io.github.g00fy2.quickiesample.history

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.g00fy2.quickiesample.WifiResultActivity
import io.github.g00fy2.quickiesample.data.AppDatabase
import io.github.g00fy2.quickiesample.data.ScanHistoryItem
import io.github.g00fy2.quickiesample.data.ScanHistoryRepository
import io.github.g00fy2.quickiesample.databinding.ActivityHistoryBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryActivity : AppCompatActivity() {

  private lateinit var binding: ActivityHistoryBinding
  private lateinit var repository: ScanHistoryRepository
  private lateinit var adapter: HistoryAdapter

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityHistoryBinding.inflate(layoutInflater)
    setContentView(binding.root)

    val db = AppDatabase.getDatabase(this)
    repository = ScanHistoryRepository(db.scanHistoryDao())

    binding.toolbar.setNavigationOnClickListener {
      finish()
    }

    adapter = HistoryAdapter(
      onItemClick = ::handleItemClick,
      onDeleteClick = { item ->
        lifecycleScope.launch(Dispatchers.IO) {
          repository.delete(item)
        }
      }
    )

    binding.rvHistory.layoutManager = LinearLayoutManager(this)
    binding.rvHistory.adapter = adapter

    binding.btnClearAll.setOnClickListener {
      showClearConfirmDialog()
    }

    lifecycleScope.launch {
      repository.allHistory.collect { list ->
        adapter.submitList(list)
        if (list.isEmpty()) {
          binding.emptyStateContainer.visibility = View.VISIBLE
          binding.rvHistory.visibility = View.GONE
          binding.btnClearAll.visibility = View.GONE
        } else {
          binding.emptyStateContainer.visibility = View.GONE
          binding.rvHistory.visibility = View.VISIBLE
          binding.btnClearAll.visibility = View.VISIBLE
        }
      }
    }
  }

  private fun handleItemClick(item: ScanHistoryItem) {
    val intent = Intent(this, WifiResultActivity::class.java).apply {
      when (item.type) {
        "WIFI" -> {
          putExtra(WifiResultActivity.EXTRA_TYPE, WifiResultActivity.TYPE_WIFI)
          putExtra(WifiResultActivity.EXTRA_SSID, item.wifiSsid ?: item.title)
          putExtra(WifiResultActivity.EXTRA_PASSWORD, item.wifiPassword.orEmpty())
          putExtra(WifiResultActivity.EXTRA_ENCRYPTION, item.wifiEncryption ?: "WPA")
          putExtra(WifiResultActivity.EXTRA_CONTENT, item.content)
        }
        "URL" -> {
          putExtra(WifiResultActivity.EXTRA_TYPE, WifiResultActivity.TYPE_URL)
          putExtra(WifiResultActivity.EXTRA_CONTENT, item.content)
        }
        else -> {
          putExtra(WifiResultActivity.EXTRA_TYPE, WifiResultActivity.TYPE_TEXT)
          putExtra(WifiResultActivity.EXTRA_CONTENT, item.content)
        }
      }
    }
    startActivity(intent)
  }

  private fun copyToClipboard(text: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val clip = ClipData.newPlainText("Scanned QR Code", text)
    clipboard?.setPrimaryClip(clip)
    Toast.makeText(this, "Copied: $text", Toast.LENGTH_SHORT).show()
  }

  private fun showClearConfirmDialog() {
    MaterialAlertDialogBuilder(this)
      .setTitle("Clear History?")
      .setMessage("Are you sure you want to remove all saved scan records?")
      .setNegativeButton("Cancel", null)
      .setPositiveButton("Clear") { _, _ ->
        lifecycleScope.launch(Dispatchers.IO) {
          repository.clearAll()
        }
      }
      .show()
  }
}
