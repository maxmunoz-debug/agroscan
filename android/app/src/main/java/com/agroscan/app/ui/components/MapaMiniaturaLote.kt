package com.agroscan.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.agroscan.app.ui.theme.AgroGreenPrimary
import com.agroscan.app.ui.theme.SemaforoRojo

/**
 * Miniatura interactiva de la parcela con Georreferenciación Exacta:
 * - Genera una vista centrada simétricamente en las coordenadas GPS reales (lat, lon).
 * - El pin rojo y el halo de precisión quedan exactamente en la posición física de la parcela.
 * - Utiliza ArcGIS World Street Map (abierto, nítido y sin marcas de agua).
 * - Al tocarlo, abre el mapa interactivo para ajustar o cambiar de posición.
 */
@Composable
fun MapaMiniaturaLote(
    lat: Double,
    lon: Double,
    modifier: Modifier = Modifier,
    altura: Dp = 82.dp,
    onClick: () -> Unit = {}
) {
    val context = LocalContext.current

    // Bounding Box simétrico: asegura que (lat, lon) esté matemáticamente en el centro del mapa
    val delta = 0.0025 // ~270 metros de cobertura alrededor del lote
    val minLon = lon - delta * 1.5
    val maxLon = lon + delta * 1.5
    val minLat = lat - delta
    val maxLat = lat + delta

    val mapUrl = remember(lat, lon) {
        "https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/export?bbox=$minLon,$minLat,$maxLon,$maxLat&bboxSR=4326&imageSR=4326&size=600,220&format=png&f=image"
    }

    val imageRequest = remember(mapUrl) {
        ImageRequest.Builder(context)
            .data(mapUrl)
            .crossfade(true)
            .build()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(altura)
            .clip(RoundedCornerShape(14.dp))
            .border(1.2.dp, AgroGreenPrimary.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .clickable { onClick() }
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = "Ubicación de la Parcela",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Halo verde translúcido de precisión (10 metros alrededor del punto)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(32.dp)
                .clip(CircleShape)
                .background(AgroGreenPrimary.copy(alpha = 0.28f))
                .border(1.5.dp, AgroGreenPrimary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // Pin rojo central de la parcela exacta
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(SemaforoRojo)
                    .border(2.dp, Color.White, CircleShape)
            )
        }

        // Chip interactivo: "Toca para ajustar"
        Surface(
            shape = RoundedCornerShape(topStart = 8.dp),
            color = AgroGreenPrimary.copy(alpha = 0.92f),
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.OpenInFull, contentDescription = null, tint = Color.White, modifier = Modifier.size(10.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Toca para ajustar", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}
