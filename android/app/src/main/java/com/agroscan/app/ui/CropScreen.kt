package com.agroscan.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agroscan.app.ui.theme.*
import kotlin.math.max
import kotlin.math.min

private enum class DragHandle {
    NONE,
    TOP,
    BOTTOM,
    LEFT,
    RIGHT,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    INSIDE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropScreen(
    originalBitmap: Bitmap,
    onCropConfirmed: (Bitmap) -> Unit,
    onRetake: () -> Unit,
    onCancel: () -> Unit
) {
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    // Coordenadas del recuadro de recorte dentro de la pantalla
    var cropLeft by remember { mutableFloatStateOf(0f) }
    var cropTop by remember { mutableFloatStateOf(0f) }
    var cropRight by remember { mutableFloatStateOf(0f) }
    var cropBottom by remember { mutableFloatStateOf(0f) }

    var imgRect by remember { mutableStateOf(Rect.Zero) }
    var inicializado by remember { mutableStateOf(false) }

    var activeHandle by remember { mutableStateOf(DragHandle.NONE) }

    val density = LocalDensity.current
    val touchThreshold = with(density) { 32.dp.toPx() } // Área sensible al toque de bordes y esquinas
    val minBoxSize = with(density) { 60.dp.toPx() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Revisión de Muestra", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextDark)
                        Text("Ajusta los bordes para encuadrar la fresa", fontSize = 12.sp, color = TextGray)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "Cancelar", tint = TextDark)
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            // Restablecer recorte a toda la foto
                            cropLeft = imgRect.left
                            cropTop = imgRect.top
                            cropRight = imgRect.right
                            cropBottom = imgRect.bottom
                        }
                    ) {
                        Text("Foto Completa", color = AgroGreenPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AgroMintBackground)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ================= 1. VISOR CON BORDES INDEPENDIENTES =================
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onSizeChanged { size ->
                        containerSize = size
                        if (size.width > 0 && size.height > 0) {
                            // Calcular el rectángulo donde cabe la imagen con fit-center
                            val bitmapW = originalBitmap.width.toFloat()
                            val bitmapH = originalBitmap.height.toFloat()
                            val scale = min(size.width / bitmapW, size.height / bitmapH)

                            val drawW = bitmapW * scale
                            val drawH = bitmapH * scale
                            val left = (size.width - drawW) / 2f
                            val top = (size.height - drawH) / 2f

                            imgRect = Rect(left, top, left + drawW, top + drawH)

                            if (!inicializado) {
                                // Inicialmente dejamos un margen suave del 10%
                                val marginX = drawW * 0.08f
                                val marginY = drawH * 0.08f
                                cropLeft = left + marginX
                                cropRight = left + drawW - marginX
                                cropTop = top + marginY
                                cropBottom = top + drawH - marginY
                                inicializado = true
                            }
                        }
                    }
                    .pointerInput(imgRect) {
                        detectDragGestures(
                            onDragStart = { pos ->
                                activeHandle = detectarHandle(
                                    x = pos.x,
                                    y = pos.y,
                                    left = cropLeft,
                                    top = cropTop,
                                    right = cropRight,
                                    bottom = cropBottom,
                                    threshold = touchThreshold
                                )
                            },
                            onDragEnd = {
                                activeHandle = DragHandle.NONE
                            },
                            onDragCancel = {
                                activeHandle = DragHandle.NONE
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val dx = dragAmount.x
                                val dy = dragAmount.y

                                when (activeHandle) {
                                    DragHandle.LEFT -> {
                                        cropLeft = (cropLeft + dx).coerceIn(imgRect.left, cropRight - minBoxSize)
                                    }
                                    DragHandle.RIGHT -> {
                                        cropRight = (cropRight + dx).coerceIn(cropLeft + minBoxSize, imgRect.right)
                                    }
                                    DragHandle.TOP -> {
                                        cropTop = (cropTop + dy).coerceIn(imgRect.top, cropBottom - minBoxSize)
                                    }
                                    DragHandle.BOTTOM -> {
                                        cropBottom = (cropBottom + dy).coerceIn(cropTop + minBoxSize, imgRect.bottom)
                                    }
                                    DragHandle.TOP_LEFT -> {
                                        cropLeft = (cropLeft + dx).coerceIn(imgRect.left, cropRight - minBoxSize)
                                        cropTop = (cropTop + dy).coerceIn(imgRect.top, cropBottom - minBoxSize)
                                    }
                                    DragHandle.TOP_RIGHT -> {
                                        cropRight = (cropRight + dx).coerceIn(cropLeft + minBoxSize, imgRect.right)
                                        cropTop = (cropTop + dy).coerceIn(imgRect.top, cropBottom - minBoxSize)
                                    }
                                    DragHandle.BOTTOM_LEFT -> {
                                        cropLeft = (cropLeft + dx).coerceIn(imgRect.left, cropRight - minBoxSize)
                                        cropBottom = (cropBottom + dy).coerceIn(cropTop + minBoxSize, imgRect.bottom)
                                    }
                                    DragHandle.BOTTOM_RIGHT -> {
                                        cropRight = (cropRight + dx).coerceIn(cropLeft + minBoxSize, imgRect.right)
                                        cropBottom = (cropBottom + dy).coerceIn(cropTop + minBoxSize, imgRect.bottom)
                                    }
                                    DragHandle.INSIDE -> {
                                        val boxW = cropRight - cropLeft
                                        val boxH = cropBottom - cropTop

                                        var newL = cropLeft + dx
                                        var newT = cropTop + dy
                                        var newR = newL + boxW
                                        var newB = newT + boxH

                                        if (newL < imgRect.left) {
                                            newL = imgRect.left
                                            newR = newL + boxW
                                        }
                                        if (newR > imgRect.right) {
                                            newR = imgRect.right
                                            newL = newR - boxW
                                        }
                                        if (newT < imgRect.top) {
                                            newT = imgRect.top
                                            newB = newT + boxH
                                        }
                                        if (newB > imgRect.bottom) {
                                            newB = imgRect.bottom
                                            newT = newB - boxH
                                        }

                                        cropLeft = newL
                                        cropTop = newT
                                        cropRight = newR
                                        cropBottom = newB
                                    }
                                    DragHandle.NONE -> {}
                                }
                            }
                        )
                    }
            ) {
                val imageBitmap = remember(originalBitmap) { originalBitmap.asImageBitmap() }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (imgRect.width > 0 && imgRect.height > 0) {
                        // 1. Dibujar la imagen ajustada
                        drawImage(
                            image = imageBitmap,
                            dstOffset = androidx.compose.ui.unit.IntOffset(imgRect.left.toInt(), imgRect.top.toInt()),
                            dstSize = androidx.compose.ui.unit.IntSize(imgRect.width.toInt(), imgRect.height.toInt())
                        )

                        // 2. Máscara oscura exterior
                        val darkPath = Path().apply {
                            fillType = PathFillType.EvenOdd
                            addRect(Rect(0f, 0f, size.width, size.height))
                            addRect(Rect(cropLeft, cropTop, cropRight, cropBottom))
                        }
                        drawPath(darkPath, color = Color.Black.copy(alpha = 0.55f))

                        // 3. Borde del recuadro
                        val boxW = cropRight - cropLeft
                        val boxH = cropBottom - cropTop
                        drawRect(
                            color = Color.White,
                            topLeft = Offset(cropLeft, cropTop),
                            size = Size(boxW, boxH),
                            style = Stroke(width = 1.5.dp.toPx())
                        )

                        // 4. Guías de tercios interiores
                        val thirdW = boxW / 3f
                        val thirdH = boxH / 3f
                        drawLine(Color.White.copy(alpha = 0.3f), Offset(cropLeft + thirdW, cropTop), Offset(cropLeft + thirdW, cropBottom), 1f)
                        drawLine(Color.White.copy(alpha = 0.3f), Offset(cropLeft + 2 * thirdW, cropTop), Offset(cropLeft + 2 * thirdW, cropBottom), 1f)
                        drawLine(Color.White.copy(alpha = 0.3f), Offset(cropLeft, cropTop + thirdH), Offset(cropRight, cropTop + thirdH), 1f)
                        drawLine(Color.White.copy(alpha = 0.3f), Offset(cropLeft, cropTop + 2 * thirdH), Offset(cropRight, cropTop + 2 * thirdH), 1f)

                        // 5. Agarraderas en las 4 esquinas (estilo WhatsApp / Galería)
                        val cornerLen = 22.dp.toPx()
                        val cornerStroke = 4.dp.toPx()
                        val cColor = Color.White

                        // Esquina Superior Izquierda
                        drawLine(cColor, Offset(cropLeft - 1, cropTop), Offset(cropLeft + cornerLen, cropTop), cornerStroke)
                        drawLine(cColor, Offset(cropLeft, cropTop - 1), Offset(cropLeft, cropTop + cornerLen), cornerStroke)

                        // Esquina Superior Derecha
                        drawLine(cColor, Offset(cropRight + 1, cropTop), Offset(cropRight - cornerLen, cropTop), cornerStroke)
                        drawLine(cColor, Offset(cropRight, cropTop - 1), Offset(cropRight, cropTop + cornerLen), cornerStroke)

                        // Esquina Inferior Izquierda
                        drawLine(cColor, Offset(cropLeft - 1, cropBottom), Offset(cropLeft + cornerLen, cropBottom), cornerStroke)
                        drawLine(cColor, Offset(cropLeft, cropBottom + 1), Offset(cropLeft, cropBottom - cornerLen), cornerStroke)

                        // Esquina Inferior Derecha
                        drawLine(cColor, Offset(cropRight + 1, cropBottom), Offset(cropRight - cornerLen, cropBottom), cornerStroke)
                        drawLine(cColor, Offset(cropRight, cropBottom + 1), Offset(cropRight, cropBottom - cornerLen), cornerStroke)

                        // 6. Agarraderas centrales de cada lado
                        val handleLen = 16.dp.toPx()
                        val midX = cropLeft + boxW / 2f
                        val midY = cropTop + boxH / 2f

                        // Borde superior
                        drawLine(cColor, Offset(midX - handleLen / 2, cropTop), Offset(midX + handleLen / 2, cropTop), cornerStroke)
                        // Borde inferior
                        drawLine(cColor, Offset(midX - handleLen / 2, cropBottom), Offset(midX + handleLen / 2, cropBottom), cornerStroke)
                        // Borde izquierdo
                        drawLine(cColor, Offset(cropLeft, midY - handleLen / 2), Offset(cropLeft, midY + handleLen / 2), cornerStroke)
                        // Borde derecho
                        drawLine(cColor, Offset(cropRight, midY - handleLen / 2), Offset(cropRight, midY + handleLen / 2), cornerStroke)
                    }
                }
            }

            // ================= 2. BOTONES: "TOMAR DE NUEVO" Y "ANALIZAR" =================
            Surface(
                color = AgroCardWhite,
                shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Botón Tomar de Nuevo
                    OutlinedButton(
                        onClick = onRetake,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = TextDark, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Tomar de nuevo", fontSize = 13.sp, color = TextDark, fontWeight = FontWeight.SemiBold)
                    }

                    // Botón Analizar con Recorte Aplicado
                    Button(
                        onClick = {
                            if (imgRect.width > 0 && imgRect.height > 0) {
                                val scaleX = originalBitmap.width.toFloat() / imgRect.width
                                val scaleY = originalBitmap.height.toFloat() / imgRect.height

                                val startX = ((cropLeft - imgRect.left) * scaleX).toInt().coerceIn(0, originalBitmap.width - 1)
                                val startY = ((cropTop - imgRect.top) * scaleY).toInt().coerceIn(0, originalBitmap.height - 1)
                                val cropW = ((cropRight - cropLeft) * scaleX).toInt().coerceIn(1, originalBitmap.width - startX)
                                val cropH = ((cropBottom - cropTop) * scaleY).toInt().coerceIn(1, originalBitmap.height - startY)

                                val recortado = Bitmap.createBitmap(originalBitmap, startX, startY, cropW, cropH)
                                onCropConfirmed(recortado)
                            } else {
                                onCropConfirmed(originalBitmap)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AgroGreenPrimary),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1.2f)
                            .height(50.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Analizar Fresa", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

/**
 * Detecta qué borde, esquina o centro está tocando el dedo
 */
private fun detectarHandle(
    x: Float,
    y: Float,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    threshold: Float
): DragHandle {
    val nearLeft = kotlin.math.abs(x - left) <= threshold
    val nearRight = kotlin.math.abs(x - right) <= threshold
    val nearTop = kotlin.math.abs(y - top) <= threshold
    val nearBottom = kotlin.math.abs(y - bottom) <= threshold

    return when {
        nearLeft && nearTop -> DragHandle.TOP_LEFT
        nearRight && nearTop -> DragHandle.TOP_RIGHT
        nearLeft && nearBottom -> DragHandle.BOTTOM_LEFT
        nearRight && nearBottom -> DragHandle.BOTTOM_RIGHT
        nearLeft && y in (top..bottom) -> DragHandle.LEFT
        nearRight && y in (top..bottom) -> DragHandle.RIGHT
        nearTop && x in (left..right) -> DragHandle.TOP
        nearBottom && x in (left..right) -> DragHandle.BOTTOM
        x in (left..right) && y in (top..bottom) -> DragHandle.INSIDE
        else -> DragHandle.NONE
    }
}