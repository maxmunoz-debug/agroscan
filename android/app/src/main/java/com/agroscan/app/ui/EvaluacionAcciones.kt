package com.agroscan.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.agroscan.app.data.EvaluacionHistorial
import com.agroscan.app.ui.theme.*

// Paleta neutral para muestras no válidas (no es una fresa)
val NeutralGris = Color(0xFF6B7280)
val NeutralGrisFondo = Color(0xFFF3F4F6)

/**
 * Columna vertical con dos acciones para cada tarjeta de evaluación:
 * arriba Descargar PDF y al pie Mover a la papelera de auditoría.
 */
@Composable
fun BotonesAccionEvaluacion(
    onDescargar: () -> Unit,
    onEliminar: () -> Unit,
    tamano: Dp = 36.dp
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            onClick = onDescargar,
            shape = CircleShape,
            color = AgroMintBackground,
            border = BorderStroke(1.dp, AgroGreenPrimary.copy(alpha = 0.35f)),
            modifier = Modifier.size(tamano)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Descargar PDF",
                    tint = AgroGreenPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Surface(
            onClick = onEliminar,
            shape = CircleShape,
            color = SemaforoRojoFondo.copy(alpha = 0.6f),
            border = BorderStroke(1.dp, SemaforoRojo.copy(alpha = 0.25f)),
            modifier = Modifier.size(tamano)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Mover a la papelera",
                    tint = SemaforoRojo.copy(alpha = 0.85f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Confirmación antes de mover una evaluación a la papelera.
 * Pide un motivo para dejar trazabilidad (auditoría).
 */
@Composable
fun ConfirmarPapeleraDialog(
    evaluacion: EvaluacionHistorial,
    onConfirmar: (motivo: String) -> Unit,
    onCancelar: () -> Unit
) {
    val motivos = listOf("Foto errónea", "Registro duplicado", "Lote equivocado", "Otro")
    var motivoSel by remember { mutableStateOf(motivos.first()) }
    var motivoOtro by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onCancelar,
        icon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = SemaforoRojo) },
        title = { Text("¿Mover a la papelera?", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${evaluacion.loteNombre} • ${evaluacion.fechaHora}",
                    fontSize = 12.sp,
                    color = TextGray,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "El registro no se borra: queda en la papelera de auditoría con fecha, evaluador y motivo, y puedes restaurarlo cuando quieras.",
                    fontSize = 12.5.sp,
                    color = TextDark,
                    lineHeight = 17.sp
                )
                Text("Motivo:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
                motivos.forEach { m ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = motivoSel == m,
                            onClick = { motivoSel = m },
                            colors = RadioButtonDefaults.colors(selectedColor = AgroGreenPrimary)
                        )
                        Text(m, fontSize = 13.sp, color = TextDark)
                    }
                }
                if (motivoSel == "Otro") {
                    OutlinedTextField(
                        value = motivoOtro,
                        onValueChange = { motivoOtro = it },
                        placeholder = { Text("Describe el motivo", fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val motivoFinal = if (motivoSel == "Otro") motivoOtro.trim().ifBlank { "Otro" } else motivoSel
                    onConfirmar(motivoFinal)
                },
                colors = ButtonDefaults.buttonColors(containerColor = SemaforoRojo),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Mover a papelera", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancelar) { Text("Cancelar", color = TextGray) }
        },
        containerColor = AgroCardWhite
    )
}

/**
 * Papelera de Auditoría: lista de evaluaciones eliminadas con su trazabilidad
 * (fecha de eliminación, evaluador y motivo) y opción de restaurar.
 */
@Composable
fun PapeleraEvaluacionesDialog(
    papelera: List<EvaluacionHistorial>,
    onRestaurar: (EvaluacionHistorial) -> Unit,
    onCerrar: () -> Unit
) {
    Dialog(onDismissRequest = onCerrar) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = AgroCardWhite,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = AgroGreenPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Papelera de Auditoría", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = TextDark)
                        Text("${papelera.size} registro(s) eliminados", fontSize = 11.5.sp, color = TextGray)
                    }
                    IconButton(onClick = onCerrar) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextGray)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))

                if (papelera.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 30.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("La papelera está vacía", color = TextGray, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(papelera, key = { it.id }) { ev ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = AgroMintBackground,
                                border = BorderStroke(1.dp, AgroCardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            ev.loteNombre,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = TextDark,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            "Análisis: ${ev.fechaHora} • ${ev.resultado.fruta}",
                                            fontSize = 11.sp,
                                            color = TextGray,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            "Eliminado: ${ev.fechaEliminacion ?: "-"} por ${ev.eliminadoPor ?: "-"}",
                                            fontSize = 11.sp,
                                            color = SemaforoRojo.copy(alpha = 0.85f)
                                        )
                                        Text(
                                            "Motivo: ${ev.motivoEliminacion ?: "-"}",
                                            fontSize = 11.sp,
                                            color = TextDark
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    OutlinedButton(
                                        onClick = { onRestaurar(ev) },
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, AgroGreenPrimary),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Restore,
                                            contentDescription = null,
                                            tint = AgroGreenPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Restaurar", fontSize = 12.sp, color = AgroGreenPrimary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
