package com.agroscan.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.agroscan.app.data.ChatRequest
import com.agroscan.app.data.MensajeChat
import com.agroscan.app.data.NetworkClient
import com.agroscan.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatbotDialog(
    onDismissRequest: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var mensajeInput by remember { mutableStateOf("") }
    var estaEscribiendo by remember { mutableStateOf(false) }

    // Mensajes iniciales del chat agronómico
    val mensajes = remember {
        mutableStateListOf(
            MensajeChat(
                texto = "¡Hola! Soy tu Asistente Agrónomo IA de AgroScan (Tecsup) 🍓.\n\nPuedes consultarme sobre:\n• Control y dosis de fungicidas (Switch, Captan, Serenade)\n• Síntomas de Botrytis cinerea y hongos en fresa\n• Manejo en macrotúneles y riego\n• Calibración y enfoque de la cámara ESP32",
                esUsuario = false,
                hora = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            )
        )
    }

    val preguntasRapidas = listOf(
        "¿Dosis de Switch para Botrytis?",
        "¿Cómo aplicar Bacillus subtilis?",
        "¿Distancia óptima de captura ESP32?",
        "¿Ventilación adecuada en túneles?"
    )

    fun enviarPregunta(textoPregunta: String) {
        val limpia = textoPregunta.trim()
        if (limpia.isEmpty() || estaEscribiendo) return

        val horaActual = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        mensajes.add(MensajeChat(texto = limpia, esUsuario = true, hora = horaActual))
        mensajeInput = ""
        estaEscribiendo = true

        coroutineScope.launch {
            if (mensajes.isNotEmpty()) {
                listState.animateScrollToItem(mensajes.size - 1)
            }

            val respuestaBot = withContext(Dispatchers.IO) {
                try {
                    val response = NetworkClient.api.enviarMensajeChat(ChatRequest(limpia))
                    response.respuesta
                } catch (e: Exception) {
                    "⚠️ No pude contactar con el Asistente IA (${e.localizedMessage ?: "Error de red"}). Verifica que el backend esté en ejecución y la IP sea correcta en Ajustes."
                }
            }

            val horaRespuesta = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            mensajes.add(MensajeChat(texto = respuestaBot, esUsuario = false, hora = horaRespuesta))
            estaEscribiendo = false
            if (mensajes.isNotEmpty()) {
                listState.animateScrollToItem(mensajes.size - 1)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        // Fondo con velo semitransparente que cubre toda la pantalla
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Contenedor tipo Modal / BottomSheet elevado con bordes redondeados arriba
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .imePadding() // Eleva toda la ventana exactamente sobre el teclado virtual
                    .navigationBarsPadding()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // Evita que clicks dentro del diálogo lo cierren
                    ),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = AgroCardWhite,
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // ================= 1. CABECERA INSTITUCIONAL SIEMPRE FIJA =================
                    Surface(
                        color = AgroGreenPrimary,
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Psychology,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Asistente Agrónomo IA",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Tecsup • Especialista en Fresas 🟢",
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.88f)
                                    )
                                }
                            }

                            IconButton(onClick = onDismissRequest) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cerrar",
                                    tint = Color.White
                                )
                            }
                        }
                    }

                    // ================= 2. CHIPS DE CONSULTAS RÁPIDAS =================
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AgroMintBackground)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(preguntasRapidas) { pregunta ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = AgroCardWhite,
                                border = androidx.compose.foundation.BorderStroke(1.dp, AgroCardBorder),
                                modifier = Modifier.clickable { enviarPregunta(pregunta) }
                            ) {
                                Text(
                                    text = pregunta,
                                    fontSize = 11.sp,
                                    color = AgroGreenPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    // ================= 3. HISTORIAL DE MENSAJES (SE ENCOGE SUAVE, CABECERA QUEDA INTACTA) =================
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(mensajes, key = { it.id }) { mensaje ->
                            val esMio = mensaje.esUsuario

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (esMio) Arrangement.End else Arrangement.Start
                            ) {
                                if (!esMio) {
                                    Box(
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(CircleShape)
                                            .background(AgroGreenPrimary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Eco,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                }

                                Column(
                                    horizontalAlignment = if (esMio) Alignment.End else Alignment.Start,
                                    modifier = Modifier.widthIn(max = 290.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(
                                            topStart = 16.dp,
                                            topEnd = 16.dp,
                                            bottomStart = if (esMio) 16.dp else 4.dp,
                                            bottomEnd = if (esMio) 4.dp else 16.dp
                                        ),
                                        color = if (esMio) AgroGreenPrimary else AgroMintBackground,
                                        border = if (esMio) null else androidx.compose.foundation.BorderStroke(1.dp, AgroCardBorder)
                                    ) {
                                        Text(
                                            text = mensaje.texto,
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp,
                                            color = if (esMio) Color.White else TextDark,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                        )
                                    }

                                    Text(
                                        text = mensaje.hora,
                                        fontSize = 10.sp,
                                        color = TextGray,
                                        modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)
                                    )
                                }
                            }
                        }

                        if (estaEscribiendo) {
                            item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 38.dp, top = 4.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = AgroGreenPrimary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "El agrónomo IA está respondiendo...",
                                        fontSize = 11.sp,
                                        color = TextGray
                                    )
                                }
                            }
                        }
                    }

                    // ================= 4. BARRA DE ENTRADA FIJA ELEVADA =================
                    Surface(
                        color = AgroCardWhite,
                        tonalElevation = 8.dp,
                        border = androidx.compose.foundation.BorderStroke(1.dp, AgroCardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = mensajeInput,
                                onValueChange = { mensajeInput = it },
                                placeholder = {
                                    Text("Escribe tu consulta agronómica...", fontSize = 13.sp)
                                },
                                maxLines = 3,
                                shape = RoundedCornerShape(20.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AgroGreenPrimary,
                                    unfocusedBorderColor = AgroCardBorder,
                                    focusedContainerColor = AgroMintBackground,
                                    unfocusedContainerColor = AgroMintBackground
                                ),
                                modifier = Modifier.weight(1f)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            IconButton(
                                onClick = { enviarPregunta(mensajeInput) },
                                enabled = mensajeInput.isNotBlank() && !estaEscribiendo,
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = AgroGreenPrimary,
                                    contentColor = Color.White,
                                    disabledContainerColor = AgroCardBorder,
                                    disabledContentColor = TextGray
                                ),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Enviar",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
