package com.rotalucro.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rotalucro.app.calculator.DriverSettings
import com.rotalucro.app.calculator.KmThreshold
import com.rotalucro.app.calculator.OfferRating
import com.rotalucro.app.calculator.RideCalculator
import com.rotalucro.app.calculator.RideOffer
import com.rotalucro.app.calculator.RideResult
import com.rotalucro.app.calculator.ScheduledKmThreshold
import com.rotalucro.app.data.CaptureDiagnostics
import com.rotalucro.app.ui.theme.AppBackground
import com.rotalucro.app.ui.theme.BorderColor
import com.rotalucro.app.ui.theme.BrandAmber
import com.rotalucro.app.ui.theme.BrandBlue
import com.rotalucro.app.ui.theme.BrandGreen
import com.rotalucro.app.ui.theme.BrandNavy
import com.rotalucro.app.ui.theme.BrandRed
import com.rotalucro.app.ui.theme.CardSurface
import com.rotalucro.app.ui.theme.MutedText
import com.rotalucro.app.ui.theme.SlateText
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

private enum class Destination(
    val label: String,
    val icon: ImageVector
) {
    HOME("Início", Icons.Rounded.Home),
    RULES("Regras", Icons.Rounded.Schedule),
    SIMULATOR("Simular", Icons.Rounded.Calculate),
    SETTINGS("Ajustes", Icons.Rounded.Settings)
}

