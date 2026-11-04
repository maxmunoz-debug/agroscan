package com.agroscan.app.ui

import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import java.io.File

import com.agroscan.app.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.agroscan.app.util.WeatherHelper
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.border

@Composable
fun HomeScreen(
    userName: String,
    parcelaActual: String = "Lote 1 • Fresa Albión",
    historial: List<EvaluacionHistorial> = emptyList(),
    lotes: List<LoteItem> = emptyList(),
    onIniciarEvaluacion: () -> Unit,
    onCambiarParcela: (String) -> Unit = {},
    onIrHistorial: () -> Unit = {},
    onIrLotes: () -> Unit,
    onIrAjustes: () -> Unit,
    mostrarBottomBar: Boolean = true
) {
    val scrollState = rememberScrollState()
    var showChatbot by remember { mutableStateOf(false) }
    var menuLotesDesplegado by remember { mutableStateOf(false) }

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
            if (mostrarBottomBar) {
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
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(20.dp)
        ) {
            // 1. Encabezado Oficial: Logo + Nombre a la izquierda | Perfil interactivo a la derecha
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Izquierda: Logo Oficial del Proyecto + agroScan
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(AgroCardWhite)
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(id = R.drawable.agroscan_logo),
                            contentDescription = "Logo AgroScan",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "agroScan",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AgroGreenPrimary,
                        letterSpacing = (-0.5).sp
                    )
                }

                // Derecha: Foto de Perfil + Nombre (sin roles) -> Tocar lleva a Ajustes/Perfil
                Surface(
                    onClick = onIrAjustes,
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Transparent
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(AgroGreenPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            val context = LocalContext.current
                            val fotoPerfil = remember { java.io.File(context.filesDir, "perfil_usuario.jpg") }
                            if (fotoPerfil.exists()) {
                                Image(
                                    painter = coil.compose.rememberAsyncImagePainter(fotoPerfil),
                                    contentDescription = "Foto de perfil",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                val inicial = if (userName.isNotBlank()) userName.first().uppercase() else "U"
                                Text(
                                    text = inicial,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = userName,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextDark,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Lote Activo en tiempo real
            val loteObjeto = remember(parcelaActual, lotes) {
                lotes.firstOrNull { parcelaActual.contains(it.nombre) } ?: lotes.firstOrNull()
            }
            val latActual = loteObjeto?.latitud ?: -12.046374
            val lonActual = loteObjeto?.longitud ?: -77.042793
            var climaTexto by remember { mutableStateOf("") }
            var cargandoClima by remember { mutableStateOf(false) }

            LaunchedEffect(latActual, lonActual) {
                cargandoClima = true
                val res = WeatherHelper.obtenerClima(latActual, lonActual)
                if (res != null) {
                    climaTexto = "${res.first}°C • ${res.second}% H"
                } else {
                    climaTexto = "22.5°C • 84% H"
                }
                cargandoClima = false
            }

            // 2. Tarjeta Profesional de Campo: Lote Actual + Foto a la Derecha + Clima al Pie
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = AgroGreenPrimary),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    // FILA SUPERIOR: Lote + Selector a la Izquierda | Foto de la Parcela a la Derecha
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { menuLotesDesplegado = true }
                        ) {
                            // Ícono de Ubicación interactivo con menú desplegable
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = "Cambiar Lote",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )

                                DropdownMenu(
                                    expanded = menuLotesDesplegado,
                                    onDismissRequest = { menuLotesDesplegado = false },
                                    modifier = Modifier.background(AgroCardWhite)
                                ) {
                                    Text(
                                        text = "Cambiar Parcela Activa:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextGray,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                    )
                                    HorizontalDivider(thickness = 0.5.dp, color = AgroCardBorder)
                                    lotes.forEach { lote ->
                                        val textoCompleto = "${lote.nombre} (${lote.ubicacion}) • Fresa ${lote.variedad}"
                                        val seleccionado = parcelaActual.contains(lote.nombre)
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Default.Spa,
                                                        contentDescription = null,
                                                        tint = if (seleccionado) SemaforoVerde else TextGray,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Column {
                                                        Text(
                                                            text = "${lote.nombre} - ${lote.variedad}",
                                                            fontWeight = if (seleccionado) FontWeight.Bold else FontWeight.Medium,
                                                            fontSize = 12.sp,
                                                            color = if (seleccionado) AgroGreenPrimary else TextDark
                                                        )
                                                        Text(
                                                            text = lote.ubicacion,
                                                            fontSize = 10.sp,
                                                            color = TextGray
                                                        )
                                                    }
                                                }
                                            },
                                            onClick = {
                                                onCambiarParcela(textoCompleto)
                                                menuLotesDesplegado = false
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = loteObjeto?.nombre ?: parcelaActual,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Desplegar",
                                        tint = Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Text(
                                    text = "${loteObjeto?.ubicacion ?: "Sector General"} • Fresa ${loteObjeto?.variedad ?: "Albión"}",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Toca para cambiar parcela activa",
                                    fontSize = 9.5.sp,
                                    color = Color.White.copy(alpha = 0.65f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Foto de la Parcela a la Derecha
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.15f))
                                .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            val fotoUri = loteObjeto?.fotoUri
                            if (!fotoUri.isNullOrBlank()) {
                                Image(
                                    painter = coil.compose.rememberAsyncImagePainter(fotoUri),
                                    contentDescription = "Foto de la parcela",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Spa,
                                    contentDescription = "Parcela",
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(thickness = 0.5.dp, color = Color.White.copy(alpha = 0.18f))
                    Spacer(modifier = Modifier.height(8.dp))

                    // FILA INFERIOR AL PIE: Clima en Tiempo Real obtenido por API Meteorológica
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.WbSunny,
                                contentDescription = "Clima",
                                tint = SemaforoAmarillo,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (cargandoClima) "Consultando satélite..." else if (climaTexto.isNotBlank()) climaTexto else "22.5°C • 84% H",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Text(
                            text = String.format(java.util.Locale.US, "GPS: %.3f, %.3f", latActual, lonActual),
                            fontSize = 9.5.sp,
                            color = Color.White.copy(alpha = 0.65f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
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
                                porcentajeSanos >= 85 -> "Cosecha óptima"
                                porcentajeSanos >= 60 -> "Atención moderada"
                                else -> "Alerta fitosanitaria"
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
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(SemaforoVerde))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("${pctOptimo.toInt()}% Óptimo", fontSize = 11.sp, color = TextDark, fontWeight = FontWeight.Medium)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(SemaforoAmarillo))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("${pctRevision.toInt()}% Revisar", fontSize = 11.sp, color = TextDark, fontWeight = FontWeight.Medium)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(SemaforoRojo))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("${pctCritico.toInt()}% Alerta", fontSize = 11.sp, color = TextDark, fontWeight = FontWeight.Medium)
                            }
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

