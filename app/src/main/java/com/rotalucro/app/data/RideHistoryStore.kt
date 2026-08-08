package com.rotalucro.app.data

import android.content.Context
import com.rotalucro.app.calculator.OfferRating
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
        val emptyReturnKm: Double
    )

    fun saveLatestAsAccepted(context: Context): Entry? {
        val last = LastRideStore.load(context) ?: return null
        // Evita salvar por engano uma oferta antiga depois de horas.
        if (System.currentTimeMillis() - last.timestamp > 10 * 60_000L) return null
        val entry = Entry(
            id = System.currentTimeMillis(),
            acceptedAt = System.currentTimeMillis(),
            fare = last.fare,
            totalKm = last.totalKm,
            totalMin = last.totalMin,
            analysisPerKm = last.analysisPerKm,
            analysisPerHour = last.analysisPerHour,
            estimatedProfit = last.profit,
            rating = last.rating,
            possibleEmptyReturn = last.possibleEmptyReturn,
            emptyReturnKm = last.emptyReturnKm
        )
        val items = load(context).toMutableList()
        val duplicate = items.firstOrNull {
            kotlin.math.abs(it.fare - entry.fare) < 0.01 &&
                kotlin.math.abs(it.totalKm - entry.totalKm) < 0.01 &&
                entry.acceptedAt - it.acceptedAt < 90_000L
        }
        if (duplicate != null) return duplicate
        items.add(0, entry)
        persist(context, items.take(MAX_ITEMS))
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
                            emptyReturnKm = o.optDouble("emptyReturnKm")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun delete(context: Context, id: Long) = persist(context, load(context).filterNot { it.id == id })
    fun clear(context: Context) = persist(context, emptyList())

    private fun persist(context: Context, entries: List<Entry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(JSONObject().apply {
                put("id", e.id)
                put("acceptedAt", e.acceptedAt)
                put("fare", e.fare)
                put("totalKm", e.totalKm)
                put("totalMin", e.totalMin)
                put("analysisPerKm", e.analysisPerKm)
                put("analysisPerHour", e.analysisPerHour)
                put("estimatedProfit", e.estimatedProfit)
                put("rating", e.rating.name)
                put("possibleEmptyReturn", e.possibleEmptyReturn)
                put("emptyReturnKm", e.emptyReturnKm)
            })
        }
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }
}
