package com.tuapp.tabatatrainer.ui.freeride

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuapp.tabatatrainer.service.LapData
import com.tuapp.tabatatrainer.util.laps.LapRowFormatter

/**
 * Lista de vueltas compartida por portrait y landscape (spec 006, R5/R6).
 * Fila: `[bandera] etiqueta+nº · distancia · tiempo · ritmo · FC · pasos`. El estilo sale de [LapRowFormatter]
 * (solo del tipo de vuelta): AUTO tenue y normal, MANUAL en negrita, acento, bandera y fondo tintado.
 * La más reciente arriba.
 */
@Composable
internal fun LapHistoryList(
    laps: List<LapData>,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val headerSize = if (compact) 10.sp else 12.sp
    val bodySize = if (compact) 11.sp else 14.sp
    val labelWidth = if (compact) 46.dp else 58.dp
    val hPad = if (compact) 8.dp else 12.dp
    val headerVPad = if (compact) 6.dp else 8.dp
    val rowVPad = if (compact) 6.dp else 10.dp
    val headerColor = Color.White.copy(alpha = 0.7f)

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .padding(horizontal = hPad, vertical = headerVPad)
        ) {
            Text("#", Modifier.width(labelWidth), color = headerColor, fontSize = headerSize)
            Text("Dist.", Modifier.weight(1f), color = headerColor, fontSize = headerSize)
            Text("Tiempo", Modifier.weight(1f), color = headerColor, fontSize = headerSize)
            Text("Ritmo", Modifier.weight(1f), color = headerColor, fontSize = headerSize)
            Text("FC", Modifier.weight(0.8f), color = headerColor, fontSize = headerSize)
            Text("Pasos", Modifier.weight(0.9f), color = headerColor, fontSize = headerSize)
        }

        LazyColumn(modifier = Modifier.weight(1f, fill = false).fillMaxWidth()) {
            items(laps.reversed(), key = { it.number }) { lap ->
                val row = LapRowFormatter.format(
                    type = lap.type,
                    typeNumber = lap.typeNumber,
                    distanceMeters = lap.distanceMeters,
                    durationSeconds = lap.endTimeSeconds - lap.startTimeSeconds
                )
                val color = Color(row.style.textColorArgb)
                val weight = if (row.style.bold) FontWeight.Bold else FontWeight.Normal

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(row.style.rowBackgroundArgb))
                        .padding(horizontal = hPad, vertical = rowVPad),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.width(labelWidth),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        if (row.style.showFlag) {
                            Icon(
                                Icons.Default.Flag,
                                contentDescription = "Vuelta manual",
                                tint = color,
                                modifier = Modifier.size(if (compact) 11.dp else 14.dp)
                            )
                        }
                        Text(row.label, color = color, fontWeight = weight, fontSize = bodySize)
                    }
                    Text(row.distanceKm, Modifier.weight(1f), color = color, fontWeight = weight, fontSize = bodySize)
                    Text(row.time, Modifier.weight(1f), color = color, fontWeight = weight, fontSize = bodySize)
                    Text(row.pace, Modifier.weight(1f), color = color, fontWeight = weight, fontSize = bodySize)
                    Text(
                        if (lap.avgHeartRate > 0) "${lap.avgHeartRate}" else "--",
                        Modifier.weight(0.8f),
                        color = color,
                        fontWeight = weight,
                        fontSize = bodySize
                    )
                    Text(lap.steps?.toString() ?: "--", Modifier.weight(0.9f), color = color, fontWeight = weight, fontSize = bodySize)
                }
            }
        }
    }
}
