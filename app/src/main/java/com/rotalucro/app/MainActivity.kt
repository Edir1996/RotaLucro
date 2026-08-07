package com.rotalucro.app

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rotalucro.app.accessibility.OverlayController
import com.rotalucro.app.accessibility.RideAccessibilityService
import com.rotalucro.app.data.CaptureDiagnostics
import com.rotalucro.app.data.DiagnosticsStore
import com.rotalucro.app.data.SettingsStore
import com.rotalucro.app.ui.RotaLucroApp
import com.rotalucro.app.ui.theme.RotaLucroTheme

class MainActivity : ComponentActivity() {
    private var accessibilityEnabled by mutableStateOf(false)
    private var readerConnected by mutableStateOf(false)
    private var diagnostics by mutableStateOf(CaptureDiagnostics())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            RotaLucroTheme {
                RotaLucroApp(
                    initialSettings = SettingsStore.load(this),
                    accessibilityEnabled = accessibilityEnabled,
                    readerConnected = readerConnected,
                    diagnostics = diagnostics,
                    onOpenAccessibility = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onTestOverlay = {
                        val shown = OverlayController.showPreview()
                        Toast.makeText(
                            this,
                            if (shown) "Prévia exibida sobre a tela." else "Ative a acessibilidade do RotaLucro primeiro.",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onRefreshDiagnostics = { refreshStatus() },
                    onSaveSettings = { settings ->
                        SettingsStore.save(this, settings)
                        Toast.makeText(this, "Configurações salvas.", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        accessibilityEnabled = isAccessibilityServiceEnabled()
        readerConnected = OverlayController.isConnected()
        diagnostics = DiagnosticsStore.load(this)
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponent = ComponentName(this, RideAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()

        return enabledServices
            .split(':')
            .mapNotNull(ComponentName::unflattenFromString)
            .any { it == expectedComponent }
    }
}
