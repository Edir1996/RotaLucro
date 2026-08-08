package com.rotalucro.app.ocr

import android.content.Context
import android.content.Intent
import com.rotalucro.app.calculator.OfferRating
import com.rotalucro.app.calculator.RideRecommendation
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
    const val EXTRA_SMART_SCORE = "smart_score"
    const val EXTRA_RECOMMENDATION = "recommendation"
    const val EXTRA_DEMAND_DISTANCE = "demand_distance"
    const val EXTRA_DEMAND_CLASS = "demand_class"
    const val EXTRA_DEMAND_ZONE = "demand_zone"
    const val EXTRA_DEMAND_LEVEL = "demand_level"
    const val EXTRA_OUTSIDE_CITY = "outside_city"
    const val EXTRA_DESTINATION = "destination"

    fun publish(context: Context, result: RideResult) {
        context.sendBroadcast(baseIntent(context, result).putExtra(EXTRA_PREVIEW, false))
    }

    fun publishPreview(context: Context) {
        context.sendBroadcast(
            Intent(ACTION_RESULT).setPackage(context.packageName)
                .putExtra(EXTRA_FARE, 18.50)
                .putExtra(EXTRA_PER_KM, 1.85)
                .putExtra(EXTRA_PER_HOUR, 52.85)
                .putExtra(EXTRA_ANALYSIS_PER_KM, 1.32)
                .putExtra(EXTRA_ANALYSIS_PER_HOUR, 39.20)
                .putExtra(EXTRA_DISTANCE, 10.0)
                .putExtra(EXTRA_MINUTES, 21)
                .putExtra(EXTRA_ANALYSIS_DISTANCE, 14.0)
                .putExtra(EXTRA_ANALYSIS_MINUTES, 29)
                .putExtra(EXTRA_EMPTY_RETURN, true)
                .putExtra(EXTRA_EMPTY_RETURN_KM, 4.0)
                .putExtra(EXTRA_PROFIT, 14.20)
                .putExtra(EXTRA_MINIMUM, 1.40)
                .putExtra(EXTRA_EXCELLENT, 2.00)
                .putExtra(EXTRA_THRESHOLD_NAME, "Prévia inteligente")
                .putExtra(EXTRA_RATING, OfferRating.ATTENTION.name)
                .putExtra(EXTRA_SMART_SCORE, 61)
                .putExtra(EXTRA_RECOMMENDATION, RideRecommendation.CAUTION.name)
                .putExtra(EXTRA_DEMAND_DISTANCE, 4.0)
                .putExtra(EXTRA_DEMAND_CLASS, "Boa")
                .putExtra(EXTRA_DEMAND_ZONE, "Centro / Beira-Rio")
                .putExtra(EXTRA_DEMAND_LEVEL, "ALTA")
                .putExtra(EXTRA_OUTSIDE_CITY, false)
                .putExtra(EXTRA_DESTINATION, "Destino de exemplo")
                .putExtra(EXTRA_PREVIEW, true)
        )
    }

    private fun baseIntent(context: Context, result: RideResult): Intent {
        val d = result.demandAssessment
        return Intent(ACTION_RESULT)
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
            .putExtra(EXTRA_MINIMUM, result.effectiveMinimumPerKm)
            .putExtra(EXTRA_EXCELLENT, result.effectiveExcellentPerKm)
            .putExtra(EXTRA_THRESHOLD_NAME, result.activeThreshold.name)
            .putExtra(EXTRA_RATING, result.rating.name)
            .putExtra(EXTRA_SMART_SCORE, result.smartScore)
            .putExtra(EXTRA_RECOMMENDATION, result.recommendation.name)
            .putExtra(EXTRA_DEMAND_DISTANCE, d?.distanceToDemandKm ?: -1.0)
            .putExtra(EXTRA_DEMAND_CLASS, d?.distanceClass?.label.orEmpty())
            .putExtra(EXTRA_DEMAND_ZONE, d?.nearestDemandZoneName.orEmpty())
            .putExtra(EXTRA_DEMAND_LEVEL, d?.demandLevel?.name.orEmpty())
            .putExtra(EXTRA_OUTSIDE_CITY, d?.outsideBaseCity ?: false)
            .putExtra(EXTRA_DESTINATION, result.offer.destinationLocationText.orEmpty())
    }
}
