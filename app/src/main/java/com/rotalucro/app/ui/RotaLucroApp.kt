package com.rotalucro.app.ui

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rotalucro.app.R
import com.rotalucro.app.calculator.DriverSettings
import com.rotalucro.app.calculator.RideCalculator
import com.rotalucro.app.calculator.ScheduledKmThreshold
import com.rotalucro.app.data.OcrDiagnostics
import com.rotalucro.app.data.OcrDiagnosticsStore
import com.rotalucro.app.data.RideHistoryStore
import com.rotalucro.app.data.SettingsStore
import com.rotalucro.app.runtime.RuntimeState
import com.rotalucro.app.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Início", Icons.Rounded.Home),
    RULES("Regras", Icons.Rounded.Tune),
    RIDES("Corridas", Icons.Rounded.ReceiptLong),
    BOX("Box", Icons.Rounded.DashboardCustomize),
    READER("Leitor", Icons.Rounded.Visibility),
    SETTINGS("Ajustes", Icons.Rounded.Settings)
}

@Composable
fun RotaLucroApp(
    onRequestCapture: () -> Unit,
    onStopCapture: () -> Unit,
    onScanNow: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onShowBubble: () -> Unit,
    onHideBubble: () -> Unit,
    onOpenSimulator: () -> Unit,
    onPreviewBox: () -> Unit
) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(Tab.HOME) }
    var diagnostics by remember { mutableStateOf(OcrDiagnosticsStore.load(context)) }
    var settings by remember { mutableStateOf(SettingsStore.load(context)) }
    var history by remember { mutableStateOf(RideHistoryStore.load(context)) }
    var savedMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            diagnostics = OcrDiagnosticsStore.load(context)
            history = RideHistoryStore.load(context)
            delay(850L)
        }
    }
    LaunchedEffect(savedMessage) {
        if (savedMessage != null) {
            delay(1800)
            savedMessage = null
        }
    }

    Scaffold(
        containerColor = AppBackground,
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 10.dp) {
                Tab.entries.filter { it != Tab.READER }.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = BrandBlue,
                            selectedTextColor = BrandBlue,
                            indicatorColor = Color(0xFFEEF4FF)
                        )
                    )
                }
            }
        },
        snackbarHost = {
            if (savedMessage != null) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
                    Surface(shape = RoundedCornerShape(14.dp), color = BrandNavy) {
                        Text(savedMessage!!, color = Color.White, modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp))
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Header(diagnostics)
            when (tab) {
                Tab.HOME -> HomeScreen(
                    diagnostics = diagnostics,
                    settings = settings,
                    onRequestCapture = onRequestCapture,
                    onStopCapture = onStopCapture,
                    onOpenAccessibility = onOpenAccessibility,
                    onShowBubble = onShowBubble,
                    onOpenSimulator = onOpenSimulator,
                    onReader = { tab = Tab.READER }
                )
                Tab.RULES -> RulesScreen(
                    settings = settings,
                    onSettingsChanged = { settings = it },
                    onSave = {
                        SettingsStore.save(context, settings)
                        savedMessage = "Regras salvas"
                    }
                )
                Tab.RIDES -> HistoryScreen(
                    entries = history,
                    onDelete = { id -> RideHistoryStore.delete(context, id); history = RideHistoryStore.load(context) },
                    onClear = { RideHistoryStore.clear(context); history = emptyList(); savedMessage = "Histórico limpo" }
                )
                Tab.BOX -> BoxSettingsScreen(
                    settings = settings,
                    onSettingsChanged = { settings = it },
                    onSave = { SettingsStore.save(context, settings); savedMessage = "Layout do box salvo" },
                    onPreview = { SettingsStore.save(context, settings); onPreviewBox(); savedMessage = "Prévia exibida" }
                )
                Tab.READER -> ReaderScreen(
                    diagnostics = diagnostics,
                    onRequestCapture = onRequestCapture,
                    onStopCapture = onStopCapture,
                    onScanNow = onScanNow,
                    onOpenSimulator = onOpenSimulator
                )
                Tab.SETTINGS -> SettingsScreen(
                    settings = settings,
                    onSettingsChanged = { settings = it },
                    onSave = {
                        SettingsStore.save(context, settings)
                        savedMessage = "Ajustes salvos"
                    },
                    onOpenAccessibility = onOpenAccessibility,
                    onShowBubble = onShowBubble,
                    onHideBubble = onHideBubble
                )
            }
        }
    }
}

