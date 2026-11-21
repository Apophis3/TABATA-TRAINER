package com.tuapp.tabatatrainer.sensor

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Tipo de aparato de doble señal (ANT+ y BLE) */
enum class SensorKind { HR, CADENCE }

/**
 * Un aparato físico con sus identidades por señal. Puede tener solo una (aparato conocido aún sin emparejar).
 * - Emparejado: clave = su id ANT+ (el orden "ANT+ primero" de los huecos depende de ese prefijo).
 * - Una sola señal: clave = ese id (así, para los huecos, se comporta igual que un aparato desconocido).
 */
data class PhysicalDevice(
    val key: String,
    val kind: SensorKind,
    val alias: String,
    val antId: String?,          // "ANT:12345"
    val bleId: String?,          // "BLE:AA:BB:..."
    val preferredSlot: Int? = null,
    val manual: Boolean = false  // emparejado por el usuario: el automático nunca lo rompe
) {
    val isPaired: Boolean get() = antId != null && bleId != null
}

/** Consultas que necesitan las reglas de emparejado */
interface PairingLookup {
    fun partnerOf(kind: SensorKind, id: String): String?
    fun isBlocked(kind: SensorKind, antId: String, bleId: String): Boolean
}

/** Consultas del asignador de huecos ([DualSlotAllocator]) */
interface DeviceDirectory : PairingLookup {
    fun deviceOf(kind: SensorKind, id: String): PhysicalDevice?
    fun pairKeyOf(kind: SensorKind, id: String): String?
    fun all(kind: SensorKind): List<PhysicalDevice>
}

/**
 * Libreta de aparatos físicos (pulsómetros y cadenciómetros). Lógica pura, testeable sin Android.
 * - Un id pertenece como máximo a un aparato por tipo; un hueco, como máximo a un aparato por tipo.
 * - El emparejado manual manda: el automático no rompe aparatos manuales ni une parejas bloqueadas.
 * - Separar deja dos aparatos de una señal y bloquea esa pareja para el automático.
 */
