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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
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
import com.agroscan.app.data.LoteItem
import com.agroscan.app.data.EvaluacionHistorial
import com.agroscan.app.ui.HistorialScreen
import com.agroscan.app.ui.CropScreen
import com.agroscan.app.data.NetworkClient
import com.agroscan.app.ui.HomeScreen
import com.agroscan.app.ui.LoginScreen
import com.agroscan.app.ui.LotesScreen
import com.agroscan.app.ui.ReportScreen
import com.agroscan.app.ui.SettingsScreen
import com.agroscan.app.ui.theme.*
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
    CROP
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

    var userName by remember { mutableStateOf(prefs.getString("user_name", "") ?: "") }
    var currentParcela by remember { mutableStateOf("Lote 1 (Parcela Norte) • Fresa Albión") }

    // FUENTE ÚNICA DE VERDAD COMPARTIDA ENTRE LOTES Y AJUSTES
    val listaLotesCompartida = remember {
        mutableStateListOf(
            LoteItem(1, "Lote 1", "Albión", "Parcela Norte", "Óptimo", "Hoy 08:30 AM")
        )
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
    var showDialogOpciones by remember { mutableStateOf(false) }

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

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            uncroppedBitmap = bitmap
            currentScreen = AppScreen.CROP
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
            uncroppedBitmap = bitmap
            currentScreen = AppScreen.CROP
        }
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
                    onIniciarEvaluacion = {
                        showDialogOpciones = true
                    },
                    onIrHistorial = {
                        currentScreen = AppScreen.HISTORIAL
                    },
                    onIrLotes = {
                        currentScreen = AppScreen.LOTES
                    },
                    onIrAjustes = {
                        currentScreen = AppScreen.SETTINGS
                    }
                )
            }

            // 3. GESTIÓN DE LOTES (Sincronizada con Ajustes)
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
                        showDialogOpciones = true
                    },
                    onVolver = {
                        currentScreen = AppScreen.HOME
                    }
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
                            // 1. Determinar el nuevo estado fitosanitario según el semáforo devuelto por la IA
                            val nuevoEstado = when (result.semaforo.lowercase()) {
                                "rojo" -> "Crítico"
                                "amarillo" -> "En Revisión"
                                else -> "Óptimo"
                            }

                            // 2. Formatear la fecha/hora y resumen de evaluación real
                            val horaActual = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date())
                            val resumenEvaluacion = "$horaActual • Madurez ${result.madurezPorcentaje}% • ${if (result.semaforo.lowercase() == "verde") "Óptima" else result.dictamen}"

                            // 3. Actualizar el lote en la lista compartida en tiempo real
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

                            // 4. Guardar en el Historial de Inspecciones
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

                            // 5. Actualizar la parcela activa en el Dashboard
                            currentParcela = "${lote.nombre} (${lote.ubicacion}) • Fresa $variedad"

                            Toast.makeText(
                                context,
                                "Evaluación guardada en ${lote.nombre}: Estado $nuevoEstado 🍓",
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

            // 5. AJUSTES DEL SISTEMA (¡AHORA 100% SINCRONIZADA!)
            AppScreen.SETTINGS -> {
                SettingsScreen(
                    userName = userName,
                    serverUrl = NetworkClient.currentBaseUrl,
                    lotes = listaLotesCompartida, // Le pasamos la lista compartida
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
                    onVolver = {
                        currentScreen = AppScreen.HOME
                    }
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
                    onDispararCamara = { showDialogOpciones = true },
                    onIrInicio = { currentScreen = AppScreen.HOME },
                    onIrLotes = { currentScreen = AppScreen.LOTES },
                    onIrAjustes = { currentScreen = AppScreen.SETTINGS },
                    onVolver = { currentScreen = AppScreen.HOME }
                )
            }

            // 7. ENCUADRE Y RECORTE DE MUESTRA
            AppScreen.CROP -> {
                uncroppedBitmap?.let { rawBitmap ->
                    CropScreen(
                        originalBitmap = rawBitmap,
                        onCropConfirmed = { croppedBitmap ->
                            enviarFotoAlServidor(croppedBitmap)
                        },
                        onRetake = {
                            uncroppedBitmap = null
                            showDialogOpciones = true
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

        // Diálogo para elegir Cámara o Galería
        if (showDialogOpciones) {
            AlertDialog(
                onDismissRequest = { showDialogOpciones = false },
                title = {
                    Text("Nueva Evaluación", fontWeight = FontWeight.Bold, color = AgroGreenPrimary)
                },
                text = {
                    Text("Selecciona el método de captura de la fruta:", color = TextDark)
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDialogOpciones = false
                            cameraLauncher.launch(null)
                        }
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = AgroGreenPrimary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Tomar Foto", color = AgroGreenPrimary, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showDialogOpciones = false
                            galleryLauncher.launch("image/*")
                        }
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = TextGray)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Galería", color = TextGray)
                    }
                },
                containerColor = AgroCardWhite,
                shape = RoundedCornerShape(20.dp)
            )
        }

        // Modal de Carga con IA
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
                            text = "Analizando con IA...",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = AgroGreenPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Evaluando presencia de Botrytis, grado de madurez y fungicidas...",
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
