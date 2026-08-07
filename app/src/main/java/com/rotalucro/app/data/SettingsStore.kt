package com.rotalucro.app.data

import android.content.Context
import com.rotalucro.app.calculator.DriverSettings
import com.rotalucro.app.calculator.ScheduledKmThreshold

object SettingsStore {
    private const val FILE_NAME = "driver_settings"

    fun load(context: Context): DriverSettings {
        val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

        val oldMinimum = preferences.getFloat("minimumPerKm", 1.20f)
        val oldExcellent = preferences.getFloat("excellentPerKm", 1.80f)

        return DriverSettings(
            defaultMinimumPerKm = preferences
                .getFloat("defaultMinimumPerKm", oldMinimum)
                .toDouble(),
            defaultExcellentPerKm = preferences
                .getFloat("defaultExcellentPerKm", oldExcellent)
                .toDouble(),
            scheduledThresholds = listOf(
                loadSchedule(
                    preferences = preferences,
                    index = 1,
                    defaultName = "Dinâmica 1",
                    defaultStart = 11 * 60,
                    defaultEnd = 14 * 60
                ),
                loadSchedule(
                    preferences = preferences,
                    index = 2,
                    defaultName = "Dinâmica 2",
                    defaultStart = 18 * 60,
                    defaultEnd = 22 * 60
                )
            ),
            fuelPricePerLiter = preferences.getFloat("fuelPricePerLiter", 6.0f).toDouble(),
            vehicleKmPerLiter = preferences.getFloat("vehicleKmPerLiter", 10.0f).toDouble(),
            maintenancePerKm = preferences.getFloat("maintenancePerKm", 0.35f).toDouble()
        )
    }

    fun save(context: Context, settings: DriverSettings) {
        val editor = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE).edit()
            .putFloat("defaultMinimumPerKm", settings.defaultMinimumPerKm.toFloat())
            .putFloat("defaultExcellentPerKm", settings.defaultExcellentPerKm.toFloat())
            .putFloat("fuelPricePerLiter", settings.fuelPricePerLiter.toFloat())
            .putFloat("vehicleKmPerLiter", settings.vehicleKmPerLiter.toFloat())
            .putFloat("maintenancePerKm", settings.maintenancePerKm.toFloat())

        settings.scheduledThresholds.take(2).forEachIndexed { index, schedule ->
            val number = index + 1
            editor
                .putBoolean("schedule${number}Enabled", schedule.enabled)
                .putInt("schedule${number}Start", schedule.startMinuteOfDay)
                .putInt("schedule${number}End", schedule.endMinuteOfDay)
                .putFloat("schedule${number}Minimum", schedule.minimumPerKm.toFloat())
                .putFloat("schedule${number}Excellent", schedule.excellentPerKm.toFloat())
        }

        editor.apply()
    }

    private fun loadSchedule(
        preferences: android.content.SharedPreferences,
        index: Int,
        defaultName: String,
        defaultStart: Int,
        defaultEnd: Int
    ): ScheduledKmThreshold {
        return ScheduledKmThreshold(
            name = defaultName,
            enabled = preferences.getBoolean("schedule${index}Enabled", false),
            startMinuteOfDay = preferences.getInt("schedule${index}Start", defaultStart),
            endMinuteOfDay = preferences.getInt("schedule${index}End", defaultEnd),
            minimumPerKm = preferences.getFloat("schedule${index}Minimum", 1.40f).toDouble(),
            excellentPerKm = preferences.getFloat("schedule${index}Excellent", 2.00f).toDouble()
        )
    }
}
