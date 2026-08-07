package com.rotalucro.app.calculator

import java.time.LocalTime
import kotlin.math.max
import kotlin.math.min

object RideCalculator {
    fun calculate(
        offer: RideOffer,
        settings: DriverSettings,
        minuteOfDay: Int = currentMinuteOfDay()
    ): RideResult {
        val totalDistance = max(offer.pickupDistanceKm + offer.tripDistanceKm, 0.01)
        val totalMinutes = max(offer.pickupMinutes + offer.tripMinutes, 1)
        val totalHours = totalMinutes / 60.0

        val grossPerKm = offer.fare / totalDistance
        val grossPerHour = offer.fare / totalHours
        val fuelCost = if (settings.vehicleKmPerLiter > 0) {
            (totalDistance / settings.vehicleKmPerLiter) * settings.fuelPricePerLiter
        } else {
            0.0
        }
        val maintenanceCost = totalDistance * settings.maintenancePerKm
        val estimatedProfit = offer.fare - fuelCost - maintenanceCost
        val profitPerKm = estimatedProfit / totalDistance

        val activeThreshold = settings.activeKmThreshold(minuteOfDay)
        val rating = rateMetric(
            value = grossPerKm,
            minimum = activeThreshold.minimumPerKm,
            excellent = activeThreshold.excellentPerKm
        )

        return RideResult(
            offer = offer,
            totalDistanceKm = totalDistance,
            totalMinutes = totalMinutes,
            grossPerKm = grossPerKm,
            grossPerHour = grossPerHour,
            fuelCost = fuelCost,
            maintenanceCost = maintenanceCost,
            estimatedProfit = estimatedProfit,
            profitPerKm = profitPerKm,
            activeThreshold = activeThreshold,
            rating = rating
        )
    }

    fun rateMetric(value: Double, minimum: Double, excellent: Double): OfferRating {
        val lower = min(minimum, excellent)
        val upper = max(minimum, excellent)
        return when {
            value < lower -> OfferRating.BAD
            value >= upper -> OfferRating.GOOD
            else -> OfferRating.ATTENTION
        }
    }

    fun middleReference(minimum: Double, excellent: Double): Double =
        (minimum + excellent) / 2.0

    fun currentMinuteOfDay(): Int {
        val now = LocalTime.now()
        return now.hour * 60 + now.minute
    }
}
