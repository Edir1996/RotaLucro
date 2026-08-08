package com.rotalucro.app.data

import android.content.Context
import com.rotalucro.app.calculator.OfferRating
import com.rotalucro.app.calculator.RideRecommendation
import com.rotalucro.app.calculator.RideResult

/** Keeps only the most recent analyzed offer so the floating menu can save it as accepted. */
object LastRideStore {
    private const val FILE = "last_ride"

    data class Snapshot(
        val timestamp: Long,
        val fare: Double,
        val pickupKm: Double,
        val tripKm: Double,
        val pickupMin: Int,
        val tripMin: Int,
        val totalKm: Double,
        val totalMin: Int,
        val basePerKm: Double,
        val analysisPerKm: Double,
        val analysisPerHour: Double,
        val profit: Double,
        val rating: OfferRating,
        val possibleEmptyReturn: Boolean,
        val emptyReturnKm: Double,
        val thresholdName: String,
        val destinationText: String?,
        val destinationLat: Double?,
        val destinationLon: Double?,
        val destinationCity: String?,
        val distanceToDemandKm: Double?,
        val demandZoneName: String?,
        val smartScore: Int,
        val recommendation: RideRecommendation
    )

    fun save(context: Context, result: RideResult) {
        val d = result.demandAssessment
        val e = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putLong("timestamp", System.currentTimeMillis())
            .putFloat("fare", result.fare.toFloat())
            .putFloat("pickupKm", result.offer.pickupDistanceKm.toFloat())
            .putFloat("tripKm", result.offer.tripDistanceKm.toFloat())
            .putInt("pickupMin", result.offer.pickupMinutes)
            .putInt("tripMin", result.offer.tripMinutes)
            .putFloat("totalKm", result.totalDistanceKm.toFloat())
            .putInt("totalMin", result.totalMinutes)
            .putFloat("basePerKm", result.grossPerKm.toFloat())
            .putFloat("analysisPerKm", result.analysisPerKm.toFloat())
            .putFloat("analysisPerHour", result.analysisPerHour.toFloat())
            .putFloat("profit", result.estimatedProfit.toFloat())
            .putString("rating", result.rating.name)
            .putBoolean("possibleEmptyReturn", result.possibleEmptyReturn)
            .putFloat("emptyReturnKm", result.emptyReturnDistanceKm.toFloat())
            .putString("thresholdName", result.activeThreshold.name)
            .putString("destinationText", result.offer.destinationLocationText)
            .putString("destinationCity", d?.destinationCity)
            .putString("demandZoneName", d?.nearestDemandZoneName)
            .putInt("smartScore", result.smartScore)
            .putString("recommendation", result.recommendation.name)
        if (d?.destinationLatitude != null) e.putFloat("destinationLat", d.destinationLatitude.toFloat()) else e.remove("destinationLat")
        if (d?.destinationLongitude != null) e.putFloat("destinationLon", d.destinationLongitude.toFloat()) else e.remove("destinationLon")
        if (d?.distanceToDemandKm != null) e.putFloat("distanceToDemandKm", d.distanceToDemandKm.toFloat()) else e.remove("distanceToDemandKm")
        e.apply()
    }

    fun load(context: Context): Snapshot? {
        val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val timestamp = p.getLong("timestamp", 0L)
        if (timestamp <= 0L) return null
        return Snapshot(
            timestamp = timestamp,
            fare = p.getFloat("fare", 0f).toDouble(),
            pickupKm = p.getFloat("pickupKm", 0f).toDouble(),
            tripKm = p.getFloat("tripKm", 0f).toDouble(),
            pickupMin = p.getInt("pickupMin", 0),
            tripMin = p.getInt("tripMin", 0),
            totalKm = p.getFloat("totalKm", 0f).toDouble(),
            totalMin = p.getInt("totalMin", 0),
            basePerKm = p.getFloat("basePerKm", 0f).toDouble(),
            analysisPerKm = p.getFloat("analysisPerKm", 0f).toDouble(),
            analysisPerHour = p.getFloat("analysisPerHour", 0f).toDouble(),
            profit = p.getFloat("profit", 0f).toDouble(),
            rating = runCatching { OfferRating.valueOf(p.getString("rating", OfferRating.ATTENTION.name)!!) }.getOrDefault(OfferRating.ATTENTION),
            possibleEmptyReturn = p.getBoolean("possibleEmptyReturn", false),
            emptyReturnKm = p.getFloat("emptyReturnKm", 0f).toDouble(),
            thresholdName = p.getString("thresholdName", "Faixa padrão").orEmpty(),
            destinationText = p.getString("destinationText", null),
            destinationLat = if (p.contains("destinationLat")) p.getFloat("destinationLat", 0f).toDouble() else null,
            destinationLon = if (p.contains("destinationLon")) p.getFloat("destinationLon", 0f).toDouble() else null,
            destinationCity = p.getString("destinationCity", null),
            distanceToDemandKm = if (p.contains("distanceToDemandKm")) p.getFloat("distanceToDemandKm", 0f).toDouble() else null,
            demandZoneName = p.getString("demandZoneName", null),
            smartScore = p.getInt("smartScore", 0),
            recommendation = runCatching { RideRecommendation.valueOf(p.getString("recommendation", RideRecommendation.CAUTION.name)!!) }.getOrDefault(RideRecommendation.CAUTION)
        )
    }
}
