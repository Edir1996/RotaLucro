package com.rotalucro.app.data

import android.content.Context
import android.content.SharedPreferences
import com.rotalucro.app.calculator.DriverSettings
import com.rotalucro.app.calculator.ScheduledKmThreshold

object SettingsStore {
    private const val FILE_NAME = "driver_settings"

    fun load(context: Context): DriverSettings {
        val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
        val defaults = DriverSettings.defaultScheduledThresholds()

        return DriverSettings(
            defaultMinimumPerKm = preferences.getFloat("defaultMinimumPerKm", 1.20f).toDouble(),
            defaultExcellentPerKm = preferences.getFloat("defaultExcellentPerKm", 1.80f).toDouble(),
            scheduledThresholds = defaults.mapIndexed { index, default ->
                loadSchedule(preferences, index + 1, default)
            },
            fuelPricePerLiter = preferences.getFloat("fuelPricePerLiter", 6.0f).toDouble(),
            vehicleKmPerLiter = preferences.getFloat("vehicleKmPerLiter", 35.0f).toDouble(),
            maintenancePerKm = preferences.getFloat("maintenancePerKm", 0.18f).toDouble(),
            overlayAutoHideSeconds = preferences.getInt("overlayAutoHideSeconds", 18).coerceIn(8, 45)
        )
    }

    fun save(context: Context, settings: DriverSettings) {
        val editor = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE).edit()
            .putFloat("defaultMinimumPerKm", settings.defaultMinimumPerKm.toFloat())
            .putFloat("defaultExcellentPerKm", settings.defaultExcellentPerKm.toFloat())
            .putFloat("fuelPricePerLiter", settings.fuelPricePerLiter.toFloat())
            .putFloat("vehicleKmPerLiter", settings.vehicleKmPerLiter.toFloat())
            .putFloat("maintenancePerKm", settings.maintenancePerKm.toFloat())
            .putInt("overlayAutoHideSeconds", settings.overlayAutoHideSeconds.coerceIn(8, 45))

        settings.scheduledThresholds.take(4).forEachIndexed { index, schedule ->
            val number = index + 1
            editor
                .putString("schedule${number}Name", schedule.name)
                .putBoolean("schedule${number}Enabled", schedule.enabled)
                .putInt("schedule${number}Start", schedule.startMinuteOfDay)
                .putInt("schedule${number}End", schedule.endMinuteOfDay)
                .putFloat("schedule${number}Minimum", schedule.minimumPerKm.toFloat())
                .putFloat("schedule${number}Excellent", schedule.excellentPerKm.toFloat())
        }

        editor.apply()
    }

    private fun loadSchedule(
        preferences: SharedPreferences,
        index: Int,
        default: ScheduledKmThreshold
    ): ScheduledKmThreshold {
        return ScheduledKmThreshold(
            name = preferences.getString("schedule${index}Name", default.name) ?: default.name,
            enabled = preferences.getBoolean("schedule${index}Enabled", default.enabled),
            startMinuteOfDay = preferences.getInt("schedule${index}Start", default.startMinuteOfDay),
            endMinuteOfDay = preferences.getInt("schedule${index}End", default.endMinuteOfDay),
            minimumPerKm = preferences.getFloat("schedule${index}Minimum", default.minimumPerKm.toFloat()).toDouble(),
            excellentPerKm = preferences.getFloat("schedule${index}Excellent", default.excellentPerKm.toFloat()).toDouble()
        )
    }
}
