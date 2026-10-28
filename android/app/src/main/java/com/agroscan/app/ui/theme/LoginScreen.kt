package com.agroscan.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agroscan.app.ui.theme.*

@Composable
fun LoginScreen(
    onLoginExitoso: (nombreUsuario: String) -> Unit
) {
    var nombre by remember { mutableStateOf("") }
    var correo by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMensaje by remember { mutableStateOf<String?>(null) }

    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current

    Scaffold(
        containerColor = AgroMintBackground,
        modifier = Modifier
            .fillMaxSize()
            .imePadding() // ¡EL TRUCO CLAVE! Ajusta la pantalla automáticamente cuando sube el teclado
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Logo de AgroScan

                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .clip(CircleShape)
                        .background(AgroCardWhite)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = com.agroscan.app.R.drawable.agroscan_logo),
                        contentDescription = "Logo Oficial AgroScan",
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 2. Nombre del Sistema y Subtítulo
                Text(
                    text = "agroScan",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = AgroGreenPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Diagnóstico inteligente de calidad y\nsanidad en cultivo de fresa",
                    fontSize = 13.sp,
                    color = TextGray,
                    lineHeight = 18.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 3. Chip: Sistema IA • Semáforo de Calidad
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SemaforoVerdeFondo
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(SemaforoVerde)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Sistema IA • Semáforo de Calidad",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SemaforoVerde
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 4. Tarjeta Blanca del Formulario
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = AgroCardWhite),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AgroCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Iniciar Sesión",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )

                        // Campo Nombre del Productor
                        OutlinedTextField(
                            value = nombre,
                            onValueChange = { nombre = it },
                            label = { Text("Nombre del Productor / Evaluador") },
                            placeholder = { Text("Ej. Max") },
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = null, tint = AgroGreenPrimary)
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AgroGreenPrimary,
                                unfocusedBorderColor = AgroCardBorder
                            )
                        )

                        // Campo Correo
                        OutlinedTextField(
                            value = correo,
                            onValueChange = { correo = it },
                            label = { Text("Correo electrónico") },
                            placeholder = { Text("ejemplo@campoagricola.com") },
                            leadingIcon = {
                                Icon(Icons.Default.Email, contentDescription = null, tint = AgroGreenPrimary)
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AgroGreenPrimary,
                                unfocusedBorderColor = AgroCardBorder
                            )
                        )

                        // Campo Contraseña
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Contraseña") },
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = AgroGreenPrimary)
                            },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = null,
                                        tint = TextGray
                                    )
                                }
                            },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { focusManager.clearFocus() }
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AgroGreenPrimary,
                                unfocusedBorderColor = AgroCardBorder
                            )
                        )

                        if (errorMensaje != null) {
                            Text(
                                text = errorMensaje!!,
                                color = SemaforoRojo,
                                fontSize = 12.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Botón Principal: Ingresar al Cultivo
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                if (nombre.trim().isEmpty()) {
                                    errorMensaje = "Por favor ingresa tu nombre de evaluador"
                                } else {
                                    errorMensaje = null
                                    onLoginExitoso(nombre.trim())
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AgroGreenPrimary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Text(
                                text = "Ingresar al Cultivo",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.Default.ArrowForward, contentDescription = null)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "agroScan v1.0 • Optimizado para campo",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }
        }
    }
}