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
    val pickupLocationText: String? = null,
    val destinationLocationText: String? = null,
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
        return if (start < end) minute in start until end else minute >= start || minute < end
    }

    fun asActiveThreshold(): KmThreshold = KmThreshold(
        name = name,
        minimumPerKm = minimumPerKm,
        excellentPerKm = excellentPerKm,
        startMinuteOfDay = startMinuteOfDay,
        endMinuteOfDay = endMinuteOfDay
    )
}

enum class DemandLevel { UNKNOWN, LOW, MEDIUM, HIGH, VERY_HIGH }

enum class DemandDistanceClass(val label: String) {
    EXCELLENT("Excelente"),
    GOOD("Boa"),
    ATTENTION("Atenção"),
    FAR("Afastada"),
    BAD("Ruim"),
    VERY_BAD("Muito ruim"),
    UNKNOWN("Sem localização")
}

enum class RideRecommendation { ACCEPT, CAUTION, REJECT }

data class DemandAssessment(
    val destinationText: String? = null,
    val destinationLatitude: Double? = null,
    val destinationLongitude: Double? = null,
    val destinationCity: String? = null,
    val nearestDemandZoneName: String? = null,
    val distanceToDemandKm: Double? = null,
    val distanceClass: DemandDistanceClass = DemandDistanceClass.UNKNOWN,
    val demandLevel: DemandLevel = DemandLevel.UNKNOWN,
    val learnedConfidence: Int = 0,
    val outsideBaseCity: Boolean = false,
    val source: String = "Sem dados"
)

data class DriverSettings(
    val defaultMinimumPerKm: Double = 1.20,
    val defaultExcellentPerKm: Double = 1.80,
    val scheduledThresholds: List<ScheduledKmThreshold> = defaultScheduledThresholds(),
    val fuelPricePerLiter: Double = 6.0,
    val vehicleKmPerLiter: Double = 35.0,
    val maintenancePerKm: Double = 0.18,
    val overlayAutoHideSeconds: Int = 18,
    // Box / overlay
    val overlayYPercent: Int = 5,
    val overlayXPercent: Int = 50,
    val overlayWidthPercent: Int = 94,
    val overlayOpacityPercent: Int = 96,
    val overlayScalePercent: Int = 100,
    val overlayBackgroundHex: String = "#FFFFFF",
    val overlayTextHex: String = "#0F172A",
    val overlayBadHex: String = "#EF4444",
    val overlayAttentionHex: String = "#F59E0B",
    val overlayGoodHex: String = "#22C55E",
    // Retorno vazio legado / fallback
    val emptyReturnEnabled: Boolean = true,
    val emptyReturnTripKmThreshold: Double = 10.0,
    val emptyReturnDistanceFactor: Double = 1.0,
    // Demanda inteligente
    val smartDemandEnabled: Boolean = true,
    val baseCity: String = "Itajaí, SC",
    val demandReturnFactor: Double = 1.0,
    val premiumMin5To7Km: Double = 1.40,
    val premiumMin7To9Km: Double = 1.60,
    val premiumMin9PlusKm: Double = 1.80,
    val demandLearningEnabled: Boolean = true
) {
    fun activeKmThreshold(minuteOfDay: Int): KmThreshold = scheduledThresholds
        .firstOrNull { it.matches(minuteOfDay) }
        ?.asActiveThreshold()
        ?: KmThreshold(
            name = "Faixa padrão",
            minimumPerKm = defaultMinimumPerKm,
            excellentPerKm = defaultExcellentPerKm
        )

    companion object {
        fun defaultScheduledThresholds(): List<ScheduledKmThreshold> = listOf(
            ScheduledKmThreshold("Almoço", false, 11 * 60, 14 * 60, 1.40, 2.00),
            ScheduledKmThreshold("Pico da tarde", false, 17 * 60, 20 * 60, 1.40, 2.00),
            ScheduledKmThreshold("Noite", false, 20 * 60, 23 * 60, 1.50, 2.10),
            ScheduledKmThreshold("Madrugada", false, 23 * 60, 2 * 60, 1.60, 2.20)
        )
    }
}

enum class OfferRating { GOOD, ATTENTION, BAD }

data class RideResult(
    val offer: RideOffer,
    val totalDistanceKm: Double,
    val totalMinutes: Int,
    val grossPerKm: Double,
    val grossPerHour: Double,
    val analysisDistanceKm: Double,
    val analysisMinutes: Int,
    val analysisPerKm: Double,
    val analysisPerHour: Double,
    val possibleEmptyReturn: Boolean,
    val emptyReturnDistanceKm: Double,
    val fuelCost: Double,
    val maintenanceCost: Double,
    val estimatedProfit: Double,
    val profitPerKm: Double,
    val activeThreshold: KmThreshold,
    val effectiveMinimumPerKm: Double,
    val effectiveExcellentPerKm: Double,
    val demandAssessment: DemandAssessment? = null,
    val smartScore: Int = 0,
    val recommendation: RideRecommendation = RideRecommendation.CAUTION,
    val rating: OfferRating
) {
    val fare: Double get() = offer.fare
    val estimatedCost: Double get() = fuelCost + maintenanceCost
}

data class DetectedRouteSegment(val minutes: Int, val distanceKm: Double)

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
