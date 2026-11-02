package app.trombito.rallycopilot.data.escucha

import app.trombito.rallycopilot.domain.ajustes.RepositorioAjustesConduccion
import app.trombito.rallycopilot.domain.escucha.EdicionPendiente
import app.trombito.rallycopilot.domain.escucha.ProveedorContextoConduccion
import app.trombito.rallycopilot.domain.escucha.RepositorioEdiciones
import app.trombito.rallycopilot.domain.escucha.RepositorioMarcas
import app.trombito.rallycopilot.domain.escucha.ResultadoEscucha
import app.trombito.rallycopilot.domain.voz.Locutor
import app.trombito.rallycopilot.motor.edicion.Marca
import app.trombito.rallycopilot.motor.edicion.Orden
import app.trombito.rallycopilot.motor.notas.NivelAnticipacion
import app.trombito.rallycopilot.motor.voz.ModoCopiloto
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Última frase que dijo el copiloto durante la conducción (la que repite «repite»); la escribe `CopilotoVoz`. */
@Singleton
class MemoriaFrases
    @Inject
    constructor() {
        @Volatile
        var ultima: String? = null
            private set

        fun recordar(frase: String) {
            ultima = frase
        }
    }

/**
 * Lo que hace cada orden de voz con la conducción, y la confirmación corta que dice el copiloto: «hecho» cuando
 * cambió algo, «no entendido» si no se entendió o no se pudo, nada tras «silencio» (ni tras «repite», que ya habla).
 *
 * - [Orden.Repite]: vuelve a decir la última frase del copiloto.
 * - [Orden.MasPronto] / [Orden.MasTarde]: un nivel de anticipación más pronto o más tarde (con tope en los extremos).
 * - [Orden.Silencio] / [Orden.Habla]: el modo SILENCIO y el modo en que estaba antes (COMPLETO si no se sabe).
 * - [Orden.MarcaAqui]: guarda una [Marca] en la posición y el tramo activo.
 * - [Orden.Nota] / [Orden.Quitar] / [Orden.Cuidado]: publican una [EdicionPendiente] (otro componente las aplica).
 */
@Suppress("LongParameterList") // colaboradores de las órdenes: ajustes, voz, ediciones, marcas y contexto
class EjecutorOrdenes(
    private val ajustes: RepositorioAjustesConduccion,
    private val locutor: Locutor,
    private val memoria: MemoriaFrases,
    private val ediciones: RepositorioEdiciones,
    private val marcas: RepositorioMarcas,
    private val contexto: ProveedorContextoConduccion,
    private val ahoraMs: () -> Long,
) {
    private var modoAntesDeSilencio: ModoCopiloto? = null

    /** Ejecuta lo que salió de la escucha y confirma por voz. */
    suspend fun ejecutar(resultado: ResultadoEscucha) {
        when (resultado) {
            is ResultadoEscucha.Entendido -> confirmar(ejecutar(resultado.orden))
            is ResultadoEscucha.NoEntendido -> confirmar(NO_ENTENDIDO)
            ResultadoEscucha.SinPermiso, ResultadoEscucha.NoDisponible -> Unit
        }
    }

    /** Ejecuta [orden]; devuelve la confirmación a decir (o `null` si no hay que decir nada). */
    suspend fun ejecutar(orden: Orden): String? =
        when (orden) {
            Orden.Repite -> repetir()
            Orden.MasPronto -> HECHO.also { moverAnticipacion(-1) }
            Orden.MasTarde -> HECHO.also { moverAnticipacion(1) }
            Orden.Silencio -> callar()
            Orden.Habla -> hablar()
            Orden.MarcaAqui -> marcar()
            is Orden.Nota, Orden.Quitar, Orden.Cuidado -> editar(orden)
        }

    private fun repetir(): String? {
        memoria.ultima?.let(locutor::decir)
        return null
    }

    private suspend fun moverAnticipacion(paso: Int) {
        val actuales = ajustes.ajustes.first()
        val niveles = NivelAnticipacion.entries
        val nuevo = niveles[(actuales.anticipacion.ordinal + paso).coerceIn(0, niveles.lastIndex)]
        if (nuevo != actuales.anticipacion) ajustes.guardar(actuales.copy(anticipacion = nuevo))
    }

    private suspend fun callar(): String? {
        val modo = ajustes.ajustes.first().modoCopiloto
        if (modo != ModoCopiloto.SILENCIO) {
            modoAntesDeSilencio = modo
            ajustes.elegirModoCopiloto(ModoCopiloto.SILENCIO)
        }
        return null
    }

    private suspend fun hablar(): String {
        if (ajustes.ajustes.first().modoCopiloto == ModoCopiloto.SILENCIO) {
            ajustes.elegirModoCopiloto(modoAntesDeSilencio ?: ModoCopiloto.POR_DEFECTO)
        }
        modoAntesDeSilencio = null
        return HECHO
    }

    private suspend fun marcar(): String {
        val actual = contexto.actual() ?: return NO_ENTENDIDO
        marcas.guardar(Marca(actual.posicionM, ahoraMs(), actual.tramoId), actual.posicion)
        return HECHO
    }

    private fun editar(orden: Orden): String {
        val actual = contexto.actual() ?: return NO_ENTENDIDO
        ediciones.publicar(EdicionPendiente(orden, actual.posicionM, actual.tramoId, actual.posicion, ahoraMs()))
        return HECHO
    }

    private suspend fun confirmar(frase: String?) {
        if (frase != null && ajustes.ajustes.first().voz != null) locutor.decir(frase)
    }

    companion object {
        const val HECHO = "hecho"
        const val NO_ENTENDIDO = "no entendido"
    }
}
