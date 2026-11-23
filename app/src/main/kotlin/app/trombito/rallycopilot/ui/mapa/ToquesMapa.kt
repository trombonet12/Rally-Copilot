package app.trombito.rallycopilot.ui.mapa

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.ui.alertas.RadaresUi
import app.trombito.rallycopilot.ui.alertas.SeleccionMapa
import kotlin.math.abs
import kotlin.math.hypot

/** Margen (dp) alrededor del dedo en el que un marcador cuenta como tocado. */
internal const val MARGEN_TOQUE_DP = 24f

/** Dos posiciones de nota de la hoja a menos de esto (m) son la misma: absorbe el viaje de ida y vuelta por el mapa. */
private const val TOLERANCIA_POSICION_M = 0.5

/** Lo que resuelve un toque en el mapa, con la capa que lo recibió (para el registro de diagnóstico). */
internal sealed interface ResultadoToque {
    /** Nombre corto de la capa tocada: `incidencia`, `radar`, `radar_tramo`, `bandera`, `nota`, `nota_editable`... */
    val capa: String

    /** Un elemento cuyo bocadillo se abre (o se cierra si ya estaba abierto). */
    data class Selecciona(
        val seleccion: SeleccionMapa,
        override val capa: String,
    ) : ResultadoToque

    /** Una nota editable (con el coche parado): se abre su editor. */
    data class EditaNota(
        val posicionM: Double,
    ) : ResultadoToque {
        override val capa: String get() = "nota_editable"
    }

    /** Una carretera de la capa de calor: su ficha. */
    data class Ficha(
        val ficha: FichaCalor,
    ) : ResultadoToque {
        override val capa: String get() = "calor"
    }

    /** No se tocó nada con información: se cierra lo abierto. */
    data object Nada : ResultadoToque {
        override val capa: String get() = "ninguna"
    }
}

/**
 * Las preguntas que hace el despachador al mapa, una por capa y en el orden en que se plantean. Cada una devuelve lo
 * que hay bajo el toque o null; la implementación real (ver `consultasDeMapa`) mira primero lo pintado y, si no, mide
 * la distancia en pantalla a los marcadores.
 */
@Suppress("LongParameterList") // una consulta por capa
internal class ConsultasToque(
    /** Id de la incidencia tocada. */
    val incidencia: () -> String?,
    /** Id del marcador de radar tocado (fijo, móvil o entrada de un tramo). */
    val radar: () -> String?,
    /** Id del tramo de radar cuya línea se tocó. */
    val tramoRadar: () -> String?,
    /** Bandera de salida o meta de un tramo cronometrado tocada. */
    val bandera: () -> SeleccionMapa.Bandera?,
    /** Posición en la hoja de la nota editable tocada (solo con el coche parado). */
    val notaEditable: () -> Double?,
    /** Posición en la hoja de la curva o aviso tocados. */
    val notaMapa: () -> Double?,
    /** Ficha de la carretera de la capa de calor tocada. */
    val calor: () -> FichaCalor?,
)

/**
 * Despacha un toque en [punto]: pregunta a cada capa por prioridad y se queda con la primera que responde (las demás
 * ni se consultan): incidencia, radar (fijo, móvil o entrada de tramo), línea de radar de tramo, bandera de tramo,
 * nota editable, curva o aviso del mapa y, por último, la capa de calor. La nota editable va antes que la del mapa: con
 * el coche parado ambas coinciden y tocar una nota es editarla.
 */
internal fun despacharToque(
    consultas: ConsultasToque,
    radares: RadaresUi,
    notas: NotasEnMapa,
    punto: PuntoGeo,
): ResultadoToque =
    despacharMarcadores(consultas, radares, punto)
        ?: despacharNotasYCalor(consultas, notas)
        ?: ResultadoToque.Nada

/** Incidencia, radar (fijo, móvil o entrada de tramo), línea de radar de tramo y bandera, por este orden. */
private fun despacharMarcadores(
    consultas: ConsultasToque,
    radares: RadaresUi,
    punto: PuntoGeo,
): ResultadoToque? =
    consultas.incidencia()?.let { ResultadoToque.Selecciona(SeleccionMapa.Incidencia(it), "incidencia") }
        ?: consultas.radar()?.let { seleccionDeRadar(it, radares) }
        ?: consultas.tramoRadar()?.let { ResultadoToque.Selecciona(SeleccionMapa.TramoRadar(it, punto), "radar_tramo") }
        ?: consultas.bandera()?.let { ResultadoToque.Selecciona(it, "bandera") }

