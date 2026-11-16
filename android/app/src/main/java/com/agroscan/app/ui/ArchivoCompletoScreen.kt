package com.agroscan.app.ui

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import com.agroscan.app.data.EvaluacionHistorial
import com.agroscan.app.data.LoteItem
import com.agroscan.app.data.esMuestraNoValida
import com.agroscan.app.ui.theme.*
import com.agroscan.app.util.PdfReportGenerator
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivoCompletoScreen(
    historial: List<EvaluacionHistorial>,
    lotes: List<LoteItem>,
    userName: String,
    onVerReporteDetalle: (EvaluacionHistorial) -> Unit,
    onVolver: () -> Unit,
    onMoverAPapelera: (EvaluacionHistorial, String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    var evaluacionAEliminar by remember { mutableStateOf<EvaluacionHistorial?>(null) }

    var busqueda by remember { mutableStateOf("") }
    var filtroLote by remember { mutableStateOf("Todos") }
    var filtroSemaforo by remember { mutableStateOf("Todos") } // "Todos", "Óptimo", "Revisión", "Crítico"
    var filtroFechaModo by remember { mutableStateOf("Todos") } // "Todos", "Hoy", "Última semana", "Este mes", "Manual"
    var fechaManualSeleccionada by remember { mutableStateOf<String?>(null) } // "dd/MM/yyyy"

    // Formateadores de fecha para filtros
    val sdfDia = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    val sdfMes = remember { SimpleDateFormat("MM/yyyy", Locale.getDefault()) }

    // Función para abrir DatePickerDialog
    fun abrirSelectorFecha() {
        val calendario = Calendar.getInstance()
        val datePicker = DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val calSel = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                }
                fechaManualSeleccionada = sdfDia.format(calSel.time)
                filtroFechaModo = "Manual"
            },
            calendario.get(Calendar.YEAR),
            calendario.get(Calendar.MONTH),
            calendario.get(Calendar.DAY_OF_MONTH)
        )
        datePicker.show()
    }

    // Filtrado reactivo de evaluaciones
    val historialFiltrado = remember(historial, busqueda, filtroLote, filtroSemaforo, filtroFechaModo, fechaManualSeleccionada) {
        val hoyStr = sdfDia.format(Date())
        val mesStr = sdfMes.format(Date())

        // Timestamp de hace 7 días
        val hace7Dias = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }.time

        historial.filter { item ->
            // 1. Filtro por texto
            val coincideTexto = busqueda.isBlank() || (
                item.loteNombre.contains(busqueda, ignoreCase = true) ||
                item.variedad.contains(busqueda, ignoreCase = true) ||
                item.fechaHora.contains(busqueda, ignoreCase = true) ||
                item.resultado.dictamen.contains(busqueda, ignoreCase = true) ||
                item.resultado.defectosDetectados.contains(busqueda, ignoreCase = true)
            )

            // 2. Filtro por lote
            val coincideLote = (filtroLote == "Todos" || item.loteNombre.contains(filtroLote, ignoreCase = true))

            // 3. Filtro por semáforo
            val sem = item.resultado.semaforo.lowercase()
            val coincideSemaforo = when (filtroSemaforo) {
                "Óptimo" -> sem == "verde"
                "Revisión" -> sem == "amarillo"
                "Crítico" -> sem == "rojo"
                else -> true
            }

            // 4. Filtro por fecha
            val coincideFecha = when (filtroFechaModo) {
                "Hoy" -> item.fechaHora.startsWith(hoyStr)
                "Última semana" -> {
                    try {
                        val fechaItem = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(item.fechaHora.take(10))
                        fechaItem != null && fechaItem.after(hace7Dias)
                    } catch (_: Exception) { true }
                }
                "Este mes" -> item.fechaHora.contains(mesStr)
                "Manual" -> fechaManualSeleccionada != null && item.fechaHora.startsWith(fechaManualSeleccionada!!)
                else -> true
            }

            coincideTexto && coincideLote && coincideSemaforo && coincideFecha
        }
    }

    // Métricas dinámicas en tiempo real del filtro activo
    val total = historialFiltrado.size
    val totalOptimos = historialFiltrado.count { it.resultado.semaforo.equals("verde", ignoreCase = true) }
    val totalRevision = historialFiltrado.count { it.resultado.semaforo.equals("amarillo", ignoreCase = true) }
    val totalDescarte = historialFiltrado.count { it.resultado.semaforo.equals("rojo", ignoreCase = true) && !it.resultado.esMuestraNoValida() }

    val pctOptimos = if (total > 0) (totalOptimos * 100) / total else 0
    val pctRevision = if (total > 0) (totalRevision * 100) / total else 0
    val pctDescarte = if (total > 0) (totalDescarte * 100) / total else 0

    // Lista de lotes disponibles para el selector
    val nombresLotes = remember(lotes, historial) {
        listOf("Todos") + (lotes.map { it.nombre } + historial.map { it.loteNombre }).distinct()
    }

    val textoFiltroFechaActual = when (filtroFechaModo) {
        "Manual" -> fechaManualSeleccionada ?: "Fecha"
        else -> filtroFechaModo
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Archivo Histórico Completo", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextDark)
                        Text("$total evaluaciones encontradas", fontSize = 12.sp, color = TextGray)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = AgroGreenPrimary)
                    }
                },
                actions = {
                    // Botón para exportar PDF formal según filtros activos
                    Button(
                        onClick = {
                            PdfReportGenerator.generarReporteConsolidadoPdf(
                                context = context,
                                evaluador = userName,
                                filtroLote = filtroLote,
                                filtroSemaforo = filtroSemaforo,
                                filtroFecha = textoFiltroFechaActual,
                                evaluaciones = historialFiltrado
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AgroGreenPrimary),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AgroMintBackground)
            )
        },
        containerColor = AgroMintBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(6.dp))

            // 1. BUSCADOR
            OutlinedTextField(
                value = busqueda,
                onValueChange = { busqueda = it },
                placeholder = { Text("Buscar por lote, variedad o dictamen...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextGray) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AgroGreenPrimary,
                    unfocusedBorderColor = AgroCardBorder,
                    focusedContainerColor = AgroCardWhite,
                    unfocusedContainerColor = AgroCardWhite
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 2. FILTROS RÁPIDOS EN UNA SOLA FILA HORIZONTAL CON MENÚS DESPLEGABLES
            var menuLoteAbierto by remember { mutableStateOf(false) }
            var menuCondicionAbierto by remember { mutableStateOf(false) }
            var menuFechaAbierto by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Selector 1: Lote
                Box(modifier = Modifier.weight(1f)) {
                    val loteActivo = filtroLote != "Todos"
                    Surface(
                        onClick = { menuLoteAbierto = true },
                        shape = RoundedCornerShape(12.dp),
                        color = if (loteActivo) SemaforoVerdeFondo else AgroCardWhite,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (loteActivo) SemaforoVerde else AgroCardBorder
                        ),
                        modifier = Modifier.fillMaxWidth().height(38.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val textoLote = if (filtroLote == "Todos") "Lote: Todos" else "Lote: ${filtroLote.take(7)}.."
                            Text(
                                text = textoLote,
                                fontSize = 11.sp,
                                fontWeight = if (loteActivo) FontWeight.Bold else FontWeight.Medium,
                                color = if (loteActivo) SemaforoVerde else TextDark,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = if (loteActivo) SemaforoVerde else TextGray,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = menuLoteAbierto,
                        onDismissRequest = { menuLoteAbierto = false },
                        modifier = Modifier.background(AgroCardWhite)
                    ) {
                        Text(
                            text = "Filtrar por Lote:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextGray,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                        HorizontalDivider(thickness = 0.5.dp, color = AgroCardBorder)
                        nombresLotes.forEach { loteNom ->
                            val sel = (filtroLote == loteNom)
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (sel) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = AgroGreenPrimary, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                        }
                                        Text(
                                            text = loteNom,
                                            fontSize = 12.sp,
                                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                            color = if (sel) AgroGreenPrimary else TextDark
                                        )
                                    }
                                },
                                onClick = {
                                    filtroLote = loteNom
                                    menuLoteAbierto = false
                                }
                            )
                        }
                    }
                }

                // Selector 2: Condición
                Box(modifier = Modifier.weight(1f)) {
                    val condActiva = filtroSemaforo != "Todos"
                    val (colorFondoCond, colorTextoCond) = when (filtroSemaforo) {
                        "Óptimo" -> Pair(SemaforoVerdeFondo, SemaforoVerde)
                        "Revisión" -> Pair(SemaforoAmarilloFondo, SemaforoAmarillo)
                        "Crítico" -> Pair(SemaforoRojoFondo, SemaforoRojo)
                        else -> Pair(AgroCardWhite, TextDark)
                    }

                    Surface(
                        onClick = { menuCondicionAbierto = true },
                        shape = RoundedCornerShape(12.dp),
                        color = colorFondoCond,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (condActiva) colorTextoCond else AgroCardBorder
                        ),
                        modifier = Modifier.fillMaxWidth().height(38.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (filtroSemaforo == "Todos") "Condición: Todos" else filtroSemaforo,
                                fontSize = 11.sp,
                                fontWeight = if (condActiva) FontWeight.Bold else FontWeight.Medium,
                                color = colorTextoCond,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = if (condActiva) colorTextoCond else TextGray,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = menuCondicionAbierto,
                        onDismissRequest = { menuCondicionAbierto = false },
                        modifier = Modifier.background(AgroCardWhite)
                    ) {
                        Text(
                            text = "Filtrar por Condición:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextGray,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                        HorizontalDivider(thickness = 0.5.dp, color = AgroCardBorder)
                        listOf("Todos", "Óptimo", "Revisión", "Crítico").forEach { estado ->
                            val sel = (filtroSemaforo == estado)
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        when (estado) {
                                            "Óptimo" -> Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(SemaforoVerde))
                                            "Revisión" -> Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(SemaforoAmarillo))
                                            "Crítico" -> Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(SemaforoRojo))
                                            else -> {}
                                        }
                                        if (estado != "Todos") Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = estado,
                                            fontSize = 12.sp,
                                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                            color = if (sel) AgroGreenPrimary else TextDark
                                        )
                                        if (sel) {
                                            Spacer(modifier = Modifier.weight(1f))
                                            Icon(Icons.Default.Check, contentDescription = null, tint = AgroGreenPrimary, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                },
                                onClick = {
                                    filtroSemaforo = estado
                                    menuCondicionAbierto = false
                                }
                            )
                        }
                    }
                }

                // Selector 3: Fecha
                Box(modifier = Modifier.weight(1f)) {
                    val fechaActiva = filtroFechaModo != "Todos"
                    Surface(
                        onClick = { menuFechaAbierto = true },
                        shape = RoundedCornerShape(12.dp),
                        color = if (fechaActiva) SemaforoVerdeFondo else AgroCardWhite,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (fechaActiva) SemaforoVerde else AgroCardBorder
                        ),
                        modifier = Modifier.fillMaxWidth().height(38.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val textoFechaBoton = when (filtroFechaModo) {
                                "Manual" -> fechaManualSeleccionada ?: "Fecha"
                                "Última semana" -> "Semana"
                                "Este mes" -> "Mes"
                                else -> "Fecha: Todos"
                            }
                            Text(
                                text = textoFechaBoton,
                                fontSize = 11.sp,
                                fontWeight = if (fechaActiva) FontWeight.Bold else FontWeight.Medium,
                                color = if (fechaActiva) SemaforoVerde else TextDark,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = if (fechaActiva) SemaforoVerde else TextGray,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = menuFechaAbierto,
                        onDismissRequest = { menuFechaAbierto = false },
                        modifier = Modifier.background(AgroCardWhite)
                    ) {
                        Text(
                            text = "Filtrar por Fecha:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextGray,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                        HorizontalDivider(thickness = 0.5.dp, color = AgroCardBorder)
                        listOf("Todos", "Hoy", "Última semana", "Este mes").forEach { fModo ->
                            val sel = (filtroFechaModo == fModo)
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (sel) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = AgroGreenPrimary, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                        }
                                        Text(
                                            text = fModo,
                                            fontSize = 12.sp,
                                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                            color = if (sel) AgroGreenPrimary else TextDark
                                        )
                                    }
                                },
                                onClick = {
                                    filtroFechaModo = fModo
                                    fechaManualSeleccionada = null
                                    menuFechaAbierto = false
                                }
                            )
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = AgroCardBorder)
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = AgroGreenPrimary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Elegir día en calendario...", fontSize = 12.sp, color = AgroGreenPrimary, fontWeight = FontWeight.Bold)
                                }
                            },
                            onClick = {
                                menuFechaAbierto = false
                                abrirSelectorFecha()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. TARJETA RESUMEN DINÁMICO EN TIEMPO REAL
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = AgroCardWhite,
                border = androidx.compose.foundation.BorderStroke(1.dp, AgroCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Muestras Filtradas", fontSize = 11.sp, color = TextGray)
                        Text("$total análisis", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextDark)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(SemaforoVerde))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("$pctOptimos%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SemaforoVerde)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(SemaforoAmarillo))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("$pctRevision%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SemaforoAmarillo)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(SemaforoRojo))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("$pctDescarte%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SemaforoRojo)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 4. LISTA COMPLETA DE REGISTROS
            if (historialFiltrado.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.FilterListOff, contentDescription = null, tint = TextGray, modifier = Modifier.size(42.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No hay análisis que coincidan con los filtros", color = TextGray, fontSize = 13.sp)
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(historialFiltrado) { evaluacion ->
                        val (colorBadge, fondoBadge, labelBadge) = when {
                            evaluacion.resultado.esMuestraNoValida() -> Triple(NeutralGris, NeutralGrisFondo, "No válida")
                            evaluacion.resultado.semaforo.lowercase() == "rojo" -> Triple(SemaforoRojo, SemaforoRojoFondo, "Descarte")
                            evaluacion.resultado.semaforo.lowercase() == "amarillo" -> Triple(SemaforoAmarillo, SemaforoAmarilloFondo, "Revisión")
                            else -> Triple(SemaforoVerde, SemaforoVerdeFondo, "Óptimo")
                        }

                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = AgroCardWhite),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AgroCardBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onVerReporteDetalle(evaluacion) }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val nombreCortoLote = remember(evaluacion.loteNombre) {
                                        val sinBullet = evaluacion.loteNombre.substringBefore("•").trim()
                                        if (sinBullet.count { it == '(' } > 1) {
                                            sinBullet.substringBeforeLast("(").trim()
                                        } else {
                                            sinBullet
                                        }
                                    }

                                    Text(
                                        text = nombreCortoLote,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextDark,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = fondoBadge,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, colorBadge.copy(alpha = 0.35f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(colorBadge))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = labelBadge,
                                                color = colorBadge,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val bitmapMuestra = remember(evaluacion.id) {
                                        evaluacion.bitmap ?: if (!evaluacion.fotoPath.isNullOrBlank()) {
                                            try {
                                                val f = File(evaluacion.fotoPath)
                                                if (f.exists()) android.graphics.BitmapFactory.decodeFile(f.absolutePath) else null
                                            } catch (_: Exception) { null }
                                        } else null
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(AgroMintBackground)
                                            .border(1.dp, AgroCardBorder, RoundedCornerShape(12.dp)),
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
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${evaluacion.variedad} • ${evaluacion.fechaHora}",
                                            fontSize = 10.5.sp,
                                            color = TextGray
                                        )
                                        Text(
                                            text = "Madurez: ${evaluacion.resultado.madurezPorcentaje}%",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (evaluacion.resultado.madurezPorcentaje >= 80) SemaforoVerde else SemaforoAmarillo
                                        )
                                        Text(
                                            text = evaluacion.resultado.dictamen,
                                            fontSize = 10.5.sp,
                                            color = TextDark,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(6.dp))

                                    // Acciones: Descargar PDF (arriba) y Mover a papelera (al pie)
                                    BotonesAccionEvaluacion(
                                        onDescargar = {
                                            PdfReportGenerator.generarYDescargarPdf(
                                                context = context,
                                                evaluador = userName,
                                                evaluacion = evaluacion
                                            )
                                        },
                                        onEliminar = { evaluacionAEliminar = evaluacion },
                                        tamano = 32.dp
                                    )
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }

        evaluacionAEliminar?.let { ev ->
            ConfirmarPapeleraDialog(
                evaluacion = ev,
                onConfirmar = { motivo ->
                    onMoverAPapelera(ev, motivo)
                    evaluacionAEliminar = null
                },
                onCancelar = { evaluacionAEliminar = null }
            )
        }
    }
}
