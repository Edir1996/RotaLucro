package com.rotalucro.app.calculator

import kotlin.math.max

object RideCalculator {
    fun calculate(offer: RideOffer, settings: DriverSettings): RideResult {
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

        val meetsKm = grossPerKm >= settings.minimumPerKm
        val meetsHour = grossPerHour >= settings.minimumPerHour
        val rating = when {
            meetsKm && meetsHour && estimatedProfit > 0 -> OfferRating.GOOD
            meetsKm || meetsHour -> OfferRating.ATTENTION
            else -> OfferRating.BAD
        }

        return RideResult(
            totalDistanceKm = totalDistance,
            totalMinutes = totalMinutes,
            grossPerKm = grossPerKm,
            grossPerHour = grossPerHour,
            fuelCost = fuelCost,
            maintenanceCost = maintenanceCost,
            estimatedProfit = estimatedProfit,
            profitPerKm = profitPerKm,
            rating = rating
        )
    }
}