class DeviceBook(
    initial: Collection<PhysicalDevice> = emptyList(),
    blocked: Collection<BlockedPair> = emptyList()
) : DeviceDirectory {

    data class BlockedPair(val kind: SensorKind, val antId: String, val bleId: String)

    private val devices = ArrayList<PhysicalDevice>(initial)
    private val blockedPairs = LinkedHashSet<BlockedPair>(blocked)

    @Synchronized fun all(): List<PhysicalDevice> = devices.toList()
    @Synchronized override fun all(kind: SensorKind): List<PhysicalDevice> = devices.filter { it.kind == kind }
    @Synchronized fun blocked(): List<BlockedPair> = blockedPairs.toList()

    /** Aparato (emparejado o de una señal) al que pertenece la identidad */
    @Synchronized override fun deviceOf(kind: SensorKind, id: String): PhysicalDevice? =
        devices.firstOrNull { it.kind == kind && (it.antId == id || it.bleId == id) }

    /** Clave del aparato solo si la identidad está emparejada con otra (si no, null) */
    @Synchronized override fun pairKeyOf(kind: SensorKind, id: String): String? =
        deviceOf(kind, id)?.takeIf { it.isPaired }?.key

    @Synchronized override fun partnerOf(kind: SensorKind, id: String): String? =
        deviceOf(kind, id)?.takeIf { it.isPaired }?.let { if (it.antId == id) it.bleId else it.antId }

    @Synchronized override fun isBlocked(kind: SensorKind, antId: String, bleId: String): Boolean =
        BlockedPair(kind, antId, bleId) in blockedPairs

    /**
     * Une ANT+ y BLE como un solo aparato. Devuelve true si cambió algo.
     * - Automático: se rechaza si la pareja está bloqueada o si alguna identidad está en un aparato manual.
     * - Manual: siempre se aplica y desbloquea la pareja.
     * Conserva el alias y el hueco de las fichas previas de cualquiera de las dos identidades.
     */
    @Synchronized fun link(kind: SensorKind, antId: String, bleId: String, alias: String?, manual: Boolean): Boolean {
        require(antId.startsWith("ANT:") && bleId.startsWith("BLE:")) { "link($antId, $bleId)" }
        val existing = devices.firstOrNull { it.kind == kind && it.antId == antId && it.bleId == bleId }
        if (existing != null) {
            if (!manual || existing.manual) return false
            replace(existing, existing.copy(manual = true))
            blockedPairs.remove(BlockedPair(kind, antId, bleId))
            return true
        }
        val prevAnt = deviceOf(kind, antId)
        val prevBle = deviceOf(kind, bleId)
        if (!manual) {
            if (isBlocked(kind, antId, bleId)) return false
            if (prevAnt?.let { it.manual && it.isPaired } == true || prevBle?.let { it.manual && it.isPaired } == true) return false
        }
        devices.removeAll { it === prevAnt || it === prevBle }
        devices += PhysicalDevice(
            key = antId,
            kind = kind,
            alias = prevAnt?.alias ?: prevBle?.alias ?: alias?.let { clean(it) }?.takeIf { it.isNotBlank() } ?: antId,
            antId = antId,
            bleId = bleId,
            preferredSlot = prevAnt?.preferredSlot ?: prevBle?.preferredSlot,
            manual = manual
        )
        if (manual) blockedPairs.remove(BlockedPair(kind, antId, bleId))
        return true
    }

    /** Separa un aparato emparejado en dos de una señal y bloquea la pareja. El hueco se queda en el ANT+. */
    @Synchronized fun split(kind: SensorKind, key: String): Boolean {
        val d = find(kind, key)?.takeIf { it.isPaired } ?: return false
        devices.remove(d)
        devices += PhysicalDevice(d.antId!!, kind, d.alias, d.antId, null, d.preferredSlot)
        devices += PhysicalDevice(d.bleId!!, kind, d.alias, null, d.bleId, null)
        blockedPairs += BlockedPair(kind, d.antId, d.bleId)
        return true
    }

    /**
     * Bloquea una pareja ANT+ ↔ BLE para el emparejado automático sin que haya ficha emparejada que separar
     * (agrupado solo de sesión: "no son el mismo aparato"). Un `link` manual la desbloquea. Devuelve true si cambió.
     */
    @Synchronized fun block(kind: SensorKind, antId: String, bleId: String): Boolean {
        require(antId.startsWith("ANT:") && bleId.startsWith("BLE:")) { "block($antId, $bleId)" }
        return blockedPairs.add(BlockedPair(kind, antId, bleId))
    }

    /** Olvida el aparato por completo (también sus parejas bloqueadas) */
    @Synchronized fun forget(kind: SensorKind, key: String): Boolean {
        val d = find(kind, key) ?: return false
        devices.remove(d)
        blockedPairs.removeAll { it.kind == kind && (it.antId == d.antId || it.bleId == d.bleId) }
        return true
    }

    /** Guarda un aparato de una sola señal (si aún no está en la libreta). Devuelve su clave. */
    @Synchronized fun remember(kind: SensorKind, id: String, alias: String): String {
        deviceOf(kind, id)?.let { return it.key }
        val ant = id.takeIf { it.startsWith("ANT:") }
        val ble = id.takeIf { it.startsWith("BLE:") }
        require(ant != null || ble != null) { "remember($id)" }
        devices += PhysicalDevice(id, kind, clean(alias).ifBlank { id }, ant, ble)
        return id
    }

    /** Fija el hueco del aparato (null = sin hueco fijo). Otro aparato del mismo tipo con ese hueco lo pierde. */
    @Synchronized fun setSlot(kind: SensorKind, key: String, slot: Int?): Boolean {
        val d = find(kind, key) ?: return false
        if (d.preferredSlot == slot) return false
        if (slot != null) devices.filter { it.kind == kind && it.preferredSlot == slot }
            .forEach { replace(it, it.copy(preferredSlot = null)) }
        replace(find(kind, key)!!, d.copy(preferredSlot = slot))
        return true
    }

    @Synchronized fun rename(kind: SensorKind, key: String, alias: String): Boolean {
        val d = find(kind, key) ?: return false
        val name = clean(alias)
        if (name.isEmpty() || name == d.alias) return false
        replace(d, d.copy(alias = name))
        return true
    }

    private fun find(kind: SensorKind, key: String) = devices.firstOrNull { it.kind == kind && it.key == key }

    private fun replace(old: PhysicalDevice, new: PhysicalDevice) {
        val i = devices.indexOf(old)
        if (i >= 0) devices[i] = new
    }

    @Synchronized fun serialize(): String = buildString {
        append(V2)
        for (d in devices) {
            append('\n')
            append(listOf("D", d.key, d.kind.name, d.antId.orEmpty(), d.bleId.orEmpty(),
                d.preferredSlot?.toString().orEmpty(), if (d.manual) "1" else "0", clean(d.alias)).joinToString("\t"))
        }
        for (b in blockedPairs) {
            append('\n')
            append(listOf("B", b.kind.name, b.antId, b.bleId).joinToString("\t"))
        }
    }

    companion object {
        private const val V2 = "v2"

        /** Sin tabuladores ni saltos (separadores del formato) y sin espacios sobrantes */
        private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim()

        /**
         * Lee el formato v2 o el de la spec 003 (`clave ANT BLE alias`, solo pulsómetros, automáticos).
         * Las líneas que no se entienden se ignoran; nunca lanza.
         */
        fun parse(text: String?): DeviceBook {
            val lines = text.orEmpty().lines().filter { it.isNotBlank() }
            if (lines.firstOrNull()?.trim() != V2) return DeviceBook(lines.mapNotNull(::parseLegacy))
            val devs = ArrayList<PhysicalDevice>()
            val blocked = ArrayList<BlockedPair>()
            for (line in lines.drop(1)) {
                val p = line.split('\t')
                when (p.firstOrNull()) {
                    "D" -> parseDevice(p)?.let { d ->
                        if (devs.none { it.kind == d.kind && (it.key == d.key || it.ids().any { id -> id in d.ids() }) }) devs += d
                    }
                    "B" -> if (p.size >= 4) {
                        val kind = kindOf(p[1])
                        if (kind != null && p[2].startsWith("ANT:") && p[3].startsWith("BLE:")) blocked += BlockedPair(kind, p[2], p[3])
                    }
                }
            }
            return DeviceBook(devs, blocked)
        }

        private fun PhysicalDevice.ids() = listOfNotNull(antId, bleId)

        private fun kindOf(s: String) = SensorKind.entries.firstOrNull { it.name == s }

        private fun parseDevice(p: List<String>): PhysicalDevice? {
            if (p.size < 8) return null
            val kind = kindOf(p[2]) ?: return null
            val ant = p[3].takeIf { it.startsWith("ANT:") }
            val ble = p[4].takeIf { it.startsWith("BLE:") }
            if (ant == null && ble == null) return null
            val key = if (ant != null && ble != null) ant else (ant ?: ble!!)
            return PhysicalDevice(key, kind, p[7].ifBlank { key }, ant, ble, p[5].toIntOrNull(), p[6] == "1")
        }

        private fun parseLegacy(line: String): PhysicalDevice? {
            val p = line.split('\t')
            if (p.size < 4 || !p[1].startsWith("ANT:") || !p[2].startsWith("BLE:")) return null
            return PhysicalDevice(p[1], SensorKind.HR, p[3].ifBlank { p[1] }, p[1], p[2])
        }
    }
}

