package com.agroscan.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agroscan.app.R
import com.agroscan.app.data.LoginRequest
import com.agroscan.app.data.NetworkClient
import com.agroscan.app.data.RegisterRequest
import com.agroscan.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import retrofit2.HttpException
import java.io.IOException

enum class AuthModo {
    LOGIN,
    REGISTRO
}

/**
 * Pantalla de Autenticación Institucional (Login y Registro):
 * - Conectada 100% en tiempo real a FastAPI y SQLite (sin simulación ni datos falsos).
 * - Selector sobrio entre Iniciar Sesión y Crear Cuenta.
 * - Validación exhaustiva de credenciales y feedback visual claro.
 * - Permite verificar o ajustar la IP del servidor de inspección.
 */
@Composable
fun LoginScreen(
    onLoginExitoso: (nombreUsuario: String, emailUsuario: String, rol: String) -> Unit
) {
    var modoActual by remember { mutableStateOf(AuthModo.LOGIN) }

    var nombre by remember { mutableStateOf("") }
    var correo by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    var cargando by remember { mutableStateOf(false) }
    var errorMensaje by remember { mutableStateOf<String?>(null) }
    var mostrarDialogoIp by remember { mutableStateOf(false) }
    var ipTemp by remember { mutableStateOf(NetworkClient.currentBaseUrl) }

    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current

    Scaffold(
        containerColor = AgroMintBackground,
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
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
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Logo Oficial AgroScan
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(AgroCardWhite)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.agroscan_logo),
                        contentDescription = "Logo Oficial AgroScan",
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 2. Título Institucional
                Text(
                    text = "agroScan",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = AgroGreenPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Plataforma de Inspección Fitosanitaria en Fresas",
                    fontSize = 12.sp,
                    color = TextGray,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(18.dp))

                // 3. Selector de Pestañas: [ Iniciar Sesión ] | [ Registrarse ]
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = AgroCardWhite,
                    border = androidx.compose.foundation.BorderStroke(1.dp, AgroCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp)
                    ) {
                        // Pestaña Iniciar Sesión
                        Surface(
                            onClick = {
                                if (!cargando) {
                                    modoActual = AuthModo.LOGIN
                                    errorMensaje = null
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (modoActual == AuthModo.LOGIN) AgroGreenPrimary else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Iniciar Sesión",
                                    fontSize = 13.sp,
                                    fontWeight = if (modoActual == AuthModo.LOGIN) FontWeight.Bold else FontWeight.Medium,
                                    color = if (modoActual == AuthModo.LOGIN) Color.White else TextDark
                                )
                            }
                        }

                        // Pestaña Registrarse
                        Surface(
                            onClick = {
                                if (!cargando) {
                                    modoActual = AuthModo.REGISTRO
                                    errorMensaje = null
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (modoActual == AuthModo.REGISTRO) AgroGreenPrimary else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Crear Cuenta",
                                    fontSize = 13.sp,
                                    fontWeight = if (modoActual == AuthModo.REGISTRO) FontWeight.Bold else FontWeight.Medium,
                                    color = if (modoActual == AuthModo.REGISTRO) Color.White else TextDark
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 4. Formulario Principal
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = AgroCardWhite),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AgroCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Campo Nombre (Solo en Modo Registro)
                        if (modoActual == AuthModo.REGISTRO) {
                            OutlinedTextField(
                                value = nombre,
                                onValueChange = {
                                    nombre = it
                                    errorMensaje = null
                                },
                                enabled = !cargando,
                                label = { Text("Nombre Completo") },
                                placeholder = { Text("Ej. Carlos Mendoza") },
                                leadingIcon = {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = AgroGreenPrimary)
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AgroGreenPrimary,
                                    unfocusedBorderColor = AgroCardBorder
                                )
                            )
                        }

                        // Campo Correo Electrónico / Usuario
                        OutlinedTextField(
                            value = correo,
                            onValueChange = {
                                correo = it
                                errorMensaje = null
                            },
                            enabled = !cargando,
                            label = { Text(if (modoActual == AuthModo.LOGIN) "Correo o Usuario" else "Correo Electrónico") },
                            placeholder = { Text(if (modoActual == AuthModo.LOGIN) "investigacion@tecsup.edu.pe" else "evaluador@tecsup.edu.pe") },
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
                            onValueChange = {
                                password = it
                                errorMensaje = null
                            },
                            enabled = !cargando,
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
                                imeAction = if (modoActual == AuthModo.REGISTRO) ImeAction.Next else ImeAction.Done
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

                        // Campo Confirmar Contraseña (Solo en Modo Registro)
                        if (modoActual == AuthModo.REGISTRO) {
                            OutlinedTextField(
                                value = confirmPassword,
                                onValueChange = {
                                    confirmPassword = it
                                    errorMensaje = null
                                },
                                enabled = !cargando,
                                label = { Text("Confirmar Contraseña") },
                                leadingIcon = {
                                    Icon(Icons.Default.LockReset, contentDescription = null, tint = AgroGreenPrimary)
                                },
                                trailingIcon = {
                                    IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                        Icon(
                                            imageVector = if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = null,
                                            tint = TextGray
                                        )
                                    }
                                },
                                visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
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
                        }

                        // Mensaje de Error
                        if (errorMensaje != null) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = SemaforoRojoFondo,
                                border = androidx.compose.foundation.BorderStroke(1.dp, SemaforoRojo.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        tint = SemaforoRojo,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = errorMensaje!!,
                                        color = SemaforoRojo,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        // Botón de Acción Principal (Conectado a la API Real)
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                if (modoActual == AuthModo.LOGIN) {
                                    if (correo.trim().isEmpty()) {
                                        errorMensaje = "Por favor ingresa tu correo o usuario"
                                        return@Button
                                    }
                                    if (password.trim().isEmpty()) {
                                        errorMensaje = "Por favor ingresa tu contraseña"
                                        return@Button
                                    }

                                    cargando = true
                                    errorMensaje = null

                                    coroutineScope.launch {
                                        try {
                                            val response = withContext(Dispatchers.IO) {
                                                NetworkClient.api.login(
                                                    LoginRequest(
                                                        email = correo.trim().lowercase(),
                                                        password = password.trim().trim()
                                                    )
                                                )
                                            }
                                            cargando = false
                                            onLoginExitoso(response.nombre, response.email, response.rol)
                                        } catch (e: HttpException) {
                                            cargando = false
                                            val errorBody = e.response()?.errorBody()?.string()
                                            val detalle = try {
                                                JSONObject(errorBody ?: "").optString("detail", "Credenciales incorrectas")
                                            } catch (_: Exception) {
                                                "Credenciales incorrectas (Código ${e.code()})"
                                            }
                                            errorMensaje = detalle
                                        } catch (e: IOException) {
                                            cargando = false
                                            errorMensaje = "No se pudo conectar al servidor (${NetworkClient.currentBaseUrl}). Verifica que el backend esté activo."
                                        } catch (e: Exception) {
                                            cargando = false
                                            errorMensaje = e.localizedMessage ?: "Error de comunicación con el servidor"
                                        }
                                    }

                                } else {
                                    // Modo REGISTRO
                                    if (nombre.trim().isEmpty()) {
                                        errorMensaje = "Por favor ingresa tu nombre completo"
                                        return@Button
                                    }
                                    if (correo.trim().isEmpty() || !correo.contains("@")) {
                                        errorMensaje = "Ingresa un correo electrónico válido"
                                        return@Button
                                    }
                                    if (password.length < 6) {
                                        errorMensaje = "La contraseña debe tener al menos 6 caracteres"
                                        return@Button
                                    }
                                    if (password != confirmPassword) {
                                        errorMensaje = "Las contraseñas no coinciden"
                                        return@Button
                                    }

                                    cargando = true
                                    errorMensaje = null

                                    coroutineScope.launch {
                                        try {
                                            val response = withContext(Dispatchers.IO) {
                                                NetworkClient.api.register(
                                                    RegisterRequest(
                                                        nombre = nombre.trim(),
                                                        email = correo.trim().lowercase(),
                                                        password = password.trim()
                                                    )
                                                )
                                            }
                                            cargando = false
                                            onLoginExitoso(response.nombre, response.email, response.rol)
                                        } catch (e: HttpException) {
                                            cargando = false
                                            val errorBody = e.response()?.errorBody()?.string()
                                            val detalle = try {
                                                JSONObject(errorBody ?: "").optString("detail", "Error al registrar")
                                            } catch (_: Exception) {
                                                "Error al registrar usuario (Código ${e.code()})"
                                            }
                                            errorMensaje = detalle
                                        } catch (e: IOException) {
                                            cargando = false
                                            errorMensaje = "No se pudo conectar al servidor (${NetworkClient.currentBaseUrl}). Verifica que el backend esté activo."
                                        } catch (e: Exception) {
                                            cargando = false
                                            errorMensaje = e.localizedMessage ?: "Error inesperado al registrar"
                                        }
                                    }
                                }
                            },
                            enabled = !cargando,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AgroGreenPrimary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            if (cargando) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Verificando credenciales...", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Text(
                                    text = if (modoActual == AuthModo.LOGIN) "Ingresar al Sistema" else "Registrar Evaluador",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Enlace rápido para cambiar de modo
                TextButton(
                    onClick = {
                        if (!cargando) {
                            modoActual = if (modoActual == AuthModo.LOGIN) AuthModo.REGISTRO else AuthModo.LOGIN
                            errorMensaje = null
                        }
                    }
                ) {
                    Text(
                        text = if (modoActual == AuthModo.LOGIN) "¿No tienes cuenta? Regístrate aquí" else "¿Ya tienes cuenta? Inicia sesión",
                        color = AgroGreenPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Configuración de Conexión / IP del Servidor
                TextButton(
                    onClick = {
                        ipTemp = NetworkClient.currentBaseUrl
                        mostrarDialogoIp = true
                    }
                ) {
                    Icon(Icons.Default.SettingsEthernet, contentDescription = null, tint = TextGray, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Servidor: ${NetworkClient.currentBaseUrl.removePrefix("http://").removeSuffix("/")}",
                        color = TextGray,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Investigación Aplicada • Tecsup 2026",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }
        }
    }

    // Diálogo para ajustar la IP del Servidor de Inspección directamente desde el Login
    if (mostrarDialogoIp) {
        AlertDialog(
            onDismissRequest = { mostrarDialogoIp = false },
            title = { Text("Configurar Servidor", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Ingresa la dirección IP del servidor donde corre el backend de AgroScan:",
                        fontSize = 13.sp,
                        color = TextDark
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = ipTemp,
                        onValueChange = { ipTemp = it },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "USB: http://127.0.0.1:8000/\nWi-Fi: http://10.250.201.202:8000/",
                        fontSize = 11.sp,
                        color = TextGray
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        NetworkClient.setBaseUrl(ipTemp.trim())
                        mostrarDialogoIp = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AgroGreenPrimary)
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarDialogoIp = false }) {
                    Text("Cancelar", color = TextGray)
                }
            }
        )
    }
}
