package com.rotalucro.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Rect
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.rotalucro.app.MainActivity
import com.rotalucro.app.R
import com.rotalucro.app.calculator.OfferRating
import com.rotalucro.app.calculator.RideRecommendation
import com.rotalucro.app.data.RideHistoryStore
import com.rotalucro.app.data.SettingsStore
import com.rotalucro.app.ocr.AccessibilityOcrEngine
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
    private lateinit var visualReader: AccessibilityOcrEngine
    private var lastForegroundPackage: String = ""
    private var last99EventAtElapsed: Long = 0L

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                RideOverlayBus.ACTION_RESULT -> showRideResult(intent)
                OcrCaptureService.ACTION_STATUS_CHANGED -> updateBubbleStatus()
                ACTION_SHOW_BUBBLE -> showBubble()
                ACTION_HIDE_BUBBLE -> hideBubble()
                ACTION_SET_READER_ENABLED -> {
                    val enabled = intent.getBooleanExtra(EXTRA_READER_ENABLED, true)
                    getSharedPreferences(READER_PREFS, MODE_PRIVATE).edit().putBoolean(KEY_READER_ENABLED, enabled).apply()
                    if (::visualReader.isInitialized) visualReader.setEnabled(enabled)
                    updateBubbleStatus()
                }
                ACTION_SCAN_NOW -> if (::visualReader.isInitialized) visualReader.scanNow()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        RuntimeState.accessibilityConnected = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        registerInternalReceiver()
        visualReader = AccessibilityOcrEngine(this)
        val readerEnabled = getSharedPreferences(READER_PREFS, MODE_PRIVATE).getBoolean(KEY_READER_ENABLED, true)
        visualReader.start(readerEnabled)
        if (getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_BUBBLE_VISIBLE, true)) showBubble()
        mainHandler.post(statusTicker)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString().orEmpty()

        if (pkg == PACKAGE_99) {
            // O evento da própria 99 é a fonte principal. Isso continua funcionando quando
            // o motorista está no Maps e a 99 se abre sozinha ao chegar uma oferta.
            last99EventAtElapsed = SystemClock.elapsedRealtime()
            RuntimeState.last99EventAt = System.currentTimeMillis()
            RuntimeState.readerEventCount += 1
            RuntimeState.lastReaderSource = "Evento da 99"
            updateForegroundPackage(PACKAGE_99)
            if (::visualReader.isInitialized) visualReader.on99AccessibilityEvent(event.eventType)

            // Depois que a janela estabilizar, confirma qual app está efetivamente na frente.
            mainHandler.postDelayed({ refreshForegroundFromWindows() }, 420L)
            return
        }

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            mainHandler.postDelayed({ refreshForegroundFromWindows() }, 180L)
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        RuntimeState.accessibilityConnected = false
        RuntimeState.is99Visible = false
        mainHandler.removeCallbacksAndMessages(null)
        if (::visualReader.isInitialized) visualReader.destroy()
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
            addAction(ACTION_SET_READER_ENABLED)
            addAction(ACTION_SCAN_NOW)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(receiver, filter)
    }

    private val statusTicker = object : Runnable {
        override fun run() {
            // Health-check leve. A leitura em si é disparada por eventos; este ticker apenas
            // evita que um overlay próprio faça o RotaLucro "esquecer" que a 99 está visível.
            refreshForegroundFromWindows()
            updateBubbleStatus()
            mainHandler.postDelayed(this, 700L)
        }
    }

    private fun refreshForegroundFromWindows() {
        val now = SystemClock.elapsedRealtime()
        val recent99Event = now - last99EventAtElapsed <= RECENT_99_EVENT_GRACE_MS

        val activePackages = runCatching {
            windows.asSequence()
                .filter { it.isActive || it.isFocused }
                .mapNotNull { window -> runCatching { window.root?.packageName?.toString() }.getOrNull() }
                .filter { it.isNotBlank() }
                .toList()
        }.getOrDefault(emptyList())

        val rootPackage = runCatching { rootInActiveWindow?.packageName?.toString().orEmpty() }.getOrDefault("")
        val hasActive99Window = activePackages.contains(PACKAGE_99)

        when {
            hasActive99Window || rootPackage == PACKAGE_99 -> updateForegroundPackage(PACKAGE_99)
            recent99Event -> {
                RuntimeState.currentPackage = PACKAGE_99
                RuntimeState.is99Visible = true
            }
            rootPackage.isNotBlank() && rootPackage != packageName -> updateForegroundPackage(rootPackage)
            activePackages.firstOrNull { it != packageName && it != "com.android.systemui" } != null -> {
                updateForegroundPackage(activePackages.first { it != packageName && it != "com.android.systemui" })
            }
        }
    }

    private fun updateForegroundPackage(pkg: String) {
        RuntimeState.currentPackage = pkg
        RuntimeState.is99Visible = pkg == PACKAGE_99
        lastForegroundPackage = pkg
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
        val metrics = resources.displayMetrics
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(9))
            background = roundedDrawable("#FDFEFF", 18f, "#D8E4F5", dp(1))
            elevation = dp(14).toFloat()
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, dp(4), dp(6))
        }
        header.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_launcher_foreground)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, LinearLayout.LayoutParams(dp(30), dp(30)))
        header.addView(TextView(this).apply {
            text = if (RuntimeState.captureActive) "RotaLucro • leitor ativo" else "RotaLucro • leitor pausado"
            setTextColor(Color.parseColor(if (RuntimeState.captureActive) "#15803D" else "#475569"))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(7), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, dp(30), 1f))
        header.addView(TextView(this).apply {
            text = "×"
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#64748B"))
            textSize = 22f
            setOnClickListener { hideMenu() }
        }, LinearLayout.LayoutParams(dp(36), dp(30)))
        outer.addView(header, LinearLayout.LayoutParams(-1, dp(36)))

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        addTopMenuButton(row, if (RuntimeState.captureActive) "Pausar leitor" else "Ativar leitor") {
            if (::visualReader.isInitialized) {
                val enabled = !RuntimeState.captureActive
                getSharedPreferences(READER_PREFS, MODE_PRIVATE).edit().putBoolean(KEY_READER_ENABLED, enabled).apply()
                visualReader.setEnabled(enabled)
            }
            updateBubbleStatus(); hideMenu()
        }
        addTopMenuButton(row, "Ler agora") { if (::visualReader.isInitialized) visualReader.scanNow(); hideMenu() }
        addTopMenuButton(row, "Salvar aceita") {
            val saved = RideHistoryStore.saveLatestAsAccepted(this)
            Toast.makeText(this, if (saved != null) "Corrida salva e sincronizando" else "Nenhuma oferta recente", Toast.LENGTH_SHORT).show()
            hideMenu()
        }
        addTopMenuButton(row, "Abrir app") { openApp(false); hideMenu() }
        addTopMenuButton(row, "Ocultar bolha", destructive = true) { hideMenu(); hideBubble() }

        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, dp(46)))
        }
        outer.addView(scroll, LinearLayout.LayoutParams(-1, dp(48)))

        val params = WindowManager.LayoutParams(
            (metrics.widthPixels - dp(16)).coerceAtLeast(dp(300)), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(8)
            y = dp(52)
        }
        windowManager.addView(outer, params)
        menu = outer
    }

    private fun addTopMenuButton(parent: LinearLayout, label: String, destructive: Boolean = false, action: () -> Unit) {
        parent.addView(TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor(if (destructive) "#B91C1C" else "#0F172A"))
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(13), 0, dp(13), 0)
            background = roundedDrawable(if (destructive) "#FEF2F2" else "#EEF4FF", 13f)
            setOnClickListener { action() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(40)).apply {
            rightMargin = dp(7)
        })
    }

    private fun hideMenu() { removeView(menu); menu = null }

    private fun hideBubble() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_BUBBLE_VISIBLE, false).apply()
        RuntimeState.bubbleVisible = false; hideMenu(); removeView(bubble)
        bubble = null; bubbleParams = null; bubbleStatus = null
    }

    private fun updateBubbleStatus() {
        val color = when {
            RuntimeState.ocrProcessing -> "#F59E0B"
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
        val smartScore = intent.getIntExtra(RideOverlayBus.EXTRA_SMART_SCORE, 0)
        val recommendation = runCatching { RideRecommendation.valueOf(intent.getStringExtra(RideOverlayBus.EXTRA_RECOMMENDATION).orEmpty()) }.getOrDefault(RideRecommendation.CAUTION)
        val demandDistance = intent.getDoubleExtra(RideOverlayBus.EXTRA_DEMAND_DISTANCE, -1.0)
        val demandClass = intent.getStringExtra(RideOverlayBus.EXTRA_DEMAND_CLASS).orEmpty()
        val demandZone = intent.getStringExtra(RideOverlayBus.EXTRA_DEMAND_ZONE).orEmpty()
        val outsideCity = intent.getBooleanExtra(RideOverlayBus.EXTRA_OUTSIDE_CITY, false)

        val accent = safeColor(when (rating) {
            OfferRating.BAD -> settings.overlayBadHex
            OfferRating.ATTENTION -> settings.overlayAttentionHex
            OfferRating.GOOD -> settings.overlayGoodHex
        }, when (rating) {
            OfferRating.BAD -> "#EF4444"
            OfferRating.ATTENTION -> "#F59E0B"
            OfferRating.GOOD -> "#22C55E"
        })
        val backgroundHex = safeColor(settings.overlayBackgroundHex, "#FFFFFF")
        val textColor = safeColor(settings.overlayTextHex, "#0F172A")
        val secondary = withAlpha(textColor, 0.72f)
        val ratingLabel = when (recommendation) {
            RideRecommendation.ACCEPT -> "ACEITAR"
            RideRecommendation.CAUTION -> "ATENÇÃO"
            RideRecommendation.REJECT -> "RECUSAR"
        }
        val scale = settings.overlayScalePercent.coerceIn(75, 135) / 100f
        fun sp(base: Float) = base * scale
        fun pad(base: Int) = (dp(base) * scale).toInt().coerceAtLeast(1)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad(16), pad(11), pad(16), pad(12))
            background = roundedDrawable(backgroundHex, 18f, accent, dp(3))
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
        if (demandDistance >= 0.0) {
            card.addView(TextView(this).apply {
                val zone = if (demandZone.isNotBlank()) " • $demandZone" else ""
                val city = if (outsideCity) " • fora da cidade" else ""
                text = "📍 ${oneDecimal(demandDistance)} km da demanda • $demandClass$zone$city"
                setTextColor(Color.parseColor(secondary)); textSize = sp(11.8f); typeface = Typeface.DEFAULT_BOLD; setPadding(0, pad(4), 0, 0)
            })
        }
        card.addView(TextView(this).apply {
            text = "🧠 Score $smartScore/100  •  $thresholdName  •  mín ${money(min)}  •  ótima ${money(excellent)}  •  lucro ${money(profit)}"
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

    /** Retorna a janela da 99 para takeScreenshotOfWindow() no Android 14+. */
    fun find99WindowId(): Int? {
        if (Build.VERSION.SDK_INT < 34) return null
        return runCatching {
            val candidates = windows.filter { window ->
                runCatching { window.root?.packageName?.toString() == PACKAGE_99 }.getOrDefault(false)
            }
            (candidates.firstOrNull { it.isActive || it.isFocused } ?: candidates.firstOrNull())?.id
        }.getOrNull()
    }

    /** Root da janela da 99, mesmo quando o root ativo momentaneamente é o nosso overlay. */
    fun find99WindowRoot() = runCatching {
        val candidates = windows.filter { window ->
            runCatching { window.root?.packageName?.toString() == PACKAGE_99 }.getOrDefault(false)
        }
        (candidates.firstOrNull { it.isActive || it.isFocused } ?: candidates.firstOrNull())?.root
    }.getOrNull()

    /**
     * Confirma se a 99 está visível sem depender apenas de rootInActiveWindow.
     * Um pequeno período de tolerância cobre a animação da 99 entrando por cima do Maps.
     */
    fun is99LikelyVisible(): Boolean {
        if (RuntimeState.simulatorVisible) return true
        val now = SystemClock.elapsedRealtime()
        if (now - last99EventAtElapsed <= RECENT_99_EVENT_GRACE_MS) return true
        return runCatching {
            windows.any { window ->
                (window.isActive || window.isFocused) &&
                    runCatching { window.root?.packageName?.toString() == PACKAGE_99 }.getOrDefault(false)
            }
        }.getOrDefault(RuntimeState.is99Visible)
    }

    /** Áreas dos nossos overlays, mascaradas antes do OCR no fallback de screenshot do display. */
    fun overlayRectsForOcr(): List<Rect> {
        val result = mutableListOf<Rect>()
        listOf(bubble, menu, resultBox).forEach { view ->
            if (view != null && view.isShown) {
                val loc = IntArray(2)
                runCatching {
                    view.getLocationOnScreen(loc)
                    result += Rect(loc[0], loc[1], loc[0] + view.width, loc[1] + view.height)
                }
            }
        }
        return result
    }

    fun sendReaderStatusBroadcast() {
        sendBroadcast(Intent(OcrCaptureService.ACTION_STATUS_CHANGED).setPackage(packageName))
        mainHandler.post { updateBubbleStatus() }
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
        const val ACTION_SET_READER_ENABLED = "com.rotalucro.app.action.SET_READER_ENABLED"
        const val ACTION_SCAN_NOW = "com.rotalucro.app.action.SCAN_NOW_A11Y"
        const val EXTRA_READER_ENABLED = "reader_enabled"
        private const val PREFS = "bubble_settings"
        private const val READER_PREFS = "visual_reader_settings"
        private const val KEY_READER_ENABLED = "enabled"
        private const val KEY_BUBBLE_VISIBLE = "visible"
        private const val KEY_BUBBLE_X = "x"
        private const val KEY_BUBBLE_Y = "y"
        private const val RECENT_99_EVENT_GRACE_MS = 3_500L
    }
}
