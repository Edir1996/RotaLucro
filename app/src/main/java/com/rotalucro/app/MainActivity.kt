package com.rotalucro.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rotalucro.app.calculator.DriverSettings
import com.rotalucro.app.calculator.KmThreshold
import com.rotalucro.app.calculator.OfferRating
import com.rotalucro.app.calculator.RideCalculator
import com.rotalucro.app.calculator.RideOffer
import com.rotalucro.app.calculator.RideResult
import com.rotalucro.app.calculator.ScheduledKmThreshold
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
    val initialSchedule1 = initialSettings.scheduledThresholds.getOrElse(0) {
        DriverSettings.defaultScheduledThresholds()[0]
    }
    val initialSchedule2 = initialSettings.scheduledThresholds.getOrElse(1) {
        DriverSettings.defaultScheduledThresholds()[1]
    }

    var defaultMinimumPerKm by remember {
        mutableStateOf(initialSettings.defaultMinimumPerKm.asInput())
    }
    var defaultExcellentPerKm by remember {
        mutableStateOf(initialSettings.defaultExcellentPerKm.asInput())
    }

    var schedule1Enabled by remember { mutableStateOf(initialSchedule1.enabled) }
    var schedule1Start by remember { mutableStateOf(initialSchedule1.startMinuteOfDay.asTimeInput()) }
    var schedule1End by remember { mutableStateOf(initialSchedule1.endMinuteOfDay.asTimeInput()) }
    var schedule1Minimum by remember { mutableStateOf(initialSchedule1.minimumPerKm.asInput()) }
    var schedule1Excellent by remember { mutableStateOf(initialSchedule1.excellentPerKm.asInput()) }

    var schedule2Enabled by remember { mutableStateOf(initialSchedule2.enabled) }
    var schedule2Start by remember { mutableStateOf(initialSchedule2.startMinuteOfDay.asTimeInput()) }
    var schedule2End by remember { mutableStateOf(initialSchedule2.endMinuteOfDay.asTimeInput()) }
    var schedule2Minimum by remember { mutableStateOf(initialSchedule2.minimumPerKm.asInput()) }
    var schedule2Excellent by remember { mutableStateOf(initialSchedule2.excellentPerKm.asInput()) }

    var fuelPrice by remember { mutableStateOf(initialSettings.fuelPricePerLiter.asInput()) }
    var kmPerLiter by remember { mutableStateOf(initialSettings.vehicleKmPerLiter.asInput()) }
    var maintenancePerKm by remember { mutableStateOf(initialSettings.maintenancePerKm.asInput()) }

    var fare by remember { mutableStateOf("25,00") }
    var pickupKm by remember { mutableStateOf("2,0") }
    var tripKm by remember { mutableStateOf("8,0") }
    var pickupMin by remember { mutableStateOf("6") }
    var tripMin by remember { mutableStateOf("20") }
    var result by remember { mutableStateOf<RideResult?>(null) }
    var savedMessage by remember { mutableStateOf<String?>(null) }

    val currentSettings = DriverSettings(
        defaultMinimumPerKm = defaultMinimumPerKm.toNumber(1.20),
        defaultExcellentPerKm = defaultExcellentPerKm.toNumber(1.80),
        scheduledThresholds = listOf(
            ScheduledKmThreshold(
                name = "Dinâmica 1",
                enabled = schedule1Enabled,
                startMinuteOfDay = schedule1Start.toMinuteOfDay(initialSchedule1.startMinuteOfDay),
                endMinuteOfDay = schedule1End.toMinuteOfDay(initialSchedule1.endMinuteOfDay),
                minimumPerKm = schedule1Minimum.toNumber(1.40),
                excellentPerKm = schedule1Excellent.toNumber(2.00)
            ),
            ScheduledKmThreshold(
                name = "Dinâmica 2",
                enabled = schedule2Enabled,
                startMinuteOfDay = schedule2Start.toMinuteOfDay(initialSchedule2.startMinuteOfDay),
                endMinuteOfDay = schedule2End.toMinuteOfDay(initialSchedule2.endMinuteOfDay),
                minimumPerKm = schedule2Minimum.toNumber(1.40),
                excellentPerKm = schedule2Excellent.toNumber(2.00)
            )
        ),
        fuelPricePerLiter = fuelPrice.toNumber(6.0),
        vehicleKmPerLiter = kmPerLiter.toNumber(10.0),
        maintenancePerKm = maintenancePerKm.toNumber(0.35)
    )

    val currentMinute = RideCalculator.currentMinuteOfDay()

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
                text = "Analise a oferta da 99 e use uma faixa de R$/km diferente conforme o horário.",
                style = MaterialTheme.typography.bodyMedium
            )

            DisclosureCard(onOpenAccessibility)
            ActiveThresholdCard(
                threshold = currentSettings.activeKmThreshold(currentMinute),
                currentMinute = currentMinute
            )

            SectionTitle("Faixa padrão — fora da dinâmica")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DecimalField(
                    label = "Mínimo por km",
                    value = defaultMinimumPerKm,
                    modifier = Modifier.weight(1f)
                ) { defaultMinimumPerKm = it }
                DecimalField(
                    label = "Máximo/ótimo por km",
                    value = defaultExcellentPerKm,
                    modifier = Modifier.weight(1f)
                ) { defaultExcellentPerKm = it }
            }
            ThresholdGuide(
                minimum = currentSettings.defaultMinimumPerKm,
                excellent = currentSettings.defaultExcellentPerKm
            )

            SectionTitle("Faixas por horário")
            Text(
                "Ative os períodos em que você exige um valor maior por km. Fora deles, o aplicativo usa a faixa padrão."
            )

            ScheduleEditor(
                title = "Dinâmica 1",
                enabled = schedule1Enabled,
                start = schedule1Start,
                end = schedule1End,
                minimum = schedule1Minimum,
                excellent = schedule1Excellent,
                onEnabledChange = { schedule1Enabled = it },
                onStartChange = { schedule1Start = it },
                onEndChange = { schedule1End = it },
                onMinimumChange = { schedule1Minimum = it },
                onExcellentChange = { schedule1Excellent = it }
            )

            ScheduleEditor(
                title = "Dinâmica 2",
                enabled = schedule2Enabled,
                start = schedule2Start,
                end = schedule2End,
                minimum = schedule2Minimum,
                excellent = schedule2Excellent,
                onEnabledChange = { schedule2Enabled = it },
                onStartChange = { schedule2Start = it },
                onEndChange = { schedule2End = it },
                onMinimumChange = { schedule2Minimum = it },
                onExcellentChange = { schedule2Excellent = it }
            )

            SectionTitle("Custos do veículo")
            DecimalField("Preço do combustível por litro", fuelPrice) { fuelPrice = it }
            DecimalField("Média do veículo em km/l", kmPerLiter) { kmPerLiter = it }
            DecimalField("Manutenção estimada por km", maintenancePerKm) { maintenancePerKm = it }

            Button(
                onClick = {
                    onSaveSettings(currentSettings)
                    savedMessage = "Configurações salvas."
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Salvar configurações")
            }
            savedMessage?.let {
                Text(it, color = GoodColor, fontWeight = FontWeight.SemiBold)
            }

            SectionTitle("Simular uma corrida agora")
            Text(
                "A simulação utiliza automaticamente a faixa que está ativa no horário atual.",
                style = MaterialTheme.typography.bodySmall
            )
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
                    result = RideCalculator.calculate(
                        offer = RideOffer(
                            fare = fare.toNumber(0.0),
                            pickupDistanceKm = pickupKm.toNumber(0.0),
                            tripDistanceKm = tripKm.toNumber(0.0),
                            pickupMinutes = pickupMin.toIntOrNull() ?: 0,
                            tripMinutes = tripMin.toIntOrNull() ?: 0
                        ),
                        settings = currentSettings,
                        minuteOfDay = currentMinute
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
                "O RotaLucro usa o Serviço de Acessibilidade somente para ler os textos do cartão de oferta. Ele não toca em botões e não aceita nem recusa corridas."
            )
            Button(onClick = onOpenAccessibility, modifier = Modifier.fillMaxWidth()) {
                Text("Ativar nas configurações de acessibilidade")
            }
        }
    }
}

