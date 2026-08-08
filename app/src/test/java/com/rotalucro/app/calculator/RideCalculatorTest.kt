package com.rotalucro.app.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RideCalculatorTest {
    private val settings = DriverSettings(
        defaultMinimumPerKm = 1.20,
        defaultExcellentPerKm = 1.80,
        scheduledThresholds = listOf(
            ScheduledKmThreshold(
                name = "Pico",
                enabled = true,
                startMinuteOfDay = 18 * 60,
                endMinuteOfDay = 22 * 60,
                minimumPerKm = 1.40,
                excellentPerKm = 2.00
            )
        ),
        fuelPricePerLiter = 6.0,
        vehicleKmPerLiter = 30.0,
        maintenancePerKm = 0.20
    )

    @Test
    fun parsesReal99MotoOfferAndIgnoresDynamicBaseFare() {
        val attempt = OfferParser.parseWithDiagnostics(
            listOf(
                "Moto",
                "R$8,40",
                "⚡1,6x",
                "0% Taxa de serviço",
                "R$1,27 Tarifa base dinâmica incl.",
                "4,86 • 302 corridas",
                "Perfil Premium",
                "6min (2km)",
                "Rua de origem",
                "5min (2,3km)",
                "Rua de destino",
                "Aceitar"
            )
        )

        assertNotNull(attempt.offer)
        val offer = requireNotNull(attempt.offer)
        assertEquals(8.40, offer.fare, 0.001)
        assertEquals(2.0, offer.pickupDistanceKm, 0.001)
        assertEquals(2.3, offer.tripDistanceKm, 0.001)
        assertEquals(6, offer.pickupMinutes)
        assertEquals(5, offer.tripMinutes)
        assertEquals(1.6, offer.surgeMultiplier ?: 0.0, 0.001)
        assertEquals(1.27, offer.dynamicBaseFare ?: 0.0, 0.001)
    }

    @Test
    fun calculatesRealOfferMetrics() {
        val result = RideCalculator.calculate(
            offer = RideOffer(
                fare = 8.40,
                pickupDistanceKm = 2.0,
                tripDistanceKm = 2.3,
                pickupMinutes = 6,
                tripMinutes = 5
            ),
            settings = settings,
            minuteOfDay = 10 * 60
        )

        assertEquals(4.3, result.totalDistanceKm, 0.001)
        assertEquals(11, result.totalMinutes)
        assertEquals(1.953, result.grossPerKm, 0.01)
        assertEquals(45.818, result.grossPerHour, 0.01)
        assertEquals(OfferRating.GOOD, result.rating)
    }

    @Test
    fun belowMinimumIsRed() {
        assertEquals(OfferRating.BAD, RideCalculator.rateMetric(1.10, 1.20, 1.80))
    }

    @Test
    fun betweenMinimumAndExcellentIsYellow() {
        assertEquals(OfferRating.ATTENTION, RideCalculator.rateMetric(1.50, 1.20, 1.80))
    }

    @Test
    fun excellentOrHigherIsGreen() {
        assertEquals(OfferRating.GOOD, RideCalculator.rateMetric(1.80, 1.20, 1.80))
    }

    @Test
    fun dynamicScheduleChangesMinimumByTime() {
        val outside = settings.activeKmThreshold(10 * 60)
        val peak = settings.activeKmThreshold(19 * 60)

        assertEquals(1.20, outside.minimumPerKm, 0.001)
        assertEquals(1.40, peak.minimumPerKm, 0.001)
        assertEquals("Pico", peak.name)
    }

    @Test
    fun scheduleCanCrossMidnight() {
        val overnight = ScheduledKmThreshold(
            name = "Madrugada",
            enabled = true,
            startMinuteOfDay = 22 * 60,
            endMinuteOfDay = 2 * 60,
            minimumPerKm = 1.50,
            excellentPerKm = 2.10
        )

        assertTrue(overnight.matches(23 * 60))
        assertTrue(overnight.matches(60))
        assertEquals(false, overnight.matches(12 * 60))
    }

    @Test
    fun middleReferenceUsesMinimumAndExcellent() {
        assertEquals(1.50, RideCalculator.middleReference(1.20, 1.80), 0.001)
    }
    @Test
    fun diagnosticsKeepPartialValuesWhenRouteIsIncomplete() {
        val attempt = OfferParser.parseWithDiagnostics(
            listOf(
                "Moto",
                "R$8,40",
                "6min (2km)",
                "Aceitar"
            )
        )

        assertEquals(null, attempt.offer)
        assertEquals(8.40, attempt.fare ?: 0.0, 0.001)
        assertNotNull(attempt.pickupSegment)
        assertEquals(null, attempt.tripSegment)
        assertTrue(attempt.normalizedTexts.contains("R$8,40"))
    }

    @Test
    fun longTripCanIncludeEmptyReturnInRating() {
        val settingsWithReturn = settings.copy(
            emptyReturnEnabled = true,
            emptyReturnTripKmThreshold = 5.0,
            emptyReturnDistanceFactor = 1.0
        )
        val result = RideCalculator.calculate(
            offer = RideOffer(
                fare = 18.0,
                pickupDistanceKm = 1.0,
                tripDistanceKm = 9.0,
                pickupMinutes = 4,
                tripMinutes = 18
            ),
            settings = settingsWithReturn,
            minuteOfDay = 10 * 60
        )
        assertTrue(result.possibleEmptyReturn)
        assertEquals(9.0, result.emptyReturnDistanceKm, 0.001)
        assertEquals(19.0, result.analysisDistanceKm, 0.001)
        assertEquals(18.0 / 19.0, result.analysisPerKm, 0.001)
        assertEquals(OfferRating.BAD, result.rating)
    }

}