@Composable
private fun Header(diagnostics: OcrDiagnostics) {
    Box(
        Modifier.fillMaxWidth().background(
            Brush.horizontalGradient(listOf(Color(0xFF0B1220), Color(0xFF172554), Color(0xFF1D4ED8)))
        ).padding(horizontal = 20.dp, vertical = 17.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(painterResource(R.drawable.ic_launcher_foreground), contentDescription = "RotaLucro", tint = Color.Unspecified, modifier = Modifier.size(44.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("RotaLucro", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Copiloto de rentabilidade", color = Color(0xFFBFDBFE), fontSize = 12.sp)
            }
            StatusPill(
                text = if (diagnostics.captureActive) "OCR ATIVO" else "OCR OFF",
                color = if (diagnostics.captureActive) Color(0xFF22C55E) else Color(0xFF64748B)
            )
        }
    }
}

@Composable
private fun HomeScreen(
    diagnostics: OcrDiagnostics,
    settings: DriverSettings,
    onRequestCapture: () -> Unit,
    onStopCapture: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onShowBubble: () -> Unit,
    onOpenSimulator: () -> Unit,
    onReader: () -> Unit
) {
    val active = settings.activeKmThreshold(RideCalculator.currentMinuteOfDay())
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        CardBlock {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Sistema", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when {
                            !diagnostics.accessibilityConnected -> "Ative a acessibilidade para detectar a 99 e usar a bolha."
                            !diagnostics.captureActive -> "Leitor pronto. Falta autorizar a captura OCR."
                            diagnostics.appDetected == "99" -> "99 detectada. OCR analisando ofertas."
                            else -> "Tudo pronto. Aguardando uma oferta da 99."
                        },
                        color = MutedText,
                        fontSize = 13.sp
                    )
                }
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(
                        if (diagnostics.captureActive && diagnostics.accessibilityConnected) Color(0xFFDCFCE7) else Color(0xFFF1F5F9)
                    ), contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Radar, null, tint = if (diagnostics.captureActive) BrandGreen else MutedText)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniStatus("Acessibilidade", diagnostics.accessibilityConnected)
                MiniStatus("Captura", diagnostics.captureActive)
                MiniStatus("99", diagnostics.appDetected == "99")
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = if (diagnostics.captureActive) onStopCapture else onRequestCapture,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (diagnostics.captureActive) Color(0xFFDC2626) else BrandBlue),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(if (diagnostics.captureActive) Icons.Rounded.StopCircle else Icons.Rounded.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text(if (diagnostics.captureActive) "Desativar OCR" else "Ativar OCR", fontWeight = FontWeight.Bold)
            }
            if (!diagnostics.accessibilityConnected) {
                TextButton(onClick = onOpenAccessibility, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.AccessibilityNew, null)
                    Spacer(Modifier.width(7.dp))
                    Text("Abrir Acessibilidade")
                }
            }
        }

        CardBlock {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Regra ativa agora", color = MutedText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(active.name, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
                Icon(Icons.Rounded.Schedule, null, tint = BrandBlue)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricBox("Mínimo", money(active.minimumPerKm), Color(0xFFFEE2E2), BrandRed, Modifier.weight(1f))
                MetricBox("Média", money(active.middlePerKm), Color(0xFFFEF3C7), Color(0xFFD97706), Modifier.weight(1f))
                MetricBox("Ótima", money(active.excellentPerKm), Color(0xFFDCFCE7), BrandGreen, Modifier.weight(1f))
            }
        }

        if (diagnostics.grossPerKm != null) {
            CardBlock {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Última análise", color = MutedText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("${money(diagnostics.analysisPerKm ?: diagnostics.grossPerKm)}/km", fontSize = 28.sp, fontWeight = FontWeight.Black)
                    }
                    TextButton(onClick = onReader) { Text("Diagnóstico") }
                }
                Text("${money(diagnostics.analysisPerHour ?: diagnostics.grossPerHour ?: 0.0)}/h  •  ${formatTimeAgo(diagnostics.lastReadAt)}", color = MutedText, fontSize = 13.sp)
                if (diagnostics.possibleEmptyReturn) {
                    Spacer(Modifier.height(7.dp))
                    Text("⚠ Retorno vazio considerado: +${one(diagnostics.emptyReturnKm ?: 0.0)} km", color = BrandRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        CardBlock {
            Text("Teste sem usar a 99", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Abra uma oferta simulada. O OCR captura a tela, reconhece valor/km/min e mostra o box flutuante.", color = MutedText, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onOpenSimulator, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Rounded.Science, null)
                Spacer(Modifier.width(8.dp))
                Text("Abrir laboratório OCR")
            }
        }

        CardBlock {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(40.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Bolha flutuante", fontWeight = FontWeight.Bold)
                    Text("Arraste pela tela e toque para abrir os atalhos.", color = MutedText, fontSize = 12.sp)
                }
                TextButton(onClick = onShowBubble) { Text("Mostrar") }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun RulesScreen(settings: DriverSettings, onSettingsChanged: (DriverSettings) -> Unit, onSave: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionTitle("Regras por horário", "A cor do box é definida pelo R$/km da faixa ativa.")
        CardBlock {
            Text("Fora dos horários especiais", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DecimalField("Mínimo R$/km", settings.defaultMinimumPerKm, Modifier.weight(1f)) {
                    onSettingsChanged(settings.copy(defaultMinimumPerKm = it))
                }
                DecimalField("Ótima R$/km", settings.defaultExcellentPerKm, Modifier.weight(1f)) {
                    onSettingsChanged(settings.copy(defaultExcellentPerKm = it))
                }
            }
            Spacer(Modifier.height(8.dp))
            Legend(settings.defaultMinimumPerKm, settings.defaultExcellentPerKm)
        }

        settings.scheduledThresholds.forEachIndexed { index, schedule ->
            ScheduleCard(schedule) { updated ->
                val list = settings.scheduledThresholds.toMutableList()
                list[index] = updated
                onSettingsChanged(settings.copy(scheduledThresholds = list))
            }
        }
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
            Icon(Icons.Rounded.Save, null); Spacer(Modifier.width(8.dp)); Text("Salvar regras", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun ScheduleCard(schedule: ScheduledKmThreshold, onChange: (ScheduledKmThreshold) -> Unit) {
    CardBlock {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(schedule.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(if (schedule.enabled) "Ativa no horário definido" else "Desativada", color = MutedText, fontSize = 12.sp)
            }
            Switch(checked = schedule.enabled, onCheckedChange = { onChange(schedule.copy(enabled = it)) })
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TimeField("Início", schedule.startMinuteOfDay, Modifier.weight(1f)) { onChange(schedule.copy(startMinuteOfDay = it)) }
            TimeField("Fim", schedule.endMinuteOfDay, Modifier.weight(1f)) { onChange(schedule.copy(endMinuteOfDay = it)) }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DecimalField("Mínimo", schedule.minimumPerKm, Modifier.weight(1f)) { onChange(schedule.copy(minimumPerKm = it)) }
            DecimalField("Ótima", schedule.excellentPerKm, Modifier.weight(1f)) { onChange(schedule.copy(excellentPerKm = it)) }
        }
        Spacer(Modifier.height(8.dp))
        Legend(schedule.minimumPerKm, schedule.excellentPerKm)
    }
}

@Composable
private fun ReaderScreen(
    diagnostics: OcrDiagnostics,
    onRequestCapture: () -> Unit,
    onStopCapture: () -> Unit,
    onScanNow: () -> Unit,
    onOpenSimulator: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionTitle("Diagnóstico do leitor", "Veja exatamente onde a leitura está funcionando ou falhando.")
        CardBlock {
            DiagnosticLine("Leitor conectado", diagnostics.accessibilityConnected)
            DiagnosticLine("Captura de tela", diagnostics.captureActive)
            DiagnosticLine("OCR processando", diagnostics.ocrRunning, neutralWhenFalse = true)
            DiagnosticLine("App detectado: ${diagnostics.appDetected}", diagnostics.appDetected == "99" || diagnostics.appDetected == "Simulador", neutralWhenFalse = true)
            Divider(Modifier.padding(vertical = 10.dp))
            Text("Linhas encontradas: ${diagnostics.recognizedLineCount}", fontWeight = FontWeight.SemiBold)
            Text(diagnostics.reason, color = MutedText, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }

        CardBlock {
            Text("TEXTOS ÚTEIS RECONHECIDOS", color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            Spacer(Modifier.height(8.dp))
            if (diagnostics.usefulTexts.isEmpty()) {
                Text("Nenhum ainda.", color = MutedText)
            } else {
                diagnostics.usefulTexts.forEach { Text(it, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp)) }
            }
        }

        CardBlock {
            ParseLine("VALOR", diagnostics.fare?.let(::money))
            ParseLine("COLETA", if (diagnostics.pickupKm != null && diagnostics.pickupMin != null) "${one(diagnostics.pickupKm)} km / ${diagnostics.pickupMin} min" else null)
            ParseLine("VIAGEM", if (diagnostics.tripKm != null && diagnostics.tripMin != null) "${one(diagnostics.tripKm)} km / ${diagnostics.tripMin} min" else null)
            ParseLine("R$/KM OFERTA", diagnostics.grossPerKm?.let { "${money(it)}/km" })
            ParseLine("R$/KM EFETIVO", diagnostics.analysisPerKm?.let { "${money(it)}/km" })
            ParseLine("R$/HORA EFETIVO", diagnostics.analysisPerHour?.let { "${money(it)}/h" })
            if (diagnostics.possibleEmptyReturn) ParseLine("RETORNO VAZIO", diagnostics.emptyReturnKm?.let { "+${one(it)} km" })
            ParseLine("BOX", if (diagnostics.boxDisplayed) "Exibido" else null)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = if (diagnostics.captureActive) onStopCapture else onRequestCapture, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                Text(if (diagnostics.captureActive) "Parar OCR" else "Ativar OCR")
            }
            OutlinedButton(onClick = onScanNow, enabled = diagnostics.captureActive, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                Text("Ler agora")
            }
        }
        OutlinedButton(onClick = onOpenSimulator, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Icon(Icons.Rounded.Science, null); Spacer(Modifier.width(7.dp)); Text("Testar com oferta simulada")
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun SettingsScreen(
    settings: DriverSettings,
    onSettingsChanged: (DriverSettings) -> Unit,
    onSave: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onShowBubble: () -> Unit,
    onHideBubble: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionTitle("Custos e sistema", "Ajuste os custos para melhorar a estimativa de lucro.")
        CardBlock {
            Text("Custos do veículo", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.height(10.dp))
            DecimalField("Combustível R$/L", settings.fuelPricePerLiter, Modifier.fillMaxWidth()) { onSettingsChanged(settings.copy(fuelPricePerLiter = it)) }
            Spacer(Modifier.height(9.dp))
            DecimalField("Consumo km/L", settings.vehicleKmPerLiter, Modifier.fillMaxWidth()) { onSettingsChanged(settings.copy(vehicleKmPerLiter = it)) }
            Spacer(Modifier.height(9.dp))
            DecimalField("Manutenção R$/km", settings.maintenancePerKm, Modifier.fillMaxWidth()) { onSettingsChanged(settings.copy(maintenancePerKm = it)) }
        }
        CardBlock {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Retorno vazio / saída da cidade", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text("Para viagens longas, o app pode considerar a volta sem passageiro na rentabilidade.", color = MutedText, fontSize = 12.sp)
                }
                Switch(checked = settings.emptyReturnEnabled, onCheckedChange = { onSettingsChanged(settings.copy(emptyReturnEnabled = it)) })
            }
            if (settings.emptyReturnEnabled) {
                Spacer(Modifier.height(12.dp))
                DecimalField("Considerar retorno a partir de (km da viagem)", settings.emptyReturnTripKmThreshold, Modifier.fillMaxWidth()) {
                    onSettingsChanged(settings.copy(emptyReturnTripKmThreshold = it.coerceAtLeast(0.1)))
                }
                Spacer(Modifier.height(9.dp))
                DecimalField("Fator da volta vazia (1,0 = 100%)", settings.emptyReturnDistanceFactor, Modifier.fillMaxWidth()) {
                    onSettingsChanged(settings.copy(emptyReturnDistanceFactor = it.coerceIn(0.0, 2.0)))
                }
                Text("Ex.: viagem de 12 km com fator 1,0 adiciona 12 km de retorno à análise. A cor do box passa a usar o R$/km efetivo.", color = MutedText, fontSize = 11.sp, modifier = Modifier.padding(top = 7.dp))
            }
        }
        CardBlock {
            Text("Permissões e bolha", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.height(8.dp))
            Text("A acessibilidade só identifica qual app está em primeiro plano e desenha a bolha/box. Os valores da corrida são lidos pelo OCR local.", color = MutedText, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onOpenAccessibility, modifier = Modifier.fillMaxWidth()) { Text("Abrir Acessibilidade") }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onShowBubble, modifier = Modifier.weight(1f)) { Text("Mostrar bolha") }
                OutlinedButton(onClick = onHideBubble, modifier = Modifier.weight(1f)) { Text("Ocultar bolha") }
            }
        }
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
            Icon(Icons.Rounded.Save, null); Spacer(Modifier.width(8.dp)); Text("Salvar ajustes", fontWeight = FontWeight.Bold)
        }
        Text("Privacidade: o app não salva capturas de tela. O OCR é processado localmente e o diagnóstico guarda apenas números/textos úteis da oferta, não endereços.", color = MutedText, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))
    }
}


