package com.tuapp.tabatatrainer.sensor

import android.content.Context
import com.dsi.ant.plugins.antplus.pcc.MultiDeviceSearch
import com.dsi.ant.plugins.antplus.pcc.defines.DeviceType
import com.dsi.ant.plugins.antplus.pcc.defines.RequestAccessResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.EnumSet
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

/** Un hub (pulso, cadencia) que usa la radio ANT+ compartida */
interface AntRadioClient {
    val antType: DeviceType
    val antTag: String                      // "💓" / "🚴" (mensajes de Logcat)

    /** Quiere que se busque su tipo ahora (en marcha y con canales libres) */
    fun wantsAntSearch(): Boolean

    /** Ya conectado o conectando: la búsqueda lo ignora */
    fun isAntKnown(number: Int): Boolean

    /** Aparato nuevo encontrado (la búsqueda ya está cerrada). El cliente llama a [AntRadio.beginConnect] */
    fun onAntFound(number: Int, displayName: String?)
}

/**
 * Radio ANT+ compartida por pulso y cadencia (spec 007, T-03).
 *
 * La búsqueda multi-dispositivo ocupa la radio del pincho: mientras corre, `requestAccess` falla con
 * SEARCH_TIMEOUT / CHANNEL_NOT_AVAILABLE / ALL_CHANNELS_IN_USE. Por eso hay **una sola** búsqueda, con la unión
 * de los tipos que piden los hubs, por ventanas (15 s buscando / 15 s de pausa); se cierra antes de pedir acceso
 * a un aparato y no se busca mientras alguien conecta (con límite de [CONNECT_TIMEOUT_MS] por si el acceso no
 * responde). Cada hub libera solo lo suyo: parar uno no corta la búsqueda que necesita el otro.
 */
