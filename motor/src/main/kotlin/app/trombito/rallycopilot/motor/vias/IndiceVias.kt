package app.trombito.rallycopilot.motor.vias

import app.trombito.rallycopilot.motor.geo.ProyeccionPlana
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.Rumbo
import kotlin.math.floor
import kotlin.math.hypot

/**
 * Vía más cercana a una posición: distancia al segmento en metros, si la marcha sigue el sentido de dibujo y
 * [posicionEnViaM], los metros (haversine) desde el primer punto de la vía hasta la proyección del punto.
 */
data class Coincidencia(
    val via: Via,
    val distanciaMetros: Double,
    val adelante: Boolean,
    val posicionEnViaM: Double = 0.0,
)

/** Coincidencia con su puntuación (distancia + penalización de rumbo), para comparar candidatas. */
internal data class Candidata(
    val coincidencia: Coincidencia,
    val puntuacion: Double,
)

/**
 * Índice espacial de vías: rejilla de celdas de [celdaMetros] sobre una proyección equirectangular local
 * (latitud de referencia 39,6, centro de Mallorca) donde se guardan los segmentos de cada vía.
 */
class IndiceVias(
    private val vias: List<Via>,
    private val celdaMetros: Double = 100.0,
) {
    private val ax: DoubleArray
    private val ay: DoubleArray
    private val bx: DoubleArray
    private val by: DoubleArray
    private val viaDeSegmento: IntArray
    private val inicioSegmentoM: DoubleArray
    private val largoSegmentoM: DoubleArray
    private val celdas: Map<Long, IntArray>

    init {
        require(celdaMetros > 0.0) { "celdaMetros debe ser positivo: $celdaMetros" }
        val total = vias.sumOf { (it.puntos.size - 1).coerceAtLeast(0) }
        ax = DoubleArray(total)
        ay = DoubleArray(total)
        bx = DoubleArray(total)
        by = DoubleArray(total)
        viaDeSegmento = IntArray(total)
        inicioSegmentoM = DoubleArray(total)
        largoSegmentoM = DoubleArray(total)
        val construccion = HashMap<Long, MutableList<Int>>()
        var s = 0
        vias.forEachIndexed { indiceVia, via ->
            var acumuladaM = 0.0
            via.puntos.zipWithNext { a, b ->
                inicioSegmentoM[s] = acumuladaM
                largoSegmentoM[s] = a.distanciaA(b)
                acumuladaM += largoSegmentoM[s]
                ax[s] = x(a)
                ay[s] = y(a)
                bx[s] = x(b)
                by[s] = y(b)
                viaDeSegmento[s] = indiceVia
                registrar(construccion, s)
                s++
            }
        }
        celdas = construccion.mapValues { it.value.toIntArray() }
    }

    /**
     * Vía más cercana a [punto] dentro de [radioMetros]. Con [rumboGrados] (0 = norte, horario) entre las candidatas
     * se penaliza la diferencia de rumbo, ignorando el sentido de la vía; sin rumbo manda solo la distancia.
     */
    fun cercana(
        punto: PuntoGeo,
        rumboGrados: Double?,
        radioMetros: Double = RADIO_POR_DEFECTO_M,
    ): Coincidencia? = buscar(punto, rumboGrados, radioMetros, null)?.coincidencia

    /** Como [cercana] pero limitada a los segmentos de la vía [viaId]. */
    internal fun buscar(
        punto: PuntoGeo,
        rumboGrados: Double?,
        radioMetros: Double,
        viaId: Long?,
    ): Candidata? {
        val consulta = Consulta(x(punto), y(punto), rumboGrados, radioMetros)
        val mejor = Mejor()
        for (cx in celda(consulta.px - radioMetros)..celda(consulta.px + radioMetros)) {
            for (cy in celda(consulta.py - radioMetros)..celda(consulta.py + radioMetros)) {
                evaluarCelda(clave(cx, cy), consulta, viaId, mejor)
            }
        }
        if (mejor.segmento < 0) return null
        val coincidencia =
            Coincidencia(
                vias[viaDeSegmento[mejor.segmento]],
                mejor.distancia,
                mejor.adelante,
                inicioSegmentoM[mejor.segmento] + mejor.t * largoSegmentoM[mejor.segmento],
            )
        return Candidata(coincidencia, mejor.puntuacion)
    }

    private class Consulta(
        val px: Double,
        val py: Double,
        val rumboGrados: Double?,
        val radioMetros: Double,
    )

    private class Mejor {
        var segmento = -1
        var distancia = 0.0
        var puntuacion = Double.POSITIVE_INFINITY
        var adelante = true
        var t = 0.0
    }

    private fun evaluarCelda(
        clave: Long,
        consulta: Consulta,
        viaId: Long?,
        mejor: Mejor,
    ) {
        val segmentos = celdas[clave] ?: return
        for (s in segmentos) {
            if (viaId == null || vias[viaDeSegmento[s]].id == viaId) evaluar(s, consulta, mejor)
        }
    }

    private fun evaluar(
        s: Int,
        consulta: Consulta,
        mejor: Mejor,
    ) {
        val t = proyeccion(s, consulta.px, consulta.py)
        val distancia = hypot(consulta.px - (ax[s] + t * (bx[s] - ax[s])), consulta.py - (ay[s] + t * (by[s] - ay[s])))
        if (distancia > consulta.radioMetros) return
        val dx = bx[s] - ax[s]
        val dy = by[s] - ay[s]
        var penalizacion = 0.0
        var adelante = true
        if (consulta.rumboGrados != null && (dx != 0.0 || dy != 0.0)) {
            val diferencia = Rumbo.diferencia(consulta.rumboGrados, Rumbo.deVector(dx, dy))
            adelante = diferencia <= ANGULO_RECTO
            // Mod 180: ir en un sentido o en el contrario del segmento puntúa igual.
            penalizacion = diferenciaAxial(diferencia) * PENALIZACION_M_POR_GRADO
        }
        val puntuacion = distancia + penalizacion
        if (puntuacion < mejor.puntuacion) {
            mejor.segmento = s
            mejor.distancia = distancia
            mejor.puntuacion = puntuacion
            mejor.adelante = adelante
            mejor.t = t
        }
    }

    /** Fracción 0..1 del segmento [s] donde cae la proyección de (px, py). */
    private fun proyeccion(
        s: Int,
        px: Double,
        py: Double,
    ): Double {
        val dx = bx[s] - ax[s]
        val dy = by[s] - ay[s]
        val largo2 = dx * dx + dy * dy
        return if (largo2 == 0.0) 0.0 else (((px - ax[s]) * dx + (py - ay[s]) * dy) / largo2).coerceIn(0.0, 1.0)
    }

    private fun registrar(
        construccion: HashMap<Long, MutableList<Int>>,
        s: Int,
    ) {
        for (cx in celda(minOf(ax[s], bx[s]))..celda(maxOf(ax[s], bx[s]))) {
            for (cy in celda(minOf(ay[s], by[s]))..celda(maxOf(ay[s], by[s]))) {
                construccion.getOrPut(clave(cx, cy)) { ArrayList() }.add(s)
            }
        }
    }

    private fun celda(metros: Double): Int = floor(metros / celdaMetros).toInt()

    private fun clave(
        cx: Int,
        cy: Int,
    ): Long = (cx.toLong() shl 32) or (cy.toLong() and 0xFFFFFFFFL)

    private companion object {
        const val RADIO_POR_DEFECTO_M = 25.0
        const val LAT_REFERENCIA = 39.6
        const val ANGULO_RECTO = 90.0
        const val ANGULO_LLANO = 180.0

        /** Un segmento perpendicular al rumbo cuesta 22,5 m extra: gana al más cercano solo si este es otra calle. */
        const val PENALIZACION_M_POR_GRADO = 0.25

        /** Plano único del índice: escala fija a la latitud de referencia (Mallorca) y longitud 0 como origen. */
        private val PLANO = ProyeccionPlana(PuntoGeo(LAT_REFERENCIA, 0.0))

        fun x(p: PuntoGeo): Double = PLANO.x(p)

        fun y(p: PuntoGeo): Double = PLANO.y(p)

        /** Diferencia en 0..90 ignorando el sentido (mod 180). */
        fun diferenciaAxial(diferencia: Double): Double =
            if (diferencia > ANGULO_RECTO) ANGULO_LLANO - diferencia else diferencia
    }
}
