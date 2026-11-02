package app.trombito.rallycopilot.data.escucha

import app.trombito.rallycopilot.domain.escucha.ControlOrdenes
import app.trombito.rallycopilot.domain.escucha.Escucha
import app.trombito.rallycopilot.domain.voz.Locutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Une la escucha con las órdenes: espera a que el copiloto termine de hablar (para no oírse a sí mismo), escucha,
 * ejecuta y confirma. [escuchando] es `true` desde que se pide hasta que acaba la confirmación de la escucha, para
 * animar el botón; una petición mientras ya se escucha se ignora.
 */
class ControlOrdenesImpl(
    private val escucha: Escucha,
    private val ejecutor: EjecutorOrdenes,
    private val locutor: Locutor,
    private val ambito: CoroutineScope,
) : ControlOrdenes {
    private val _escuchando = MutableStateFlow(false)
    override val escuchando: StateFlow<Boolean> = _escuchando.asStateFlow()

    override fun tienePermiso(): Boolean = escucha.tienePermiso()

    override fun escucharOrden() {
        if (!_escuchando.compareAndSet(expect = false, update = true)) return
        ambito.launch {
            try {
                withTimeoutOrNull(ESPERA_VOZ_MS) { locutor.hablando.first { !it } }
                ejecutor.ejecutar(escucha.escuchar())
            } finally {
                _escuchando.value = false
            }
        }
    }

    private companion object {
        /** Lo máximo que se espera a que acabe de hablar el copiloto antes de abrir el micrófono. */
        const val ESPERA_VOZ_MS = 3_000L
    }
}