@Singleton
class AntRadio @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val SEARCH_WINDOW_MS = 15_000L   // duración de cada ventana de búsqueda
        const val SEARCH_PAUSE_MS = 15_000L    // pausa entre ventanas (deja la radio libre para los canales)
        const val RETRY_MS = 3_000L            // tras conectar, fallar o perder un aparato
        private const val CONNECT_TIMEOUT_MS = 20_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val clients = CopyOnWriteArrayList<AntRadioClient>()
    private val connecting = ConcurrentHashMap<String, Long>()   // "HEARTRATE:12345" -> desde

    private var search: MultiDeviceSearch? = null
    private var searchTypes: Set<DeviceType> = emptySet()
    private var generation = 0                // invalida callbacks de búsquedas ya cerradas
    private var windowJob: Job? = null
    private var retryJob: Job? = null
    private var retryAt = 0L
    private var pluginMissing = false         // ANT+ Plugins sin instalar: no reintentar solo (sí al arrancar un hub)

    @Synchronized
    fun register(client: AntRadioClient) {
        if (client !in clients) clients += client
    }

    /** El cliente quiere buscar dentro de [delayMs] (si ya hay una búsqueda antes, se respeta la más próxima) */
    @Synchronized
    fun request(delayMs: Long = 0L) {
        val at = System.currentTimeMillis() + delayMs
        if (retryJob?.isActive == true && retryAt <= at) return
        retryJob?.cancel()
        retryAt = at
        retryJob = scope.launch { delay(delayMs); startSearch() }
    }

    /** Red de seguridad (cada pocos segundos desde los hubs): si alguien quiere buscar y nada está previsto, se programa */
    @Synchronized
    fun ensure() {
        if (pluginMissing || search != null || retryJob?.isActive == true || connecting.isNotEmpty()) return
        if (clients.any { it.wantsAntSearch() }) request(SEARCH_PAUSE_MS)
    }

    /** Antes de pedir acceso a un aparato: cierra la búsqueda y marca la radio como ocupada */
    @Synchronized
    fun beginConnect(client: AntRadioClient, number: Int): Boolean {
        val key = key(client, number)
        if (connecting.containsKey(key)) return false
        closeSearch()
        connecting[key] = System.currentTimeMillis()
        return true
    }

    /** Acceso resuelto (bien o mal): la radio queda libre y se vuelve a buscar dentro de [retryMs] */
    @Synchronized
    fun endConnect(client: AntRadioClient, number: Int, retryMs: Long = RETRY_MS) {
        connecting.remove(key(client, number))
        request(retryMs)
    }

    /** El hub para: libera sus conexiones pendientes y su parte de la búsqueda */
    @Synchronized
    fun release(client: AntRadioClient) {
        connecting.keys.removeAll { it.startsWith("${client.antType}:") }
        if (search != null && searchTypes == setOf(client.antType)) {
            closeSearch()
            Timber.d("${client.antTag} ANT+: búsqueda cerrada")
        }
        if (clients.any { it !== client && it.wantsAntSearch() }) request(SEARCH_PAUSE_MS)
    }

    private fun key(client: AntRadioClient, number: Int) = "${client.antType}:$number"

    private fun tagOf(types: Set<DeviceType>) =
        clients.filter { it.antType in types }.joinToString("") { it.antTag }

    @Synchronized
    private fun startSearch() {
        retryJob = null   // esta es la espera que acaba de cumplirse: permite programar la siguiente
        if (search != null) return
        val now = System.currentTimeMillis()
        connecting.entries.removeAll { (k, since) ->
            (now - since >= CONNECT_TIMEOUT_MS).also { if (it) Timber.w("📡 ANT+ $k: el acceso no responde, se libera la radio") }
        }
        if (connecting.isNotEmpty()) { request(SEARCH_PAUSE_MS); return }   // endConnect volverá a pedirla antes

        val wanted = clients.filter { it.wantsAntSearch() }
        if (wanted.isEmpty()) return
        val types = wanted.map { it.antType }.toSet()
        val tag = tagOf(types)
        val gen = ++generation
        try {
            search = MultiDeviceSearch(context, EnumSet.copyOf(types), object : MultiDeviceSearch.SearchCallbacks {
                override fun onSearchStarted(rssi: MultiDeviceSearch.RssiSupport?) {
                    Timber.d("$tag ANT+: búsqueda iniciada ${types.joinToString()}")
                }

                override fun onDeviceFound(result: com.dsi.ant.plugins.antplus.pccbase.MultiDeviceSearch.MultiDeviceSearchResult) {
                    onFound(gen, result.antDeviceType, result.antDeviceNumber, result.deviceDisplayName)
                }

                override fun onSearchStopped(reason: RequestAccessResult?) {
                    onStopped(gen, tag, reason)
                }
            })
            searchTypes = types
            windowJob?.cancel()
            windowJob = scope.launch {
                delay(SEARCH_WINDOW_MS)
                synchronized(this@AntRadio) {
                    if (generation == gen && search != null) { closeSearch(); request(SEARCH_PAUSE_MS) }
                }
            }
        } catch (e: Exception) {
            search = null; searchTypes = emptySet()
            Timber.e(e, "$tag ANT+: no disponible")
            request(SEARCH_PAUSE_MS)
        }
    }

    @Synchronized
    private fun onFound(gen: Int, type: DeviceType?, number: Int, name: String?) {
        if (gen != generation || search == null) return
        val client = clients.firstOrNull { it.antType == type && it.wantsAntSearch() } ?: return
        if (client.isAntKnown(number)) return
        Timber.d("${client.antTag} ANT+: encontrado $name ($number)")
        closeSearch()   // liberar la radio antes de pedir acceso
        client.onAntFound(number, name)
        if (connecting.isEmpty()) request(RETRY_MS)   // el cliente no llegó a conectar: seguir buscando
    }

    @Synchronized
    private fun onStopped(gen: Int, tag: String, reason: RequestAccessResult?) {
        if (gen != generation || search == null) return   // ya cerrada por nosotros
        Timber.d("$tag ANT+: búsqueda parada ($reason)")
        search = null; searchTypes = emptySet()
        windowJob?.cancel()
        pluginMissing = reason == RequestAccessResult.DEPENDENCY_NOT_INSTALLED
        if (!pluginMissing && connecting.isEmpty()) request(SEARCH_PAUSE_MS)
    }

    private fun closeSearch() {
        windowJob?.cancel()
        val s = search
        search = null; searchTypes = emptySet()
        generation++
        try { s?.close() } catch (_: Exception) {}
    }
}
