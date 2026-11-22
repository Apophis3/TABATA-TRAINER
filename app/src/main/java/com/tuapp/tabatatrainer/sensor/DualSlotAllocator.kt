package com.tuapp.tabatatrainer.sensor

/** Una señal (ANT+ o BLE) tal como se ve ahora, sea pulso o cadencia */
data class SignalState(
    val id: String,              // "ANT:12345" o "BLE:AA:BB:..."
    val name: String,
    val isAnt: Boolean,
    val connected: Boolean,
    val fresh: Boolean,          // conectada y con dato reciente
    val value: Int = 0,          // bpm o rpm
    val battery: SensorBattery? = null
) {
    val protocol: String get() = if (isAnt) "ANT+" else "BLE"
}

/** Estado de un hueco (HR1/HR2, C1/C2) para la UI */
data class SlotStatus(
    val slot: Int,
    val deviceKey: String? = null,   // aparato asignado al hueco (null = libre)
    val alias: String? = null,       // nombre guardado del aparato, si lo tiene
    val sourceId: String? = null,    // señal que alimenta el hueco ahora
    val sourceName: String? = null,
    val protocol: String? = null,    // "ANT+" / "BLE"
    val connected: Boolean = false,
    val value: Int = 0,
    val battery: SensorBattery? = null,
    val fresh: Boolean = false       // la fuente tiene dato reciente (no basta "conectado"; spec 007 R7.1)
)

/**
 * Una señal vista, con el aparato y el hueco al que pertenece ahora, para la hoja "Dispositivos" (spec 007, T-06).
 * [group] = clave del aparato (registro, vínculo de sesión o la propia señal); [sessionOnly] = agrupada con otra
 * señal solo en esta sesión (sin vínculo guardado).
 */
data class VisibleSignal(
    val kind: SensorKind,
    val id: String,
    val name: String,
    val isAnt: Boolean,
    val value: Int,
    val connected: Boolean,
    val fresh: Boolean,
    val battery: SensorBattery?,
    val group: String,
    val slot: Int?,
    val sessionOnly: Boolean
) {
    val protocol: String get() = if (isAnt) "ANT+" else "BLE"
}

/** Aparato nuevo que espera la aprobación del usuario para usarse (spec 007, R11) */
data class NewDeviceAsk(
    val kind: SensorKind,
    val id: String,
    val name: String,
    val isAnt: Boolean,
    val value: Int
) {
    val protocol: String get() = if (isAnt) "ANT+" else "BLE"
}

/**
 * Reparto de huecos para aparatos de doble señal (spec 007, T-02). Lógica pura, sin Android, común a pulso y
 * cadencia. Cada segundo recibe las señales vistas y decide qué aparato ocupa cada hueco y qué señal lo alimenta.
 *
 * Reglas, en orden:
 * 1. Un aparato que acaba en dos huecos (al agruparse) se queda en su hueco preferido o, si no tiene, en el más bajo.
 * 2. Hueco preferido (lo asigna el usuario): un aparato vivo con hueco preferido lo ocupa; quien estuviera allí
 *    pasa al hueco que deja libre. La reserva es blanda: un aparato ajeno solo usa un hueco reservado si no
 *    queda otro.
 * 3. Aparatos nuevos: primer hueco libre, ANT+ primero. Un desconocido que podría ser la otra señal de un
 *    aparato con hueco espera [quarantineMs] (cuarentena) por si se agrupa.
 * 4. ANT+ desplaza a BLE: con tantos aparatos ANT+ como huecos (contando los que tienen hueco aunque estén
 *    caídos un momento), ningún hueco se queda en un aparato sin ANT+; el ANT+ lo ocupa en el mismo hueco.
 * 5. Fuente del hueco: ANT+ con dato > BLE con dato > ANT+ conectado > cualquiera conectado.
 * 6. Relevo sin solapamiento: si el aparato de un hueco cae por un protocolo y aparece un desconocido por el
 *    otro con valor parecido, hereda el hueco (vínculo solo de sesión).
 * 7. Sin valor congelado (R2.1): una fuente conectada sin dato reciente se mantiene [staleHoldMs] y después el hueco
 *    se queda sin fuente (sin valor) hasta el siguiente dato, sin perder su aparato.
 * 8. Señales ignoradas (R10): no cuentan para nada; ignorarlas suelta su hueco. Duran hasta [unignore], también
 *    tras [clear].
 * 9. Aparato nuevo (R11, con [askNewMs]): un aparato sin ficha en el registro ni aprobado no recibe hueco; tras
 *    [askNewMs] seguidos con dato se pregunta al usuario ([asking]); [approve] lo deja entrar.
 */
