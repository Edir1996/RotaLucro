package com.rotalucro.app.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RideCalculatorTest {
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
            settings = DriverSettings(
                minimumPerKm = 2.0,
                minimumPerHour = 35.0,
                fuelPricePerLiter = 6.0,
                vehicleKmPerLiter = 10.0,
                maintenancePerKm = 0.30
            )
        )

        assertEquals(10.0, result.totalDistanceKm, 0.001)
        assertEquals(25, result.totalMinutes)
        assertEquals(3.0, result.grossPerKm, 0.001)
        assertEquals(6.0, result.fuelCost, 0.001)
        assertEquals(3.0, result.maintenanceCost, 0.001)
        assertEquals(21.0, result.estimatedProfit, 0.001)
        assertEquals(OfferRating.GOOD, result.rating)
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
