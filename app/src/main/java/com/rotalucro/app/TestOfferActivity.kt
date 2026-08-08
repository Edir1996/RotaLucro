package com.rotalucro.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rotalucro.app.runtime.RuntimeState
import com.rotalucro.app.ui.theme.RotaLucroTheme

class TestOfferActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RotaLucroTheme {
                SimulatorScreen(onClose = { finish() })
            }
        }
    }

    override fun onStart() {
        super.onStart()
        RuntimeState.simulatorVisible = true
    }

    override fun onStop() {
        RuntimeState.simulatorVisible = false
        super.onStop()
    }
}

private data class DemoOffer(val label: String, val fare: String, val pickup: String, val trip: String, val surge: String)

@Composable
private fun SimulatorScreen(onClose: () -> Unit) {
    val examples = listOf(
        DemoOffer("Exemplo real", "R$8,40", "6min (2km)", "5min (2,3km)", "1,6x"),
        DemoOffer("Corrida ruim", "R$6,00", "6min (2km)", "12min (4km)", "1,2x"),
        DemoOffer("Corrida média", "R$8,00", "5min (1km)", "10min (4km)", "1,3x"),
        DemoOffer("Corrida ótima", "R$10,00", "4min (1km)", "8min (3km)", "1,8x"),
        DemoOffer("Viagem longa", "R$18,00", "4min (1km)", "18min (9km)", "1,4x")
    )
    var selected by remember { mutableIntStateOf(0) }
    val offer = examples[selected]

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF111827)) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Laboratório OCR", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("Oferta simulada para testar captura + OCR", color = Color(0xFF94A3B8), fontSize = 13.sp)
                    }
                    TextButton(onClick = onClose) { Text("Fechar", color = Color.White) }
                }
                Spacer(Modifier.height(18.dp))

                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    examples.forEachIndexed { index, item ->
                        FilterChip(
                            selected = selected == index,
                            onClick = { selected = index },
                            label = { Text(item.label, maxLines = 1) }
                        )
                    }
                }

                Spacer(Modifier.weight(1f))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2937)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                ) {
                    Column(Modifier.padding(22.dp)) {
                        Text("Moto", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(offer.fare, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 44.sp)
                            Spacer(Modifier.width(18.dp))
                            Text("⚡${offer.surge}", color = Color(0xFFF6C453), fontWeight = FontWeight.Bold, fontSize = 25.sp)
                        }
                        Spacer(Modifier.height(18.dp))
                        Divider(color = Color(0xFF475569))
                        Spacer(Modifier.height(14.dp))
                        Text("0% Taxa de serviço", color = Color(0xFFF6C453), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text("R$1,27 Tarifa base dinâmica incl.", color = Color(0xFFF6C453), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(18.dp))
                        Divider(color = Color(0xFF334155))
                        Spacer(Modifier.height(16.dp))
                        Text("4,86 • 302 corridas • Perfil Premium", color = Color(0xFFE2E8F0), fontSize = 15.sp)
                        Spacer(Modifier.height(18.dp))
                        Text("🟢 ${offer.pickup}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("Rua de teste, 123", color = Color(0xFFCBD5E1), fontSize = 15.sp)
                        Spacer(Modifier.height(14.dp))
                        Text("🟠 ${offer.trip}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("Destino de teste, 721", color = Color(0xFFCBD5E1), fontSize = 15.sp)
                        Spacer(Modifier.height(22.dp))
                        Box(
                            Modifier.fillMaxWidth().height(58.dp)
                                .background(Color(0xFFFFD600), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Aceitar", color = Color.Black, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    "Com o OCR ativo, o RotaLucro deve reconhecer esta tela e exibir o box sem abrir a 99.",
                    color = Color(0xFFCBD5E1), fontSize = 13.sp
                )
                Spacer(Modifier.weight(1f))
            }
        }
    }
}
