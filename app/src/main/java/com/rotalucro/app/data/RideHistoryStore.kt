package com.rotalucro.app.data

import android.content.Context
import com.rotalucro.app.calculator.OfferRating
import com.rotalucro.app.calculator.RideRecommendation
import com.rotalucro.app.cloud.CloudApiClient
import org.json.JSONArray
import org.json.JSONObject

object RideHistoryStore {
    private const val FILE = "ride_history"
    private const val KEY = "accepted_rides"
    private const val MAX_ITEMS = 250

    data class Entry(
        val id: Long,
        val acceptedAt: Long,
        val fare: Double,
        val totalKm: Double,
        val totalMin: Int,
        val analysisPerKm: Double,
        val analysisPerHour: Double,
        val estimatedProfit: Double,
        val rating: OfferRating,
        val possibleEmptyReturn: Boolean,
        val emptyReturnKm: Double,
        val destinationText: String? = null,
        val destinationLat: Double? = null,
        val destinationLon: Double? = null,
        val destinationCity: String? = null,
        val distanceToDemandKm: Double? = null,
        val demandZoneName: String? = null,
        val smartScore: Int = 0,
        val recommendation: RideRecommendation = RideRecommendation.CAUTION
    )

    fun saveLatestAsAccepted(context: Context): Entry? {
        val last = LastRideStore.load(context) ?: return null
        if (System.currentTimeMillis() - last.timestamp > 10 * 60_000L) return null
        val now = System.currentTimeMillis()
        val entry = Entry(
            id = now,
            acceptedAt = now,
            fare = last.fare,
            totalKm = last.totalKm,
            totalMin = last.totalMin,
            analysisPerKm = last.analysisPerKm,
            analysisPerHour = last.analysisPerHour,
            estimatedProfit = last.profit,
            rating = last.rating,
            possibleEmptyReturn = last.possibleEmptyReturn,
            emptyReturnKm = last.emptyReturnKm,
            destinationText = last.destinationText,
            destinationLat = last.destinationLat,
            destinationLon = last.destinationLon,
            destinationCity = last.destinationCity,
            distanceToDemandKm = last.distanceToDemandKm,
            demandZoneName = last.demandZoneName,
            smartScore = last.smartScore,
            recommendation = last.recommendation
        )
        val items = load(context).toMutableList()
        val duplicate = items.firstOrNull {
            kotlin.math.abs(it.fare - entry.fare) < 0.01 &&
                kotlin.math.abs(it.totalKm - entry.totalKm) < 0.01 &&
                entry.acceptedAt - it.acceptedAt < 90_000L
        }
        if (duplicate != null) { CloudApiClient.syncAsync(context); return duplicate }
        items.add(0, entry)
        persist(context, items.take(MAX_ITEMS))
        CloudApiClient.syncAsync(context)
        if (SettingsStore.load(context).demandLearningEnabled) {
            DemandLearningStore.startPending(context, entry.destinationLat, entry.destinationLon, entry.acceptedAt, entry.totalMin)
        }
        return entry
    }

    fun load(context: Context): List<Entry> {
        val raw = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        Entry(
                            id = o.optLong("id"),
                            acceptedAt = o.optLong("acceptedAt"),
                            fare = o.optDouble("fare"),
                            totalKm = o.optDouble("totalKm"),
                            totalMin = o.optInt("totalMin"),
                            analysisPerKm = o.optDouble("analysisPerKm"),
                            analysisPerHour = o.optDouble("analysisPerHour"),
                            estimatedProfit = o.optDouble("estimatedProfit"),
                            rating = runCatching { OfferRating.valueOf(o.optString("rating")) }.getOrDefault(OfferRating.ATTENTION),
                            possibleEmptyReturn = o.optBoolean("possibleEmptyReturn"),
                            emptyReturnKm = o.optDouble("emptyReturnKm"),
                            destinationText = o.optString("destinationText").takeIf { it.isNotBlank() },
                            destinationLat = o.takeIf { it.has("destinationLat") && !it.isNull("destinationLat") }?.optDouble("destinationLat"),
                            destinationLon = o.takeIf { it.has("destinationLon") && !it.isNull("destinationLon") }?.optDouble("destinationLon"),
                            destinationCity = o.optString("destinationCity").takeIf { it.isNotBlank() },
                            distanceToDemandKm = o.takeIf { it.has("distanceToDemandKm") && !it.isNull("distanceToDemandKm") }?.optDouble("distanceToDemandKm"),
                            demandZoneName = o.optString("demandZoneName").takeIf { it.isNotBlank() },
                            smartScore = o.optInt("smartScore", 0),
                            recommendation = runCatching { RideRecommendation.valueOf(o.optString("recommendation", RideRecommendation.CAUTION.name)) }.getOrDefault(RideRecommendation.CAUTION)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun delete(context: Context, id: Long) {
        persist(context, load(context).filterNot { it.id == id })
        CloudApiClient.syncAsync(context)
    }
    fun clear(context: Context) {
        persist(context, emptyList())
        CloudApiClient.syncAsync(context)
    }

    private fun persist(context: Context, entries: List<Entry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(JSONObject().apply {
                put("id", e.id); put("acceptedAt", e.acceptedAt); put("fare", e.fare); put("totalKm", e.totalKm); put("totalMin", e.totalMin)
                put("analysisPerKm", e.analysisPerKm); put("analysisPerHour", e.analysisPerHour); put("estimatedProfit", e.estimatedProfit)
                put("rating", e.rating.name); put("possibleEmptyReturn", e.possibleEmptyReturn); put("emptyReturnKm", e.emptyReturnKm)
                put("destinationText", e.destinationText ?: JSONObject.NULL); put("destinationCity", e.destinationCity ?: JSONObject.NULL)
                put("demandZoneName", e.demandZoneName ?: JSONObject.NULL); put("smartScore", e.smartScore); put("recommendation", e.recommendation.name)
                if (e.destinationLat != null) put("destinationLat", e.destinationLat) else put("destinationLat", JSONObject.NULL)
                if (e.destinationLon != null) put("destinationLon", e.destinationLon) else put("destinationLon", JSONObject.NULL)
                if (e.distanceToDemandKm != null) put("distanceToDemandKm", e.distanceToDemandKm) else put("distanceToDemandKm", JSONObject.NULL)
            })
        }
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }
}
