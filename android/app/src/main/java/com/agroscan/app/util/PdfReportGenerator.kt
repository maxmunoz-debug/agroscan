package com.agroscan.app.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import com.agroscan.app.R
import com.agroscan.app.data.EvaluacionHistorial
import java.io.File
import java.io.FileOutputStream

/**
 * Generador de Reportes Técnicos Fitosanitarios en PDF:
 * - Logo oficial con preservación exacta de proporciones (sin deformación).
 * - Diseño ejecutivo, sobrio y minimalista (Norma Institucional Tecsup 2026).
 * - Sin bloques de color invasivos, sin emojis y con tipografía de alta legibilidad.
 * - Ajuste de texto automático (word-wrap) para descripciones técnicas.
 * - Apertura automática inmediata en el lector de PDF del dispositivo.
 */
object PdfReportGenerator {

    fun generarYDescargarPdf(
        context: Context,
        evaluador: String,
        evaluacion: EvaluacionHistorial
    ): File? {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // Formato A4
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Paleta Sobria y Ejecutiva (Minimalismo Institucional)
        val textoOscuro = 0xFF0F172A.toInt()       // Slate 900
        val textoSecundario = 0xFF334155.toInt()   // Slate 700
        val textoMuted = 0xFF64748B.toInt()        // Slate 500
        val fondoTarjeta = 0xFFF8FAFC.toInt()      // Slate 50
        val bordeSuave = 0xFFCBD5E1.toInt()        // Slate 300
        val lineaDivisoria = 0xFFE2E8F0.toInt()    // Slate 200

        val (colorEstado, colorFondoEstado, textoEstado) = when (evaluacion.resultado.semaforo.lowercase()) {
            "rojo" -> Triple(0xFF991B1B.toInt(), 0xFFFEF2F2.toInt(), "DESCARTE • ALERTA FITOSANITARIA")
            "amarillo" -> Triple(0xFF9A3412.toInt(), 0xFFFFFBEB.toInt(), "REVISIÓN • ATENCIÓN MODERADA")
            else -> Triple(0xFF166534.toInt(), 0xFFF0FDF4.toInt(), "ÓPTIMO • APTO EXPORTACIÓN")
        }

        val margenIzquierdo = 45f
        val margenDerecho = 550f

        // ================= 1. CABECERA EJECUTIVA TECSUP CON LOGO PROPORCIONAL =================
        val logoBitmap = try {
            BitmapFactory.decodeResource(context.resources, R.drawable.agroscan_logo)?.let { raw ->
                val targetHeight = 42
                val targetWidth = (raw.width * (targetHeight.toFloat() / raw.height)).toInt()
                Bitmap.createScaledBitmap(raw, targetWidth, targetHeight, true)
            }
        } catch (_: Exception) { null }

        if (logoBitmap != null) {
            canvas.drawBitmap(logoBitmap, margenIzquierdo, 26f, null)
        }

        val textoX = if (logoBitmap != null) margenIzquierdo + logoBitmap.width + 12f else margenIzquierdo

        // Título de la Plataforma
        paint.color = textoOscuro
        paint.textSize = 21f
        paint.isFakeBoldText = true
        canvas.drawText("agroScan", textoX, 45f, paint)

        // Institución
        paint.color = textoSecundario
        paint.textSize = 11f
        paint.isFakeBoldText = true
        canvas.drawText("TECSUP", textoX + 100f, 43f, paint)

        // Subtítulo técnico
        paint.color = textoMuted
        paint.textSize = 9.5f
        paint.isFakeBoldText = false
        canvas.drawText("Informe Técnico Fitosanitario • Cultivo de Fresa (Fragaria x ananassa)", textoX, 62f, paint)

        // Metadatos de Cabecera Derecha
        paint.textAlign = Paint.Align.RIGHT
        paint.color = textoOscuro
        paint.textSize = 10f
        paint.isFakeBoldText = true
        val codigoDoc = "AS-${evaluacion.fechaHora.replace("/", "").replace(" ", "-").replace(":", "")}"
        canvas.drawText("DOC: $codigoDoc", margenDerecho, 44f, paint)

        paint.color = textoMuted
        paint.textSize = 9f
        paint.isFakeBoldText = false
        canvas.drawText("Fecha: ${evaluacion.fechaHora}", margenDerecho, 60f, paint)
        paint.textAlign = Paint.Align.LEFT

        // Línea divisoria superior fina y elegante
        paint.color = textoOscuro
        paint.strokeWidth = 1.5f
        canvas.drawLine(margenIzquierdo, 74f, margenIzquierdo + 35f, 74f, paint)
        paint.color = lineaDivisoria
        paint.strokeWidth = 1f
        canvas.drawLine(margenIzquierdo + 35f, 74f, margenDerecho, 74f, paint)

        // ================= 2. DATOS GENERALES DE LA INSPECCIÓN =================
        val metaY = 88f
        val metaAlto = 64f
        val rectMeta = RectF(margenIzquierdo, metaY, margenDerecho, metaY + metaAlto)

        paint.color = fondoTarjeta
        canvas.drawRoundRect(rectMeta, 6f, 6f, paint)

        paint.color = lineaDivisoria
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(rectMeta, 6f, 6f, paint)
        paint.style = Paint.Style.FILL

        // Columna 1
        paint.textSize = 8.5f
        paint.color = textoMuted
        canvas.drawText("EVALUADOR RESPONSABLE", margenIzquierdo + 14f, metaY + 20f, paint)
        paint.textSize = 11f
        paint.color = textoOscuro
        paint.isFakeBoldText = true
        canvas.drawText(evaluador.ifEmpty { "Evaluador de Campo" }, margenIzquierdo + 14f, metaY + 36f, paint)

        paint.textSize = 8.5f
        paint.color = textoMuted
        paint.isFakeBoldText = false
        canvas.drawText("VARIEDAD BOTÁNICA", margenIzquierdo + 14f, metaY + 52f, paint)
        paint.textSize = 11f
        paint.color = textoOscuro
        paint.isFakeBoldText = true
        canvas.drawText(evaluacion.variedad, margenIzquierdo + 130f, metaY + 52f, paint)

        // Columna 2
        paint.textSize = 8.5f
        paint.color = textoMuted
        paint.isFakeBoldText = false
        canvas.drawText("PARCELA / LOTE", margenIzquierdo + 260f, metaY + 20f, paint)
        paint.textSize = 11f
        paint.color = textoOscuro
        paint.isFakeBoldText = true
        canvas.drawText(evaluacion.loteNombre, margenIzquierdo + 260f, metaY + 36f, paint)

        paint.textSize = 8.5f
        paint.color = textoMuted
        paint.isFakeBoldText = false
        canvas.drawText("FECHA / HORA REGISTRADA", margenIzquierdo + 260f, metaY + 52f, paint)
        paint.textSize = 10.5f
        paint.color = textoSecundario
        paint.isFakeBoldText = true
        canvas.drawText(evaluacion.fechaHora, margenIzquierdo + 395f, metaY + 52f, paint)

        // ================= 3. ANÁLISIS DE MUESTRA Y DIAGNÓSTICO =================
        val seccionY = 170f
        val fotoTamano = 138f

        // Foto de la muestra con marco fino sobrio
        if (evaluacion.bitmap != null) {
            try {
                val scaled = Bitmap.createScaledBitmap(evaluacion.bitmap, fotoTamano.toInt(), fotoTamano.toInt(), true)
                canvas.drawBitmap(scaled, margenIzquierdo, seccionY, null)

                paint.color = bordeSuave
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f
                canvas.drawRect(margenIzquierdo, seccionY, margenIzquierdo + fotoTamano, seccionY + fotoTamano, paint)
                paint.style = Paint.Style.FILL
            } catch (_: Exception) { }
        } else {
            val rectPlaceholder = RectF(margenIzquierdo, seccionY, margenIzquierdo + fotoTamano, seccionY + fotoTamano)
            paint.color = fondoTarjeta
            canvas.drawRect(rectPlaceholder, paint)
            paint.color = bordeSuave
            paint.style = Paint.Style.STROKE
            canvas.drawRect(rectPlaceholder, paint)
            paint.style = Paint.Style.FILL
            paint.color = textoMuted
            paint.textSize = 9.5f
            canvas.drawText("Sin muestra visual", margenIzquierdo + 24f, seccionY + 70f, paint)
        }

        // Bloque descriptivo al lado de la foto
        val detalleX = margenIzquierdo + fotoTamano + 18f
        val anchoDetalle = margenDerecho - detalleX

        // Badge de Estado Sobrio (borde nítido, sin estridencias)
        val pillRect = RectF(detalleX, seccionY, detalleX + 270f, seccionY + 24f)
        paint.color = colorFondoEstado
        canvas.drawRoundRect(pillRect, 4f, 4f, paint)
        paint.color = colorEstado
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(pillRect, 4f, 4f, paint)
        paint.style = Paint.Style.FILL

        // Indicador circular de semáforo
        paint.color = colorEstado
        canvas.drawCircle(detalleX + 11f, seccionY + 12f, 3.5f, paint)

        paint.textSize = 9f
        paint.isFakeBoldText = true
        canvas.drawText(textoEstado, detalleX + 22f, seccionY + 15.5f, paint)

        // Nivel de Madurez
        var posY = seccionY + 44f
        paint.color = textoMuted
        paint.textSize = 8.5f
        paint.isFakeBoldText = true
        canvas.drawText("MADUREZ DE COSECHA", detalleX, posY, paint)

        paint.color = textoOscuro
        paint.textSize = 12f
        paint.isFakeBoldText = true
        canvas.drawText("${evaluacion.resultado.madurezPorcentaje}%", detalleX + 115f, posY, paint)

        paint.color = textoSecundario
        paint.textSize = 9.5f
        paint.isFakeBoldText = false
        canvas.drawText("(${evaluacion.resultado.colorPredominante})", detalleX + 150f, posY, paint)

        // Barra sobria de progreso
        posY += 7f
        paint.color = fondoTarjeta
        val rectBarraFondo = RectF(detalleX, posY, detalleX + anchoDetalle, posY + 4f)
        canvas.drawRect(rectBarraFondo, paint)
        paint.color = colorEstado
        val anchoProgreso = (anchoDetalle * (evaluacion.resultado.madurezPorcentaje / 100f)).coerceIn(5f, anchoDetalle)
        val rectBarraProgreso = RectF(detalleX, posY, detalleX + anchoProgreso, posY + 4f)
        canvas.drawRect(rectBarraProgreso, paint)

        // Patógeno / Defectos
        posY += 22f
        paint.color = textoMuted
        paint.textSize = 8.5f
        paint.isFakeBoldText = true
        canvas.drawText("PATÓGENO / SÍNTOMAS", detalleX, posY, paint)

        posY += 13f
        paint.color = if (evaluacion.resultado.semaforo.lowercase() == "rojo") colorEstado else textoOscuro
        paint.textSize = 10f
        paint.isFakeBoldText = true
        posY = drawWrappedText(canvas, paint, evaluacion.resultado.defectosDetectados, detalleX, posY, anchoDetalle, 13f)

        // Dictamen Técnico
        posY += 6f
        paint.color = textoMuted
        paint.textSize = 8.5f
        paint.isFakeBoldText = true
        canvas.drawText("DICTAMEN TÉCNICO", detalleX, posY, paint)

        posY += 13f
        paint.color = textoSecundario
        paint.textSize = 9.5f
        paint.isFakeBoldText = false
        drawWrappedText(canvas, paint, evaluacion.resultado.dictamen, detalleX, posY, anchoDetalle, 13f)

        // ================= 4. TRATAMIENTOS Y FUNGICIDAS SUGERIDOS =================
        var seccionTratamientosY = 346f

        paint.color = textoOscuro
        paint.textSize = 11.5f
        paint.isFakeBoldText = true
        canvas.drawText("Tratamientos y Fungicidas Sugeridos", margenIzquierdo, seccionTratamientosY, paint)

        paint.color = lineaDivisoria
        paint.strokeWidth = 1f
        canvas.drawLine(margenIzquierdo, seccionTratamientosY + 6f, margenDerecho, seccionTratamientosY + 6f, paint)

        seccionTratamientosY += 24f
        paint.textSize = 9.5f
        paint.isFakeBoldText = false

        evaluacion.resultado.fungicidasSugeridos.forEach { fungicida ->
            paint.color = textoSecundario
            canvas.drawCircle(margenIzquierdo + 5f, seccionTratamientosY - 3f, 2f, paint)

            paint.color = textoOscuro
            canvas.drawText(fungicida, margenIzquierdo + 16f, seccionTratamientosY, paint)
            seccionTratamientosY += 20f
        }

        // ================= 5. MEDIDAS DE MANEJO PREVENTIVO =================
        var seccionPreventivaY = seccionTratamientosY + 12f

        paint.color = textoOscuro
        paint.textSize = 11.5f
        paint.isFakeBoldText = true
        canvas.drawText("Medidas de Manejo Preventivo en Campo", margenIzquierdo, seccionPreventivaY, paint)

        paint.color = lineaDivisoria
        paint.strokeWidth = 1f
        canvas.drawLine(margenIzquierdo, seccionPreventivaY + 6f, margenDerecho, seccionPreventivaY + 6f, paint)

        seccionPreventivaY += 24f
        paint.textSize = 9.5f
        paint.isFakeBoldText = false

        evaluacion.resultado.medidasPreventivas.forEach { medida ->
            paint.color = textoSecundario
            canvas.drawCircle(margenIzquierdo + 5f, seccionPreventivaY - 3f, 2f, paint)

            paint.color = textoSecundario
            canvas.drawText(medida, margenIzquierdo + 16f, seccionPreventivaY, paint)
            seccionPreventivaY += 20f
        }

        // ================= 6. FIRMA DEL EVALUADOR =================
        val firmaY = 720f
        val anchoLineaFirma = 180f
        val xFirma = margenDerecho - anchoLineaFirma

        paint.color = bordeSuave
        paint.strokeWidth = 1f
        canvas.drawLine(xFirma, firmaY, margenDerecho, firmaY, paint)

        paint.color = textoOscuro
        paint.textSize = 9.5f
        paint.isFakeBoldText = true
        canvas.drawText(evaluador.ifEmpty { "Evaluador de Campo" }, xFirma + 10f, firmaY + 15f, paint)

        paint.color = textoMuted
        paint.textSize = 8.5f
        paint.isFakeBoldText = false
        canvas.drawText("Firma del evaluador responsable", xFirma + 10f, firmaY + 27f, paint)

        // ================= 7. PIE DE PÁGINA =================
        paint.color = lineaDivisoria
        canvas.drawLine(margenIzquierdo, 792f, margenDerecho, 792f, paint)

        paint.color = textoMuted
        paint.textSize = 8.5f
        paint.isFakeBoldText = false
        canvas.drawText("AgroScan • Tecsup 2026 • Evaluación Fitosanitaria", margenIzquierdo, 808f, paint)

        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Página 1 de 1", margenDerecho, 808f, paint)
        paint.textAlign = Paint.Align.LEFT

        document.finishPage(page)

        // Guardado del archivo
        val nombreArchivo = "AgroScan_Ficha_${System.currentTimeMillis()}.pdf"
        var archivoSalida: File? = null

        try {
            val docsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            val file = File(docsDir, nombreArchivo)
            val outputStream = FileOutputStream(file)
            document.writeTo(outputStream)
            outputStream.flush()
            outputStream.close()
            archivoSalida = file

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, nombreArchivo)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        document.writeTo(os)
                    }
                }
            }

            document.close()

            // Abrir automáticamente el visor de PDF
            abrirPdfOCompartir(context, file)

        } catch (e: Exception) {
            document.close()
            Toast.makeText(context, "Error al generar PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }

        return archivoSalida
    }

    private fun drawWrappedText(
        canvas: android.graphics.Canvas,
        paint: Paint,
        text: String,
        x: Float,
        startY: Float,
        maxWidth: Float,
        lineHeight: Float
    ): Float {
        var currentY = startY
        val words = text.split(" ")
        var currentLine = ""

        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            val width = paint.measureText(testLine)
            if (width <= maxWidth) {
                currentLine = testLine
            } else {
                if (currentLine.isNotEmpty()) {
                    canvas.drawText(currentLine, x, currentY, paint)
                    currentY += lineHeight
                }
                currentLine = word
            }
        }
        if (currentLine.isNotEmpty()) {
            canvas.drawText(currentLine, x, currentY, paint)
            currentY += lineHeight
        }
        return currentY
    }

    private fun abrirPdfOCompartir(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // Intentar abrir directamente
            try {
                context.startActivity(intent)
            } catch (_: Exception) {
                val chooser = Intent.createChooser(intent, "Abrir Ficha Técnica AgroScan").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Guardado en Descargas. Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}
