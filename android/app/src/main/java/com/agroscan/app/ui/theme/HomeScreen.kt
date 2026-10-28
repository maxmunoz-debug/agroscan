package com.agroscan.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agroscan.app.AppScreen
import com.agroscan.app.data.EvaluacionHistorial
import com.agroscan.app.data.LoteItem
import com.agroscan.app.ui.AgroScanBottomBar
import com.agroscan.app.ui.ChatbotDialog
import com.agroscan.app.ui.theme.*

@Composable
fun HomeScreen(
    userName: String,
    parcelaActual: String = "Lote 1 • Fresa Albión",
    historial: List<EvaluacionHistorial> = emptyList(),
    lotes: List<LoteItem> = emptyList(),
    onIniciarEvaluacion: () -> Unit,
    onIrHistorial: () -> Unit = {},
    onIrLotes: () -> Unit,
    onIrAjustes: () -> Unit
) {
    val scrollState = rememberScrollState()
    var showChatbot by remember { mutableStateOf(false) }

    // ================= CÁLCULO DE MÉTRICAS DINÁMICAS (CERO HARDCODING) =================
    val totalEvaluaciones = historial.size
    val optimos = historial.count { it.resultado.semaforo.equals("verde", ignoreCase = true) }
    val revision = historial.count { it.resultado.semaforo.equals("amarillo", ignoreCase = true) }
    val criticos = historial.count { it.resultado.semaforo.equals("rojo", ignoreCase = true) }

    val porcentajeSanos = if (totalEvaluaciones > 0) {
        ((optimos * 100f) / totalEvaluaciones).toInt()
    } else {
        0
    }

    val pctOptimo = if (totalEvaluaciones > 0) (optimos * 100f / totalEvaluaciones) else 0f
    val pctRevision = if (totalEvaluaciones > 0) (revision * 100f / totalEvaluaciones) else 0f
    val pctCritico = if (totalEvaluaciones > 0) (criticos * 100f / totalEvaluaciones) else 0f

    // Consejo agronómico dinámico según evaluaciones reales
    val (tituloConsejo, textoConsejo, colorConsejo, fondoConsejo) = when {
        totalEvaluaciones == 0 -> Quadruple(
            "GUÍA DE MONITOREO INICIAL",
            "Enfoca las fresas a una distancia de 15 a 20 cm bajo buena luz. Realiza el muestreo en las primeras horas para evitar condensación y evaluar Botrytis con precisión.",
            AgroGreenPrimary,
            AgroMintBackground
        )
        criticos > 0 -> Quadruple(
            "ALERTA FITOSANITARIA: BOTRYTIS DETECTADA",
            "Se detectaron $criticos fruto(s) con pudrición gris o descarte. Aplica Switch 62.5 WG (0.8 kg/ha) o Captan inmediatamente, retira restos dañados y ventila los macrotúneles.",
            SemaforoRojo,
            SemaforoRojoFondo
        )
        revision > 0 -> Quadruple(
            "MANEJO PREVENTIVO EN CAMPO",
            "Hay $revision fruto(s) con maduración heterogénea o indicios leves. Se recomienda cosecha selectiva antes del mediodía y aplicación preventiva de biofungicida Bacillus subtilis (Serenade ASO).",
            SemaforoAmarillo,
            SemaforoAmarilloFondo
        )
        else -> Quadruple(
            "CULTIVO EN CONDICIÓN ÓPTIMA",
            "El 100% de las evaluaciones registradas cumplen con estándar de exportación o mercado de primera. Mantener el programa de fertirriego y ventilación regular.",
            SemaforoVerde,
            SemaforoVerdeFondo
        )
    }

    Scaffold(
        containerColor = AgroMintBackground,
        bottomBar = {
            AgroScanBottomBar(
                currentScreen = AppScreen.HOME,
                onNavigate = { destino ->
                    when (destino) {
                        AppScreen.HISTORIAL -> onIrHistorial()
                        AppScreen.LOTES -> onIrLotes()
                        AppScreen.SETTINGS -> onIrAjustes()
                        else -> {}
                    }
                },
                onDispararCamara = onIniciarEvaluacion
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(20.dp)
        ) {
            // 1. Encabezado personalizado con el nombre del evaluador
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "AgroScan",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = AgroGreenPrimary
                    )
                    Text(
                        text = "¡Hola, $userName! 🍓",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                    Text(
                        text = parcelaActual,
                        fontSize = 13.sp,
                        color = TextGray
                    )
                }

                // Monitoreo ambiental / Clima
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = AgroCardWhite,
                    border = androidx.compose.foundation.BorderStroke(1.dp, AgroCardBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.WbSunny,
                            contentDescription = "Clima",
                            tint = SemaforoAmarillo,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "24°C • 62% H.",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextDark
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 2. Tarjeta Principal: NUEVA EVALUACIÓN (Call to action Stitch)
            Card(
                onClick = onIniciarEvaluacion,
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = AgroGreenPrimary),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(AgroGreenSecondary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoCamera,
                                contentDescription = "Cámara",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Nueva Evaluación",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Diagnóstico fitosanitario en surco",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Ir",
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Acceso rápido al Asistente Agrónomo IA (Tecsup)
            Surface(
                onClick = { showChatbot = true },
                shape = RoundedCornerShape(18.dp),
                color = AgroCardWhite,
                border = androidx.compose.foundation.BorderStroke(1.dp, AgroCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(AgroMintBackground),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = null,
                                tint = AgroGreenPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Asistente Agrónomo IA (Tecsup)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextDark
                            )
                            Text(
                                text = "Preguntas en vivo sobre plagas, dosis y fresas",
                                fontSize = 11.sp,
                                color = TextGray
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = TextGray
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 3. Resumen de Evaluaciones Reales (Métricas Dinámicas)
            Text(
                text = "Resumen de Monitoreo",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Card 1: Total Evaluaciones
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = AgroCardWhite),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AgroCardBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "Evaluaciones", fontSize = 12.sp, color = TextGray)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$totalEvaluaciones",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = AgroGreenPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (totalEvaluaciones == 0) "Sin evaluaciones aún" else "$optimos Ópt. • $revision Rev. • $criticos Crít.",
                            fontSize = 11.sp,
                            color = if (totalEvaluaciones == 0) TextGray else SemaforoVerde,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Card 2: Porcentaje de Frutos Sanos
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = AgroCardWhite),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AgroCardBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "Frutos Sanos", fontSize = 12.sp, color = TextGray)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (totalEvaluaciones == 0) "--%" else "$porcentajeSanos%",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (totalEvaluaciones == 0) TextGray else if (porcentajeSanos >= 80) SemaforoVerde else if (porcentajeSanos >= 50) SemaforoAmarillo else SemaforoRojo
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = when {
                                totalEvaluaciones == 0 -> "Listo para escanear"
                                porcentajeSanos >= 85 -> "Cosecha óptima 🟢"
                                porcentajeSanos >= 60 -> "Atención moderada 🟡"
                                else -> "Alerta fitosanitaria 🔴"
                            },
                            fontSize = 11.sp,
                            color = TextGray
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 4. Semáforo de Calidad IA (Calculado en Vivo)
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = AgroCardWhite),
                border = androidx.compose.foundation.BorderStroke(1.dp, AgroCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Analytics,
                                contentDescription = "Semáforo",
                                tint = AgroGreenPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Semáforo de Calidad IA",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextDark
                            )
                        }
                        Text(
                            text = if (totalEvaluaciones == 0) "Esperando datos" else "$totalEvaluaciones muestras",
                            fontSize = 11.sp,
                            color = TextGray
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (totalEvaluaciones == 0) {
                        // Estado cuando no hay evaluaciones todavía
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape)
                                .background(AgroCardBorder)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Aún no hay frutos evaluados. Presiona el botón de cámara central para escanear y generar el balance en tiempo real.",
                            fontSize = 11.sp,
                            color = TextGray
                        )
                    } else {
                        // Barra dividida proporcionalmente según las evaluaciones reales
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(CircleShape)
                        ) {
                            if (pctOptimo > 0f) {
                                Box(
                                    modifier = Modifier
                                        .weight(pctOptimo.coerceAtLeast(0.01f))
                                        .fillMaxHeight()
                                        .background(SemaforoVerde)
                                )
                            }
                            if (pctRevision > 0f) {
                                Box(
                                    modifier = Modifier
                                        .weight(pctRevision.coerceAtLeast(0.01f))
                                        .fillMaxHeight()
                                        .background(SemaforoAmarillo)
                                )
                            }
                            if (pctCritico > 0f) {
                                Box(
                                    modifier = Modifier
                                        .weight(pctCritico.coerceAtLeast(0.01f))
                                        .fillMaxHeight()
                                        .background(SemaforoRojo)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("🟢 ${pctOptimo.toInt()}% Óptimo", fontSize = 11.sp, color = TextDark)
                            Text("🟡 ${pctRevision.toInt()}% Revisar", fontSize = 11.sp, color = TextDark)
                            Text("🔴 ${pctCritico.toInt()}% Alerta", fontSize = 11.sp, color = TextDark)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 5. Consejo Agronómico IA Dinámico
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = fondoConsejo),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.TipsAndUpdates,
                        contentDescription = "Tip",
                        tint = colorConsejo,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = tituloConsejo,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorConsejo
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = textoConsejo,
                            fontSize = 12.sp,
                            color = TextDark,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }

    if (showChatbot) {
        ChatbotDialog(onDismissRequest = { showChatbot = false })
    }
}

// Clase auxiliar para tupla de 4 elementos en Compose
private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
