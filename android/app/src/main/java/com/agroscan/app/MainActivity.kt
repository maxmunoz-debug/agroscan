package com.agroscan.app

import android.content.Context
import android.graphics.Bitmap
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    var currentParcela by remember { mutableStateOf("Lote 1 (Parcela Norte) • Fresa Albión") }

    // FUENTE PERSISTENTE DE LOTES (Se guardan en memoria permanente del teléfono)
    val lotesJson = prefs.getString("lotes_json", null)
    val lotesCargados = remember {
        if (!lotesJson.isNullOrBlank()) {
            try {
                val type = object : TypeToken<List<LoteItem>>() {}.type
                gson.fromJson<List<LoteItem>>(lotesJson, type) ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    val listaLotesCompartida = remember {
        mutableStateListOf(
            *if (lotesCargados.isNotEmpty()) {
                lotesCargados.toTypedArray()
            } else {
                arrayOf(
                    LoteItem(1, "Lote 1", "Albión", "Parcela Norte", "Óptimo", "Hoy 08:30 AM", latitud = -12.046374, longitud = -77.042793),
                    LoteItem(2, "Lote 2", "Monterrey", "Sector Sur", "Óptimo", "Ayer 16:00 PM", latitud = -12.046500, longitud = -77.042900)
                )
            }
        )
    }

    // Persistencia reactiva: guarda cambios automáticamente cada vez que se agrega/edita/elimina un lote
    LaunchedEffect(listaLotesCompartida.size, listaLotesCompartida.map { "${it.id}-${it.nombre}-${it.latitud}-${it.longitud}-${it.fotoUri}" }) {
        val json = gson.toJson(listaLotesCompartida.toList())
        prefs.edit().putString("lotes_json", json).apply()
    }

    val papeleraLotesCompartida = remember { mutableStateListOf<LoteItem>() }
    val listaHistorialCompartida = remember { mutableStateListOf<EvaluacionHistorial>() }

    var currentScreen by remember {
        mutableStateOf(if (userName.isNotEmpty()) AppScreen.HOME else AppScreen.LOGIN)
    }

    BackHandler(enabled = (currentScreen != AppScreen.HOME && currentScreen != AppScreen.LOGIN)) {
        currentScreen = AppScreen.HOME
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
                    "Error conectando con AgroScan Backend: ${e.localizedMessage ?: "Verifica que el servidor esté corriendo"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // Lanzador para CARGAR FOTOS de la galería
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
            // Enviar directamente la foto cargada al modelo de IA
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
                    onLoginExitoso = { nombreIngresado ->
                        userName = nombreIngresado
                        prefs.edit().putString("user_name", nombreIngresado).apply()
                        currentScreen = AppScreen.HOME
                    }
                )
            }

            // 2. DASHBOARD DE INICIO
            AppScreen.HOME -> {
                HomeScreen(
                    userName = userName,
                    parcelaActual = currentParcela,
                    historial = listaHistorialCompartida,
                    lotes = listaLotesCompartida,
                    onIniciarEvaluacion = { abrirCamaraConGps() },
                    onCambiarParcela = { nuevaParcela -> currentParcela = nuevaParcela },
                    onIrHistorial = { currentScreen = AppScreen.HISTORIAL },
                    onIrLotes = { currentScreen = AppScreen.LOTES },
                    onIrAjustes = { currentScreen = AppScreen.SETTINGS }
                )
            }

            // 3. GESTIÓN DE LOTES (Con Barra Inferior Permanente)
            AppScreen.LOTES -> {
                LotesScreen(
                    lotes = listaLotesCompartida,
                    papeleraLotes = papeleraLotesCompartida,
                    loteActivoActual = currentParcela,
                    onSeleccionarLoteActivo = { loteSeleccionado ->
                        currentParcela = loteSeleccionado
                        currentScreen = AppScreen.HOME
                    },
                    onIniciarEvaluacionEnLote = { loteNombre ->
                        currentParcela = "$loteNombre • Fresa Albión"
                        abrirCamaraConGps()
                    },
                    onIrInicio = { currentScreen = AppScreen.HOME },
                    onIrHistorial = { currentScreen = AppScreen.HISTORIAL },
                    onIrAjustes = { currentScreen = AppScreen.SETTINGS },
                    onDispararCamara = { abrirCamaraConGps() },
                    onVolver = { currentScreen = AppScreen.HOME }
                )
            }

            // 4. REPORTE FITOSANITARIO CON FUNGICIDAS
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

                            val horaCompleta = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
                            listaHistorialCompartida.add(
                                0,
                                EvaluacionHistorial(
                                    fechaHora = horaCompleta,
                                    loteNombre = "${lote.nombre} (${lote.ubicacion})",
                                    variedad = variedad,
                                    notas = notas,
                                    resultado = result,
                                    bitmap = selectedBitmap
                                )
                            )

                            currentParcela = "${lote.nombre} (${lote.ubicacion}) • Fresa $variedad"

                            Toast.makeText(
                                context,
                                "Evaluación guardada en ${lote.nombre}: Estado $nuevoEstado",
                                Toast.LENGTH_LONG
                            ).show()
                            currentScreen = AppScreen.HOME
                        },
                        onVolver = {
                            currentScreen = AppScreen.HOME
                        }
                    )
                }
            }

            // 5. AJUSTES DEL SISTEMA (Con Barra Inferior Permanente)
            AppScreen.SETTINGS -> {
                SettingsScreen(
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
                        prefs.edit().remove("user_name").apply()
                        userName = ""
                        currentScreen = AppScreen.LOGIN
                    },
                    onIrInicio = { currentScreen = AppScreen.HOME },
                    onIrHistorial = { currentScreen = AppScreen.HISTORIAL },
                    onIrLotes = { currentScreen = AppScreen.LOTES },
                    onDispararCamara = { abrirCamaraConGps() },
                    onVolver = { currentScreen = AppScreen.HOME }
                )
            }

            // 6. HISTORIAL DE INSPECCIONES
            AppScreen.HISTORIAL -> {
                HistorialScreen(
                    historial = listaHistorialCompartida,
                    userName = userName,
                    onVerReporteDetalle = { evaluacionSeleccionada ->
                        scanResult = evaluacionSeleccionada.resultado
                        selectedBitmap = evaluacionSeleccionada.bitmap
                        currentScreen = AppScreen.REPORT
                    },
                    onDispararCamara = { abrirCamaraConGps() },
                    onIrInicio = { currentScreen = AppScreen.HOME },
                    onIrLotes = { currentScreen = AppScreen.LOTES },
                    onIrAjustes = { currentScreen = AppScreen.SETTINGS },
                    onVolver = { currentScreen = AppScreen.HOME }
                )
            }

            // 7. CÁMARA INTELIGENTE IN-APP (CameraX + ImageAnalysis en 1 Solo Paso)
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
                        // Cargar foto de galería
                        galleryLauncher.launch("image/*")
                    },
                    onVolver = {
                        currentScreen = AppScreen.HOME
                    }
                )
            }

            // 8. ENCUADRE Y RECORTE MANUAL (Fallback)
            AppScreen.CROP -> {
                uncroppedBitmap?.let { rawBitmap ->
                    CropScreen(
                        originalBitmap = rawBitmap,
                        onCropConfirmed = { croppedBitmap ->
                            enviarFotoAlServidor(croppedBitmap)
                        },
                        onRetake = {
                            uncroppedBitmap = null
                            abrirCamaraConGps()
                        },
                        onCancel = {
                            currentScreen = AppScreen.HOME
                        }
                    )
                } ?: run {
                    currentScreen = AppScreen.HOME
                }
            }
        }

        // Modal de Carga con IA (Animación de Análisis)
        if (isLoading) {
            Dialog(onDismissRequest = { }) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = AgroCardWhite,
                    shadowElevation = 8.dp,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = AgroGreenPrimary,
                            strokeWidth = 4.dp,
                            modifier = Modifier.size(50.dp)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "Analizando con Gemini AI...",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = AgroGreenPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Inspeccionando Botrytis cinerea, madurez y dosificación fitosanitaria...",
                            fontSize = 12.sp,
                            color = TextGray,
                            lineHeight = 16.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
