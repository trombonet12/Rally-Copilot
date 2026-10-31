package app.trombito.rallycopilot.motor.notas

/**
 * Convierte las curvas detectadas en notas de rally: grado según [tabla], modificadores (larga, cierra, abre),
 * encadenado con «y» y distancias redondeadas. Puro y determinista. Las horquillas no llevan modificadores.
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
     * vía o del piloto) en una posición en metros; si la curva la alcanza es grado 6 y se omite salvo que gire
     * 20° o más.
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
        val distancia = distanciaA(c.curva, siguiente)
        return Nota(
            posicionM = c.curva.inicioM,
            finM = c.curva.finM,
            direccion = c.curva.direccion,
            grado = c.grado,
            modificadores = if (c.grado == null) emptySet() else modificadores(c.curva, c.grado, vRefKmh),
            distanciaSiguienteM = distancia,
            unidaConSiguiente = siguiente != null && distancia == null,
        )
    }

    private fun modificadores(
        curva: Curva,
        grado: Int,
        vRefKmh: (Double) -> Double?,
    ): Set<Modificador> {
        val vRef = vRefKmh(curva.inicioM)
        val gradoEntrada = tabla.grado(curva.radioEntradaM, vRef)
        val gradoSalida = tabla.grado(curva.radioSalidaM, vRef)
        val arco = curva.finM - curva.inicioM
        val larga = grado <= GRADO_MAX_LARGA
        return buildSet {
            if (larga && arco >= ARCO_MUY_LARGA_M) {
                add(Modificador.MUY_LARGA)
            } else if (larga && arco >= ARCO_LARGA_M) {
                add(Modificador.LARGA)
            }
            if (gradoSalida <= gradoEntrada - 1) {
                add(Modificador.CIERRA)
            } else if (gradoSalida >= gradoEntrada + 1) {
                add(Modificador.ABRE)
            }
        }
    }

    /** Hueco redondeado hacia abajo a [DISTANCIAS_M], o `null` si la nota va unida a la siguiente (o no hay). */
    private fun distanciaA(
        curva: Curva,
        siguiente: Curva?,
    ): Int? {
        if (siguiente == null) return null
        val hueco = (siguiente.inicioM - curva.finM).coerceAtLeast(0.0)
        val metrosPorSegundo = tabla.velocidadCurvaKmh(curva.radioCentralM) / 3.6
        val unida = hueco < HUECO_UNIDA_M || hueco < HUECO_UNIDA_S * metrosPorSegundo
        return if (unida) null else DISTANCIAS_M.lastOrNull { it <= hueco }
    }

    private companion object {
        const val ANGULO_MIN_GRADO_6 = 20.0
        const val GRADO_MAX_LARGA = 4
        const val ARCO_LARGA_M = 80.0
        const val ARCO_MUY_LARGA_M = 150.0
        const val HUECO_UNIDA_M = 25.0
        const val HUECO_UNIDA_S = 1.5
        val DISTANCIAS_M = listOf(10, 20, 30, 40, 50, 60, 70, 80, 100, 120, 150, 200, 250, 300, 400, 500)
    }
}
