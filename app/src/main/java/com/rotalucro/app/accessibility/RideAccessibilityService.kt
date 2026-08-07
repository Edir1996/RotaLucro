package com.rotalucro.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import com.rotalucro.app.calculator.OfferParser
import com.rotalucro.app.calculator.OfferRating
import com.rotalucro.app.calculator.RideCalculator
import com.rotalucro.app.calculator.RideOffer
import com.rotalucro.app.calculator.RideResult
import com.rotalucro.app.data.CaptureDiagnostics
import com.rotalucro.app.data.DiagnosticsStore
import com.rotalucro.app.data.SettingsStore
import java.text.NumberFormat
import java.util.Locale

class RideAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var overlayView: View? = null
    private var lastSignature: String? = null
    private var lastShownAt: Long = 0L
    private var pendingPackage: String = DRIVER_PACKAGE

    private val scanRunnable = Runnable { scanCurrentOffer(pendingPackage) }
    private val hideRunnable = Runnable { removeOverlay() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 80
            flags = flags or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            packageNames = arrayOf(DRIVER_PACKAGE)
        }
        OverlayController.attach(this)
        DiagnosticsStore.save(
            this,
            CaptureDiagnostics(
                timestamp = System.currentTimeMillis(),
                packageName = DRIVER_PACKAGE,
                success = false,
                message = "Serviço conectado. Aguardando uma oferta da 99."
            )
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString().orEmpty()
        if (!isDriverPackage(packageName)) return

        pendingPackage = packageName
        scheduleScans()
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        removeOverlay()
        OverlayController.detach(this)
        super.onDestroy()
    }

    internal fun showPreviewOverlay() {
        val settings = SettingsStore.load(this)
        val previewOffer = RideOffer(
            fare = 8.40,
            pickupDistanceKm = 2.0,
            tripDistanceKm = 2.3,
            pickupMinutes = 6,
            tripMinutes = 5,
            surgeMultiplier = 1.6,
            dynamicBaseFare = 1.27,
            productName = "Moto"
        )
        showOverlay(RideCalculator.calculate(previewOffer, settings), isPreview = true)
    }

    private fun scheduleScans() {
        handler.removeCallbacks(scanRunnable)
        handler.postDelayed(scanRunnable, 70)
        handler.postDelayed(scanRunnable, 260)
        handler.postDelayed(scanRunnable, 650)
        handler.postDelayed(scanRunnable, 1_100)
    }

    private fun scanCurrentOffer(packageName: String) {
        val texts = linkedSetOf<String>()
        val roots = mutableListOf<AccessibilityNodeInfo>()

        runCatching {
            windows.orEmpty()
                .mapNotNullTo(roots) { it.root }
        }
        rootInActiveWindow?.let { roots += it }

        roots.forEach { root ->
            runCatching { collectTexts(root, texts) }
            runCatching { root.recycle() }
        }

        val attempt = OfferParser.parseWithDiagnostics(texts.toList())
        val now = System.currentTimeMillis()
        val offer = attempt.offer

        if (offer == null) {
            DiagnosticsStore.save(
                this,
                CaptureDiagnostics(
                    timestamp = now,
                    packageName = packageName,
                    textCount = attempt.normalizedTextCount,
                    success = false,
                    message = attempt.reason
                )
            )
            return
        }

        val result = RideCalculator.calculate(offer, SettingsStore.load(this))
        val signature = buildString {
            append("%.2f".format(Locale.US, offer.fare))
            append('|')
            append("%.2f".format(Locale.US, result.totalDistanceKm))
            append('|')
            append(result.totalMinutes)
        }

        DiagnosticsStore.save(
            this,
            CaptureDiagnostics(
                timestamp = now,
                packageName = packageName,
                textCount = attempt.normalizedTextCount,
                success = true,
                message = attempt.reason,
                summary = "${money(result.fare)} • ${formatKm(result.totalDistanceKm)} • ${result.totalMinutes} min • ${money(result.grossPerKm)}/km"
            )
        )

        if (signature != lastSignature || now - lastShownAt > 2_000L) {
            lastSignature = signature
            lastShownAt = now
            showOverlay(result, isPreview = false)
        }
    }

    private fun collectTexts(node: AccessibilityNodeInfo?, output: MutableSet<String>) {
        if (node == null) return

        node.text?.toString()?.takeIf { it.isNotBlank() }?.let(output::add)
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let(output::add)
        node.hintText?.toString()?.takeIf { it.isNotBlank() }?.let(output::add)

        for (index in 0 until node.childCount) {
            val child = node.getChild(index)
            collectTexts(child, output)
            runCatching { child?.recycle() }
        }
    }

    private fun showOverlay(result: RideResult, isPreview: Boolean) {
        removeOverlay()
        handler.removeCallbacks(hideRunnable)

        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val density = resources.displayMetrics.density
        val accent = ratingColor(result.rating)
        val containerPadding = dp(12, density)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(containerPadding, dp(10, density), containerPadding, dp(11, density))
            background = roundedBackground(
                fillColor = Color.rgb(252, 253, 255),
                strokeColor = accent,
                strokeWidth = dp(3, density),
                radius = 18 * density
            )
            elevation = 18 * density
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val statusBadge = TextView(this).apply {
            text = if (isPreview) "PRÉVIA" else ratingLabel(result.rating)
            setTextColor(Color.WHITE)
            textSize = 11f
            letterSpacing = 0.08f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(10, density), dp(5, density), dp(10, density), dp(5, density))
            background = roundedBackground(accent, accent, 0, 40 * density)
        }

        val brandAndRule = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10, density), 0, 0, 0)
            addView(TextView(this@RideAccessibilityService).apply {
                text = "RotaLucro"
                setTextColor(Color.rgb(15, 23, 42))
                textSize = 14f
                setTypeface(typeface, Typeface.BOLD)
            })
            addView(TextView(this@RideAccessibilityService).apply {
                text = result.activeThreshold.name
                setTextColor(Color.rgb(100, 116, 139))
                textSize = 11f
            })
        }

        val close = TextView(this).apply {
            text = "×"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(71, 85, 105))
            setPadding(dp(10, density), 0, 0, 0)
            setOnClickListener { removeOverlay() }
            contentDescription = "Fechar análise"
        }

        topRow.addView(statusBadge)
        topRow.addView(
            brandAndRule,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        topRow.addView(close)

        val metricsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(10, density), 0, dp(8, density))
        }

        metricsRow.addView(
            metricColumn(
                value = money(result.grossPerKm),
                label = "POR KM",
                valueColor = accent,
                density = density
            ),
            weightedParams()
        )
        metricsRow.addView(verticalDivider(density))
        metricsRow.addView(
            metricColumn(
                value = money(result.grossPerHour),
                label = "POR HORA",
                valueColor = Color.rgb(15, 23, 42),
                density = density
            ),
            weightedParams()
        )
        metricsRow.addView(verticalDivider(density))
        metricsRow.addView(
            metricColumn(
                value = money(result.estimatedProfit),
                label = "LUCRO EST.",
                valueColor = if (result.estimatedProfit >= 0) Color.rgb(22, 163, 74) else Color.rgb(220, 38, 38),
                density = density
            ),
            weightedParams()
        )

        val detailLine = TextView(this).apply {
            text = buildString {
                append(money(result.fare))
                result.offer.surgeMultiplier?.let { append("  •  ${oneDecimal(it)}x") }
                append("  •  ${formatKm(result.totalDistanceKm)}")
                append("  •  ${result.totalMinutes} min")
            }
            setTextColor(Color.rgb(30, 41, 59))
            textSize = 13f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
        }

        val ruleLine = TextView(this).apply {
            text = "Ruim < ${money(result.activeThreshold.minimumPerKm)}  •  Média até ${money(result.activeThreshold.excellentPerKm)}  •  Ótima ≥ ${money(result.activeThreshold.excellentPerKm)}"
            setTextColor(Color.rgb(100, 116, 139))
            textSize = 10.5f
            gravity = Gravity.CENTER
            setPadding(0, dp(4, density), 0, 0)
        }

        container.addView(topRow)
        container.addView(metricsRow)
        container.addView(detailLine)
        container.addView(ruleLine)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(42, density)
            horizontalMargin = 0.025f
        }

        runCatching {
            windowManager.addView(container, params)
            overlayView = container
            val timeoutSeconds = SettingsStore.load(this).overlayAutoHideSeconds.coerceIn(8, 45)
            handler.postDelayed(hideRunnable, timeoutSeconds * 1_000L)
        }
    }

    private fun metricColumn(
        value: String,
        label: String,
        valueColor: Int,
        density: Float
    ): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(3, density), 0, dp(3, density), 0)

        addView(TextView(this@RideAccessibilityService).apply {
            text = value
            textSize = 16.5f
            gravity = Gravity.CENTER
            setTextColor(valueColor)
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
        })
        addView(TextView(this@RideAccessibilityService).apply {
            text = label
            textSize = 9.5f
            letterSpacing = 0.06f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(100, 116, 139))
            setTypeface(typeface, Typeface.BOLD)
        })
    }

    private fun verticalDivider(density: Float): View = Space(this).apply {
        background = GradientDrawable().apply { setColor(Color.rgb(226, 232, 240)) }
        layoutParams = LinearLayout.LayoutParams(dp(1, density), dp(38, density))
    }

    private fun weightedParams() = LinearLayout.LayoutParams(
        0,
        LinearLayout.LayoutParams.WRAP_CONTENT,
        1f
    )

    private fun roundedBackground(
        fillColor: Int,
        strokeColor: Int,
        strokeWidth: Int,
        radius: Float
    ) = GradientDrawable().apply {
        cornerRadius = radius
        setColor(fillColor)
        if (strokeWidth > 0) setStroke(strokeWidth, strokeColor)
    }

    private fun removeOverlay() {
        handler.removeCallbacks(hideRunnable)
        val view = overlayView ?: return
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        runCatching { windowManager.removeView(view) }
        overlayView = null
    }

    private fun isDriverPackage(packageName: String): Boolean =
        packageName == DRIVER_PACKAGE || packageName.startsWith("$DRIVER_PACKAGE.")

    private fun ratingLabel(rating: OfferRating): String = when (rating) {
        OfferRating.GOOD -> "ÓTIMA"
        OfferRating.ATTENTION -> "MÉDIA"
        OfferRating.BAD -> "RUIM"
    }

    private fun ratingColor(rating: OfferRating): Int = when (rating) {
        OfferRating.GOOD -> Color.rgb(22, 163, 74)
        OfferRating.ATTENTION -> Color.rgb(234, 179, 8)
        OfferRating.BAD -> Color.rgb(220, 38, 38)
    }

    private fun money(value: Double): String =
        NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(value)

    private fun formatKm(value: Double): String =
        String.format(Locale("pt", "BR"), "%.1f km", value)

    private fun oneDecimal(value: Double): String =
        String.format(Locale("pt", "BR"), "%.1f", value)

    private fun dp(value: Int, density: Float): Int = (value * density).toInt()

    private companion object {
        const val DRIVER_PACKAGE = "com.app99.driver"
    }
}
