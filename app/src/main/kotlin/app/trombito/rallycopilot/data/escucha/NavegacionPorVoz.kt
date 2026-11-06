package app.trombito.rallycopilot.data.escucha

import app.trombito.rallycopilot.domain.ajustes.RepositorioAjustesConduccion
import app.trombito.rallycopilot.domain.busqueda.Buscador
import app.trombito.rallycopilot.domain.busqueda.ResultadoBusqueda
import app.trombito.rallycopilot.domain.navegacion.ControlNavegacion
import app.trombito.rallycopilot.domain.navegacion.Destino
import app.trombito.rallycopilot.domain.navegacion.EdicionParadas
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.navegacion.IniciarRutaA
import app.trombito.rallycopilot.domain.navegacion.ResultadoInicioRuta
import app.trombito.rallycopilot.domain.ubicacion.FuenteUbicacion
import app.trombito.rallycopilot.domain.ubicacion.posicionActual
import app.trombito.rallycopilot.motor.edicion.TipoLugar
import app.trombito.rallycopilot.motor.voz.RespuestasVoz
import kotlinx.coroutines.flow.first

/**
 * La navegación por voz: «llévame a <lugar>», «volver a casa», «añade parada en <lugar>», «cancela la ruta»,
 * «<categoría> más cercana» y «otro».
 *
 * - [irA]: el primer resultado del buscador para el nombre (con la posición como desempate) y arranca el guiado
 *   ([IniciarRutaA]); guarda los demás resultados para [otro], que vale durante [ventanaOtroMs] y pasa al siguiente.
 * - [volverACasa]: la casa de los ajustes; sin ella, dice cómo fijarla.
 * - [anadirParada]: solo con un guiado en curso.
 * - [cancelarRuta] y [masCercano] hacen una pregunta de sí o no: queda [esperaConfirmacion] hasta la respuesta
 *   ([confirmar]) o hasta [descartarConfirmacion], como en `ConduccionPorVoz`.
 *
 * Todas devuelven la frase que dice el copiloto.
 */
