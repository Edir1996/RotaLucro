package com.rotalucro.app.demand

import com.rotalucro.app.calculator.DemandAssessment
import com.rotalucro.app.calculator.DemandDistanceClass
import com.rotalucro.app.calculator.DemandLevel
import com.rotalucro.app.data.DemandZoneStore
import kotlin.math.*

object DemandEngine {
    data class GeoPoint(val lat: Double, val lon: Double, val city: String? = null)

    fun assess(
        destinationText: String?,
        destination: GeoPoint?,
        baseCity: String,
        zones: List<DemandZoneStore.Zone>,
        learned: LearnedSignal? = null
    ): DemandAssessment {
        val enabled = zones.filter { it.enabled }
        val demandZones = enabled.filter { it.demandLevel == DemandLevel.MEDIUM || it.demandLevel == DemandLevel.HIGH || it.demandLevel == DemandLevel.VERY_HIGH }
        val keywordZone = destinationText?.let { text ->
            enabled.firstOrNull { zone -> zone.keywordList().any { text.contains(it, ignoreCase = true) } }
        }
        val keywordDemandZone = keywordZone?.takeIf { it in demandZones }

        var nearestName: String? = keywordDemandZone?.name
        var nearestDistance: Double? = if (keywordDemandZone != null) 0.0 else null
        var level = keywordZone?.demandLevel ?: DemandLevel.UNKNOWN
        var source = if (keywordZone != null) "Palavra-chave configurada" else "Sem dados"

        if (destination != null) {
            var lowContaining: DemandZoneStore.Zone? = null
            enabled.filter { it.demandLevel == DemandLevel.LOW }.forEach { zone ->
                val zLat = zone.latitude ?: return@forEach
                val zLon = zone.longitude ?: return@forEach
                if (haversineKm(destination.lat, destination.lon, zLat, zLon) <= zone.radiusKm) lowContaining = zone
            }
            demandZones.forEach { zone ->
                val zLat = zone.latitude ?: return@forEach
                val zLon = zone.longitude ?: return@forEach
                val centerDistance = haversineKm(destination.lat, destination.lon, zLat, zLon)
                val edgeDistance = (centerDistance - zone.radiusKm).coerceAtLeast(0.0)
                if (nearestDistance == null || edgeDistance < nearestDistance!!) {
                    nearestDistance = edgeDistance
                    nearestName = zone.name
                    if (edgeDistance <= 0.01) level = zone.demandLevel
                    source = "Região configurada"
                }
            }
            if (lowContaining != null && (nearestDistance == null || nearestDistance!! > 0.01)) {
                level = DemandLevel.LOW
                source = "Região marcada como baixa demanda"
            }
        }

        if (destination != null && learned != null && learned.confidence >= 28) {
            val learnedDistance = learned.distanceKm.coerceAtLeast(0.0)
            if (nearestDistance == null || learnedDistance < nearestDistance!!) {
                nearestDistance = learnedDistance
                nearestName = "Área aprendida"
                if (learnedDistance <= 0.01) {
                    level = when {
                        learned.confidence >= 75 -> DemandLevel.VERY_HIGH
                        learned.confidence >= 50 -> DemandLevel.HIGH
                        else -> DemandLevel.MEDIUM
                    }
                }
                source = "Aprendizado do seu histórico"
            }
        }

        val outside = destination?.city?.let { city ->
            val base = baseCity.substringBefore(',').trim()
            base.isNotBlank() && city.isNotBlank() && !normalize(city).contains(normalize(base)) && !normalize(base).contains(normalize(city))
        } ?: false

        return DemandAssessment(
            destinationText = destinationText,
            destinationLatitude = destination?.lat,
            destinationLongitude = destination?.lon,
            destinationCity = destination?.city,
            nearestDemandZoneName = nearestName,
            distanceToDemandKm = nearestDistance,
            distanceClass = classifyDistance(nearestDistance),
            demandLevel = level,
            learnedConfidence = learned?.confidence ?: 0,
            outsideBaseCity = outside,
            source = source
        )
    }

    data class LearnedSignal(val confidence: Int, val distanceKm: Double)

    fun classifyDistance(km: Double?): DemandDistanceClass = when {
        km == null -> DemandDistanceClass.UNKNOWN
        km <= 3.0 -> DemandDistanceClass.EXCELLENT
        km <= 5.0 -> DemandDistanceClass.GOOD
        km <= 7.0 -> DemandDistanceClass.ATTENTION
        km <= 9.0 -> DemandDistanceClass.FAR
        km <= 12.0 -> DemandDistanceClass.BAD
        else -> DemandDistanceClass.VERY_BAD
    }

    fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0088
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun normalize(s: String): String = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
}
