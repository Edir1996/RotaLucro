package com.rotalucro.app.ocr

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.rotalucro.app.MainActivity
import com.rotalucro.app.R
import com.rotalucro.app.calculator.RideCalculator
import com.rotalucro.app.data.OcrDiagnosticsStore
import com.rotalucro.app.data.SettingsStore
import com.rotalucro.app.runtime.RuntimeState
import java.util.concurrent.atomic.AtomicBoolean

class OcrCaptureService : Service() {
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private lateinit var workerThread: HandlerThread
    private lateinit var worker: Handler
    private val processing = AtomicBoolean(false)
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private var screenWidth = 0
    private var screenHeight = 0
    private var densityDpi = 0
    private var lastScanAt = 0L
    private var lastSignature = ""
    private var lastSignatureAt = 0L
    private var misses = 0
    private var forceOneScan = false

    override fun onCreate() {
        super.onCreate()
        workerThread = HandlerThread("RotaLucroOCR").apply { start() }
        worker = Handler(workerThread.looper)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SCAN_NOW -> {
                forceOneScan = true
                return START_STICKY
            }
        }

        if (projection == null) {
            val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                ?: Activity.RESULT_CANCELED
            val resultData: Intent? = if (Build.VERSION.SDK_INT >= 33) {
                intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
            } else {
                @Suppress("DEPRECATION") (intent?.getParcelableExtra(EXTRA_RESULT_DATA) as? Intent)
            }

            if (resultCode != Activity.RESULT_OK || resultData == null) {
                stopSelf()
                return START_NOT_STICKY
            }

            startForegroundCompat(buildNotification("OCR ativo • aguardando oferta"))
            try {
                startProjection(resultCode, resultData)
            } catch (t: Throwable) {
                RuntimeState.captureActive = false
                updateNotification("Falha ao iniciar captura")
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startProjection(resultCode: Int, resultData: Intent) {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = manager.getMediaProjection(resultCode, resultData)
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stopSelf()
            }
        }, worker)

