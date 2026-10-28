package com.agroscan.app.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agroscan.app.data.LoteItem
import com.agroscan.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LotesScreen(
    lotes: MutableList<LoteItem>,
    papeleraLotes: MutableList<LoteItem>,
    loteActivoActual: String,
    onSeleccionarLoteActivo: (nombreCompletoLote: String) -> Unit,
    onIniciarEvaluacionEnLote: (nombreLote: String) -> Unit,
    onVolver: () -> Unit
) {
    val context = LocalContext.current
    var busqueda by remember { mutableStateOf("") }

    var showDialogNuevoLote by remember { mutableStateOf(false) }
    var showDialogEditarLote by remember { mutableStateOf(false) }
    var showDialogPapelera by remember { mutableStateOf(false) }
    var loteAEditarIndex by remember { mutableStateOf<Int?>(null) }
    var loteAEliminarIndex by remember { mutableStateOf<Int?>(null) }

    val lotesFiltrados = lotes.filter {
        it.nombre.contains(busqueda, ignoreCase = true) ||
                it.variedad.contains(busqueda, ignoreCase = true) ||
                it.ubicacion.contains(busqueda, ignoreCase = true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Gestión de Lotes", fontWeight = FontWeight.Bold, fontSize = 19.sp, color = TextDark)
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = SemaforoVerdeFondo
                            ) {
                                Text(
                                    text = "${lotes.size} activos",
                                    color = SemaforoVerde,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        Text("Elige o edita tu parcela de fresas", fontSize = 12.sp, color = TextGray)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver a Inicio", tint = AgroGreenPrimary)
                    }
                },
                actions = {
                    // Botón para ver la Papelera de reciclaje
                    IconButton(onClick = { showDialogPapelera = true }) {
                        BadgedBox(
                            badge = {
                                if (papeleraLotes.isNotEmpty()) {
                                    Badge(containerColor = SemaforoRojo) { Text("${papeleraLotes.size}") }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Papelera", tint = TextGray)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AgroMintBackground)
            )
        },
        containerColor = AgroMintBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            // Buscador
            OutlinedTextField(
                value = busqueda,
                onValueChange = { busqueda = it },
                placeholder = { Text("Buscar lote o variedad...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextGray) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AgroGreenPrimary,
                    unfocusedBorderColor = AgroCardBorder,
                    focusedContainerColor = AgroCardWhite,
                    unfocusedContainerColor = AgroCardWhite
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Lista de Tarjetas de Lotes
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(lotesFiltrados) { index, lote ->
                    val esActivo = loteActivoActual.contains(lote.nombre)

                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (esActivo) SemaforoVerdeFondo else AgroCardWhite
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (esActivo) SemaforoVerde else AgroCardBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val textoLote = "${lote.nombre} (${lote.ubicacion}) • Fresa ${lote.variedad}"
                                onSeleccionarLoteActivo(textoLote)
                                Toast.makeText(context, "Lote activo: ${lote.nombre}", Toast.LENGTH_SHORT).show()
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(if (esActivo) SemaforoVerde else AgroMintBackground),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Spa,
                                        contentDescription = null,
                                        tint = if (esActivo) Color.White else AgroGreenPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column {
                                    Text(
                                        text = "${lote.nombre} - ${lote.variedad}",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextDark
                                    )
                                    Text(
                                        text = lote.ubicacion,
                                        fontSize = 12.sp,
                                        color = TextGray
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    when (lote.estado) {
                                                        "Óptimo" -> SemaforoVerde
                                                        "En Revisión" -> SemaforoAmarillo
                                                        else -> SemaforoRojo
                                                    }
                                                )
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Estado: ${lote.estado}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = when (lote.estado) {
                                                "Óptimo" -> SemaforoVerde
                                                "En Revisión" -> SemaforoAmarillo
                                                else -> SemaforoRojo
                                            }
                                        )
                                    }
                                }
                            }

                            // Acciones: Editar, Evaluar o Borrar
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        loteAEditarIndex = index
                                        showDialogEditarLote = true
                                    }
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Editar", tint = AgroGreenPrimary, modifier = Modifier.size(20.dp))
                                }

                                IconButton(
                                    onClick = { onIniciarEvaluacionEnLote(lote.nombre) }
                                ) {
                                    Icon(Icons.Default.PhotoCamera, contentDescription = "Evaluar", tint = AgroGreenPrimary, modifier = Modifier.size(20.dp))
                                }

                                if (lotes.size > 1) {
                                    IconButton(
                                        onClick = {
                                            loteAEliminarIndex = index // Abre confirmación
                                        }
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Borrar", tint = SemaforoRojo, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Botón Añadir Lote
            Button(
                onClick = { showDialogNuevoLote = true },
                colors = ButtonDefaults.buttonColors(containerColor = AgroGreenPrimary),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Añadir nuevo lote o surco", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }

        // ================= DIÁLOGO DE CONFIRMACIÓN DE BORRADO =================
        if (loteAEliminarIndex != null) {
            val loteABorrar = lotes[loteAEliminarIndex!!]
            AlertDialog(
                onDismissRequest = { loteAEliminarIndex = null },
                icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = SemaforoRojo) },
                title = { Text("¿Mover a la Papelera?", fontWeight = FontWeight.Bold) },
                text = {
                    Text("El lote '${loteABorrar.nombre}' será movido a la papelera. Podrás restaurarlo cuando desees.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            papeleraLotes.add(loteABorrar)
                            lotes.removeAt(loteAEliminarIndex!!)
                            loteAEliminarIndex = null
                            Toast.makeText(context, "Lote movido a la papelera", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SemaforoRojo)
                    ) {
                        Text("Mover a Papelera")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { loteAEliminarIndex = null }) {
                        Text("Cancelar", color = TextGray)
                    }
                },
                containerColor = AgroCardWhite,
                shape = RoundedCornerShape(20.dp)
            )
        }

        // ================= DIÁLOGO DE LA PAPELERA =================
        if (showDialogPapelera) {
            AlertDialog(
                onDismissRequest = { showDialogPapelera = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = AgroGreenPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Papelera de Lotes", fontWeight = FontWeight.Bold, color = AgroGreenPrimary)
                    }
                },
                text = {
                    if (papeleraLotes.isEmpty()) {
                        Text("La papelera está vacía.", color = TextGray, fontSize = 13.sp)
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            papeleraLotes.forEachIndexed { pIndex, pLote ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = AgroMintBackground,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(pLote.nombre, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDark)
                                            Text(pLote.variedad, fontSize = 11.sp, color = TextGray)
                                        }

                                        Row {
                                            IconButton(
                                                onClick = {
                                                    lotes.add(pLote)
                                                    papeleraLotes.removeAt(pIndex)
                                                    Toast.makeText(context, "Lote restaurado ↩️", Toast.LENGTH_SHORT).show()
                                                }
                                            ) {
                                                Icon(Icons.Default.Restore, contentDescription = "Restaurar", tint = SemaforoVerde)
                                            }

                                            IconButton(
                                                onClick = {
                                                    papeleraLotes.removeAt(pIndex)
                                                    Toast.makeText(context, "Eliminado permanentemente", Toast.LENGTH_SHORT).show()
                                                }
                                            ) {
                                                Icon(Icons.Default.DeleteForever, contentDescription = "Eliminar", tint = SemaforoRojo)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showDialogPapelera = false }) { Text("Cerrar") }
                },
                containerColor = AgroCardWhite,
                shape = RoundedCornerShape(20.dp)
            )
        }

        // Diálogo para Añadir Lote
        if (showDialogNuevoLote) {
            var nombreLoteInput by remember { mutableStateOf("") }
            var variedadInput by remember { mutableStateOf("") }
            var ubicacionInput by remember { mutableStateOf("") }

            AlertDialog(
                onDismissRequest = { showDialogNuevoLote = false },
                title = { Text("Nuevo Lote de Fresa", fontWeight = FontWeight.Bold, color = AgroGreenPrimary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(value = nombreLoteInput, onValueChange = { nombreLoteInput = it }, label = { Text("Nombre del Lote (Ej. Lote 2)") })
                        OutlinedTextField(value = variedadInput, onValueChange = { variedadInput = it }, label = { Text("Variedad (Ej. San Andreas, Monterrey)") })
                        OutlinedTextField(value = ubicacionInput, onValueChange = { ubicacionInput = it }, label = { Text("Ubicación (Ej. Invernadero B, Surco 4)") })
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (nombreLoteInput.trim().isNotEmpty()) {
                                lotes.add(
                                    LoteItem(
                                        id = lotes.size + 1,
                                        nombre = nombreLoteInput.trim(),
                                        variedad = if (variedadInput.trim().isNotEmpty()) variedadInput.trim() else "Albión",
                                        ubicacion = if (ubicacionInput.trim().isNotEmpty()) ubicacionInput.trim() else "Sector General",
                                        estado = "Óptimo",
                                        ultimaEvaluacion = "Recién registrado"
                                    )
                                )
                                showDialogNuevoLote = false
                                Toast.makeText(context, "Lote registrado", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AgroGreenPrimary)
                    ) { Text("Guardar Lote") }
                },
                dismissButton = {
                    TextButton(onClick = { showDialogNuevoLote = false }) { Text("Cancelar") }
                }
            )
        }

        // Diálogo para Editar Lote
        if (showDialogEditarLote && loteAEditarIndex != null) {
            val loteActual = lotes[loteAEditarIndex!!]
            var editNombre by remember { mutableStateOf(loteActual.nombre) }
            var editVariedad by remember { mutableStateOf(loteActual.variedad) }
            var editUbicacion by remember { mutableStateOf(loteActual.ubicacion) }

            AlertDialog(
                onDismissRequest = { showDialogEditarLote = false },
                title = { Text("Editar ${loteActual.nombre}", fontWeight = FontWeight.Bold, color = AgroGreenPrimary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(value = editNombre, onValueChange = { editNombre = it }, label = { Text("Nombre") })
                        OutlinedTextField(value = editVariedad, onValueChange = { editVariedad = it }, label = { Text("Variedad") })
                        OutlinedTextField(value = editUbicacion, onValueChange = { editUbicacion = it }, label = { Text("Ubicación") })
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (editNombre.trim().isNotEmpty()) {
                                lotes[loteAEditarIndex!!] = loteActual.copy(
                                    nombre = editNombre.trim(),
                                    variedad = editVariedad.trim(),
                                    ubicacion = editUbicacion.trim()
                                )
                                showDialogEditarLote = false
                                Toast.makeText(context, "Lote actualizado", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AgroGreenPrimary)
                    ) { Text("Guardar Cambios") }
                },
                dismissButton = {
                    TextButton(onClick = { showDialogEditarLote = false }) { Text("Cancelar") }
                }
            )
        }
    }
}