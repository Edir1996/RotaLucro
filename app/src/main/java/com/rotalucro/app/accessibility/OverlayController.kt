package com.rotalucro.app.accessibility

import com.rotalucro.app.runtime.RuntimeState

/**
 * Compatibility shim kept so repositories upgraded from v0.4/v0.5 do not retain
 * a stale OverlayController that references methods removed by the OCR architecture.
 *
 * The v0.6+ overlay is owned directly by [RideAccessibilityService].
 */
object OverlayController {
    fun isConnected(): Boolean = RuntimeState.accessibilityConnected

    @Deprecated("Preview overlay is handled by the OCR/laboratory flow in v0.6+")
    fun showPreview(): Boolean = false

    @Deprecated("Simulator capture is handled by the OCR laboratory in v0.6+")
    fun enableSimulatorCapture(): Boolean = false

    @Deprecated("Simulator capture is handled by the OCR laboratory in v0.6+")
    fun disableSimulatorCapture() = Unit
}
