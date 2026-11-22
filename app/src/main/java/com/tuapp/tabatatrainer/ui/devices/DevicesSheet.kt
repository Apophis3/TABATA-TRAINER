package com.tuapp.tabatatrainer.ui.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.tuapp.tabatatrainer.sensor.SensorKind
import com.tuapp.tabatatrainer.ui.components.BatteryBadge
import com.tuapp.tabatatrainer.ui.components.protocolColor
import com.tuapp.tabatatrainer.ui.theme.TabataColors

private val SheetBackground = Color(0xFF1E1E1E)
private val CardBackground = Color(0xFF2D2D2D)
private val TextSecondary = Color(0xFFAAAAAA)
private val Accent = Color(0xFFFF6B35)
private val Danger = Color(0xFFE53935)

/** Nombres de los huecos de un tipo de sensor */
private fun slotNames(kind: SensorKind) = if (kind == SensorKind.HR) listOf("HR1", "HR2") else listOf("C1", "C2")

/**
 * Hoja "Dispositivos" (spec 007, T-06; R8, R8.1–R8.3): la misma en Home, Config, Tabata y Ruta. Se abre al tocar la
 * barra de sensores; atrás o deslizar hacia abajo la cierran. Abrirla o cerrarla no toca la sesión en marcha.
 * - [screen]: nombre de la pantalla, solo para el Logcat.
 * - [liveSession]: hay Tabata o Ruta en marcha (avisa de que cambiar el hueco cambia lo que se graba).
 * - [onReconnect]: el mismo callback que la flecha de esa pantalla, con la misma regla (null = sin botón).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesSheet(
    screen: String,
    liveSession: Boolean,
    onReconnect: (() -> Unit)?,
    onDismiss: () -> Unit,
    viewModel: DevicesViewModel = hiltViewModel()
) {
    DisposableEffect(screen) {
        viewModel.onSheetOpened(screen)
        onDispose { viewModel.onSheetClosed(screen) }
    }
    val state by viewModel.state.collectAsState()
    var kind by rememberSaveable { mutableStateOf(SensorKind.HR) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SheetBackground,
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Dispositivos", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (onReconnect != null) {
                    TextButton(onClick = onReconnect) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Reconectar", color = Accent)
                    }
                }
            }
            KindTabs(kind) { kind = it }
            val kindState = state.of(kind)
            GuidedPanel(kind, kindState.guided, viewModel)
            if (kindState.cards.isEmpty()) {
                Text(
                    if (kind == SensorKind.HR) "No se ve ningún pulsómetro. Póntelo o pulsa Reconectar."
                    else "No se ve ningún cadenciómetro. Mueve el pedal o pulsa Reconectar.",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }
            kindState.cards.forEach { card ->
                DeviceCardView(card, kindState.cards, liveSession, viewModel)
            }
            if (kindState.ignored.isNotEmpty()) IgnoredPanel(kind, kindState.ignored, viewModel)
        }
    }
}

@Composable
private fun KindTabs(selected: SensorKind, onSelect: (SensorKind) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CardBackground)
    ) {
        listOf(SensorKind.HR to "Pulsómetros", SensorKind.CADENCE to "Cadenciómetros").forEach { (k, label) ->
            val on = k == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) Accent.copy(alpha = 0.25f) else Color.Transparent)
                    .clickable { onSelect(k) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, color = if (on) Color.White else TextSecondary)
            }
        }
    }
}

/** Selector de hueco fijo: Auto | HR1 | HR2 (o C1 | C2) */
@Composable
private fun SlotSelector(kind: SensorKind, selected: Int?, onSelect: (Int?) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Hueco fijo", fontSize = 13.sp, color = TextSecondary)
        (listOf<Int?>(null) + slotNames(kind).indices).forEach { slot ->
            val on = slot == selected
            Text(
                text = slot?.let { slotNames(kind)[it] } ?: "Auto",
                fontSize = 13.sp,
                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                color = if (on) Color.White else TextSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (on) Accent.copy(alpha = 0.35f) else Color.Transparent)
                    .border(1.dp, if (on) Accent else Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    .clickable { onSelect(slot) }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviceCardView(card: DeviceCard, cards: List<DeviceCard>, liveSession: Boolean, vm: DevicesViewModel) {
    var renaming by remember { mutableStateOf(false) }
    var choosingLink by remember { mutableStateOf(false) }
    var confirmForget by remember { mutableStateOf(false) }
    var confirmIgnore by remember { mutableStateOf(false) }
    val candidates = linkCandidates(card, cards)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardBackground)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            card.slot?.let { slot ->
                Text(
                    slotNames(card.kind)[slot],
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(Accent.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                card.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { renaming = true }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Edit, contentDescription = "Renombrar", tint = TextSecondary, modifier = Modifier.size(18.dp))
            }
        }
        Text(savedText(card), fontSize = 12.sp, color = TextSecondary)

        if (card.signals.isEmpty()) {
            Text("Apagado o fuera de alcance", fontSize = 13.sp, color = TextSecondary)
        }
        card.signals.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .size(10.dp)
                        .background(protocolColor(s.protocol) ?: Color.Gray, RoundedCornerShape(2.dp))
                )
                Text(
                    signalLine(s),
                    fontSize = 14.sp,
                    color = if (s.fresh) Color.White else Color.White.copy(alpha = 0.45f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                BatteryBadge(s.battery, fontSize = 11.sp)
            }
        }
        if (card.sessionOnly) {
            Text("Agrupado solo en esta sesión", fontSize = 12.sp, color = TabataColors.Scanning)
        }

        SlotSelector(card.kind, card.device?.preferredSlot) { vm.setSlot(card, it) }
        if (liveSession) {
            Text(
                "Con la sesión en marcha, cambiar el hueco cambia desde ese momento qué aparato se graba en " +
                    slotNames(card.kind).joinToString("/") + ".",
                fontSize = 11.sp,
                color = TextSecondary
            )
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (card.loneId != null && candidates.isNotEmpty()) {
                TextButton(onClick = { choosingLink = true }) { Text("Unir con…", color = Accent) }
            }
            if (card.isPaired) {
                TextButton(onClick = { vm.split(card) }) { Text("Separar", color = Accent) }
            }
            if (card.sessionOnly) {
                TextButton(onClick = { vm.unlinkSession(card) }) { Text("No son el mismo aparato", color = Accent) }
            }
            if (card.canSaveAsOne) {
                TextButton(onClick = { vm.saveAsOne(card) }) { Text("Guardar como un aparato", color = Accent) }
            }
            if (card.device != null) {
                TextButton(onClick = { confirmForget = true }) { Text("Olvidar", color = Danger) }
            }
            if (card.signals.isNotEmpty()) {
                TextButton(onClick = { confirmIgnore = true }) { Text("No es mío", color = Danger) }
            }
        }
    }

    if (renaming) {
        NameDialog(
            title = "Renombrar",
            initial = card.title,
            onConfirm = { vm.rename(card, it); renaming = false },
            onDismiss = { renaming = false }
        )
    }
    if (choosingLink) {
        AlertDialog(
            onDismissRequest = { choosingLink = false },
            title = { Text("Unir «${card.title}» con…", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Solo si son el mismo aparato (una señal ANT+ y otra BLE).", color = TextSecondary, fontSize = 13.sp)
                    candidates.forEach { other ->
                        TextButton(onClick = { vm.link(card, other); choosingLink = false }) {
                            Text(
                                other.title + other.signals.firstOrNull()?.let { " · ${signalLine(it)}" }.orEmpty(),
                                color = Color.White
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosingLink = false }) { Text("Cancelar", color = TextSecondary) } },
            containerColor = SheetBackground
        )
    }
    if (confirmIgnore) {
        AlertDialog(
            onDismissRequest = { confirmIgnore = false },
            title = { Text("¿«${card.title}» no es tuyo?", color = Color.White) },
            text = {
                Text(
                    "Deja de usarse mientras la app siga abierta: suelta su hueco, no se graba y no se vuelve a " +
                        "conectar. Se deshace en «Ignorados» (Volver a usar) o al cerrar la app.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.ignore(card); confirmIgnore = false }) { Text("No es mío", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmIgnore = false }) { Text("Cancelar", color = TextSecondary) } },
            containerColor = SheetBackground
        )
    }
    if (confirmForget) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text("¿Olvidar «${card.title}»?", color = Color.White) },
            text = {
                Text(
                    "Se borran su nombre, su hueco fijo y sus vínculos. Si vuelve a verse y es seguro, la app puede " +
                        "volver a emparejarlo sola.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.forget(card); confirmForget = false }) { Text("Olvidar", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmForget = false }) { Text("Cancelar", color = TextSecondary) } },
            containerColor = SheetBackground
        )
    }
}

/** Lista "Ignorados" (R10): señales marcadas "No es mío" en esta sesión de la app */
@Composable
private fun IgnoredPanel(kind: SensorKind, ignored: List<IgnoredSignal>, vm: DevicesViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardBackground)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("Ignorados", fontWeight = FontWeight.Bold)
        Text("No se usan mientras la app siga abierta.", fontSize = 12.sp, color = TextSecondary)
        ignored.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    s.label,
                    fontSize = 14.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { vm.unignore(kind, s.id) }) { Text("Volver a usar", color = Accent) }
            }
        }
    }
}

