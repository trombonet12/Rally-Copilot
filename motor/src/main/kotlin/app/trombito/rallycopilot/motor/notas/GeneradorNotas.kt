package app.trombito.rallycopilot.motor.notas

import app.trombito.rallycopilot.motor.velocidad.kmhAMs
import kotlin.math.min

/**
 * Convierte las curvas detectadas en notas de rally: grado según el ángulo girado ([TablaGrados.gradoPorAngulo]),
 * modificadores, encadenado con «y» y distancias redondeadas. Puro y determinista. Una curva de menos de
 * [TablaGrados.anguloMinimoGrados] no genera nota; una de más de 180° es una horquilla (sin grado).
 *
 * Modificadores:
 * - «escuadra»: curva de 70–110° con arco de 30 m o menos y radio mínimo de 15 m o menos (un ángulo recto cerrado).
 *   Es grado 3 con [Modificador.ESCUADRA] y sin «larga».
 * - «larga» / «muy larga» por longitud de arco (fin − inicio): ≥ 100 m / ≥ 200 m. Vale también para las horquillas,
 *   que no llevan otros modificadores.
 * - «cierra» / «abre» solo en grado 4 o más cerrado, con arco de al menos 40 m, un salto de grado entre la entrada y
 *   la salida (grados por radio) y una variación de radio suficiente (R entrada / R salida ≥ 1,4 para cierra;
 *   R salida / R entrada ≥ 1,6 para abre).
 */
