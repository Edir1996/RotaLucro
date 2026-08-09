package com.rotalucro.app.cloud

import android.content.Context
import android.os.Build
import com.rotalucro.app.data.DemandLearningStore
import com.rotalucro.app.data.DemandZoneStore
import com.rotalucro.app.data.RideHistoryStore
import com.rotalucro.app.data.SettingsStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

object CloudApiClient {
    data class ApiResult(val ok: Boolean, val message: String, val syncedAt: String? = null)
    data class LoginResult(val ok: Boolean, val message: String, val session: CloudSession? = null)

    private val executor = Executors.newSingleThreadExecutor()

    fun loginAsync(
        context: Context,
        username: String,
        password: String,
        callback: (LoginResult) -> Unit
    ) {
        executor.execute {
            val result = login(context, username, password)
            android.os.Handler(context.mainLooper).post { callback(result) }
        }
    }

    fun syncAsync(context: Context, callback: ((ApiResult) -> Unit)? = null) {
        executor.execute {
            val result = sync(context)
            if (callback != null) android.os.Handler(context.mainLooper).post { callback(result) }
        }
    }

    fun logoutAsync(context: Context) {
        val session = AccountStore.load(context)
        AccountStore.clearSession(context)
        if (session == null) return
        executor.execute {
            runCatching {
                requestJson(
                    url = "${session.baseUrl}/api/v1/logout.php",
                    method = "POST",
                    token = session.token,
                    body = JSONObject().put("device_id", session.deviceId)
                )
            }
        }
    }