@Suppress("LongParameterList") // colaboradores de la navegación: búsqueda, ruta, control, ajustes, posición y reloj
class NavegacionPorVoz(
    private val buscador: Buscador,
    private val iniciar: IniciarRutaA,
    private val control: ControlNavegacion,
    private val ajustes: RepositorioAjustesConduccion,
    private val ubicacion: FuenteUbicacion,
    private val ahoraMs: () -> Long,
    private val ventanaOtroMs: Long = VENTANA_OTRO_MS,
) {
    private class Busqueda(
        val resultados: List<ResultadoBusqueda>,
        val indice: Int,
        val instanteMs: Long,
    )

    @Volatile
    private var ultima: Busqueda? = null

    @Volatile
    private var pendiente: (suspend () -> String)? = null

    /** `true` tras una pregunta de sí o no, hasta que llega la respuesta. */
    val esperaConfirmacion: Boolean get() = pendiente != null

    /** Ir a [lugar] (el nombre, normalizado): el primer resultado de la búsqueda. */
    @Suppress("ReturnCount") // cada salida temprana es una respuesta distinta del copiloto
    suspend fun irA(lugar: String): String {
        val cerca = ubicacion.posicionActual() ?: return RespuestasVoz.SIN_POSICION
        val resultados = buscador.buscar(lugar, cerca, MAX_RESULTADOS)
        if (resultados.isEmpty()) return RespuestasVoz.lugarNoEncontrado(lugar)
        ultima = Busqueda(resultados, 0, ahoraMs())
        return llevarA(resultados.first())
    }

    /** El siguiente resultado de la última búsqueda, si fue hace menos de [ventanaOtroMs]. */
    suspend fun otro(): String {
        val anterior = ultima
        val vigente = anterior != null && ahoraMs() - anterior.instanteMs <= ventanaOtroMs
        val siguiente = anterior?.takeIf { vigente }?.let { it.resultados.getOrNull(it.indice + 1) }
        if (anterior == null || siguiente == null) return RespuestasVoz.SIN_OTRA_OPCION
        ultima = Busqueda(anterior.resultados, anterior.indice + 1, ahoraMs())
        return llevarA(siguiente)
    }

    /** Ir a la casa de los ajustes. */
    suspend fun volverACasa(): String {
        val casa = ajustes.ajustes.first().casa ?: return RespuestasVoz.SIN_CASA
        return llevarA(Destino(NOMBRE_CASA, casa))
    }

    /** Añade [lugar] como parada del guiado en curso. */
    @Suppress("ReturnCount") // cada salida temprana es una respuesta distinta del copiloto
    suspend fun anadirParada(lugar: String): String {
        val guiando = control.estado.value as? EstadoNavegacion.Guiando ?: return RespuestasVoz.SIN_RUTA_PARA_PARADA
        if (guiando.paradas.size >= EdicionParadas.MAX_PARADAS) return RespuestasVoz.DEMASIADAS_PARADAS
        val resultado =
            buscador.buscar(lugar, ubicacion.posicionActual(), 1).firstOrNull()
                ?: return RespuestasVoz.lugarNoEncontrado(lugar)
        control.anadirParada(Destino(resultado.nombre, resultado.punto))
        return RespuestasVoz.paradaAnadida(resultado.nombre)
    }

    /** Pide confirmación para dejar la ruta y seguir en modo libre; sin guiado no hay nada que cancelar. */
    fun cancelarRuta(): String {
        if (control.estado.value !is EstadoNavegacion.Guiando) return RespuestasVoz.SIN_RUTA
        pendiente = {
            control.empezarLibre()
            RespuestasVoz.RUTA_CANCELADA
        }
        return RespuestasVoz.PEDIR_CANCELAR_RUTA
    }

    /** El sitio de [tipo] más cercano en línea recta; pregunta si se va y, con un «sí», arranca la ruta. */
    @Suppress("ReturnCount") // cada salida temprana es una respuesta distinta del copiloto
    suspend fun masCercano(tipo: TipoLugar): String {
        val aqui = ubicacion.posicionActual() ?: return RespuestasVoz.SIN_POSICION
        val cercano =
            buscador.buscar(tipo.consulta, aqui, CANDIDATOS_CATEGORIA).firstOrNull { it.categoria == tipo.categoria }
                ?: return RespuestasVoz.sinCercano(tipo)
        pendiente = { llevarA(cercano) }
        return RespuestasVoz.masCercano(tipo, aqui.distanciaA(cercano.punto), cercano.municipio)
    }

    /** La respuesta a la pregunta pendiente; `null` si no había ninguna. */
    suspend fun confirmar(si: Boolean): String? {
        val accion = pendiente ?: return null
        pendiente = null
        return if (si) accion() else ConduccionPorVoz.CANCELADO
    }

    /** Lo que se dijo en vez de una respuesta anula la pregunta pendiente. */
    fun descartarConfirmacion() {
        pendiente = null
    }

    private suspend fun llevarA(resultado: ResultadoBusqueda): String =
        llevarA(Destino(resultado.nombre, resultado.punto))

    private suspend fun llevarA(destino: Destino): String =
        when (val inicio = iniciar(destino)) {
            is ResultadoInicioRuta.Iniciada -> RespuestasVoz.llevandoteA(destino.nombre, inicio.tiempoS)
            ResultadoInicioRuta.SinConduccion -> ConduccionPorVoz.SIN_CONDUCCION
            ResultadoInicioRuta.SinPosicion -> RespuestasVoz.SIN_POSICION
            ResultadoInicioRuta.SinDatos -> RespuestasVoz.SIN_DATOS_RUTA
            is ResultadoInicioRuta.Error -> RespuestasVoz.RUTA_FALLIDA
        }

    companion object {
        /** Tiempo tras una búsqueda por voz en el que «otro» pasa al siguiente resultado. */
        const val VENTANA_OTRO_MS = 10_000L

        /** Resultados que se guardan de una búsqueda para «otro». */
        const val MAX_RESULTADOS = 5

        /** La categoría sola da primero los de la categoría; se piden algunos por si la primera fila es de otra. */
        private const val CANDIDATOS_CATEGORIA = 3

        /** Cómo se llama el destino que sale de la casa de los ajustes. */
        const val NOMBRE_CASA = "casa"
    }
}
