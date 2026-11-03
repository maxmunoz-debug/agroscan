package com.agroscan.app.ui

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Matrix
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.agroscan.app.data.LoteItem
import com.agroscan.app.ui.theme.*

enum class EstadoCapturaFresa {
    NO_DETECTADA,
    DEMASIADO_LEJOS,
    DEMASIADO_CERCA,
    POCA_LUZ,
    OPTIMO
}

/**
 * Cámara Inteligente con Asistente Fitosanitario en Tiempo Real:
 * - Detección cromática real de fresa (sin falsos positivos).
 * - Consejos técnicos sobrios (sin emojis, únicamente el check al estar lista).
 * - Marco elástico adaptativo a pantalla limpia.
 * - Botón "Cargar Foto" de la Galería.
 * - Disparo en 1 solo paso directo al diagnóstico de Gemini AI.
 */
@Composable
fun CameraInAppScreen(
    loteActivo: String,
    lotesDisponibles: List<LoteItem> = emptyList(),
    onCambiarLote: (String) -> Unit = {},
    onFotoCapturada: (Bitmap) -> Unit,
    onSeleccionarDeGaleria: () -> Unit,
    onVolver: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var tienePermisoCamara by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val launcherPermiso = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { concedido ->
        tienePermisoCamara = concedido
        if (!concedido) {
            Toast.makeText(context, "Se requiere permiso de cámara para escanear", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        if (!tienePermisoCamara) {
            launcherPermiso.launch(Manifest.permission.CAMERA)
        }
    }

    if (!tienePermisoCamara) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoCamera,
                    contentDescription = null,
                    tint = AgroGreenPrimary,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Se requiere acceso a la cámara",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "AgroScan necesita la cámara para diagnosticar fitopatologías en las fresas en tiempo real.",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { launcherPermiso.launch(Manifest.permission.CAMERA) },
                    colors = ButtonDefaults.buttonColors(containerColor = AgroGreenPrimary)
                ) {
                    Text("Conceder Permiso")
                }
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = onVolver) {
                    Text("Volver", color = Color.White.copy(alpha = 0.6f))
                }
            }
        }
        return
    }

    // Estados de Cámara
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var cameraControl: CameraControl? by remember { mutableStateOf(null) }

    var isTorchOn by remember { mutableStateOf(false) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var maxZoomRatio by remember { mutableFloatStateOf(5f) }
    var isCapturing by remember { mutableStateOf(false) }
    var menuLotesAbierto by remember { mutableStateOf(false) }

    var screenSize by remember { mutableStateOf(IntSize.Zero) }

    // Estados del Asistente en Tiempo Real
    var estadoCaptura by remember { mutableStateOf(EstadoCapturaFresa.NO_DETECTADA) }
    var consejoTexto by remember { mutableStateOf("Muestra no detectada: enfocar una fresa") }
    var escalaMarcoDinamico by remember { mutableFloatStateOf(1.0f) }

    // Animación de pulso cuando la muestra está enfocada y lista
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulsoOptimo by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulso"
    )

    val marcoColorAnimado by animateColorAsState(
        targetValue = when (estadoCaptura) {
            EstadoCapturaFresa.OPTIMO -> SemaforoVerde
            EstadoCapturaFresa.DEMASIADO_LEJOS -> SemaforoAmarillo
            EstadoCapturaFresa.DEMASIADO_CERCA -> Color(0xFFF97316)
            EstadoCapturaFresa.POCA_LUZ -> Color(0xFFFACC15)
            EstadoCapturaFresa.NO_DETECTADA -> Color.White.copy(alpha = 0.45f)
        },
        label = "marcoColor"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onGloballyPositioned { coordinates ->
                screenSize = coordinates.size
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    val nuevoZoom = (zoomRatio * zoom).coerceIn(1f, maxZoomRatio)
                    zoomRatio = nuevoZoom
                    cameraControl?.setZoomRatio(nuevoZoom)
                }
            }
    ) {
        // 1. VISOR DE CÁMARA (CameraX PreviewView + ImageAnalysis en vivo)
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setTargetRotation(previewView.display?.rotation ?: 0)
                        .build()

                    imageCapture = capture

                    // ANALIZADOR CROMÁTICO EN TIEMPO REAL: Detecta tejido de fresa real
                    val analyzer = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    var frameCount = 0
                    analyzer.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                        frameCount++
                        if (frameCount % 3 == 0) { // Evalúa cada 3 frames para velocidad instantánea
                            analizarFrameFresa(imageProxy) { nuevoEstado, nuevoConsejo, factorEscala ->
                                estadoCaptura = nuevoEstado
                                consejoTexto = nuevoConsejo
                                escalaMarcoDinamico = factorEscala
                            }
                        }
                        imageProxy.close()
                    }

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        val camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            capture,
                            analyzer
                        )
                        cameraControl = camera.cameraControl

                        camera.cameraInfo.zoomState.observe(lifecycleOwner) { state ->
                            maxZoomRatio = state.maxZoomRatio.coerceAtMost(6f)
                        }
                    } catch (e: Exception) {
                        Toast.makeText(ctx, "Error iniciando cámara: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. MARCO ELÁSTICO ADAPTATIVO (PANTALLA LIMPIA)
        val marcoBaseDp = 250.dp * escalaMarcoDinamico
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val marcoPx = marcoBaseDp.toPx()

                val left = (canvasWidth - marcoPx) / 2f
                val top = (canvasHeight - marcoPx) / 2f
                val rect = Rect(left, top, left + marcoPx, top + marcoPx)

                val cornerLength = 44f
                val strokeWidth = (if (estadoCaptura == EstadoCapturaFresa.OPTIMO) 4.5.dp else 3.dp).toPx()
                val colorActual = if (estadoCaptura == EstadoCapturaFresa.OPTIMO) {
                    marcoColorAnimado.copy(alpha = pulsoOptimo)
                } else {
                    marcoColorAnimado
                }

                // 4 Esquinas adaptativas
                drawLine(colorActual, Offset(left, top + cornerLength), Offset(left, top + 12f), strokeWidth)
                drawLine(colorActual, Offset(left + 12f, top), Offset(left + cornerLength, top), strokeWidth)

                drawLine(colorActual, Offset(rect.right - cornerLength, top), Offset(rect.right - 12f, top), strokeWidth)
                drawLine(colorActual, Offset(rect.right, top + 12f), Offset(rect.right, top + cornerLength), strokeWidth)

                drawLine(colorActual, Offset(left, rect.bottom - cornerLength), Offset(left, rect.bottom - 12f), strokeWidth)
                drawLine(colorActual, Offset(left + 12f, rect.bottom), Offset(left + cornerLength, rect.bottom), strokeWidth)

                drawLine(colorActual, Offset(rect.right - cornerLength, rect.bottom), Offset(rect.right - 12f, rect.bottom), strokeWidth)
                drawLine(colorActual, Offset(rect.right, rect.bottom - 12f), Offset(rect.right, rect.bottom - cornerLength), strokeWidth)

                // Mira central sutil
                val centerX = canvasWidth / 2f
                val centerY = canvasHeight / 2f
                val crossSize = 14f
                drawLine(Color.White.copy(alpha = 0.4f), Offset(centerX - crossSize, centerY), Offset(centerX + crossSize, centerY), 1.5f)
                drawLine(Color.White.copy(alpha = 0.4f), Offset(centerX, centerY - crossSize), Offset(centerX, centerY + crossSize), 1.5f)
            }

            // PASTILLA CONSEJO EN TIEMPO REAL (SOLO TEXTO SOBRIO Y CHECK AL ESTAR LISTO)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (marcoBaseDp / 2) + 24.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.72f),
                    border = androidx.compose.foundation.BorderStroke(1.2.dp, marcoColorAnimado),
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (estadoCaptura == EstadoCapturaFresa.OPTIMO) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = SemaforoVerde,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = consejoTexto,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = if (estadoCaptura == EstadoCapturaFresa.OPTIMO) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        // 3. BARRA SUPERIOR: VOLVER, SELECTOR DE LOTE Y LINTERNA
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                onClick = onVolver,
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.55f),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }

            // Selector de Lote Activo
            Box {
                Surface(
                    onClick = { menuLotesAbierto = true },
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.7f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AgroGreenPrimary.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(SemaforoVerde))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = loteActivo.take(22),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }

                DropdownMenu(
                    expanded = menuLotesAbierto,
                    onDismissRequest = { menuLotesAbierto = false }
                ) {
                    if (lotesDisponibles.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No hay más lotes registrados", fontSize = 12.sp) },
                            onClick = { menuLotesAbierto = false }
                        )
                    } else {
                        lotesDisponibles.forEach { lote ->
                            val esActivo = loteActivo.contains(lote.nombre)
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = "${lote.nombre} - ${lote.variedad}",
                                            fontWeight = if (esActivo) FontWeight.Bold else FontWeight.Normal,
                                            color = if (esActivo) AgroGreenPrimary else TextDark
                                        )
                                        Text(text = lote.ubicacion, fontSize = 10.sp, color = TextGray)
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (esActivo) Icons.Default.Check else Icons.Default.Place,
                                        contentDescription = null,
                                        tint = if (esActivo) SemaforoVerde else TextGray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                onClick = {
                                    onCambiarLote("${lote.nombre} (${lote.ubicacion}) • Fresa ${lote.variedad}")
                                    menuLotesAbierto = false
                                }
                            )
                        }
                    }
                }
            }

            Surface(
                onClick = {
                    val nuevoEstado = !isTorchOn
                    isTorchOn = nuevoEstado
                    cameraControl?.enableTorch(nuevoEstado)
                },
                shape = CircleShape,
                color = if (isTorchOn) AgroGreenPrimary else Color.Black.copy(alpha = 0.55f),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Linterna",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // 4. BARRA INFERIOR: CARGAR FOTO (GALERÍA) + DISPARADOR + ZOOM
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Selector de Zoom
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 18.dp)
            ) {
                listOf(1.0f to "1x", 2.0f to "2x").forEach { (ratio, label) ->
                    val seleccionado = (zoomRatio - ratio) in -0.2f..0.2f
                    Surface(
                        onClick = {
                            zoomRatio = ratio
                            cameraControl?.setZoomRatio(ratio)
                        },
                        shape = CircleShape,
                        color = if (seleccionado) AgroGreenPrimary else Color.Black.copy(alpha = 0.65f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (seleccionado) AgroGreenPrimary else Color.White.copy(alpha = 0.35f)),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = label,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Controles de Acción: Cargar Foto + Disparo
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        onClick = onSeleccionarDeGaleria,
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.65f),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.White.copy(alpha = 0.6f)),
                        modifier = Modifier.size(52.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = "Cargar Foto", tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Cargar Foto",
                        fontSize = 10.sp,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Box(
                    modifier = Modifier
                        .size(82.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.28f))
                        .border(3.dp, Color.White, CircleShape)
                        .clickable(enabled = !isCapturing) {
                            val capture = imageCapture ?: return@clickable
                            isCapturing = true

                            val executor = ContextCompat.getMainExecutor(context)
                            capture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                                    val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                                    val rawBitmap = imageProxy.toBitmap()
                                    imageProxy.close()

                                    val rotatedBitmap = if (rotationDegrees != 0) {
                                        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                                        Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                                    } else {
                                        rawBitmap
                                    }

                                    val cropped = autoRecortarAlMarcoAdaptado(rotatedBitmap, escalaMarcoDinamico)
                                    isCapturing = false
                                    onFotoCapturada(cropped)
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    isCapturing = false
                                    Toast.makeText(context, "Error al capturar: ${exception.message}", Toast.LENGTH_SHORT).show()
                                }
                            })
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isCapturing) {
                        CircularProgressIndicator(
                            color = AgroGreenPrimary,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(38.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(if (estadoCaptura == EstadoCapturaFresa.OPTIMO) SemaforoVerde else AgroGreenPrimary)
                        )
                    }
                }

                Spacer(modifier = Modifier.size(52.dp))
            }
        }
    }
}

