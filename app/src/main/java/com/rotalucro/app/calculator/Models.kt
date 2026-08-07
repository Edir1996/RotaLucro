package com.rotalucro.app.calculator

data class RideOffer(
    val fare: Double,
    val pickupDistanceKm: Double,
    val tripDistanceKm: Double,
    val pickupMinutes: Int,
    val tripMinutes: Int,
    val sourceText: List<String> = emptyList()
)

data class DriverSettings(
    val minimumPerKm: Double = 2.0,
    val minimumPerHour: Double = 35.0,
    val fuelPricePerLiter: Double = 6.0,
    val vehicleKmPerLiter: Double = 10.0,
    val maintenancePerKm: Double = 0.35
)

enum class OfferRating {
    GOOD,
    ATTENTION,
    BAD
}

data class RideResult(
    val totalDistanceKm: Double,
    val totalMinutes: Int,
    val grossPerKm: Double,
    val grossPerHour: Double,
    val fuelCost: Double,
    val maintenanceCost: Double,
    val estimatedProfit: Double,
    val profitPerKm: Double,
    val rating: OfferRating
)
