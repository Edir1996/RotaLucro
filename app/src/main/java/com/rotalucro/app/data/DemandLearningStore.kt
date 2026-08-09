package com.rotalucro.app.data

import android.content.Context
import com.rotalucro.app.cloud.CloudApiClient
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.round

/**
 * Demand learning model.
 *
 * Accepted destinations are recorded immediately as observations so the user can see that
 * the app is learning. They only start influencing the smart score after a future offer is
 * observed around the estimated drop-off time, which confirms that the area produced demand.
 */
object DemandLearningStore {
    private const val FILE = "demand_learning"
    private const val KEY_HOTSPOTS = "hotspots"
    private const val KEY_PENDING = "pending"
    private const val MAX_HOTSPOTS = 120
    private const val MAX_PENDING = 20
    private const val KEY_HISTORY_IMPORTED_093 = "history_imported_093"

    data class Hotspot(
        val lat: Double,
        val lon: Double,
        val visits: Int,
        val successes: Int,
        val lastSeenAt: Long,
        val label: String? = null
    ) {
        /**
         * Observations alone stay low-confidence. A confirmed follow-up offer is what makes an
         * area eligible to affect route analysis.
         */
        val confidence: Int
            get() {
                val observationScore = visits.coerceAtMost(5) * 4
                val confirmationScore = successes.coerceAtMost(4) * 24
                return (observationScore + confirmationScore).coerceAtMost(95)
            }

        val confirmed: Boolean get() = successes > 0
    }

    private data class Pending(
        val lat: Double,
        val lon: Double,
        val expectedDropoffAt: Long,
        val createdAt: Long,
        val label: String? = null
    )

    /**
     * Called when the driver explicitly saves the latest offer as accepted.
     * The destination immediately appears as an area "in learning", but it is not yet treated
     * as proven demand. A pending drop-off is also scheduled for later confirmation.
     */
    fun recordAcceptedDestination(
        context: Context,
        lat: Double?,
        lon: Double?,
        label: String?,
        acceptedAt: Long,
        totalMin: Int
    ) {
        if (lat == null || lon == null) return
        addObservation(context, lat, lon, label, acceptedAt)
        addPending(context, lat, lon, label, acceptedAt, totalMin)
        CloudApiClient.syncAsync(context)
    }

