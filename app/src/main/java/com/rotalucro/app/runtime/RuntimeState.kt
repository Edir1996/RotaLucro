package com.rotalucro.app.runtime

object RuntimeState {
    @Volatile var accessibilityConnected: Boolean = false
    @Volatile var currentPackage: String = ""
    @Volatile var is99Visible: Boolean = false
    @Volatile var simulatorVisible: Boolean = false
    @Volatile var captureActive: Boolean = false
    @Volatile var ocrProcessing: Boolean = false
    @Volatile var bubbleVisible: Boolean = true

    // Diagnóstico do BR99 Reader.
    @Volatile var readerEventCount: Int = 0
    @Volatile var lastReaderSource: String = "Aguardando"
    @Volatile var last99EventAt: Long = 0L
}
