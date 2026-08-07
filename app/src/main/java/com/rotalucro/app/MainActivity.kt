package com.rotalucro.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.rotalucro.app.calculator.DriverSettings
import com.rotalucro.app.calculator.OfferRating
import com.rotalucro.app.calculator.RideCalculator
import com.rotalucro.app.calculator.RideOffer
import com.rotalucro.app.calculator.RideResult
import com.rotalucro.app.data.SettingsStore
import com.rotalucro.app.ui.theme.RotaLucroTheme
import java.text.NumberFormat
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RotaLucroTheme {
                AppScreen(
                    initialSettings = SettingsStore.load(this),
                    onSaveSettings = { SettingsStore.save(this, it) },
                    onOpenAccessibility = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                )
            }
        }
    }
}

@Composable
private fun AppScreen(
    initialSettings: DriverSettings,
    onSaveSettings: (DriverSettings) -> Unit,
    onOpenAccessibility: () -> Unit
) {
    var minimumPerKm by remember { mutableStateOf(initialSettings.minimumPerKm.asInput()) }
    var minimumPerHour by remember { mutableStateOf(initialSettings.minimumPerHour.asInput()) }
    var fuelPrice by remember { mutableStateOf(initialSettings.fuelPricePerLiter.asInput()) }
    var kmPerLiter by remember { mutableStateOf(initialSettings.vehicleKmPerLiter.asInput()) }
    var maintenancePerKm by remember { mutableStateOf(initialSettings.maintenancePerKm.asInput()) }

    var fare by remember { mutableStateOf("25,00") }
    var pickupKm by remember { mutableStateOf("2,0") }
    var tripKm by remember { mutableStateOf("8,0") }
    var pickupMin by remember { mutableStateOf("6") }
    var tripMin by remember { mutableStateOf("20") }
    var result by remember { mutableStateOf<RideResult?>(null) }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "RotaLucro",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Calculadora de ofertas para motoristas. O app não aceita nem recusa corridas automaticamente.",
                style = MaterialTheme.typography.bodyMedium
            )

            DisclosureCard(onOpenAccessibility)

            SectionTitle("Suas metas e custos")
            DecimalField("Mínimo por km", minimumPerKm) { minimumPerKm = it }
            DecimalField("Mínimo por hora", minimumPerHour) { minimumPerHour = it }
            DecimalField("Preço do combustível por litro", fuelPrice) { fuelPrice = it }
            DecimalField("Média do veículo em km/l", kmPerLiter) { kmPerLiter = it }
            DecimalField("Manutenção estimada por km", maintenancePerKm) { maintenancePerKm = it }

            Button(
                onClick = {
                    onSaveSettings(
                        DriverSettings(
                            minimumPerKm = minimumPerKm.toNumber(2.0),
                            minimumPerHour = minimumPerHour.toNumber(35.0),
                            fuelPricePerLiter = fuelPrice.toNumber(6.0),
                            vehicleKmPerLiter = kmPerLiter.toNumber(10.0),
                            maintenancePerKm = maintenancePerKm.toNumber(0.35)
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Salvar configurações")
            }

            SectionTitle("Simular uma corrida")
            DecimalField("Valor da oferta", fare) { fare = it }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DecimalField("Km até buscar", pickupKm, Modifier.weight(1f)) { pickupKm = it }
                DecimalField("Km da viagem", tripKm, Modifier.weight(1f)) { tripKm = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IntegerField("Min até buscar", pickupMin, Modifier.weight(1f)) { pickupMin = it }
                IntegerField("Min da viagem", tripMin, Modifier.weight(1f)) { tripMin = it }
            }

            Button(
                onClick = {
                    val settings = DriverSettings(
                        minimumPerKm = minimumPerKm.toNumber(2.0),
                        minimumPerHour = minimumPerHour.toNumber(35.0),
                        fuelPricePerLiter = fuelPrice.toNumber(6.0),
                        vehicleKmPerLiter = kmPerLiter.toNumber(10.0),
                        maintenancePerKm = maintenancePerKm.toNumber(0.35)
                    )
                    result = RideCalculator.calculate(
                        RideOffer(
                            fare = fare.toNumber(0.0),
                            pickupDistanceKm = pickupKm.toNumber(0.0),
                            tripDistanceKm = tripKm.toNumber(0.0),
                            pickupMinutes = pickupMin.toIntOrNull() ?: 0,
                            tripMinutes = tripMin.toIntOrNull() ?: 0
                        ),
                        settings
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Calcular corrida")
            }

            result?.let { ResultCard(it) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DisclosureCard(onOpenAccessibility: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Leitura automática da 99", fontWeight = FontWeight.Bold)
            Text(
                "Para calcular em tempo real, o RotaLucro usa o Serviço de Acessibilidade somente para ler os textos do cartão de oferta da 99. Nenhuma senha é lida, nenhum toque é feito e nenhuma corrida é aceita ou recusada pelo app."
            )
            Button(onClick = onOpenAccessibility, modifier = Modifier.fillMaxWidth()) {
                Text("Ativar nas configurações de acessibilidade")
            }
        }
    }
}

@Composable
private fun ResultCard(result: RideResult) {
    val title = when (result.rating) {
        OfferRating.GOOD -> "Boa corrida"
        OfferRating.ATTENTION -> "Atenção"
        OfferRating.BAD -> "Corrida fraca"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Bruto por km: ${money(result.grossPerKm)}")
            Text("Bruto por hora: ${money(result.grossPerHour)}")
            Text("Combustível: ${money(result.fuelCost)}")
            Text("Manutenção: ${money(result.maintenanceCost)}")
            Text("Lucro estimado: ${money(result.estimatedProfit)}", fontWeight = FontWeight.Bold)
            Text("Distância total: ${result.totalDistanceKm.oneDecimal()} km")
            Text("Tempo total: ${result.totalMinutes} min")
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun DecimalField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            onValueChange(newValue.filter { it.isDigit() || it == ',' || it == '.' })
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun IntegerField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit)) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = modifier.fillMaxWidth()
    )
}

private fun String.toNumber(default: Double): Double =
    replace(".", "")
        .replace(",", ".")
        .toDoubleOrNull()
        ?: default

private fun Double.asInput(): String = String.format(Locale("pt", "BR"), "%.2f", this)
private fun Double.oneDecimal(): String = String.format(Locale("pt", "BR"), "%.1f", this)
private fun money(value: Double): String = NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(value)
