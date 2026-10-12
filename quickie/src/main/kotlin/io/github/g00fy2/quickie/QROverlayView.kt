package io.github.g00fy2.quickie

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Resources.NotFoundException
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuff.Mode.CLEAR
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.TransitionDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.DrawableCompat
import com.google.mlkit.vision.barcode.common.Barcode
import io.github.g00fy2.quickie.databinding.QuickieOverlayViewBinding
import kotlin.math.min
import kotlin.math.roundToInt

@Suppress("TooManyFunctions")
public class QROverlayView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

  private val binding = QuickieOverlayViewBinding.inflate(LayoutInflater.from(context), this)
  private val grayColor = ContextCompat.getColor(context, R.color.quickie_gray)
  private val accentColor = getAccentColor()
  private val backgroundColor = ColorUtils.setAlphaComponent(Color.BLACK, BACKGROUND_ALPHA.roundToInt())
  private val alphaPaint = Paint().apply { alpha = BACKGROUND_ALPHA.roundToInt() }
  private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val loadingBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = backgroundColor }
  private val transparentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.TRANSPARENT
    xfermode = PorterDuffXfermode(CLEAR)
  }
  private val outerRadius = OUT_RADIUS.toPx()
  private val innerRadius = (OUT_RADIUS - STROKE_WIDTH).toPx()
  private val outerFrame = RectF()
  private val innerFrame = RectF()
  private var maskBitmap: Bitmap? = null
  private var maskCanvas: Canvas? = null
  private var horizontalFrameRatio = 1f
  var isHighlighted = false
    set(value) {
      if (field != value) {
        field = value
        invalidate()
      }
    }
  var isLoading = false
    set(value) {
      if (field != value) {
        field = value
        binding.progressView.visibility = if (value) VISIBLE else GONE
      }
    }

  init {
    setWillNotDraw(false)
  }

  override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
    super.onLayout(changed, left, top, right, bottom)

    if ((changed || maskBitmap == null || maskBitmap?.width != width || maskBitmap?.height != height) && width > 0 && height > 0) {
      maskBitmap?.recycle()
      maskBitmap = createBitmap(width, height).apply { maskCanvas = Canvas(this) }
      calculateFrameAndTitlePos()
    }
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    maskBitmap?.recycle()
    maskBitmap = null
    maskCanvas = null
  }

  override fun onDraw(canvas: Canvas) {
    val bitmap = maskBitmap ?: return
    val maskC = maskCanvas ?: return
    strokePaint.color = if (isHighlighted) accentColor else grayColor
    maskC.drawColor(backgroundColor)
    maskC.drawRoundRect(outerFrame, outerRadius, outerRadius, strokePaint)
    maskC.drawRoundRect(innerFrame, innerRadius, innerRadius, transparentPaint)
    if (isLoading) maskC.drawRoundRect(innerFrame, innerRadius, innerRadius, loadingBackgroundPaint)
    canvas.drawBitmap(bitmap, 0f, 0f, alphaPaint)
    super.onDraw(canvas)
  }

  fun setCustomText(stringRes: Int) {
    if (stringRes != 0) {
      try {
        binding.titleTextView.setText(stringRes)
      } catch (ignore: NotFoundException) {
        // string resource not found
      }
    }
  }

  fun setCustomIcon(drawableRes: Int?) {
    if (drawableRes == null) {
      binding.titleTextView.setCompoundDrawables(null, null, null, null)
    } else if (drawableRes != 0) {
      try {
        ResourcesCompat.getDrawable(resources, drawableRes, null)?.limitDrawableSize()?.let {
          binding.titleTextView.setCompoundDrawables(null, it, null, null)
        }
      } catch (ignore: NotFoundException) {
        // drawable resource not found
      }
    }
  }

  fun setHorizontalFrameRatio(ratio: Float) {
    if (ratio > 1f) {
      horizontalFrameRatio = ratio
      calculateFrameAndTitlePos()
    }
  }

  fun getFrameRect(): RectF {
    if (outerFrame.isEmpty && width > 0 && height > 0) {
      calculateFrameAndTitlePos()
    }
    return if (!outerFrame.isEmpty) {
      RectF(outerFrame)
    } else {
      RectF()
    }
  }

  fun isBarcodeInFrame(
    barcode: Barcode,
    imageProxyWidth: Int,
    imageProxyHeight: Int,
    rotationDegrees: Int
  ): Boolean {
    val frame = getFrameRect()
    if (frame.isEmpty) return false

    val bbox = barcode.boundingBox ?: return true

    val rotatedWidth = if (rotationDegrees == 90 || rotationDegrees == 270) imageProxyHeight else imageProxyWidth
    val rotatedHeight = if (rotationDegrees == 90 || rotationDegrees == 270) imageProxyWidth else imageProxyHeight

    if (width <= 0 || height <= 0 || rotatedWidth <= 0 || rotatedHeight <= 0) return false

    val scale = maxOf(width.toFloat() / rotatedWidth, height.toFloat() / rotatedHeight)
    val scaledWidth = rotatedWidth * scale
    val scaledHeight = rotatedHeight * scale
    val offsetX = (width - scaledWidth) / 2f
    val offsetY = (height - scaledHeight) / 2f

    val barcodeCenterX = bbox.centerX().toFloat() * scale + offsetX
    val barcodeCenterY = bbox.centerY().toFloat() * scale + offsetY

    // Check if the center of the barcode is within the frame, or intersects with an expanded frame tolerance
    if (frame.contains(barcodeCenterX, barcodeCenterY)) return true

    val barcodeRect = RectF(
      bbox.left.toFloat() * scale + offsetX,
      bbox.top.toFloat() * scale + offsetY,
      bbox.right.toFloat() * scale + offsetX,
      bbox.bottom.toFloat() * scale + offsetY
    )
    val tolerance = 24f.toPx()
    val expandedFrame = RectF(frame).apply { inset(-tolerance, -tolerance) }
    return RectF.intersects(expandedFrame, barcodeRect)
  }

  fun setCloseVisibilityAndOnClick(visible: Boolean, action: () -> Unit = {}) {
    binding.closeImageView.visibility = GONE
  }

  fun setTorchVisibilityAndOnClick(visible: Boolean, action: (Boolean) -> Unit = {}) {
    binding.torchImageView.visibility = if (visible) VISIBLE else GONE
    binding.torchImageView.setOnClickListener { action(!it.isSelected) }
  }

  fun setTorchState(on: Boolean) {
    if (binding.torchImageView.isSelected == on) return
    binding.torchImageView.isSelected = on

    // Tactile haptic feedback
    try {
      binding.torchImageView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    } catch (ignored: Exception) {
    }

    // Instant state change with zero animation
    binding.torchImageView.setBackgroundResource(
      if (on) R.drawable.quickie_bg_torch_on else R.drawable.quickie_bg_round
    )
    binding.torchImageView.setColorFilter(
      if (on) Color.parseColor("#FFF59D") else Color.WHITE,
      PorterDuff.Mode.SRC_IN
    )
    binding.torchImageView.rotation = 0f
    binding.torchImageView.scaleX = 1f
    binding.torchImageView.scaleY = 1f
  }

  private fun calculateFrameAndTitlePos() {
    val centralX = width / 2
    val centralY = height / 2
    val minLength = min(centralX, centralY)
    val marginRatio = if (horizontalFrameRatio > 1f) {
      FRAME_MARGIN_RATIO * ((1f / horizontalFrameRatio) * 1.5f)
    } else {
      FRAME_MARGIN_RATIO
    }
    val strokeLength = minLength - (minLength * marginRatio)
    val strokeWidth = STROKE_WIDTH.toPx()
    outerFrame.set(
      centralX - strokeLength,
      centralY - strokeLength / horizontalFrameRatio,
      centralX + strokeLength,
      centralY + strokeLength / horizontalFrameRatio
    )
    innerFrame.set(
      outerFrame.left + strokeWidth,
      outerFrame.top + strokeWidth,
      outerFrame.right - strokeWidth,
      outerFrame.bottom - strokeWidth
    )

    val topInsetsToOuterFrame = (-paddingTop + centralY - strokeLength).roundToInt()
    val titleCenter = (topInsetsToOuterFrame - binding.titleTextView.height) / 2
    binding.titleTextView.updateTopMargin(titleCenter)
    // hide title text if not enough vertical space
    binding.titleTextView.visibility =
      if (topInsetsToOuterFrame < binding.titleTextView.height) INVISIBLE else VISIBLE
  }

  private fun getAccentColor(): Int {
    return TypedValue().let {
      if (context.theme.resolveAttribute(android.R.attr.colorAccent, it, true)) {
        it.data
      } else {
        ContextCompat.getColor(context, R.color.quickie_accent_fallback)
      }
    }
  }

  private fun View.updateTopMargin(topPx: Int) {
    val params = layoutParams as MarginLayoutParams
    params.topMargin = topPx
    layoutParams = params
  }

  private fun Drawable.limitDrawableSize(): Drawable {
    val heightLimit = ICON_MAX_HEIGHT.toPx()
    val scale = heightLimit / minimumHeight
    if (scale < 1) {
      setBounds(0, 0, (minimumWidth * scale).roundToInt(), (minimumHeight * scale).roundToInt())
    } else {
      setBounds(0, 0, minimumWidth, minimumHeight)
    }
    return this
  }

  private fun View.setTintAndStateAwareBackground() {
    background?.let { drawable ->
      val wrappedDrawable = DrawableCompat.wrap(drawable)

      val states = arrayOf(
        intArrayOf(android.R.attr.state_pressed, android.R.attr.state_selected),
        intArrayOf(android.R.attr.state_pressed, -android.R.attr.state_selected),
        intArrayOf(-android.R.attr.state_pressed, android.R.attr.state_selected),
        intArrayOf()
      )
      val stateColors = intArrayOf(grayColor, accentColor, accentColor, grayColor)
      val colorStateList = ColorStateList(states, stateColors).withAlpha(BUTTON_BACKGROUND_ALPHA.roundToInt())

      DrawableCompat.setTintList(wrappedDrawable, colorStateList)
      background = wrappedDrawable
    }
  }

  private fun Float.toPx() =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this, resources.displayMetrics)

  companion object {
    private const val BACKGROUND_ALPHA = 0.77 * 255
    private const val BUTTON_BACKGROUND_ALPHA = 0.6 * 255
    private const val STROKE_WIDTH = 4f
    private const val OUT_RADIUS = 16f
    private const val FRAME_MARGIN_RATIO = 1f / 4
    private const val ICON_MAX_HEIGHT = 56f
  }
}