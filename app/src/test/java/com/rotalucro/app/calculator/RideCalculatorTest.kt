package com.rotalucro.app.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RideCalculatorTest {
    private val settings = DriverSettings(
        defaultMinimumPerKm = 1.20,
        defaultExcellentPerKm = 1.80,
        scheduledThresholds = listOf(
            ScheduledKmThreshold(
                name = "Dinâmica 1",
                enabled = true,
                startMinuteOfDay = 18 * 60,
                endMinuteOfDay = 22 * 60,
                minimumPerKm = 1.40,
                excellentPerKm = 2.00
            )
        ),
        fuelPricePerLiter = 6.0,
        vehicleKmPerLiter = 10.0,
        maintenancePerKm = 0.30
    )

    @Test
    fun calculatesDistanceTimeAndCosts() {
        val result = RideCalculator.calculate(
            offer = RideOffer(
                fare = 30.0,
                pickupDistanceKm = 2.0,
                tripDistanceKm = 8.0,
                pickupMinutes = 5,
                tripMinutes = 20
            ),
            settings = settings,
            minuteOfDay = 10 * 60
        )

        assertEquals(10.0, result.totalDistanceKm, 0.001)
        assertEquals(25, result.totalMinutes)
        assertEquals(3.0, result.grossPerKm, 0.001)
        assertEquals(72.0, result.grossPerHour, 0.001)
        assertEquals(6.0, result.fuelCost, 0.001)
        assertEquals(3.0, result.maintenanceCost, 0.001)
        assertEquals(21.0, result.estimatedProfit, 0.001)
        assertEquals("Fora da dinâmica", result.activeThreshold.name)
        assertEquals(OfferRating.GOOD, result.perKmRating)
        assertEquals(OfferRating.GOOD, result.rating)
    }

    @Test
    fun belowDefaultMinimumMakesBoxRedOutsideDynamicTime() {
        val result = RideCalculator.calculate(
            offer = RideOffer(
                fare = 11.0,
                pickupDistanceKm = 2.0,
                tripDistanceKm = 8.0,
                pickupMinutes = 3,
                tripMinutes = 12
            ),
            settings = settings,
            minuteOfDay = 10 * 60
        )

        assertEquals(1.10, result.grossPerKm, 0.001)
        assertEquals("Fora da dinâmica", result.activeThreshold.name)
        assertEquals(OfferRating.BAD, result.rating)
    }

    @Test
    fun dynamicTimeUsesHigherMinimum() {
        val result = RideCalculator.calculate(
            offer = RideOffer(
                fare = 13.0,
                pickupDistanceKm = 2.0,
                tripDistanceKm = 8.0,
                pickupMinutes = 3,
                tripMinutes = 12
            ),
            settings = settings,
            minuteOfDay = 19 * 60
        )

        assertEquals(1.30, result.grossPerKm, 0.001)
        assertEquals("Dinâmica 1", result.activeThreshold.name)
        assertEquals(1.40, result.activeThreshold.minimumPerKm, 0.001)
        assertEquals(OfferRating.BAD, result.rating)
    }

    @Test
    fun valueBetweenDynamicMinimumAndExcellentIsYellow() {
        val result = RideCalculator.calculate(
            offer = RideOffer(
                fare = 17.0,
                pickupDistanceKm = 2.0,
                tripDistanceKm = 8.0,
                pickupMinutes = 3,
                tripMinutes = 12
            ),
            settings = settings,
            minuteOfDay = 19 * 60
        )

        assertEquals(1.70, result.grossPerKm, 0.001)
        assertEquals(OfferRating.ATTENTION, result.rating)
    }

    @Test
    fun valueAtDynamicExcellentThresholdIsGreen() {
        val result = RideCalculator.calculate(
            offer = RideOffer(
                fare = 20.0,
                pickupDistanceKm = 2.0,
                tripDistanceKm = 8.0,
                pickupMinutes = 3,
                tripMinutes = 12
            ),
            settings = settings,
            minuteOfDay = 19 * 60
        )

        assertEquals(2.00, result.grossPerKm, 0.001)
        assertEquals(OfferRating.GOOD, result.rating)
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
    fun calculatesMiddleReference() {
        assertEquals(1.50, RideCalculator.middleReference(1.20, 1.80), 0.001)
        assertEquals(1.70, RideCalculator.middleReference(1.40, 2.00), 0.001)
    }

    @Test
    fun parserReadsTypicalBrazilianValues() {
        val offer = OfferParser.parse(
            listOf(
                "R$ 27,50",
                "2,4 km • 7 min",
                "9,8 km • 24 min"
            )
        )

        requireNotNull(offer)
        assertEquals(27.50, offer.fare, 0.001)
        assertEquals(2.4, offer.pickupDistanceKm, 0.001)
        assertEquals(9.8, offer.tripDistanceKm, 0.001)
        assertEquals(7, offer.pickupMinutes)
        assertEquals(24, offer.tripMinutes)
        assertTrue(offer.sourceText.isNotEmpty())
    }
}
