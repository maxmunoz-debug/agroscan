package com.agroscan.app.ui

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextOverflow
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

            // 3. Botones de Filtro Simétricos y 100% Responsive
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val filtros = listOf(
                    Triple("Todos", "Todos", AgroGreenPrimary),
                    Triple("Verde", "Óptimo", SemaforoVerde),
                    Triple("Amarillo", "Revisión", SemaforoAmarillo),
                    Triple("Rojo", "Descarte", SemaforoRojo)
                )
                filtros.forEach { (filtroId, label, dotColor) ->
                    val activo = (filtroSemaforo == filtroId)
                    Surface(
                        onClick = { filtroSemaforo = filtroId },
                        shape = RoundedCornerShape(12.dp),
                        color = if (activo) AgroGreenPrimary else AgroCardWhite,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (activo) AgroGreenPrimary else AgroCardBorder
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (filtroId != "Todos") {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (activo) Color.White else dotColor)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (activo) FontWeight.Bold else FontWeight.Medium,
                                color = if (activo) Color.White else TextDark,
                                maxLines = 1
                            )
                        }
                    }
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
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = AgroCardWhite),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AgroCardBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onVerReporteDetalle(evaluacion) }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp)
                            ) {
                                // FILA 1: Nombre de Parcela + Badge de Estado (Semáforo Protegido)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = evaluacion.loteNombre,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextDark,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = fondoBadge,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, colorBadge.copy(alpha = 0.35f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(7.dp)
                                                    .clip(CircleShape)
                                                    .background(colorBadge)
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text(
                                                text = labelBadge,
                                                color = colorBadge,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // FILA 2: Foto de Muestra + Datos Técnicos + Botón PDF
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Foto de la muestra
                                    val bitmapMuestra = remember(evaluacion.id) {
                                        evaluacion.bitmap ?: if (!evaluacion.fotoPath.isNullOrBlank()) {
                                            try {
                                                val f = java.io.File(evaluacion.fotoPath)
                                                if (f.exists()) android.graphics.BitmapFactory.decodeFile(f.absolutePath) else null
                                            } catch (_: Exception) { null }
                                        } else null
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(AgroMintBackground)
                                            .border(1.dp, AgroCardBorder, RoundedCornerShape(14.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (bitmapMuestra != null) {
                                            Image(
                                                bitmap = bitmapMuestra.asImageBitmap(),
                                                contentDescription = "Muestra de fresa",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Eco,
                                                contentDescription = null,
                                                tint = AgroGreenPrimary,
                                                modifier = Modifier.size(26.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    // Información de Variedad, Fecha, Madurez y Diagnóstico
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${evaluacion.variedad} • ${evaluacion.fechaHora}",
                                            fontSize = 11.sp,
                                            color = TextGray,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "Madurez: ",
                                                fontSize = 12.sp,
                                                color = TextDark,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "${evaluacion.resultado.madurezPorcentaje}%",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = AgroGreenPrimary
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = evaluacion.resultado.defectosDetectados,
                                            fontSize = 11.sp,
                                            color = if (evaluacion.resultado.semaforo.lowercase() == "rojo") SemaforoRojo else TextDark,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    // Botón Descargar PDF Circular y Elegante
                                    Surface(
                                        onClick = {
                                            PdfReportGenerator.generarYDescargarPdf(
                                                context = context,
                                                evaluador = userName,
                                                evaluacion = evaluacion
                                            )
                                                                                    },
                                        shape = CircleShape,
                                        color = AgroMintBackground,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, AgroGreenPrimary.copy(alpha = 0.35f)),
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                            Icon(
                                                imageVector = Icons.Default.PictureAsPdf,
                                                contentDescription = "Descargar PDF",
                                                tint = AgroGreenPrimary,
                                                modifier = Modifier.size(18.dp)
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
    }
}

