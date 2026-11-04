package com.agroscan.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.agroscan.app.data.AgroScanResult
import com.agroscan.app.data.EvaluacionCreateRequest
import com.agroscan.app.data.EvaluacionHistorial
import com.agroscan.app.data.LoteItem
import com.agroscan.app.data.NetworkClient
import com.agroscan.app.ui.*
import com.agroscan.app.ui.theme.*
import com.agroscan.app.util.GpsLocationHelper
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class AppScreen {
    LOGIN,
    HOME,
    REPORT,
    LOTES,
    SETTINGS,
    HISTORIAL,
    CROP,
    CAMERA
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AgroScanTheme {
                AgroScanApp()
            }
        }
    }
}

@Composable
fun AgroScanApp() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("agroscan_prefs", Context.MODE_PRIVATE) }
    val gson = remember { Gson() }

    var userName by remember { mutableStateOf(prefs.getString("user_name", "") ?: "") }
    var userEmail by remember { mutableStateOf(prefs.getString("user_email", "") ?: "") }
    var currentParcela by remember { mutableStateOf("Lote 1 (Parcela Norte) • Fresa Albión") }

    fun getLotesKey(email: String): String {
        val sanitized = email.trim().lowercase().replace("@", "_").replace(".", "_")
        return if (sanitized.isNotBlank()) "lotes_json_$sanitized" else "lotes_json"
    }

    fun getHistorialKey(email: String): String {
        val sanitized = email.trim().lowercase().replace("@", "_").replace(".", "_")
        return if (sanitized.isNotBlank()) "historial_json_$sanitized" else "historial_json"
    }

    fun guardarBitmapMuestra(id: String, bmp: Bitmap?): String? {
        if (bmp == null) return null
        return try {
            val dir = File(context.filesDir, "evaluaciones")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "eval_$id.jpg")
            FileOutputStream(file).use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            file.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    val listaLotesCompartida = remember { mutableStateListOf<LoteItem>() }
    val papeleraLotesCompartida = remember { mutableStateListOf<LoteItem>() }
    val listaHistorialCompartida = remember { mutableStateListOf<EvaluacionHistorial>() }

    fun cargarDatosDeUsuario(email: String) {
        listaLotesCompartida.clear()
        val lotesKey = getLotesKey(email)
        val lotesRaw = prefs.getString(lotesKey, null)
        if (!lotesRaw.isNullOrBlank()) {
            try {
                val type = object : TypeToken<List<LoteItem>>() {}.type
                val list: List<LoteItem> = gson.fromJson(lotesRaw, type) ?: emptyList()
                listaLotesCompartida.addAll(list)
            } catch (_: Exception) {}
        }
        if (listaLotesCompartida.isEmpty()) {
            val defaultLotes = listOf(
                LoteItem(1, "Lote 1 (Parcela Norte)", "Albión", "Sector General", "Óptimo", "Sin evaluar", latitud = -12.046374, longitud = -77.042793),
                LoteItem(2, "Lote 2 (Parcela Sur)", "Monterrey", "Sector General", "Óptimo", "Sin evaluar", latitud = -12.046500, longitud = -77.042900)
            )
            listaLotesCompartida.addAll(defaultLotes)
            prefs.edit().putString(lotesKey, gson.toJson(defaultLotes)).apply()
        }

        listaHistorialCompartida.clear()
        val histKey = getHistorialKey(email)
        val histRaw = prefs.getString(histKey, null)
        if (!histRaw.isNullOrBlank()) {
            try {
                val type = object : TypeToken<List<EvaluacionHistorial>>() {}.type
                val list: List<EvaluacionHistorial> = gson.fromJson(histRaw, type) ?: emptyList()
                listaHistorialCompartida.addAll(list)
            } catch (_: Exception) {}
        }
    }

    // Inicializar datos al montar si el usuario ya tenía sesión abierta
    LaunchedEffect(userEmail) {
        if (userEmail.isNotBlank()) {
            cargarDatosDeUsuario(userEmail)
        }
    }

    // Persistencia reactiva de parcelas exclusiva por usuario
    LaunchedEffect(listaLotesCompartida.size, listaLotesCompartida.map { "${it.id}-${it.nombre}-${it.latitud}-${it.longitud}-${it.fotoUri}" }, userEmail) {
        if (userEmail.isNotBlank() && listaLotesCompartida.isNotEmpty()) {
            val json = gson.toJson(listaLotesCompartida.toList())
            prefs.edit().putString(getLotesKey(userEmail), json).apply()
        }
    }

    // Persistencia reactiva del historial exclusiva por usuario
    LaunchedEffect(listaHistorialCompartida.size, userEmail) {
        if (userEmail.isNotBlank()) {
            val json = gson.toJson(listaHistorialCompartida.toList())
            prefs.edit().putString(getHistorialKey(userEmail), json).apply()
        }
    }

    // Navegación principal con Pager Deslizable (Swipe Horizontal)
    val paginasPrincipales = listOf(AppScreen.HOME, AppScreen.HISTORIAL, AppScreen.LOTES, AppScreen.SETTINGS)
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 4 })

    var currentScreen by remember {
        mutableStateOf(if (userName.isNotEmpty() && userEmail.isNotEmpty()) AppScreen.HOME else AppScreen.LOGIN)
    }

    // Sincronizar el currentScreen cuando el usuario desliza la pantalla con su dedo
    LaunchedEffect(pagerState.currentPage) {
        val nuevaPantalla = paginasPrincipales[pagerState.currentPage]
        if (currentScreen in paginasPrincipales && currentScreen != nuevaPantalla) {
            currentScreen = nuevaPantalla
        }
    }

    // Sincronizar el pagerState si el currentScreen cambia por código
    LaunchedEffect(currentScreen) {
        val targetIndex = paginasPrincipales.indexOf(currentScreen)
        if (targetIndex != -1 && pagerState.currentPage != targetIndex) {
            pagerState.scrollToPage(targetIndex)
        }
    }

    BackHandler(enabled = (currentScreen != AppScreen.LOGIN)) {
        if (currentScreen !in paginasPrincipales) {
            currentScreen = AppScreen.HOME
        } else if (pagerState.currentPage != 0) {
            coroutineScope.launch { pagerState.animateScrollToPage(0) }
        }
    }

    var isLoading by remember { mutableStateOf(false) }

    var uncroppedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var scanResult by remember { mutableStateOf<AgroScanResult?>(null) }

    fun enviarFotoAlServidor(bitmap: Bitmap) {
        selectedBitmap = bitmap
        isLoading = true

        coroutineScope.launch {
            try {
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                val byteArray = stream.toByteArray()

                val requestBody = byteArray.toRequestBody("image/jpeg".toMediaTypeOrNull())
                val multipartBody = MultipartBody.Part.createFormData("file", "fresa.jpg", requestBody)

                val result = withContext(Dispatchers.IO) {
                    NetworkClient.api.analizarMuestra(multipartBody)
                }

                scanResult = result
                isLoading = false
                currentScreen = AppScreen.REPORT

            } catch (e: Exception) {
                isLoading = false
                Toast.makeText(
                    context,
                    "Error conectando con AgroScan Backend: ${e.localizedMessage ?: "Verifica que el servidor esté activo"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val bitmap = if (Build.VERSION.SDK_INT < 28) {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            } else {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source)
            }
            enviarFotoAlServidor(bitmap)
        }
    }

    fun abrirCamaraConGps() {
        GpsLocationHelper.obtenerUbicacionActual(context) { lat, lon ->
            val loteCercano = GpsLocationHelper.detectarLoteCercano(lat, lon, listaLotesCompartida)
            loteCercano?.let { (lote, distancia) ->
                currentParcela = "${lote.nombre} (${lote.ubicacion}) • Fresa ${lote.variedad}"
                Toast.makeText(context, "Lote detectado por GPS: ${lote.nombre} (${distancia}m)", Toast.LENGTH_SHORT).show()
            }
        }
        currentScreen = AppScreen.CAMERA
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (currentScreen) {
            // 1. PANTALLA DE LOGIN
            AppScreen.LOGIN -> {
                LoginScreen(
                    onLoginExitoso = { nombreIngresado, emailIngresado, rolIngresado ->
                        userName = nombreIngresado
                        userEmail = emailIngresado
                        prefs.edit()
                            .putString("user_name", nombreIngresado)
                            .putString("user_email", emailIngresado)
                            .putString("user_role", rolIngresado)
                            .apply()
                        cargarDatosDeUsuario(emailIngresado)
                        coroutineScope.launch { pagerState.scrollToPage(0) }
                        currentScreen = AppScreen.HOME
                    }
                )
            }

            // 2. REPORTE FITOSANITARIO
            AppScreen.REPORT -> {
                scanResult?.let { result ->
                    ReportScreen(
                        result = result,
                        imageBitmap = selectedBitmap,
                        lotes = listaLotesCompartida,
                        loteInicial = currentParcela,
                        userName = userName,
                        onGuardarEvaluacion = { lote, variedad, notas ->
                            val nuevoEstado = when (result.semaforo.lowercase()) {
                                "rojo" -> "Crítico"
                                "amarillo" -> "En Revisión"
                                else -> "Óptimo"
                            }

                            val horaActual = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date())
                            val resumenEvaluacion = "$horaActual • Madurez ${result.madurezPorcentaje}% • ${if (result.semaforo.lowercase() == "verde") "Óptima" else result.dictamen}"

                            val index = listaLotesCompartida.indexOfFirst { it.id == lote.id || it.nombre == lote.nombre }
                            if (index != -1) {
                                listaLotesCompartida[index] = listaLotesCompartida[index].copy(
                                    estado = nuevoEstado,
                                    variedad = variedad,
                                    ultimaEvaluacion = resumenEvaluacion
                                )
                            } else {
                                listaLotesCompartida.add(
                                    LoteItem(
                                        id = listaLotesCompartida.size + 1,
                                        nombre = lote.nombre,
                                        variedad = variedad,
                                        ubicacion = lote.ubicacion,
                                        estado = nuevoEstado,
                                        ultimaEvaluacion = resumenEvaluacion
                                    )
                                )
                            }

                            val evalId = UUID.randomUUID().toString()
                            val fotoPath = guardarBitmapMuestra(evalId, selectedBitmap)
                            val horaCompleta = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())

                            val nuevaEvaluacion = EvaluacionHistorial(
                                id = evalId,
                                fechaHora = horaCompleta,
                                loteNombre = "${lote.nombre} (${lote.ubicacion})",
                                variedad = variedad,
                                notas = notas,
                                resultado = result,
                                bitmap = selectedBitmap,
                                fotoPath = fotoPath,
                                latitud = lote.latitud,
                                longitud = lote.longitud
                            )

                            listaHistorialCompartida.add(0, nuevaEvaluacion)

                            if (userEmail.isNotBlank()) {
                                prefs.edit()
                                    .putString(getLotesKey(userEmail), gson.toJson(listaLotesCompartida.toList()))
                                    .putString(getHistorialKey(userEmail), gson.toJson(listaHistorialCompartida.toList()))
                                    .apply()

                                coroutineScope.launch(Dispatchers.IO) {
                                    try {
                                        NetworkClient.api.guardarEvaluacion(
                                            EvaluacionCreateRequest(
                                                usuario_email = userEmail,
                                                lote_id = lote.id,
                                                lote_nombre = lote.nombre,
                                                variedad = variedad,
                                                fruta = result.fruta,
                                                madurez_porcentaje = result.madurezPorcentaje,
                                                color_predominante = result.colorPredominante,
                                                defectos_detectados = result.defectosDetectados,
                                                semaforo = result.semaforo,
                                                dictamen = result.dictamen,
                                                recomendacion = result.recomendacion,
                                                fungicidas_sugeridos = result.fungicidasSugeridos,
                                                medidas_preventivas = result.medidasPreventivas,
                                                notas = notas
                                            )
                                        )
                                    } catch (_: Exception) {}
                                }
                            }

                            currentParcela = "${lote.nombre} (${lote.ubicacion}) • Fresa $variedad"

                            Toast.makeText(
                                context,
                                "Evaluación guardada en ${lote.nombre}: Estado $nuevoEstado",
                                Toast.LENGTH_LONG
                            ).show()
                            currentScreen = AppScreen.HOME
                        },
                        onVolver = {
                            val yaGuardado = listaHistorialCompartida.any {
                                it.resultado.dictamen == result.dictamen && it.resultado.madurezPorcentaje == result.madurezPorcentaje
                            }
                            if (!yaGuardado) {
                                val loteActual = listaLotesCompartida.firstOrNull() ?: LoteItem(1, "Lote 1 (Parcela Norte)", "Albión", "Sector General", "Óptimo", "Sin evaluar")
                                val evalId = UUID.randomUUID().toString()
                                val fotoPath = guardarBitmapMuestra(evalId, selectedBitmap)
                                val horaCompleta = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
                                val autoEval = EvaluacionHistorial(
                                    id = evalId,
                                    fechaHora = horaCompleta,
                                    loteNombre = "${loteActual.nombre} (${loteActual.ubicacion})",
                                    variedad = loteActual.variedad,
                                    notas = "Auto-guardado al escanear",
                                    resultado = result,
                                    bitmap = selectedBitmap,
                                    fotoPath = fotoPath
                                )
                                listaHistorialCompartida.add(0, autoEval)
                                if (userEmail.isNotBlank()) {
                                    prefs.edit()
                                        .putString(getHistorialKey(userEmail), gson.toJson(listaHistorialCompartida.toList()))
                                        .apply()
                                }
                            }
                            currentScreen = AppScreen.HOME
                        }
                    )
                }
            }

            // 3. CÁMARA INTELIGENTE
            AppScreen.CAMERA -> {
                CameraInAppScreen(
                    loteActivo = currentParcela,
                    lotesDisponibles = listaLotesCompartida,
                    onCambiarLote = { nuevoLote ->
                        currentParcela = nuevoLote
                    },
                    onFotoCapturada = { bitmapAutoRecortado ->
                        enviarFotoAlServidor(bitmapAutoRecortado)
                    },
                    onSeleccionarDeGaleria = {
                        galleryLauncher.launch("image/*")
                    },
                    onVolver = {
                        currentScreen = AppScreen.HOME
                    }
                )
            }

            // 4. RECORTE DE IMAGEN
            AppScreen.CROP -> {
                uncroppedBitmap?.let { bmp ->
                    CropScreen(
                        originalBitmap = bmp,
                        onCropConfirmed = { croppedBmp ->
                            enviarFotoAlServidor(croppedBmp)
                        },
                        onRetake = {
                            currentScreen = AppScreen.CAMERA
                        },
                        onCancel = {
                            currentScreen = AppScreen.CAMERA
                        }
                    )
                }
            }

            // 5. NAVEGACIÓN PRINCIPAL CON DESLIZAMIENTO HORIZONTAL (SWIPE GESTURES)
            else -> {
                Scaffold(
                    bottomBar = {
                        AgroScanBottomBar(
                            currentScreen = paginasPrincipales[pagerState.currentPage],
                            onNavigate = { destino ->
                                val targetIndex = paginasPrincipales.indexOf(destino)
                                if (targetIndex != -1) {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(targetIndex)
                                    }
                                }
                            },
                            onDispararCamara = { abrirCamaraConGps() }
                        )
                    }
                ) { scaffoldPadding ->
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = scaffoldPadding.calculateBottomPadding())
                    ) { page ->
                        when (page) {
                            0 -> HomeScreen(
                                userName = userName,
                                parcelaActual = currentParcela,
                                historial = listaHistorialCompartida,
                                lotes = listaLotesCompartida,
                                onIniciarEvaluacion = { abrirCamaraConGps() },
                                onCambiarParcela = { nuevaParcela -> currentParcela = nuevaParcela },
                                onIrHistorial = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                                onIrLotes = { coroutineScope.launch { pagerState.animateScrollToPage(2) } },
                                onIrAjustes = { coroutineScope.launch { pagerState.animateScrollToPage(3) } },
                                mostrarBottomBar = false
                            )

                            1 -> HistorialScreen(
                                historial = listaHistorialCompartida,
                                userName = userName,
                                onVerReporteDetalle = { evaluacionSeleccionada ->
                                    scanResult = evaluacionSeleccionada.resultado
                                    selectedBitmap = evaluacionSeleccionada.bitmap ?: if (!evaluacionSeleccionada.fotoPath.isNullOrBlank()) {
                                        try {
                                            val f = File(evaluacionSeleccionada.fotoPath)
                                            if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
                                        } catch (_: Exception) { null }
                                    } else null
                                    currentScreen = AppScreen.REPORT
                                },
                                onDispararCamara = { abrirCamaraConGps() },
                                onIrInicio = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                                onIrLotes = { coroutineScope.launch { pagerState.animateScrollToPage(2) } },
                                onIrAjustes = { coroutineScope.launch { pagerState.animateScrollToPage(3) } },
                                onVolver = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                                mostrarBottomBar = false
                            )

                            2 -> LotesScreen(
                                lotes = listaLotesCompartida,
                                papeleraLotes = papeleraLotesCompartida,
                                loteActivoActual = currentParcela,
                                onSeleccionarLoteActivo = { loteSeleccionado ->
                                    currentParcela = loteSeleccionado
                                    coroutineScope.launch { pagerState.animateScrollToPage(0) }
                                },
                                onIniciarEvaluacionEnLote = { loteNombre ->
                                    currentParcela = "$loteNombre • Fresa Albión"
                                    abrirCamaraConGps()
                                },
                                onIrInicio = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                                onIrHistorial = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                                onIrAjustes = { coroutineScope.launch { pagerState.animateScrollToPage(3) } },
                                onDispararCamara = { abrirCamaraConGps() },
                                onVolver = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                                mostrarBottomBar = false
                            )

                            3 -> SettingsScreen(
                                userName = userName,
                                serverUrl = NetworkClient.currentBaseUrl,
                                lotes = listaLotesCompartida,
                                papeleraLotes = papeleraLotesCompartida,
                                onGuardarNombre = { nuevo ->
                                    userName = nuevo
                                    prefs.edit().putString("user_name", nuevo).apply()
                                },
                                onGuardarServerUrl = { nuevaUrl ->
                                    NetworkClient.setBaseUrl(nuevaUrl)
                                    prefs.edit().putString("server_url", nuevaUrl).apply()
                                },
                                onCerrarSesion = {
                                    prefs.edit()
                                        .remove("user_name")
                                        .remove("user_email")
                                        .remove("user_role")
                                        .apply()
                                    userName = ""
                                    userEmail = ""
                                    listaLotesCompartida.clear()
                                    listaHistorialCompartida.clear()
                                    currentScreen = AppScreen.LOGIN
                                },
                                onIrInicio = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                                onIrHistorial = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                                onIrLotes = { coroutineScope.launch { pagerState.animateScrollToPage(2) } },
                                onDispararCamara = { abrirCamaraConGps() },
                                onVolver = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                                mostrarBottomBar = false
                            )
                        }
                    }
                }
            }
        }

        // Diálogo de carga al analizar con la IA
        if (isLoading) {
            Dialog(onDismissRequest = {}) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Analizando muestra fitopatológica...",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
