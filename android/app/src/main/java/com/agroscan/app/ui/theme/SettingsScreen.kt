package com.agroscan.app.ui

import android.app.Activity
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import coil.compose.rememberAsyncImagePainter
import com.agroscan.app.data.LoteItem
import com.agroscan.app.ui.ChatbotDialog
import com.agroscan.app.data.NetworkClient
import com.agroscan.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    userName: String,
    serverUrl: String,
    lotes: MutableList<LoteItem>, // ¡LISTA COMPARTIDA REAL!
    papeleraLotes: MutableList<LoteItem>,
    onGuardarNombre: (nuevoNombre: String) -> Unit,
    onGuardarServerUrl: (nuevaUrl: String) -> Unit,
    onCerrarSesion: () -> Unit,
    onVolver: () -> Unit // ¡FLECHITA DE REGRESAR!
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // Estados reales del Perfil
    var userPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var userRole by remember { mutableStateOf("Evaluador Fitosanitario") }
    var userEmail by remember { mutableStateOf("productor@campoagricola.com") }

    // Diálogos
    var showDialogEditarPerfil by remember { mutableStateOf(false) }
    var showDialogEditarIp by remember { mutableStateOf(false) }
    var showDialogNuevoLote by remember { mutableStateOf(false) }
    var showDialogPapelera by remember { mutableStateOf(false) }
    var loteAEliminarIndex by remember { mutableStateOf<Int?>(null) }
    var showDialogGuiaCampo by remember { mutableStateOf(false) }

    var nuevoLoteInput by remember { mutableStateOf("") }
    var nuevaVariedadInput by remember { mutableStateOf("") }

    // Conexión Real con Backend
    var probandoBackend by remember { mutableStateOf(false) }
    var estadoBackend by remember { mutableStateOf<String?>(null) }

    // Conexión Real con ESP32-CAM
    var esp32Ip by remember { mutableStateOf("http://192.168.4.1/") }
    var probandoEsp32 by remember { mutableStateOf(false) }
    var estadoEsp32 by remember { mutableStateOf("No verificada ⚪") }

    // CONTROL REAL DE BRILLO SOLAR
    var modoAltoContraste by remember { mutableStateOf(false) }

    LaunchedEffect(modoAltoContraste) {
        activity?.window?.let { window ->
            val layoutParams = window.attributes
            layoutParams.screenBrightness = if (modoAltoContraste) {
                1.0f
            } else {
                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
            window.attributes = layoutParams
        }
    }

    // Memoria REAL del teléfono Samsung
    val statFs = remember { StatFs(Environment.getDataDirectory().path) }
    val bytesDisponibles = statFs.availableBlocksLong * statFs.blockSizeLong
    val bytesTotales = statFs.blockCountLong * statFs.blockSizeLong
    val gbDisponibles = bytesDisponibles / (1024 * 1024 * 1024)
    val gbTotales = bytesTotales / (1024 * 1024 * 1024)
    val porcentajeOcupado = 1f - (bytesDisponibles.toFloat() / bytesTotales.toFloat())

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            userPhotoUri = uri
            Toast.makeText(context, "Foto de perfil actualizada", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Ajustes del Sistema", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TextDark)
                },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = AgroGreenPrimary)
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
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ================= 1. PERFIL EDITABLE CON FOTO =================
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = AgroCardWhite),
                border = androidx.compose.foundation.BorderStroke(1.dp, AgroCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(AgroGreenPrimary)
                            .clickable { photoPickerLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (userPhotoUri != null) {
                            Image(
                                painter = rememberAsyncImagePainter(userPhotoUri),
                                contentDescription = "Foto real",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(
                                text = if (userName.isNotEmpty()) userName.take(2).uppercase() else "AG",
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(AgroCardWhite)
                                .border(1.dp, AgroCardBorder, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = AgroGreenPrimary,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (userName.isNotEmpty()) userName else "Sin nombre",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )
                        Text(
                            text = userRole,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = AgroGreenPrimary
                        )
                        if (userEmail.isNotEmpty()) {
                            Text(
                                text = userEmail,
                                fontSize = 11.sp,
                                color = TextGray
                            )
                        }
                    }

                    IconButton(onClick = { showDialogEditarPerfil = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar", tint = AgroGreenPrimary)
                    }
                }
            }

            // ================= 2. GESTIÓN DE CULTIVOS Y LOTES (100% SINCRONIZADA) =================
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = AgroCardWhite),
                border = androidx.compose.foundation.BorderStroke(1.dp, AgroCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Agriculture, contentDescription = null, tint = AgroGreenPrimary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Gestión de Cultivos y Lotes", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextDark)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { showDialogPapelera = true }) {
                                BadgedBox(
                                    badge = {
                                        if (papeleraLotes.isNotEmpty()) {
                                            Badge(containerColor = SemaforoRojo) { Text("${papeleraLotes.size}") }
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Papelera", tint = TextGray, modifier = Modifier.size(20.dp))
                                }
                            }
                            IconButton(onClick = { showDialogNuevoLote = true }) {
                                Icon(Icons.Default.AddCircle, contentDescription = "Añadir", tint = AgroGreenPrimary)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (lotes.isEmpty()) {
                        Text("No hay lotes activos.", fontSize = 12.sp, color = TextGray)
                    } else {
                        lotes.forEachIndexed { index, lote ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SemaforoVerde, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = "${lote.nombre} • ${lote.variedad} (${lote.ubicacion})", fontSize = 12.sp, color = TextDark)
                                }

                                if (lotes.size > 1) {
                                    IconButton(
                                        onClick = {
                                            loteAEliminarIndex = index
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Borrar", tint = SemaforoRojo, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ================= 3. DISPOSITIVO Y CONECTIVIDAD IOT =================
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = AgroCardWhite),
                border = androidx.compose.foundation.BorderStroke(1.dp, AgroCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Sensors, contentDescription = null, tint = AgroGreenPrimary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Dispositivo y Conectividad IoT", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextDark)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = AgroMintBackground,
                        border = androidx.compose.foundation.BorderStroke(1.dp, AgroCardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(AgroCardWhite),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.CameraIndoor, contentDescription = null, tint = AgroGreenPrimary, modifier = Modifier.size(24.dp))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Cámara ESP32-CAM", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextDark)
                                    Text(estadoEsp32, fontSize = 11.sp, color = TextGray)
                                }
                            }

                            Button(
                                onClick = {
                                    probandoEsp32 = true
                                    estadoEsp32 = "Buscando..."
                                    coroutineScope.launch {
                                        val respondio = withContext(Dispatchers.IO) {
                                            try {
                                                val url = URL(esp32Ip)
                                                val conn = url.openConnection() as HttpURLConnection
                                                conn.connectTimeout = 3000
                                                conn.readTimeout = 3000
                                                conn.requestMethod = "GET"
                                                conn.responseCode in 200..399
                                            } catch (e: Exception) {
                                                false
                                            }
                                        }
                                        probandoEsp32 = false
                                        estadoEsp32 = if (respondio) "Conectada 🟢" else "Desconectada 🔴"
                                    }
                                },
                                enabled = !probandoEsp32,
                                colors = ButtonDefaults.buttonColors(containerColor = AgroGreenPrimary),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(if (probandoEsp32) "..." else "Verificar", fontSize = 11.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Almacenamiento Local de Fotos: $gbDisponibles GB libres de $gbTotales GB",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextDark
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { porcentajeOcupado },
                        color = AgroGreenPrimary,
                        trackColor = AgroMintBackground,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                    )
                }
            }

            // ================= 4. SERVIDOR BACKEND & IA =================
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = AgroCardWhite),
                border = androidx.compose.foundation.BorderStroke(1.dp, AgroCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Dns, contentDescription = null, tint = AgroGreenPrimary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Servidor Backend & IA", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextDark)
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(text = "IP Actual:", fontSize = 11.sp, color = TextGray)
                    Text(text = serverUrl, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AgroGreenPrimary)

                    if (estadoBackend != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = estadoBackend!!,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (estadoBackend!!.contains("🟢")) SemaforoVerde else SemaforoRojo
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showDialogEditarIp = true },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cambiar IP", fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                probandoBackend = true
                                estadoBackend = "Verificando..."
                                coroutineScope.launch {
                                    val ok = withContext(Dispatchers.IO) { NetworkClient.probarConexion() }
                                    probandoBackend = false
                                    estadoBackend = if (ok) "Servidor en línea 🟢" else "Sin respuesta 🔴"
                                }
                            },
                            enabled = !probandoBackend,
                            colors = ButtonDefaults.buttonColors(containerColor = AgroGreenPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (probandoBackend) "Probando..." else "Probar Red", fontSize = 12.sp)
                        }
                    }
                }
            }

            // ================= 5. CUENTA, SOPORTE, BRILLO & VERSIÓN =================
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = AgroCardWhite),
                border = androidx.compose.foundation.BorderStroke(1.dp, AgroCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = AgroGreenPrimary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cuenta y Soporte de Campo", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextDark)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Modo Alto Contraste Solar ☀️", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextDark)
                            Text("Ajusta el brillo al 100% para trabajo bajo sol", fontSize = 11.sp, color = TextGray)
                        }
                        Switch(
                            checked = modoAltoContraste,
                            onCheckedChange = { modoAltoContraste = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = AgroGreenPrimary, checkedTrackColor = SemaforoVerdeFondo)
                        )
                    }

                    Divider(modifier = Modifier.padding(vertical = 12.dp), color = AgroCardBorder)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDialogGuiaCampo = true },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Psychology, contentDescription = null, tint = AgroGreenPrimary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Asistente Agrónomo IA (Tecsup)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextDark)
                                Text("Chat en vivo sobre plagas, dosis de fungicidas y campo", fontSize = 11.sp, color = TextGray)
                            }
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextGray)
                    }

                    Divider(modifier = Modifier.padding(vertical = 12.dp), color = AgroCardBorder)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Versión agroScan", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextDark)
                            Text("v1.0 (Compilación de campo 2026.10)", fontSize = 11.sp, color = TextGray)
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SemaforoVerdeFondo
                        ) {
                            Text(
                                text = "Actualizado",
                                color = SemaforoVerde,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedButton(
                        onClick = onCerrarSesion,
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = SemaforoRojoFondo,
                            contentColor = SemaforoRojo
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SemaforoRojo.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cerrar Sesión", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // ================= DIÁLOGO DE CONFIRMACIÓN DE BORRADO =================
        if (loteAEliminarIndex != null && loteAEliminarIndex!! < lotes.size) {
            val loteABorrar = lotes[loteAEliminarIndex!!]
            AlertDialog(
                onDismissRequest = { loteAEliminarIndex = null },
                icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = SemaforoRojo) },
                title = { Text("¿Mover a la Papelera?", fontWeight = FontWeight.Bold) },
                text = {
                    Text("El lote '${loteABorrar.nombre}' será movido a la papelera. Podrás restaurarlo cuando desees.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            papeleraLotes.add(loteABorrar)
                            lotes.removeAt(loteAEliminarIndex!!)
                            loteAEliminarIndex = null
                            Toast.makeText(context, "Lote movido a la papelera", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SemaforoRojo)
                    ) {
                        Text("Mover a Papelera")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { loteAEliminarIndex = null }) {
                        Text("Cancelar", color = TextGray)
                    }
                },
                containerColor = AgroCardWhite,
                shape = RoundedCornerShape(20.dp)
            )
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
                                            IconButton(
                                                onClick = {
                                                    lotes.add(pLote)
                                                    papeleraLotes.removeAt(pIndex)
                                                    Toast.makeText(context, "Lote restaurado ↩️", Toast.LENGTH_SHORT).show()
                                                }
                                            ) {
                                                Icon(Icons.Default.Restore, contentDescription = "Restaurar", tint = SemaforoVerde)
                                            }

                                            IconButton(
                                                onClick = {
                                                    papeleraLotes.removeAt(pIndex)
                                                    Toast.makeText(context, "Eliminado definitivamente 🗑️", Toast.LENGTH_SHORT).show()
                                                }
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "Eliminar definitivo", tint = SemaforoRojo)
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

        // ================= DIÁLOGOS =================
        if (showDialogEditarPerfil) {
            var tempNombre by remember { mutableStateOf(userName) }
            var tempRol by remember { mutableStateOf(userRole) }
            var tempEmail by remember { mutableStateOf(userEmail) }

            AlertDialog(
                onDismissRequest = { showDialogEditarPerfil = false },
                title = { Text("Editar Datos de Perfil") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = tempNombre, onValueChange = { tempNombre = it }, label = { Text("Nombre") })
                        OutlinedTextField(value = tempRol, onValueChange = { tempRol = it }, label = { Text("Rol") })
                        OutlinedTextField(value = tempEmail, onValueChange = { tempEmail = it }, label = { Text("Correo") })
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        onGuardarNombre(tempNombre.trim())
                        userRole = tempRol.trim()
                        userEmail = tempEmail.trim()
                        showDialogEditarPerfil = false
                    }) { Text("Guardar") }
                },
                dismissButton = {
                    TextButton(onClick = { showDialogEditarPerfil = false }) { Text("Cancelar") }
                }
            )
        }

        // Diálogo para Añadir Lote (Añade directo a la lista compartida)
        if (showDialogNuevoLote) {
            AlertDialog(
                onDismissRequest = { showDialogNuevoLote = false },
                title = { Text("Registrar Nuevo Lote", fontWeight = FontWeight.Bold, color = AgroGreenPrimary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = nuevoLoteInput,
                            onValueChange = { nuevoLoteInput = it },
                            label = { Text("Nombre del Lote") },
                            placeholder = { Text("Ej. Lote 2") }
                        )
                        OutlinedTextField(
                            value = nuevaVariedadInput,
                            onValueChange = { nuevaVariedadInput = it },
                            label = { Text("Variedad") },
                            placeholder = { Text("Ej. San Andreas") }
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        if (nuevoLoteInput.trim().isNotEmpty()) {
                            lotes.add(
                                LoteItem(
                                    id = lotes.size + 1,
                                    nombre = nuevoLoteInput.trim(),
                                    variedad = if (nuevaVariedadInput.trim().isNotEmpty()) nuevaVariedadInput.trim() else "Albión",
                                    ubicacion = "Sector General",
                                    estado = "Óptimo",
                                    ultimaEvaluacion = "Recién registrado"
                                )
                            )
                            nuevoLoteInput = ""
                            nuevaVariedadInput = ""
                            showDialogNuevoLote = false
                            Toast.makeText(context, "Lote registrado y sincronizado", Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("Registrar") }
                },
                dismissButton = {
                    TextButton(onClick = { showDialogNuevoLote = false }) { Text("Cancelar") }
                }
            )
        }

        if (showDialogEditarIp) {
            var tempIp by remember { mutableStateOf(serverUrl) }
            AlertDialog(
                onDismissRequest = { showDialogEditarIp = false },
                title = { Text("Configurar IP Backend") },
                text = {
                    OutlinedTextField(value = tempIp, onValueChange = { tempIp = it }, label = { Text("URL Servidor") })
                },
                confirmButton = {
                    Button(onClick = {
                        onGuardarServerUrl(tempIp.trim())
                        showDialogEditarIp = false
                    }) { Text("Guardar") }
                },
                dismissButton = {
                    TextButton(onClick = { showDialogEditarIp = false }) { Text("Cancelar") }
                }
            )
        }

        if (showDialogGuiaCampo) {
            ChatbotDialog(onDismissRequest = { showDialogGuiaCampo = false })
        }
    }
}
