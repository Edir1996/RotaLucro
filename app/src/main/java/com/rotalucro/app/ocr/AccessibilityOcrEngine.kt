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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.rotalucro.app.accessibility.RideAccessibilityService
import com.rotalucro.app.calculator.DemandAssessment
import com.rotalucro.app.calculator.OfferRating
import com.rotalucro.app.calculator.RideCalculator
import com.rotalucro.app.data.DemandLearningStore
import com.rotalucro.app.data.LastRideStore
import com.rotalucro.app.data.OcrDiagnosticsStore
import com.rotalucro.app.data.SettingsStore
import com.rotalucro.app.demand.DestinationGeoResolver
import com.rotalucro.app.runtime.RuntimeState
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Leitor visual que usa AccessibilityService.takeScreenshot().
 *
 * Diferente do MediaProjection, não existe uma sessão de gravação/transmissão para manter viva.
 * O serviço de acessibilidade tira uma imagem pontual, processa localmente com ML Kit e descarta.
 */
class AccessibilityOcrEngine(
    private val service: RideAccessibilityService
) {
    private val workerThread = HandlerThread("RotaLucroA11yOCR").apply { start() }
    private val worker = Handler(workerThread.looper)
    private val executor = Executor { command -> worker.post(command) }
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val processing = AtomicBoolean(false)

    private var readerEnabled = false
    private var lastCaptureAt = 0L
    private var processingStartedAt = 0L
    private var lastSignature = ""
    private var lastSignatureAt = 0L
    private var misses = 0
    private var resolvingDemandKey = ""
    private val demandCache = LinkedHashMap<String, DemandAssessment>()

    private val scanLoop = object : Runnable {
        override fun run() {
            if (readerEnabled) {
                val now = System.currentTimeMillis()
                // Watchdog: uma Task do OCR não deve bloquear o leitor para sempre.
                if (processing.get() && processingStartedAt > 0L && now - processingStartedAt > PROCESSING_WATCHDOG_MS) {
                    processing.set(false)
                    RuntimeState.ocrProcessing = false
                    OcrDiagnosticsStore.recordStatus(service, "Watchdog liberou uma leitura que demorou demais.")
                }
                if (RuntimeState.is99Visible || RuntimeState.simulatorVisible) {
                    requestScan(force = false)
                }
            }
            worker.postDelayed(this, LOOP_INTERVAL_MS)
        }
    }

    fun start(enabled: Boolean) {
        setEnabled(enabled)
        worker.removeCallbacks(scanLoop)
        worker.post(scanLoop)
    }

    fun setEnabled(enabled: Boolean) {
        readerEnabled = enabled
        RuntimeState.captureActive = enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        if (enabled && Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            OcrDiagnosticsStore.recordStatus(service, "Leitor visual automático requer Android 11 ou superior.")
        } else if (enabled) {
            OcrDiagnosticsStore.recordStatus(service, "Leitor visual ativo. Aguardando a 99 aparecer na tela.")
        } else {
            OcrDiagnosticsStore.recordStatus(service, "Leitor visual pausado.")
        }
        service.sendReaderStatusBroadcast()
    }

    fun isEnabled(): Boolean = readerEnabled

    /** Chamado quando a 99 vem para frente, inclusive saindo do Maps. */
    fun on99BecameVisible() {
        if (!readerEnabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        // Flutter costuma desenhar o cartão em etapas. Uma pequena rajada cobre o primeiro frame,
        // o cartão parcialmente renderizado e o cartão já completo, respeitando o intervalo da API.
        worker.postDelayed({ requestScan(force = true) }, 180L)
        worker.postDelayed({ requestScan(force = true) }, 1_380L)
        worker.postDelayed({ requestScan(force = true) }, 2_650L)
    }

    fun scanNow() {
        if (!readerEnabled) setEnabled(true)
        worker.post { requestScan(force = true) }
    }

    private fun requestScan(force: Boolean) {
        if (!readerEnabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val allowedScreen = RuntimeState.is99Visible || RuntimeState.simulatorVisible
        if (!allowedScreen && !force) return

        val now = System.currentTimeMillis()
        if (!force && now - lastCaptureAt < MIN_CAPTURE_INTERVAL_MS) return
        if (force && now - lastCaptureAt < MIN_FORCE_INTERVAL_MS) {
            worker.postDelayed({ requestScan(force = true) }, MIN_FORCE_INTERVAL_MS - (now - lastCaptureAt) + 30L)
            return
        }
        if (!processing.compareAndSet(false, true)) return

        processingStartedAt = now
        RuntimeState.ocrProcessing = true
        lastCaptureAt = now

        val callback = object : AccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                val bitmap = screenshotToBitmap(screenshot)
                if (bitmap == null) {
                    finishProcessing("A captura foi recebida, mas não foi possível criar a imagem.")
                    return
                }
                analyzeBitmap(bitmap)
            }

            override fun onFailure(errorCode: Int) {
                val message = when (errorCode) {
                    AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT -> "Captura solicitada cedo demais; tentando novamente."
                    AccessibilityService.ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS -> "Sem acesso à captura. Reative a acessibilidade do RotaLucro."
                    AccessibilityService.ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY -> "Display inválido para captura."
                    AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR -> "Falha interna ao capturar a tela."
                    else -> if (Build.VERSION.SDK_INT >= 34 && errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW) {
                        "A janela atual bloqueou screenshots (FLAG_SECURE)."
                    } else if (Build.VERSION.SDK_INT >= 34 && errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_INVALID_WINDOW) {
                        "A janela da 99 mudou antes da captura; tentando novamente."
                    } else {
                        "Falha da captura visual (código $errorCode)."
                    }
                }
                finishProcessing(message)
                if (readerEnabled && (RuntimeState.is99Visible || RuntimeState.simulatorVisible)) {
                    worker.postDelayed({ requestScan(force = false) }, 1_050L)
                }
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= 34) {
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
            finishProcessing("Erro ao iniciar captura: ${t.javaClass.simpleName}")
        }
    }

    private fun screenshotToBitmap(result: AccessibilityService.ScreenshotResult): Bitmap? {
        val buffer = result.hardwareBuffer
        return try {
            val wrapped = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace) ?: return null
            // Software + mutable para podermos mascarar os nossos próprios overlays em Android 11–13.
            wrapped.copy(Bitmap.Config.ARGB_8888, true)
        } catch (_: Throwable) {
            null
        } finally {
            try { buffer.close() } catch (_: Throwable) {}
        }
    }

    private fun analyzeBitmap(bitmap: Bitmap) {
        val prepared = try {
            prepareForOcr(bitmap)
        } catch (_: Throwable) {
            bitmap
        }

        val input = InputImage.fromBitmap(prepared, 0)
        recognizer.process(input)
            .addOnSuccessListener(executor) { text ->
                val lines = text.textBlocks.flatMap { block -> block.lines }.map { line ->
                    val box = line.boundingBox
                    OcrLine(
                        text = line.text,
                        top = box?.top ?: 0,
                        height = box?.height() ?: 0,
                        left = box?.left ?: 0,
                        width = box?.width() ?: 0
                    )
                }
                handleOcr(lines)
            }
            .addOnFailureListener(executor) {
                val parse = OcrParseResult(null, "Falha do OCR: ${it.javaClass.simpleName}", emptyList())
                OcrDiagnosticsStore.record(service, parse, null, 0, false)
                service.sendReaderStatusBroadcast()
            }
            .addOnCompleteListener(executor) {
                try { prepared.recycle() } catch (_: Throwable) {}
                if (prepared !== bitmap) try { bitmap.recycle() } catch (_: Throwable) {}
                processing.set(false)
                processingStartedAt = 0L
                RuntimeState.ocrProcessing = false
                service.sendReaderStatusBroadcast()
            }
    }

    private fun prepareForOcr(source: Bitmap): Bitmap {
        // Em Android 14+ takeScreenshotOfWindow evita que o overlay de acessibilidade cubra a 99.
        // No fallback de display (Android 11–13), apagamos as áreas da bolha/menu/box antes do OCR.
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

        // O cartão da 99 ocupa a maior parte inferior; esse recorte remove status bar e boa parte do mapa.
        val cropTop = (source.height * 0.16f).toInt().coerceIn(0, source.height - 2)
        val cropBottom = (source.height * 0.96f).toInt().coerceIn(cropTop + 1, source.height)
        return Bitmap.createBitmap(source, 0, cropTop, source.width, cropBottom - cropTop)
    }

    private fun handleOcr(lines: List<OcrLine>) {
        val parsed = OcrOfferParser.parse(lines)
        val likelyOffer = RuntimeState.is99Visible || RuntimeState.simulatorVisible ||
            parsed.usefulTexts.any { it.contains("Aceitar", ignoreCase = true) }
        val offer = parsed.offer

        if (!likelyOffer || offer == null) {
            misses++
            if (misses >= 3) lastSignature = ""
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
                OcrDiagnosticsStore.recordStatus(service, "Oferta reconhecida. Analisando o destino e a demanda.")
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
        val signature = "${"%.2f".format(result.fare)}-${"%.2f".format(result.totalDistanceKm)}-${result.totalMinutes}"
        val now = System.currentTimeMillis()
        var showBox = false

        if (forceDisplay || signature != lastSignature || now - lastSignatureAt > 18_000L) {
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

    private fun finishProcessing(message: String) {
        processing.set(false)
        processingStartedAt = 0L
        RuntimeState.ocrProcessing = false
        OcrDiagnosticsStore.recordStatus(service, message)
        service.sendReaderStatusBroadcast()
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
        private const val LOOP_INTERVAL_MS = 560L
        private const val MIN_CAPTURE_INTERVAL_MS = 1_100L
        private const val MIN_FORCE_INTERVAL_MS = 1_100L
        private const val PROCESSING_WATCHDOG_MS = 8_000L
    }
}
