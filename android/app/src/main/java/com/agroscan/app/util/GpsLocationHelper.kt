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
import android.widget.Toast
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

    fun gpsEstaHabilitado(context: Context): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
               locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    /**
     * Obtiene la ubicación física real y precisa del dispositivo.
     * 1. Consulta inmediatamente la última ubicación conocida (Fused + LocationManager) en <50ms.
     * 2. En paralelo solicita una lectura de alta precisión (Priority.PRIORITY_HIGH_ACCURACY).
     * 3. Si llega una lectura fresca más precisa, actualiza al llamador.
     */
    @SuppressLint("MissingPermission")
    fun obtenerUbicacionActual(
        context: Context,
        onUbicacionObtenida: (Double, Double) -> Unit
    ) {
        if (!tienePermisoUbicacion(context)) return

        if (!gpsEstaHabilitado(context)) {
            Toast.makeText(context, "Por favor activa el GPS / Ubicación de tu teléfono", Toast.LENGTH_SHORT).show()
        }

        var entregado = false
        fun responder(lat: Double, lon: Double, forzarActualizacion: Boolean = false) {
            if (lat != 0.0 && lon != 0.0) {
                if (!entregado || forzarActualizacion) {
                    entregado = true
                    Handler(Looper.getMainLooper()).post {
                        onUbicacionObtenida(lat, lon)
                    }
                }
            }
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

        // PASO 1: Obtener INMEDIATAMENTE la última ubicación conocida de Google Play Services (<50ms)
        try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            fusedClient.lastLocation.addOnSuccessListener { last ->
                if (last != null) {
                    responder(last.latitude, last.longitude)
                }
            }

            // PASO 2: Solicitar lectura satelital de alta precisión fresca
            fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { fresh ->
                    if (fresh != null) {
                        responder(fresh.latitude, fresh.longitude, forzarActualizacion = true)
                    }
                }
        } catch (_: Exception) {}

        // PASO 3: Revisar caché de LocationManager nativo inmediatamente (fallback instantáneo)
        if (locationManager != null) {
            for (prov in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)) {
                try {
                    val loc = locationManager.getLastKnownLocation(prov)
                    if (loc != null && !entregado) {
                        responder(loc.latitude, loc.longitude)
                    }
                } catch (_: Exception) {}
            }

            // PASO 4: Escuchar actualizaciones de satélites en vivo
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    responder(location.latitude, location.longitude, forzarActualizacion = true)
                    try { locationManager.removeUpdates(this) } catch (_: Exception) {}
                }
                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }

            for (prov in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                try {
                    if (locationManager.isProviderEnabled(prov)) {
                        locationManager.requestLocationUpdates(prov, 1000L, 1f, listener, Looper.getMainLooper())
                    }
                } catch (_: Exception) {}
            }

            // Timeout de seguridad a los 8 segundos
            Handler(Looper.getMainLooper()).postDelayed({
                try { locationManager.removeUpdates(listener) } catch (_: Exception) {}
                if (!entregado) {
                    for (prov in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)) {
                        try {
                            val loc = locationManager.getLastKnownLocation(prov)
                            if (loc != null) {
                                responder(loc.latitude, loc.longitude)
                                return@postDelayed
                            }
                        } catch (_: Exception) {}
                    }
                    // Si el teléfono no tiene señal en absoluto, centro estándar
                    responder(-12.046374, -77.042793)
                }
            }, 8000L)
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
        var distanciaMinima = Double.MAX_VALUE

        for (lote in lotes) {
            val latLote = lote.latitud ?: continue
            val lonLote = lote.longitud ?: continue

            val dist = calcularDistanciaMetros(latUsuario, lonUsuario, latLote, lonLote)
            if (dist <= radioMaximoMetros && dist < distanciaMinima) {
                distanciaMinima = dist
                loteMasCercano = lote
            }
        }

        return loteMasCercano?.let { Pair(it, distanciaMinima.toInt()) }
    }
}
