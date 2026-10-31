package app.trombito.rallycopilot.motor.notas

import app.trombito.rallycopilot.motor.geo.PuntoGeo

/** Elemento de la hoja de notas: una nota de curva o un aviso suelto, con la frase que se canta. */
sealed interface ElementoHoja {
    val posicionM: Double
    val frase: String

    /**
     * Nota de curva con los [avisosUnidos] (los que caen a menos de 20 m antes de ella), que se cantan en la misma
     * frase: «rasante, derecha tres».
     */
    data class DeNota(
        val nota: Nota,
        val avisosUnidos: List<Aviso> = emptyList(),
    ) : ElementoHoja {
        override val posicionM: Double get() = nota.posicionM
        override val frase: String
            get() {
                val previos = avisosUnidos.filterNot { it.tipo == TipoAviso.CUIDADO && nota.cuidado }
                return (previos.map { it.tipo.frase } + FrasesNota.frase(nota)).joinToString(", ")
            }
    }

    /** Aviso que se canta solo. */
    data class DeAviso(
        val aviso: Aviso,
    ) : ElementoHoja {
        override val posicionM: Double get() = aviso.posicionM
        override val frase: String get() = aviso.tipo.frase
    }
}

/** Hoja de notas de una ruta: elementos ordenados por posición (a igual posición, el aviso antes que la nota). */
data class HojaNotas(
    val elementos: List<ElementoHoja>,
) {
    /** Las notas de curva, en orden. */
    val notas: List<Nota> get() = elementos.filterIsInstance<ElementoHoja.DeNota>().map { it.nota }

    /** Todos los avisos, sueltos o unidos a una nota, en orden. */
    val avisos: List<Aviso>
        get() =
            elementos
                .flatMap {
                    when (it) {
                        is ElementoHoja.DeNota -> it.avisosUnidos
                        is ElementoHoja.DeAviso -> listOf(it.aviso)
                    }
                }.sortedBy { it.posicionM }
}

/**
 * Ensambla la hoja de notas completa de una ruta: curvas ([DetectorCurvas] + [GeneradorNotas]) y, si hay
 * [contexto], avisos de las vías, rasantes y cruces ([ExtractorAvisos]) y marcas de precaución en las notas
 * ([Precaucion]). Sin contexto solo hay notas de curva (las marcas que no dependen de las vías se aplican igual).
 */
class GeneradorHoja(
    private val tabla: TablaGrados = TablaGrados(),
    private val contexto: ContextoVias? = null,
) {
    /** Hoja de [ruta]; [vRefKmh] da la velocidad de referencia en una posición (ver [GeneradorNotas.generar]). */
    fun generar(
        ruta: List<PuntoGeo>,
        vRefKmh: (Double) -> Double? = { null },
    ): HojaNotas {
        val notas = GeneradorNotas(tabla).generar(DetectorCurvas().detectar(ruta), vRefKmh)
        val extraidos = contexto?.let { ExtractorAvisos(it).extraer(ruta) } ?: AvisosRuta(emptyList(), emptyList())
        val marcadas = Precaucion.marcar(notas, extraidos.avisos, extraidos.curvasPeligrosasM)
        val sueltos =
            Precaucion
                .curvasSinNota(
                    marcadas,
                    extraidos.curvasPeligrosasM,
                ).map { Aviso(it, TipoAviso.CUIDADO) }
        return HojaNotas(Ensamblado.unir(marcadas, (extraidos.avisos + sueltos).sinDuplicados()))
    }
}

/** Une los avisos con las notas en una lista ordenada de elementos. */
internal object Ensamblado {
    /** Distancia máxima antes de una nota para que un aviso se cante junto a ella. */
    const val UNION_MAX_M = 20.0

