package com.agroscan.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.agroscan.app.ui.theme.*
import com.agroscan.app.util.GpsLocationHelper
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import kotlin.math.roundToInt

/**
 * Crea el ícono de "Tú (GPS)" con halo azul y centro eléctrico (estilo Google Maps/GPS nativo).
 */
private fun crearIconoGpsUsuario(context: Context): Drawable {
    val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val paintHalo = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.parseColor("#332563EB")
        style = Paint.Style.FILL
    }
    val paintBorde = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    val paintCentro = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.parseColor("#2563EB")
        style = Paint.Style.FILL
    }

    canvas.drawCircle(32f, 32f, 30f, paintHalo)
    canvas.drawCircle(32f, 32f, 16f, paintBorde)
    canvas.drawCircle(32f, 32f, 11f, paintCentro)

    return BitmapDrawable(context.resources, bitmap)
}

/**
 * Mapa Selector Pro con 2 Puntos Reales:
 * 1. MARCADOR AZUL EN EL MAPA: Tu posición física GPS en tiempo real.
 * 2. PUNTERO ROJO CENTRAL CON HALO DE 10m: El punto que ajustas al arrastrar el mapa.
 * 3. Indicador de distancia en vivo entre tú y la parcela seleccionada.
 */
@Composable
fun MapaSelectorParcela(
    latInicial: Double,
    lonInicial: Double,
    radioMetros: Int = 10,
    altura: Dp = 350.dp,
    onCoordenadasCambiadas: (Double, Double) -> Unit
) {
    val context = LocalContext.current
    var latActual by remember { mutableDoubleStateOf(latInicial) }
    var lonActual by remember { mutableDoubleStateOf(lonInicial) }
    var latGpsUsuario by remember { mutableStateOf<Double?>(null) }
    var lonGpsUsuario by remember { mutableStateOf<Double?>(null) }

    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }
    var myLocationMarker by remember { mutableStateOf<Marker?>(null) }

    // Calcular distancia en tiempo real entre el usuario y el punto fijado
    val distanciaMetros = remember(latActual, lonActual, latGpsUsuario, lonGpsUsuario) {
        if (latGpsUsuario != null && lonGpsUsuario != null) {
            GpsLocationHelper.calcularDistanciaMetros(latGpsUsuario!!, lonGpsUsuario!!, latActual, lonActual).roundToInt()
        } else {
            0
        }
    }

    LaunchedEffect(Unit) {
        Configuration.getInstance().load(context, context.getSharedPreferences("osm_prefs", Context.MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = "AgroScan-Tecsup/2.0 (investigacion@tecsup.edu.pe)"

        // Leer GPS en tiempo real y colocar el MARCADOR AZUL visible
        GpsLocationHelper.obtenerUbicacionActual(context) { gpsLat, gpsLon ->
            latGpsUsuario = gpsLat
            lonGpsUsuario = gpsLon

            mapViewInstance?.let { map ->
                if (myLocationMarker == null) {
                    val marker = Marker(map).apply {
                        position = GeoPoint(gpsLat, gpsLon)
                        title = "Tú estás aquí"
                        icon = crearIconoGpsUsuario(context)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    }
                    map.overlays.add(marker)
                    myLocationMarker = marker
                } else {
                    myLocationMarker?.position = GeoPoint(gpsLat, gpsLon)
                }
                map.invalidate()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(altura)
            .clip(RoundedCornerShape(22.dp))
            .border(1.5.dp, AgroGreenPrimary.copy(alpha = 0.5f), RoundedCornerShape(22.dp))
    ) {
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(18.5)
                    val puntoInicial = GeoPoint(latInicial, lonInicial)
                    controller.setCenter(puntoInicial)

                    // Colocar el marcador de usuario apenas esté disponible
                    GpsLocationHelper.obtenerUbicacionActual(ctx) { gLat, gLon ->
                        latGpsUsuario = gLat
                        lonGpsUsuario = gLon
                        val marker = Marker(this).apply {
                            position = GeoPoint(gLat, gLon)
                            title = "Tú estás aquí"
                            icon = crearIconoGpsUsuario(ctx)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        }
                        overlays.add(marker)
                        myLocationMarker = marker
                        invalidate()
                    }

                    setOnTouchListener { v, event ->
                        when (event.action) {
                            MotionEvent.ACTION_UP -> {
                                val centro = mapCenter as? GeoPoint
                                if (centro != null) {
                                    latActual = centro.latitude
                                    lonActual = centro.longitude
                                    onCoordenadasCambiadas(centro.latitude, centro.longitude)
                                }
                            }
                        }
                        v.onTouchEvent(event)
                    }
                    mapViewInstance = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // PUNTERO ROJO CENTRAL DE AJUSTE (CON CÍRCULO VERDE DE 10 METROS)
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(AgroGreenPrimary.copy(alpha = 0.22f))
                    .border(2.dp, AgroGreenPrimary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(SemaforoRojo)
                        .border(2.5.dp, Color.White, CircleShape)
                )
            }
        }

        // LEYENDA CLARA CON LOS 2 PUNTOS Y DISTANCIA EN METROS
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = AgroCardWhite.copy(alpha = 0.95f),
            shadowElevation = 4.dp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(Color(0xFF2563EB)))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Tú (GPS)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextDark)
                }
                Text("•", color = TextGray)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(SemaforoRojo))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Parcela (${radioMetros}m)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextDark)
                }
                if (latGpsUsuario != null) {
                    Text("•", color = TextGray)
                    Text(
                        text = if (distanciaMetros == 0) "En el punto" else "A ${distanciaMetros}m",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (distanciaMetros <= radioMetros) SemaforoVerde else AgroGreenPrimary
                    )
                }
            }
        }

        // BOTONES FLOTANTES: MI UBICACIÓN Y ZOOM
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FloatingActionButton(
                onClick = {
                    GpsLocationHelper.obtenerUbicacionActual(context) { gpsLat, gpsLon ->
                        latGpsUsuario = gpsLat
                        lonGpsUsuario = gpsLon
                        myLocationMarker?.position = GeoPoint(gpsLat, gpsLon)
                        mapViewInstance?.controller?.animateTo(GeoPoint(gpsLat, gpsLon))
                        latActual = gpsLat
                        lonActual = gpsLon
                        onCoordenadasCambiadas(gpsLat, gpsLon)
                    }
                },
                shape = CircleShape,
                containerColor = AgroGreenPrimary,
                contentColor = Color.White,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Mi Ubicación", modifier = Modifier.size(20.dp))
            }

            FloatingActionButton(
                onClick = { mapViewInstance?.controller?.zoomIn() },
                shape = CircleShape,
                containerColor = AgroCardWhite,
                contentColor = AgroGreenPrimary,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Zoom In", modifier = Modifier.size(16.dp))
            }

            FloatingActionButton(
                onClick = { mapViewInstance?.controller?.zoomOut() },
                shape = CircleShape,
                containerColor = AgroCardWhite,
                contentColor = AgroGreenPrimary,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Zoom Out", modifier = Modifier.size(16.dp))
            }
        }
    }
}
