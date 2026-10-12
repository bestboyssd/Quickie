package io.github.g00fy2.quickiesample

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Size
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import io.github.g00fy2.quickie.QRCodeAnalyzer
import io.github.g00fy2.quickiesample.data.AppDatabase
import io.github.g00fy2.quickiesample.data.ScanHistoryItem
import io.github.g00fy2.quickiesample.data.ScanHistoryRepository
import io.github.g00fy2.quickiesample.databinding.ActivityMainBinding
import io.github.g00fy2.quickiesample.databinding.DialogSettingsBinding
import io.github.g00fy2.quickiesample.history.HistoryActivity
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

  private lateinit var binding: ActivityMainBinding
  private lateinit var analysisExecutor: ExecutorService
  private lateinit var prefs: SharedPreferences
  private lateinit var historyRepository: ScanHistoryRepository

  private var cameraProvider: ProcessCameraProvider? = null
  private var imageAnalysis: ImageAnalysis? = null
  private var qrCodeAnalyzer: QRCodeAnalyzer? = null
  private var activeCamera: Camera? = null
  private var isHandlingBarcode = false
  private var isCameraBinding = false
  private var isCameraBound = false
  private var pendingTorchState = false

  private val galleryScanner by lazy { BarcodeScanning.getClient() }

  private val pickMediaLauncher =
    registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
        scanBarcodeFromUri(uri)
      }
    }

  private val requestCameraPermissionLauncher =
    registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      if (granted) {
        binding.permissionContainer.visibility = View.GONE
        binding.overlayView.visibility = View.VISIBLE
        if (!isCameraBound && !isCameraBinding) {
          startCamera()
        }
      } else {
        binding.permissionContainer.visibility = View.VISIBLE
        binding.overlayView.visibility = View.INVISIBLE
      }
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    installSplashScreen()
    super.onCreate(savedInstanceState)
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)

    prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val db = AppDatabase.getDatabase(this)
    historyRepository = ScanHistoryRepository(db.scanHistoryDao())

    setupEdgeToEdgeUI()
    analysisExecutor = Executors.newSingleThreadExecutor()

    // Initialize torch toggle immediately so it appears on first frame with all buttons
    binding.overlayView.setTorchVisibilityAndOnClick(true) { torchOn ->
      val camera = activeCamera
      if (camera != null && camera.cameraInfo.hasFlashUnit()) {
        camera.cameraControl.enableTorch(torchOn)
      } else {
        pendingTorchState = torchOn
        binding.overlayView.setTorchState(torchOn)
      }
    }

    binding.btnSettings.setOnClickListener {
      showSettingsDialog()
    }

    binding.btnGallery.setOnClickListener {
      pickMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    binding.grantPermissionButton.setOnClickListener {
      requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
      binding.permissionContainer.visibility = View.GONE
      binding.overlayView.visibility = View.VISIBLE
    } else {
      binding.permissionContainer.visibility = View.VISIBLE
      binding.overlayView.visibility = View.INVISIBLE
      requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }
  }

  override fun onPause() {
    super.onPause()
    imageAnalysis?.clearAnalyzer()
  }

  override fun onStop() {
    super.onStop()
    try {
      cameraProvider?.unbindAll()
    } catch (ignored: Exception) {}
    activeCamera = null
    imageAnalysis = null
    isCameraBound = false
    isCameraBinding = false
  }

  override fun onResume() {
    super.onResume()
    isHandlingBarcode = false
    binding.overlayView.isHighlighted = false
    binding.overlayView.isLoading = false
    qrCodeAnalyzer?.reset()

    if (binding.permissionContainer.visibility != View.VISIBLE) {
      if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
        if (!isCameraBound && !isCameraBinding) {
          startCamera()
        } else if (isCameraBound) {
          imageAnalysis?.let { setupAnalyzer(it) }
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent?) {
    super.onNewIntent(intent)
    setIntent(intent)
    isHandlingBarcode = false
    binding.overlayView.isHighlighted = false
    binding.overlayView.isLoading = false
    qrCodeAnalyzer?.reset()
    if (binding.permissionContainer.visibility != View.VISIBLE) {
      if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
        if (!isCameraBound && !isCameraBinding) {
          startCamera()
        } else if (isCameraBound) {
          imageAnalysis?.let { setupAnalyzer(it) }
        }
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    try {
      cameraProvider?.unbindAll()
    } catch (ignored: Exception) {
    }
    try {
      galleryScanner.close()
    } catch (ignored: Exception) {
    }
    analysisExecutor.shutdown()
  }

  private fun setupEdgeToEdgeUI() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    ViewCompat.setOnApplyWindowInsetsListener(binding.mainRoot) { _, insets ->
      val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
      binding.overlayView.setPadding(bars.left, bars.top, bars.right, bars.bottom)

      val density = resources.displayMetrics.density
      val offset16 = (16 * density).toInt()

      // Adjust Settings button (top & start)
      val lpSettings = binding.btnSettings.layoutParams as ViewGroup.MarginLayoutParams
      val newTopMargin = bars.top + offset16
      val newStartMargin = bars.left + offset16
      if (lpSettings.topMargin != newTopMargin || lpSettings.marginStart != newStartMargin) {
        lpSettings.topMargin = newTopMargin
        lpSettings.marginStart = newStartMargin
        binding.btnSettings.layoutParams = lpSettings
      }

      // Adjust Gallery button (bottom)
      val lpGallery = binding.btnGallery.layoutParams as ViewGroup.MarginLayoutParams
      val newBottomMargin = bars.bottom + (32 * density).toInt()
      if (lpGallery.bottomMargin != newBottomMargin) {
        lpGallery.bottomMargin = newBottomMargin
        binding.btnGallery.layoutParams = lpGallery
      }

      WindowInsetsCompat.CONSUMED
    }
  }

  private fun startCamera() {
    if (isCameraBinding || isCameraBound) return
    isCameraBinding = true

    val cameraProviderFuture = try {
      ProcessCameraProvider.getInstance(this)
    } catch (e: Exception) {
      isCameraBinding = false
      showError(e.localizedMessage ?: "Failed to initialize camera")
      return
    }

    cameraProviderFuture.addListener({
      if (isFinishing || isDestroyed) return@addListener

      val provider = try {
        cameraProviderFuture.get()
      } catch (e: Exception) {
        isCameraBinding = false
        showError(e.localizedMessage ?: "Failed to get camera provider")
        return@addListener
      }
      cameraProvider = provider

      val resolutionSelector = ResolutionSelector.Builder()
        .setResolutionStrategy(
          ResolutionStrategy(
            Size(1280, 720),
            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
          )
        )
        .build()

      val preview = Preview.Builder()
        .setResolutionSelector(resolutionSelector)
        .build().also {
          it.surfaceProvider = binding.previewView.surfaceProvider
        }

      val analysis = ImageAnalysis.Builder()
        .setResolutionSelector(resolutionSelector)
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .build()

      setupAnalyzer(analysis)
      imageAnalysis = analysis

      provider.unbindAll()
      val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

      try {
        val camera = provider.bindToLifecycle(this, cameraSelector, preview, analysis)
        activeCamera = camera
        isCameraBound = true
        isCameraBinding = false

        binding.overlayView.visibility = View.VISIBLE
        binding.overlayView.isHighlighted = false

        if (camera.cameraInfo.hasFlashUnit()) {
          binding.overlayView.setTorchVisibilityAndOnClick(true) { torchOn ->
            camera.cameraControl.enableTorch(torchOn)
          }
          camera.cameraInfo.torchState.observe(this) { state ->
            binding.overlayView.setTorchState(state == TorchState.ON)
          }
          if (pendingTorchState) {
            camera.cameraControl.enableTorch(true)
            pendingTorchState = false
          }
        } else {
          binding.overlayView.setTorchVisibilityAndOnClick(false)
        }
      } catch (e: Exception) {
        isCameraBound = false
        isCameraBinding = false
        binding.overlayView.visibility = View.INVISIBLE
        showError(e.localizedMessage ?: "Failed to bind camera")
      }
    }, ContextCompat.getMainExecutor(this))
  }

  private fun setupAnalyzer(analysis: ImageAnalysis) {
    val analyzer = QRCodeAnalyzer(
      barcodeFormats = intArrayOf(Barcode.FORMAT_ALL_FORMATS),
      onSuccess = { barcode ->
        runOnUiThread { onCodeDetected(barcode) }
      },
      onFailure = { },
      onPassCompleted = { failureOccurred ->
        if (binding.overlayView.isLoading != failureOccurred) {
          runOnUiThread {
            if (!isFinishing) {
              binding.overlayView.isLoading = failureOccurred
            }
          }
        }
      },
      validator = { barcode, imageProxy ->
        binding.overlayView.isBarcodeInFrame(
          barcode,
          imageProxy.width,
          imageProxy.height,
          imageProxy.imageInfo.rotationDegrees
        )
      }
    )
    qrCodeAnalyzer = analyzer
    analysis.setAnalyzer(analysisExecutor, analyzer)
  }

  private fun onCodeDetected(barcode: Barcode) {
    if (isFinishing || isHandlingBarcode) return
    isHandlingBarcode = true

    // Immediately trigger vibration and haptic feedback
    triggerVibrationFeedback()

    binding.overlayView.isHighlighted = true
    imageAnalysis?.clearAnalyzer()

    val rawContent = barcode.rawValue
      ?: barcode.rawBytes?.let { String(it) }.orEmpty()

    // 1. Check for Wi-Fi QR Code
    if (barcode.valueType == Barcode.TYPE_WIFI || barcode.wifi != null || rawContent.startsWith("WIFI:", ignoreCase = true)) {
      val (ssid, password, encryption) = parseWifiBarcode(barcode, rawContent)

      // Save to Room Database
      lifecycleScope.launch(Dispatchers.IO) {
        historyRepository.insert(
          ScanHistoryItem(
            content = rawContent,
            type = "WIFI",
            title = ssid,
            wifiSsid = ssid,
            wifiPassword = password,
            wifiEncryption = encryption
          )
        )
      }

      val intent = Intent(this, WifiResultActivity::class.java).apply {
        putExtra(WifiResultActivity.EXTRA_TYPE, WifiResultActivity.TYPE_WIFI)
        putExtra(WifiResultActivity.EXTRA_SSID, ssid)
        putExtra(WifiResultActivity.EXTRA_PASSWORD, password)
        putExtra(WifiResultActivity.EXTRA_ENCRYPTION, encryption)
      }
      startActivity(intent)
      return
    }

    // 2. Check for URL
    val isUrl = barcode.valueType == Barcode.TYPE_URL ||
      barcode.url != null ||
      rawContent.startsWith("http://", ignoreCase = true) ||
      rawContent.startsWith("https://", ignoreCase = true)

    val url = when {
      barcode.url != null -> barcode.url?.url
      isUrl -> rawContent
      else -> null
    }

    // Save to Room Database
    lifecycleScope.launch(Dispatchers.IO) {
      if (isUrl) {
        val displayTitle = (url ?: rawContent)
          .removePrefix("https://")
          .removePrefix("http://")
          .take(35)
        historyRepository.insert(
          ScanHistoryItem(
            content = url ?: rawContent,
            type = "URL",
            title = displayTitle
          )
        )
      } else {
        val displayTitle = rawContent.lines().firstOrNull()?.trim()?.take(35) ?: "Scanned QR Code"
        historyRepository.insert(
          ScanHistoryItem(
            content = rawContent,
            type = "TEXT",
            title = displayTitle.ifBlank { "Scanned QR Code" }
          )
        )
      }
    }

    // Auto-open URL in system browser if setting is enabled
    val autoOpenUrls = prefs.getBoolean(KEY_AUTO_OPEN_URL, false)
    if (isUrl && !url.isNullOrBlank() && autoOpenUrls) {
      openUrl(url)
      return
    }

    // 3. Open Result Screen for all scans
    val intent = Intent(this, WifiResultActivity::class.java).apply {
      if (isUrl) {
        putExtra(WifiResultActivity.EXTRA_TYPE, WifiResultActivity.TYPE_URL)
        putExtra(WifiResultActivity.EXTRA_CONTENT, url ?: rawContent)
      } else {
        putExtra(WifiResultActivity.EXTRA_TYPE, WifiResultActivity.TYPE_TEXT)
        putExtra(WifiResultActivity.EXTRA_CONTENT, rawContent)
      }
    }
    startActivity(intent)
  }

  private fun triggerVibrationFeedback() {
    try {
      val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
      } else {
        @Suppress("DEPRECATION")
        getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
      }

      if (vibrator != null && vibrator.hasVibrator()) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          try {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
          } catch (e: Exception) {
            vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
          }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
          @Suppress("DEPRECATION")
          vibrator.vibrate(80)
        }
      }
    } catch (ignored: Exception) {
    }

    try {
      val hapticConstant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        HapticFeedbackConstants.CONFIRM
      } else {
        HapticFeedbackConstants.KEYBOARD_TAP
      }
      @Suppress("DEPRECATION")
      binding.root.performHapticFeedback(
        hapticConstant,
        HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING or HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
      )
    } catch (ignored: Exception) {
    }
  }

  private fun parseWifiBarcode(barcode: Barcode, raw: String): Triple<String, String, String> {
    var ssid = barcode.wifi?.ssid
    var password = barcode.wifi?.password
    var encryption = when (barcode.wifi?.encryptionType) {
      Barcode.WiFi.TYPE_OPEN -> "None"
      Barcode.WiFi.TYPE_WEP -> "WEP"
      Barcode.WiFi.TYPE_WPA -> "WPA"
      else -> null
    }

    if (ssid.isNullOrEmpty() || encryption == null) {
      val tokens = raw.removePrefix("WIFI:").removePrefix("wifi:").split(";")
      for (token in tokens) {
        if (token.startsWith("S:", ignoreCase = true) && ssid.isNullOrEmpty()) {
          ssid = token.substring(2)
        } else if (token.startsWith("P:", ignoreCase = true) && password.isNullOrEmpty()) {
          password = token.substring(2)
        } else if (token.startsWith("T:", ignoreCase = true) && encryption == null) {
          val type = token.substring(2).uppercase()
          encryption = if (type == "NOPASS") "None" else type
        }
      }
    }

    return Triple(ssid.orEmpty().ifBlank { "Unknown Network" }, password.orEmpty(), encryption ?: "WPA")
  }

  private fun showSettingsDialog() {
    val dialogBinding = DialogSettingsBinding.inflate(layoutInflater)
    val dialog = BottomSheetDialog(this)
    dialog.setContentView(dialogBinding.root)

    val currentAutoOpen = prefs.getBoolean(KEY_AUTO_OPEN_URL, false)
    dialogBinding.switchAutoOpenUrl.isChecked = currentAutoOpen

    dialogBinding.switchAutoOpenUrl.setOnCheckedChangeListener { _, isChecked ->
      prefs.edit().putBoolean(KEY_AUTO_OPEN_URL, isChecked).apply()
    }

    dialogBinding.btnOpenHistory.setOnClickListener {
      dialog.dismiss()
      startActivity(Intent(this, HistoryActivity::class.java))
    }

    dialogBinding.btnCloseSettings.setOnClickListener {
      dialog.dismiss()
    }

    dialog.show()
  }

  private fun openUrl(url: String) {
    try {
      startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (ignored: ActivityNotFoundException) {
      Snackbar.make(binding.root, "No browser found to open link", Snackbar.LENGTH_SHORT).show()
    }
  }

  private fun scanBarcodeFromUri(uri: Uri) {
    try {
      val inputImage = InputImage.fromFilePath(this, uri)
      binding.overlayView.isLoading = true
      galleryScanner.process(inputImage)
        .addOnSuccessListener { codes ->
          binding.overlayView.isLoading = false
          val barcode = codes.firstOrNull()
          if (barcode != null) {
            onCodeDetected(barcode)
          } else {
            Snackbar.make(binding.root, "No QR code found in selected image", Snackbar.LENGTH_SHORT).show()
          }
        }
        .addOnFailureListener {
          binding.overlayView.isLoading = false
          Snackbar.make(binding.root, "Failed to analyze image", Snackbar.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
      binding.overlayView.isLoading = false
      Snackbar.make(binding.root, "Could not load image", Snackbar.LENGTH_SHORT).show()
    }
  }

  private fun showError(message: String) {
    Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
  }

  companion object {
    const val OPEN_SCANNER = "open_scanner"
    private const val PREFS_NAME = "quickie_scanner_settings"
    private const val KEY_AUTO_OPEN_URL = "key_auto_open_url"
  }
}
