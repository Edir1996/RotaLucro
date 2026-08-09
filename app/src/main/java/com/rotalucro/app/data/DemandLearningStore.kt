package com.rotalucro.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import com.rotalucro.app.cloud.CloudApiClient
import kotlin.math.round

/** Learns only positive demand signals: a new offer received soon after the expected drop-off. */
object DemandLearningStore {
    private const val FILE = "demand_learning"
    private const val KEY_HOTSPOTS = "hotspots"
    private const val KEY_PENDING = "pending"
    private const val MAX_HOTSPOTS = 120

    data class Hotspot(
        val lat: Double,
        val lon: Double,
        val successes: Int,
        val lastSeenAt: Long
    ) {
        val confidence: Int get() = (successes * 14).coerceAtMost(95)
    }

    private data class Pending(
        val lat: Double,
        val lon: Double,
        val expectedDropoffAt: Long,
        val createdAt: Long
    )

    fun startPending(context: Context, lat: Double?, lon: Double?, acceptedAt: Long, totalMin: Int) {
        if (lat == null || lon == null) return
        val expected = acceptedAt + totalMin.coerceAtLeast(1) * 60_000L
        val o = JSONObject().apply {
            put("lat", lat); put("lon", lon); put("expected", expected); put("created", acceptedAt)
        }
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY_PENDING, o.toString()).apply()
    }

    fun registerNewOffer(context: Context, now: Long = System.currentTimeMillis()) {
        val p = loadPending(context) ?: return
        val earliest = p.expectedDropoffAt - 3 * 60_000L
        val latest = p.expectedDropoffAt + 30 * 60_000L
        if (now in earliest..latest) {
            addSuccess(context, p.lat, p.lon, now)
            clearPending(context)
        } else if (now > latest) {
            clearPending(context)
        }
    }

    fun nearest(context: Context, lat: Double, lon: Double, maxKm: Double = 3.0): Hotspot? {
        return load(context)
            .filter { it.confidence >= 28 }
            .map { it to haversineKm(lat, lon, it.lat, it.lon) }
            .filter { it.second <= maxKm }
            .minByOrNull { it.second }
            ?.first
    }

    fun load(context: Context): List<Hotspot> {
        val raw = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_HOTSPOTS, "[]") ?: "[]"
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(Hotspot(o.optDouble("lat"), o.optDouble("lon"), o.optInt("successes", 1), o.optLong("lastSeenAt")))
                }
            }
        }.getOrDefault(emptyList())
    }

    fun clear(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().remove(KEY_HOTSPOTS).remove(KEY_PENDING).apply()
        CloudApiClient.syncAsync(context)
    }

    private fun addSuccess(context: Context, lat: Double, lon: Double, now: Long) {
        val keyLat = round(lat * 100.0) / 100.0
        val keyLon = round(lon * 100.0) / 100.0
        val items = load(context).toMutableList()
        val idx = items.indexOfFirst { haversineKm(keyLat, keyLon, it.lat, it.lon) <= 1.6 }
        if (idx >= 0) {
            val old = items[idx]
            items[idx] = old.copy(
                lat = (old.lat * old.successes + lat) / (old.successes + 1),
                lon = (old.lon * old.successes + lon) / (old.successes + 1),
                successes = old.successes + 1,
                lastSeenAt = now
            )
        } else {
            items.add(Hotspot(keyLat, keyLon, 1, now))
        }
        persist(context, items.sortedByDescending { it.lastSeenAt }.take(MAX_HOTSPOTS))
        CloudApiClient.syncAsync(context)
    }

    private fun persist(context: Context, items: List<Hotspot>) {
        val arr = JSONArray()
        items.forEach { h -> arr.put(JSONObject().apply {
            put("lat", h.lat); put("lon", h.lon); put("successes", h.successes); put("lastSeenAt", h.lastSeenAt)
        }) }
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY_HOTSPOTS, arr.toString()).apply()
    }

    private fun loadPending(context: Context): Pending? {
        val raw = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_PENDING, null) ?: return null
        return runCatching {
            val o = JSONObject(raw)
            Pending(o.getDouble("lat"), o.getDouble("lon"), o.getLong("expected"), o.getLong("created"))
        }.getOrNull()
    }

    private fun clearPending(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().remove(KEY_PENDING).apply()
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0088
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        return 2 * r * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    }
}
