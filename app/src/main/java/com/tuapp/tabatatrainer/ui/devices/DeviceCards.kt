package com.tuapp.tabatatrainer.ui.devices

import com.tuapp.tabatatrainer.sensor.PhysicalDevice
import com.tuapp.tabatatrainer.sensor.SensorKind
import com.tuapp.tabatatrainer.sensor.VisibleSignal

// Lógica pura de la hoja "Dispositivos" (spec 007, T-06): tarjetas por aparato y modo guiado. Sin Android, testeable.

/**
 * Un aparato en la hoja: su ficha guardada (si la tiene) y las señales vistas que hoy van juntas.
 * [group] es la clave estable de la tarjeta (grupo del asignador o clave del aparato guardado).
 */
data class DeviceCard(
    val group: String,
    val kind: SensorKind,
    val device: PhysicalDevice?,        // ficha guardada (null = no guardado)
    val signals: List<VisibleSignal>,   // señales vistas ahora, ANT+ primero (vacía = apagado)
    val slot: Int?                      // hueco que ocupa ahora (null = ninguno)
) {
    val title: String get() = device?.alias ?: signals.firstOrNull { !it.isAnt && it.name.isNotBlank() }?.name
        ?: signals.firstOrNull()?.let { signalLabel(it) } ?: group

    val isPaired: Boolean get() = device?.isPaired == true
    val antId: String? get() = device?.antId ?: signals.firstOrNull { it.isAnt }?.id
    val bleId: String? get() = device?.bleId ?: signals.firstOrNull { !it.isAnt }?.id

    /** Señal con la que se guarda la ficha si aún no existe (renombrar, fijar hueco) */
    val primaryId: String get() = device?.key ?: antId ?: bleId ?: group

    /**
     * Parejas ANT+ ↔ BLE agrupadas solo en esta sesión dentro de la tarjeta ("No son el mismo aparato").
     * Incluye el relevo de un aparato guardado a una señal desconocida (su ANT+ guardado ↔ ese BLE, o al revés).
     */
    val sessionLinks: List<Pair<String, String>>
        get() {
            val loose = signals.filter { it.sessionOnly }.map { it.id }.toSet()
            if (loose.isEmpty()) return emptyList()
            val ants = (signals.filter { it.isAnt }.map { it.id } + listOfNotNull(device?.antId)).distinct()
            val bles = (signals.filter { !it.isAnt }.map { it.id } + listOfNotNull(device?.bleId)).distinct()
            return ants.flatMap { a -> bles.map { b -> a to b } }.filter { (a, b) -> a in loose || b in loose }
        }

    val sessionOnly: Boolean get() = sessionLinks.isNotEmpty()

    /** "Guardar como un aparato": ANT+ y BLE agrupados solo en la sesión, sin ficha emparejada */
    val canSaveAsOne: Boolean get() = !isPaired && sessionLinks.size == 1

    /** Id de su única señal (para "Unir con…"); null si ya tiene las dos o está agrupado */
    val loneId: String?
        get() = when {
            isPaired || sessionOnly -> null
            antId != null && bleId == null -> antId
            bleId != null && antId == null -> bleId
            else -> null
        }
}

/** Texto de la señal: "ANT+ 12345" o "BLE XOSS X2" */
fun signalLabel(s: VisibleSignal): String =
    if (s.isAnt) "ANT+ ${s.id.removePrefix("ANT:")}" else "BLE ${s.name.ifBlank { s.id.removePrefix("BLE:") }}"

/** Valor de la señal: "72 bpm" solo con dato reciente; en cadencia 0 rpm reciente es "0 rpm" */
fun signalValue(s: VisibleSignal): String = when {
    s.fresh -> "${s.value} ${if (s.kind == SensorKind.HR) "bpm" else "rpm"}"
    s.connected -> "sin dato"
    else -> "sin señal"
}

/** Línea de una señal en la tarjeta: "ANT+ 12345 · 72 bpm" */
fun signalLine(s: VisibleSignal): String = "${signalLabel(s)} · ${signalValue(s)}"

/**
 * Tarjetas de un tipo de sensor: una por grupo de señales vistas (con su ficha si la hay) y una por cada aparato
 * guardado que no se ve ahora. Orden: por hueco (HR1, HR2), luego el resto de vistos, luego los apagados.
 */
fun buildDeviceCards(kind: SensorKind, saved: List<PhysicalDevice>, signals: List<VisibleSignal>): List<DeviceCard> {
    val mine = saved.filter { it.kind == kind }
    val used = HashSet<String>()
    val live = signals.filter { it.kind == kind }.groupBy { it.group }.map { (group, sigs) ->
        val devices = mine.filter { d -> d.key == group || sigs.any { it.id == d.antId || it.id == d.bleId } }
        devices.forEach { used += it.key }
        val device = devices.firstOrNull { it.isPaired } ?: devices.firstOrNull { it.key == group } ?: devices.firstOrNull()
        DeviceCard(group, kind, device, sigs.sortedByDescending { it.isAnt }, sigs.firstNotNullOfOrNull { it.slot })
    }
    val offline = mine.filter { it.key !in used }.map { DeviceCard(it.key, kind, it, emptyList(), null) }
    return live.sortedWith(compareBy<DeviceCard> { it.slot ?: Int.MAX_VALUE }.thenBy { it.title.lowercase() }) +
        offline.sortedBy { it.title.lowercase() }
}

/** Tarjetas a las que se puede unir [card] ("Unir con…"): vistas, de una sola señal y del otro protocolo */
fun linkCandidates(card: DeviceCard, cards: List<DeviceCard>): List<DeviceCard> {
    val own = card.loneId ?: return emptyList()
    val ownAnt = own.startsWith("ANT:")
    return cards.filter { c ->
        c.group != card.group && c.signals.isNotEmpty() && c.loneId?.let { it.startsWith("ANT:") != ownAnt } == true
    }
}

/** Paso del modo guiado "enciende solo un aparato" */
sealed interface GuidedStep {
    data object WaitingAnt : GuidedStep
    data object WaitingBle : GuidedStep
    data class TooMany(val ants: Int, val bles: Int) : GuidedStep
    data class AlreadyLinked(val alias: String) : GuidedStep
    data class Ready(val ant: VisibleSignal, val ble: VisibleSignal, val suggestedName: String) : GuidedStep
}

/**
 * Modo guiado: con un solo aparato encendido se ve exactamente una señal ANT+ y una BLE conectadas, y se pueden
 * unir a mano. Con más de una de cualquier protocolo, hay que apagar los demás.
 */
fun guidedPairing(kind: SensorKind, saved: List<PhysicalDevice>, signals: List<VisibleSignal>): GuidedStep {
    val live = signals.filter { it.kind == kind && it.connected }
    val ants = live.filter { it.isAnt }
    val bles = live.filter { !it.isAnt }
    return when {
        ants.size > 1 || bles.size > 1 -> GuidedStep.TooMany(ants.size, bles.size)
        ants.isEmpty() -> GuidedStep.WaitingAnt
        bles.isEmpty() -> GuidedStep.WaitingBle
        else -> {
            val a = ants.single()
            val b = bles.single()
            val mine = saved.filter { it.kind == kind }
            mine.firstOrNull { it.isPaired && it.antId == a.id && it.bleId == b.id }?.let { GuidedStep.AlreadyLinked(it.alias) }
                ?: GuidedStep.Ready(
                    a, b,
                    suggestedName = mine.firstOrNull { it.antId == a.id || it.bleId == b.id }?.alias
                        ?: b.name.ifBlank { a.name.ifBlank { signalLabel(a) } }
                )
        }
    }
}
