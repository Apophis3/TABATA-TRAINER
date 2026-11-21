package com.tuapp.tabatatrainer.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuapp.tabatatrainer.sensor.SlotStatus
import com.tuapp.tabatatrainer.ui.theme.TabataColors

/** GPS en la barra: apagado (gris), buscando (amarillo, parpadea) o con señal (verde) */
enum class GpsIndicator { OFF, SEARCHING, FIX }

/**
 * Estilo de la barra:
 * - [CARD]: Home y Config. Rejilla HR1 HR2 / C1 C2 con valor, hueco y batería.
 * - [ROW]: Ruta en vertical y Tabata. GPS y los 4 huecos en fila.
 * - [COMPACT]: Ruta en horizontal (páginas 1 y 3). GPS y los 4 huecos pequeños, sin batería ni flecha.
 */
enum class SensorBarStyle { CARD, ROW, COMPACT }

/** Valor de un hueco en la barra: solo con dato reciente ("--" si no, R2.1); en cadencia 0 rpm reciente es "0" */
fun slotValueText(status: SlotStatus?, isCadence: Boolean): String = when {
    status == null || !status.connected || !status.fresh -> "--"
    isCadence || status.value > 0 -> "${status.value}"
    else -> "--"
}

/**
 * Barra de sensores común (spec 007, T-06; R8.1, R8.2): la misma en Home, Config, Tabata y Ruta.
 * Tocarla abre la hoja "Dispositivos" ([onOpenDevices]); la flecha ([onReconnect], si no es null) solo reconecta.
 * [slots] = HR1, HR2, C1, C2 (ver [rememberSensorSlots]); [gps] null = sin GPS en la barra.
 */
@Composable
fun SensorBar(
    slots: List<SlotStatus>,
    gps: GpsIndicator?,
    style: SensorBarStyle,
    onOpenDevices: () -> Unit,
    modifier: Modifier = Modifier,
    onReconnect: (() -> Unit)? = null,
    showBattery: Boolean = true,
    containerColor: Color = Color.Black.copy(alpha = 0.3f),
    accent: Color = Color.White.copy(alpha = 0.7f)
) {
    when (style) {
        SensorBarStyle.CARD -> CardBar(slots, gps, onOpenDevices, modifier, onReconnect, showBattery, containerColor, accent)
        SensorBarStyle.ROW -> RowBar(slots, gps, onOpenDevices, modifier, onReconnect, showBattery, containerColor, accent)
        SensorBarStyle.COMPACT -> CompactBar(slots, gps, onOpenDevices, modifier)
    }
}

private fun Modifier.opensDevices(onOpenDevices: () -> Unit) =
    clickable(onClickLabel = "Abrir dispositivos", role = Role.Button) { onOpenDevices() }

@Composable
private fun ReconnectButton(onReconnect: () -> Unit, tint: Color, size: Dp) {
    IconButton(onClick = onReconnect, modifier = Modifier.size(size)) {
        Icon(Icons.Default.Refresh, contentDescription = "Reconectar sensores", tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun CardBar(
    slots: List<SlotStatus>,
    gps: GpsIndicator?,
    onOpenDevices: () -> Unit,
    modifier: Modifier,
    onReconnect: (() -> Unit)?,
    showBattery: Boolean,
    containerColor: Color,
    accent: Color
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(containerColor, shape)
            .opensDevices(onOpenDevices)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // HR1 HR2 arriba, C1 C2 abajo: valor + hueco en su recuadro de señal + batería
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (row in 0..1) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    for (col in 0..1) {
                        val i = row * 2 + col
                        SlotValue(SENSOR_SLOT_LABELS[i], slots.getOrNull(i), isCadence = row == 1, showBattery, Modifier.weight(1f))
                    }
                }
            }
        }
        gps?.let { GpsBadge(it, stacked = true) }
        onReconnect?.let { ReconnectButton(it, accent, 36.dp) }
    }
}

@Composable
private fun SlotValue(label: String, status: SlotStatus?, isCadence: Boolean, showBattery: Boolean, modifier: Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = slotValueText(status, isCadence),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = slotSignalColor(status),
            maxLines = 1
        )
        SensorSlotBadge(label, status, labelColor = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, showBattery = showBattery)
    }
}

@Composable
private fun RowBar(
    slots: List<SlotStatus>,
    gps: GpsIndicator?,
    onOpenDevices: () -> Unit,
    modifier: Modifier,
    onReconnect: (() -> Unit)?,
    showBattery: Boolean,
    containerColor: Color,
    accent: Color
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(containerColor, shape)
            .opensDevices(onOpenDevices)
            .padding(horizontal = 8.dp, vertical = if (showBattery) 10.dp else 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        gps?.let { GpsBadge(it, stacked = false) }
        SENSOR_SLOT_LABELS.forEachIndexed { i, label ->
            val status = slots.getOrNull(i)
            SensorSlotBadge(
                label = label,
                status = status,
                labelColor = slotSignalColor(status),
                fontSize = 11.sp,
                stacked = true,
                showBattery = showBattery
            )
        }
        onReconnect?.let { ReconnectButton(it, accent, 32.dp) }
    }
}

@Composable
private fun CompactBar(slots: List<SlotStatus>, gps: GpsIndicator?, onOpenDevices: () -> Unit, modifier: Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .opensDevices(onOpenDevices),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        gps?.let { GpsDot(it) }
        SENSOR_SLOT_LABELS.forEachIndexed { i, label ->
            val status = slots.getOrNull(i)
            SensorSlotBadge(
                label = label,
                status = status,
                labelColor = slotSignalColor(status),
                fontSize = 10.sp,
                showBattery = false
            )
        }
    }
}

private fun gpsColor(gps: GpsIndicator): Color = when (gps) {
    GpsIndicator.FIX -> TabataColors.WorkGreen
    GpsIndicator.SEARCHING -> Color.Yellow
    GpsIndicator.OFF -> Color.Gray
}

/** Icono de GPS; buscando, con un halo que parpadea */
@Composable
private fun GpsIcon(gps: GpsIndicator, size: Dp) {
    Box(contentAlignment = Alignment.Center) {
        Icon(Icons.Default.GpsFixed, contentDescription = "GPS", tint = gpsColor(gps), modifier = Modifier.size(size))
        if (gps == GpsIndicator.SEARCHING) {
            val alpha by rememberInfiniteTransition(label = "gps").animateFloat(
                initialValue = 0.3f, targetValue = 1f,
                animationSpec = infiniteRepeatable(animation = tween(500), repeatMode = RepeatMode.Reverse),
                label = "gpsAlpha"
            )
            Box(Modifier.size(size).background(Color.Yellow.copy(alpha = alpha * 0.3f), CircleShape))
        }
    }
}

/** GPS con su texto: al lado (fila) o debajo (tarjeta) */
@Composable
private fun GpsBadge(gps: GpsIndicator, stacked: Boolean) {
    val label: @Composable () -> Unit = { Text("GPS", fontSize = 10.sp, color = gpsColor(gps)) }
    if (stacked) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            GpsIcon(gps, 18.dp)
            label()
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            GpsIcon(gps, 20.dp)
            label()
        }
    }
}

/** GPS en la barra compacta: icono pequeño dentro de un círculo */
@Composable
private fun GpsDot(gps: GpsIndicator) {
    val color = gpsColor(gps)
    Box(
        modifier = Modifier
            .size(24.dp)
            .background(color.copy(alpha = if (gps == GpsIndicator.OFF) 0.2f else 0.3f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.GpsFixed, contentDescription = "GPS", tint = color, modifier = Modifier.size(14.dp))
    }
}
