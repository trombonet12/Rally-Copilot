package app.trombito.rallycopilot.motor.tramos

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.Rumbo
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/** Subtramo en recorrido: [recorridoM] sobre la traza de [tramoRefId] y [deltaMs] contra su mejor pasada. */
data class SubtramoEnCurso(
    val tramoRefId: String,
    val recorridoM: Double,
    val transcurridoMs: Long,
    val deltaMs: Long?,
)

/**
 * Trozo de un tramo guardado recorrido en su sentido: de [desdeM] a [hastaM] (metros desde la salida de [tramoRefId]),
 * [longitudM] sobre la traza, a partir del instante [inicioFijacionMs] y con [tiempoMs] de duración; [mejorMs] es el
 * mejor tiempo previo en ese mismo trozo entre todos los tramos y pasadas (null si ninguno lo cubre).
 */
data class SubtramoHecho(
    val tramoRefId: String,
    val desdeM: Double,
    val hastaM: Double,
    val longitudM: Double,
    val inicioFijacionMs: Long,
    val tiempoMs: Long,
    val mejorMs: Long?,
    val desde: PuntoGeo,
    val hasta: PuntoGeo,
)

/** Lo ocurrido con una fijación: el subtramo en curso (si lo hay) y los hechos que se acaban de cerrar. */
data class ResultadoSubtramo(
    val enCurso: SubtramoEnCurso?,
    val hechos: List<SubtramoHecho>,
)

/** Rumbo de marcha: el del GPS si se mueve; si no, el de [previa] a [f] (si dista más de 3 m). */
private fun rumboDeMarcha(
    f: Fijacion,
    previa: Fijacion?,
): Double? {
    val enMarcha = (f.velocidadMs ?: Double.MAX_VALUE) >= DetectorSubtramo.VELOCIDAD_MARCHA_MS
    val bruto = f.rumboGrados?.takeIf { it.isFinite() && enMarcha }
    val anterior = previa?.takeIf { it.punto.distanciaA(f.punto) > DISTANCIA_MIN_RUMBO_M }
    return when {
        bruto != null -> Rumbo.normalizar(bruto)
        anterior != null -> Rumbo.entre(anterior.punto, f.punto)
        else -> null
    }
}

private const val DISTANCIA_MIN_RUMBO_M = 3.0

/** Punto y instante (reloj de las fijaciones) de un extremo de trozo. */
private class Extremo(
    val punto: PuntoGeo,
    val ms: Long,
)

/**
 * Cronometra cualquier trozo de [longitudMinM] (1 km) o más de un tramo guardado recorrido en su sentido, entrando y
 * saliendo por cualquier punto, y lo compara con el mejor tiempo previo en ese mismo trozo entre todos los tramos y
 * pasadas. Cada viaje se guarda como un tramo, así que hay cientos: un índice de rejilla (`IndiceTrazas`) reduce cada
 * fijación a los tramos cercanos.
 *
 * - Abrir un candidato: fijación a [RADIO_M] (20 m) o menos de la traza de un tramo y rumbo de marcha (GPS si se
 *   mueve a [VELOCIDAD_MARCHA_MS] o más; si no, respecto a la fijación anterior si dista más de 3 m) a menos de
 *   [ANGULO_GRADOS] (45°) del de la traza. Sentido contrario o sin rumbo no abre. Si la fijación anterior ya estaba
 *   sobre la traza, el trozo empieza en ella.
 *   Un tramo recién cerrado no reabre hasta que la conducción se aparte de su traza.
 * - Seguir: progreso monótono, buscado en una ventana alrededor del último. Se cierra con más de [LATERAL_MAX_M]
 *   (50 m) durante más de [DESVIO_MS] (5 s), con más de [DESVIO_INMEDIATO_M] (150 m) de golpe, al llegar al final de la
 *   traza (instante interpolado) o en [cerrar]; el trozo acaba en la última fijación sobre la traza.
 * - Cerrar: con [longitudMinM] o más recorridos es un trozo; si es la pasada completa (desde [SALIDA_M] o menos y hasta
 *   la meta) no se emite: ya la cronometra [DetectorTramo].
 * - Un trozo de carretera cubierto por varios tramos solapados es un solo hecho: se agrupan los trozos cuyo intervalo
 *   de conducción se solapa [SOLAPE_MIN] (80 %) o más del menor y queda el de más recorrido como `tramoRefId`. El hecho
 *   se emite cuando ningún candidato abierto que empezó antes puede aún unirse al grupo.
 * - [SubtramoEnCurso] solo existe desde [RECORRIDO_EN_CURSO_M] (300 m) recorridos; entre varios, el de más recorrido
 *   con referencia (o, sin ninguna, el de más recorrido con `deltaMs` null).
 *
 * Los tiempos son los del reloj de las fijaciones: sin reloj real ni azar.
 */
