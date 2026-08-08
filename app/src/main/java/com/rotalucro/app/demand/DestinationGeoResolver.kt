package com.rotalucro.app.demand

import android.content.Context
import android.location.Geocoder
import com.rotalucro.app.calculator.DemandAssessment
import com.rotalucro.app.calculator.DriverSettings
import com.rotalucro.app.calculator.RideOffer
import com.rotalucro.app.data.DemandLearningStore
import com.rotalucro.app.data.DemandZoneStore
import java.util.Locale
import java.util.concurrent.Executors

/** Resolves destination and configured demand zones using Android's geocoder on a background thread. */
object DestinationGeoResolver {
    private val executor = Executors.newSingleThreadExecutor()

    fun assessAsync(context: Context, offer: RideOffer, settings: DriverSettings, callback: (DemandAssessment) -> Unit) {
        executor.execute {
            val zones = DemandZoneStore.load(context).toMutableList()
            if (Geocoder.isPresent()) {
                zones.forEachIndexed { index, zone ->
                    if (zone.enabled && (zone.latitude == null || zone.longitude == null) && zone.referenceAddress.isNotBlank()) {
                        geocode(context, zone.referenceAddress, settings.baseCity, preferBase = true)?.let { point ->
                            zones[index] = zone.copy(latitude = point.lat, longitude = point.lon)
                            DemandZoneStore.updateResolved(context, zone.id, point.lat, point.lon)
                        }
                    }
                }
            }
            val destination = if (Geocoder.isPresent()) geocode(context, offer.destinationLocationText.orEmpty(), settings.baseCity, preferBase = offer.tripDistanceKm < 10.0) else null
            val learnedSignal = destination?.let { p ->
                DemandLearningStore.nearest(context, p.lat, p.lon, 50.0)?.let { hotspot ->
                    val d = DemandEngine.haversineKm(p.lat, p.lon, hotspot.lat, hotspot.lon)
                    DemandEngine.LearnedSignal(hotspot.confidence, (d - 2.2).coerceAtLeast(0.0))
                }
            }
            callback(
                DemandEngine.assess(
                    destinationText = offer.destinationLocationText,
                    destination = destination,
                    baseCity = settings.baseCity,
                    zones = zones,
                    learned = learnedSignal
                )
            )
        }
    }

    private fun geocode(context: Context, raw: String, baseCity: String, preferBase: Boolean): DemandEngine.GeoPoint? {
        val text = raw.trim()
        if (text.length < 4) return null
        return runCatching {
            val geocoder = Geocoder(context, Locale("pt", "BR"))
            val firstQuery = if (preferBase && baseCity.isNotBlank()) "$text, $baseCity" else text
            val secondQuery = if (preferBase) text else if (baseCity.isNotBlank()) "$text, $baseCity" else text
            @Suppress("DEPRECATION")
            var address = geocoder.getFromLocationName(firstQuery, 1)?.firstOrNull()
            if (address == null && secondQuery != firstQuery) {
                @Suppress("DEPRECATION")
                address = geocoder.getFromLocationName(secondQuery, 1)?.firstOrNull()
            }
            address?.let { DemandEngine.GeoPoint(it.latitude, it.longitude, it.locality ?: it.subAdminArea ?: it.adminArea) }
        }.getOrNull()
    }
}
