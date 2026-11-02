package app.trombito.rallycopilot.data.escucha

import app.trombito.rallycopilot.data.voz.FocoAudio
import app.trombito.rallycopilot.domain.escucha.Escucha
import app.trombito.rallycopilot.domain.escucha.ResultadoEscucha
import app.trombito.rallycopilot.motor.edicion.InterpreteOrdenes
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * [Escucha] con foco de audio exclusivo ([foco]), un micrófono ([audio]) y un reconocedor con la gramática cerrada de
 * [GramaticaOrdenes] ([reconocedores]). El tiempo se mide en muestras leídas (no con el reloj): acaba [SILENCIO_S]
 * segundos después de la última actividad de voz, o a los [MAXIMO_S] s. Las frases que el reconocedor cierre por su
 * cuenta se acumulan, de modo que una pausa corta en mitad de la orden («derecha tres… cincuenta») no la corta.
 */
class EscuchaImpl(
    private val foco: FocoAudio,
    private val audio: FabricaAudio,
    private val reconocedores: FabricaReconocedor,
    private val permiso: () -> Boolean,
    private val dispatcher: CoroutineDispatcher,
) : Escucha {
    private val _escuchando = MutableStateFlow(false)
    override val escuchando: StateFlow<Boolean> = _escuchando.asStateFlow()
    private val turno = Mutex()

    override fun tienePermiso(): Boolean = permiso()

    override suspend fun escuchar(): ResultadoEscucha {
        if (!permiso()) return ResultadoEscucha.SinPermiso
        return turno.withLock {
            if (!foco.pedir()) {
                ResultadoEscucha.NoDisponible
            } else {
                _escuchando.value = true
                try {
                    withContext(dispatcher) { grabar() }
                } finally {
                    _escuchando.value = false
                    foco.soltar()
                }
            }
        }
    }

    private suspend fun grabar(): ResultadoEscucha {
        val texto =
            reconocedores.crear(GramaticaOrdenes.jsonOrdenes())?.use { reconocedor ->
                audio.abrir()?.use { fuente -> reconocer(fuente, reconocedor) }
            } ?: return ResultadoEscucha.NoDisponible
        val orden = InterpreteOrdenes.interpretar(texto)
        return if (orden != null) {
            ResultadoEscucha.Entendido(orden, texto)
        } else {
            ResultadoEscucha.NoEntendido(texto)
        }
    }

    /** Lo que se dice hasta el silencio o el máximo; `null` si el micrófono falla. */
    private suspend fun reconocer(
        fuente: FuenteAudio,
        reconocedor: Reconocedor,
    ): String? {
        val buffer = ShortArray(MUESTRAS_POR_LECTURA)
        val partes = ArrayList<String>()
        var muestras = 0
        var ultimaActividad = 0
        var parcialAnterior = ""
        var hablo = false
        while (coroutineContext.isActive && muestras < MAXIMO_MUESTRAS) {
            val n = fuente.leer(buffer)
            if (n < 0) return null
            muestras += n
            if (reconocedor.aceptar(buffer, n)) {
                reconocedor.resultado().takeIf { it.isNotBlank() }?.let { partes += it }
                hablo = true
                ultimaActividad = muestras
                parcialAnterior = ""
            } else {
                val parcial = reconocedor.parcial()
                if (parcial.isNotBlank()) hablo = true
                if (parcial != parcialAnterior) ultimaActividad = muestras
                parcialAnterior = parcial
            }
            if (hablo && muestras - ultimaActividad >= SILENCIO_MUESTRAS) break
        }
        reconocedor.fin().takeIf { it.isNotBlank() }?.let { partes += it }
        return partes.joinToString(" ")
    }

    companion object {
        /** Silencio tras hablar que da por acabada la orden. */
        const val SILENCIO_S = 1.2

        /** Duración máxima de una escucha. */
        const val MAXIMO_S = 5.0

        private const val MUESTRAS_POR_LECTURA = FRECUENCIA_HZ / 10
        private const val SILENCIO_MUESTRAS = (SILENCIO_S * FRECUENCIA_HZ).toInt()
        private const val MAXIMO_MUESTRAS = (MAXIMO_S * FRECUENCIA_HZ).toInt()
    }
}
