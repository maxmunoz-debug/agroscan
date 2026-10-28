package com.agroscan.app.data

import android.graphics.Bitmap
import com.google.gson.annotations.SerializedName
import java.util.UUID

// Modelo que coincide 1:1 con la respuesta real de nuestro Backend Python
data class AgroScanResult(
    @SerializedName("fruta")
    val fruta: String = "Fresa",

    @SerializedName("madurez_porcentaje")
    val madurezPorcentaje: Int = 0,

    @SerializedName("color_predominante")
    val colorPredominante: String = "",

    @SerializedName("defectos_detectados")
    val defectosDetectados: String = "Ninguno",

    @SerializedName("semaforo")
    val semaforo: String = "verde", // "verde", "amarillo", "rojo"

    @SerializedName("dictamen")
    val dictamen: String = "",

    @SerializedName("recomendacion")
    val recomendacion: String = "",

    // Tratamientos agrícolas (lo que pidió la profesora)
    val fungicidasSugeridos: List<String> = listOf(
        "Captan 80 WDG (2.0 - 2.5 kg/ha)",
        "Ciprodinil + Fludioxonil (Switch 62.5 WG - 1.0 kg/ha)",
        "Biológico: Bacillus subtilis (Serenade ASO - 3.0 L/ha)"
    ),

    val medidasPreventivas: List<String> = listOf(
        "Mejorar ventilación en túneles/macrotúneles",
        "Retirar y destruir frutos senescentes y afectados",
        "Evitar riego por aspersión durante floración y fructificación"
    )
)

// Modelo compartido de Lote para toda la app
data class LoteItem(
    val id: Int,
    var nombre: String,
    var variedad: String,
    var ubicacion: String,
    var estado: String = "Óptimo",
    var ultimaEvaluacion: String = "Hoy"
)

// Modelo para el Historial de Evaluaciones y Reportes PDF
data class EvaluacionHistorial(
    val id: String = UUID.randomUUID().toString(),
    val fechaHora: String,
    val loteNombre: String,
    val variedad: String,
    val notas: String = "",
    val resultado: AgroScanResult,
    val bitmap: Bitmap? = null
)

// Modelo para los mensajes del Chatbot Asistente Agrónomo
data class MensajeChat(
    val id: String = UUID.randomUUID().toString(),
    val texto: String,
    val esUsuario: Boolean,
    val hora: String
)