/**
 * Registro persistente (SharedPreferences) de aparatos físicos de doble señal.
 * Usa el mismo archivo que la spec 003 ("hr_device_registry"): el formato antiguo se migra al leerlo.
 */
@Singleton
class SensorDeviceRegistry @Inject constructor(
    @ApplicationContext context: Context
) : DeviceDirectory {
    private val prefs = context.getSharedPreferences("hr_device_registry", Context.MODE_PRIVATE)
    private val book = DeviceBook.parse(prefs.getString(KEY, null))

    private val _devices = MutableStateFlow(book.all())
    /** Aparatos guardados, para la UI */
    val devices: StateFlow<List<PhysicalDevice>> = _devices.asStateFlow()

    init {
        // Deja en Logcat lo guardado (incluidos vínculos migrados de la spec 003) para detectar emparejados erróneos
        val all = book.all()
        Timber.d("💓🚴 Registro: ${all.size} aparatos" + all.joinToString("") {
            "\n  ${it.kind} ${it.alias}: ${it.antId ?: "—"} ↔ ${it.bleId ?: "—"}" +
                (it.preferredSlot?.let { s -> " · hueco ${s + 1}" } ?: "") + if (it.manual) " · manual" else ""
        })
    }

    override fun deviceOf(kind: SensorKind, id: String) = book.deviceOf(kind, id)
    override fun all(kind: SensorKind) = book.all(kind)
    override fun pairKeyOf(kind: SensorKind, id: String) = book.pairKeyOf(kind, id)
    override fun partnerOf(kind: SensorKind, id: String) = book.partnerOf(kind, id)
    override fun isBlocked(kind: SensorKind, antId: String, bleId: String) = book.isBlocked(kind, antId, bleId)

    fun link(kind: SensorKind, antId: String, bleId: String, alias: String?, manual: Boolean): Boolean =
        changed(book.link(kind, antId, bleId, alias, manual)) {
            "${tag(kind)} Registro: $antId ↔ $bleId vinculados${if (manual) " (manual)" else ""}"
        }

    fun split(kind: SensorKind, key: String) = changed(book.split(kind, key)) { "${tag(kind)} Registro: $key separado" }
    fun forget(kind: SensorKind, key: String) = changed(book.forget(kind, key)) { "${tag(kind)} Registro: $key olvidado" }
    fun block(kind: SensorKind, antId: String, bleId: String) = changed(book.block(kind, antId, bleId)) {
        "${tag(kind)} Registro: $antId ↔ $bleId bloqueados (no son el mismo aparato)"
    }
    fun setSlot(kind: SensorKind, key: String, slot: Int?) = changed(book.setSlot(kind, key, slot)) {
        "${tag(kind)} Registro: $key → hueco ${slot?.plus(1) ?: "libre"}"
    }
    fun rename(kind: SensorKind, key: String, alias: String) = changed(book.rename(kind, key, alias)) { "${tag(kind)} Registro: $key → $alias" }
    fun remember(kind: SensorKind, id: String, alias: String): String {
        val before = book.all().size
        val key = book.remember(kind, id, alias)
        changed(book.all().size != before) { "${tag(kind)} Registro: $id guardado" }
        return key
    }

    private inline fun changed(result: Boolean, log: () -> String): Boolean {
        if (result) {
            prefs.edit().putString(KEY, book.serialize()).apply()
            _devices.value = book.all()
            Timber.d(log())
        }
        return result
    }

    private fun tag(kind: SensorKind) = if (kind == SensorKind.HR) "💓" else "🚴"

    private companion object { const val KEY = "devices" }
}
