package com.rotalucro.app.data

import android.content.Context
import com.rotalucro.app.calculator.DemandLevel
import org.json.JSONArray
import org.json.JSONObject

object DemandZoneStore {
    private const val FILE = "demand_zones"
    private const val KEY = "zones"
    private const val MAX_ZONES = 20

    data class Zone(
        val id: Long,
        val name: String,
        val referenceAddress: String,
        val keywords: String = "",
        val radiusKm: Double = 2.0,
        val demandLevel: DemandLevel = DemandLevel.HIGH,
        val enabled: Boolean = true,
        val latitude: Double? = null,
        val longitude: Double? = null
    ) {
        fun keywordList(): List<String> = keywords.split(';', ',', '\n')
            .map { it.trim() }
            .filter { it.length >= 3 }
    }

    fun load(context: Context): List<Zone> {
        val raw = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        Zone(
                            id = o.optLong("id", System.currentTimeMillis() + i),
                            name = o.optString("name", "Região de demanda"),
                            referenceAddress = o.optString("referenceAddress", ""),
                            keywords = o.optString("keywords", ""),
                            radiusKm = o.optDouble("radiusKm", 2.0).coerceIn(0.3, 20.0),
                            demandLevel = runCatching { DemandLevel.valueOf(o.optString("demandLevel", DemandLevel.HIGH.name)) }.getOrDefault(DemandLevel.HIGH),
                            enabled = o.optBoolean("enabled", true),
                            latitude = o.takeIf { it.has("latitude") && !it.isNull("latitude") }?.optDouble("latitude"),
                            longitude = o.takeIf { it.has("longitude") && !it.isNull("longitude") }?.optDouble("longitude")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, zones: List<Zone>) {
        val arr = JSONArray()
        zones.take(MAX_ZONES).forEach { z ->
            arr.put(JSONObject().apply {
                put("id", z.id)
                put("name", z.name)
                put("referenceAddress", z.referenceAddress)
                put("keywords", z.keywords)
                put("radiusKm", z.radiusKm)
                put("demandLevel", z.demandLevel.name)
                put("enabled", z.enabled)
                if (z.latitude != null) put("latitude", z.latitude) else put("latitude", JSONObject.NULL)
                if (z.longitude != null) put("longitude", z.longitude) else put("longitude", JSONObject.NULL)
            })
        }
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }

    fun updateResolved(context: Context, id: Long, lat: Double, lon: Double) {
        val updated = load(context).map { if (it.id == id) it.copy(latitude = lat, longitude = lon) else it }
        save(context, updated)
    }
}
