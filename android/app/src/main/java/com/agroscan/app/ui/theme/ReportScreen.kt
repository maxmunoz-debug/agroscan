package com.agroscan.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.window.Dialog
import com.agroscan.app.data.AgroScanResult
import com.agroscan.app.data.EvaluacionHistorial
import com.agroscan.app.data.LoteItem
import com.agroscan.app.data.esMuestraNoValida
import com.agroscan.app.ui.theme.*
import com.agroscan.app.util.PdfReportGenerator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    result: AgroScanResult,
    imageBitmap: Bitmap?,
    lotes: List<LoteItem>,
    loteInicial: String = "",
    userName: String = "Evaluador",
    onGuardarEvaluacion: (lote: LoteItem, variedad: String, notas: String) -> Unit,
    onVolver: () -> Unit,
    // Modo detalle: el diagnóstico se abrió desde el Historial (ya está guardado → sin botón Guardar)
    evaluacionGuardada: EvaluacionHistorial? = null,
    onTomarOtraFoto: () -> Unit = onVolver
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var showDialogGuardar by remember { mutableStateOf(false) }
    val esModoDetalle = evaluacionGuardada != null
    val muestraNoValida = remember(result) { result.esMuestraNoValida() }

    // Buscar si ya venía un lote seleccionado o tomar el primero registrado
    val lotePorDefecto = lotes.find { loteInicial.contains(it.nombre) } ?: lotes.firstOrNull()
    var loteSeleccionado by remember { mutableStateOf(lotePorDefecto) }
    var variedadSeleccionada by remember { mutableStateOf(lotePorDefecto?.variedad ?: "Albión") }
    var notasEvaluador by remember { mutableStateOf("") }

    // Sincronizar si cambia la lista de lotes
    LaunchedEffect(lotes, loteInicial) {
        if (loteSeleccionado == null || !lotes.any { it.id == loteSeleccionado?.id }) {
            val nuevo = lotes.find { loteInicial.contains(it.nombre) } ?: lotes.firstOrNull()
            loteSeleccionado = nuevo
            if (nuevo != null) {
                variedadSeleccionada = nuevo.variedad
            }
        }
    }

    // Colores según semáforo devuelto por la IA (neutral si la imagen no es una fresa)
    val (colorSemaforo, colorFondo, textoSemaforo) = when {
        muestraNoValida -> Triple(NeutralGris, NeutralGrisFondo, "MUESTRA NO VÁLIDA • NO ES UNA FRESA")
        result.semaforo.lowercase() == "rojo" -> Triple(SemaforoRojo, SemaforoRojoFondo, "ROJO - DESCARTE INMEDIATO")
        result.semaforo.lowercase() == "amarillo" -> Triple(SemaforoAmarillo, SemaforoAmarilloFondo, "AMARILLO - ATENCIÓN / PULPA")
        else -> Triple(SemaforoVerde, SemaforoVerdeFondo, "VERDE - ÓPTIMO / EXPORTACIÓN")
    }

    // Subtítulo: en modo detalle se muestran los datos del registro guardado
    val subtituloTopBar = if (evaluacionGuardada != null) {
        "${evaluacionGuardada.loteNombre} • ${evaluacionGuardada.fechaHora}"
    } else if (loteSeleccionado != null) {
        "${loteSeleccionado!!.nombre} • Fresa $variedadSeleccionada"
    } else {
        "Fresa $variedadSeleccionada"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Diagnóstico AgroScan", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = subtituloTopBar,
                            fontSize = 12.sp,
                            color = TextGray,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
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
                .verticalScroll(scrollState)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Badge del Semáforo Principal
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colorFondo,
                border = androidx.compose.foundation.BorderStroke(1.dp, colorSemaforo.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(colorSemaforo)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = textoSemaforo,
                        color = colorSemaforo,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            // 2. FOTO + ESPECIFICACIONES BÁSICAS
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = AgroCardWhite),
                border = androidx.compose.foundation.BorderStroke(1.dp, AgroCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(AgroMintBackground)
                            .border(1.dp, AgroCardBorder, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (imageBitmap != null) {
                            Image(
                                bitmap = imageBitmap.asImageBitmap(),
                                contentDescription = "Fresa analizada",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Eco,
                                contentDescription = "Foto",
                                tint = AgroGreenPrimary,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = result.fruta,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (muestraNoValida) TextDark else AgroGreenPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Madurez: ${result.madurezPorcentaje}%",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextDark
                        )
                        Text(
                            text = "Color: ${result.colorPredominante}",
                            fontSize = 12.sp,
                            color = TextGray
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = colorFondo
                        ) {
                            Text(
                                text = if (muestraNoValida) "Muestra no válida" else result.dictamen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorSemaforo,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // 3. Diagnóstico Fitosanitario (Hongos / Defectos)
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = AgroCardWhite),
                border = androidx.compose.foundation.BorderStroke(1.dp, AgroCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Coronavirus,
                            contentDescription = "Patógeno",
                            tint = when {
                                muestraNoValida -> NeutralGris
                                result.semaforo == "rojo" -> SemaforoRojo
                                else -> AgroGreenPrimary
                            },
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Diagnóstico Fitosanitario",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextDark
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = result.defectosDetectados,
                        fontSize = 13.sp,
                        color = TextDark,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Dictamen agronómico: ${result.recomendacion}",
                        fontSize = 12.sp,
                        color = TextGray,
                        lineHeight = 16.sp
                    )
                }
            }

            if (muestraNoValida) {
                // 4-B. GUÍA DE CAPTURA (en lugar de fungicidas cuando la imagen no es una fresa)
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = AgroCardWhite),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AgroCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Guía de captura",
                                tint = AgroGreenPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Guía de Captura",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = TextDark
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "No se aplican tratamientos porque la imagen no corresponde a una fresa. Para un diagnóstico válido:",
                            fontSize = 12.sp,
                            color = TextGray,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        listOf(
                            "Enfoca una sola fresa real (Fragaria x ananassa), ocupando la mayor parte del recuadro.",
                            "Usa luz natural y evita sombras fuertes o contraluz.",
                            "Mantén el celular estable a unos 15–25 cm del fruto.",
                            "Evita fotografiar pantallas, dibujos o imágenes impresas."
                        ).forEach { consejo ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = AgroGreenPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = consejo, fontSize = 12.sp, color = TextDark, lineHeight = 16.sp)
                            }
                        }
                    }
                }
            } else {
            // 4. TRATAMIENTO Y FUNGICIDAS RECOMENDADOS
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = AgroCardWhite),
                border = androidx.compose.foundation.BorderStroke(1.dp, AgroCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Medication,
                            contentDescription = "Fungicidas",
                            tint = AgroGreenPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Fungicidas y Tratamientos",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextDark
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    result.fungicidasSugeridos.forEach { fungicida ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "OK",
                                tint = SemaforoVerde,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = fungicida, fontSize = 12.sp, color = TextDark)
                        }
                    }
                }
            }

            // 5. Medidas Preventivas en Campo
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = AgroCardWhite),
                border = androidx.compose.foundation.BorderStroke(1.dp, AgroCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Prevención",
                            tint = AgroGreenPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Medidas Preventivas de Manejo",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextDark
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    result.medidasPreventivas.forEach { medida ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text("•", fontSize = 14.sp, color = AgroGreenPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = medida, fontSize = 12.sp, color = TextDark, lineHeight = 16.sp)
                        }
                    }
                }
            }

            } // fin de tarjetas agronómicas (solo para fresas válidas)

            // 6. BOTONES DE ACCIÓN (sin guardado automático: solo se guarda al tocar "Guardar")
            if (esModoDetalle) {
                // Registro ya guardado → solo lectura: aviso + exportar PDF
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = AgroCardWhite,
                    border = androidx.compose.foundation.BorderStroke(1.dp, AgroCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Verified, contentDescription = null, tint = AgroGreenPrimary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Registro guardado en el historial",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextDark
                            )
                            if (!evaluacionGuardada?.notas.isNullOrBlank()) {
                                Text(
                                    text = "Notas: ${evaluacionGuardada?.notas}",
                                    fontSize = 11.5.sp,
                                    color = TextGray
                                )
                            }
                        }
                    }
                }
            } else if (muestraNoValida) {
                // Imagen no es una fresa → no tiene sentido guardarla en el historial fitosanitario
                Button(
                    onClick = onTomarOtraFoto,
                    colors = ButtonDefaults.buttonColors(containerColor = AgroGreenPrimary),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tomar otra foto", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onVolver,
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, AgroCardBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextGray),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Descartar y volver al inicio", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Button(
                    onClick = { showDialogGuardar = true },
                    colors = ButtonDefaults.buttonColors(containerColor = AgroGreenPrimary),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = "Guardar")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Guardar Evaluación", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }

            // 7. Exportar Ficha Técnica en PDF (no aplica a muestras no válidas recién escaneadas)
            if (esModoDetalle || !muestraNoValida) {
                OutlinedButton(
                    onClick = {
                        val evaluacionPdf = evaluacionGuardada?.let { it.copy(bitmap = it.bitmap ?: imageBitmap) }
                            ?: EvaluacionHistorial(
                                fechaHora = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date()),
                                loteNombre = loteSeleccionado?.nombre ?: "Lote 1",
                                variedad = variedadSeleccionada,
                                notas = notasEvaluador,
                                resultado = result,
                                bitmap = imageBitmap
                            )
                        PdfReportGenerator.generarYDescargarPdf(
                            context = context,
                            evaluador = userName,
                            evaluacion = evaluacionPdf
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, AgroGreenPrimary),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AgroGreenPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = "PDF", tint = AgroGreenPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Exportar Ficha Técnica en PDF", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Descartar explícito (solo para análisis nuevos de fresas válidas)
            if (!esModoDetalle && !muestraNoValida) {
                TextButton(
                    onClick = onVolver,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = TextGray, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Descartar sin guardar", color = TextGray, fontSize = 13.sp)
                }
            }
        }

        // ================= DIÁLOGO INTELIGENTE DE GUARDADO (SINCRONIZADO CON LOTES REALES) =================
        if (showDialogGuardar) {
            var esOtraVariedad by remember { mutableStateOf(false) }
            var otraVariedadInput by remember { mutableStateOf("") }

            Dialog(onDismissRequest = { showDialogGuardar = false }) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = AgroCardWhite,
                    shadowElevation = 8.dp,
                    modifier = Modifier.padding(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Asignar Parcela y Lote",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = AgroGreenPrimary
                        )

                        // 1. Selector de Lotes Reales
                        Text(
                            text = "1. Selecciona el Lote de Cultivo:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextDark
                        )
                        if (lotes.isEmpty()) {
                            Text(
                                text = "No hay lotes activos. Regístralos en la pantalla de Lotes o Ajustes.",
                                fontSize = 12.sp,
                                color = TextGray
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                lotes.forEach { lote ->
                                    val isSelected = (loteSeleccionado?.id == lote.id)
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            loteSeleccionado = lote
                                            variedadSeleccionada = lote.variedad
                                            esOtraVariedad = false
                                        },
                                        label = {
                                            Text(
                                                text = "${lote.nombre} (${lote.ubicacion})",
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        leadingIcon = if (isSelected) {
                                            {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        } else null,
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = AgroGreenPrimary,
                                            selectedLabelColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                            }
                        }

                        // 2. Selector de Variedad de Fresa (Dinámico, sin límites y con opción manual)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "2. Variedad de Fresa:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextDark
                                )
                                if (loteSeleccionado != null) {
                                    Text(
                                        text = "Reg. en lote: ${loteSeleccionado?.variedad}",
                                        fontSize = 11.sp,
                                        color = AgroGreenPrimary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            // Variedades comerciales cultivadas en Perú + las que el usuario haya registrado
                            val variedadesCatalogo = remember(lotes, loteSeleccionado) {
                                val set = linkedSetOf<String>()
                                loteSeleccionado?.variedad?.let { if (it.isNotBlank()) set.add(it) }
                                set.addAll(listOf("Albión", "Monterrey", "San Andreas", "Camarosa", "Portola", "Fronteras", "Ventana", "Aroma"))
                                lotes.forEach { if (it.variedad.isNotBlank()) set.add(it.variedad) }
                                set.toList()
                            }

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(variedadesCatalogo) { variedad ->
                                    val isSelected = (!esOtraVariedad && variedadSeleccionada.equals(variedad, ignoreCase = true))
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            variedadSeleccionada = variedad
                                            esOtraVariedad = false
                                        },
                                        label = { Text(variedad, fontSize = 12.sp) },
                                        leadingIcon = if (isSelected) {
                                            {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        } else null,
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = AgroGreenPrimary,
                                            selectedLabelColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }

                                item {
                                    FilterChip(
                                        selected = esOtraVariedad,
                                        onClick = {
                                            esOtraVariedad = true
                                            if (otraVariedadInput.isNotBlank()) {
                                                variedadSeleccionada = otraVariedadInput
                                            }
                                        },
                                        label = { Text("+ Otra...", fontSize = 12.sp) },
                                        leadingIcon = if (esOtraVariedad) {
                                            {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        } else null,
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = AgroGreenSecondary,
                                            selectedLabelColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                            }

                            // Campo de texto cuando se elige "+ Otra..."
                            if (esOtraVariedad) {
                                OutlinedTextField(
                                    value = otraVariedadInput,
                                    onValueChange = {
                                        otraVariedadInput = it
                                        variedadSeleccionada = it
                                    },
                                    label = { Text("Escribe la variedad personalizada") },
                                    placeholder = { Text("Ej. Sweet Charlie, Rociera, Benicia...") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = AgroGreenPrimary,
                                        unfocusedBorderColor = AgroCardBorder
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // 3. Notas del Evaluador (Opcional)
                        OutlinedTextField(
                            value = notasEvaluador,
                            onValueChange = { notasEvaluador = it },
                            label = { Text("3. Notas de campo (Opcional)") },
                            placeholder = { Text("Ej. Surco 3, maduración rápida, fruta de exportación") },
                            maxLines = 3,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Botones de Acción
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showDialogGuardar = false }) {
                                Text("Cancelar", color = TextGray)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    showDialogGuardar = false
                                    val variedadFinal = if (esOtraVariedad && otraVariedadInput.isNotBlank()) {
                                        otraVariedadInput.trim()
                                    } else {
                                        variedadSeleccionada.ifBlank { loteSeleccionado?.variedad ?: "Albión" }
                                    }
                                    val loteFinal = loteSeleccionado ?: LoteItem(
                                        id = 1,
                                        nombre = "Lote 1",
                                        variedad = variedadFinal,
                                        ubicacion = "Sector General",
                                        estado = "Óptimo",
                                        ultimaEvaluacion = "Hoy"
                                    )
                                    onGuardarEvaluacion(loteFinal, variedadFinal, notasEvaluador)
                                },
                                enabled = (loteSeleccionado != null || lotes.isNotEmpty()),
                                colors = ButtonDefaults.buttonColors(containerColor = AgroGreenPrimary),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Confirmar y Guardar", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
