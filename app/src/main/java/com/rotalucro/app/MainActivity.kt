package com.rotalucro.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.rotalucro.app.accessibility.RideAccessibilityService
import com.rotalucro.app.ocr.OcrCaptureService
import com.rotalucro.app.ocr.RideOverlayBus
import com.rotalucro.app.runtime.RuntimeState
import com.rotalucro.app.ui.RotaLucroApp
import com.rotalucro.app.ui.theme.RotaLucroTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RotaLucroTheme {
                RotaLucroApp(
                    onRequestCapture = { enableVisualReader() },
                    onStopCapture = { OcrCaptureService.stop(this) },
                    onScanNow = { OcrCaptureService.scanNow(this) },
                    onOpenAccessibility = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    onShowBubble = { sendBroadcast(Intent(RideAccessibilityService.ACTION_SHOW_BUBBLE).setPackage(packageName)) },
                    onHideBubble = { sendBroadcast(Intent(RideAccessibilityService.ACTION_HIDE_BUBBLE).setPackage(packageName)) },
                    onOpenSimulator = { startActivity(Intent(this, TestOfferActivity::class.java)) },
                    onPreviewBox = { RideOverlayBus.publishPreview(this) }
                )
            }
        }
        handleReaderRequest(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleReaderRequest(intent)
    }

    private fun handleReaderRequest(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_REQUEST_CAPTURE, false) == true) {
            intent.removeExtra(EXTRA_REQUEST_CAPTURE)
            window.decorView.postDelayed({ enableVisualReader() }, 250L)
        }
    }

    private fun enableVisualReader() {
        if (!RuntimeState.accessibilityConnected) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        OcrCaptureService.start(this)
    }

    companion object {
        // Mantido para compatibilidade com intents criados por versões anteriores.
        const val EXTRA_REQUEST_CAPTURE = "request_capture"
    }
}
