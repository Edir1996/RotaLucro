package com.rotalucro.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
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
        val accent = ratingColor(result.rating)
        val outerPadding = dp(12, density)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(outerPadding, outerPadding, outerPadding, outerPadding)
            background = GradientDrawable().apply {
                cornerRadius = 18 * density
                setColor(Color.WHITE)
                setStroke(dp(4, density), accent)
            }
            elevation = 16 * density
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = TextView(this).apply {
            text = when (result.rating) {
                OfferRating.GOOD -> "ÓTIMA CORRIDA"
                OfferRating.ATTENTION -> "CORRIDA MÉDIA"
                OfferRating.BAD -> "CORRIDA RUIM"
            }
            setTextColor(accent)
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
        }

        val close = TextView(this).apply {
            text = "×"
            textSize = 26f
            gravity = Gravity.CENTER
            setTextColor(Color.DKGRAY)
            setPadding(dp(12, density), 0, 0, 0)
            setOnClickListener { removeOverlay() }
        }

        header.addView(
            title,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        header.addView(
            close,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val metrics = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(8, density), 0, dp(8, density))
        }

        metrics.addView(
            metricColumn(
                value = money(result.grossPerKm),
                label = "por km",
                color = ratingColor(result.perKmRating),
                density = density
            ),
            weightedParams()
        )
        metrics.addView(
            metricColumn(
                value = money(result.grossPerHour),
                label = "por hora",
                color = Color.rgb(55, 71, 79),
                density = density
            ),
            weightedParams()
        )
        metrics.addView(
            metricColumn(
                value = money(result.estimatedProfit),
                label = "lucro est.",
                color = if (result.estimatedProfit > 0) {
                    Color.rgb(46, 125, 50)
                } else {
                    Color.rgb(211, 47, 47)
                },
                density = density
            ),
            weightedParams()
        )

        val details = TextView(this).apply {
            text = buildString {
                append("Oferta ${money(result.fare)}  •  ${formatKm(result.totalDistanceKm)}  •  ${result.totalMinutes} min\n")
                append("${result.activeThreshold.name}: ")
                append("${money(result.activeThreshold.minimumPerKm)}–")
                append("${money(result.activeThreshold.excellentPerKm)}/km\n")
                append("Custos: ${money(result.estimatedCost)}")
            }
            setTextColor(Color.rgb(45, 45, 45))
            textSize = 13f
            gravity = Gravity.CENTER_HORIZONTAL
            setTypeface(typeface, Typeface.BOLD)
        }

        container.addView(header)
        container.addView(metrics)
        container.addView(details)

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
            y = dp(32, density)
            horizontalMargin = 0.035f
        }

        runCatching {
            windowManager.addView(container, params)
            overlayView = container
        }
    }

    private fun metricColumn(
        value: String,
        label: String,
        color: Int,
        density: Float
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(2, density), 0, dp(2, density), 0)

            addView(TextView(this@RideAccessibilityService).apply {
                text = value
                textSize = 16f
                gravity = Gravity.CENTER
                setTextColor(color)
                setTypeface(typeface, Typeface.BOLD)
                maxLines = 1
            })

            addView(TextView(this@RideAccessibilityService).apply {
                text = label
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(Color.DKGRAY)
            })
        }
    }

    private fun weightedParams() = LinearLayout.LayoutParams(
        0,
        LinearLayout.LayoutParams.WRAP_CONTENT,
        1f
    )

    private fun removeOverlay() {
        val view = overlayView ?: return
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        runCatching { windowManager.removeView(view) }
        overlayView = null
    }

    private fun ratingColor(rating: OfferRating): Int = when (rating) {
        OfferRating.GOOD -> Color.rgb(46, 125, 50)
        OfferRating.ATTENTION -> Color.rgb(249, 168, 37)
        OfferRating.BAD -> Color.rgb(211, 47, 47)
    }

    private fun money(value: Double): String =
        NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(value)

    private fun formatKm(value: Double): String =
        String.format(Locale("pt", "BR"), "%.1f km", value)

    private fun dp(value: Int, density: Float): Int = (value * density).toInt()

    private companion object {
        const val DRIVER_PACKAGE = "com.app99.driver"
    }
}