private data class ScheduleDraft(
    val name: String,
    val enabled: Boolean,
    val start: String,
    val end: String,
    val minimum: String,
    val excellent: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RotaLucroApp(
    initialSettings: DriverSettings,
    accessibilityEnabled: Boolean,
    readerConnected: Boolean,
    diagnostics: CaptureDiagnostics,
    onOpenAccessibility: () -> Unit,
    onTestOverlay: () -> Unit,
    onRefreshDiagnostics: () -> Unit,
    onSaveSettings: (DriverSettings) -> Unit
) {
    var destination by rememberSaveable { mutableStateOf(Destination.HOME) }

    var defaultMinimum by remember { mutableStateOf(initialSettings.defaultMinimumPerKm.asInput()) }
    var defaultExcellent by remember { mutableStateOf(initialSettings.defaultExcellentPerKm.asInput()) }
    var fuelPrice by remember { mutableStateOf(initialSettings.fuelPricePerLiter.asInput()) }
    var kmPerLiter by remember { mutableStateOf(initialSettings.vehicleKmPerLiter.asInput()) }
    var maintenancePerKm by remember { mutableStateOf(initialSettings.maintenancePerKm.asInput()) }
    var overlayTimeout by remember { mutableStateOf(initialSettings.overlayAutoHideSeconds.toString()) }

    val startingSchedules = remember(initialSettings) {
        val defaults = DriverSettings.defaultScheduledThresholds()
        List(4) { index ->
            val schedule = initialSettings.scheduledThresholds.getOrElse(index) { defaults[index] }
            ScheduleDraft(
                name = schedule.name,
                enabled = schedule.enabled,
                start = schedule.startMinuteOfDay.asTimeInput(),
                end = schedule.endMinuteOfDay.asTimeInput(),
                minimum = schedule.minimumPerKm.asInput(),
                excellent = schedule.excellentPerKm.asInput()
            )
        }
    }
    var schedules by remember { mutableStateOf(startingSchedules) }

    fun buildSettings(): DriverSettings = DriverSettings(
        defaultMinimumPerKm = defaultMinimum.toNumber(1.20),
        defaultExcellentPerKm = defaultExcellent.toNumber(1.80),
        scheduledThresholds = schedules.mapIndexed { index, draft ->
            val fallback = DriverSettings.defaultScheduledThresholds()[index]
            ScheduledKmThreshold(
                name = draft.name.ifBlank { fallback.name },
                enabled = draft.enabled,
                startMinuteOfDay = draft.start.toMinuteOfDay(fallback.startMinuteOfDay),
                endMinuteOfDay = draft.end.toMinuteOfDay(fallback.endMinuteOfDay),
                minimumPerKm = draft.minimum.toNumber(fallback.minimumPerKm),
                excellentPerKm = draft.excellent.toNumber(fallback.excellentPerKm)
            )
        },
        fuelPricePerLiter = fuelPrice.toNumber(6.0),
        vehicleKmPerLiter = kmPerLiter.toNumber(35.0),
        maintenancePerKm = maintenancePerKm.toNumber(0.18),
        overlayAutoHideSeconds = overlayTimeout.toIntOrNull()?.coerceIn(8, 45) ?: 18
    )

    val currentSettings = buildSettings()

    Scaffold(
        containerColor = AppBackground,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("RotaLucro", fontWeight = FontWeight.ExtraBold, color = SlateText)
                        Text("análise inteligente de ofertas", fontSize = 11.sp, color = MutedText)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = CardSurface
                )
            )
        },
        bottomBar = {
            NavigationBar(containerColor = CardSurface, tonalElevation = 8.dp) {
                Destination.entries.forEach { item ->
                    NavigationBarItem(
                        selected = destination == item,
                        onClick = { destination = item },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = BrandBlue,
                            selectedTextColor = BrandBlue,
                            indicatorColor = Color(0xFFDBEAFE),
                            unselectedIconColor = MutedText,
                            unselectedTextColor = MutedText
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (destination) {
                Destination.HOME -> DashboardScreen(
                    settings = currentSettings,
                    accessibilityEnabled = accessibilityEnabled,
                    readerConnected = readerConnected,
                    diagnostics = diagnostics,
                    onOpenAccessibility = onOpenAccessibility,
                    onTestOverlay = onTestOverlay,
                    onRefreshDiagnostics = onRefreshDiagnostics
                )

                Destination.RULES -> RulesScreen(
                    defaultMinimum = defaultMinimum,
                    defaultExcellent = defaultExcellent,
                    schedules = schedules,
                    onDefaultMinimumChange = { defaultMinimum = it },
                    onDefaultExcellentChange = { defaultExcellent = it },
                    onScheduleChange = { index, draft ->
                        schedules = schedules.toMutableList().also { it[index] = draft }
                    },
                    onSave = { onSaveSettings(buildSettings()) }
                )

                Destination.SIMULATOR -> SimulatorScreen(settings = currentSettings)

                Destination.SETTINGS -> SettingsScreen(
                    fuelPrice = fuelPrice,
                    kmPerLiter = kmPerLiter,
                    maintenancePerKm = maintenancePerKm,
                    overlayTimeout = overlayTimeout,
                    onFuelPriceChange = { fuelPrice = it },
                    onKmPerLiterChange = { kmPerLiter = it },
                    onMaintenanceChange = { maintenancePerKm = it },
                    onOverlayTimeoutChange = { overlayTimeout = it },
                    onSave = { onSaveSettings(buildSettings()) }
                )
            }
        }
    }
}

@Composable
private fun DashboardScreen(
    settings: DriverSettings,
    accessibilityEnabled: Boolean,
    readerConnected: Boolean,
    diagnostics: CaptureDiagnostics,
    onOpenAccessibility: () -> Unit,
    onTestOverlay: () -> Unit,
    onRefreshDiagnostics: () -> Unit
) {
    val currentMinute = RideCalculator.currentMinuteOfDay()
    val activeThreshold = settings.activeKmThreshold(currentMinute)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            HeroCard(activeThreshold, currentMinute)
        }
        item {
            SectionHeader("Monitoramento", "Confira se o leitor está pronto antes de abrir a 99.")
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusCard(
                    title = "Acessibilidade",
                    subtitle = if (accessibilityEnabled) "Ativada" else "Precisa ativar",
                    active = accessibilityEnabled,
                    modifier = Modifier.weight(1f)
                )
                StatusCard(
                    title = "Leitor",
                    subtitle = if (readerConnected) "Conectado" else "Desconectado",
                    active = readerConnected,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onTestOverlay,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Testar box")
                }
                OutlinedButton(
                    onClick = onOpenAccessibility,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, BrandBlue)
                ) {
                    Text("Acessibilidade")
                }
            }
        }
        item {
            DiagnosticCard(diagnostics, onRefreshDiagnostics)
        }
        item {
            HowItWorksCard()
        }
    }
}

