package com.agroscan.app.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import com.agroscan.app.data.EvaluacionHistorial
import java.io.File
import java.io.FileOutputStream

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

        // Colores temáticos agroScan
        val verdePrimario = 0xFF1B4332.toInt()
        val verdeFondo = 0xFFF3F9F4.toInt()
        val textoOscuro = 0xFF1E293B.toInt()
        val textoGris = 0xFF64748B.toInt()
        val bordeColor = 0xFFE2E8F0.toInt()

        val colorSemaforo = when (evaluacion.resultado.semaforo.lowercase()) {
            "rojo" -> 0xFFC1121F.toInt()
            "amarillo" -> 0xFFD4A373.toInt()
            else -> 0xFF2D6A4F.toInt()
        }
        val textoSemaforo = when (evaluacion.resultado.semaforo.lowercase()) {
            "rojo" -> "ROJO - DESCARTE INMEDIATO"
            "amarillo" -> "AMARILLO - ATENCIÓN / REVISIÓN"
            else -> "VERDE - ÓPTIMO / EXPORTACIÓN"
        }

        // 1. Cabecera Verde Superior
        paint.color = verdePrimario
        canvas.drawRect(0f, 0f, 595f, 75f, paint)

        paint.color = Color.WHITE
        paint.textSize = 20f
        paint.isFakeBoldText = true
        canvas.drawText("agroScan • Ficha Técnica de Inspección", 25f, 38f, paint)

        paint.textSize = 10f
        paint.isFakeBoldText = false
        canvas.drawText("Sistema de Evaluación Fitosanitaria con IA y Visión Computacional", 25f, 58f, paint)

        // 2. Tarjeta de Metadatos
        paint.color = verdeFondo
        val rectMeta = RectF(25f, 90f, 570f, 160f)
        canvas.drawRoundRect(rectMeta, 10f, 10f, paint)

        paint.color = bordeColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(rectMeta, 10f, 10f, paint)
        paint.style = Paint.Style.FILL

        paint.color = textoOscuro
        paint.textSize = 11f
        paint.isFakeBoldText = true
        canvas.drawText("Fecha y Hora:", 40f, 115f, paint)
        canvas.drawText("Evaluador:", 40f, 135f, paint)
        canvas.drawText("Lote / Parcela:", 300f, 115f, paint)
        canvas.drawText("Variedad:", 300f, 135f, paint)

        paint.isFakeBoldText = false
        paint.color = textoGris
        canvas.drawText(evaluacion.fechaHora, 120f, 115f, paint)
        canvas.drawText(evaluador.ifEmpty { "Evaluador de Campo" }, 120f, 135f, paint)
        canvas.drawText(evaluacion.loteNombre, 385f, 115f, paint)
        canvas.drawText(evaluacion.variedad, 385f, 135f, paint)

        if (evaluacion.notas.isNotBlank()) {
            paint.isFakeBoldText = true
            paint.color = textoOscuro
            canvas.drawText("Notas:", 40f, 152f, paint)
            paint.isFakeBoldText = false
            paint.color = textoGris
            canvas.drawText(evaluacion.notas, 120f, 152f, paint)
        }

        // 3. Foto de la Muestra y Diagnóstico Semáforo
        val fotoY = 175f
        if (evaluacion.bitmap != null) {
            try {
                val scaledBitmap = Bitmap.createScaledBitmap(evaluacion.bitmap, 140, 140, true)
                canvas.drawBitmap(scaledBitmap, 25f, fotoY, null)

                paint.color = bordeColor
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.5f
                canvas.drawRect(25f, fotoY, 165f, fotoY + 140f, paint)
                paint.style = Paint.Style.FILL
            } catch (_: Exception) { }
        }

        // Cuadro del Semáforo al lado de la foto
        val semaforoRect = RectF(180f, fotoY, 570f, fotoY + 36f)
        paint.color = colorSemaforo
        canvas.drawRoundRect(semaforoRect, 8f, 8f, paint)

        paint.color = Color.WHITE
        paint.textSize = 13f
        paint.isFakeBoldText = true
        canvas.drawText(textoSemaforo, 195f, fotoY + 23f, paint)

        // Detalles métricos
        paint.color = textoOscuro
        paint.textSize = 12f
        paint.isFakeBoldText = true
        val infoX = 185f
        var currentY = fotoY + 56f
        canvas.drawText("Madurez:", infoX, currentY, paint)
        paint.isFakeBoldText = false
        paint.color = textoGris
        canvas.drawText("${evaluacion.resultado.madurezPorcentaje}% (Color: ${evaluacion.resultado.colorPredominante})", infoX + 75f, currentY, paint)

        currentY += 20f
        paint.color = textoOscuro
        paint.isFakeBoldText = true
        canvas.drawText("Defectos / Patógeno:", infoX, currentY, paint)
        paint.isFakeBoldText = false
        paint.color = if (evaluacion.resultado.semaforo.lowercase() == "rojo") colorSemaforo else textoGris
        canvas.drawText(evaluacion.resultado.defectosDetectados, infoX + 130f, currentY, paint)

        currentY += 20f
        paint.color = textoOscuro
        paint.isFakeBoldText = true
        canvas.drawText("Dictamen:", infoX, currentY, paint)
        paint.isFakeBoldText = false
        paint.color = textoGris
        canvas.drawText(evaluacion.resultado.dictamen, infoX + 75f, currentY, paint)

        // 4. Sección de Fungicidas Recomendados
        var sectionY = 335f
        paint.color = verdePrimario
        paint.textSize = 14f
        paint.isFakeBoldText = true
        canvas.drawText("Tratamientos y Fungicidas Sugeridos", 25f, sectionY, paint)

        paint.color = bordeColor
        paint.strokeWidth = 1f
        canvas.drawLine(25f, sectionY + 5f, 570f, sectionY + 5f, paint)

        sectionY += 22f
        paint.textSize = 11f
        paint.isFakeBoldText = false
        evaluacion.resultado.fungicidasSugeridos.forEach { fungicida ->
            paint.color = colorSemaforo
            canvas.drawCircle(32f, sectionY - 4f, 3f, paint)
            paint.color = textoOscuro
            canvas.drawText(fungicida, 42f, sectionY, paint)
            sectionY += 18f
        }

        // 5. Medidas de Manejo Preventivo
        sectionY += 10f
        paint.color = verdePrimario
        paint.textSize = 14f
        paint.isFakeBoldText = true
        canvas.drawText("Medidas Preventivas en Campo (Buenas Prácticas)", 25f, sectionY, paint)

        paint.color = bordeColor
        canvas.drawLine(25f, sectionY + 5f, 570f, sectionY + 5f, paint)

        sectionY += 22f
        paint.textSize = 11f
        paint.isFakeBoldText = false
        evaluacion.resultado.medidasPreventivas.forEach { medida ->
            paint.color = verdePrimario
            canvas.drawCircle(32f, sectionY - 4f, 3f, paint)
            paint.color = textoOscuro
            canvas.drawText(medida, 42f, sectionY, paint)
            sectionY += 18f
        }

        // 6. Pie de Página Institucional
        paint.color = textoGris
        paint.textSize = 9f
        paint.isFakeBoldText = false
        canvas.drawLine(25f, 790f, 570f, 790f, paint)
        canvas.drawText("Tecsup • Proyecto Agroscaner • Validación con Gemini IA • Generado automáticamente", 25f, 805f, paint)

        document.finishPage(page)

        // Guardado del archivo
        val nombreArchivo = "agroScan_Ficha_${System.currentTimeMillis()}.pdf"
        var archivoSalida: File? = null

        try {
            // Guardar en almacenamiento privado compartido para FileProvider
            val docsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            val file = File(docsDir, nombreArchivo)
            val outputStream = FileOutputStream(file)
            document.writeTo(outputStream)
            outputStream.flush()
            outputStream.close()
            archivoSalida = file

            // Si es Android 10+ (API 29+), guardarlo también en la carpeta pública de Descargas (Downloads)
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

            // Abrir selector para visualizar o compartir
            abrirPdfOCompartir(context, file)
            Toast.makeText(context, "PDF guardado en Descargas 📄", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            document.close()
            Toast.makeText(context, "Error al generar PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }

        return archivoSalida
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

            val chooser = Intent.createChooser(intent, "Abrir Ficha Técnica AgroScan")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (_: Exception) {
            // Si el dispositivo no tiene visor de PDF predeterminado, ofrecemos compartirlo (WhatsApp, Drive, etc.)
            try {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    file
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Compartir Ficha Técnica"))
            } catch (_: Exception) { }
        }
    }
}