    /**
     * One-time upgrade helper: turns already-saved rides from older versions into low-confidence
     * observations, so users do not lose the history they already collected.
     */
    fun importExistingHistory(context: Context, entries: List<RideHistoryStore.Entry>) {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_HISTORY_IMPORTED_093, false)) return
        var imported = 0
        entries.sortedBy { it.acceptedAt }.forEach { entry ->
            val lat = entry.destinationLat
            val lon = entry.destinationLon
            if (lat != null && lon != null) {
                addObservation(context, lat, lon, entry.destinationText, entry.acceptedAt)
                imported++
            }
        }
        prefs.edit().putBoolean(KEY_HISTORY_IMPORTED_093, true).apply()
        if (imported > 0) CloudApiClient.syncAsync(context)
    }

    /** Backwards-compatible alias used by older code paths. */
    fun startPending(context: Context, lat: Double?, lon: Double?, acceptedAt: Long, totalMin: Int) {
        recordAcceptedDestination(context, lat, lon, null, acceptedAt, totalMin)
    }

    /**
     * Called when a genuinely new offer is detected by OCR. If this happens close to the
     * estimated end of a previously accepted ride, that destination receives one confirmation.
     */
    fun registerNewOffer(context: Context, now: Long = System.currentTimeMillis()) {
        val pending = loadPending(context)
        if (pending.isEmpty()) return

        val valid = pending.filter {
            val earliest = it.expectedDropoffAt - 3 * 60_000L
            val latest = it.expectedDropoffAt + 30 * 60_000L
            now in earliest..latest
        }

        val matched = valid.minByOrNull { abs(now - it.expectedDropoffAt) }
        val remaining = pending.filterNot { item ->
            item == matched || now > item.expectedDropoffAt + 30 * 60_000L
        }

        if (matched != null) {
            addConfirmation(context, matched.lat, matched.lon, matched.label, now)
        }
        persistPending(context, remaining)
    }

    /** Returns only areas with actual evidence of a follow-up offer. */
    fun nearest(context: Context, lat: Double, lon: Double, maxKm: Double = 3.0): Hotspot? {
        return load(context)
            .filter { it.confirmed && it.confidence >= 28 }
            .map { it to haversineKm(lat, lon, it.lat, it.lon) }
            .filter { it.second <= maxKm }
            .minByOrNull { it.second }
            ?.first
    }

    fun load(context: Context): List<Hotspot> {
        val raw = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY_HOTSPOTS, "[]") ?: "[]"
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val legacySuccesses = o.optInt("successes", 0)
                    add(
                        Hotspot(
                            lat = o.optDouble("lat"),
                            lon = o.optDouble("lon"),
                            visits = o.optInt("visits", legacySuccesses.coerceAtLeast(1)).coerceAtLeast(1),
                            successes = legacySuccesses.coerceAtLeast(0),
                            lastSeenAt = o.optLong("lastSeenAt"),
                            label = nullableString(o, "label")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun clear(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .remove(KEY_HOTSPOTS)
            .remove(KEY_PENDING)
            .apply()
        CloudApiClient.syncAsync(context)
    }

    private fun addObservation(context: Context, lat: Double, lon: Double, label: String?, now: Long) {
        val keyLat = round(lat * 100.0) / 100.0
        val keyLon = round(lon * 100.0) / 100.0
        val items = load(context).toMutableList()
        val idx = items.indexOfFirst { haversineKm(keyLat, keyLon, it.lat, it.lon) <= 1.6 }
        if (idx >= 0) {
            val old = items[idx]
            val nextVisits = old.visits + 1
            items[idx] = old.copy(
                lat = (old.lat * old.visits + lat) / nextVisits,
                lon = (old.lon * old.visits + lon) / nextVisits,
                visits = nextVisits,
                lastSeenAt = now,
                label = label?.takeIf { it.isNotBlank() } ?: old.label
            )
        } else {
            items.add(
                Hotspot(
                    lat = keyLat,
                    lon = keyLon,
                    visits = 1,
                    successes = 0,
                    lastSeenAt = now,
                    label = label?.takeIf { it.isNotBlank() }
                )
            )
        }
        persist(context, items.sortedByDescending { it.lastSeenAt }.take(MAX_HOTSPOTS))
    }

    private fun addConfirmation(context: Context, lat: Double, lon: Double, label: String?, now: Long) {
        val items = load(context).toMutableList()
        val idx = items.indexOfFirst { haversineKm(lat, lon, it.lat, it.lon) <= 1.6 }
        if (idx >= 0) {
            val old = items[idx]
            items[idx] = old.copy(
                successes = old.successes + 1,
                lastSeenAt = now,
                label = label?.takeIf { it.isNotBlank() } ?: old.label
            )
        } else {
            items.add(
                Hotspot(
                    lat = round(lat * 100.0) / 100.0,
                    lon = round(lon * 100.0) / 100.0,
                    visits = 1,
                    successes = 1,
                    lastSeenAt = now,
                    label = label?.takeIf { it.isNotBlank() }
                )
            )
        }
        persist(context, items.sortedByDescending { it.lastSeenAt }.take(MAX_HOTSPOTS))
        CloudApiClient.syncAsync(context)
    }

    private fun addPending(
        context: Context,
        lat: Double,
        lon: Double,
        label: String?,
        acceptedAt: Long,
        totalMin: Int
    ) {
        val expected = acceptedAt + totalMin.coerceAtLeast(1) * 60_000L
        val items = loadPending(context).toMutableList()
        items.add(
            Pending(
                lat = lat,
                lon = lon,
                expectedDropoffAt = expected,
                createdAt = acceptedAt,
                label = label?.takeIf { it.isNotBlank() }
            )
        )
        persistPending(context, items.sortedByDescending { it.createdAt }.take(MAX_PENDING))
    }

    private fun persist(context: Context, items: List<Hotspot>) {
        val arr = JSONArray()
        items.forEach { h ->
            arr.put(JSONObject().apply {
                put("lat", h.lat)
                put("lon", h.lon)
                put("visits", h.visits)
                put("successes", h.successes)
                put("lastSeenAt", h.lastSeenAt)
                put("label", h.label ?: JSONObject.NULL)
            })
        }
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(KEY_HOTSPOTS, arr.toString())
            .apply()
    }

    private fun loadPending(context: Context): List<Pending> {
        val raw = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY_PENDING, null) ?: return emptyList()
        return runCatching {
            if (raw.trim().startsWith("[")) {
                val arr = JSONArray(raw)
                buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        add(
                            Pending(
                                lat = o.getDouble("lat"),
                                lon = o.getDouble("lon"),
                                expectedDropoffAt = o.getLong("expected"),
                                createdAt = o.optLong("created"),
                                label = nullableString(o, "label")
                            )
                        )
                    }
                }
            } else {
                // Migration from v0.9.2, which stored only one pending item as an object.
                val o = JSONObject(raw)
                listOf(
                    Pending(
                        lat = o.getDouble("lat"),
                        lon = o.getDouble("lon"),
                        expectedDropoffAt = o.getLong("expected"),
                        createdAt = o.optLong("created"),
                        label = nullableString(o, "label")
                    )
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun persistPending(context: Context, items: List<Pending>) {
        if (items.isEmpty()) {
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().remove(KEY_PENDING).apply()
            return
        }
        val arr = JSONArray()
        items.forEach { p ->
            arr.put(JSONObject().apply {
                put("lat", p.lat)
                put("lon", p.lon)
                put("expected", p.expectedDropoffAt)
                put("created", p.createdAt)
                put("label", p.label ?: JSONObject.NULL)
            })
        }
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(KEY_PENDING, arr.toString())
            .apply()
    }

    private fun nullableString(o: JSONObject, key: String): String? {
        if (!o.has(key) || o.isNull(key)) return null
        return o.optString(key).trim().takeIf { it.isNotBlank() && !it.equals("null", true) }
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
