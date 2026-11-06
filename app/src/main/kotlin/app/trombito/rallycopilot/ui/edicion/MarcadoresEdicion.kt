package app.trombito.rallycopilot.ui.edicion

import androidx.compose.ui.geometry.Offset
import app.trombito.rallycopilot.domain.edicion.TramoEditable
import app.trombito.rallycopilot.motor.edicion.Marca
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.notas.Direccion
import app.trombito.rallycopilot.motor.notas.Nota
import java.util.Arrays
import kotlin.math.cos
import kotlin.math.hypot

/** Qué es un marcador de la edición del tramo; cada tipo tiene su color en el mapa y en la leyenda. */
enum class TipoMarcador { NOTA, CUIDADO, MARCA, RADAR, CORTE }

/**
 * Un marcador sobre el mapa de edición: [id] estable, [posicionM] (metros desde la salida, a mitad de la curva en las
 * notas), el [texto] corto de la píldora («D4», «IH», «M», «C1») y dónde cae ([punto]).
 */
data class MarcadorEdicion(
    val id: String,
    val tipo: TipoMarcador,
    val posicionM: Double,
    val texto: String,
    val punto: PuntoGeo,
)

/** Texto corto de una nota: lado y grado («D4», «I2»), con «H» en las horquillas («DH»). */
fun textoCortoNota(nota: Nota): String {
    val lado = if (nota.direccion == Direccion.IZQUIERDA) "I" else "D"
    return lado + (nota.grado?.toString() ?: "H")
}

/** Identificador del marcador de una nota: su inicio en metros, que es lo que la identifica en el tramo. */
fun idMarcadorNota(nota: Nota): String = "n-${nota.posicionM}"

/** Los marcadores del tramo: notas, marcas «marca aquí» y radares con posición y cortes de sector (C1, C2…). */
fun marcadoresDe(
    datos: TramoEditable,
    sectoresM: List<Double>,
    traza: TrazaMetros,
): List<MarcadorEdicion> {
    val notas =
        datos.hoja.notas.map { n ->
            val medio = (n.posicionM + n.finM) / 2.0
            MarcadorEdicion(
                id = idMarcadorNota(n),
                tipo = if (n.cuidado) TipoMarcador.CUIDADO else TipoMarcador.NOTA,
                posicionM = medio,
                texto = textoCortoNota(n),
                punto = traza.puntoEn(medio),
            )
        }
    val marcas =
        datos.marcas.mapIndexed { i, m ->
            val radar = m.marca.etiqueta == Marca.ETIQUETA_RADAR
            val tipo = if (radar) TipoMarcador.RADAR else TipoMarcador.MARCA
            MarcadorEdicion("m-$i", tipo, m.marca.posicionM, if (radar) "R" else "M", traza.puntoEn(m.marca.posicionM))
        }
    val cortes =
        sectoresM.mapIndexed { i, m -> MarcadorEdicion("c-$i", TipoMarcador.CORTE, m, "C${i + 1}", traza.puntoEn(m)) }
    return notas + marcas + cortes
}

/** El marcador cuyo centro en pantalla cae a menos de [radioPx] de [toque]; el más cercano si hay varios. */
fun marcadorCercano(
    marcadores: List<Pair<MarcadorEdicion, Offset>>,
    toque: Offset,
    radioPx: Float,
): MarcadorEdicion? =
    marcadores
        .map { it.first to (it.second - toque).getDistance() }
        .filter { it.second <= radioPx }
        .minByOrNull { it.second }
        ?.first

/** Punto de la traza más cercano a un toque: sus [metros] desde la salida y la [distanciaM] al toque. */
class ProyeccionEnTraza(
    val metros: Double,
    val distanciaM: Double,
)

/**
 * La traza de un tramo con su distancia acumulada, para pasar de metros a posición geográfica ([puntoEn]) y de una
 * posición (un toque en el mapa) a metros ([proyectar]). Usa los mismos metros que las notas guardadas: la suma de las
 * distancias entre puntos de la traza.
 */
class TrazaMetros(
    private val traza: List<PuntoGeo>,
) {
    private val acumulada = DoubleArray(traza.size)

    init {
        for (i in 1 until traza.size) acumulada[i] = acumulada[i - 1] + traza[i - 1].distanciaA(traza[i])
    }

    val longitudM: Double get() = acumulada.lastOrNull() ?: 0.0

    /** Posición a [metros] de la salida, acotada a los extremos de la traza. */
    fun puntoEn(metros: Double): PuntoGeo {
        val d = metros.coerceIn(0.0, longitudM)
        val busqueda = Arrays.binarySearch(acumulada, d)
        val i = if (busqueda >= 0) busqueda else -busqueda - 2
        return if (traza.isEmpty()) {
            PuntoGeo(0.0, 0.0)
        } else if (i >= traza.lastIndex) {
            traza.last()
        } else {
            val largo = acumulada[i + 1] - acumulada[i]
            val t = if (largo > 0.0) (d - acumulada[i]) / largo else 0.0
            PuntoGeo(
                traza[i].lat + t * (traza[i + 1].lat - traza[i].lat),
                traza[i].lon + t * (traza[i + 1].lon - traza[i].lon),
            )
        }
    }

    /** Punto de la traza más cercano a [punto]; null si la traza no tiene al menos dos puntos. */
    fun proyectar(punto: PuntoGeo): ProyeccionEnTraza? {
        if (traza.size < 2) return null
        val metrosPorGradoLat = METROS_POR_GRADO
        val metrosPorGradoLon = METROS_POR_GRADO * cos(Math.toRadians(punto.lat))
        var mejorDistancia = Double.MAX_VALUE
        var mejorMetros = 0.0
        for (i in 0 until traza.size - 1) {
            val ax = (traza[i].lon - punto.lon) * metrosPorGradoLon
            val ay = (traza[i].lat - punto.lat) * metrosPorGradoLat
            val dx = (traza[i + 1].lon - punto.lon) * metrosPorGradoLon - ax
            val dy = (traza[i + 1].lat - punto.lat) * metrosPorGradoLat - ay
            val largo2 = dx * dx + dy * dy
            val t = if (largo2 > 0.0) (-(ax * dx + ay * dy) / largo2).coerceIn(0.0, 1.0) else 0.0
            val distancia = hypot(ax + t * dx, ay + t * dy)
            if (distancia < mejorDistancia) {
                mejorDistancia = distancia
                mejorMetros = acumulada[i] + t * (acumulada[i + 1] - acumulada[i])
            }
        }
        return ProyeccionEnTraza(mejorMetros, mejorDistancia)
    }

    private companion object {
        const val METROS_POR_GRADO = 111_320.0
    }
}
