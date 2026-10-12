package io.github.g00fy2.quickie

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

public class QRCodeAnalyzer(
  private val barcodeFormats: IntArray,
  private val onSuccess: ((Barcode) -> Unit),
  private val onFailure: ((Exception) -> Unit),
  private val onPassCompleted: ((Boolean) -> Unit),
  private val validator: ((Barcode, ImageProxy) -> Boolean)? = null
) : ImageAnalysis.Analyzer {

  private val barcodeScanner by lazy {
    val optionsBuilder = if (barcodeFormats.size > 1) {
      BarcodeScannerOptions.Builder().setBarcodeFormats(barcodeFormats.first(), *barcodeFormats.drop(1).toIntArray())
    } else {
      BarcodeScannerOptions.Builder().setBarcodeFormats(barcodeFormats.firstOrNull() ?: Barcode.FORMAT_UNKNOWN)
    }
    try {
      BarcodeScanning.getClient(optionsBuilder.build())
    } catch (e: Exception) {
      onFailure(e)
      null
    }
  }

  private var isProcessing = false

  public fun reset() {
    isProcessing = false
  }

  @ExperimentalGetImage
  override fun analyze(imageProxy: ImageProxy) {
    val mediaImage = imageProxy.image
    if (mediaImage == null || isProcessing) {
      try {
        imageProxy.close()
      } catch (ignored: Exception) {
      }
      return
    }

    val scanner = barcodeScanner
    if (scanner == null) {
      try {
        imageProxy.close()
      } catch (ignored: Exception) {
      }
      return
    }

    isProcessing = true
    try {
      val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
      scanner.process(inputImage)
        .addOnSuccessListener { codes ->
          val validBarcode = if (validator != null) {
            codes.firstOrNull { validator.invoke(it, imageProxy) }
          } else {
            codes.firstNotNullOfOrNull { it }
          }
          validBarcode?.let { onSuccess(it) }
        }
        .addOnFailureListener {
          onFailure(it)
        }
        .addOnCompleteListener {
          isProcessing = false
          onPassCompleted(false)
          try {
            imageProxy.close()
          } catch (ignored: Exception) {
          }
        }
    } catch (e: Exception) {
      isProcessing = false
      try {
        imageProxy.close()
      } catch (ignored: Exception) {
      }
    }
  }
}
