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

    // Tratamientos agrícolas recomendados
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

// Modelo compartido de Lote con Foto personalizada y Georreferenciación GPS
data class LoteItem(
    val id: Int,
    var nombre: String,
    var variedad: String,
    var ubicacion: String,
    var estado: String = "Óptimo",
    var ultimaEvaluacion: String = "Hoy",
    var latitud: Double? = null,
    var longitud: Double? = null,
    var fotoUri: String? = null // Foto real de la parcela/lote
)

// Modelo para el Historial de Evaluaciones y Reportes PDF
data class EvaluacionHistorial(
    val id: String = UUID.randomUUID().toString(),
    val fechaHora: String,
    val loteNombre: String,
    val variedad: String,
    val notas: String = "",
    val resultado: AgroScanResult,
    @Transient var bitmap: Bitmap? = null,
    val fotoPath: String? = null,
    val latitud: Double? = null,
    val longitud: Double? = null,
    // Trazabilidad de auditoría (Papelera / Soft Delete): el registro nunca se borra físicamente
    val fechaEliminacion: String? = null,
    val eliminadoPor: String? = null,
    val motivoEliminacion: String? = null
)

/**
 * Indica si la IA determinó que la imagen NO corresponde a una fresa
 * (ej. un dibujo, una persona u otro objeto). En ese caso la muestra no es válida
 * y no debe tratarse como un descarte fitosanitario.
 */
fun AgroScanResult.esMuestraNoValida(): Boolean {
    val f = fruta.lowercase()
    val textoIA = "$dictamen $defectosDetectados".lowercase()
    val negaciones = listOf(
        "no corresponde", "no es ", "no es una", "no se detecta", "no contiene",
        "no identificad", "no válid", "no valid", "desconocid", "sin fresa"
    )
    val frutaNoEsFresa = !(f.contains("fresa") || f.contains("frutilla") || f.contains("strawberr"))
    val frutaNegada = negaciones.any { f.contains(it) }
    val iaIndicaObjetoErroneo = textoIA.contains("error de objeto") ||
            textoIA.contains("no contiene una fresa") ||
            textoIA.contains("no es una fresa") ||
            textoIA.contains("no corresponde a fresa") ||
            textoIA.contains("no corresponde a una fresa")
    return frutaNoEsFresa || frutaNegada || iaIndicaObjetoErroneo
}

// Modelo para los mensajes del Chatbot Asistente Agrónomo
data class MensajeChat(
    val id: String = UUID.randomUUID().toString(),
    val texto: String,
    val esUsuario: Boolean,
    val hora: String
)

// Modelos de Autenticación con Backend Real SQLite
data class RegisterRequest(
    @SerializedName("nombre")
    val nombre: String,
    @SerializedName("email")
    val email: String,
    @SerializedName("password")
    val password: String
)

data class LoginRequest(
    @SerializedName("email")
    val email: String,
    @SerializedName("password")
    val password: String
)

data class AuthResponse(
    @SerializedName("id")
    val id: Int,
    @SerializedName("nombre")
    val nombre: String,
    @SerializedName("email")
    val email: String,
    @SerializedName("rol")
    val rol: String,
    @SerializedName("mensaje")
    val mensaje: String
)