/** Nota editable, curva o aviso del mapa y capa de calor, por este orden. */
private fun despacharNotasYCalor(
    consultas: ConsultasToque,
    notas: NotasEnMapa,
): ResultadoToque? =
    consultas.notaEditable()?.let { ResultadoToque.EditaNota(it) }
        ?: consultas.notaMapa()?.let {
            ResultadoToque.Selecciona(SeleccionMapa.Nota(posicionConocida(it, notas)), "nota")
        }
        ?: consultas.calor()?.let { ResultadoToque.Ficha(it) }

/** El marcador de entrada de un tramo selecciona el tramo; cualquier otro, el radar. */
private fun seleccionDeRadar(
    id: String,
    radares: RadaresUi,
): ResultadoToque {
    val tramoId = radares.marcadores.firstOrNull { it.id == id }?.tramoId
    return if (tramoId != null) {
        ResultadoToque.Selecciona(SeleccionMapa.TramoRadar(tramoId), "radar_tramo")
    } else {
        ResultadoToque.Selecciona(SeleccionMapa.Radar(id), "radar")
    }
}

/** La posición de la hoja que pintó el mapa ([leida]) reducida a la de la nota conocida que coincide, para comparar. */
internal fun posicionConocida(
    leida: Double,
    notas: NotasEnMapa,
): Double =
    (notas.curvas.map { it.posicionM } + notas.avisos.map { it.posicionM })
        .firstOrNull { abs(it - leida) < TOLERANCIA_POSICION_M } ?: leida

/**
 * La línea del registro de diagnóstico de un toque: «toque capa=radar resultado=bocadillo», «toque capa=ninguna».
 * Solo la capa y lo que se hizo: ni coordenadas ni ids que identifiquen un lugar.
 */
internal fun lineaDiagnosticoToque(resultado: ResultadoToque): String =
    when (resultado) {
        is ResultadoToque.Nada -> {
            "toque capa=${resultado.capa}"
        }

        is ResultadoToque.Selecciona -> {
            "toque capa=${resultado.capa} resultado=bocadillo"
        }

        is ResultadoToque.EditaNota -> {
            "toque capa=${resultado.capa} resultado=editor"
        }

        is ResultadoToque.Ficha -> {
            "toque capa=${resultado.capa} resultado=ficha"
        }
    }

/**
 * El candidato de [candidatos] más cercano al toque ([x], [y], px) a no más de [margenPx]; [proyectar] da su
 * posición en pantalla (null si no se puede proyectar). null si ninguno está al alcance. Es el respaldo de
 * `queryRenderedFeatures`, que solo ve lo ya colocado en el último fotograma y falla con la cámara en movimiento.
 */
internal fun <T> masCercano(
    candidatos: List<T>,
    x: Float,
    y: Float,
    margenPx: Float,
    proyectar: (T) -> Pair<Float, Float>?,
): T? =
    candidatos
        .mapNotNull { c -> proyectar(c)?.let { (px, py) -> c to hypot(px - x, py - y) } }
        .filter { it.second <= margenPx }
        .minByOrNull { it.second }
        ?.first

/** Distancia (px) del toque ([x], [y]) a la línea quebrada [linea] (puntos en pantalla); infinita si no hay puntos. */
internal fun distanciaALinea(
    linea: List<Pair<Float, Float>>,
    x: Float,
    y: Float,
): Float =
    when (linea.size) {
        0 -> Float.POSITIVE_INFINITY
        1 -> hypot(linea[0].first - x, linea[0].second - y)
        else -> linea.zipWithNext().minOf { (a, b) -> distanciaASegmentoPx(a, b, x, y) }
    }

private fun distanciaASegmentoPx(
    a: Pair<Float, Float>,
    b: Pair<Float, Float>,
    x: Float,
    y: Float,
): Float {
    val dx = b.first - a.first
    val dy = b.second - a.second
    val largo2 = dx * dx + dy * dy
    val t = if (largo2 == 0f) 0f else (((x - a.first) * dx + (y - a.second) * dy) / largo2).coerceIn(0f, 1f)
    return hypot(a.first + t * dx - x, a.second + t * dy - y)
}
