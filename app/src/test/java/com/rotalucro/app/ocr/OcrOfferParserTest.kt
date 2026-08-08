package com.rotalucro.app.ocr

import com.rotalucro.app.calculator.DriverSettings
import com.rotalucro.app.calculator.OfferRating
import com.rotalucro.app.calculator.RideCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class OcrOfferParserTest {
    @Test
    fun `recognizes real 99 example and ignores dynamic base fare`() {
        val lines = listOf(
            OcrLine("Moto", 50, 20),
            OcrLine("R$8,40", 90, 65),
            OcrLine("1,6x", 100, 30),
            OcrLine("0% Taxa de serviço", 220, 22),
            OcrLine("R$1,27 Tarifa base dinâmica incl.", 260, 22),
            OcrLine("6min (2km)", 430, 28),
            OcrLine("5min (2,3km)", 520, 28),
            OcrLine("Aceitar", 650, 24)
        )
        val parsed = OcrOfferParser.parse(lines)
        val offer = parsed.offer
        assertNotNull(offer)
        assertEquals(8.40, offer!!.fare, 0.001)
        assertEquals(2.0, offer.pickupDistanceKm, 0.001)
        assertEquals(2.3, offer.tripDistanceKm, 0.001)
        assertEquals(6, offer.pickupMinutes)
        assertEquals(5, offer.tripMinutes)
        assertEquals(1.6, offer.surgeMultiplier!!, 0.001)
        assertEquals(1.27, offer.dynamicBaseFare!!, 0.001)

        val result = RideCalculator.calculate(offer, DriverSettings(), minuteOfDay = 10 * 60)
        assertEquals(1.953, result.grossPerKm, 0.01)
        assertEquals(45.818, result.grossPerHour, 0.02)
        assertEquals(OfferRating.GOOD, result.rating)
    }

    @Test
    fun `recognizes second real example`() {
        val lines = listOf(
            OcrLine("R$7,50", 100, 60),
            OcrLine("0% Taxa de serviço", 220, 20),
            OcrLine("7min (1,9km)", 420, 28),
            OcrLine("14min (6km)", 510, 28)
        )
        val offer = OcrOfferParser.parse(lines).offer!!
        val result = RideCalculator.calculate(offer, DriverSettings(), minuteOfDay = 10 * 60)
        assertEquals(7.9, result.totalDistanceKm, 0.001)
        assertEquals(21, result.totalMinutes)
        assertEquals(0.949, result.grossPerKm, 0.01)
        assertEquals(OfferRating.BAD, result.rating)
    }

    @Test
    fun `handles route text when OCR joins lines`() {
        val parsed = OcrOfferParser.parse(listOf(
            OcrLine("R$ 12,00", 100, 58),
            OcrLine("6min", 350, 25),
            OcrLine("(2km)", 380, 25),
            OcrLine("8min (3,5km)", 470, 25)
        ))
        assertNotNull(parsed.offer)
        assertEquals(5.5, parsed.offer!!.pickupDistanceKm + parsed.offer!!.tripDistanceKm, 0.001)
    }
}
