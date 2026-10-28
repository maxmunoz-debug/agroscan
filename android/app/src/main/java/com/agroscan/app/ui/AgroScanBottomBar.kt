package com.agroscan.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agroscan.app.AppScreen
import com.agroscan.app.ui.theme.*

@Composable
fun AgroScanBottomBar(
    currentScreen: AppScreen,
    onNavigate: (AppScreen) -> Unit,
    onDispararCamara: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .height(84.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Barra inferior redondeada estilo Google Stitch
        Surface(
            color = AgroCardWhite,
            shadowElevation = 12.dp,
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Inicio
                NavItem(
                    icon = Icons.Default.Home,
                    label = "Inicio",
                    selected = (currentScreen == AppScreen.HOME),
                    onClick = { onNavigate(AppScreen.HOME) },
                    modifier = Modifier.weight(1f)
                )

                // 2. Historial
                NavItem(
                    icon = Icons.Default.History,
                    label = "Historial",
                    selected = (currentScreen == AppScreen.HISTORIAL),
                    onClick = { onNavigate(AppScreen.HISTORIAL) },
                    modifier = Modifier.weight(1f)
                )

                // Espacio central para el botón flotante de la cámara
                Spacer(modifier = Modifier.weight(1.1f))

                // 3. Lotes
                NavItem(
                    icon = Icons.Default.Eco,
                    label = "Lotes",
                    selected = (currentScreen == AppScreen.LOTES),
                    onClick = { onNavigate(AppScreen.LOTES) },
                    modifier = Modifier.weight(1f)
                )

                // 4. Ajustes
                NavItem(
                    icon = Icons.Default.Settings,
                    label = "Ajustes",
                    selected = (currentScreen == AppScreen.SETTINGS),
                    onClick = { onNavigate(AppScreen.SETTINGS) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // ================= BOTÓN CIRCULAR FLOTANTE CENTRAL DE CÁMARA (STITCH) =================
        FloatingActionButton(
            onClick = onDispararCamara,
            shape = CircleShape,
            containerColor = AgroGreenPrimary,
            contentColor = Color.White,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 8.dp,
                pressedElevation = 12.dp
            ),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(60.dp)
                .border(3.5.dp, AgroCardWhite, CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.PhotoCamera,
                contentDescription = "Diagnóstico con Cámara",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun NavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (selected) AgroMintBackground else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) AgroGreenPrimary else TextGray,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) AgroGreenPrimary else TextGray
        )
    }
}