class DualSlotAllocator(
    private val kind: SensorKind,
    private val directory: DeviceDirectory,
    private val label: String,                    // "HR" o "C" (solo para los mensajes)
    private val handoffMaxDiff: Int,
    private val slots: Int = 2,
    private val quarantineMs: Long = 30_000L,
    private val orphanAntGraceMs: Long = 60_000L, // un hueco ANT+ caído no pasa a un aparato solo-BLE antes de esto
    private val staleHoldMs: Long = 3_000L,       // fuente conectada sin dato: se mantiene este tiempo (además de los 5 s de frescura)
    private val askNewMs: Long? = null,           // R11: un aparato nuevo pregunta tras este tiempo con dato (null = entra sin preguntar)
    private val log: (String) -> Unit = {}
) {
    // Vínculos solo de esta sesión (agrupado por valores, relevo): id -> grupo
    private val sessionTwins = HashMap<String, String>()
    private val quarantineSince = HashMap<String, Long>()
    private val orphanSince = HashMap<Int, Long>()

    // Señales ignoradas por el usuario (R10): id -> etiqueta para la hoja
    private val ignored = LinkedHashMap<String, String>()

    // R11: señales aprobadas en esta sesión, aparatos nuevos con dato desde cuándo y los que ya hay que preguntar
    private val approved = HashSet<String>()
    private val askSince = HashMap<String, Long>()
    private var toAsk: List<NewDeviceAsk> = emptyList()

    // Hueco -> grupo (clave del registro si está emparejado; si no, el id de una de sus señales)
    private val slotGroup = arrayOfNulls<String>(slots)
    private val slotSource = arrayOfNulls<String>(slots)
    private val slotLastValue = IntArray(slots)
    private val staleSince = LongArray(slots)     // desde cuándo la fuente del hueco no tiene dato (0 = tiene)

    // --- Consultas -----------------------------------------------------------------------------

    /** Aparato al que pertenece una señal: registro (si está emparejada) > vínculo de sesión > ella misma */
    @Synchronized fun groupOf(id: String): String =
        directory.pairKeyOf(kind, id) ?: sessionTwins[id]?.let { directory.pairKeyOf(kind, it) ?: it } ?: id

    @Synchronized fun isKnown(id: String) = directory.pairKeyOf(kind, id) != null || sessionTwins.containsKey(id)

    @Synchronized fun hasSlot(group: String) = group in slotGroup

    /** Señal que alimenta el hueco ahora (null = ninguna) */
    fun sourceOf(slot: Int): String? = slotSource[slot]

    @Synchronized fun groupIn(slot: Int): String? = slotGroup[slot]

    // --- Vínculos de sesión ----------------------------------------------------------------------

    @Synchronized fun joinSession(id: String, group: String) { sessionTwins[id] = group }

    @Synchronized fun leaveSession(vararg ids: String) { ids.forEach { sessionTwins.remove(it) } }

    // --- Señales ignoradas (R10) -----------------------------------------------------------------

    @Synchronized fun isIgnored(id: String) = id in ignored

    /** Señales ignoradas (id -> etiqueta), en el orden en que se ignoraron */
    @Synchronized fun ignoredSignals(): Map<String, String> = LinkedHashMap(ignored)

    /**
     * Ignora estas señales (id -> etiqueta): sueltan en el acto el hueco de su aparato y sus vínculos de sesión, y dejan
     * de contar en [tick] y [describe]. No las borra [clear].
     */
    @Synchronized
    fun ignore(ids: Map<String, String>) {
        if (ids.isEmpty()) return
        val groups = ids.keys.map { groupOf(it) }.toSet() + ids.keys
        for (i in 0 until slots) {
            val g = slotGroup[i] ?: continue
            if (g !in groups) continue
            slotGroup[i] = null; slotSource[i] = null; slotLastValue[i] = 0; staleSince[i] = 0L
            orphanSince.remove(i)
            log("$label${i + 1}: libre (señal ignorada)")
        }
        sessionTwins.entries.removeAll { (id, g) -> id in ids || g in ids }
        quarantineSince.keys.removeAll(groups)
        askSince.keys.removeAll(groups)
        toAsk = toAsk.filter { it.id !in ids && groupOf(it.id) !in groups }
        ignored.putAll(ids)
        log("Ignoradas: ${ids.values.joinToString()}")
    }

    /** "Volver a usar": la señal cuenta otra vez desde el siguiente tick */
    @Synchronized
    fun unignore(id: String) {
        ignored.remove(id)?.let { log("Vuelve a usarse: $it") }
    }

    // --- Aparatos nuevos (R11) --------------------------------------------------------------------

    /** Aparatos nuevos por los que hay que preguntar (calculado en cada [tick]) */
    @Synchronized fun asking(): List<NewDeviceAsk> = toAsk

    /** "Sí, usar": la señal (y su aparato) entra con las reglas de siempre desde el siguiente tick */
    @Synchronized
    fun approve(id: String) {
        if (!approved.add(id)) return
        askSince.remove(groupOf(id))
        toAsk = toAsk.filter { it.id != id }
        log("$id aprobado por el usuario")
    }

    /**
     * Señales agrupadas solo en la sesión → señales del otro protocolo de su mismo grupo, entre [ids] (las conectadas).
     * Las emparejadas en el registro no cuentan. Para que el emparejado no las use de rival (R3.1).
     */
    @Synchronized
    fun sessionPartners(ids: Collection<String>): Map<String, Set<String>> {
        val loose = ids.filter { directory.pairKeyOf(kind, it) == null }
        val result = HashMap<String, Set<String>>()
        for ((_, members) in loose.groupBy { groupOf(it) }) {
            for (id in members) {
                val isAnt = id.startsWith(ANT_PREFIX)
                val others = members.filter { it.startsWith(ANT_PREFIX) != isAnt }.toSet()
                if (others.isNotEmpty()) result[id] = others
            }
        }
        return result
    }

    /** Señales vistas con su aparato y su hueco (para la hoja "Dispositivos") */
    @Synchronized
    fun describe(signals: List<SignalState>): List<VisibleSignal> = signals.filter { it.id !in ignored }.map { s ->
        val g = groupOf(s.id)
        VisibleSignal(
            kind = kind, id = s.id, name = s.name, isAnt = s.isAnt, value = s.value, connected = s.connected,
            fresh = s.fresh, battery = s.battery, group = g,
            slot = slotGroup.indexOf(g).takeIf { it >= 0 },
            sessionOnly = directory.pairKeyOf(kind, s.id) == null &&
                (sessionTwins.containsKey(s.id) || sessionTwins.containsValue(s.id))
        )
    }

    // --- Ciclo de vida -----------------------------------------------------------------------------

    /** Radios paradas: se olvida la fuente y las esperas, pero se conserva qué aparato va en cada hueco */
    @Synchronized fun pause() {
        quarantineSince.clear(); orphanSince.clear(); askSince.clear(); toAsk = emptyList()
        for (i in 0 until slots) { slotSource[i] = null; staleSince[i] = 0L }
    }

    /** Olvida todo lo de la sesión (los aparatos guardados en el registro no se tocan; ignoradas y aprobadas siguen) */
    @Synchronized fun clear() {
        pause()
        sessionTwins.clear()
        for (i in 0 until slots) { slotGroup[i] = null; slotLastValue[i] = 0 }
    }

    // --- Reparto -------------------------------------------------------------------------------------

    @Synchronized
    fun tick(all: List<SignalState>, now: Long): List<SlotStatus> {
        val signals = if (ignored.isEmpty()) all else all.filter { it.id !in ignored }
        val byId = signals.associateBy { it.id }
        detectHandoffs(signals, now)
        val live = signals.filter { it.connected }.map { groupOf(it.id) }.toSet()

        mergeSlots()
        applyPreferredSlots(signals, live)
        assignPending(signals, byId, live, now)
        return (0 until slots).map { pickSource(it, signals, now) }
    }

    /** Regla 1: un hueco cuyo aparato se ha agrupado pasa a la clave del aparato; sin duplicados */
    private fun mergeSlots() {
        for (i in 0 until slots) slotGroup[i]?.let { slotGroup[i] = groupOf(it) }
        for (i in 0 until slots) for (j in i + 1 until slots) {
            val g = slotGroup[i] ?: continue
            if (g != slotGroup[j]) continue
            if (preferredOf(g, emptyList()) == j) slotGroup[i] = null else slotGroup[j] = null
        }
    }

    /** Regla 2: hueco preferido */
    private fun applyPreferredSlots(signals: List<SignalState>, live: Set<String>) {
        for (g in live) {
            val p = preferredOf(g, signals) ?: continue
            val occupant = slotGroup[p]
            if (occupant == g) continue
            if (occupant != null && preferredOf(occupant, signals) == p) continue   // no debería pasar: un hueco por aparato
            // Quien ocupaba el hueco pasa al que deja libre el aparato o, si no tenía, al primer libre
            val from = slotGroup.indexOf(g)
            slotGroup[p] = g
            val to = if (from >= 0) from else (0 until slots).firstOrNull { slotGroup[it] == null }
            if (to != null) slotGroup[to] = occupant
            quarantineSince.remove(g)
            log("$label${p + 1}: ${nameOf(g, signals)} ocupa su hueco guardado" +
                (occupant?.let { " (${nameOf(it, signals)} pasa a ${to?.let { t -> "$label${t + 1}" } ?: "espera"})" } ?: ""))
        }
    }

    /** Reglas 3 y 4: aparatos sin hueco */
    private fun assignPending(signals: List<SignalState>, byId: Map<String, SignalState>, live: Set<String>, now: Long) {
        for (i in 0 until slots) {
            val g = slotGroup[i]
            if (g != null && g !in live) orphanSince.getOrPut(i) { now } else orphanSince.remove(i)
        }
        val pending = live.filter { it !in slotGroup }.sortedBy { if (hasAnt(it, signals)) 0 else 1 }
        quarantineSince.keys.retainAll(pending.toSet())
        val waiting = HashSet<String>()
        for (g in pending) {
            if (inQuarantine(g, signals, byId, live, now)) continue
            if (askNewMs != null && !isTrusted(g, signals)) {
                // R11: sin hueco hasta que el usuario lo apruebe; cuenta el tiempo seguido con dato
                if (signals.any { it.fresh && groupOf(it.id) == g }) {
                    waiting += g
                    askSince.getOrPut(g) {
                        log("${nameOf(g, signals)}: nuevo, sin aprobar; se preguntará si sigue ${askNewMs / 1000} s")
                        now
                    }
                }
                continue
            }
            // La espera ya cumplida se conserva mientras siga sin hueco: borrarla aquí la reiniciaba cada 30 s
            // y un aparato sin hueco libre nunca llegaba a usar el que se libera después
            val slot = slotFor(g, signals, live, now) ?: continue
            quarantineSince.remove(g)
            val displaced = slotGroup[slot]
            slotGroup[slot] = g
            orphanSince.remove(slot)
            log("$label${slot + 1}: asignado a ${nameOf(g, signals)}" +
                (displaced?.let { " (sustituye a ${nameOf(it, signals)}${if (it in live) ", ANT+ preferente" else ""})" } ?: ""))
        }
        askSince.keys.retainAll(waiting)
        toAsk = if (askNewMs == null) emptyList() else askSince.filter { (_, since) -> now - since >= askNewMs }.keys
            .mapNotNull { g ->
                signals.filter { groupOf(it.id) == g && it.fresh }.minByOrNull { if (it.isAnt) 0 else 1 }
                    ?.let { NewDeviceAsk(kind, it.id, it.name, it.isAnt, it.value) }
            }
            .sortedBy { if (it.isAnt) 0 else 1 }
    }

    /** R11: aparato guardado en el registro (con alguna de sus señales) o aprobado en esta sesión */
    private fun isTrusted(g: String, signals: List<SignalState>): Boolean =
        idsOf(g, signals).any { it in approved || directory.deviceOf(kind, it) != null }

    private fun slotFor(g: String, signals: List<SignalState>, live: Set<String>, now: Long): Int? {
        val own = preferredOf(g, signals)
        val reserved = directory.all(kind).mapNotNull { it.preferredSlot }.toSet()
        fun forOther(i: Int) = i in reserved && i != own
        val gAnt = hasAnt(g, signals)

        val empty = (0 until slots).filter { slotGroup[it] == null }
        // Hueco cuyo aparato no está: se le guarda en la sesión y solo se reutiliza si no hay libre. Un hueco
        // de aparato ANT+ no pasa a uno solo-BLE hasta pasado el margen (búsquedas ANT+, reconexiones).
        val orphans = (0 until slots).filter { i ->
            val o = slotGroup[i]
            o != null && o !in live &&
                (gAnt || !hasAnt(o, signals) || now - (orphanSince[i] ?: now) >= orphanAntGraceMs)
        }
        empty.firstOrNull { !forOther(it) }?.let { return it }
        orphans.firstOrNull { !forOther(it) }?.let { return it }
        empty.firstOrNull()?.let { return it }
        orphans.firstOrNull()?.let { return it }

        // ANT+ desplaza a BLE: con tantos aparatos ANT+ como huecos, el BLE sobra (es la otra cara de uno de ellos)
        if (!gAnt) return null
        // Un aparato sin aprobar (R11) no cuenta: no puede echar a un BLE propio
        val antGroups = (live.filter { askNewMs == null || it in slotGroup || isTrusted(it, signals) } + slotGroup.filterNotNull())
            .toSet().count { hasAnt(it, signals) }
        if (antGroups < slots) return null
        return (0 until slots).firstOrNull { i ->
            val o = slotGroup[i]
            o != null && !hasAnt(o, signals) && preferredOf(o, signals) != i
        }
    }

    /** Reglas 5 y 7: fuente del hueco */
    private fun pickSource(i: Int, signals: List<SignalState>, now: Long): SlotStatus {
        val g = slotGroup[i]
        val members = if (g == null) emptyList() else signals.filter { groupOf(it.id) == g }
        val fresh = members.filter { it.fresh }.minByOrNull { if (it.isAnt) 0 else 1 }
        val stale = members.firstOrNull { it.connected && it.isAnt } ?: members.firstOrNull { it.connected }
        val best = when {
            fresh != null -> { staleSince[i] = 0L; fresh }
            stale == null -> { staleSince[i] = 0L; null }
            else -> {
                if (staleSince[i] == 0L) staleSince[i] = now
                if (now - staleSince[i] < staleHoldMs) stale else {
                    if (slotSource[i] != null) log("$label${i + 1}: ${stale.name} (${stale.protocol}) sin dato reciente, el hueco deja de dar valor")
                    null
                }
            }
        }
        if (best?.id != slotSource[i]) log("$label${i + 1}: ${best?.let { "${it.name} (${it.protocol})" } ?: "—"}")
        slotSource[i] = best?.id
        if (best != null && best.value > 0) slotLastValue[i] = best.value
        return SlotStatus(
            slot = i,
            deviceKey = g,
            alias = g?.let { aliasOf(it, members) },
            sourceId = best?.id,
            sourceName = best?.name,
            protocol = best?.protocol,
            connected = best != null,
            value = best?.value ?: 0,
            // ANT+ HR no da batería (SDK 3.9): se usa la de la otra señal del mismo aparato (spec 005, T-03),
            // solo si esa señal sigue conectada. Sin fuente no hay batería (Home la usa como "conectado").
            battery = best?.let { b -> b.battery ?: members.firstNotNullOfOrNull { m -> m.battery.takeIf { m.connected } } },
            fresh = best?.fresh == true
        )
    }

    /**
     * Un desconocido no recibe hueco propio durante [quarantineMs] si podría ser la otra señal de un aparato que
     * ya tiene hueco: otra señal de distinto protocolo sin emparejar con hueco, o un hueco caído que podría relevar.
     */
    private fun inQuarantine(
        g: String, signals: List<SignalState>, byId: Map<String, SignalState>, live: Set<String>, now: Long
    ): Boolean {
        val d = byId[g] ?: return false
        if (isKnown(d.id)) return false
        val possibleTwin = signals.any { o ->
            o.isAnt != d.isAnt && o.connected && directory.partnerOf(kind, o.id) == null && groupOf(o.id) in slotGroup &&
                !blockedPair(o.id, d.id)
        }
        val orphanSlot = (0 until slots).any { i -> slotGroup[i]?.let { it !in live && canHandoff(it, d, signals) } == true }
        if (!possibleTwin && !orphanSlot) return false
        val since = quarantineSince.getOrPut(g) {
            log("${d.name} (${d.protocol}): en espera, ¿es el mismo aparato que uno ya asignado?")
            now
        }
        return now - since < quarantineMs
    }

    /**
     * Solo puede relevar si el aparato del hueco no tiene ya ese protocolo (si lo tuviera, sería otro aparato) ni
     * el usuario ha separado esa pareja (bloqueada, R3).
     */
    private fun canHandoff(slotGroupId: String, d: SignalState, signals: List<SignalState>): Boolean {
        val protocols = signals.filter { groupOf(it.id) == slotGroupId }.map { it.isAnt }.toSet()
        return protocols.isNotEmpty() && d.isAnt !in protocols && directory.partnerOf(kind, d.id) == null &&
            idsOf(slotGroupId, signals).none { blockedPair(it, d.id) }
    }

    /** La pareja ANT+ ↔ BLE (en cualquier orden) está bloqueada porque el usuario la separó */
    private fun blockedPair(a: String, b: String): Boolean = when {
        a.startsWith(ANT_PREFIX) && !b.startsWith(ANT_PREFIX) -> directory.isBlocked(kind, a, b)
        b.startsWith(ANT_PREFIX) && !a.startsWith(ANT_PREFIX) -> directory.isBlocked(kind, b, a)
        else -> false
    }

    /** Regla 6: relevo sin solapamiento */
    private fun detectHandoffs(signals: List<SignalState>, now: Long) {
        val live = signals.filter { it.connected }.map { groupOf(it.id) }.toSet()
        val orphans = (0 until slots).filter { i -> slotGroup[i].let { it != null && it !in live } }
        if (orphans.isEmpty()) return
        for (d in signals) {
            if (!d.fresh || isKnown(d.id) || d.id in slotGroup) continue
            val candidates = orphans.filter { i ->
                canHandoff(slotGroup[i]!!, d, signals) && slotLastValue[i] > 0 &&
                    kotlin.math.abs(d.value - slotLastValue[i]) <= handoffMaxDiff
            }
            if (candidates.size == 1) {
                val i = candidates.single()
                sessionTwins[d.id] = slotGroup[i]!!
                log("${d.name} (${d.protocol}) releva al aparato de $label${i + 1}")
            }
        }
    }

    // --- Datos de un grupo -----------------------------------------------------------------------------

    /** Ids conocidos de un grupo: su clave, sus vínculos de sesión y las señales vistas que pertenecen a él */
    private fun idsOf(g: String, signals: List<SignalState>): Set<String> = buildSet {
        add(g)
        sessionTwins.forEach { (id, grp) -> if (grp == g) add(id) }
        signals.forEach { if (groupOf(it.id) == g) add(it.id) }
        directory.deviceOf(kind, g)?.let { d -> d.antId?.let { add(it) }; d.bleId?.let { add(it) } }
    }

    /** El aparato tiene (o ha tenido en la sesión) señal ANT+ */
    private fun hasAnt(g: String, signals: List<SignalState>) =
        idsOf(g, signals).any { it.startsWith(ANT_PREFIX) } || signals.any { it.isAnt && groupOf(it.id) == g }

    private fun preferredOf(g: String, signals: List<SignalState>): Int? =
        idsOf(g, signals).firstNotNullOfOrNull { directory.deviceOf(kind, it)?.preferredSlot }?.takeIf { it in 0 until slots }

    private fun aliasOf(g: String, members: List<SignalState>): String? =
        (listOf(g) + members.map { it.id }).firstNotNullOfOrNull { directory.deviceOf(kind, it)?.alias }

    private fun nameOf(g: String, signals: List<SignalState>): String =
        directory.deviceOf(kind, g)?.alias ?: signals.firstOrNull { it.id == g }?.let { "${it.name} (${it.protocol})" } ?: g

    private companion object { const val ANT_PREFIX = "ANT:" }
}