        val metrics = resources.displayMetrics
        screenWidth = metrics.widthPixels.coerceAtLeast(1)
        screenHeight = metrics.heightPixels.coerceAtLeast(1)
        densityDpi = metrics.densityDpi

        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
        imageReader?.setOnImageAvailableListener({ reader -> onImageAvailable(reader) }, worker)
        virtualDisplay = projection?.createVirtualDisplay(
            "RotaLucroCapture",
            screenWidth,
            screenHeight,
            densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            worker
        )
        RuntimeState.captureActive = true
        sendStatusBroadcast()
    }

    private fun onImageAvailable(reader: ImageReader) {
        val now = System.currentTimeMillis()
        val shouldScan = forceOneScan || RuntimeState.is99Visible || RuntimeState.simulatorVisible
        if (!shouldScan || now - lastScanAt < 950L || processing.get()) {
            reader.acquireLatestImage()?.close()
            return
        }
        forceOneScan = false
        lastScanAt = now
        val image = reader.acquireLatestImage() ?: return
        if (!processing.compareAndSet(false, true)) {
            image.close()
            return
        }
        RuntimeState.ocrProcessing = true

        val bitmap = try {
            imageToBitmap(image)
        } catch (_: Throwable) {
            null
        } finally {
            image.close()
        }
        if (bitmap == null) {
            processing.set(false)
            RuntimeState.ocrProcessing = false
            return
        }

        // A oferta da 99 fica na metade inferior. O recorte reduz ruído do mapa/status bar.
        val cropTop = (bitmap.height * 0.18f).toInt().coerceIn(0, bitmap.height - 2)
        val cropBottom = (bitmap.height * 0.94f).toInt().coerceIn(cropTop + 1, bitmap.height)
        val crop = Bitmap.createBitmap(bitmap, 0, cropTop, bitmap.width, cropBottom - cropTop)
        if (crop !== bitmap) bitmap.recycle()

        val input = InputImage.fromBitmap(crop, 0)
        recognizer.process(input)
            .addOnSuccessListener { text ->
                val lines = text.textBlocks.flatMap { block -> block.lines }.map { line ->
                    val box = line.boundingBox
                    OcrLine(
                        text = line.text,
                        top = box?.top ?: 0,
                        height = box?.height() ?: 0
                    )
                }
                handleOcr(lines)
            }
            .addOnFailureListener {
                val parse = OcrParseResult(null, "Falha do OCR: ${it.javaClass.simpleName}", emptyList())
                OcrDiagnosticsStore.record(this, parse, null, 0, false)
                sendStatusBroadcast()
            }
            .addOnCompleteListener {
                crop.recycle()
                processing.set(false)
                RuntimeState.ocrProcessing = false
            }
    }

    private fun handleOcr(lines: List<OcrLine>) {
        val parsed = OcrOfferParser.parse(lines)
        val result = parsed.offer?.let { RideCalculator.calculate(it, SettingsStore.load(this)) }
        var showBox = false

        if (result != null) {
            misses = 0
            val signature = "${"%.2f".format(result.fare)}-${"%.2f".format(result.totalDistanceKm)}-${result.totalMinutes}"
            val now = System.currentTimeMillis()
            if (signature != lastSignature || now - lastSignatureAt > 18_000L) {
                lastSignature = signature
                lastSignatureAt = now
                showBox = true
                RideOverlayBus.publish(this, result)
            }
            updateNotification("${formatMoney(result.grossPerKm)}/km • ${ratingName(result.rating)}")
        } else {
            misses++
            if (misses >= 3) lastSignature = ""
            updateNotification("OCR ativo • ${if (RuntimeState.is99Visible) "99 detectada" else "aguardando oferta"}")
        }

        OcrDiagnosticsStore.record(this, parsed, result, lines.size, showBox)
        sendStatusBroadcast()
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * screenWidth
        val paddedWidth = screenWidth + rowPadding / pixelStride
        val full = Bitmap.createBitmap(paddedWidth, screenHeight, Bitmap.Config.ARGB_8888)
        full.copyPixelsFromBuffer(buffer)
        return if (paddedWidth == screenWidth) {
            full
        } else {
            Bitmap.createBitmap(full, 0, 0, screenWidth, screenHeight).also { full.recycle() }
        }
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = android.app.PendingIntent.getActivity(
            this,
            100,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("RotaLucro")
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "Leitor OCR", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Mantém a captura OCR ativa enquanto você usa a 99."
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun sendStatusBroadcast() {
        sendBroadcast(Intent(ACTION_STATUS_CHANGED).setPackage(packageName))
    }

    override fun onDestroy() {
        RuntimeState.captureActive = false
        RuntimeState.ocrProcessing = false
        try { imageReader?.setOnImageAvailableListener(null, null) } catch (_: Throwable) {}
        try { virtualDisplay?.release() } catch (_: Throwable) {}
        try { imageReader?.close() } catch (_: Throwable) {}
        try { projection?.stop() } catch (_: Throwable) {}
        recognizer.close()
        workerThread.quitSafely()
        sendStatusBroadcast()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.rotalucro.app.action.START_OCR"
        const val ACTION_STOP = "com.rotalucro.app.action.STOP_OCR"
        const val ACTION_SCAN_NOW = "com.rotalucro.app.action.SCAN_NOW"
        const val ACTION_STATUS_CHANGED = "com.rotalucro.app.action.OCR_STATUS_CHANGED"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        private const val CHANNEL_ID = "rotalucro_ocr"
        private const val NOTIFICATION_ID = 4102

        fun stop(context: Context) {
            context.stopService(Intent(context, OcrCaptureService::class.java))
        }

        fun scanNow(context: Context) {
            if (RuntimeState.captureActive) {
                context.startService(Intent(context, OcrCaptureService::class.java).setAction(ACTION_SCAN_NOW))
            }
        }

        private fun formatMoney(value: Double) = "R$ ${"%.2f".format(value).replace('.', ',')}"
        private fun ratingName(rating: com.rotalucro.app.calculator.OfferRating) = when (rating) {
            com.rotalucro.app.calculator.OfferRating.BAD -> "ruim"
            com.rotalucro.app.calculator.OfferRating.ATTENTION -> "média"
            com.rotalucro.app.calculator.OfferRating.GOOD -> "ótima"
        }
    }
}