    private fun login(context: Context, username: String, password: String): LoginResult {
        val baseUrl = ServerConfig.BASE_URL
        if (username.isBlank() || password.isBlank()) return LoginResult(false, "Informe usuário e senha")
        return try {
            val deviceId = AccountStore.ensureDeviceId(context)
            val body = JSONObject().apply {
                put("username", username.trim())
                put("password", password)
                put("device_id", deviceId)
                put("device_name", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
                put("app_version", "0.9.1")
            }
            val json = requestJson("$baseUrl/api/v1/login.php", "POST", null, body)
            if (!json.optBoolean("ok", false)) {
                LoginResult(false, json.optString("message", "Não foi possível entrar"))
            } else {
                val user = json.optJSONObject("user") ?: JSONObject()
                val session = CloudSession(
                    baseUrl = baseUrl,
                    token = json.getString("token"),
                    username = user.optString("username", username.trim()),
                    displayName = user.optString("display_name", username.trim()),
                    role = user.optString("role", "user"),
                    expiresAt = json.optString("expires_at").takeIf { it.isNotBlank() },
                    deviceId = deviceId
                )
                AccountStore.save(context, session)
                sync(context)
                LoginResult(true, "Login realizado", session)
            }
        } catch (t: Throwable) {
            LoginResult(false, friendlyNetworkError(t))
        }
    }

    private fun sync(context: Context): ApiResult {
        val session = AccountStore.load(context) ?: return ApiResult(false, "Faça login para sincronizar")
        return try {
            val payload = buildSyncPayload(context, session)
            val json = requestJson("${session.baseUrl}/api/v1/sync.php", "POST", session.token, payload)
            ApiResult(
                ok = json.optBoolean("ok", false),
                message = json.optString("message", if (json.optBoolean("ok", false)) "Sincronizado" else "Falha na sincronização"),
                syncedAt = json.optString("synced_at", null)
            )
        } catch (t: Throwable) {
            ApiResult(false, friendlyNetworkError(t))
        }
    }

    private fun buildSyncPayload(context: Context, session: CloudSession): JSONObject {
        val settings = SettingsStore.load(context)
        return JSONObject().apply {
            put("device_id", session.deviceId)
            put("device_name", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
            put("app_version", "0.9.1")
            put("replace", true)
            put("rides", JSONArray().apply {
                RideHistoryStore.load(context).forEach { e ->
                    put(JSONObject().apply {
                        put("client_id", e.id)
                        put("accepted_at", e.acceptedAt)
                        put("fare", e.fare)
                        put("total_km", e.totalKm)
                        put("total_min", e.totalMin)
                        put("analysis_per_km", e.analysisPerKm)
                        put("analysis_per_hour", e.analysisPerHour)
                        put("estimated_profit", e.estimatedProfit)
                        put("rating", e.rating.name)
                        put("possible_empty_return", e.possibleEmptyReturn)
                        put("empty_return_km", e.emptyReturnKm)
                        put("destination_text", e.destinationText ?: JSONObject.NULL)
                        put("destination_lat", e.destinationLat ?: JSONObject.NULL)
                        put("destination_lon", e.destinationLon ?: JSONObject.NULL)
                        put("destination_city", e.destinationCity ?: JSONObject.NULL)
                        put("distance_to_demand_km", e.distanceToDemandKm ?: JSONObject.NULL)
                        put("demand_zone_name", e.demandZoneName ?: JSONObject.NULL)
                        put("smart_score", e.smartScore)
                        put("recommendation", e.recommendation.name)
                    })
                }
            })
            put("hotspots", JSONArray().apply {
                DemandLearningStore.load(context).forEach { h ->
                    put(JSONObject().apply {
                        put("client_key", "${"%.5f".format(java.util.Locale.US, h.lat)}_${"%.5f".format(java.util.Locale.US, h.lon)}")
                        put("lat", h.lat)
                        put("lon", h.lon)
                        put("successes", h.successes)
                        put("confidence", h.confidence)
                        put("last_seen_at", h.lastSeenAt)
                    })
                }
            })
            put("zones", JSONArray().apply {
                DemandZoneStore.load(context).forEach { z ->
                    put(JSONObject().apply {
                        put("client_id", z.id)
                        put("name", z.name)
                        put("reference_address", z.referenceAddress)
                        put("keywords", z.keywords)
                        put("radius_km", z.radiusKm)
                        put("demand_level", z.demandLevel.name)
                        put("enabled", z.enabled)
                        put("lat", z.latitude ?: JSONObject.NULL)
                        put("lon", z.longitude ?: JSONObject.NULL)
                    })
                }
            })
            put("settings", JSONObject().apply {
                put("default_minimum_per_km", settings.defaultMinimumPerKm)
                put("default_excellent_per_km", settings.defaultExcellentPerKm)
                put("fuel_price_per_liter", settings.fuelPricePerLiter)
                put("vehicle_km_per_liter", settings.vehicleKmPerLiter)
                put("maintenance_per_km", settings.maintenancePerKm)
                put("base_city", settings.baseCity)
                put("smart_demand_enabled", settings.smartDemandEnabled)
                put("demand_learning_enabled", settings.demandLearningEnabled)
                put("premium_min_5_7_km", settings.premiumMin5To7Km)
                put("premium_min_7_9_km", settings.premiumMin7To9Km)
                put("premium_min_9_plus_km", settings.premiumMin9PlusKm)
                put("scheduled_thresholds", JSONArray().apply {
                    settings.scheduledThresholds.forEach { s -> put(JSONObject().apply {
                        put("name", s.name); put("enabled", s.enabled); put("start", s.startMinuteOfDay); put("end", s.endMinuteOfDay)
                        put("minimum", s.minimumPerKm); put("excellent", s.excellentPerKm)
                    }) }
                })
            })
        }
    }

    private fun requestJson(url: String, method: String, token: String?, body: JSONObject?): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 12_000
            connection.readTimeout = 18_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("User-Agent", "RotaLucro-Android/0.9.0")
            if (!token.isNullOrBlank()) {
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("X-Api-Token", token)
            }
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { out -> out.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = if (stream != null) BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText() } else ""
            val json = if (text.isBlank()) JSONObject() else JSONObject(text)
            if (code !in 200..299 && !json.has("ok")) json.put("ok", false)
            if (code == 401) json.put("message", "Sessão expirada. Entre novamente.")
            return json
        } finally {
            connection.disconnect()
        }
    }

    private fun friendlyNetworkError(t: Throwable): String {
        val name = t.javaClass.simpleName
        return when {
            name.contains("UnknownHost", true) -> "Não foi possível localizar o servidor. Confira o endereço do painel e a internet."
            name.contains("SSL", true) -> "Falha de segurança HTTPS. Confira o certificado SSL do painel."
            name.contains("Timeout", true) -> "O servidor demorou para responder. Tente novamente."
            else -> "Falha ao conectar ao painel: ${t.message ?: name}"
        }
    }
}