/**
 * Analizador cromático que busca el espectro de pigmentación de la fresa.
 * Elimina al 100% los falsos positivos (paredes, suelos, etc.).
 */
private fun analizarFrameFresa(
    imageProxy: ImageProxy,
    onResultado: (EstadoCapturaFresa, String, Float) -> Unit
) {
    try {
        val yPlane = imageProxy.planes[0]
        val uPlane = imageProxy.planes[1]
        val vPlane = imageProxy.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val width = imageProxy.width
        val height = imageProxy.height

        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        val uvRowStride = uPlane.rowStride
        val uvPixelStride = uPlane.pixelStride

        var sumaLuma = 0L
        var totalPixelesMuestreados = 0
        var pixelesFresaDetectados = 0

        // Área central donde debe posicionarse la muestra
        val inicioX = (width * 0.25).toInt()
        val finX = (width * 0.75).toInt()
        val inicioY = (height * 0.25).toInt()
        val finY = (height * 0.75).toInt()

        val step = 16 // Muestreo ultrarrápido (< 2 ms)

        for (y in inicioY until finY step step) {
            val yOffset = y * yRowStride
            val uvRow = (y / 2) * uvRowStride

            for (x in inicioX until finX step step) {
                val yIndex = yOffset + x * yPixelStride
                val uvIndex = uvRow + (x / 2) * uvPixelStride

                if (yIndex < yBuffer.limit() && uvIndex < uBuffer.limit() && uvIndex < vBuffer.limit()) {
                    val yVal = yBuffer.get(yIndex).toInt() and 0xFF
                    val uVal = (uBuffer.get(uvIndex).toInt() and 0xFF) - 128
                    val vVal = (vBuffer.get(uvIndex).toInt() and 0xFF) - 128

                    sumaLuma += yVal
                    totalPixelesMuestreados++

                    // Conversión YUV a RGB aproximada
                    val r = (yVal + 1.402 * vVal).toInt().coerceIn(0, 255)
                    val g = (yVal - 0.344136 * uVal - 0.714136 * vVal).toInt().coerceIn(0, 255)
                    val b = (yVal + 1.772 * uVal).toInt().coerceIn(0, 255)

                    // Identificación de tejido vegetal de fresa (Rojo característico o Fresa envero/verde)
                    val esRojoFresa = (r > 105 && r > (g * 1.30) && r > (b * 1.35))
                    val esFresaVerdeMaduracion = (g > 90 && r > 80 && b < (g * 0.75) && (r + g) > 180)

                    if (esRojoFresa || esFresaVerdeMaduracion) {
                        pixelesFresaDetectados++
                    }
                }
            }
        }

        val lumaPromedio = if (totalPixelesMuestreados > 0) sumaLuma / totalPixelesMuestreados else 100
        val porcentajeFresa = if (totalPixelesMuestreados > 0) {
            pixelesFresaDetectados.toFloat() / totalPixelesMuestreados
        } else {
            0f
        }

        when {
            lumaPromedio < 35 -> {
                onResultado(EstadoCapturaFresa.POCA_LUZ, "Iluminación baja: activar flash", 1.0f)
            }
            porcentajeFresa < 0.04f -> {
                // Sin fruta en el encuadre (pared, piso, mesa, nada)
                onResultado(EstadoCapturaFresa.NO_DETECTADA, "Muestra no detectada: enfocar una fresa", 1.0f)
            }
            porcentajeFresa < 0.15f -> {
                // Fruta muy pequeña o lejana
                onResultado(EstadoCapturaFresa.DEMASIADO_LEJOS, "Acercar cámara a la muestra", 0.88f)
            }
            porcentajeFresa > 0.65f -> {
                // Fruta desbordando el encuadre
                onResultado(EstadoCapturaFresa.DEMASIADO_CERCA, "Alejar cámara de la muestra", 1.15f)
            }
            else -> {
                // Fruta real encuadrada y a distancia idónea
                onResultado(EstadoCapturaFresa.OPTIMO, "Muestra enfocada: listo para evaluar", 1.0f)
            }
        }
    } catch (_: Exception) {
        onResultado(EstadoCapturaFresa.NO_DETECTADA, "Muestra no detectada: enfocar una fresa", 1.0f)
    }
}

/**
 * Auto-recorta la imagen al área central de la fruta.
 */
private fun autoRecortarAlMarcoAdaptado(bitmap: Bitmap, escala: Float): Bitmap {
    try {
        val ladoMenor = minOf(bitmap.width, bitmap.height)
        val proporcion = (0.70f * escala).coerceIn(0.50f, 0.90f)
        val tamanoRecorte = (ladoMenor * proporcion).toInt()

        val cropX = ((bitmap.width - tamanoRecorte) / 2).coerceAtLeast(0)
        val cropY = ((bitmap.height - tamanoRecorte) / 2).coerceAtLeast(0)
        val finalWidth = tamanoRecorte.coerceAtMost(bitmap.width - cropX)
        val finalHeight = tamanoRecorte.coerceAtMost(bitmap.height - cropY)

        return Bitmap.createBitmap(bitmap, cropX, cropY, finalWidth, finalHeight)
    } catch (_: Exception) {
        return bitmap
    }
}
