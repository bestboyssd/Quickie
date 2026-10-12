package io.github.g00fy2.quickiesample

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import io.github.g00fy2.quickiesample.databinding.ActivityWifiResultBinding

class WifiResultActivity : AppCompatActivity() {

  private lateinit var binding: ActivityWifiResultBinding
  private var activeNetworkCallback: ConnectivityManager.NetworkCallback? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    WindowCompat.setDecorFitsSystemWindows(window, false)
    binding = ActivityWifiResultBinding.inflate(layoutInflater)
    setContentView(binding.root)

    val type = intent.getStringExtra(EXTRA_TYPE) ?: TYPE_TEXT
    val content = intent.getStringExtra(EXTRA_CONTENT).orEmpty()
    val ssid = intent.getStringExtra(EXTRA_SSID).orEmpty()
    val password = intent.getStringExtra(EXTRA_PASSWORD).orEmpty()
    val encryption = intent.getStringExtra(EXTRA_ENCRYPTION).orEmpty().ifBlank { "WPA/WPA2" }

    when (type) {
      TYPE_WIFI -> {
        binding.cardResultIcon.setBackgroundResource(R.drawable.bg_wifi_icon)
        binding.ivResultIcon.setImageResource(R.drawable.ic_wifi_large)
        binding.ivResultIcon.imageTintList = ColorStateList.valueOf(Color.WHITE)
        binding.tvPrimaryLabel.text = "Network name"
        binding.tvPrimaryValue.text = ssid.ifBlank { "Unknown Network" }
        binding.tvPrimaryValue.typeface = Typeface.DEFAULT_BOLD

        // Password section with toggleable visibility
        binding.layoutPassword.visibility = View.VISIBLE
        if (password.isNotEmpty()) {
          var isPasswordVisible = false
          val maskedPassword = "*".repeat(password.length)
          binding.tvPasswordValue.text = maskedPassword

          binding.tvPasswordValue.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            binding.tvPasswordValue.text = if (isPasswordVisible) password else maskedPassword
          }

          binding.tvPasswordValue.setOnLongClickListener {
            copyToClipboard(password, "Password copied to clipboard")
            true
          }
        } else {
          binding.tvPasswordValue.text = "None"
          binding.tvPasswordValue.setOnClickListener(null)
          binding.tvPasswordValue.setOnLongClickListener(null)
        }

        binding.layoutSecondary.visibility = View.VISIBLE
        binding.tvSecondaryLabel.text = "Encryption"
        binding.tvSecondaryValue.text = encryption

        // Bottom action: Only Connect button for Wi-Fi
        binding.btnConnect.visibility = View.VISIBLE
        binding.layoutTextActions.visibility = View.GONE
        binding.btnConnect.setOnClickListener {
          connectAutomaticallyToWifi(ssid, password, encryption)
        }
      }
      TYPE_URL -> {
        binding.cardResultIcon.setBackgroundResource(R.drawable.bg_text_icon)
        binding.ivResultIcon.setImageResource(R.drawable.ic_open_in_new)
        binding.ivResultIcon.imageTintList = ColorStateList.valueOf(Color.WHITE)
        binding.tvPrimaryLabel.text = "Website URL"
        binding.tvPrimaryValue.text = content
        binding.tvPrimaryValue.typeface = Typeface.DEFAULT
        binding.layoutPassword.visibility = View.GONE
        binding.layoutSecondary.visibility = View.GONE

        binding.tvPrimaryValue.setOnClickListener {
          openBrowser(content)
        }

        binding.btnConnect.visibility = View.GONE
        binding.layoutTextActions.visibility = View.VISIBLE
        binding.btnSearch.text = "Open link"
        binding.btnSearch.setOnClickListener {
          openBrowser(content)
        }
        binding.btnCopyText.text = "Copy link"
        binding.btnCopyText.setOnClickListener {
          copyToClipboard(content, "Link copied to clipboard")
        }
      }
      else -> {
        // Normal Text Result (matching screenshot with slate squircle "T" and stacked Search / Copy text)
        binding.cardResultIcon.setBackgroundResource(R.drawable.bg_text_icon)
        binding.ivResultIcon.setImageResource(R.drawable.ic_text_format)
        binding.ivResultIcon.imageTintList = ColorStateList.valueOf(Color.WHITE)
        binding.tvPrimaryLabel.text = "Text"
        binding.tvPrimaryValue.text = content.ifBlank { "No content" }
        binding.tvPrimaryValue.typeface = Typeface.MONOSPACE
        binding.layoutPassword.visibility = View.GONE
        binding.layoutSecondary.visibility = View.GONE

        // Bottom actions: Stacked Search and Copy text buttons
        binding.btnConnect.visibility = View.GONE
        binding.layoutTextActions.visibility = View.VISIBLE
        binding.btnSearch.text = "Search"
        binding.btnSearch.setOnClickListener {
          searchWeb(content)
        }
        binding.btnCopyText.text = "Copy text"
        binding.btnCopyText.setOnClickListener {
          copyToClipboard(content, "Text copied to clipboard")
        }
      }
    }

    binding.btnBack.setOnClickListener {
      finish()
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    activeNetworkCallback?.let { callback ->
      try {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        cm?.unregisterNetworkCallback(callback)
      } catch (ignored: Exception) {}
    }
  }

  private fun searchWeb(query: String) {
    if (query.isBlank()) return
    try {
      val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
        putExtra(SearchManager.QUERY, query)
      }
      startActivity(intent)
    } catch (e: Exception) {
      try {
        val searchUrl = "https://www.google.com/search?q=" + Uri.encode(query)
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl)))
      } catch (ex: Exception) {
        Toast.makeText(this, "No browser available", Toast.LENGTH_SHORT).show()
      }
    }
  }

  private fun copyToClipboard(text: String, successMessage: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Scanned Text", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(this, successMessage, Toast.LENGTH_SHORT).show()
  }

  private fun openBrowser(url: String) {
    try {
      val formattedUrl = if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
        "https://$url"
      } else {
        url
      }
      startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(formattedUrl)))
    } catch (e: ActivityNotFoundException) {
      Toast.makeText(this, "No browser found to open link", Toast.LENGTH_SHORT).show()
    }
  }

  private fun connectAutomaticallyToWifi(ssid: String, pass: String, enc: String) {
    if (ssid.isBlank()) {
      openWifiSettings()
      return
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      try {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val specifierBuilder = WifiNetworkSpecifier.Builder().setSsid(ssid)

        when (enc.uppercase()) {
          "WPA", "WPA2", "WPA/WPA2", "WPA3", "WEP" -> {
            if (pass.isNotEmpty()) {
              specifierBuilder.setWpa2Passphrase(pass)
            }
          }
        }

        val request = NetworkRequest.Builder()
          .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
          .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
          .setNetworkSpecifier(specifierBuilder.build())
          .build()

        Toast.makeText(this, "Connecting to $ssid...", Toast.LENGTH_SHORT).show()

        activeNetworkCallback?.let {
          try { connectivityManager.unregisterNetworkCallback(it) } catch (ignored: Exception) {}
        }

        val networkCallback = object : ConnectivityManager.NetworkCallback() {
          override fun onAvailable(network: Network) {
            super.onAvailable(network)
            connectivityManager.bindProcessToNetwork(network)
            runOnUiThread {
              Toast.makeText(this@WifiResultActivity, "Connected to $ssid", Toast.LENGTH_SHORT).show()
            }
          }

          override fun onUnavailable() {
            super.onUnavailable()
            runOnUiThread {
              Toast.makeText(this@WifiResultActivity, "Connection cancelled or unavailable", Toast.LENGTH_SHORT).show()
            }
          }
        }
        activeNetworkCallback = networkCallback
        connectivityManager.requestNetwork(request, networkCallback)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && pass.isNotEmpty()) {
          val suggestionBuilder = WifiNetworkSuggestion.Builder().setSsid(ssid)
          suggestionBuilder.setWpa2Passphrase(pass)
          val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
          wifiManager?.addNetworkSuggestions(listOf(suggestionBuilder.build()))
        }
      } catch (e: Exception) {
        openWifiSettings()
      }
    } else {
      openWifiSettings()
    }
  }

  private fun openWifiSettings() {
    try {
      startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
    } catch (e: Exception) {
      Toast.makeText(this, "Could not open Wi-Fi settings", Toast.LENGTH_SHORT).show()
    }
  }

  companion object {
    const val EXTRA_TYPE = "extra_type"
    const val EXTRA_CONTENT = "extra_content"
    const val EXTRA_SSID = "extra_ssid"
    const val EXTRA_PASSWORD = "extra_password"
    const val EXTRA_ENCRYPTION = "extra_encryption"

    const val TYPE_WIFI = "WIFI"
    const val TYPE_URL = "URL"
    const val TYPE_TEXT = "TEXT"
  }
}
