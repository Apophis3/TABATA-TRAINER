package com.tuapp.tabatatrainer.sensor

/**
 * Vigilante de conexiones BLE ya hechas (spec 007, R12). Una conexión entra al suscribirse a la medida y cada
 * notificación renueva su hora (también las de "sin contacto con la piel", R2.1: la banda sigue viva). Si se pierde
 * el aviso de "desconectado" (Bluetooth apagado y encendido, por ejemplo) la dirección se quedaría para siempre en
 * `gatts` y el escaneo la ignoraría; [silent] dice cuáles llevan [silentMs] mudas para cerrarlas y reengancharlas.
 *
 * Las conexiones que aún no se han suscrito no se vigilan aquí (eso lo hace el vigilante de conexión de 20 s).
 * Hilo seguro: los avisos GATT llegan por hilos del sistema y la comprobación va en el tick.
 */
internal class BleLinkWatch(private val silentMs: Long) {
    private val lastNotify = HashMap<String, Long>()

    /** Conexión suscrita: empieza a contar desde ahora */
    @Synchronized
    fun onConnected(address: String, now: Long) { lastNotify[address] = now }

    /** Notificación de la medida. Solo cuenta para conexiones ya suscritas */
    @Synchronized
    fun onNotify(address: String, now: Long) {
        if (address in lastNotify) lastNotify[address] = now
    }

    @Synchronized
    fun forget(address: String) { lastNotify.remove(address) }

    @Synchronized
    fun clear() = lastNotify.clear()

    /** Direcciones suscritas sin ninguna notificación en los últimos [silentMs] */
    @Synchronized
    fun silent(now: Long): List<String> = lastNotify.filter { now - it.value >= silentMs }.keys.toList()
}
