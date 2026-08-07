package com.rotalucro.app.data

import android.content.Context
import com.rotalucro.app.calculator.DriverSettings

object SettingsStore {
    private const val FILE_NAME = "driver_settings"

    fun load(context: Context): DriverSettings {
        val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
        return DriverSettings(
            minimumPerKm = preferences.getFloat("minimumPerKm", 2.0f).toDouble(),
            minimumPerHour = preferences.getFloat("minimumPerHour", 35.0f).toDouble(),
            fuelPricePerLiter = preferences.getFloat("fuelPricePerLiter", 6.0f).toDouble(),
            vehicleKmPerLiter = preferences.getFloat("vehicleKmPerLiter", 10.0f).toDouble(),
            maintenancePerKm = preferences.getFloat("maintenancePerKm", 0.35f).toDouble()
        )
    }

    fun save(context: Context, settings: DriverSettings) {
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat("minimumPerKm", settings.minimumPerKm.toFloat())
            .putFloat("minimumPerHour", settings.minimumPerHour.toFloat())
            .putFloat("fuelPricePerLiter", settings.fuelPricePerLiter.toFloat())
            .putFloat("vehicleKmPerLiter", settings.vehicleKmPerLiter.toFloat())
            .putFloat("maintenancePerKm", settings.maintenancePerKm.toFloat())
            .apply()
    }
}
