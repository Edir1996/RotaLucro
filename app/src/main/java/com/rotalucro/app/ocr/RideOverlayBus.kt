package com.rotalucro.app.ocr

import android.content.Context
import android.content.Intent
import com.rotalucro.app.calculator.OfferRating
import com.rotalucro.app.calculator.RideResult

object RideOverlayBus {
    const val ACTION_RESULT = "com.rotalucro.app.action.RIDE_RESULT"
    const val EXTRA_FARE = "fare"
    const val EXTRA_PER_KM = "per_km"
    const val EXTRA_PER_HOUR = "per_hour"
    const val EXTRA_ANALYSIS_PER_KM = "analysis_per_km"
    const val EXTRA_ANALYSIS_PER_HOUR = "analysis_per_hour"
    const val EXTRA_DISTANCE = "distance"
    const val EXTRA_MINUTES = "minutes"
    const val EXTRA_ANALYSIS_DISTANCE = "analysis_distance"
    const val EXTRA_ANALYSIS_MINUTES = "analysis_minutes"
    const val EXTRA_EMPTY_RETURN = "empty_return"
    const val EXTRA_EMPTY_RETURN_KM = "empty_return_km"
    const val EXTRA_PROFIT = "profit"
    const val EXTRA_MINIMUM = "minimum"
    const val EXTRA_EXCELLENT = "excellent"
    const val EXTRA_THRESHOLD_NAME = "threshold_name"
    const val EXTRA_RATING = "rating"
    const val EXTRA_PREVIEW = "preview"

    fun publish(context: Context, result: RideResult) {
        context.sendBroadcast(baseIntent(context, result).putExtra(EXTRA_PREVIEW, false))
    }

    fun publishPreview(context: Context) {
        context.sendBroadcast(
            Intent(ACTION_RESULT).setPackage(context.packageName)
                .putExtra(EXTRA_FARE, 18.50)
                .putExtra(EXTRA_PER_KM, 1.85)
                .putExtra(EXTRA_PER_HOUR, 52.85)
                .putExtra(EXTRA_ANALYSIS_PER_KM, 1.85)
                .putExtra(EXTRA_ANALYSIS_PER_HOUR, 52.85)
                .putExtra(EXTRA_DISTANCE, 10.0)
                .putExtra(EXTRA_MINUTES, 21)
                .putExtra(EXTRA_ANALYSIS_DISTANCE, 10.0)
                .putExtra(EXTRA_ANALYSIS_MINUTES, 21)
                .putExtra(EXTRA_EMPTY_RETURN, false)
                .putExtra(EXTRA_EMPTY_RETURN_KM, 0.0)
                .putExtra(EXTRA_PROFIT, 15.20)
                .putExtra(EXTRA_MINIMUM, 1.20)
                .putExtra(EXTRA_EXCELLENT, 1.80)
                .putExtra(EXTRA_THRESHOLD_NAME, "Prévia do box")
                .putExtra(EXTRA_RATING, OfferRating.GOOD.name)
                .putExtra(EXTRA_PREVIEW, true)
        )
    }

    private fun baseIntent(context: Context, result: RideResult) = Intent(ACTION_RESULT)
        .setPackage(context.packageName)
        .putExtra(EXTRA_FARE, result.fare)
        .putExtra(EXTRA_PER_KM, result.grossPerKm)
        .putExtra(EXTRA_PER_HOUR, result.grossPerHour)
        .putExtra(EXTRA_ANALYSIS_PER_KM, result.analysisPerKm)
        .putExtra(EXTRA_ANALYSIS_PER_HOUR, result.analysisPerHour)
        .putExtra(EXTRA_DISTANCE, result.totalDistanceKm)
        .putExtra(EXTRA_MINUTES, result.totalMinutes)
        .putExtra(EXTRA_ANALYSIS_DISTANCE, result.analysisDistanceKm)
        .putExtra(EXTRA_ANALYSIS_MINUTES, result.analysisMinutes)
        .putExtra(EXTRA_EMPTY_RETURN, result.possibleEmptyReturn)
        .putExtra(EXTRA_EMPTY_RETURN_KM, result.emptyReturnDistanceKm)
        .putExtra(EXTRA_PROFIT, result.estimatedProfit)
        .putExtra(EXTRA_MINIMUM, result.activeThreshold.minimumPerKm)
        .putExtra(EXTRA_EXCELLENT, result.activeThreshold.excellentPerKm)
        .putExtra(EXTRA_THRESHOLD_NAME, result.activeThreshold.name)
        .putExtra(EXTRA_RATING, result.rating.name)
}
