package com.rotalucro.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.rotalucro.app.calculator.OfferParser
import com.rotalucro.app.calculator.OfferRating
import com.rotalucro.app.calculator.RideCalculator
import com.rotalucro.app.calculator.RideResult
import com.rotalucro.app.data.SettingsStore
import java.text.NumberFormat
import java.util.Locale

class RideAccessibilityService : AccessibilityService() {
    private var overlayView: View? = null
    private var lastSignature: String? = null
    private var lastShownAt: Long = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName?.toString() != DRIVER_PACKAGE) return

        val root = rootInActiveWindow ?: return
        val texts = mutableListOf<String>()
        collectTexts(root, texts)

        val signature = texts.joinToString("|")
        val now = System.currentTimeMillis()
        if (signature == lastSignature && now - lastShownAt < 2_000) return

        val offer = OfferParser.parse(texts) ?: return
        val result = RideCalculator.calculate(offer, SettingsStore.load(this))

        lastSignature = signature
        lastShownAt = now
        showOverlay(result)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }

    private fun collectTexts(node: AccessibilityNodeInfo?, output: MutableList<String>) {
        if (node == null) return
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let(output::add)
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let(output::add)

        for (index in 0 until node.childCount) {
            val child = node.getChild(index)
            collectTexts(child, output)
            child?.recycle()
        }
    }

    private fun showOverlay(result: RideResult) {
        removeOverlay()

        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val density = resources.displayMetrics.density
        val padding = (14 * density).toInt()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            background = GradientDrawable().apply {
                cornerRadius = 18 * density
                setColor(backgroundColor(result.rating))
            }
            elevation = 12 * density
        }

        val title = TextView(this).apply {
            text = when (result.rating) {
                OfferRating.GOOD -> "BOA CORRIDA"
                OfferRating.ATTENTION -> "ANALISE COM CALMA"
                OfferRating.BAD -> "CORRIDA FRACA"
            }
            setTextColor(Color.WHITE)
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

        val details = TextView(this).apply {
            text = buildString {
                append("${money(result.grossPerKm)}/km  •  ${money(result.grossPerHour)}/h\n")
                append("Lucro estimado: ${money(result.estimatedProfit)}  •  ${formatKm(result.totalDistanceKm)}")
            }
            setTextColor(Color.WHITE)
            textSize = 15f
            setPadding(0, (6 * density).toInt(), 0, 0)
        }

        val close = Button(this).apply {
            text = "Fechar"
            setOnClickListener { removeOverlay() }
        }

        container.addView(title)
        container.addView(details)
        container.addView(close)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = (36 * density).toInt()
            horizontalMargin = 0.04f
        }

        runCatching {
            windowManager.addView(container, params)
            overlayView = container
        }
    }

    private fun removeOverlay() {
        val view = overlayView ?: return
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        runCatching { windowManager.removeView(view) }
        overlayView = null
    }

    private fun backgroundColor(rating: OfferRating): Int = when (rating) {
        OfferRating.GOOD -> Color.rgb(22, 124, 70)
        OfferRating.ATTENTION -> Color.rgb(180, 108, 0)
        OfferRating.BAD -> Color.rgb(170, 45, 45)
    }

    private fun money(value: Double): String =
        NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(value)

    private fun formatKm(value: Double): String =
        String.format(Locale("pt", "BR"), "%.1f km", value)

    private companion object {
        const val DRIVER_PACKAGE = "com.app99.driver"
    }
}
