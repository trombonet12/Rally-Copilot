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
import app.trombito.rallycopilot.motor.voz.RespuestasVoz
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
 * cambió algo, «no te he entendido, di ayuda» si no se entendió, nada tras «silencio» (ni tras «repite», que ya habla).
 *
 * - [Orden.Repite]: vuelve a decir la última frase del copiloto.
 * - [Orden.MarcaAqui] / [Orden.RadarAqui]: guardan una [Marca] (la de radar con la etiqueta `radar`) en la posición y
 *   el tramo activo.
 * - [Orden.Nota] / [Orden.Quitar] / [Orden.Cuidado]: publican una [EdicionPendiente] (ligadas al tramo activo o
 *   pendientes; la fusión con las notas es de `NotasSesion`).
 * - [Orden.Ajuste] (modo, volumen, anticipación, filtros, capa, orientación, zoom): [AjustesPorVoz].
 * - [Orden.Pregunta]: [RespondedorPreguntas] busca los datos y responde con una frase corta.
 * - [Orden.Salida] / [Orden.ParaCrono] / [Orden.Confirma]: [ConduccionPorVoz]. [esperaConfirmacion] avisa de que el
 *   copiloto ha hecho una pregunta de sí o no y toca escuchar otra vez.
 * - [Orden.IrA] / [Orden.ParadaEn] / [Orden.VolverACasa] / [Orden.CancelarRuta] / [Orden.MasCercano] / [Orden.Otro]:
 *   [NavegacionPorVoz] (ir a un lugar, casa, paradas, cancelar la ruta, lo más cercano); sus preguntas de sí o no
 *   también dejan [esperaConfirmacion].
 * - [Orden.Ayuda]: lee las cinco órdenes más útiles.
 */
@Suppress("LongParameterList") // colaboradores de las órdenes: ajustes, voz, ediciones, marcas, contexto y respuestas
class EjecutorOrdenes(
    private val ajustes: RepositorioAjustesConduccion,
    private val locutor: Locutor,
    private val memoria: MemoriaFrases,
    private val ediciones: RepositorioEdiciones,
    private val marcas: RepositorioMarcas,
    private val contexto: ProveedorContextoConduccion,
    private val ahoraMs: () -> Long,
    private val preguntas: RespondedorPreguntas,
    private val conduccion: ConduccionPorVoz,
    private val navegacion: NavegacionPorVoz,
) {
    private val controles = AjustesPorVoz(ajustes)

    /** `true` si el copiloto acaba de preguntar «¿sí o no?» y la siguiente escucha es la respuesta. */
    val esperaConfirmacion: Boolean get() = conduccion.esperaConfirmacion || navegacion.esperaConfirmacion

    /** Ejecuta lo que salió de la escucha y confirma por voz; devuelve la confirmación (`null` si no hubo). */
    suspend fun ejecutar(resultado: ResultadoEscucha): String? {
        val frase =
            when (resultado) {
                is ResultadoEscucha.Entendido -> {
                    if (resultado.orden !is Orden.Confirma) descartarConfirmacion()
                    ejecutar(resultado.orden)
                }

                is ResultadoEscucha.NoEntendido -> {
                    val cancelaPregunta = esperaConfirmacion && resultado.texto.isBlank()
                    descartarConfirmacion()
                    if (cancelaPregunta) ConduccionPorVoz.CANCELADO else NO_ENTENDIDO
                }

                ResultadoEscucha.SinPermiso, ResultadoEscucha.NoDisponible -> {
                    descartarConfirmacion()
                    return null
                }
            }
        confirmar(frase)
        return frase
    }

    /** Ejecuta [orden]; devuelve la confirmación a decir (o `null` si no hay que decir nada). */
    @Suppress("CyclomaticComplexMethod") // un reparto exhaustivo, una rama por familia de órdenes
    suspend fun ejecutar(orden: Orden): String? =
        when (orden) {
            Orden.Repite -> repetir()
            Orden.MarcaAqui -> marcar(etiqueta = null)
            Orden.RadarAqui -> marcar(Marca.ETIQUETA_RADAR).let { if (it == HECHO) "radar marcado" else it }
            is Orden.Nota, Orden.Quitar, Orden.Cuidado -> editar(orden)
            is Orden.Ajuste -> controles.aplicar(orden)
            is Orden.Pregunta -> preguntas.responder(orden.tipo)
            Orden.Salida -> conduccion.darSalida(ajustes.ajustes.first().salidaManual)
            Orden.ParaCrono -> conduccion.pararCrono()
            is Orden.Confirma -> confirmarRespuesta(orden.si)
            Orden.Ayuda -> RespuestasVoz.AYUDA
            is Orden.IrA -> navegacion.irA(orden.lugar)
            is Orden.ParadaEn -> navegacion.anadirParada(orden.lugar)
            Orden.VolverACasa -> navegacion.volverACasa()
            Orden.CancelarRuta -> navegacion.cancelarRuta()
            is Orden.MasCercano -> navegacion.masCercano(orden.tipo)
            Orden.Otro -> navegacion.otro()
        }

    /** Anula la pregunta de sí o no pendiente (la escucha de la respuesta ya pasó). */
    fun descartarConfirmacion() {
        conduccion.descartarConfirmacion()
        navegacion.descartarConfirmacion()
    }

    /** Responde a la pregunta pendiente (la de terminar o la de la navegación); `null` si no había ninguna. */
    private suspend fun confirmarRespuesta(si: Boolean): String? =
        if (conduccion.esperaConfirmacion) conduccion.confirmar(si) else navegacion.confirmar(si)

    private fun repetir(): String? {
        memoria.ultima?.let(locutor::decir)
        return null
    }

    private suspend fun marcar(etiqueta: String?): String {
        val actual = contexto.actual() ?: return SIN_CONDUCCION
        marcas.guardar(Marca(actual.posicionM, ahoraMs(), actual.tramoId, etiqueta), actual.posicion)
        return HECHO
    }

    private suspend fun editar(orden: Orden): String {
        val actual = contexto.actual() ?: return SIN_CONDUCCION
        ediciones.publicar(EdicionPendiente(orden, actual.posicionM, actual.tramoId, actual.posicion, ahoraMs()))
        return HECHO
    }

    private suspend fun confirmar(frase: String?) {
        if (frase != null && ajustes.ajustes.first().voz != null) locutor.decir(frase)
    }

    companion object {
        const val HECHO = AjustesPorVoz.HECHO
        const val NO_ENTENDIDO = RespuestasVoz.NO_ENTENDIDO
        const val SIN_CONDUCCION = "no hay conducción"
    }
}
