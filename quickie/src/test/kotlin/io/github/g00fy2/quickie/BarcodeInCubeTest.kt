package io.github.g00fy2.quickie

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class BarcodeInCubeTest {

  private data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom
  }

  private fun isBarcodeInFrameCalc(
    barcodeLeft: Float,
    barcodeTop: Float,
    barcodeRight: Float,
    barcodeBottom: Float,
    viewWidth: Int,
    viewHeight: Int,
    frame: Box,
    imageProxyWidth: Int,
    imageProxyHeight: Int,
    rotationDegrees: Int
  ): Boolean {
    val rotatedWidth = if (rotationDegrees == 90 || rotationDegrees == 270) imageProxyHeight else imageProxyWidth
    val rotatedHeight = if (rotationDegrees == 90 || rotationDegrees == 270) imageProxyWidth else imageProxyHeight

    if (viewWidth <= 0 || viewHeight <= 0 || rotatedWidth <= 0 || rotatedHeight <= 0) return true

    val scale = maxOf(viewWidth.toFloat() / rotatedWidth, viewHeight.toFloat() / rotatedHeight)
    val scaledWidth = rotatedWidth * scale
    val scaledHeight = rotatedHeight * scale
    val offsetX = (viewWidth - scaledWidth) / 2f
    val offsetY = (viewHeight - scaledHeight) / 2f

    val barcodeCenterX = ((barcodeLeft + barcodeRight) / 2f) * scale + offsetX
    val barcodeCenterY = ((barcodeTop + barcodeBottom) / 2f) * scale + offsetY

    val tolerance = frame.width * 0.05f
    val expandedFrame = Box(
      frame.left - tolerance,
      frame.top - tolerance,
      frame.right + tolerance,
      frame.bottom + tolerance
    )

    return expandedFrame.contains(barcodeCenterX, barcodeCenterY)
  }

  @Test
  fun `Barcode centered in preview is within cube frame`() {
    val viewW = 1080
    val viewH = 2400
    val cubeFrame = Box(240f, 900f, 840f, 1500f)

    // Camera 1280x720 rotated 90deg -> 720x1280
    // Barcode centered at (360, 640)
    val result = isBarcodeInFrameCalc(
      310f, 590f, 410f, 690f,
      viewW,
      viewH,
      cubeFrame,
      1280,
      720,
      90
    )
    assertTrue(result, "Centered barcode must be accepted inside the cube")
  }

  @Test
  fun `Barcode outside cube frame at top is rejected`() {
    val viewW = 1080
    val viewH = 2400
    val cubeFrame = Box(240f, 900f, 840f, 1500f)

    // Barcode at top of camera frame (Y=100 in rotated 720x1280)
    val result = isBarcodeInFrameCalc(
      310f, 50f, 410f, 150f,
      viewW,
      viewH,
      cubeFrame,
      1280,
      720,
      90
    )
    assertFalse(result, "Barcode outside cube at top must be rejected")
  }

  @Test
  fun `Barcode outside cube frame at bottom is rejected`() {
    val viewW = 1080
    val viewH = 2400
    val cubeFrame = Box(240f, 900f, 840f, 1500f)

    // Barcode at bottom of camera frame (Y=1150 in rotated 720x1280)
    val result = isBarcodeInFrameCalc(
      310f, 1100f, 410f, 1200f,
      viewW,
      viewH,
      cubeFrame,
      1280,
      720,
      90
    )
    assertFalse(result, "Barcode outside cube at bottom must be rejected")
  }

  @Test
  fun `Barcode outside cube frame at far left is rejected`() {
    val viewW = 1080
    val viewH = 2400
    val cubeFrame = Box(240f, 900f, 840f, 1500f)

    // Barcode at left (X=50 in rotated 720x1280)
    val result = isBarcodeInFrameCalc(
      20f, 600f, 80f, 680f,
      viewW,
      viewH,
      cubeFrame,
      1280,
      720,
      90
    )
    assertFalse(result, "Barcode outside cube at far left must be rejected")
  }
}