@Composable
private fun HeroCard(threshold: KmThreshold, currentMinute: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(BrandNavy, Color(0xFF172554), BrandBlue)
                )
            )
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Regra ativa agora",
                        color = Color(0xFFBFDBFE),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        threshold.name,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Surface(
                    color = Color.White.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(99.dp)
                ) {
                    Text(
                        currentMinute.asTimeInput(),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                "A cor do box é definida pelo valor total recebido dividido por todos os quilômetros até buscar e deixar o passageiro.",
                color = Color(0xFFE2E8F0),
                fontSize = 13.sp,
                lineHeight = 19.sp
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThresholdMiniCard(
                    label = "Mínimo",
                    value = money(threshold.minimumPerKm),
                    color = BrandRed,
                    modifier = Modifier.weight(1f)
                )
                ThresholdMiniCard(
                    label = "Média",
                    value = money(threshold.middlePerKm),
                    color = BrandAmber,
                    modifier = Modifier.weight(1f)
                )
                ThresholdMiniCard(
                    label = "Ótima",
                    value = money(threshold.excellentPerKm),
                    color = BrandGreen,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ThresholdMiniCard(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.height(5.dp))
        Text(label, color = Color(0xFFCBD5E1), fontSize = 10.sp)
        Text("$value/km", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
    }
}

@Composable
private fun StatusCard(
    title: String,
    subtitle: String,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = if (active) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)
            ) {
                Icon(
                    imageVector = if (active) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = if (active) BrandGreen else BrandRed,
                    modifier = Modifier.padding(8.dp).size(20.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(subtitle, color = if (active) BrandGreen else BrandRed, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun DiagnosticCard(
    diagnostics: CaptureDiagnostics,
    onRefresh: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Última leitura da 99", fontWeight = FontWeight.ExtraBold)
                    Text(diagnostics.formattedTime, color = MutedText, fontSize = 12.sp)
                }
                FilledTonalButton(onClick = onRefresh, contentPadding = PaddingValues(horizontal = 12.dp)) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Atualizar")
                }
            }
            Surface(
                color = if (diagnostics.success) Color(0xFFDCFCE7) else Color(0xFFFFF7ED),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = if (diagnostics.success) Icons.Rounded.CheckCircle else Icons.Rounded.Info,
                        contentDescription = null,
                        tint = if (diagnostics.success) BrandGreen else Color(0xFFEA580C)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(diagnostics.message, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        if (diagnostics.summary.isNotBlank()) {
                            Text(diagnostics.summary, color = MutedText, fontSize = 12.sp)
                        }
                        if (diagnostics.textCount > 0) {
                            Text("${diagnostics.textCount} textos acessíveis encontrados", color = MutedText, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HowItWorksCard() {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF2FF)),
        border = BorderStroke(1.dp, Color(0xFFC7D2FE))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Info, contentDescription = null, tint = BrandBlue)
                Spacer(Modifier.width(8.dp))
                Text("Como o box funciona", fontWeight = FontWeight.ExtraBold)
            }
            Text("• Vermelho: abaixo do mínimo da faixa ativa.", fontSize = 13.sp)
            Text("• Amarelo: entre o mínimo e o valor ótimo.", fontSize = 13.sp)
            Text("• Verde: igual ou acima do valor ótimo.", fontSize = 13.sp)
            Text(
                "O R$/hora e o lucro estimado aparecem como informação, mas a cor principal segue o R$/km configurado por você.",
                color = MutedText,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun RulesScreen(
    defaultMinimum: String,
    defaultExcellent: String,
    schedules: List<ScheduleDraft>,
    onDefaultMinimumChange: (String) -> Unit,
    onDefaultExcellentChange: (String) -> Unit,
    onScheduleChange: (Int, ScheduleDraft) -> Unit,
    onSave: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionHeader(
                "Regras por horário",
                "Defina uma faixa padrão e aumente sua exigência nos horários de dinâmica."
            )
        }
        item {
            RuleEditorCard(
                title = "Faixa padrão",
                subtitle = "Usada quando nenhum horário abaixo estiver ativo.",
                minimum = defaultMinimum,
                excellent = defaultExcellent,
                onMinimumChange = onDefaultMinimumChange,
                onExcellentChange = onDefaultExcellentChange
            )
        }
        items(schedules.size) { index ->
            ScheduleEditorCard(
                index = index,
                draft = schedules[index],
                onChange = { onScheduleChange(index, it) }
            )
        }
        item {
            RatingLegend(
                defaultMinimum.toNumber(1.20),
                defaultExcellent.toNumber(1.80)
            )
        }
        item {
            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
            ) {
                Icon(Icons.Rounded.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Salvar regras", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun RuleEditorCard(
    title: String,
    subtitle: String,
    minimum: String,
    excellent: String,
    onMinimumChange: (String) -> Unit,
    onExcellentChange: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            Text(subtitle, color = MutedText, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DecimalField("Mínimo por km", minimum, Modifier.weight(1f), onMinimumChange)
                DecimalField("Ótima a partir de", excellent, Modifier.weight(1f), onExcellentChange)
            }
            MiniRange(
                minimum = minimum.toNumber(1.20),
                excellent = excellent.toNumber(1.80)
            )
        }
    }
}

@Composable
private fun ScheduleEditorCard(
    index: Int,
    draft: ScheduleDraft,
    onChange: (ScheduleDraft) -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (draft.enabled) Color(0xFFF8FAFF) else CardSurface
        ),
        border = BorderStroke(1.dp, if (draft.enabled) Color(0xFFBFDBFE) else BorderColor)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = if (draft.enabled) Color(0xFFDBEAFE) else Color(0xFFF1F5F9)
                ) {
                    Text(
                        "${index + 1}",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        color = if (draft.enabled) BrandBlue else MutedText,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(draft.name.ifBlank { "Horário ${index + 1}" }, fontWeight = FontWeight.ExtraBold)
                    Text(if (draft.enabled) "Ativo" else "Desativado", color = if (draft.enabled) BrandGreen else MutedText, fontSize = 12.sp)
                }
                Switch(
                    checked = draft.enabled,
                    onCheckedChange = { onChange(draft.copy(enabled = it)) }
                )
            }

            OutlinedTextField(
                value = draft.name,
                onValueChange = { onChange(draft.copy(name = it.take(24))) },
                label = { Text("Nome do horário") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TimeField("Início", draft.start, Modifier.weight(1f)) {
                    onChange(draft.copy(start = it))
                }
                TimeField("Fim", draft.end, Modifier.weight(1f)) {
                    onChange(draft.copy(end = it))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DecimalField("Mínimo por km", draft.minimum, Modifier.weight(1f)) {
                    onChange(draft.copy(minimum = it))
                }
                DecimalField("Ótima a partir de", draft.excellent, Modifier.weight(1f)) {
                    onChange(draft.copy(excellent = it))
                }
            }

            MiniRange(
                minimum = draft.minimum.toNumber(1.40),
                excellent = draft.excellent.toNumber(2.00)
            )

            if (draft.enabled && draft.start.toMinuteOfDay(-1) == draft.end.toMinuteOfDay(-2)) {
                Text("O início e o fim não podem ser iguais.", color = BrandRed, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun MiniRange(minimum: Double, excellent: Double) {
    val lower = min(minimum, excellent)
    val upper = max(minimum, excellent)
    val middle = RideCalculator.middleReference(lower, upper)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF1F5F9))
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        SmallRangeValue("Ruim", "< ${money(lower)}", BrandRed)
        SmallRangeValue("Média", money(middle), BrandAmber)
        SmallRangeValue("Ótima", "≥ ${money(upper)}", BrandGreen)
    }
}

@Composable
private fun SmallRangeValue(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(value, color = SlateText, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun RatingLegend(minimum: Double, excellent: Double) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
        border = BorderStroke(1.dp, Color(0xFFFDE68A))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("Como a média é calculada", fontWeight = FontWeight.ExtraBold)
            Text(
                "O valor central é a média entre o mínimo e o ótimo: ${money(RideCalculator.middleReference(minimum, excellent))}/km.",
                fontSize = 13.sp
            )
            Text(
                "Quando horários se sobrepõem, o primeiro horário ativo da lista é usado.",
                color = MutedText,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun SimulatorScreen(settings: DriverSettings) {
    var fare by remember { mutableStateOf("8,40") }
    var pickupKm by remember { mutableStateOf("2,0") }
    var tripKm by remember { mutableStateOf("2,3") }
    var pickupMinutes by remember { mutableStateOf("6") }
    var tripMinutes by remember { mutableStateOf("5") }
    var result by remember {
        mutableStateOf(
            RideCalculator.calculate(
                RideOffer(8.40, 2.0, 2.3, 6, 5, surgeMultiplier = 1.6, productName = "Moto"),
                settings
            )
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionHeader("Simulador", "Teste uma oferta antes de usar o leitor automático.")
        }
        item {
            ResultPreviewCard(result)
        }
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Dados da oferta", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                    DecimalField("Valor total", fare, Modifier.fillMaxWidth()) { fare = it }
                    Text("Até buscar o passageiro", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DecimalField("Distância (km)", pickupKm, Modifier.weight(1f)) { pickupKm = it }
                        IntegerField("Tempo (min)", pickupMinutes, Modifier.weight(1f)) { pickupMinutes = it }
                    }
                    Text("Corrida com o passageiro", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DecimalField("Distância (km)", tripKm, Modifier.weight(1f)) { tripKm = it }
                        IntegerField("Tempo (min)", tripMinutes, Modifier.weight(1f)) { tripMinutes = it }
                    }
                    Button(
                        onClick = {
                            result = RideCalculator.calculate(
                                RideOffer(
                                    fare = fare.toNumber(0.0),
                                    pickupDistanceKm = pickupKm.toNumber(0.0),
                                    tripDistanceKm = tripKm.toNumber(0.0),
                                    pickupMinutes = pickupMinutes.toIntOrNull() ?: 0,
                                    tripMinutes = tripMinutes.toIntOrNull() ?: 0,
                                    productName = "Moto"
                                ),
                                settings
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
                    ) {
                        Icon(Icons.Rounded.Calculate, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Calcular corrida", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item {
            CostBreakdownCard(result)
        }
    }
}

@Composable
private fun ResultPreviewCard(result: RideResult) {
    val accent = ratingColor(result.rating)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(3.dp, accent),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = accent, shape = RoundedCornerShape(99.dp)) {
                    Text(
                        ratingLabel(result.rating),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp
                    )
                }
                Spacer(Modifier.width(9.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("RotaLucro", fontWeight = FontWeight.ExtraBold)
                    Text(result.activeThreshold.name, color = MutedText, fontSize = 11.sp)
                }
                Text("BOX", color = MutedText, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                OverlayMetric(money(result.grossPerKm), "POR KM", accent, Modifier.weight(1f))
                OverlayMetric(money(result.grossPerHour), "POR HORA", SlateText, Modifier.weight(1f))
                OverlayMetric(
                    money(result.estimatedProfit),
                    "LUCRO EST.",
                    if (result.estimatedProfit >= 0) BrandGreen else BrandRed,
                    Modifier.weight(1f)
                )
            }

            HorizontalDivider(color = BorderColor)
            Text(
                "${money(result.fare)}  •  ${result.totalDistanceKm.oneDecimal()} km  •  ${result.totalMinutes} min",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp
            )
            Text(
                "Ruim < ${money(result.activeThreshold.minimumPerKm)}  •  Média até ${money(result.activeThreshold.excellentPerKm)}  •  Ótima ≥ ${money(result.activeThreshold.excellentPerKm)}",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = MutedText,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun OverlayMetric(
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 1)
        Text(label, color = MutedText, fontWeight = FontWeight.Bold, fontSize = 9.sp)
    }
}

@Composable
private fun CostBreakdownCard(result: RideResult) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.DirectionsCar, contentDescription = null, tint = BrandBlue)
                Spacer(Modifier.width(8.dp))
                Text("Estimativa de custos", fontWeight = FontWeight.ExtraBold)
            }
            DetailRow("Combustível", money(result.fuelCost))
            DetailRow("Manutenção", money(result.maintenanceCost))
            HorizontalDivider(color = BorderColor)
            DetailRow("Custo total", money(result.estimatedCost), bold = true)
            DetailRow("Lucro estimado", money(result.estimatedProfit), bold = true, valueColor = if (result.estimatedProfit >= 0) BrandGreen else BrandRed)
        }
    }
}

@Composable
private fun SettingsScreen(
    fuelPrice: String,
    kmPerLiter: String,
    maintenancePerKm: String,
    overlayTimeout: String,
    onFuelPriceChange: (String) -> Unit,
    onKmPerLiterChange: (String) -> Unit,
    onMaintenanceChange: (String) -> Unit,
    onOverlayTimeoutChange: (String) -> Unit,
    onSave: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionHeader("Ajustes", "Personalize os custos e o tempo de exibição do box.")
        }
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.DirectionsCar, contentDescription = null, tint = BrandBlue)
                        Spacer(Modifier.width(8.dp))
                        Text("Custos da moto", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                    }
                    DecimalField("Preço do combustível por litro", fuelPrice, Modifier.fillMaxWidth(), onFuelPriceChange)
                    DecimalField("Consumo médio (km/l)", kmPerLiter, Modifier.fillMaxWidth(), onKmPerLiterChange)
                    DecimalField("Manutenção por km", maintenancePerKm, Modifier.fillMaxWidth(), onMaintenanceChange)
                    Text(
                        "O lucro estimado desconta combustível e manutenção de todos os quilômetros da oferta.",
                        color = MutedText,
                        fontSize = 12.sp
                    )
                }
            }
        }
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Comportamento do box", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                    IntegerField(
                        "Ocultar automaticamente após (8 a 45 segundos)",
                        overlayTimeout,
                        Modifier.fillMaxWidth(),
                        onOverlayTimeoutChange
                    )
                    Text(
                        "O box aparece no topo para não cobrir o botão Aceitar da 99 e pode ser fechado no ×.",
                        color = MutedText,
                        fontSize = 12.sp
                    )
                }
            }
        }
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF2FF)),
                border = BorderStroke(1.dp, Color(0xFFC7D2FE))
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Info, contentDescription = null, tint = BrandBlue)
                        Spacer(Modifier.width(8.dp))
                        Text("Privacidade e segurança", fontWeight = FontWeight.ExtraBold)
                    }
                    Text(
                        "O RotaLucro usa a acessibilidade somente para identificar valor, tempo e distância na oferta da 99.",
                        fontSize = 13.sp
                    )
                    Text(
                        "Ele não toca no botão Aceitar, não recusa corridas e não salva endereços, nomes ou avaliações.",
                        color = MutedText,
                        fontSize = 12.sp
                    )
                }
            }
        }
        item {
            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
            ) {
                Icon(Icons.Rounded.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Salvar ajustes", fontWeight = FontWeight.Bold)
            }
        }
        item {
            Text(
                "RotaLucro 0.4.0 • versão de testes",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = MutedText,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    bold: Boolean = false,
    valueColor: Color = SlateText
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f), color = if (bold) SlateText else MutedText, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(value, color = valueColor, fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.SemiBold)
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = SlateText)
        Text(subtitle, color = MutedText, fontSize = 13.sp, lineHeight = 18.sp)
    }
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
        label = { Text(label, fontSize = 12.sp) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
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
        onValueChange = { onValueChange(it.filter(Char::isDigit).take(3)) },
        label = { Text(label, fontSize = 12.sp) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
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
        label = { Text(label, fontSize = 12.sp) },
        placeholder = { Text("18:00") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    )
}

private fun ratingColor(rating: OfferRating): Color = when (rating) {
    OfferRating.GOOD -> BrandGreen
    OfferRating.ATTENTION -> BrandAmber
    OfferRating.BAD -> BrandRed
}

private fun ratingLabel(rating: OfferRating): String = when (rating) {
    OfferRating.GOOD -> "ÓTIMA"
    OfferRating.ATTENTION -> "MÉDIA"
    OfferRating.BAD -> "RUIM"
}

private fun String.toNumber(default: Double): Double =
    trim().replace(",", ".").toDoubleOrNull() ?: default

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
private fun money(value: Double): String = NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(value)
