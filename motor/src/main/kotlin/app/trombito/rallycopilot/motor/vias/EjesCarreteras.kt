package app.trombito.rallycopilot.motor.vias

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.TrazaMetros

/**
 * Índice de ejes lineales por carretera (ver [EjeCarretera]) construido de las [vias] de OSM y, si los hay, de los
 * hitos kilométricos [hitos]. Todo es perezoso: agrupar las vías por `ref` se hace en la primera consulta y el eje de
 * cada carretera la primera vez que se pide (después se reutiliza), así que consultar pocas carreteras es barato aunque
 * haya miles de vías. Seguro entre hilos.
 *
 * Heurísticas (se documentan aquí porque deciden los PK de todos los tramos):
 * - Un eje por `ref` normalizada (primera ref de la vía; `Ma-13A` no es `Ma-13`) con todos sus `way`, salvo rotondas y
 *   enlaces (`*_link`). Encadenado y calzadas dobles: ver [EncadenadoVias]; con dos calzadas el eje es una de ellas.
 * - Origen con al menos dos hitos de esa ref a menos de 300 m del eje: el extremo donde el km es menor (sentido en el
 *   que crece la tendencia de los hitos). Sin ellos, el extremo más cercano a Palma: en Mallorca las Ma-1x y Ma-xx
 *   salen de Palma por convención (las carreteras entre pueblos no cumplen, y su PK es solo una estimación).
 */
class EjesCarreteras(
    private val vias: List<Via>,
    private val hitos: IndicePuntosKm? = null,
) {
    private class Entrada(
        construir: () -> EjeCarretera?,
    ) {
        val eje: EjeCarretera? by lazy(construir)
    }

    private val porRef: Map<String, Entrada> by lazy {
        vias
            .filter { !it.esRotonda && !it.clase.endsWith("_link") && it.puntos.size >= 2 }
            .mapNotNull { via -> primeraRef(via.ref)?.let { ref -> ref to via } }
            .groupBy({ IndicePuntosKm.normalizar(it.first) }, { it })
            .filterKeys { it.isNotEmpty() }
            .mapValues { (_, pares) ->
                val ref = pares.first().first
                val deRef = pares.map { it.second }
                Entrada { construir(ref, deRef) }
            }
    }

    /** Eje de la carretera [ref] (la primera si trae varias); null si ninguna vía la lleva. */
    fun de(ref: String?): EjeCarretera? = primeraRef(ref)?.let { porRef[IndicePuntosKm.normalizar(it)]?.eje }

    private fun construir(
        ref: String,
        deRef: List<Via>,
    ): EjeCarretera? {
        val base = EncadenadoVias.eje(deRef.map { it.puntos })
        if (base.size < 2) return null
        val calibrado = calibrarConHitos(base, hitos?.hitosDe(ref).orEmpty())
        val (puntos, anclas) = calibrado ?: (orientarHaciaPalma(base) to emptyList())
        return EjeCarretera(ref, puntos, anclas)
    }

    /** Eje orientado con el km creciente y sus anclas; null si no hay al menos dos hitos coherentes. */
    private fun calibrarConHitos(
        base: List<PuntoGeo>,
        hitosRef: List<HitoKm>,
    ): Pair<List<PuntoGeo>, List<AnclaKm>>? {
        val traza = TrazaMetros(base)
        val anclas = hitosRef.mapNotNull { anclaDe(traza, it) }
        val largo = traza.longitudM
        // La orientación es la que deja más hitos con el km creciente: un hito mal ubicado no la voltea.
        val directas = coherentes(anclas)
        val inversas = coherentes(anclas.map { AnclaKm(largo - it.posM, it.km) })
        val invertir = inversas.size > directas.size
        val elegidas = if (invertir) inversas else directas
        return if (elegidas.size < 2) null else (if (invertir) base.asReversed() else base) to elegidas
    }

    private fun anclaDe(
        traza: TrazaMetros,
        hito: HitoKm,
    ): AnclaKm? =
        traza
            .proyectar(hito.punto)
            ?.takeIf { it.distanciaM <= MAX_DISTANCIA_HITO_M }
            ?.let { AnclaKm(it.metros, hito.km) }

    /** La subsucesión más larga con posición y km estrictamente crecientes (descarta hitos mal ubicados). */
    private fun coherentes(anclas: List<AnclaKm>): List<AnclaKm> {
        val orden = anclas.sortedWith(compareBy({ it.posM }, { it.km }))
        val longitud = IntArray(orden.size) { 1 }
        val previo = IntArray(orden.size) { -1 }
        for (i in orden.indices) {
            for (j in 0 until i) {
                if (orden[j].posM < orden[i].posM && orden[j].km < orden[i].km && longitud[j] + 1 > longitud[i]) {
                    longitud[i] = longitud[j] + 1
                    previo[i] = j
                }
            }
        }
        var fin = longitud.indices.maxByOrNull { longitud[it] } ?: return emptyList()
        val resultado = ArrayList<AnclaKm>()
        while (fin >= 0) {
            resultado.add(orden[fin])
            fin = previo[fin]
        }
        return resultado.asReversed()
    }

    private fun orientarHaciaPalma(base: List<PuntoGeo>): List<PuntoGeo> =
        if (base.last().distanciaA(PALMA) < base.first().distanciaA(PALMA)) base.asReversed() else base

    companion object {
        /** Centro de Palma: el origen del PK de las carreteras sin hitos. */
        val PALMA = PuntoGeo(39.5696, 2.6502)

        private const val MAX_DISTANCIA_HITO_M = 300.0
    }
}
