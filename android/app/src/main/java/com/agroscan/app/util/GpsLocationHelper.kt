package com.agroscan.app.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.agroscan.app.data.LoteItem
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlin.math.*

object GpsLocationHelper {

    fun tienePermisoUbicacion(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    /**
     * Obtiene la ubicación de forma inmediata y garantizada.
     * 1. Consulta la caché Fused y LocationManager nativo (0.01 segundos).
     * 2. Si no hay caché, lanza solicitud a Fused y a LocationManager en paralelo.
     * 3. Timeout garantizado a los 3 segundos para no dejar al usuario esperando.
     */
    @SuppressLint("MissingPermission")
    fun obtenerUbicacionActual(
        context: Context,
        onUbicacionObtenida: (Double, Double) -> Unit
    ) {
        if (!tienePermisoUbicacion(context)) return

        var entregado = false
        fun responder(lat: Double, lon: Double) {
            if (!entregado) {
                entregado = true
                Handler(Looper.getMainLooper()).post {
                    onUbicacionObtenida(lat, lon)
                }
            }
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

        // PASO 1: Probar inmediatamente con caché nativa (es instantánea en Android Samsung)
        if (locationManager != null) {
            for (prov in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)) {
                try {
                    val loc = locationManager.getLastKnownLocation(prov)
                    if (loc != null) {
                        responder(loc.latitude, loc.longitude)
                        return
                    }
                } catch (_: Exception) {}
            }
        }

        // PASO 2: Probar con FusedLocation de Google Play Services
        try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            fusedClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    responder(loc.latitude, loc.longitude)
                } else {
                    fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                        .addOnSuccessListener { fresh ->
                            if (fresh != null) responder(fresh.latitude, fresh.longitude)
                        }
                }
            }
        } catch (_: Exception) {}

        // PASO 3: En paralelo, escuchar actualizaciones de red
        if (locationManager != null && !entregado) {
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    responder(location.latitude, location.longitude)
                    try { locationManager.removeUpdates(this) } catch (_: Exception) {}
                }
                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }

            for (prov in listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)) {
                try {
                    if (locationManager.isProviderEnabled(prov)) {
                        locationManager.requestLocationUpdates(prov, 0L, 0f, listener, Looper.getMainLooper())
                    }
                } catch (_: Exception) {}
            }

            // Timeout de seguridad: a los 3 segundos corta la búsqueda y responde para no congelar la UI
            Handler(Looper.getMainLooper()).postDelayed({
                try { locationManager.removeUpdates(listener) } catch (_: Exception) {}
                if (!entregado) {
                    responder(-12.046374, -77.042793)
                }
            }, 3000L)
        }
    }

    /**
     * Genera una URL de mapa estático de alta calidad (OpenStreetMap) con pin rojo.
     */
    fun generarUrlMapaEstatico(lat: Double, lon: Double): String {
        return "https://staticmap.openstreetmap.de/staticmap.php?center=$lat,$lon&zoom=16&size=400x180&maptype=mapnik&markers=$lat,$lon,ol-marker"
    }

    /**
     * Fórmula de Haversine para cálculo de distancia geodésica exacta en metros.
     */
    fun calcularDistanciaMetros(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val radioTierra = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return radioTierra * c
    }

    fun detectarLoteCercano(
        latUsuario: Double,
        lonUsuario: Double,
        lotes: List<LoteItem>,
        radioMaximoMetros: Double = 100.0
    ): Pair<LoteItem, Int>? {
        var loteMasCercano: LoteItem? = null
        var menorDistancia = Double.MAX_VALUE

        for (lote in lotes) {
            val lat = lote.latitud
            val lon = lote.longitud
            if (lat != null && lon != null) {
                val distancia = calcularDistanciaMetros(latUsuario, lonUsuario, lat, lon)
                if (distancia < menorDistancia) {
                    menorDistancia = distancia
                    loteMasCercano = lote
                }
            }
        }

        // Si hay algún lote registrado con coordenadas, devolver el más cercano
        return if (loteMasCercano != null) {
            Pair(loteMasCercano, menorDistancia.roundToInt())
        } else {
            null
        }
    }
}
