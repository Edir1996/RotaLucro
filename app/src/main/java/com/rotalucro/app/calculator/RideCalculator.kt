package com.rotalucro.app.calculator

import java.time.LocalTime
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object RideCalculator {
    fun calculate(
        offer: RideOffer,
        settings: DriverSettings,
        minuteOfDay: Int = currentMinuteOfDay(),
        demand: DemandAssessment? = null
    ): RideResult {
        val totalDistance = max(offer.pickupDistanceKm + offer.tripDistanceKm, 0.01)
        val totalMinutes = max(offer.pickupMinutes + offer.tripMinutes, 1)
        val grossPerKm = offer.fare / totalDistance
        val grossPerHour = offer.fare / (totalMinutes / 60.0)
        val activeThreshold = settings.activeKmThreshold(minuteOfDay)

        val smartDemandUsable = settings.smartDemandEnabled && demand?.distanceToDemandKm != null
        val demandDistance = demand?.distanceToDemandKm ?: 0.0
        val strongDemand = demand?.demandLevel == DemandLevel.HIGH || demand?.demandLevel == DemandLevel.VERY_HIGH

        // Quanto mais longe o destino termina de uma zona de demanda, maior o mínimo exigido.
        // Destino com forte demanda ou área aprendida recebe alívio parcial do prêmio.
        val rawPremiumMinimum = when {
            !smartDemandUsable -> activeThreshold.minimumPerKm
            demandDistance < 5.0 -> activeThreshold.minimumPerKm
            demandDistance < 7.0 -> max(activeThreshold.minimumPerKm, settings.premiumMin5To7Km)
            demandDistance < 9.0 -> max(activeThreshold.minimumPerKm, settings.premiumMin7To9Km)
            else -> max(activeThreshold.minimumPerKm, settings.premiumMin9PlusKm)
        }
        val demandRelief = when {
            !smartDemandUsable -> 0.0
            strongDemand -> (rawPremiumMinimum - activeThreshold.minimumPerKm) * 0.65
            (demand?.learnedConfidence ?: 0) >= 50 -> (rawPremiumMinimum - activeThreshold.minimumPerKm) * 0.45
            else -> 0.0
        }
        val effectiveMinimum = (rawPremiumMinimum - demandRelief).coerceAtLeast(activeThreshold.minimumPerKm)
        val spread = (activeThreshold.excellentPerKm - activeThreshold.minimumPerKm).coerceAtLeast(0.40)
        val effectiveExcellent = effectiveMinimum + spread

        val smartReturnKm = if (smartDemandUsable && demandDistance > 3.0) {
            val cityFactor = if (demand?.outsideBaseCity == true) 1.15 else 1.0
            demandDistance * settings.demandReturnFactor.coerceIn(0.0, 2.0) * cityFactor
        } else 0.0

        // Fallback legado se não conseguimos resolver o destino geograficamente.
        val legacyReturn = !smartDemandUsable && settings.emptyReturnEnabled &&
            offer.tripDistanceKm >= settings.emptyReturnTripKmThreshold.coerceAtLeast(0.1)
        val legacyReturnKm = if (legacyReturn) offer.tripDistanceKm * settings.emptyReturnDistanceFactor.coerceIn(0.0, 2.0) else 0.0
        val emptyReturnKm = if (smartReturnKm > 0.0) smartReturnKm else legacyReturnKm
        val possibleEmptyReturn = emptyReturnKm > 0.05

        val tripSpeedKmPerMin = if (offer.tripMinutes > 0) offer.tripDistanceKm / offer.tripMinutes else 0.45
        val emptyReturnMinutes = if (possibleEmptyReturn) {
            (emptyReturnKm / tripSpeedKmPerMin.coerceAtLeast(0.12)).roundToInt().coerceAtLeast(1)
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

        val rating = rateMetric(analysisPerKm, effectiveMinimum, effectiveExcellent)
        val score = smartScore(
            analysisPerKm = analysisPerKm,
            minimum = effectiveMinimum,
            excellent = effectiveExcellent,
            perHour = analysisPerHour,
            demand = demand,
            pickupKm = offer.pickupDistanceKm,
            tripKm = offer.tripDistanceKm
        )
        val recommendation = when {
            analysisPerKm < effectiveMinimum || score < 45 -> RideRecommendation.REJECT
            analysisPerKm >= effectiveExcellent && score >= 70 -> RideRecommendation.ACCEPT
            analysisPerKm >= effectiveMinimum && score >= 62 -> RideRecommendation.ACCEPT
            else -> RideRecommendation.CAUTION
        }

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
            effectiveMinimumPerKm = effectiveMinimum,
            effectiveExcellentPerKm = effectiveExcellent,
            demandAssessment = demand,
            smartScore = score,
            recommendation = recommendation,
            rating = rating
        )
    }

    private fun smartScore(
        analysisPerKm: Double,
        minimum: Double,
        excellent: Double,
        perHour: Double,
        demand: DemandAssessment?,
        pickupKm: Double,
        tripKm: Double
    ): Int {
        val priceSpan = (excellent - minimum).coerceAtLeast(0.25)
        val priceScore = (((analysisPerKm - minimum * 0.60) / (priceSpan + minimum * 0.40)) * 55.0).coerceIn(0.0, 55.0)
        val hourScore = ((perHour - 18.0) / 32.0 * 15.0).coerceIn(0.0, 15.0)
        val demandScore = when (demand?.demandLevel ?: DemandLevel.UNKNOWN) {
            DemandLevel.VERY_HIGH -> 20.0
            DemandLevel.HIGH -> 17.0
            DemandLevel.MEDIUM -> 12.0
            DemandLevel.LOW -> 5.0
            DemandLevel.UNKNOWN -> 8.0
        }
        val distancePenalty = when (demand?.distanceClass ?: DemandDistanceClass.UNKNOWN) {
            DemandDistanceClass.EXCELLENT -> 0.0
            DemandDistanceClass.GOOD -> 0.0
            DemandDistanceClass.ATTENTION -> 3.0
            DemandDistanceClass.FAR -> 7.0
            DemandDistanceClass.BAD -> 12.0
            DemandDistanceClass.VERY_BAD -> 18.0
            DemandDistanceClass.UNKNOWN -> 2.0
        }
        val pickupPenalty = when {
            pickupKm <= 2.0 -> 0.0
            pickupKm <= 4.0 -> 2.0
            pickupKm <= tripKm -> 5.0
            else -> 9.0
        }
        val outsidePenalty = if (demand?.outsideBaseCity == true && demand.demandLevel < DemandLevel.HIGH) 6.0 else 0.0
        return (priceScore + hourScore + demandScore + 10.0 - distancePenalty - pickupPenalty - outsidePenalty)
            .roundToInt().coerceIn(0, 100)
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
