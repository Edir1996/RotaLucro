package com.rotalucro.app.cloud

import android.content.Context
import java.util.UUID

data class CloudSession(
    val baseUrl: String,
    val token: String,
    val username: String,
    val displayName: String,
    val role: String,
    val expiresAt: String? = null,
    val deviceId: String
)

object AccountStore {
    private const val FILE = "cloud_account"

    fun load(context: Context): CloudSession? {
        val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val token = p.getString("token", null)?.trim().orEmpty()
        if (token.isBlank()) return null
        return CloudSession(
            baseUrl = ServerConfig.BASE_URL,
            token = token,
            username = p.getString("username", "").orEmpty(),
            displayName = p.getString("display_name", "").orEmpty(),
            role = p.getString("role", "user").orEmpty(),
            expiresAt = p.getString("expires_at", null),
            deviceId = ensureDeviceId(context)
        )
    }

    fun save(context: Context, session: CloudSession) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString("token", session.token)
            .putString("username", session.username)
            .putString("display_name", session.displayName)
            .putString("role", session.role)
            .putString("expires_at", session.expiresAt)
            .putString("device_id", session.deviceId)
            .apply()
    }

    fun clearSession(context: Context) {
        val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val deviceId = ensureDeviceId(context)
        p.edit().clear().putString("device_id", deviceId).apply()
    }

    fun ensureDeviceId(context: Context): String {
        val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val current = p.getString("device_id", null)?.trim().orEmpty()
        if (current.isNotBlank()) return current
        val created = UUID.randomUUID().toString()
        p.edit().putString("device_id", created).apply()
        return created
    }

    fun normalizeBaseUrl(raw: String): String = raw.trim().trimEnd('/')
}
