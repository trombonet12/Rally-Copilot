package app.trombito.rallycopilot.domain.copia

import kotlinx.coroutines.flow.StateFlow

/**
 * Copia automática de los datos en el almacenamiento del móvil (carpeta de la app), para sacarla con `adb pull` sin
 * abrir la app. Conserva siempre la última y unas pocas fechadas. Nunca lanza: un fallo se devuelve como `false`.
 */
interface CopiaAutomaticaDatos {
    /** Interruptor «Copia automática» (encendido por defecto). Solo gobierna las copias que nadie ha pedido. */
    val activa: StateFlow<Boolean>

    fun activar(valor: Boolean)

    /** Copia ya, aunque el interruptor esté apagado (botón «Copiar ahora»). `true` si quedó guardada. */
    suspend fun copiarAhora(): Boolean

    /** Copia al acabar un recorrido guardado, si el interruptor está encendido. */
    suspend fun copiarAlTerminar(): Boolean

    /**
     * Copia una vez al día: si el interruptor está encendido y la última fechada tiene más de 20 h (o no existe).
     * [ahoraMs] es la hora de pared en ms epoch.
     */
    suspend fun copiarSiToca(ahoraMs: Long): Boolean
}
