package com.rotalucro.app.data

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CaptureDiagnostics(
    val timestamp: Long = 0L,
    val packageName: String = "",
    val simulated: Boolean = false,
    val elementCount: Int = 0,
    val textCount: Int = 0,
    val capturedTexts: List<String> = emptyList(),
    val success: Boolean = false,
    val message: String = "Aguardando a primeira oferta da 99.",
    val summary: String = "",
    val fare: Double? = null,
    val pickupDistanceKm: Double? = null,
    val pickupMinutes: Int? = null,
    val tripDistanceKm: Double? = null,
    val tripMinutes: Int? = null,
    val grossPerKm: Double? = null,
    val grossPerHour: Double? = null,
    val boxShown: Boolean = false
) {
    val formattedTime: String
        get() = if (timestamp <= 0L) "Ainda não houve leitura" else {
            SimpleDateFormat("dd/MM HH:mm:ss", Locale("pt", "BR")).format(Date(timestamp))
        }

    val appDetected: Boolean
        get() = packageName.isNotBlank()

    val sourceLabel: String
        get() = when {
            simulated -> "Simulador"
            packageName == "com.app99.driver" || packageName.startsWith("com.app99.driver.") -> "99"
            packageName.isNotBlank() -> packageName
            else -> "Nenhum"
        }
}

object DiagnosticsStore {
    private const val FILE_NAME = "capture_diagnostics"
    private const val TEXT_SEPARATOR = "\u001E"

    fun load(context: Context): CaptureDiagnostics {
        val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
        return CaptureDiagnostics(
            timestamp = preferences.getLong("timestamp", 0L),
            packageName = preferences.getString("packageName", "") ?: "",
            simulated = preferences.getBoolean("simulated", false),
            elementCount = preferences.getInt("elementCount", 0),
            textCount = preferences.getInt("textCount", 0),
            capturedTexts = (preferences.getString("capturedTexts", "") ?: "")
                .split(TEXT_SEPARATOR)
                .filter { it.isNotBlank() },
            success = preferences.getBoolean("success", false),
            message = preferences.getString("message", "Aguardando a primeira oferta da 99.")
                ?: "Aguardando a primeira oferta da 99.",
            summary = preferences.getString("summary", "") ?: "",
            fare = preferences.getString("fare", null)?.toDoubleOrNull(),
            pickupDistanceKm = preferences.getString("pickupDistanceKm", null)?.toDoubleOrNull(),
            pickupMinutes = preferences.getInt("pickupMinutes", -1).takeIf { it >= 0 },
            tripDistanceKm = preferences.getString("tripDistanceKm", null)?.toDoubleOrNull(),
            tripMinutes = preferences.getInt("tripMinutes", -1).takeIf { it >= 0 },
            grossPerKm = preferences.getString("grossPerKm", null)?.toDoubleOrNull(),
            grossPerHour = preferences.getString("grossPerHour", null)?.toDoubleOrNull(),
            boxShown = preferences.getBoolean("boxShown", false)
        )
    }

    fun save(context: Context, diagnostics: CaptureDiagnostics) {
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong("timestamp", diagnostics.timestamp)
            .putString("packageName", diagnostics.packageName)
            .putBoolean("simulated", diagnostics.simulated)
            .putInt("elementCount", diagnostics.elementCount)
            .putInt("textCount", diagnostics.textCount)
            .putString("capturedTexts", diagnostics.capturedTexts.joinToString(TEXT_SEPARATOR))
            .putBoolean("success", diagnostics.success)
            .putString("message", diagnostics.message)
            .putString("summary", diagnostics.summary)
            .putString("fare", diagnostics.fare?.toString())
            .putString("pickupDistanceKm", diagnostics.pickupDistanceKm?.toString())
            .putInt("pickupMinutes", diagnostics.pickupMinutes ?: -1)
            .putString("tripDistanceKm", diagnostics.tripDistanceKm?.toString())
            .putInt("tripMinutes", diagnostics.tripMinutes ?: -1)
            .putString("grossPerKm", diagnostics.grossPerKm?.toString())
            .putString("grossPerHour", diagnostics.grossPerHour?.toString())
            .putBoolean("boxShown", diagnostics.boxShown)
            .apply()
    }
}
