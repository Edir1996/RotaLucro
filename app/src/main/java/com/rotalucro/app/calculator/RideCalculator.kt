package com.rotalucro.app.calculator

import java.time.LocalTime
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

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

        // Em viagens longas o motorista pode voltar sem passageiro. Como o app não conhece
        // com segurança o município de destino, o usuário define a distância a partir da qual
        // esse risco deve entrar na conta e quanto da viagem considerar como retorno vazio.
        val possibleEmptyReturn = settings.emptyReturnEnabled &&
            offer.tripDistanceKm >= settings.emptyReturnTripKmThreshold.coerceAtLeast(0.1)
        val returnFactor = settings.emptyReturnDistanceFactor.coerceIn(0.0, 2.0)
        val emptyReturnKm = if (possibleEmptyReturn) offer.tripDistanceKm * returnFactor else 0.0
        val emptyReturnMinutes = if (possibleEmptyReturn && offer.tripDistanceKm > 0.0) {
            (offer.tripMinutes * returnFactor).roundToInt().coerceAtLeast(0)
        } else 0

        val analysisDistance = max(totalDistance + emptyReturnKm, 0.01)
        val analysisMinutes = max(totalMinutes + emptyReturnMinutes, 1)
        val analysisPerKm = offer.fare / analysisDistance
        val analysisPerHour = offer.fare / (analysisMinutes / 60.0)

        val fuelCost = if (settings.vehicleKmPerLiter > 0) {
            (analysisDistance / settings.vehicleKmPerLiter) * settings.fuelPricePerLiter
        } else 0.0
        val maintenanceCost = analysisDistance * settings.maintenancePerKm
        val estimatedProfit = offer.fare - fuelCost - maintenanceCost
        val profitPerKm = estimatedProfit / analysisDistance

        val activeThreshold = settings.activeKmThreshold(minuteOfDay)
        val rating = rateMetric(analysisPerKm, activeThreshold.minimumPerKm, activeThreshold.excellentPerKm)

        return RideResult(
            offer = offer,
            totalDistanceKm = totalDistance,
            totalMinutes = totalMinutes,
            grossPerKm = grossPerKm,
            grossPerHour = grossPerHour,
            analysisDistanceKm = analysisDistance,
            analysisMinutes = analysisMinutes,
            analysisPerKm = analysisPerKm,
            analysisPerHour = analysisPerHour,
            possibleEmptyReturn = possibleEmptyReturn,
            emptyReturnDistanceKm = emptyReturnKm,
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

    fun middleReference(minimum: Double, excellent: Double): Double = (minimum + excellent) / 2.0

    fun currentMinuteOfDay(): Int {
        val now = LocalTime.now()
        return now.hour * 60 + now.minute
    }
}