@Composable
private fun ActiveThresholdCard(threshold: KmThreshold, currentMinute: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "Faixa ativa agora — ${currentMinute.asTimeInput()}",
                fontWeight = FontWeight.Bold
            )
            Text(threshold.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "Vermelho abaixo de ${money(threshold.minimumPerKm)}/km • " +
                    "amarelo até ${money(threshold.excellentPerKm)}/km • " +
                    "verde a partir disso."
            )
        }
    }
}

@Composable
private fun ScheduleEditor(
    title: String,
    enabled: Boolean,
    start: String,
    end: String,
    minimum: String,
    excellent: String,
    onEnabledChange: (Boolean) -> Unit,
    onStartChange: (String) -> Unit,
    onEndChange: (String) -> Unit,
    onMinimumChange: (String) -> Unit,
    onExcellentChange: (String) -> Unit
) {
    val startMinute = start.toMinuteOfDay(-1)
    val endMinute = end.toMinuteOfDay(-2)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = enabled, onCheckedChange = onEnabledChange)
                Text(title, fontWeight = FontWeight.Bold)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TimeField("Início", start, Modifier.weight(1f), onStartChange)
                TimeField("Fim", end, Modifier.weight(1f), onEndChange)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DecimalField("Mínimo por km", minimum, Modifier.weight(1f), onMinimumChange)
                DecimalField("Máximo/ótimo", excellent, Modifier.weight(1f), onExcellentChange)
            }

            ThresholdGuide(
                minimum = minimum.toNumber(1.40),
                excellent = excellent.toNumber(2.00)
            )

            if (enabled && startMinute == endMinute) {
                Text(
                    "O horário inicial e final não podem ser iguais.",
                    color = BadColor,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun ThresholdGuide(minimum: Double, excellent: Double) {
    val lower = minOf(minimum, excellent)
    val upper = maxOf(minimum, excellent)
    val middle = RideCalculator.middleReference(lower, upper)

    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text("Vermelho: abaixo de ${money(lower)}/km", color = BadColor)
        Text(
            "Amarelo: de ${money(lower)} até menos de ${money(upper)}/km",
            color = AttentionColor
        )
        Text("Verde: a partir de ${money(upper)}/km", color = GoodColor)
        Text(
            "Valor médio entre mínimo e máximo: ${money(middle)}/km",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ResultCard(result: RideResult) {
    val accent = ratingColor(result.rating)
    val title = when (result.rating) {
        OfferRating.GOOD -> "ÓTIMA CORRIDA"
        OfferRating.ATTENTION -> "CORRIDA MÉDIA"
        OfferRating.BAD -> "CORRIDA RUIM"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(3.dp, accent),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, color = accent, fontWeight = FontWeight.ExtraBold)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MetricBox(
                    value = money(result.grossPerKm),
                    label = "por km",
                    color = ratingColor(result.perKmRating),
                    modifier = Modifier.weight(1f)
                )
                MetricBox(
                    value = money(result.grossPerHour),
                    label = "por hora",
                    color = NeutralMetricColor,
                    modifier = Modifier.weight(1f)
                )
                MetricBox(
                    value = money(result.estimatedProfit),
                    label = "lucro est.",
                    color = if (result.estimatedProfit > 0) GoodColor else BadColor,
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider()
            Text(
                "Oferta ${money(result.fare)} • ${result.totalDistanceKm.oneDecimal()} km • ${result.totalMinutes} min",
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Faixa usada: ${result.activeThreshold.name} — " +
                    "${money(result.activeThreshold.minimumPerKm)} a " +
                    "${money(result.activeThreshold.excellentPerKm)}/km"
            )
            Text(
                "Custos estimados: ${money(result.estimatedCost)} " +
                    "(${money(result.fuelCost)} combustível + ${money(result.maintenanceCost)} manutenção)"
            )
        }
    }
}

@Composable
private fun MetricBox(
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            color = color,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = label,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall
        )
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
private fun TimeField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            onValueChange(newValue.filter { it.isDigit() || it == ':' }.take(5))
        },
        label = { Text(label) },
        placeholder = { Text("18:00") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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

private val BadColor = Color(0xFFD32F2F)
private val AttentionColor = Color(0xFFF9A825)
private val GoodColor = Color(0xFF2E7D32)
private val NeutralMetricColor = Color(0xFF37474F)

private fun ratingColor(rating: OfferRating): Color = when (rating) {
    OfferRating.GOOD -> GoodColor
    OfferRating.ATTENTION -> AttentionColor
    OfferRating.BAD -> BadColor
}

private fun String.toNumber(default: Double): Double =
    trim()
        .replace(",", ".")
        .toDoubleOrNull()
        ?: default

private fun String.toMinuteOfDay(default: Int): Int {
    val value = trim()
    val parts = when {
        value.contains(':') -> value.split(':', limit = 2)
        value.length in 3..4 -> listOf(value.dropLast(2), value.takeLast(2))
        else -> return default
    }

    val hour = parts.getOrNull(0)?.toIntOrNull() ?: return default
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: return default
    if (hour !in 0..23 || minute !in 0..59) return default
    return hour * 60 + minute
}

private fun Int.asTimeInput(): String {
    val safe = coerceIn(0, 1439)
    return String.format(Locale("pt", "BR"), "%02d:%02d", safe / 60, safe % 60)
}

private fun Double.asInput(): String = String.format(Locale("pt", "BR"), "%.2f", this)
private fun Double.oneDecimal(): String = String.format(Locale("pt", "BR"), "%.1f", this)
private fun money(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(value)