private fun savedText(card: DeviceCard): String {
    val d = card.device ?: return "No guardado"
    return when {
        d.isPaired -> "Guardado · ANT+ y BLE" + if (d.manual) " · unido a mano" else ""
        d.antId != null -> "Guardado · solo ANT+"
        else -> "Guardado · solo BLE"
    }
}

@Composable
private fun NameDialog(title: String, initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = Color.White) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it.take(40) }, singleLine = true) },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text("Guardar", color = Accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = TextSecondary) } },
        containerColor = SheetBackground
    )
}

/** Modo guiado "enciende solo un aparato" (plegado al principio) */
@Composable
private fun GuidedPanel(kind: SensorKind, step: GuidedStep, vm: DevicesViewModel) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardBackground)
            .clickable(enabled = !open) { open = true }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Emparejar paso a paso", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (open) TextButton(onClick = { open = false }) { Text("Cerrar", color = TextSecondary) }
        }
        if (open) {
            Text(
                "Deja encendido solo el ${if (kind == SensorKind.HR) "pulsómetro" else "cadenciómetro"} que quieres " +
                    "unir (apaga o aleja los demás). Se unirán su señal ANT+ y su señal BLE.",
                fontSize = 13.sp,
                color = TextSecondary
            )
            when (step) {
                GuidedStep.WaitingAnt -> Text("Esperando ANT+…", color = TabataColors.Scanning)
                GuidedStep.WaitingBle -> Text("Esperando BLE…", color = TabataColors.Scanning)
                is GuidedStep.TooMany -> Text(
                    "Se ven ${step.ants} ANT+ y ${step.bles} BLE: apaga los demás",
                    color = TabataColors.Scanning
                )
                is GuidedStep.AlreadyLinked -> Text("Ya es «${step.alias}»", color = TabataColors.Connected)
                is GuidedStep.Ready -> GuidedReady(kind, step, vm)
            }
        }
    }
}

@Composable
private fun GuidedReady(kind: SensorKind, step: GuidedStep.Ready, vm: DevicesViewModel) {
    var name by remember(step.ant.id, step.ble.id) { mutableStateOf(step.suggestedName) }
    var slot by remember(step.ant.id, step.ble.id) { mutableStateOf<Int?>(null) }
    Text("${signalLine(step.ant)}\n${signalLine(step.ble)}", fontSize = 14.sp)
    OutlinedTextField(
        value = name,
        onValueChange = { name = it.take(40) },
        singleLine = true,
        label = { Text("Nombre") },
        modifier = Modifier.fillMaxWidth()
    )
    SlotSelector(kind, slot) { slot = it }
    Button(
        onClick = { vm.guidedLink(kind, step, name, slot) },
        enabled = name.isNotBlank(),
        colors = ButtonDefaults.buttonColors(containerColor = Accent)
    ) {
        Text("Unir", color = Color.White)
    }
}
