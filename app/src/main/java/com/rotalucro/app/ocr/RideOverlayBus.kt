package com.rotalucro.app.ocr

import android.content.Context
import android.content.Intent
import com.rotalucro.app.calculator.RideResult

object RideOverlayBus {
    const val ACTION_RESULT = "com.rotalucro.app.action.RIDE_RESULT"
    const val EXTRA_FARE = "fare"
    const val EXTRA_PER_KM = "per_km"
    const val EXTRA_PER_HOUR = "per_hour"
    const val EXTRA_DISTANCE = "distance"
    const val EXTRA_MINUTES = "minutes"
    const val EXTRA_PROFIT = "profit"
    const val EXTRA_MINIMUM = "minimum"
    const val EXTRA_EXCELLENT = "excellent"
    const val EXTRA_THRESHOLD_NAME = "threshold_name"
    const val EXTRA_RATING = "rating"

    fun publish(context: Context, result: RideResult) {
        context.sendBroadcast(
            Intent(ACTION_RESULT)
                .setPackage(context.packageName)
                .putExtra(EXTRA_FARE, result.fare)
                .putExtra(EXTRA_PER_KM, result.grossPerKm)
                .putExtra(EXTRA_PER_HOUR, result.grossPerHour)
                .putExtra(EXTRA_DISTANCE, result.totalDistanceKm)
                .putExtra(EXTRA_MINUTES, result.totalMinutes)
                .putExtra(EXTRA_PROFIT, result.estimatedProfit)
                .putExtra(EXTRA_MINIMUM, result.activeThreshold.minimumPerKm)
                .putExtra(EXTRA_EXCELLENT, result.activeThreshold.excellentPerKm)
                .putExtra(EXTRA_THRESHOLD_NAME, result.activeThreshold.name)
                .putExtra(EXTRA_RATING, result.rating.name)
        )
    }
}
