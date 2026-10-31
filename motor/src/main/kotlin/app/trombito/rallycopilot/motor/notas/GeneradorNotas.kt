package app.trombito.rallycopilot.motor.notas

import kotlin.math.min

/**
 * Convierte las curvas detectadas en notas de rally: grado según [tabla], modificadores, encadenado con «y» y
 * distancias redondeadas. Puro y determinista.
 *
 * Modificadores:
 * - «larga» / «muy larga» por ángulo girado: curva normal ≥ 100° / ≥ 160°; horquilla ≥ 200° / ≥ 240°. Las horquillas
 *   no llevan otros modificadores.
 * - «cierra» / «abre» solo en grado 4 o más cerrado, con arco de al menos 40 m, un salto de grado entre la entrada y
 *   la salida y una variación de radio suficiente (R entrada / R salida ≥ 1,4 para cierra; R salida / R entrada ≥ 1,6
 *   para abre).
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
     * vía o del piloto) en una posición en metros: los grados pasan a ser relativos a ella (ver [TablaGrados]) y,
     * si la curva es grado 6, se omite salvo que gire 20° o más.
     */
    fun generar(
        curvas: List<Curva>,
        vRefKmh: (Double) -> Double? = { null },
    ): List<Nota> {
        val candidatas =
            curvas
                .map { Candidata(it, gradoDe(it, vRefKmh)) }
                .filterNot { it.grado == 6 && it.curva.anguloGrados < ANGULO_MIN_GRADO_6 }
        return candidatas.mapIndexed { i, c -> nota(c, candidatas.getOrNull(i + 1)?.curva, vRefKmh) }
    }

    private fun gradoDe(
        curva: Curva,
        vRefKmh: (Double) -> Double?,
    ): Int? =
        if (tabla.esHorquilla(curva.anguloGrados, curva.radioCentralM)) {
            null
        } else {
            tabla.grado(curva.radioCentralM, vRefKmh(curva.inicioM))
        }

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
        )
    }

    private fun modificadores(
        curva: Curva,
        grado: Int?,
        vRefKmh: (Double) -> Double?,
    ): Set<Modificador> =
        buildSet {
            largura(curva.anguloGrados, horquilla = grado == null)?.let { add(it) }
            if (grado != null && grado <= GRADO_MAX_ABRE_CIERRA) {
                tendencia(curva, vRefKmh(curva.inicioM))?.let { add(it) }
            }
        }

    /** «larga» / «muy larga» según el ángulo; las horquillas usan umbrales más altos. */
    private fun largura(
        angulo: Double,
        horquilla: Boolean,
    ): Modificador? {
        val larga = if (horquilla) ANGULO_LARGA_HORQUILLA else ANGULO_LARGA
        val muyLarga = if (horquilla) ANGULO_MUY_LARGA_HORQUILLA else ANGULO_MUY_LARGA
        return when {
            angulo >= muyLarga -> Modificador.MUY_LARGA
            angulo >= larga -> Modificador.LARGA
            else -> null
        }
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
     * menor de 20 m o de 1,0 s a `min(velocidad de curva, vRef)`.
     */
    private fun distanciaA(
        curva: Curva,
        siguiente: Curva?,
        vRefKmh: (Double) -> Double?,
    ): Int? {
        if (siguiente == null) return null
        val hueco = (siguiente.inicioM - curva.finM).coerceAtLeast(0.0)
        val vCurva = tabla.velocidadCurvaKmh(curva.radioCentralM)
        val kmh = vRefKmh(curva.inicioM)?.let { min(vCurva, it) } ?: vCurva
        val metrosPorSegundo = kmh / KMH_POR_MS
        val unida = hueco < HUECO_UNIDA_M || hueco < HUECO_UNIDA_S * metrosPorSegundo
        return if (unida) null else DISTANCIAS_M.lastOrNull { it <= hueco }
    }

    private companion object {
        const val ANGULO_MIN_GRADO_6 = 20.0
        const val ANGULO_LARGA = 100.0
        const val ANGULO_MUY_LARGA = 160.0
        const val ANGULO_LARGA_HORQUILLA = 200.0
        const val ANGULO_MUY_LARGA_HORQUILLA = 240.0
        const val GRADO_MAX_ABRE_CIERRA = 4
        const val ARCO_MIN_TENDENCIA_M = 40.0
        const val RATIO_CIERRA = 1.4
        const val RATIO_ABRE = 1.6
        const val HUECO_UNIDA_M = 20.0
        const val HUECO_UNIDA_S = 1.0
        const val KMH_POR_MS = 3.6
        val DISTANCIAS_M = listOf(10, 20, 30, 40, 50, 60, 70, 80, 100, 120, 150, 200, 250, 300, 400, 500, 600, 700, 800)
    }
}
