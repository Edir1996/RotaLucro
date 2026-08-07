package com.rotalucro.app.data

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CaptureDiagnostics(
    val timestamp: Long = 0L,
    val packageName: String = "",
    val textCount: Int = 0,
    val success: Boolean = false,
    val message: String = "Aguardando a primeira oferta da 99.",
    val summary: String = ""
) {
    val formattedTime: String
        get() = if (timestamp <= 0L) "Ainda não houve leitura" else {
            SimpleDateFormat("dd/MM HH:mm:ss", Locale("pt", "BR")).format(Date(timestamp))
        }
}

object DiagnosticsStore {
    private const val FILE_NAME = "capture_diagnostics"

    fun load(context: Context): CaptureDiagnostics {
        val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
        return CaptureDiagnostics(
            timestamp = preferences.getLong("timestamp", 0L),
            packageName = preferences.getString("packageName", "") ?: "",
            textCount = preferences.getInt("textCount", 0),
            success = preferences.getBoolean("success", false),
            message = preferences.getString("message", "Aguardando a primeira oferta da 99.")
                ?: "Aguardando a primeira oferta da 99.",
            summary = preferences.getString("summary", "") ?: ""
        )
    }

    fun save(context: Context, diagnostics: CaptureDiagnostics) {
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong("timestamp", diagnostics.timestamp)
            .putString("packageName", diagnostics.packageName)
            .putInt("textCount", diagnostics.textCount)
            .putBoolean("success", diagnostics.success)
            .putString("message", diagnostics.message)
            .putString("summary", diagnostics.summary)
            .apply()
    }
}
