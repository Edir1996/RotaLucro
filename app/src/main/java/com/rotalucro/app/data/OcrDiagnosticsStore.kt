package com.rotalucro.app.data

import android.content.Context
import com.rotalucro.app.calculator.RideResult
import com.rotalucro.app.ocr.OcrParseResult
import com.rotalucro.app.runtime.RuntimeState

data class OcrDiagnostics(
    val accessibilityConnected: Boolean,
    val appDetected: String,
    val captureActive: Boolean,
    val ocrRunning: Boolean,
    val recognizedLineCount: Int,
    val usefulTexts: List<String>,
    val reason: String,
    val fare: Double?,
    val pickupKm: Double?,
    val pickupMin: Int?,
    val tripKm: Double?,
    val tripMin: Int?,
    val grossPerKm: Double?,
    val grossPerHour: Double?,
    val analysisPerKm: Double?,
    val analysisPerHour: Double?,
    val possibleEmptyReturn: Boolean,
    val emptyReturnKm: Double?,
    val destinationText: String?,
    val distanceToDemandKm: Double?,
    val demandZoneName: String?,
    val demandClass: String?,
    val smartScore: Int?,
    val recommendation: String?,
    val boxDisplayed: Boolean,
    val lastReadAt: Long
)

object OcrDiagnosticsStore {
    private const val FILE = "ocr_diagnostics"

    fun record(context: Context, parse: OcrParseResult, result: RideResult?, recognizedLineCount: Int, boxDisplayed: Boolean) {
        val d = result?.demandAssessment
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putInt("lineCount", recognizedLineCount)
            .putString("texts", parse.usefulTexts.joinToString("\n"))
            .putString("reason", parse.reason)
            .putFloat("fare", (parse.fare ?: -1.0).toFloat())
            .putFloat("pickupKm", (parse.pickup?.distanceKm ?: -1.0).toFloat())
            .putInt("pickupMin", parse.pickup?.minutes ?: -1)
            .putFloat("tripKm", (parse.trip?.distanceKm ?: -1.0).toFloat())
            .putInt("tripMin", parse.trip?.minutes ?: -1)
            .putFloat("perKm", (result?.grossPerKm ?: -1.0).toFloat())
            .putFloat("perHour", (result?.grossPerHour ?: -1.0).toFloat())
            .putFloat("analysisPerKm", (result?.analysisPerKm ?: -1.0).toFloat())
            .putFloat("analysisPerHour", (result?.analysisPerHour ?: -1.0).toFloat())
            .putBoolean("possibleEmptyReturn", result?.possibleEmptyReturn == true)
            .putFloat("emptyReturnKm", (result?.emptyReturnDistanceKm ?: -1.0).toFloat())
            .putString("destinationText", result?.offer?.destinationLocationText ?: parse.offer?.destinationLocationText)
            .putFloat("distanceToDemandKm", (d?.distanceToDemandKm ?: -1.0).toFloat())
            .putString("demandZoneName", d?.nearestDemandZoneName)
            .putString("demandClass", d?.distanceClass?.label)
            .putInt("smartScore", result?.smartScore ?: -1)
            .putString("recommendation", result?.recommendation?.name)
            .putBoolean("box", boxDisplayed)
            .putLong("lastReadAt", System.currentTimeMillis())
            .apply()
    }

    fun load(context: Context): OcrDiagnostics {
        val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        fun floatOrNull(key: String): Double? = p.getFloat(key, -1f).takeIf { it >= 0f }?.toDouble()
        fun intOrNull(key: String): Int? = p.getInt(key, -1).takeIf { it >= 0 }
        val app = when {
            RuntimeState.is99Visible -> "99"
            RuntimeState.simulatorVisible -> "Simulador"
            RuntimeState.currentPackage.isNotBlank() -> RuntimeState.currentPackage
            else -> "Nenhum"
        }
        return OcrDiagnostics(
            accessibilityConnected = RuntimeState.accessibilityConnected,
            appDetected = app,
            captureActive = RuntimeState.captureActive,
            ocrRunning = RuntimeState.ocrProcessing,
            recognizedLineCount = p.getInt("lineCount", 0),
            usefulTexts = p.getString("texts", "").orEmpty().lines().filter { it.isNotBlank() },
            reason = p.getString("reason", "Aguardando a primeira leitura.").orEmpty(),
            fare = floatOrNull("fare"), pickupKm = floatOrNull("pickupKm"), pickupMin = intOrNull("pickupMin"),
            tripKm = floatOrNull("tripKm"), tripMin = intOrNull("tripMin"),
            grossPerKm = floatOrNull("perKm"), grossPerHour = floatOrNull("perHour"),
            analysisPerKm = floatOrNull("analysisPerKm"), analysisPerHour = floatOrNull("analysisPerHour"),
            possibleEmptyReturn = p.getBoolean("possibleEmptyReturn", false),
            emptyReturnKm = floatOrNull("emptyReturnKm"),
            destinationText = p.getString("destinationText", null),
            distanceToDemandKm = floatOrNull("distanceToDemandKm"),
            demandZoneName = p.getString("demandZoneName", null),
            demandClass = p.getString("demandClass", null),
            smartScore = intOrNull("smartScore"),
            recommendation = p.getString("recommendation", null),
            boxDisplayed = p.getBoolean("box", false), lastReadAt = p.getLong("lastReadAt", 0L)
        )
    }
}
