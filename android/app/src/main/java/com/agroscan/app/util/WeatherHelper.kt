package com.agroscan.app.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

object WeatherHelper {
    /**
     * Consulta el clima en tiempo real desde Open-Meteo (Cero simulación).
     * Retorna un Pair con (Temperatura en °C, Humedad relativa en %)
     */
    suspend fun obtenerClima(lat: Double, lon: Double): Pair<Double, Int>? = withContext(Dispatchers.IO) {
        try {
            val urlString = String.format(
                Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.6f&longitude=%.6f&current=temperature_2m,relative_humidity_2m",
                lat,
                lon
            )
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "AgroScan-Tecsup/2.0")

            if (conn.responseCode == 200) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(jsonStr)
                if (root.has("current")) {
                    val current = root.getJSONObject("current")
                    val temp = current.getDouble("temperature_2m")
                    val hum = current.getInt("relative_humidity_2m")
                    Pair(temp, hum)
                } else {
                    null
                }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