@Composable
private fun HistoryScreen(
    entries: List<RideHistoryStore.Entry>,
    onDelete: (Long) -> Unit,
    onClear: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SectionTitle("Corridas aceitas", "Salve pela bolha logo depois de aceitar uma oferta.")
            }
            if (entries.isNotEmpty()) TextButton(onClick = onClear) { Text("Limpar") }
        }
        if (entries.isEmpty()) {
            CardBlock {
                Icon(Icons.Rounded.ReceiptLong, null, tint = BrandBlue, modifier = Modifier.size(34.dp))
                Spacer(Modifier.height(9.dp))
                Text("Nenhuma corrida salva", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Depois de aceitar na 99, toque na bolha RotaLucro e escolha “Salvar última como aceita”. Fazemos assim para não confundir corrida aceita com oferta recusada ou expirada.", color = MutedText, fontSize = 12.sp)
            }
        } else {
            val totalFare = entries.sumOf { it.fare }
            val totalProfit = entries.sumOf { it.estimatedProfit }
            val totalKm = entries.sumOf { it.totalKm }
            CardBlock {
                Text("Resumo", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricBox("Corridas", entries.size.toString(), Color(0xFFEEF4FF), BrandBlue, Modifier.weight(1f))
                    MetricBox("Faturado", money(totalFare), Color(0xFFECFDF5), BrandGreen, Modifier.weight(1f))
                    MetricBox("Km", one(totalKm), Color(0xFFF8FAFC), Color(0xFF475569), Modifier.weight(1f))
                }
                Text("Lucro estimado acumulado: ${money(totalProfit)}", color = MutedText, fontSize = 12.sp, modifier = Modifier.padding(top = 9.dp))
            }
            entries.forEach { item ->
                CardBlock {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(money(item.fare), fontWeight = FontWeight.Black, fontSize = 22.sp)
                            Text(SimpleDateFormat("dd/MM • HH:mm", Locale("pt", "BR")).format(Date(item.acceptedAt)), color = MutedText, fontSize = 11.sp)
                        }
                        Surface(shape = RoundedCornerShape(20.dp), color = when (item.rating) {
                            com.rotalucro.app.calculator.OfferRating.BAD -> Color(0xFFFEE2E2)
                            com.rotalucro.app.calculator.OfferRating.ATTENTION -> Color(0xFFFEF3C7)
                            com.rotalucro.app.calculator.OfferRating.GOOD -> Color(0xFFDCFCE7)
                        }) {
                            Text("${money(item.analysisPerKm)}/km", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("${one(item.totalKm)} km • ${item.totalMin} min • ${money(item.analysisPerHour)}/h • lucro est. ${money(item.estimatedProfit)}", color = MutedText, fontSize = 12.sp)
                    if (item.possibleEmptyReturn) Text("⚠ Análise considerou +${one(item.emptyReturnKm)} km de retorno vazio", color = BrandRed, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 5.dp))
                    TextButton(onClick = { onDelete(item.id) }, contentPadding = PaddingValues(0.dp), modifier = Modifier.align(Alignment.End)) { Text("Excluir") }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun BoxSettingsScreen(
    settings: DriverSettings,
    onSettingsChanged: (DriverSettings) -> Unit,
    onSave: () -> Unit,
    onPreview: () -> Unit
) {
    val bg = safeComposeColor(settings.overlayBackgroundHex, Color.White)
    val fg = safeComposeColor(settings.overlayTextHex, Color(0xFF0F172A))
    val good = safeComposeColor(settings.overlayGoodHex, BrandGreen)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionTitle("Personalizar box", "Escolha posição, tamanho, transparência e cores do aviso sobre a 99.")
        CardBlock {
            Text("Prévia", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.height(10.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)),
                color = bg.copy(alpha = settings.overlayOpacityPercent.coerceIn(35, 100) / 100f),
                border = androidx.compose.foundation.BorderStroke(3.dp, good)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("R$ 1,85/km", color = fg, fontSize = (24 * settings.overlayScalePercent / 100f).sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                        Surface(shape = RoundedCornerShape(20.dp), color = good) { Text("ÓTIMA", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)) }
                    }
                    Text("R$ 52,85/h • 10,0 km • 21 min • R$ 18,50", color = fg.copy(alpha = .72f), fontSize = 12.sp)
                }
            }
        }
        CardBlock {
            Text("Posição e tamanho", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            SliderSetting("Posição vertical", settings.overlayYPercent, 0..75, "%") { onSettingsChanged(settings.copy(overlayYPercent = it)) }
            SliderSetting("Posição horizontal", settings.overlayXPercent, 0..100, "%") { onSettingsChanged(settings.copy(overlayXPercent = it)) }
            SliderSetting("Largura", settings.overlayWidthPercent, 55..100, "%") { onSettingsChanged(settings.copy(overlayWidthPercent = it)) }
            SliderSetting("Tamanho do conteúdo", settings.overlayScalePercent, 75..135, "%") { onSettingsChanged(settings.copy(overlayScalePercent = it)) }
            SliderSetting("Transparência", settings.overlayOpacityPercent, 35..100, "%") { onSettingsChanged(settings.copy(overlayOpacityPercent = it)) }
            IntField("Ocultar após (segundos)", settings.overlayAutoHideSeconds, Modifier.fillMaxWidth(), 5..60) { onSettingsChanged(settings.copy(overlayAutoHideSeconds = it)) }
        }
        CardBlock {
            Text("Cores", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text("Use um preset ou personalize em hexadecimal.", color = MutedText, fontSize = 11.sp, modifier = Modifier.padding(bottom = 9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { onSettingsChanged(settings.copy(overlayBackgroundHex = "#FFFFFF", overlayTextHex = "#0F172A")) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 6.dp)) { Text("Claro", fontSize = 11.sp) }
                OutlinedButton(onClick = { onSettingsChanged(settings.copy(overlayBackgroundHex = "#111827", overlayTextHex = "#F8FAFC")) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 6.dp)) { Text("Escuro", fontSize = 11.sp) }
                OutlinedButton(onClick = { onSettingsChanged(settings.copy(overlayBackgroundHex = "#000000", overlayTextHex = "#FFFFFF", overlayBadHex = "#FF3B30", overlayAttentionHex = "#FFD60A", overlayGoodHex = "#30D158")) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 6.dp)) { Text("Contraste", fontSize = 11.sp) }
            }
            Spacer(Modifier.height(8.dp))
            HexColorField("Fundo", settings.overlayBackgroundHex) { onSettingsChanged(settings.copy(overlayBackgroundHex = it)) }
            Spacer(Modifier.height(8.dp))
            HexColorField("Texto", settings.overlayTextHex) { onSettingsChanged(settings.copy(overlayTextHex = it)) }
            Spacer(Modifier.height(8.dp))
            HexColorField("Ruim", settings.overlayBadHex) { onSettingsChanged(settings.copy(overlayBadHex = it)) }
            Spacer(Modifier.height(8.dp))
            HexColorField("Média", settings.overlayAttentionHex) { onSettingsChanged(settings.copy(overlayAttentionHex = it)) }
            Spacer(Modifier.height(8.dp))
            HexColorField("Ótima", settings.overlayGoodHex) { onSettingsChanged(settings.copy(overlayGoodHex = it)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onPreview, modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(14.dp)) { Text("Testar box") }
            Button(onClick = onSave, modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(14.dp)) { Text("Salvar", fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SliderSetting(label: String, value: Int, range: IntRange, suffix: String, onValue: (Int) -> Unit) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row { Text(label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f)); Text("$value$suffix", color = BrandBlue, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
        Slider(value = value.toFloat(), onValueChange = { onValue(it.toInt()) }, valueRange = range.first.toFloat()..range.last.toFloat())
    }
}

@Composable
private fun HexColorField(label: String, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onValue, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
}

private fun safeComposeColor(raw: String, fallback: Color): Color = runCatching { Color(android.graphics.Color.parseColor(raw)) }.getOrDefault(fallback)

@Composable
private fun CardBlock(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        content = { Column(Modifier.padding(16.dp), content = content) }
    )
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Text(subtitle, color = MutedText, fontSize = 13.sp)
    }
}

@Composable
private fun MiniStatus(label: String, ok: Boolean) {
    Surface(shape = RoundedCornerShape(12.dp), color = if (ok) Color(0xFFECFDF5) else Color(0xFFF1F5F9)) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(if (ok) BrandGreen else Color(0xFF94A3B8)))
            Spacer(Modifier.width(5.dp))
            Text(label, fontSize = 11.sp, color = if (ok) Color(0xFF166534) else MutedText, maxLines = 1)
        }
    }
}

@Composable
private fun MetricBox(label: String, value: String, bg: Color, fg: Color, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(bg).padding(vertical = 10.dp, horizontal = 8.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = fg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text(value, color = fg, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Legend(min: Double, excellent: Double) {
    val middle = (min + excellent) / 2.0
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        LegendChip("< ${money(min)}", "Ruim", Color(0xFFFEE2E2), BrandRed, Modifier.weight(1f))
        LegendChip("${money(min)}–${money(excellent)}", "Média", Color(0xFFFEF3C7), Color(0xFFD97706), Modifier.weight(1f))
        LegendChip("≥ ${money(excellent)}", "Ótima", Color(0xFFDCFCE7), BrandGreen, Modifier.weight(1f))
    }
    Text("Referência central: ${money(middle)}/km", color = MutedText, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun LegendChip(range: String, label: String, bg: Color, fg: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(10.dp)).background(bg).padding(7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = fg, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(range, color = fg, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DiagnosticLine(label: String, ok: Boolean, neutralWhenFalse: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 14.sp)
        Text(
            if (ok) "✓" else if (neutralWhenFalse) "—" else "✕",
            color = if (ok) BrandGreen else if (neutralWhenFalse) MutedText else BrandRed,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
    }
}

@Composable
private fun ParseLine(label: String, value: String?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(78.dp))
        Text(value ?: "Não reconhecido", Modifier.weight(1f), fontWeight = if (value != null) FontWeight.SemiBold else FontWeight.Normal, fontSize = 14.sp)
        Text(if (value != null) "✓" else "✕", color = if (value != null) BrandGreen else BrandRed, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(100.dp), color = color.copy(alpha = .18f)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
            Text(text, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DecimalField(label: String, value: Double, modifier: Modifier, onValue: (Double) -> Unit) {
    var text by remember(value) { mutableStateOf(two(value)) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' }.take(7) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier.onFocusChanged { state ->
            if (!state.isFocused) {
                text.replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }?.let(onValue)
                text = two(text.replace(',', '.').toDoubleOrNull() ?: value)
            }
        },
        shape = RoundedCornerShape(13.dp)
    )
}

@Composable
private fun IntField(label: String, value: Int, modifier: Modifier, range: IntRange, onValue: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it.filter(Char::isDigit).take(3) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.onFocusChanged { state ->
            if (!state.isFocused) {
                val parsed = text.toIntOrNull()?.coerceIn(range) ?: value
                onValue(parsed); text = parsed.toString()
            }
        },
        shape = RoundedCornerShape(13.dp)
    )
}

@Composable
private fun TimeField(label: String, minutes: Int, modifier: Modifier, onValue: (Int) -> Unit) {
    var text by remember(minutes) { mutableStateOf(formatMinutes(minutes)) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it.filter { ch -> ch.isDigit() || ch == ':' }.take(5) },
        label = { Text(label) },
        singleLine = true,
        modifier = modifier.onFocusChanged { state ->
            if (!state.isFocused) {
                val parsed = parseTime(text)
                if (parsed != null) onValue(parsed)
                text = formatMinutes(parsed ?: minutes)
            }
        },
        shape = RoundedCornerShape(13.dp)
    )
}

private fun isAccessibilityEnabled(context: Context): Boolean {
    val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
    return enabled.contains(context.packageName, ignoreCase = true)
}

private fun formatMinutes(minuteOfDay: Int): String = "%02d:%02d".format((minuteOfDay / 60) % 24, minuteOfDay % 60)
private fun parseTime(value: String): Int? {
    val parts = value.split(':')
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h * 60 + m
}
private fun money(value: Double): String = "R$ ${two(value)}"
private fun two(value: Double): String = "%.2f".format(Locale.US, value).replace('.', ',')
private fun one(value: Double): String = "%.1f".format(Locale.US, value).replace('.', ',')
private fun formatTimeAgo(timestamp: Long): String {
    if (timestamp <= 0L) return "sem leitura"
    val delta = System.currentTimeMillis() - timestamp
    return if (delta < 60_000L) "agora" else SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(Date(timestamp))
}
