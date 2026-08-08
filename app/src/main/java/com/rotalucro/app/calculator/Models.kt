package com.rotalucro.app.calculator

data class RideOffer(
    val fare: Double,
    val pickupDistanceKm: Double,
    val tripDistanceKm: Double,
    val pickupMinutes: Int,
    val tripMinutes: Int,
    val surgeMultiplier: Double? = null,
    val dynamicBaseFare: Double? = null,
    val productName: String? = null,
    val sourceText: List<String> = emptyList()
)

data class KmThreshold(
    val name: String,
    val minimumPerKm: Double,
    val excellentPerKm: Double,
    val startMinuteOfDay: Int? = null,
    val endMinuteOfDay: Int? = null
) {
    val middlePerKm: Double
        get() = (minimumPerKm + excellentPerKm) / 2.0
}

data class ScheduledKmThreshold(
    val name: String,
    val enabled: Boolean,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    val minimumPerKm: Double,
    val excellentPerKm: Double
) {
    fun matches(minuteOfDay: Int): Boolean {
        if (!enabled) return false

        val minute = minuteOfDay.coerceIn(0, 1439)
        val start = startMinuteOfDay.coerceIn(0, 1439)
        val end = endMinuteOfDay.coerceIn(0, 1439)
        if (start == end) return false

        return if (start < end) {
            minute in start until end
        } else {
            minute >= start || minute < end
        }
    }

    fun asActiveThreshold(): KmThreshold = KmThreshold(
        name = name,
        minimumPerKm = minimumPerKm,
        excellentPerKm = excellentPerKm,
        startMinuteOfDay = startMinuteOfDay,
        endMinuteOfDay = endMinuteOfDay
    )
}

data class DriverSettings(
    val defaultMinimumPerKm: Double = 1.20,
    val defaultExcellentPerKm: Double = 1.80,
    val scheduledThresholds: List<ScheduledKmThreshold> = defaultScheduledThresholds(),
    val fuelPricePerLiter: Double = 6.0,
    val vehicleKmPerLiter: Double = 35.0,
    val maintenancePerKm: Double = 0.18,
    val overlayAutoHideSeconds: Int = 18
) {
    fun activeKmThreshold(minuteOfDay: Int): KmThreshold {
        return scheduledThresholds
            .firstOrNull { it.matches(minuteOfDay) }
            ?.asActiveThreshold()
            ?: KmThreshold(
                name = "Faixa padrão",
                minimumPerKm = defaultMinimumPerKm,
                excellentPerKm = defaultExcellentPerKm
            )
    }

    companion object {
        fun defaultScheduledThresholds(): List<ScheduledKmThreshold> = listOf(
            ScheduledKmThreshold(
                name = "Almoço",
                enabled = false,
                startMinuteOfDay = 11 * 60,
                endMinuteOfDay = 14 * 60,
                minimumPerKm = 1.40,
                excellentPerKm = 2.00
            ),
            ScheduledKmThreshold(
                name = "Pico da tarde",
                enabled = false,
                startMinuteOfDay = 17 * 60,
                endMinuteOfDay = 20 * 60,
                minimumPerKm = 1.40,
                excellentPerKm = 2.00
            ),
            ScheduledKmThreshold(
                name = "Noite",
                enabled = false,
                startMinuteOfDay = 20 * 60,
                endMinuteOfDay = 23 * 60,
                minimumPerKm = 1.50,
                excellentPerKm = 2.10
            ),
            ScheduledKmThreshold(
                name = "Madrugada",
                enabled = false,
                startMinuteOfDay = 23 * 60,
                endMinuteOfDay = 2 * 60,
                minimumPerKm = 1.60,
                excellentPerKm = 2.20
            )
        )
    }
}

enum class OfferRating {
    GOOD,
    ATTENTION,
    BAD
}

data class RideResult(
    val offer: RideOffer,
    val totalDistanceKm: Double,
    val totalMinutes: Int,
    val grossPerKm: Double,
    val grossPerHour: Double,
    val fuelCost: Double,
    val maintenanceCost: Double,
    val estimatedProfit: Double,
    val profitPerKm: Double,
    val activeThreshold: KmThreshold,
    val rating: OfferRating
) {
    val fare: Double
        get() = offer.fare

    val estimatedCost: Double
        get() = fuelCost + maintenanceCost
}

data class DetectedRouteSegment(
    val minutes: Int,
    val distanceKm: Double
)

data class ParseAttempt(
    val offer: RideOffer?,
    val reason: String,
    val normalizedTextCount: Int,
    val normalizedTexts: List<String> = emptyList(),
    val fare: Double? = null,
    val pickupSegment: DetectedRouteSegment? = null,
    val tripSegment: DetectedRouteSegment? = null,
    val surgeMultiplier: Double? = null,
    val dynamicBaseFare: Double? = null,
    val productName: String? = null
)
