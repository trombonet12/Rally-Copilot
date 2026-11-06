package app.trombito.rallycopilot.data.escucha

import app.trombito.rallycopilot.domain.escucha.ControlOrdenes
import app.trombito.rallycopilot.domain.escucha.Escucha
import app.trombito.rallycopilot.domain.voz.Locutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Une la escucha con las órdenes: espera a que el copiloto termine de hablar (para no oírse a sí mismo), escucha,
 * ejecuta y confirma; si la confirmación es una pregunta de sí o no («di sí para terminar»), espera a que acabe de
 * hablar y escucha una vez más la respuesta. [escuchando] es `true` desde que se pide hasta que acaba la confirmación
 * de la escucha, para animar el botón; una petición mientras ya se escucha se ignora.
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
                esperarAQueAcabeDeHablar()
                ejecutor.ejecutar(escucha.escuchar())
                if (ejecutor.esperaConfirmacion) {
                    esperarAQueAcabeDeHablar(ARRANQUE_VOZ_MS)
                    ejecutor.ejecutar(escucha.escuchar())
                    ejecutor.descartarConfirmacion()
                }
            } finally {
                _escuchando.value = false
            }
        }
    }

    /** Con [arranqueMs] se da tiempo a que la frase recién pedida empiece a sonar antes de mirar si ha acabado. */
    private suspend fun esperarAQueAcabeDeHablar(arranqueMs: Long = 0L) {
        if (arranqueMs > 0) delay(arranqueMs)
        withTimeoutOrNull(ESPERA_VOZ_MS) { locutor.hablando.first { !it } }
    }

    private companion object {
        /** Lo máximo que se espera a que acabe de hablar el copiloto antes de abrir el micrófono. */
        const val ESPERA_VOZ_MS = 3_000L

        /** Lo que tarda en empezar a sonar una frase pedida al locutor (sintetizarla si no estaba en caché). */
        const val ARRANQUE_VOZ_MS = 400L
    }
}
