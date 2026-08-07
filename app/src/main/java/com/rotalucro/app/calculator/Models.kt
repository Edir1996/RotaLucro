package com.rotalucro.app.calculator

data class RideOffer(
    val fare: Double,
    val pickupDistanceKm: Double,
    val tripDistanceKm: Double,
    val pickupMinutes: Int,
    val tripMinutes: Int,
    val sourceText: List<String> = emptyList()
)

data class KmThreshold(
    val name: String,
    val minimumPerKm: Double,
    val excellentPerKm: Double,
    val startMinuteOfDay: Int? = null,
    val endMinuteOfDay: Int? = null
)

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

        // Início e fim iguais são tratados como uma faixa vazia para evitar
        // que uma configuração acidental substitua o perfil padrão o dia inteiro.
        if (start == end) return false

        return if (start < end) {
            minute in start until end
        } else {
            // Permite horários que atravessam a meia-noite, por exemplo 22:00–02:00.
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
    val vehicleKmPerLiter: Double = 10.0,
    val maintenancePerKm: Double = 0.35
) {
    fun activeKmThreshold(minuteOfDay: Int): KmThreshold {
        return scheduledThresholds
            .firstOrNull { it.matches(minuteOfDay) }
            ?.asActiveThreshold()
            ?: KmThreshold(
                name = "Fora da dinâmica",
                minimumPerKm = defaultMinimumPerKm,
                excellentPerKm = defaultExcellentPerKm
            )
    }

    companion object {
        fun defaultScheduledThresholds(): List<ScheduledKmThreshold> = listOf(
            ScheduledKmThreshold(
                name = "Dinâmica 1",
                enabled = false,
                startMinuteOfDay = 11 * 60,
                endMinuteOfDay = 14 * 60,
                minimumPerKm = 1.40,
                excellentPerKm = 2.00
            ),
            ScheduledKmThreshold(
                name = "Dinâmica 2",
                enabled = false,
                startMinuteOfDay = 18 * 60,
                endMinuteOfDay = 22 * 60,
                minimumPerKm = 1.40,
                excellentPerKm = 2.00
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
    val fare: Double,
    val totalDistanceKm: Double,
    val totalMinutes: Int,
    val grossPerKm: Double,
    val grossPerHour: Double,
    val fuelCost: Double,
    val maintenanceCost: Double,
    val estimatedProfit: Double,
    val profitPerKm: Double,
    val activeThreshold: KmThreshold,
    val perKmRating: OfferRating,
    val rating: OfferRating
) {
    val estimatedCost: Double
        get() = fuelCost + maintenanceCost
}
