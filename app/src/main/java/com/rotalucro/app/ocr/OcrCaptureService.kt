package com.rotalucro.app.ocr

import android.content.Context
import android.content.Intent
import com.rotalucro.app.accessibility.RideAccessibilityService

/**
 * Compatibilidade com as telas/atalhos das versões anteriores.
 * A partir da v0.10 o leitor não é mais um MediaProjection Service: os comandos são enviados
 * ao AccessibilityService, que usa takeScreenshot() para imagens pontuais.
 */
object OcrCaptureService {
    const val ACTION_STATUS_CHANGED = "com.rotalucro.app.action.OCR_STATUS_CHANGED"

    fun start(context: Context) {
        context.sendBroadcast(
            Intent(RideAccessibilityService.ACTION_SET_READER_ENABLED)
                .setPackage(context.packageName)
                .putExtra(RideAccessibilityService.EXTRA_READER_ENABLED, true)
        )
    }

    fun stop(context: Context) {
        context.sendBroadcast(
            Intent(RideAccessibilityService.ACTION_SET_READER_ENABLED)
                .setPackage(context.packageName)
                .putExtra(RideAccessibilityService.EXTRA_READER_ENABLED, false)
        )
    }

    fun scanNow(context: Context) {
        context.sendBroadcast(
            Intent(RideAccessibilityService.ACTION_SCAN_NOW)
                .setPackage(context.packageName)
        )
    }
}
