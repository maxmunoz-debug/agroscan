package com.agroscan.app.ui

import android.Manifest
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.agroscan.app.AppScreen
import com.agroscan.app.data.LoteItem
import com.agroscan.app.ui.components.MapaMiniaturaLote
import com.agroscan.app.ui.components.MapaSelectorParcela
import com.agroscan.app.ui.theme.*
import com.agroscan.app.util.GeocodingHelper
import com.agroscan.app.util.GpsLocationHelper
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LotesScreen(
    lotes: MutableList<LoteItem>,
    papeleraLotes: MutableList<LoteItem>,
    loteActivoActual: String,
    onSeleccionarLoteActivo: (nombreCompletoLote: String) -> Unit,
    onIniciarEvaluacionEnLote: (nombreLote: String) -> Unit,
    onLotesCambiados: () -> Unit = {},
    onIrInicio: () -> Unit = {},
    onIrHistorial: () -> Unit = {},
    onIrAjustes: () -> Unit = {},
    onDispararCamara: () -> Unit = {},
    onVolver: () -> Unit = {},
    mostrarBottomBar: Boolean = true
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var busqueda by remember { mutableStateOf("") }

    // Gestor de permisos de ubicación con acción pendiente
    var pendingLocationAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val concedido = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                        permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (concedido) {
            pendingLocationAction?.invoke()
        } else {
            Toast.makeText(context, "Se requiere permiso de ubicación para posicionar la parcela", Toast.LENGTH_SHORT).show()
        }
        pendingLocationAction = null
    }

    fun ejecutarConPermisoUbicacion(accion: () -> Unit) {
        if (GpsLocationHelper.tienePermisoUbicacion(context)) {
            accion()
        } else {
            pendingLocationAction = accion
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // Selector de foto de parcela con persistencia real e inmediata en disco interno
    var loteFotoSeleccionadaId by remember { mutableStateOf<Int?>(null) }
    val loteFotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val idActual = loteFotoSeleccionadaId
        if (uri != null && idActual != null) {
            val idx = lotes.indexOfFirst { it.id == idActual }
            if (idx != -1) {
                val actual = lotes[idx]
                try {
                    val dir = File(context.filesDir, "lotes_fotos")
                    if (!dir.exists()) dir.mkdirs()
                    val destFile = File(dir, "lote_${actual.id}_${System.currentTimeMillis()}.jpg")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    lotes[idx] = actual.copy(fotoUri = destFile.absolutePath)
                    onLotesCambiados()
                    Toast.makeText(context, "Foto de ${actual.nombre} guardada con éxito ✓", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Error al guardar foto: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    var showDialogNuevoLote by remember { mutableStateOf(false) }
    var showDialogEditarLote by remember { mutableStateOf(false) }
    var showDialogAjustarMapa by remember { mutableStateOf(false) }
    var showDialogPapelera by remember { mutableStateOf(false) }
    var showDialogFotoOpciones by remember { mutableStateOf(false) }
    var loteFotoOpcionesId by remember { mutableStateOf<Int?>(null) }
    var loteAEditarId by remember { mutableStateOf<Int?>(null) }
    var loteAjustandoId by remember { mutableStateOf<Int?>(null) }
    var loteAEliminarId by remember { mutableStateOf<Int?>(null) }

    val lotesFiltrados = lotes.filter {
        it.nombre.contains(busqueda, ignoreCase = true) ||
                it.variedad.contains(busqueda, ignoreCase = true) ||
                it.ubicacion.contains(busqueda, ignoreCase = true)
    }

    Scaffold(
        bottomBar = {
            if (mostrarBottomBar) {
                AgroScanBottomBar(
                    currentScreen = AppScreen.LOTES,
                    onNavigate = { destino ->
                        when (destino) {
                            AppScreen.HOME -> onIrInicio()
                            AppScreen.HISTORIAL -> onIrHistorial()
                            AppScreen.SETTINGS -> onIrAjustes()
                            else -> {}
                        }
                    },
                    onDispararCamara = onDispararCamara
                )
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Gestión de Lotes", fontWeight = FontWeight.Bold, fontSize = 19.sp, color = TextDark)
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = SemaforoVerdeFondo
                            ) {
                                Text(
                                    text = "${lotes.size} activos",
                                    color = SemaforoVerde,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        Text("Elige o edita tu parcela de fresas", fontSize = 12.sp, color = TextGray)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver a Inicio", tint = AgroGreenPrimary)
                    }
                },
                actions = {
                    // Botón para ver la Papelera de reciclaje
                    IconButton(onClick = { showDialogPapelera = true }) {
                        BadgedBox(
                            badge = {
                                if (papeleraLotes.isNotEmpty()) {
                                    Badge(containerColor = SemaforoRojo) { Text("${papeleraLotes.size}") }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Papelera", tint = TextGray)
                        }
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
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            // Buscador
            OutlinedTextField(
                value = busqueda,
                onValueChange = { busqueda = it },
                placeholder = { Text("Buscar lote o variedad...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextGray) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AgroGreenPrimary,
                    unfocusedBorderColor = AgroCardBorder,
                    focusedContainerColor = AgroCardWhite,
                    unfocusedContainerColor = AgroCardWhite
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Lista de Tarjetas de Lotes
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(lotesFiltrados) { _, lote ->
                    val esActivo = loteActivoActual.contains(lote.nombre)

                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (esActivo) SemaforoVerdeFondo else AgroCardWhite
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (esActivo) SemaforoVerde else AgroCardBorder
                        ),
                        // La tarjeta ya no actúa como botón: tocar fuera de la foto/mapa no
                        // selecciona el lote ni redirige al Inicio (evita toques accidentales).
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(if (esActivo) SemaforoVerde else AgroMintBackground)
                                        .clickable {
                                            loteFotoOpcionesId = lote.id
                                            if (lote.fotoUri.isNullOrBlank()) {
                                                loteFotoSeleccionadaId = lote.id
                                                loteFotoLauncher.launch("image/*")
                                            } else {
                                                showDialogFotoOpciones = true
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!lote.fotoUri.isNullOrBlank()) {
                                        AsyncImage(
                                            model = lote.fotoUri,
                                            contentDescription = "Foto del lote",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Spa,
                                            contentDescription = "Icono Lote",
                                            tint = if (esActivo) Color.White else AgroGreenPrimary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${lote.nombre} - ${lote.variedad}",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextDark
                                    )
                                    Text(
                                        text = lote.ubicacion,
                                        fontSize = 12.sp,
                                        color = TextGray
                                    )

                                    // Miniatura Interactiva del Mapa (Tocar abre mapa grande para ajustar)
                                    if (lote.latitud != null && lote.longitud != null) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        MapaMiniaturaLote(
                                            lat = lote.latitud!!,
                                            lon = lote.longitud!!,
                                            altura = 74.dp,
                                            modifier = Modifier.fillMaxWidth(0.95f),
                                            onClick = {
                                                loteAjustandoId = lote.id
                                                showDialogAjustarMapa = true
                                            }
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Surface(
                                            onClick = {
                                                loteAjustandoId = lote.id
                                                showDialogAjustarMapa = true
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            color = AgroMintBackground,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, AgroGreenPrimary.copy(alpha = 0.35f)),
                                            modifier = Modifier.fillMaxWidth(0.95f)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = AgroGreenPrimary, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Vincular mapa (Toca aquí)", fontSize = 11.sp, color = AgroGreenPrimary, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    when (lote.estado) {
                                                        "Óptimo" -> SemaforoVerde
                                                        "En Revisión" -> SemaforoAmarillo
                                                        else -> SemaforoRojo
                                                    }
                                                )
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Estado: ${lote.estado}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = when (lote.estado) {
                                                "Óptimo" -> SemaforoVerde
                                                "En Revisión" -> SemaforoAmarillo
                                                else -> SemaforoRojo
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Acciones Ordenadas en Columna Vertical Limpia
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // 1. Editar Lote
                                Surface(
                                    onClick = {
                                        loteAEditarId = lote.id
                                        showDialogEditarLote = true
                                    },
                                    shape = CircleShape,
                                    color = AgroCardWhite,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, AgroCardBorder),
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Editar",
                                            tint = AgroGreenPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                // 2. Evaluar con Cámara
                                Surface(
                                    onClick = { onIniciarEvaluacionEnLote(lote.nombre) },
                                    shape = CircleShape,
                                    color = AgroMintBackground,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, AgroGreenPrimary.copy(alpha = 0.3f)),
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Icon(
                                            imageVector = Icons.Default.PhotoCamera,
                                            contentDescription = "Evaluar",
                                            tint = AgroGreenPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                // 3. Eliminar Lote
                                if (lotes.size > 1) {
                                    Surface(
                                        onClick = { loteAEliminarId = lote.id },
                                        shape = CircleShape,
                                        color = SemaforoRojoFondo,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, SemaforoRojo.copy(alpha = 0.3f)),
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = "Borrar",
                                                tint = SemaforoRojo,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Botón Añadir Lote
            Button(
                onClick = { showDialogNuevoLote = true },
                colors = ButtonDefaults.buttonColors(containerColor = AgroGreenPrimary),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Añadir nuevo lote o surco", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }

        // ================= DIÁLOGO OPCIONES DE FOTO DE LOTE =================
        if (showDialogFotoOpciones && loteFotoOpcionesId != null) {
            val idx = lotes.indexOfFirst { it.id == loteFotoOpcionesId }
            if (idx != -1) {
                val actual = lotes[idx]
                AlertDialog(
                    onDismissRequest = { showDialogFotoOpciones = false },
                    title = { Text(actual.nombre, fontWeight = FontWeight.Bold, color = AgroGreenPrimary) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    showDialogFotoOpciones = false
                                    loteFotoSeleccionadaId = actual.id
                                    loteFotoLauncher.launch("image/*")
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AgroGreenPrimary),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(46.dp)
                            ) {
                                Text("Cambiar Foto", fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    showDialogFotoOpciones = false
                                    lotes[idx] = actual.copy(fotoUri = null)
                                    onLotesCambiados()
                                    Toast.makeText(context, "Foto eliminada", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = SemaforoRojo),
                                border = androidx.compose.foundation.BorderStroke(1.dp, SemaforoRojo),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(46.dp)
                            ) {
                                Text("Eliminar Foto", fontWeight = FontWeight.Bold)
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { showDialogFotoOpciones = false }) {
                            Text("Cancelar", color = TextGray)
                        }
                    },
                    containerColor = AgroCardWhite,
                    shape = RoundedCornerShape(20.dp)
                )
            }
        }

        // ================= DIÁLOGO DE CONFIRMACIÓN DE BORRADO =================
        if (loteAEliminarId != null) {
            val idx = lotes.indexOfFirst { it.id == loteAEliminarId }
            if (idx != -1) {
                val loteABorrar = lotes[idx]
                AlertDialog(
                    onDismissRequest = { loteAEliminarId = null },
                    icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = SemaforoRojo) },
                    title = { Text("¿Mover a la Papelera?", fontWeight = FontWeight.Bold) },
                    text = {
                        Text("El lote '${loteABorrar.nombre}' será movido a la papelera. Podrás restaurarlo cuando desees.")
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                papeleraLotes.add(loteABorrar)
                                lotes.removeAt(idx)
                                loteAEliminarId = null
                                onLotesCambiados()
                                Toast.makeText(context, "Lote movido a la papelera", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SemaforoRojo)
                        ) {
                            Text("Mover a Papelera")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { loteAEliminarId = null }) {
                            Text("Cancelar", color = TextGray)
                        }
                    },
                    containerColor = AgroCardWhite,
                    shape = RoundedCornerShape(20.dp)
                )
            }
        }

        // ================= DIÁLOGO DE LA PAPELERA =================
        if (showDialogPapelera) {
            AlertDialog(
                onDismissRequest = { showDialogPapelera = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = AgroGreenPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Papelera de Lotes", fontWeight = FontWeight.Bold, color = AgroGreenPrimary)
                    }
                },
                text = {
                    if (papeleraLotes.isEmpty()) {
                        Text("La papelera está vacía.", color = TextGray, fontSize = 13.sp)
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            papeleraLotes.forEachIndexed { pIndex, pLote ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = AgroMintBackground,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(pLote.nombre, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDark)
                                            Text(pLote.variedad, fontSize = 11.sp, color = TextGray)
                                        }
                                        Row {
                                            IconButton(onClick = {
                                                lotes.add(pLote)
                                                papeleraLotes.removeAt(pIndex)
                                                onLotesCambiados()
                                                Toast.makeText(context, "Lote restaurado", Toast.LENGTH_SHORT).show()
                                            }) {
                                                Icon(Icons.Default.Restore, contentDescription = "Restaurar", tint = AgroGreenPrimary)
                                            }
                                            IconButton(onClick = {
                                                papeleraLotes.removeAt(pIndex)
                                                Toast.makeText(context, "Eliminado permanentemente", Toast.LENGTH_SHORT).show()
                                            }) {
                                                Icon(Icons.Default.DeleteForever, contentDescription = "Eliminar", tint = SemaforoRojo)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showDialogPapelera = false }) {
                        Text("Cerrar", color = AgroGreenPrimary, fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = AgroCardWhite,
                shape = RoundedCornerShape(20.dp)
            )
        }

        // ================= DIÁLOGO EXCLUSIVO: AJUSTAR UBICACIÓN CON MAPA GRANDE =================
        if (showDialogAjustarMapa && loteAjustandoId != null) {
            val idx = lotes.indexOfFirst { it.id == loteAjustandoId }
            if (idx != -1) {
                val loteActual = lotes[idx]
                var latAjuste by remember { mutableDoubleStateOf(loteActual.latitud ?: -12.046374) }
                var lonAjuste by remember { mutableDoubleStateOf(loteActual.longitud ?: -77.042793) }
                var direccionDetectada by remember { mutableStateOf(loteActual.ubicacion) }

                AlertDialog(
                    onDismissRequest = { showDialogAjustarMapa = false },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Place, contentDescription = null, tint = SemaforoRojo, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Ajustar Ubicación", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = TextDark)
                                Text(loteActual.nombre, fontSize = 12.sp, color = AgroGreenPrimary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = AgroMintBackground,
                                border = androidx.compose.foundation.BorderStroke(1.dp, AgroGreenPrimary.copy(alpha = 0.25f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = AgroGreenPrimary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Arrastra el mapa para centrar el círculo rojo en la parcela. El punto azul es tu ubicación GPS.",
                                        fontSize = 11.sp,
                                        color = TextDark,
                                        lineHeight = 14.sp
                                    )
                                }
                            }

                            // Botón directo: Centrar parcela en mi GPS actual
                            Surface(
                                onClick = {
                                    ejecutarConPermisoUbicacion {
                                        GpsLocationHelper.obtenerUbicacionActual(context) { lat, lon ->
                                            latAjuste = lat
                                            lonAjuste = lon
                                            coroutineScope.launch {
                                                val dir = GeocodingHelper.obtenerDireccionLegible(context, lat, lon)
                                                direccionDetectada = dir
                                            }
                                            Toast.makeText(context, "Parcela fijada en tu posición GPS actual ✓", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = AgroMintBackground,
                                border = androidx.compose.foundation.BorderStroke(1.dp, AgroGreenPrimary.copy(alpha = 0.45f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.MyLocation, contentDescription = null, tint = AgroGreenPrimary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Fijar con mi posición GPS actual", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = AgroGreenPrimary)
                                }
                            }

                            // Mapa Selector Grande con 2 Puntos (Tú y Parcela)
                            MapaSelectorParcela(
                                latInicial = latAjuste,
                                lonInicial = lonAjuste,
                                radioMetros = 10,
                                altura = 340.dp,
                                onCoordenadasCambiadas = { nuevaLat, nuevaLon ->
                                    latAjuste = nuevaLat
                                    lonAjuste = nuevaLon
                                    coroutineScope.launch {
                                        val dir = GeocodingHelper.obtenerDireccionLegible(context, nuevaLat, nuevaLon)
                                        direccionDetectada = dir
                                    }
                                }
                            )

                            // Dirección geocodificada en tiempo real
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = AgroCardWhite,
                                border = androidx.compose.foundation.BorderStroke(1.dp, AgroCardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = AgroGreenPrimary, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = direccionDetectada,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TextDark
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val loteModificado = loteActual.copy(
                                    latitud = latAjuste,
                                    longitud = lonAjuste,
                                    ubicacion = if (direccionDetectada.isNotBlank()) direccionDetectada else loteActual.ubicacion
                                )
                                lotes[idx] = loteModificado
                                onLotesCambiados()
                                showDialogAjustarMapa = false
                                Toast.makeText(context, "Ubicación de parcela actualizada ✓", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AgroGreenPrimary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Guardar Ubicación", fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDialogAjustarMapa = false }) {
                            Text("Cancelar", color = TextGray)
                        }
                    },
                    containerColor = AgroCardWhite,
                    shape = RoundedCornerShape(22.dp)
                )
            }
        }

        // ================= DIÁLOGO PARA AÑADIR NUEVO LOTE =================
        if (showDialogNuevoLote) {
            var nombreLoteInput by remember { mutableStateOf("") }
            var variedadInput by remember { mutableStateOf("") }
            var ubicacionInput by remember { mutableStateOf("") }
            var coordsCapturadas by remember { mutableStateOf<Pair<Double, Double>?>(null) }
            var buscandoGps by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { showDialogNuevoLote = false },
                title = { Text("Nuevo Lote de Fresa", fontWeight = FontWeight.Bold, color = AgroGreenPrimary) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(value = nombreLoteInput, onValueChange = { nombreLoteInput = it }, label = { Text("Nombre del Lote (Ej. Lote 2)") })
                        OutlinedTextField(value = variedadInput, onValueChange = { variedadInput = it }, label = { Text("Variedad (Ej. San Andreas, Monterrey)") })
                        OutlinedTextField(value = ubicacionInput, onValueChange = { ubicacionInput = it }, label = { Text("Ubicación (Ej. Invernadero B, Surco 4)") })

                        // Botón de captura GPS Ultrarrápida
                        Surface(
                            onClick = {
                                ejecutarConPermisoUbicacion {
                                    buscandoGps = true
                                    GpsLocationHelper.obtenerUbicacionActual(context) { lat, lon ->
                                        coordsCapturadas = Pair(lat, lon)
                                        buscandoGps = false
                                        coroutineScope.launch {
                                            val dir = GeocodingHelper.obtenerDireccionLegible(context, lat, lon)
                                            if (ubicacionInput.isBlank() || ubicacionInput.contains("Sector General")) {
                                                ubicacionInput = dir
                                            }
                                        }
                                        Toast.makeText(context, "Ubicación fijada con éxito ✓", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (coordsCapturadas != null) SemaforoVerdeFondo else AgroMintBackground,
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (coordsCapturadas != null) SemaforoVerde else AgroCardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (buscandoGps) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = AgroGreenPrimary
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = "GPS",
                                        tint = if (coordsCapturadas != null) SemaforoVerde else AgroGreenPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = when {
                                            buscandoGps -> "Obteniendo coordenadas satelitales..."
                                            coordsCapturadas != null -> "Ubicación GPS fijada ✓"
                                            else -> "Vincular GPS actual del Lote"
                                        },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (coordsCapturadas != null) SemaforoVerde else AgroGreenPrimary
                                    )
                                    Text(
                                        text = if (coordsCapturadas != null) "Ubicación fijada en el mapa (Radio 10m)" else "Toca para capturar ubicación de esta parcela",
                                        fontSize = 10.sp,
                                        color = TextGray
                                    )
                                }
                            }
                        }

                        // Mapa Interactivo para ajustar puntero a mano (Precisión 5 a 10 metros)
                        coordsCapturadas?.let { (lat, lon) ->
                            MapaSelectorParcela(
                                latInicial = lat,
                                lonInicial = lon,
                                radioMetros = 10,
                                altura = 300.dp,
                                onCoordenadasCambiadas = { nuevaLat, nuevaLon ->
                                    coordsCapturadas = Pair(nuevaLat, nuevaLon)
                                }
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (nombreLoteInput.trim().isNotEmpty()) {
                                lotes.add(
                                    LoteItem(
                                        id = (lotes.maxOfOrNull { it.id } ?: 0) + 1,
                                        nombre = nombreLoteInput.trim(),
                                        variedad = if (variedadInput.trim().isNotEmpty()) variedadInput.trim() else "Albión",
                                        ubicacion = if (ubicacionInput.trim().isNotEmpty()) ubicacionInput.trim() else "Sector General",
                                        estado = "Óptimo",
                                        ultimaEvaluacion = "Recién registrado",
                                        latitud = coordsCapturadas?.first,
                                        longitud = coordsCapturadas?.second
                                    )
                                )
                                onLotesCambiados()
                                showDialogNuevoLote = false
                                Toast.makeText(context, "Lote registrado ✓", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AgroGreenPrimary)
                    ) { Text("Guardar Lote") }
                },
                dismissButton = {
                    TextButton(onClick = { showDialogNuevoLote = false }) { Text("Cancelar") }
                }
            )
        }

        // ================= DIÁLOGO PARA EDITAR LOTE =================
        if (showDialogEditarLote && loteAEditarId != null) {
            val idx = lotes.indexOfFirst { it.id == loteAEditarId }
            if (idx != -1) {
                val loteActual = lotes[idx]
                var editNombre by remember { mutableStateOf(loteActual.nombre) }
                var editVariedad by remember { mutableStateOf(loteActual.variedad) }
                var editUbicacion by remember { mutableStateOf(loteActual.ubicacion) }
                var editLat by remember { mutableStateOf(loteActual.latitud) }
                var editLon by remember { mutableStateOf(loteActual.longitud) }
                var actualizandoGps by remember { mutableStateOf(false) }

                AlertDialog(
                    onDismissRequest = { showDialogEditarLote = false },
                    title = { Text("Editar ${loteActual.nombre}", fontWeight = FontWeight.Bold, color = AgroGreenPrimary) },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(value = editNombre, onValueChange = { editNombre = it }, label = { Text("Nombre") })
                            OutlinedTextField(value = editVariedad, onValueChange = { editVariedad = it }, label = { Text("Variedad") })
                            OutlinedTextField(value = editUbicacion, onValueChange = { editUbicacion = it }, label = { Text("Ubicación") })

                            // Botón para Modificar / Actualizar GPS del Lote
                            Surface(
                                onClick = {
                                    ejecutarConPermisoUbicacion {
                                        actualizandoGps = true
                                        GpsLocationHelper.obtenerUbicacionActual(context) { lat, lon ->
                                            editLat = lat
                                            editLon = lon
                                            actualizandoGps = false
                                            coroutineScope.launch {
                                                val dir = GeocodingHelper.obtenerDireccionLegible(context, lat, lon)
                                                editUbicacion = dir
                                            }
                                            Toast.makeText(context, "Nueva ubicación fijada ✓", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (editLat != null) SemaforoVerdeFondo else AgroMintBackground,
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (editLat != null) SemaforoVerde else AgroCardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (actualizandoGps) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = AgroGreenPrimary
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.LocationOn,
                                            contentDescription = "GPS",
                                            tint = if (editLat != null) SemaforoVerde else AgroGreenPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = when {
                                                actualizandoGps -> "Actualizando ubicación GPS..."
                                                editLat != null -> "Ubicación GPS registrada ✓"
                                                else -> "Vincular GPS actual del Lote"
                                            },
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = if (editLat != null) SemaforoVerde else AgroGreenPrimary
                                        )
                                        Text(
                                            text = if (editLat != null && editLon != null) "Ubicación fijada en el mapa (Radio 10m)" else "Toca para fijar tu ubicación actual",
                                            fontSize = 10.sp,
                                            color = TextGray
                                        )
                                    }
                                }
                            }

                            // Mapa Interactivo en Edición (Mover puntero libremente)
                            if (editLat != null && editLon != null) {
                                MapaSelectorParcela(
                                    latInicial = editLat!!,
                                    lonInicial = editLon!!,
                                    radioMetros = 10,
                                    altura = 300.dp,
                                    onCoordenadasCambiadas = { nuevaLat, nuevaLon ->
                                        editLat = nuevaLat
                                        editLon = nuevaLon
                                        coroutineScope.launch {
                                            val dir = GeocodingHelper.obtenerDireccionLegible(context, nuevaLat, nuevaLon)
                                            editUbicacion = dir
                                        }
                                    }
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (editNombre.trim().isNotEmpty()) {
                                    lotes[idx] = loteActual.copy(
                                        nombre = editNombre.trim(),
                                        variedad = editVariedad.trim(),
                                        ubicacion = editUbicacion.trim(),
                                        latitud = editLat,
                                        longitud = editLon
                                    )
                                    onLotesCambiados()
                                    showDialogEditarLote = false
                                    Toast.makeText(context, "Lote actualizado ✓", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AgroGreenPrimary)
                        ) { Text("Guardar Cambios") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDialogEditarLote = false }) { Text("Cancelar") }
                    }
                )
            }
        }
    }
}
