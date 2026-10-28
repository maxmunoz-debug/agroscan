package com.agroscan.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agroscan.app.AppScreen
import com.agroscan.app.data.EvaluacionHistorial
import com.agroscan.app.ui.theme.*
import com.agroscan.app.util.PdfReportGenerator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorialScreen(
    historial: List<EvaluacionHistorial>,
    userName: String,
    onVerReporteDetalle: (EvaluacionHistorial) -> Unit,
    onDispararCamara: () -> Unit = {},
    onIrInicio: () -> Unit,
    onIrLotes: () -> Unit,
    onIrAjustes: () -> Unit,
    onVolver: () -> Unit
) {
    val context = LocalContext.current
    var busqueda by remember { mutableStateOf("") }
    var filtroSemaforo by remember { mutableStateOf("Todos") } // "Todos", "Verde", "Amarillo", "Rojo"

    // Métricas para el resumen estadístico superior
    val total = historial.size
    val totalOptimos = historial.count { it.resultado.semaforo.lowercase() == "verde" }
    val totalRevision = historial.count { it.resultado.semaforo.lowercase() == "amarillo" }
    val totalDescarte = historial.count { it.resultado.semaforo.lowercase() == "rojo" }

    val pctOptimos = if (total > 0) (totalOptimos * 100) / total else 0
    val pctRevision = if (total > 0) (totalRevision * 100) / total else 0
    val pctDescarte = if (total > 0) (totalDescarte * 100) / total else 0

    // Filtrado de evaluaciones
    val historialFiltrado = historial.filter { item ->
        val coincideTexto = item.loteNombre.contains(busqueda, ignoreCase = true) ||
                item.variedad.contains(busqueda, ignoreCase = true) ||
                item.fechaHora.contains(busqueda, ignoreCase = true) ||
                item.resultado.defectosDetectados.contains(busqueda, ignoreCase = true)

        val coincideSemaforo = when (filtroSemaforo) {
            "Verde" -> item.resultado.semaforo.lowercase() == "verde"
            "Amarillo" -> item.resultado.semaforo.lowercase() == "amarillo"
            "Rojo" -> item.resultado.semaforo.lowercase() == "rojo"
            else -> true
        }

        coincideTexto && coincideSemaforo
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Historial de Inspecciones", fontWeight = FontWeight.Bold, fontSize = 19.sp, color = TextDark)
                        Text("$total evaluaciones fitosanitarias", fontSize = 12.sp, color = TextGray)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = AgroGreenPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AgroMintBackground)
            )
        },
        bottomBar = {
            AgroScanBottomBar(
                currentScreen = AppScreen.HISTORIAL,
                onNavigate = { destino ->
                    when (destino) {
                        AppScreen.HOME -> onIrInicio()
                        AppScreen.LOTES -> onIrLotes()
                        AppScreen.SETTINGS -> onIrAjustes()
                        else -> {}
                    }
                },
                onDispararCamara = onDispararCamara
            )
        },
        containerColor = AgroMintBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // 1. Tarjeta de Métricas y Rendimiento Fitosanitario
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = AgroCardWhite),
                border = androidx.compose.foundation.BorderStroke(1.dp, AgroCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Balance Fitosanitario Global",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Óptimo
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$pctOptimos%", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = SemaforoVerde)
                            Text("Óptimo ($totalOptimos)", fontSize = 11.sp, color = TextGray)
                        }
                        // Revisión
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$pctRevision%", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = SemaforoAmarillo)
                            Text("Revisión ($totalRevision)", fontSize = 11.sp, color = TextGray)
                        }
                        // Descarte
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$pctDescarte%", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = SemaforoRojo)
                            Text("Descarte ($totalDescarte)", fontSize = 11.sp, color = TextGray)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Buscador
            OutlinedTextField(
                value = busqueda,
                onValueChange = { busqueda = it },
                placeholder = { Text("Buscar por lote, fecha o defecto...", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextGray, modifier = Modifier.size(20.dp)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AgroGreenPrimary,
                    unfocusedBorderColor = AgroCardBorder,
                    focusedContainerColor = AgroCardWhite,
                    unfocusedContainerColor = AgroCardWhite
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Chips de Filtro Semáforo
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf("Todos", "Verde", "Amarillo", "Rojo").forEach { filtro ->
                    val seleccionado = (filtroSemaforo == filtro)
                    val labelTexto = when (filtro) {
                        "Verde" -> "Óptimos 🟢"
                        "Amarillo" -> "Revisión 🟡"
                        "Rojo" -> "Descarte 🔴"
                        else -> "Todos"
                    }
                    FilterChip(
                        selected = seleccionado,
                        onClick = { filtroSemaforo = filtro },
                        label = { Text(labelTexto, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AgroGreenPrimary,
                            selectedLabelColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4. Lista de Inspecciones Guardadas
            if (historialFiltrado.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.HistoryEdu,
                            contentDescription = null,
                            tint = TextGray,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (historial.isEmpty()) "Aún no hay inspecciones guardadas" else "No hay resultados para el filtro actual",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextDark
                        )
                        Text(
                            text = "Toma una foto de fresa para analizarla con IA y guardarla aquí.",
                            fontSize = 12.sp,
                            color = TextGray
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(historialFiltrado) { evaluacion ->
                        val (colorBadge, fondoBadge, labelBadge) = when (evaluacion.resultado.semaforo.lowercase()) {
                            "rojo" -> Triple(SemaforoRojo, SemaforoRojoFondo, "Descarte")
                            "amarillo" -> Triple(SemaforoAmarillo, SemaforoAmarilloFondo, "Revisión")
                            else -> Triple(SemaforoVerde, SemaforoVerdeFondo, "Óptimo")
                        }

                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = AgroCardWhite),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AgroCardBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onVerReporteDetalle(evaluacion) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Miniatura de la foto
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(AgroMintBackground)
                                        .border(1.dp, AgroCardBorder, RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (evaluacion.bitmap != null) {
                                        Image(
                                            bitmap = evaluacion.bitmap.asImageBitmap(),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(Icons.Default.Eco, contentDescription = null, tint = AgroGreenPrimary)
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                // Información de la Evaluación
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = evaluacion.loteNombre,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextDark
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = fondoBadge
                                        ) {
                                            Text(
                                                text = labelBadge,
                                                color = colorBadge,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${evaluacion.variedad} • ${evaluacion.fechaHora}",
                                        fontSize = 11.sp,
                                        color = TextGray
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Madurez: ${evaluacion.resultado.madurezPorcentaje}% • ${evaluacion.resultado.defectosDetectados}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (evaluacion.resultado.semaforo.lowercase() == "rojo") SemaforoRojo else TextDark
                                    )
                                }

                                // Botón para descargar PDF directo de esta evaluación
                                IconButton(
                                    onClick = {
                                        PdfReportGenerator.generarYDescargarPdf(
                                            context = context,
                                            evaluador = userName,
                                            evaluacion = evaluacion
                                        )
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PictureAsPdf,
                                        contentDescription = "Descargar PDF",
                                        tint = AgroGreenPrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}