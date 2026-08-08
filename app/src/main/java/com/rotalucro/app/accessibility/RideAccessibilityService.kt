package com.rotalucro.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.rotalucro.app.MainActivity
import com.rotalucro.app.R
import com.rotalucro.app.calculator.OfferRating
import com.rotalucro.app.data.RideHistoryStore
import com.rotalucro.app.data.SettingsStore
import com.rotalucro.app.ocr.OcrCaptureService
import com.rotalucro.app.ocr.RideOverlayBus
import com.rotalucro.app.runtime.RuntimeState
import kotlin.math.abs

class RideAccessibilityService : AccessibilityService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager
    private var bubble: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var bubbleStatus: View? = null
    private var menu: View? = null
    private var resultBox: View? = null
    private var resultHideRunnable: Runnable? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                RideOverlayBus.ACTION_RESULT -> showRideResult(intent)
                OcrCaptureService.ACTION_STATUS_CHANGED -> updateBubbleStatus()
                ACTION_SHOW_BUBBLE -> showBubble()
                ACTION_HIDE_BUBBLE -> hideBubble()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        RuntimeState.accessibilityConnected = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        registerInternalReceiver()
        if (getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_BUBBLE_VISIBLE, true)) showBubble()
        mainHandler.post(statusTicker)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString().orEmpty()
        // Não derruba o estado da 99 por causa de eventos do SystemUI, teclado ou overlays.
        if (pkg == PACKAGE_99) {
            RuntimeState.currentPackage = pkg
            RuntimeState.is99Visible = true
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        RuntimeState.accessibilityConnected = false
        RuntimeState.is99Visible = false
        mainHandler.removeCallbacksAndMessages(null)
        try { unregisterReceiver(receiver) } catch (_: Throwable) {}
        removeView(bubble); removeView(menu); removeView(resultBox)
        bubble = null; menu = null; resultBox = null
        super.onDestroy()
    }

    private fun registerInternalReceiver() {
        val filter = IntentFilter().apply {
            addAction(RideOverlayBus.ACTION_RESULT)
            addAction(OcrCaptureService.ACTION_STATUS_CHANGED)
            addAction(ACTION_SHOW_BUBBLE)
            addAction(ACTION_HIDE_BUBBLE)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(receiver, filter)
    }

    private val statusTicker = object : Runnable {
        override fun run() {
            // rootInActiveWindow é mais estável que qualquer evento isolado para saber o app à frente.
            val pkg = runCatching { rootInActiveWindow?.packageName?.toString().orEmpty() }.getOrDefault("")
            if (pkg.isNotBlank()) {
                RuntimeState.currentPackage = pkg
                RuntimeState.is99Visible = pkg == PACKAGE_99
            }
            updateBubbleStatus()
            mainHandler.postDelayed(this, 900L)
        }
    }

    private fun showBubble() {
        if (!::windowManager.isInitialized || bubble != null) return
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_BUBBLE_VISIBLE, true).apply()
        RuntimeState.bubbleVisible = true

        val size = dp(62)
        val root = FrameLayout(this).apply {
            background = circleDrawable("#FFFFFF", stroke = "#D8E4F5")
            elevation = dp(11).toFloat()
        }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.ic_launcher_foreground)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }
        root.addView(logo, FrameLayout.LayoutParams(-1, -1))

        val dot = View(this).apply { background = circleDrawable("#64748B") }
        root.addView(dot, FrameLayout.LayoutParams(dp(14), dp(14), Gravity.END or Gravity.BOTTOM).apply {
            rightMargin = dp(2); bottomMargin = dp(2)
        })
        bubbleStatus = dot

        val metrics = resources.displayMetrics
        val defaultX = (metrics.widthPixels - size - dp(14)).coerceAtLeast(0)
        val params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt(KEY_BUBBLE_X, defaultX).coerceIn(0, (metrics.widthPixels - size).coerceAtLeast(0))
            y = prefs.getInt(KEY_BUBBLE_Y, dp(150)).coerceIn(dp(20), (metrics.heightPixels - dp(90)).coerceAtLeast(dp(20)))
        }
        attachBubbleTouch(root, params)
        windowManager.addView(root, params)
        bubble = root; bubbleParams = params
        updateBubbleStatus()
    }

    private fun attachBubbleTouch(view: View, params: WindowManager.LayoutParams) {
        var startX = 0; var startY = 0; var touchX = 0f; var touchY = 0f; var moved = false
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x; startY = params.y; touchX = event.rawX; touchY = event.rawY; moved = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt(); val dy = (event.rawY - touchY).toInt()
                    if (abs(dx) > dp(5) || abs(dy) > dp(5)) moved = true
                    params.x = (startX + dx).coerceIn(0, (resources.displayMetrics.widthPixels - dp(50)).coerceAtLeast(0))
                    params.y = (startY + dy).coerceIn(dp(20), (resources.displayMetrics.heightPixels - dp(90)).coerceAtLeast(dp(20)))
                    try { windowManager.updateViewLayout(view, params) } catch (_: Throwable) {}
                    if (menu != null) hideMenu(); true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(KEY_BUBBLE_X, params.x).putInt(KEY_BUBBLE_Y, params.y).apply()
                    else toggleMenu()
                    true
                }
                else -> false
            }
        }
    }

    private fun toggleMenu() { if (menu == null) showMenu() else hideMenu() }

    private fun showMenu() {
        val bubbleP = bubbleParams ?: return
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = roundedDrawable("#FFFFFF", 18f, "#E2E8F0", dp(1))
            elevation = dp(12).toFloat()
        }
        panel.addView(TextView(this).apply {
            text = "RotaLucro"; setTextColor(Color.parseColor("#0F172A")); textSize = 18f; typeface = Typeface.DEFAULT_BOLD
        })
        panel.addView(TextView(this).apply {
            text = if (RuntimeState.captureActive) "OCR ativo • monitorando" else "OCR pausado"
            setTextColor(Color.parseColor(if (RuntimeState.captureActive) "#16A34A" else "#64748B"))
            textSize = 12f; setPadding(0, dp(2), 0, dp(10))
        })

        addMenuButton(panel, if (RuntimeState.captureActive) "Desativar OCR" else "Ativar OCR") {
            if (RuntimeState.captureActive) OcrCaptureService.stop(this) else openApp(true)
            updateBubbleStatus(); hideMenu()
        }
        addMenuButton(panel, "Ler agora") { OcrCaptureService.scanNow(this); hideMenu() }
        addMenuButton(panel, "Salvar última como aceita") {
            val saved = RideHistoryStore.saveLatestAsAccepted(this)
            Toast.makeText(this, if (saved != null) "Corrida salva no histórico" else "Nenhuma oferta recente para salvar", Toast.LENGTH_SHORT).show()
            hideMenu()
        }
        addMenuButton(panel, "Abrir RotaLucro") { openApp(false); hideMenu() }
        addMenuButton(panel, "Ocultar bolha", destructive = true) { hideMenu(); hideBubble() }

        val panelWidth = dp(238)
        val params = WindowManager.LayoutParams(
            panelWidth, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (bubbleP.x - panelWidth + dp(62)).coerceIn(dp(8), (resources.displayMetrics.widthPixels - panelWidth - dp(8)).coerceAtLeast(dp(8)))
            y = (bubbleP.y + dp(72)).coerceAtMost(resources.displayMetrics.heightPixels - dp(390))
        }
        windowManager.addView(panel, params); menu = panel
    }

    private fun addMenuButton(parent: LinearLayout, label: String, destructive: Boolean = false, action: () -> Unit) {
        parent.addView(TextView(this).apply {
            text = label
            setTextColor(Color.parseColor(if (destructive) "#DC2626" else "#0F172A"))
            textSize = 14.5f; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(10), dp(12), dp(10))
            background = roundedDrawable(if (destructive) "#FEF2F2" else "#F8FAFC", 11f)
            setOnClickListener { action() }
        }, LinearLayout.LayoutParams(-1, dp(44)).apply { bottomMargin = dp(7) })
    }

    private fun hideMenu() { removeView(menu); menu = null }

    private fun hideBubble() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_BUBBLE_VISIBLE, false).apply()
        RuntimeState.bubbleVisible = false; hideMenu(); removeView(bubble)
        bubble = null; bubbleParams = null; bubbleStatus = null
    }

    private fun updateBubbleStatus() {
        val color = when {
            RuntimeState.captureActive && RuntimeState.is99Visible -> "#22C55E"
            RuntimeState.captureActive -> "#38BDF8"
            else -> "#64748B"
        }
        bubbleStatus?.background = circleDrawable(color)
    }

    private fun showRideResult(intent: Intent) {
        removeView(resultBox); resultBox = null
        resultHideRunnable?.let { mainHandler.removeCallbacks(it) }

        val settings = SettingsStore.load(this)
        val basePerKm = intent.getDoubleExtra(RideOverlayBus.EXTRA_PER_KM, 0.0)
        val analysisPerKm = intent.getDoubleExtra(RideOverlayBus.EXTRA_ANALYSIS_PER_KM, basePerKm)
        val analysisPerHour = intent.getDoubleExtra(RideOverlayBus.EXTRA_ANALYSIS_PER_HOUR, intent.getDoubleExtra(RideOverlayBus.EXTRA_PER_HOUR, 0.0))
        val distance = intent.getDoubleExtra(RideOverlayBus.EXTRA_DISTANCE, 0.0)
        val minutes = intent.getIntExtra(RideOverlayBus.EXTRA_MINUTES, 0)
        val fare = intent.getDoubleExtra(RideOverlayBus.EXTRA_FARE, 0.0)
        val profit = intent.getDoubleExtra(RideOverlayBus.EXTRA_PROFIT, 0.0)
        val min = intent.getDoubleExtra(RideOverlayBus.EXTRA_MINIMUM, 0.0)
        val excellent = intent.getDoubleExtra(RideOverlayBus.EXTRA_EXCELLENT, 0.0)
        val thresholdName = intent.getStringExtra(RideOverlayBus.EXTRA_THRESHOLD_NAME).orEmpty()
        val possibleReturn = intent.getBooleanExtra(RideOverlayBus.EXTRA_EMPTY_RETURN, false)
        val returnKm = intent.getDoubleExtra(RideOverlayBus.EXTRA_EMPTY_RETURN_KM, 0.0)
        val rating = runCatching { OfferRating.valueOf(intent.getStringExtra(RideOverlayBus.EXTRA_RATING).orEmpty()) }.getOrDefault(OfferRating.ATTENTION)

        val accent = safeColor(when (rating) {
            OfferRating.BAD -> settings.overlayBadHex
            OfferRating.ATTENTION -> settings.overlayAttentionHex
            OfferRating.GOOD -> settings.overlayGoodHex
        }, when (rating) {
            OfferRating.BAD -> "#EF4444"
            OfferRating.ATTENTION -> "#F59E0B"
            OfferRating.GOOD -> "#22C55E"
        })
        val background = safeColor(settings.overlayBackgroundHex, "#FFFFFF")
        val textColor = safeColor(settings.overlayTextHex, "#0F172A")
        val secondary = withAlpha(textColor, 0.72f)
        val ratingLabel = when (rating) { OfferRating.BAD -> "RUIM"; OfferRating.ATTENTION -> "MÉDIA"; OfferRating.GOOD -> "ÓTIMA" }
        val scale = settings.overlayScalePercent.coerceIn(75, 135) / 100f
        fun sp(base: Float) = base * scale
        fun pad(base: Int) = (dp(base) * scale).toInt().coerceAtLeast(1)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad(16), pad(11), pad(16), pad(12))
            background = roundedDrawable(background, 18f, accent, dp(3))
            elevation = dp(14).toFloat()
            alpha = settings.overlayOpacityPercent.coerceIn(35, 100) / 100f
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(TextView(this).apply {
            text = money(analysisPerKm) + "/km"
            setTextColor(Color.parseColor(textColor)); textSize = sp(25f); typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(TextView(this).apply {
            text = ratingLabel; setTextColor(Color.WHITE); textSize = sp(12f); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setPadding(pad(10), pad(6), pad(10), pad(6)); background = roundedDrawable(accent, 20f)
        })
        card.addView(top)

        card.addView(TextView(this).apply {
            text = "${money(analysisPerHour)}/h  •  ${oneDecimal(distance)} km  •  $minutes min  •  ${money(fare)}"
            setTextColor(Color.parseColor(secondary)); textSize = sp(13.2f); setPadding(0, pad(5), 0, 0)
        })
        if (possibleReturn) {
            card.addView(TextView(this).apply {
                text = "⚠ Possível retorno vazio +${oneDecimal(returnKm)} km  •  oferta ${money(basePerKm)}/km"
                setTextColor(Color.parseColor(accent)); textSize = sp(11.8f); typeface = Typeface.DEFAULT_BOLD; setPadding(0, pad(4), 0, 0)
            })
        }
        card.addView(TextView(this).apply {
            text = "$thresholdName  •  mín ${money(min)}  •  ótima ${money(excellent)}  •  lucro est. ${money(profit)}"
            setTextColor(Color.parseColor(secondary)); textSize = sp(11.2f); setPadding(0, pad(3), 0, 0)
        })

        val metrics = resources.displayMetrics
        val width = (metrics.widthPixels * settings.overlayWidthPercent.coerceIn(55, 100) / 100f).toInt()
        val maxX = (metrics.widthPixels - width).coerceAtLeast(0)
        val xPx = (maxX * settings.overlayXPercent.coerceIn(0, 100) / 100f).toInt()
        val yPx = (metrics.heightPixels * settings.overlayYPercent.coerceIn(0, 75) / 100f).toInt()
        val params = WindowManager.LayoutParams(
            width, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = xPx; y = yPx }
        windowManager.addView(card, params); resultBox = card

        val seconds = settings.overlayAutoHideSeconds
        val runnable = Runnable { removeView(resultBox); resultBox = null }
        resultHideRunnable = runnable; mainHandler.postDelayed(runnable, seconds * 1000L)
    }

    private fun openApp(requestCapture: Boolean) {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(MainActivity.EXTRA_REQUEST_CAPTURE, requestCapture))
    }

    private fun removeView(view: View?) { if (view != null && ::windowManager.isInitialized) try { windowManager.removeView(view) } catch (_: Throwable) {} }

    private fun roundedDrawable(fill: String, radiusDp: Float, strokeColor: String? = null, strokeWidth: Int = 0) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE; setColor(Color.parseColor(fill)); cornerRadius = dp(radiusDp.toInt()).toFloat()
        if (strokeColor != null && strokeWidth > 0) setStroke(strokeWidth, Color.parseColor(strokeColor))
    }
    private fun circleDrawable(fill: String, stroke: String = "#FFFFFF") = GradientDrawable().apply {
        shape = GradientDrawable.OVAL; setColor(Color.parseColor(fill)); setStroke(dp(2), Color.parseColor(stroke))
    }
    private fun safeColor(raw: String, fallback: String): String = runCatching { Color.parseColor(raw); raw }.getOrDefault(fallback)
    private fun withAlpha(hex: String, fraction: Float): String {
        val c = Color.parseColor(hex); val a = (255 * fraction.coerceIn(0f, 1f)).toInt()
        return String.format("#%02X%02X%02X%02X", a, Color.red(c), Color.green(c), Color.blue(c))
    }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun money(value: Double): String = "R$${"%.2f".format(value).replace('.', ',')}"
    private fun oneDecimal(value: Double): String = "%.1f".format(value).replace('.', ',')

    companion object {
        const val PACKAGE_99 = "com.app99.driver"
        const val ACTION_SHOW_BUBBLE = "com.rotalucro.app.action.SHOW_BUBBLE"
        const val ACTION_HIDE_BUBBLE = "com.rotalucro.app.action.HIDE_BUBBLE"
        private const val PREFS = "bubble_settings"
        private const val KEY_BUBBLE_VISIBLE = "visible"
        private const val KEY_BUBBLE_X = "x"
        private const val KEY_BUBBLE_Y = "y"
    }
}
