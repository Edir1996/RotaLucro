package com.rotalucro.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.rotalucro.app.accessibility.RideAccessibilityService
import com.rotalucro.app.ocr.OcrCaptureService
import com.rotalucro.app.ocr.RideOverlayBus
import com.rotalucro.app.runtime.RuntimeState
import com.rotalucro.app.ui.RotaLucroApp
import com.rotalucro.app.ui.theme.RotaLucroTheme

class MainActivity : ComponentActivity() {
    private val projectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            val intent = Intent(this, OcrCaptureService::class.java)
                .setAction(OcrCaptureService.ACTION_START)
                .putExtra(OcrCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                .putExtra(OcrCaptureService.EXTRA_RESULT_DATA, data)
            ContextCompat.startForegroundService(this, intent)
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RotaLucroTheme {
                RotaLucroApp(
                    onRequestCapture = { requestScreenCapture() },
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
        handleCaptureRequest(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleCaptureRequest(intent)
    }

    private fun handleCaptureRequest(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_REQUEST_CAPTURE, false) == true) {
            intent.removeExtra(EXTRA_REQUEST_CAPTURE)
            window.decorView.postDelayed({ requestScreenCapture() }, 350L)
        }
    }

    private fun requestScreenCapture() {
        if (RuntimeState.captureActive) return
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val captureIntent = if (Build.VERSION.SDK_INT >= 34) {
            // Capturamos a tela inteira porque a análise precisa continuar ao alternar do RotaLucro para a 99.
            manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
        } else {
            manager.createScreenCaptureIntent()
        }
        projectionLauncher.launch(captureIntent)
    }

    companion object {
        const val EXTRA_REQUEST_CAPTURE = "request_capture"
    }
}
