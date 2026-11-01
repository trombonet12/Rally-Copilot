package app.trombito.rallycopilot.motor.notas

import app.trombito.rallycopilot.motor.geo.PuntoGeo

/** Elemento de la hoja de notas: una nota de curva o un aviso suelto, con la frase que se canta. */
sealed interface ElementoHoja {
    val posicionM: Double
    val frase: String

    /**
     * Nota de curva con los [avisosUnidos] (los que caen a menos de 20 m antes de ella), que se cantan en la misma
     * frase: «rasante, derecha tres». El «cuidado» (de la nota o de un aviso de precaución) va siempre primero:
     * «cuidado, rasante, horquilla izquierda». [sobrePuente] es el puente que cae dentro de la curva: se canta al
     * final de la nota («derecha tres sobre puente, cincuenta»).
     */
    data class DeNota(
        val nota: Nota,
        val avisosUnidos: List<Aviso> = emptyList(),
        val sobrePuente: Aviso? = null,
    ) : ElementoHoja {
        override val posicionM: Double get() = nota.posicionM
        override val frase: String get() = frase(simplificada = false)

        /** Frase de la nota con sus avisos; con [simplificada] el grado va en palabra simplificada. */
        fun frase(simplificada: Boolean): String {
            val cuidado = nota.cuidado || avisosUnidos.any { it.tipo.esCuidado }
            val previos = avisosUnidos.map { it.tipo.palabra }.filter { it.isNotEmpty() }
            val partes = (if (cuidado) listOf("cuidado") else emptyList()) + previos + cuerpo(simplificada)
            return partes.joinToString(", ")
        }

        private fun cuerpo(simplificada: Boolean): String {
            val base = FrasesNota.frase(nota.copy(cuidado = false, distanciaSiguienteM = null), simplificada)
            val conPuente = if (sobrePuente != null) "$base sobre puente" else base
            return nota.distanciaSiguienteM?.let { "$conPuente, ${FrasesNota.enPalabras(it)}" } ?: conPuente
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

    /** Todos los avisos, sueltos, unidos a una nota o «sobre puente», en orden. */
    val avisos: List<Aviso>
        get() =
            elementos
                .flatMap {
                    when (it) {
                        is ElementoHoja.DeNota -> it.avisosUnidos + listOfNotNull(it.sobrePuente)
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
    /**
     * Hoja de [ruta]; [vRefKmh] da la velocidad de referencia en una posición (ver [GeneradorNotas.generar]).
     * Las curvas cuya referencia al inicio es [sinCurvasHastaKmh] o menos (calles) no generan nota; se descartan
     * antes de generar las notas, así que la distancia y la «y» de la nota anterior se calculan sin ellas. Los
     * avisos, rasantes y «cuidado» siguen saliendo. Sin referencia (`null`) no se descarta nada.
     */
    fun generar(
        ruta: List<PuntoGeo>,
        vRefKmh: (Double) -> Double? = { null },
        sinCurvasHastaKmh: Double = SIN_CURVAS_HASTA_KMH,
    ): HojaNotas {
        val curvas =
            DetectorCurvas().detectar(ruta).filterNot {
                (vRefKmh(it.inicioM) ?: Double.MAX_VALUE) <=
                    sinCurvasHastaKmh
            }
        val notas = GeneradorNotas(tabla).generar(curvas, vRefKmh)
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

    companion object {
        /** Velocidad de referencia (km/h) hasta la cual una curva se considera de calle y no se canta. */
        const val SIN_CURVAS_HASTA_KMH = 30.0
    }
}

/** Une los avisos con las notas en una lista ordenada de elementos. */
internal object Ensamblado {
    /** Distancia máxima antes de una nota para que un aviso se cante junto a ella. */
    const val UNION_MAX_M = 20.0

    /**
     * Elementos de la hoja. Un aviso a menos de [UNION_MAX_M] antes de una nota se une a ella, también si es
     * continuación de una cadena «x y y» (queda dentro de la cadena), salvo un cambio de rasante, que en ese caso se
     * descarta. Un puente que cae dentro de una curva se canta «sobre puente» al final de su nota. El resto va suelto.
     * [avisos] debe venir ordenada por posición.
     */
    fun unir(
        notas: List<Nota>,
        avisos: List<Aviso>,
    ): List<ElementoHoja> {
        val unidos = List(notas.size) { ArrayList<Aviso>() }
        val sobrePuente = arrayOfNulls<Aviso>(notas.size)
        val sueltos = ArrayList<Aviso>()
        var j = 0
        for (aviso in avisos) {
            while (j < notas.size && notas[j].posicionM < aviso.posicionM) j++
            val dentro = if (aviso.tipo == TipoAviso.PUENTE) dentroDe(notas, j - 1, aviso) else false
            val cadena = j > 0 && notas[j - 1].unidaConSiguiente
            when {
                dentro -> {
                    if (sobrePuente[j - 1] == null) sobrePuente[j - 1] = aviso
                }

                j < notas.size && notas[j].posicionM - aviso.posicionM < UNION_MAX_M -> {
                    if (!cadena || aviso.tipo != TipoAviso.CAMBIO_RASANTE) unidos[j].add(aviso)
                }

                else -> {
                    sueltos.add(aviso)
                }
            }
        }
        val deNotas = notas.mapIndexed { i, n -> ElementoHoja.DeNota(n, unidos[i], sobrePuente[i]) }
        val deAvisos = sueltos.map { ElementoHoja.DeAviso(it) }
        return (deAvisos + deNotas).sortedWith(
            compareBy<ElementoHoja> { it.posicionM }.thenBy { it is ElementoHoja.DeNota },
        )
    }

    private fun dentroDe(
        notas: List<Nota>,
        i: Int,
        aviso: Aviso,
    ): Boolean = i >= 0 && aviso.posicionM >= notas[i].posicionM && aviso.posicionM <= notas[i].finM
}

/**
 * Marcas de precaución («cuidado,») de las notas; generosas a propósito. Una nota la lleva si: es de grado 1–2 y
 * cierra; es de grado 1–3 (o horquilla) con rasante, salto o cambio de rasante en los 60 m anteriores; es 2 o más
 * grados más cerrada que la nota anterior si esta queda a menos de 150 m (y no hubo otra horquilla a menos de
 * 400 m); es una horquilla tras más de 300 m de recta; o hay un `hazard:curve` a 30 m o menos de la curva.
 */
internal object Precaucion {
    const val RASANTE_ANTES_M = 60.0
    const val SALTO_DISTANCIA_M = 150.0
    const val SALTO_GRADOS = 2
    const val RECTA_ANTES_HORQUILLA_M = 300.0
    const val PELIGRO_MAX_M = 30.0
    const val HORQUILLA_RECIENTE_M = 400.0

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
                    (saltoDeGrado(previa, nota) && !horquillaReciente(notas, i)) ||
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
                (it.tipo in RASANTES) &&
                    it.posicionM <= nota.posicionM &&
                    nota.posicionM - it.posicionM <= RASANTE_ANTES_M
            }

    private val RASANTES = setOf(TipoAviso.RASANTE, TipoAviso.SALTO, TipoAviso.CAMBIO_RASANTE)

    /** Hubo otra horquilla a menos de [HORQUILLA_RECIENTE_M] antes de la nota [i]. */
    private fun horquillaReciente(
        notas: List<Nota>,
        i: Int,
    ): Boolean =
        (0 until i).any { notas[it].grado == null && notas[i].posicionM - notas[it].finM < HORQUILLA_RECIENTE_M }

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