class GeneradorNotas(
    private val tabla: TablaGrados = TablaGrados(),
) {
    private data class Candidata(
        val curva: Curva,
        val grado: Int?,
    )

    /**
     * Genera las notas de [curvas] (ordenadas por posición). [vRefKmh] da la velocidad de referencia (límite de la
     * vía o del piloto) en una posición en metros: solo afecta a «cierra» / «abre» y al encadenado; el grado de la
     * nota depende únicamente del ángulo.
     */
    fun generar(
        curvas: List<Curva>,
        vRefKmh: (Double) -> Double? = { null },
    ): List<Nota> {
        val candidatas =
            curvas
                .filter { tabla.esHorquilla(it.anguloGrados) || tabla.gradoPorAngulo(it.anguloGrados) != null }
                .map { Candidata(it, gradoDe(it)) }
        return candidatas.mapIndexed { i, c -> nota(c, candidatas.getOrNull(i + 1)?.curva, vRefKmh) }
    }

    private fun gradoDe(curva: Curva): Int? =
        when {
            tabla.esHorquilla(curva.anguloGrados) -> null
            esEscuadra(curva) -> GRADO_ESCUADRA
            else -> tabla.gradoPorAngulo(curva.anguloGrados)
        }

    private fun esEscuadra(curva: Curva): Boolean =
        curva.anguloGrados in ESCUADRA_ANGULO_MIN..ESCUADRA_ANGULO_MAX &&
            curva.finM - curva.inicioM <= ESCUADRA_ARCO_MAX_M &&
            curva.radioMinM <= ESCUADRA_RADIO_MIN_M

    private fun nota(
        c: Candidata,
        siguiente: Curva?,
        vRefKmh: (Double) -> Double?,
    ): Nota {
        val distancia = distanciaA(c.curva, siguiente, vRefKmh)
        return Nota(
            posicionM = c.curva.inicioM,
            finM = c.curva.finM,
            direccion = c.curva.direccion,
            grado = c.grado,
            modificadores = modificadores(c.curva, c.grado, vRefKmh),
            distanciaSiguienteM = distancia,
            unidaConSiguiente = siguiente != null && distancia == null,
            radioMinM = c.curva.radioMinM,
        )
    }

    private fun modificadores(
        curva: Curva,
        grado: Int?,
        vRefKmh: (Double) -> Double?,
    ): Set<Modificador> =
        buildSet {
            if (grado != null && esEscuadra(curva)) {
                add(Modificador.ESCUADRA)
                return@buildSet
            }
            largura(curva.finM - curva.inicioM)?.let { add(it) }
            if (grado != null && grado <= GRADO_MAX_ABRE_CIERRA) {
                tendencia(curva, vRefKmh(curva.inicioM))?.let { add(it) }
            }
        }

    /** «larga» / «muy larga» según la longitud de arco. */
    private fun largura(arcoM: Double): Modificador? =
        when {
            arcoM >= ARCO_MUY_LARGA_M -> Modificador.MUY_LARGA
            arcoM >= ARCO_LARGA_M -> Modificador.LARGA
            else -> null
        }

    /** «cierra» o «abre»: salto de banda y variación de radio entre entrada y salida, en arcos de al menos 40 m. */
    private fun tendencia(
        curva: Curva,
        vRef: Double?,
    ): Modificador? {
        if (curva.finM - curva.inicioM < ARCO_MIN_TENDENCIA_M) return null
        val gradoEntrada = tabla.grado(curva.radioEntradaM, vRef)
        val gradoSalida = tabla.grado(curva.radioSalidaM, vRef)
        val cierra = gradoSalida < gradoEntrada && curva.radioEntradaM / curva.radioSalidaM >= RATIO_CIERRA
        val abre = gradoSalida > gradoEntrada && curva.radioSalidaM / curva.radioEntradaM >= RATIO_ABRE
        return when {
            cierra -> Modificador.CIERRA
            abre -> Modificador.ABRE
            else -> null
        }
    }

    /**
     * Hueco redondeado hacia abajo a [DISTANCIAS_M], o `null` si la nota va unida a la siguiente (o no hay): hueco
     * menor de 20 m o de 1,0 s a `min(velocidad de curva, vRef)`, con la de curva topada a 130 km/h.
     */
    private fun distanciaA(
        curva: Curva,
        siguiente: Curva?,
        vRefKmh: (Double) -> Double?,
    ): Int? {
        if (siguiente == null) return null
        val hueco = (siguiente.inicioM - curva.finM).coerceAtLeast(0.0)
        val vCurva = min(tabla.velocidadCurvaKmh(curva.radioCentralM), VELOCIDAD_MAX_KMH)
        val kmh = vRefKmh(curva.inicioM)?.let { min(vCurva, it) } ?: vCurva
        val metrosPorSegundo = kmh.kmhAMs()
        val unida = hueco < HUECO_UNIDA_M || hueco < HUECO_UNIDA_S * metrosPorSegundo
        return if (unida) null else DISTANCIAS_M.lastOrNull { it <= hueco }
    }

    private companion object {
        const val ARCO_LARGA_M = 100.0
        const val ARCO_MUY_LARGA_M = 200.0
        const val GRADO_ESCUADRA = 3
        const val ESCUADRA_ANGULO_MIN = 70.0
        const val ESCUADRA_ANGULO_MAX = 110.0
        const val ESCUADRA_ARCO_MAX_M = 30.0
        const val ESCUADRA_RADIO_MIN_M = 15.0
        const val GRADO_MAX_ABRE_CIERRA = 4
        const val ARCO_MIN_TENDENCIA_M = 40.0
        const val RATIO_CIERRA = 1.4
        const val RATIO_ABRE = 1.6
        const val HUECO_UNIDA_M = 20.0
        const val HUECO_UNIDA_S = 1.0

        /** Tope de la velocidad de curva al encadenar: una curva muy abierta no es «a 800 km/h». */
        const val VELOCIDAD_MAX_KMH = 130.0
        val DISTANCIAS_M = DISTANCIAS_NOTA_M
    }
}

/** Distancias (m) que se cantan entre dos notas; el hueco se redondea hacia abajo a una de ellas. */
internal val DISTANCIAS_NOTA_M =
    listOf(10, 20, 30, 40, 50, 60, 70, 80, 100, 120, 150, 200, 250, 300, 400, 500, 600, 700, 800)
