package com.rotalucro.app.accessibility

import java.lang.ref.WeakReference

object OverlayController {
    @Volatile
    private var serviceReference: WeakReference<RideAccessibilityService>? = null

    internal fun attach(service: RideAccessibilityService) {
        serviceReference = WeakReference(service)
    }

    internal fun detach(service: RideAccessibilityService) {
        if (serviceReference?.get() === service) {
            serviceReference = null
        }
    }

    fun isConnected(): Boolean = serviceReference?.get() != null

    fun showPreview(): Boolean {
        val service = serviceReference?.get() ?: return false
        service.showPreviewOverlay()
        return true
    }
}
