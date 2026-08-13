package com.rotalucro.app.ocr

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.rotalucro.app.accessibility.RideAccessibilityService
import com.rotalucro.app.calculator.DemandAssessment
import com.rotalucro.app.calculator.RideCalculator
import com.rotalucro.app.data.DemandLearningStore
import com.rotalucro.app.data.LastRideStore
import com.rotalucro.app.data.OcrDiagnosticsStore
import com.rotalucro.app.data.SettingsStore
import com.rotalucro.app.demand.DestinationGeoResolver
import com.rotalucro.app.reader.BR99NodeReader
import com.rotalucro.app.runtime.RuntimeState
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Leitor híbrido e orientado a eventos para a 99.
 *
 * Fluxo:
 * 1) um evento da 99 chega pelo AccessibilityService;
 * 2) tentamos primeiro a árvore de acessibilidade (barato e rápido);
 * 3) se a oferta não estiver exposta como texto, fazemos uma captura pontual;
 * 4) ML Kit reconhece a região da oferta;
 * 5) o parser específico da 99 valida valor + coleta + viagem antes de mostrar o box.
 *
 * Não existe sessão contínua de MediaProjection. Cada captura é independente.
 */
class AccessibilityOcrEngine(
    private val service: RideAccessibilityService
) {
    private val workerThread = HandlerThread("RotaLucroBR99Reader").apply { start() }
    private val worker = Handler(workerThread.looper)
    private val executor = Executor { command -> worker.post(command) }
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val processing = AtomicBoolean(false)

    private var readerEnabled = false
    private var lastCaptureAt = 0L
    private var processingStartedAt = 0L
    private var last99SignalAt = 0L
    private var lastSignature = ""
    private var lastSignatureAt = 0L
    private var misses = 0
    private var resolvingDemandKey = ""
    private var pendingScan = false
    private var pendingReason = "evento pendente"
    private var eventGeneration = 0
    private var processingGeneration = 0
    private val demandCache = LinkedHashMap<String, DemandAssessment>()

    private val contentDebounce = Runnable {
        inspectNodesThenCapture("conteúdo da oferta mudou", forceCapture = false)
    }

    private val heartbeat = object : Runnable {
        override fun run() {
            if (readerEnabled) {
                val now = System.currentTimeMillis()
                if (processing.get() && processingStartedAt > 0L && now - processingStartedAt > PROCESSING_WATCHDOG_MS) {
                    processingGeneration++ // invalida callbacks atrasados do job anterior
                    processing.set(false)
                    processingStartedAt = 0L
                    RuntimeState.ocrProcessing = false
                    pendingScan = true
                    pendingReason = "watchdog"
                    OcrDiagnosticsStore.recordStatus(service, "Watchdog liberou o leitor. Nova tentativa agendada.")
                }

                // Fallback de segurança: caso o Flutter não gere um evento útil, enquanto a 99
                // estiver realmente visível fazemos uma checagem esparsa. Não é uma gravação contínua.
                if (service.is99LikelyVisible() && now - lastCaptureAt >= HEARTBEAT_CAPTURE_GAP_MS) {
                    inspectNodesThenCapture("verificação de segurança", forceCapture = false)
                }
            }
            worker.postDelayed(this, HEARTBEAT_MS)
        }
    }

    fun start(enabled: Boolean) {
        setEnabled(enabled)
        worker.removeCallbacks(heartbeat)
        worker.post(heartbeat)
    }

    fun setEnabled(enabled: Boolean) {
        readerEnabled = enabled
        RuntimeState.captureActive = enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        if (enabled && Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            OcrDiagnosticsStore.recordStatus(service, "Leitor automático requer Android 11 ou superior.")
        } else if (enabled) {
            OcrDiagnosticsStore.recordStatus(service, "Leitor BR99 ativo. Aguardando uma oferta da 99.")
        } else {
            OcrDiagnosticsStore.recordStatus(service, "Leitor BR99 pausado.")
        }
        service.sendReaderStatusBroadcast()
    }

    fun isEnabled(): Boolean = readerEnabled

    /** Recebe diretamente os eventos da 99. */
    fun on99AccessibilityEvent(eventType: Int) {
        if (!readerEnabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        last99SignalAt = System.currentTimeMillis()
        eventGeneration++
        val generation = eventGeneration

        when (eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                // A 99 pode voltar por cima do Maps antes de o cartão Flutter terminar de desenhar.
                scheduleBurst(generation, 180L, "99 voltou para frente")
                scheduleBurst(generation, 1_380L, "cartão da 99 estabilizando")
                scheduleBurst(generation, 2_650L, "confirmação da oferta")
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                worker.removeCallbacks(contentDebounce)
                worker.postDelayed(contentDebounce, CONTENT_DEBOUNCE_MS)
            }
            else -> scheduleBurst(generation, 260L, "evento da 99")
        }
    }

    /** Mantido para chamadas da bolha e para compatibilidade da UI. */
    fun on99BecameVisible() {
        on99AccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
    }

    fun scanNow() {
        if (!readerEnabled) setEnabled(true)
        worker.post { inspectNodesThenCapture("leitura manual", forceCapture = true) }
    }

    private fun scheduleBurst(generation: Int, delayMs: Long, reason: String) {
        worker.postDelayed({
            if (!readerEnabled) return@postDelayed
            // Não cancelamos os frames posteriores da mesma entrada; apenas evitamos que
            // uma rajada muito antiga sobreviva a muitas mudanças de tela.
            if (generation < eventGeneration - 4) return@postDelayed
            inspectNodesThenCapture(reason, forceCapture = true)
        }, delayMs)
    }

    private fun inspectNodesThenCapture(reason: String, forceCapture: Boolean) {
        if (!readerEnabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (!forceCapture && !service.is99LikelyVisible()) return

        val snapshot = runCatching { BR99NodeReader.read(service) }.getOrNull()
        if (snapshot != null) {
            val nodeLines = snapshot.texts.mapIndexed { index, text ->
                OcrLine(text = text, top = index * 36, height = 24, left = 0, width = 0)
            }
            val nodeParsed = OcrOfferParser.parse(nodeLines)
            if (nodeParsed.offer != null) {
                val enriched = nodeParsed.copy(
                    reason = "Oferta reconhecida diretamente pela Acessibilidade.",
                    usefulTexts = (nodeParsed.usefulTexts + snapshot.viewIds.take(4).map { "ID: $it" }).take(20)
                )
                RuntimeState.lastReaderSource = "Acessibilidade direta"
                processRecognizedOffer(enriched, snapshot.nodeCount, null, forceDisplay = false)
                service.sendReaderStatusBroadcast()
                return
            }

            if (snapshot.nodeCount > 0) {
                OcrDiagnosticsStore.recordStatus(
                    service,
                    "99 detectada • ${snapshot.nodeCount} elementos verificados • usando leitura visual."
                )
            }
        }

        requestVisualScan(reason, force = forceCapture)
    }

    private fun requestVisualScan(reason: String, force: Boolean) {
        if (!readerEnabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (!force && !service.is99LikelyVisible()) return

        val now = System.currentTimeMillis()
        val since = now - lastCaptureAt
        if (since < MIN_CAPTURE_INTERVAL_MS) {
            pendingScan = true
            pendingReason = reason
            worker.postDelayed({ drainPendingScan() }, MIN_CAPTURE_INTERVAL_MS - since + 70L)
            return
        }

        if (!processing.compareAndSet(false, true)) {
            pendingScan = true
            pendingReason = reason
            return
        }

        pendingScan = false
        processingStartedAt = now
        processingGeneration++
        val jobId = processingGeneration
        RuntimeState.ocrProcessing = true
        lastCaptureAt = now
        RuntimeState.lastReaderSource = "Captura + OCR"
        OcrDiagnosticsStore.recordStatus(service, "Capturando oferta da 99 • $reason")
        service.sendReaderStatusBroadcast()

        performScreenshot(prefer99Window = Build.VERSION.SDK_INT >= 34, jobId = jobId)
    }

    private fun performScreenshot(prefer99Window: Boolean, jobId: Int) {
        val callback = object : AccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                if (jobId != processingGeneration) {
                    try { screenshot.hardwareBuffer.close() } catch (_: Throwable) {}
                    return
                }
                val bitmap = screenshotToBitmap(screenshot)
                if (bitmap == null) {
                    finishProcessing(jobId, "Captura recebida, mas a imagem não pôde ser aberta.")
                    return
                }
                analyzeBitmap(bitmap, jobId)
            }

            override fun onFailure(errorCode: Int) {
                if (jobId != processingGeneration) return
                // Android 14+: se a janela trocou exatamente no instante da captura,
                // tenta uma vez o display inteiro antes de desistir.
                if (prefer99Window && Build.VERSION.SDK_INT >= 34 && service.is99LikelyVisible()) {
                    runCatching {
                        service.takeScreenshot(Display.DEFAULT_DISPLAY, executor, object : AccessibilityService.TakeScreenshotCallback {
                            override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                                if (jobId != processingGeneration) {
                                    try { screenshot.hardwareBuffer.close() } catch (_: Throwable) {}
                                    return
                                }
                                val bitmap = screenshotToBitmap(screenshot)
                                if (bitmap == null) finishProcessing(jobId, "Fallback de captura sem bitmap.")
                                else analyzeBitmap(bitmap, jobId)
                            }

                            override fun onFailure(fallbackCode: Int) {
                                if (jobId != processingGeneration) return
                                finishProcessing(jobId, screenshotErrorMessage(fallbackCode))
                            }
                        })
                    }.onFailure { finishProcessing(jobId, "Erro no fallback de captura: ${it.javaClass.simpleName}") }
                    return
                }

                finishProcessing(jobId, screenshotErrorMessage(errorCode))
            }
        }

        try {
            if (prefer99Window && Build.VERSION.SDK_INT >= 34) {
                val windowId = service.find99WindowId()
                if (windowId != null) {
                    service.takeScreenshotOfWindow(windowId, executor, callback)
                } else {
                    service.takeScreenshot(Display.DEFAULT_DISPLAY, executor, callback)
                }
            } else {
                service.takeScreenshot(Display.DEFAULT_DISPLAY, executor, callback)
            }
        } catch (t: Throwable) {
            finishProcessing(jobId, "Erro ao iniciar captura: ${t.javaClass.simpleName}")
        }
    }

    private fun screenshotErrorMessage(errorCode: Int): String = when (errorCode) {
        AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT -> "A captura foi solicitada cedo demais; nova tentativa será feita."
        AccessibilityService.ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS -> "Sem acesso à captura. Desative e ative novamente a Acessibilidade do RotaLucro."
        AccessibilityService.ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY -> "Display inválido para captura."
        AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR -> "Falha interna ao capturar a tela."
        else -> if (Build.VERSION.SDK_INT >= 34 && errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW) {
            "A janela atual bloqueou screenshots."
        } else if (Build.VERSION.SDK_INT >= 34 && errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_INVALID_WINDOW) {
            "A janela da 99 mudou durante a captura; tentando novamente."
        } else {
            "Falha da captura visual (código $errorCode)."
        }
    }

    private fun screenshotToBitmap(result: AccessibilityService.ScreenshotResult): Bitmap? {
        val buffer = result.hardwareBuffer
        return try {
            val wrapped = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace) ?: return null
            wrapped.copy(Bitmap.Config.ARGB_8888, true)
        } catch (_: Throwable) {
            null
        } finally {
            try { buffer.close() } catch (_: Throwable) {}
        }
    }

    private fun analyzeBitmap(bitmap: Bitmap, jobId: Int) {
        val prepared = try {
            prepareForOcr(bitmap)
        } catch (_: Throwable) {
            bitmap
        }

        val input = InputImage.fromBitmap(prepared, 0)
        recognizer.process(input)
            .addOnSuccessListener(executor) { text ->
                if (jobId != processingGeneration) return@addOnSuccessListener
                val lines = text.textBlocks
                    .flatMap { block -> block.lines }
                    .map { line ->
                        val box = line.boundingBox
                        OcrLine(
                            text = line.text,
                            top = box?.top ?: 0,
                            height = box?.height() ?: 0,
                            left = box?.left ?: 0,
                            width = box?.width() ?: 0
                        )
                    }
                    .sortedWith(compareBy<OcrLine> { it.top }.thenBy { it.left })
                handleOcr(lines)
            }
            .addOnFailureListener(executor) {
                if (jobId == processingGeneration) {
                    OcrDiagnosticsStore.recordStatus(service, "Falha do OCR: ${it.javaClass.simpleName}")
                }
            }
            .addOnCompleteListener(executor) {
                try { prepared.recycle() } catch (_: Throwable) {}
                if (prepared !== bitmap) try { bitmap.recycle() } catch (_: Throwable) {}
                completeProcessing(jobId)
            }
    }

    private fun prepareForOcr(source: Bitmap): Bitmap {
        // Android 11–13 captura o display: apaga os overlays do próprio RotaLucro.
        if (Build.VERSION.SDK_INT < 34) {
            val canvas = Canvas(source)
            val paint = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }
            service.overlayRectsForOcr().forEach { r ->
                val safe = Rect(
                    r.left.coerceIn(0, source.width),
                    r.top.coerceIn(0, source.height),
                    r.right.coerceIn(0, source.width),
                    r.bottom.coerceIn(0, source.height)
                )
                if (safe.width() > 0 && safe.height() > 0) canvas.drawRect(safe, paint)
            }
        }

        // Mantém praticamente todo o cartão Flutter e descarta apenas a região superior
        // que costuma conter status bar/mapa. É deliberadamente mais tolerante que a v0.10.
        val cropTop = (source.height * 0.08f).toInt().coerceIn(0, source.height - 2)
        val cropBottom = (source.height * 0.99f).toInt().coerceIn(cropTop + 1, source.height)
        return Bitmap.createBitmap(source, 0, cropTop, source.width, cropBottom - cropTop)
    }

    private fun handleOcr(lines: List<OcrLine>) {
        val parsed = OcrOfferParser.parse(lines)
        val offer = parsed.offer

        if (offer == null) {
            misses++
            if (misses >= 4) lastSignature = ""
            OcrDiagnosticsStore.record(service, parsed, null, lines.size, false)
            service.sendReaderStatusBroadcast()
            return
        }

        val settings = SettingsStore.load(service)
        val destinationText = offer.destinationLocationText?.trim().orEmpty()
        val demandKey = "${"%.2f".format(offer.fare)}-${"%.2f".format(offer.pickupDistanceKm)}-${"%.2f".format(offer.tripDistanceKm)}-${offer.pickupMinutes}-${offer.tripMinutes}"
        val cached = demandCache[demandKey]

        if (settings.smartDemandEnabled && destinationText.isNotBlank() && cached == null) {
            if (resolvingDemandKey != demandKey) {
                resolvingDemandKey = demandKey
                OcrDiagnosticsStore.recordStatus(service, "Oferta reconhecida. Analisando destino e demanda.")
                DestinationGeoResolver.assessAsync(service, offer, settings) { assessment ->
                    demandCache[demandKey] = assessment
                    while (demandCache.size > 20) demandCache.remove(demandCache.keys.first())
                    resolvingDemandKey = ""
                    worker.post { processRecognizedOffer(parsed, lines.size, assessment, forceDisplay = true) }
                }
            }
            OcrDiagnosticsStore.record(service, parsed, null, lines.size, false)
            service.sendReaderStatusBroadcast()
            return
        }

        processRecognizedOffer(parsed, lines.size, cached, forceDisplay = false)
    }

    private fun processRecognizedOffer(
        parsed: OcrParseResult,
        lineCount: Int,
        demand: DemandAssessment?,
        forceDisplay: Boolean
    ) {
        val offer = parsed.offer ?: return
        val result = RideCalculator.calculate(offer, SettingsStore.load(service), demand = demand)
        misses = 0
        val signature = "${"%.2f".format(result.fare)}-${"%.2f".format(result.offer.pickupDistanceKm)}-${"%.2f".format(result.offer.tripDistanceKm)}-${result.offer.pickupMinutes}-${result.offer.tripMinutes}"
        val now = System.currentTimeMillis()
        var showBox = false

        if (forceDisplay || signature != lastSignature || now - lastSignatureAt > SAME_OFFER_REFRESH_MS) {
            if (signature != lastSignature) DemandLearningStore.registerNewOffer(service, now)
            lastSignature = signature
            lastSignatureAt = now
            showBox = true
            RideOverlayBus.publish(service, result)
        }

        LastRideStore.save(service, result)
        OcrDiagnosticsStore.record(service, parsed, result, lineCount, showBox)
        service.sendReaderStatusBroadcast()
    }

    private fun completeProcessing(jobId: Int) {
        if (jobId != processingGeneration) return
        processing.set(false)
        processingStartedAt = 0L
        RuntimeState.ocrProcessing = false
        service.sendReaderStatusBroadcast()
        drainPendingScan()
    }

    private fun finishProcessing(jobId: Int, message: String) {
        if (jobId != processingGeneration) return
        processing.set(false)
        processingStartedAt = 0L
        RuntimeState.ocrProcessing = false
        OcrDiagnosticsStore.recordStatus(service, message)
        service.sendReaderStatusBroadcast()
        pendingScan = true
        pendingReason = "recuperação após falha"
        worker.postDelayed({ drainPendingScan() }, RETRY_AFTER_FAILURE_MS)
    }

    private fun drainPendingScan() {
        if (!pendingScan || processing.get() || !readerEnabled) return
        if (!service.is99LikelyVisible() && System.currentTimeMillis() - last99SignalAt > RECENT_99_SIGNAL_MS) {
            pendingScan = false
            return
        }
        val reason = pendingReason
        pendingScan = false
        requestVisualScan(reason, force = true)
    }

    fun destroy() {
        readerEnabled = false
        RuntimeState.captureActive = false
        RuntimeState.ocrProcessing = false
        worker.removeCallbacksAndMessages(null)
        try { recognizer.close() } catch (_: Throwable) {}
        workerThread.quitSafely()
    }

    companion object {
        private const val CONTENT_DEBOUNCE_MS = 260L
        private const val MIN_CAPTURE_INTERVAL_MS = 1_150L
        private const val HEARTBEAT_MS = 2_200L
        private const val HEARTBEAT_CAPTURE_GAP_MS = 2_100L
        private const val PROCESSING_WATCHDOG_MS = 7_500L
        private const val RETRY_AFTER_FAILURE_MS = 1_250L
        private const val RECENT_99_SIGNAL_MS = 5_000L
        private const val SAME_OFFER_REFRESH_MS = 20_000L
    }
}
