package com.agroscan.app.util

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

object GeocodingHelper {

    /**
     * Convierte latitud y longitud en un nombre de lugar legible por humanos
     * Ejemplo: "Urb. Villa Florencia, Cañete" o "Fundo San José, Lima"
     * 100% nativo de Android y gratuito.
     */
    suspend fun obtenerDireccionLegible(context: Context, lat: Double, lon: Double): String {
        return withContext(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    var resultado = ""
                    geocoder.getFromLocation(lat, lon, 1) { addresses ->
                        if (addresses.isNotEmpty()) {
                            resultado = formatearDireccion(addresses[0])
                        }
                    }
                    // Esperar un momento si es async en Android 13+
                    var intentos = 0
                    while (resultado.isEmpty() && intentos < 10) {
                        kotlinx.coroutines.delay(100)
                        intentos++
                    }
                    if (resultado.isNotEmpty()) resultado else "Parcela georreferenciada (GPS)"
                } else {
                    @Suppress("DEPRECATION")
                    val addresses: List<Address>? = geocoder.getFromLocation(lat, lon, 1)
                    if (!addresses.isNullOrEmpty()) {
                        formatearDireccion(addresses[0])
                    } else {
                        "Parcela georreferenciada (GPS)"
                    }
                }
            } catch (e: Exception) {
                "Parcela georreferenciada (GPS)"
            }
        }
    }

    private fun formatearDireccion(address: Address): String {
        val partes = mutableListOf<String>()

        // Barrio, calle o urbanización
        address.subLocality?.let { partes.add(it) } ?: address.thoroughfare?.let { partes.add(it) }

        // Distrito o ciudad
        address.locality?.let { partes.add(it) } ?: address.subAdminArea?.let { partes.add(it) }

        return if (partes.isNotEmpty()) {
            partes.joinToString(", ")
        } else {
            address.getAddressLine(0)?.split(",")?.take(2)?.joinToString(",") ?: "Parcela georreferenciada (GPS)"
        }
    }
}