class DetectorSubtramo(
    private val tramos: List<Tramo>,
    pasadas: Map<String, List<Pasada>>,
    private val longitudMinM: Double = LONGITUD_MIN_M,
) {
    private class Trozo(
        val tramo: Tramo,
        val desdeM: Double,
        val hastaM: Double,
        val desde: Extremo,
        val hasta: Extremo,
    ) {
        val desdeMs: Long get() = desde.ms
        val hastaMs: Long get() = hasta.ms
        val recorridoM: Double get() = hastaM - desdeM
        val completo: Boolean get() = desdeM <= SALIDA_M && hastaM >= tramo.metaM

        fun solapaCon(otro: Trozo): Boolean {
            val comun = min(hastaMs, otro.hastaMs) - max(desdeMs, otro.desdeMs)
            val menor = min(hastaMs - desdeMs, otro.hastaMs - otro.desdeMs)
            return comun >= 0 && comun >= SOLAPE_MIN * menor
        }
    }

    private class Candidato(
        val indice: Int,
        val tramo: Tramo,
        val traza: TrazaPlana,
        val desdeM: Double,
        val desde: Fijacion,
    ) {
        var ultimoM = desdeM
        var ultima = desde
        var fueraDesdeMs: Long? = null
        val recorridoM: Double get() = ultimoM - desdeM

        fun ventana(tMs: Long): ClosedFloatingPointRange<Double> {
            val segundos = (tMs - ultima.tiempoMs).coerceAtLeast(0L) / MS_POR_SEGUNDO
            return (ultimoM - VENTANA_ATRAS_M)..(ultimoM + VENTANA_ADELANTE_M + VELOCIDAD_MAX_MS * segundos)
        }

        fun avanzar(
            progresoM: Double,
            f: Fijacion,
        ) {
            ultimoM = max(ultimoM, progresoM)
            ultima = f
        }

        /** Delta (ms) de [tMs] contra la mejor pasada que cubra el inicio, o null sin referencia. */
        fun deltaMs(
            comparador: ComparadorSubtramos,
            tMs: Long,
        ): Long? = comparador.tiempoDeReferencia(tramo.id, desdeM, ultimoM)?.let { (tMs - desde.tiempoMs) - it }

        fun aTrozo(): Trozo =
            Trozo(tramo, desdeM, ultimoM, Extremo(desde.punto, desde.tiempoMs), Extremo(ultima.punto, ultima.tiempoMs))

        /** Trozo que acaba en el final de la traza, con el instante interpolado entre la última fijación y [f]. */
        fun aTrozoEnFinal(
            f: Fijacion,
            brutoM: Double,
        ): Trozo {
            val fraccion = if (brutoM > ultimoM) (tramo.longitudM - ultimoM) / (brutoM - ultimoM) else 0.0
            val tMs = ultima.tiempoMs + (fraccion * (f.tiempoMs - ultima.tiempoMs)).roundToLong()
            return Trozo(
                tramo,
                desdeM,
                tramo.longitudM,
                Extremo(desde.punto, desde.tiempoMs),
                Extremo(tramo.traza.last(), tMs),
            )
        }
    }

    private val indice = IndiceTrazas(tramos)
    private val comparador = ComparadorSubtramos(indice, tramos, pasadas)
    private val activos = LinkedHashMap<Int, Candidato>()
    private val bloqueados = HashSet<Int>()
    private val pendientes = mutableListOf<Trozo>()
    private var previa: Fijacion? = null

    init {
        require(tramos.map { it.id }.toSet().size == tramos.size) { "Ids de tramo repetidos" }
    }

    /** Incorpora una fijación (en orden temporal): el subtramo en curso y los hechos cerrados con ella. */
    fun alFijar(f: Fijacion): ResultadoSubtramo {
        val rumbo = rumboDeMarcha(f, previa)
        for (c in activos.values.toList()) seguir(c, f)
        abrir(f, rumbo)
        previa = f
        return ResultadoSubtramo(enCurso(f), emitir(forzar = false))
    }

    /** Fin de la conducción: cierra lo abierto y devuelve los hechos que quedaban. */
    fun cerrar(): List<SubtramoHecho> {
        for (c in activos.values.toList()) cerrarCandidato(c, c.aTrozo())
        bloqueados.clear()
        previa = null
        return emitir(forzar = true)
    }

    private fun seguir(
        c: Candidato,
        f: Fijacion,
    ) {
        val p = c.traza.proyectar(f.punto, c.ventana(f.tiempoMs))
        val lateral = p?.lateralM ?: Double.POSITIVE_INFINITY
        if (p != null && lateral <= LATERAL_MAX_M) {
            c.fueraDesdeMs = null
            if (p.progresoM >= c.tramo.longitudM) {
                cerrarCandidato(c, c.aTrozoEnFinal(f, p.progresoM))
            } else {
                c.avanzar(p.progresoM, f)
            }
        } else {
            val fuera = c.fueraDesdeMs ?: f.tiempoMs
            c.fueraDesdeMs = fuera
            if (lateral > DESVIO_INMEDIATO_M || f.tiempoMs - fuera > DESVIO_MS) cerrarCandidato(c, c.aTrozo())
        }
    }

    private fun cerrarCandidato(
        c: Candidato,
        trozo: Trozo,
    ) {
        activos.remove(c.indice)
        bloqueados += c.indice
        if (trozo.recorridoM >= longitudMinM && !trozo.completo) pendientes += trozo
    }

    private fun abrir(
        f: Fijacion,
        rumbo: Double?,
    ) {
        val cercanos = indice.cercanos(f.punto)
        bloqueados.retainAll(cercanos)
        for (i in cercanos) if (i !in activos) tratar(i, f, rumbo)
    }

    private fun tratar(
        i: Int,
        f: Fijacion,
        rumbo: Double?,
    ) {
        val traza = indice.traza(i)
        val p = traza.proyectar(f.punto, 0.0..tramos[i].longitudM)
        if (p == null || p.lateralM > RADIO_M) {
            bloqueados.remove(i)
        } else if (i !in bloqueados && rumbo != null) {
            val rumboTraza = traza.rumboEn(p.progresoM, SEMIVENTANA_RUMBO_M)
            if (Rumbo.diferencia(rumbo, rumboTraza) < ANGULO_GRADOS) activos[i] = nuevoCandidato(i, f, p.progresoM)
        }
    }

    private fun nuevoCandidato(
        i: Int,
        f: Fijacion,
        progresoM: Double,
    ): Candidato {
        val traza = indice.traza(i)
        val anterior = previa
        val ventana = max(0.0, progresoM - VENTANA_ATRAS_APERTURA_M)..progresoM
        val pAnterior = anterior?.let { traza.proyectar(it.punto, ventana) }
        return if (anterior != null && pAnterior != null && pAnterior.lateralM <= RADIO_M) {
            Candidato(i, tramos[i], traza, pAnterior.progresoM, anterior).also { it.avanzar(progresoM, f) }
        } else {
            Candidato(i, tramos[i], traza, progresoM, f)
        }
    }

    private fun enCurso(f: Fijacion): SubtramoEnCurso? {
        val largos = activos.values.filter { it.recorridoM >= RECORRIDO_EN_CURSO_M }
        val conDelta = largos.map { Pair(it, it.deltaMs(comparador, f.tiempoMs)) }
        val elegido =
            conDelta.filter { it.second != null }.maxByOrNull { it.first.recorridoM }
                ?: conDelta.maxByOrNull { it.first.recorridoM }
        return elegido?.let { (c, delta) ->
            SubtramoEnCurso(c.tramo.id, c.recorridoM, f.tiempoMs - c.desde.tiempoMs, delta)
        }
    }

    /** Agrupa los trozos pendientes (el de más recorrido lidera) y emite los grupos que ya no pueden crecer. */
    private fun emitir(forzar: Boolean): List<SubtramoHecho> {
        val hechos = mutableListOf<SubtramoHecho>()
        val resto = pendientes.sortedByDescending { it.recorridoM }.toMutableList()
        while (resto.isNotEmpty()) {
            val lider = resto.removeAt(0)
            val grupo = resto.filter { lider.solapaCon(it) }
            resto.removeAll(grupo.toSet())
            val finMs = (grupo + lider).maxOf { it.hastaMs }
            if (forzar || activos.values.none { it.desde.tiempoMs < finMs }) {
                pendientes.removeAll((grupo + lider).toSet())
                hechos += hechoDe(lider)
            }
        }
        return hechos.sortedBy { it.inicioFijacionMs }
    }

    private fun hechoDe(t: Trozo): SubtramoHecho =
        SubtramoHecho(
            tramoRefId = t.tramo.id,
            desdeM = t.desdeM,
            hastaM = t.hastaM,
            longitudM = t.recorridoM,
            inicioFijacionMs = t.desdeMs,
            tiempoMs = t.hastaMs - t.desdeMs,
            mejorMs = comparador.mejorMs(t.desde.punto, t.hasta.punto, t.recorridoM),
            desde = t.desde.punto,
            hasta = t.hasta.punto,
        )

    companion object {
        /** Longitud mínima (m) de un trozo cronometrado. */
        const val LONGITUD_MIN_M = 1000.0

        /** Distancia (m) a la traza para abrir un candidato y para dar por «mismo sitio» un punto en otro tramo. */
        const val RADIO_M = 20.0

        /** Diferencia máxima (grados) entre el rumbo de marcha y el de la traza. */
        const val ANGULO_GRADOS = 45.0

        /** Distancia lateral (m) a partir de la cual se está fuera de la traza. */
        const val LATERAL_MAX_M = 50.0

        /** Tiempo (ms) fuera de la traza a partir del cual se cierra el candidato. */
        const val DESVIO_MS = 5_000L

        /** Distancia lateral (m) a la que se cierra de inmediato. */
        const val DESVIO_INMEDIATO_M = 150.0

        /** Solape mínimo (fracción del intervalo menor) para agrupar trozos de tramos distintos. */
        const val SOLAPE_MIN = 0.8

        /** Recorrido (m) desde el que hay subtramo en curso y delta en vivo. */
        const val RECORRIDO_EN_CURSO_M = 300.0

        /** Salida (m desde el inicio) hasta la que un trozo cuenta como empezado en la salida del tramo. */
        const val SALIDA_M = 30.0

        /** Velocidad (m/s) desde la que se fía del rumbo del GPS. */
        const val VELOCIDAD_MARCHA_MS = 1.5

        private const val SEMIVENTANA_RUMBO_M = 10.0
        private const val VENTANA_ATRAS_M = 30.0
        private const val VENTANA_ADELANTE_M = 150.0
        private const val VELOCIDAD_MAX_MS = 70.0
        private const val VENTANA_ATRAS_APERTURA_M = 200.0
        private const val MS_POR_SEGUNDO = 1000.0
    }
}
