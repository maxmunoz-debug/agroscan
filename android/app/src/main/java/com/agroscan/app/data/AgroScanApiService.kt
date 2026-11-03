package com.agroscan.app.data

import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Body
import java.util.concurrent.TimeUnit

data class ChatRequest(val pregunta: String)
data class ChatResponse(val respuesta: String)

interface AgroScanApiService {
    // 1. Ruta para verificar si el servidor está vivo
    @GET("/")
    suspend fun checkHealth(): Map<String, Any>

    // 2. Ruta para enviar la foto y recibir el análisis de la IA
    @Multipart
    @POST("analizar")
    suspend fun analizarMuestra(
        @Part file: MultipartBody.Part
    ): AgroScanResult

    // 3. Ruta para el Asistente Agrónomo IA (Tecsup)
    @POST("chat")
    suspend fun enviarMensajeChat(
        @Body body: ChatRequest
    ): ChatResponse

    // 4. Autenticación Real en SQLite
    @POST("auth/login")
    suspend fun login(
        @Body body: LoginRequest
    ): AuthResponse

    @POST("auth/register")
    suspend fun register(
        @Body body: RegisterRequest
    ): AuthResponse

    // 5. Gestión de Lotes y Evaluaciones en la Nube
    @POST("lotes")
    suspend fun crearLote(
        @Body body: LoteCreateRequest
    ): Map<String, Any>

    @POST("evaluaciones")
    suspend fun guardarEvaluacion(
        @Body body: EvaluacionCreateRequest
    ): Map<String, Any>
}

data class LoteCreateRequest(
    val nombre: String,
    val variedad: String,
    val ubicacion: String,
    val usuario_email: String = ""
)

data class EvaluacionCreateRequest(
    val usuario_email: String = "",
    val lote_id: Int? = null,
    val lote_nombre: String,
    val variedad: String,
    val fruta: String = "Fresa",
    val madurez_porcentaje: Int,
    val color_predominante: String = "",
    val defectos_detectados: String = "",
    val semaforo: String,
    val dictamen: String,
    val recomendacion: String = "",
    val fungicidas_sugeridos: List<String> = emptyList(),
    val medidas_preventivas: List<String> = emptyList(),
    val notas: String = ""
)

object NetworkClient {
    // URL por defecto (puede cambiarse en vivo desde Ajustes)
    var currentBaseUrl = "https://agroscan-qqr0.onrender.com/"
        private set

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    var api: AgroScanApiService = crearRetrofit(currentBaseUrl)
        private set

    // Función para cambiar la IP desde los Ajustes sin recompilar
    fun setBaseUrl(nuevaUrl: String) {
        val urlValida = if (nuevaUrl.endsWith("/")) nuevaUrl else "$nuevaUrl/"
        currentBaseUrl = urlValida
        api = crearRetrofit(urlValida)
    }

    private fun crearRetrofit(url: String): AgroScanApiService {
        return Retrofit.Builder()
            .baseUrl(url)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AgroScanApiService::class.java)
    }

    // Función real para probar si el servidor responde
    suspend fun probarConexion(): Boolean {
        return try {
            api.checkHealth()
            true
        } catch (e: Exception) {
            false
        }
    }
}