    /**
     * Elementos de la hoja. Un aviso a menos de [UNION_MAX_M] antes de una nota (que no sea continuación de una
     * cadena «x y y») se une a ella; el resto va suelto. [avisos] debe venir ordenada por posición.
     */
    fun unir(
        notas: List<Nota>,
        avisos: List<Aviso>,
    ): List<ElementoHoja> {
        val unidos = List(notas.size) { ArrayList<Aviso>() }
        val sueltos = ArrayList<Aviso>()
        var j = 0
        for (aviso in avisos) {
            while (j < notas.size && notas[j].posicionM < aviso.posicionM) j++
            if (j < notas.size && seUne(notas, j, aviso)) unidos[j].add(aviso) else sueltos.add(aviso)
        }
        val deNotas = notas.mapIndexed { i, n -> ElementoHoja.DeNota(n, unidos[i]) }
        val deAvisos = sueltos.map { ElementoHoja.DeAviso(it) }
        return (deAvisos + deNotas).sortedWith(
            compareBy<ElementoHoja> { it.posicionM }.thenBy { it is ElementoHoja.DeNota },
        )
    }

    private fun seUne(
        notas: List<Nota>,
        j: Int,
        aviso: Aviso,
    ): Boolean = notas[j].posicionM - aviso.posicionM < UNION_MAX_M && (j == 0 || !notas[j - 1].unidaConSiguiente)
}

/**
 * Marcas de precaución («cuidado,») de las notas; generosas a propósito. Una nota la lleva si: es de grado 1–2 y
 * cierra; es de grado 1–3 (o horquilla) con rasante o salto en los 60 m anteriores; es 2 o más grados más cerrada
 * que la nota anterior si esta queda a menos de 150 m; es una horquilla tras más de 300 m de recta; o hay un
 * `hazard:curve` a 30 m o menos de la curva.
 */
internal object Precaucion {
    const val RASANTE_ANTES_M = 60.0
    const val SALTO_DISTANCIA_M = 150.0
    const val SALTO_GRADOS = 2
    const val RECTA_ANTES_HORQUILLA_M = 300.0
    const val PELIGRO_MAX_M = 30.0

    /** [notas] con la marca [Nota.cuidado] puesta donde toca. */
    fun marcar(
        notas: List<Nota>,
        avisos: List<Aviso>,
        curvasPeligrosasM: List<Double>,
    ): List<Nota> =
        notas.mapIndexed { i, nota ->
            val previa = notas.getOrNull(i - 1)
            val motivo =
                nota.cuidado ||
                    cierraFuerte(nota) ||
                    rasanteAntes(nota, avisos) ||
                    saltoDeGrado(previa, nota) ||
                    horquillaTrasRecta(previa, nota) ||
                    curvasPeligrosasM.any { distanciaACurva(it, nota) <= PELIGRO_MAX_M }
            if (motivo) nota.copy(cuidado = true) else nota
        }

    /** Posiciones de `hazard:curve` que no caen a [PELIGRO_MAX_M] o menos de ninguna nota. */
    fun curvasSinNota(
        notas: List<Nota>,
        curvasPeligrosasM: List<Double>,
    ): List<Double> = curvasPeligrosasM.filter { p -> notas.none { distanciaACurva(p, it) <= PELIGRO_MAX_M } }

    /** Grado para comparar: la horquilla es más cerrada que el grado 1. */
    private fun efectivo(nota: Nota): Int = nota.grado ?: 0

    private fun cierraFuerte(nota: Nota): Boolean = efectivo(nota) <= 2 && Modificador.CIERRA in nota.modificadores

    private fun rasanteAntes(
        nota: Nota,
        avisos: List<Aviso>,
    ): Boolean =
        efectivo(nota) <= 3 &&
            avisos.any {
                (it.tipo == TipoAviso.RASANTE || it.tipo == TipoAviso.SALTO) &&
                    it.posicionM <= nota.posicionM &&
                    nota.posicionM - it.posicionM <= RASANTE_ANTES_M
            }

    private fun saltoDeGrado(
        previa: Nota?,
        nota: Nota,
    ): Boolean =
        previa != null &&
            nota.posicionM - previa.finM < SALTO_DISTANCIA_M &&
            efectivo(previa) - efectivo(nota) >= SALTO_GRADOS

    private fun horquillaTrasRecta(
        previa: Nota?,
        nota: Nota,
    ): Boolean = nota.grado == null && nota.posicionM - (previa?.finM ?: 0.0) > RECTA_ANTES_HORQUILLA_M

    private fun distanciaACurva(
        posicionM: Double,
        nota: Nota,
    ): Double =
        when {
            posicionM < nota.posicionM -> nota.posicionM - posicionM
            posicionM > nota.finM -> posicionM - nota.finM
            else -> 0.0
        }
}
