package com.rotalucro.app.data

import android.content.Context
import android.content.SharedPreferences
import com.rotalucro.app.calculator.DriverSettings
import com.rotalucro.app.calculator.ScheduledKmThreshold

object SettingsStore {
    private const val FILE_NAME = "driver_settings"

    fun load(context: Context): DriverSettings {
        val p = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
        val defaults = DriverSettings.defaultScheduledThresholds()
        return DriverSettings(
            defaultMinimumPerKm = p.getFloat("defaultMinimumPerKm", 1.20f).toDouble(),
            defaultExcellentPerKm = p.getFloat("defaultExcellentPerKm", 1.80f).toDouble(),
            scheduledThresholds = defaults.mapIndexed { index, default -> loadSchedule(p, index + 1, default) },
            fuelPricePerLiter = p.getFloat("fuelPricePerLiter", 6.0f).toDouble(),
            vehicleKmPerLiter = p.getFloat("vehicleKmPerLiter", 35.0f).toDouble(),
            maintenancePerKm = p.getFloat("maintenancePerKm", 0.18f).toDouble(),
            overlayAutoHideSeconds = p.getInt("overlayAutoHideSeconds", 18).coerceIn(5, 60),
            overlayYPercent = p.getInt("overlayYPercent", 5).coerceIn(0, 75),
            overlayXPercent = p.getInt("overlayXPercent", 50).coerceIn(0, 100),
            overlayWidthPercent = p.getInt("overlayWidthPercent", 94).coerceIn(55, 100),
            overlayOpacityPercent = p.getInt("overlayOpacityPercent", 96).coerceIn(35, 100),
            overlayScalePercent = p.getInt("overlayScalePercent", 100).coerceIn(75, 135),
            overlayBackgroundHex = p.getString("overlayBackgroundHex", "#FFFFFF") ?: "#FFFFFF",
            overlayTextHex = p.getString("overlayTextHex", "#0F172A") ?: "#0F172A",
            overlayBadHex = p.getString("overlayBadHex", "#EF4444") ?: "#EF4444",
            overlayAttentionHex = p.getString("overlayAttentionHex", "#F59E0B") ?: "#F59E0B",
            overlayGoodHex = p.getString("overlayGoodHex", "#22C55E") ?: "#22C55E",
            emptyReturnEnabled = p.getBoolean("emptyReturnEnabled", true),
            emptyReturnTripKmThreshold = p.getFloat("emptyReturnTripKmThreshold", 10.0f).toDouble(),
            emptyReturnDistanceFactor = p.getFloat("emptyReturnDistanceFactor", 1.0f).toDouble()
        )
    }

    fun save(context: Context, settings: DriverSettings) {
        val editor = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE).edit()
            .putFloat("defaultMinimumPerKm", settings.defaultMinimumPerKm.toFloat())
            .putFloat("defaultExcellentPerKm", settings.defaultExcellentPerKm.toFloat())
            .putFloat("fuelPricePerLiter", settings.fuelPricePerLiter.toFloat())
            .putFloat("vehicleKmPerLiter", settings.vehicleKmPerLiter.toFloat())
            .putFloat("maintenancePerKm", settings.maintenancePerKm.toFloat())
            .putInt("overlayAutoHideSeconds", settings.overlayAutoHideSeconds.coerceIn(5, 60))
            .putInt("overlayYPercent", settings.overlayYPercent.coerceIn(0, 75))
            .putInt("overlayXPercent", settings.overlayXPercent.coerceIn(0, 100))
            .putInt("overlayWidthPercent", settings.overlayWidthPercent.coerceIn(55, 100))
            .putInt("overlayOpacityPercent", settings.overlayOpacityPercent.coerceIn(35, 100))
            .putInt("overlayScalePercent", settings.overlayScalePercent.coerceIn(75, 135))
            .putString("overlayBackgroundHex", normalizeHex(settings.overlayBackgroundHex, "#FFFFFF"))
            .putString("overlayTextHex", normalizeHex(settings.overlayTextHex, "#0F172A"))
            .putString("overlayBadHex", normalizeHex(settings.overlayBadHex, "#EF4444"))
            .putString("overlayAttentionHex", normalizeHex(settings.overlayAttentionHex, "#F59E0B"))
            .putString("overlayGoodHex", normalizeHex(settings.overlayGoodHex, "#22C55E"))
            .putBoolean("emptyReturnEnabled", settings.emptyReturnEnabled)
            .putFloat("emptyReturnTripKmThreshold", settings.emptyReturnTripKmThreshold.coerceAtLeast(0.1).toFloat())
            .putFloat("emptyReturnDistanceFactor", settings.emptyReturnDistanceFactor.coerceIn(0.0, 2.0).toFloat())

        settings.scheduledThresholds.take(4).forEachIndexed { index, schedule ->
            val number = index + 1
            editor.putString("schedule${number}Name", schedule.name)
                .putBoolean("schedule${number}Enabled", schedule.enabled)
                .putInt("schedule${number}Start", schedule.startMinuteOfDay)
                .putInt("schedule${number}End", schedule.endMinuteOfDay)
                .putFloat("schedule${number}Minimum", schedule.minimumPerKm.toFloat())
                .putFloat("schedule${number}Excellent", schedule.excellentPerKm.toFloat())
        }
        editor.apply()
    }

    private fun loadSchedule(p: SharedPreferences, index: Int, default: ScheduledKmThreshold) = ScheduledKmThreshold(
        name = p.getString("schedule${index}Name", default.name) ?: default.name,
        enabled = p.getBoolean("schedule${index}Enabled", default.enabled),
        startMinuteOfDay = p.getInt("schedule${index}Start", default.startMinuteOfDay),
        endMinuteOfDay = p.getInt("schedule${index}End", default.endMinuteOfDay),
        minimumPerKm = p.getFloat("schedule${index}Minimum", default.minimumPerKm.toFloat()).toDouble(),
        excellentPerKm = p.getFloat("schedule${index}Excellent", default.excellentPerKm.toFloat()).toDouble()
    )

    private fun normalizeHex(raw: String, fallback: String): String {
        val candidate = raw.trim().uppercase().let { if (it.startsWith("#")) it else "#$it" }
        return if (Regex("^#[0-9A-F]{6}$").matches(candidate)) candidate else fallback
    }
}
