package app.trombito.rallycopilot.motor.vias

import app.trombito.rallycopilot.motor.geo.ProyeccionPlana
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.Rumbo

/**
 * Encadena los `way` de OSM de una misma carretera en una sola polilínea representativa (el eje).
 *
 * 1. Cadenas: se parte del `way` más largo y se prolonga por los dos extremos con el `way` libre cuyo extremo esté a
 *    menos de [TOLERANCIA_ENLACE_M] y siga hacia delante (giro de menos de [MAX_GIRO_GRADOS] respecto a la cadena); sin
 *    esa condición una calzada se empalmaría con la contraria, que arranca en el mismo cruce pero vuelve hacia atrás.
 * 2. Calzadas dobles (autovía con un `way` por calzada): se queda la cadena más larga y se descartan las cadenas que
 *    van pegadas a una ya aceptada (el [SOMBRA_FRACCION] de sus puntos a menos de [SOMBRA_DISTANCIA_M]). El eje es,
 *    pues, una de las dos calzadas completas, no una línea media.
 * 3. Huecos: las cadenas que no son sombra de otra (tramos separados por datos que faltan) se unen a la principal por
 *    el extremo más próximo si está a menos de [MAX_HUECO_M]; el resto se descarta.
 */
internal object EncadenadoVias {
    private const val TOLERANCIA_ENLACE_M = 30.0
    private const val MAX_GIRO_GRADOS = 90.0
    private const val VENTANA_RUMBO_M = 15.0
    private const val SOMBRA_DISTANCIA_M = 120.0
    private const val SOMBRA_FRACCION = 0.7
    private const val MAX_MUESTRAS_SOMBRA = 24
    private const val MAX_HUECO_M = 3_000.0
    private const val MISMO_PUNTO_M = 1.0

    /** Latitud de referencia de Mallorca; la longitud de origen da igual en distancias cortas. */
    private val PLANO = ProyeccionPlana(PuntoGeo(39.6, 2.9))

    private class Union(
        val indice: Int,
        val alFinal: Boolean,
        val invertir: Boolean,
        val distanciaM: Double,
    )

    /** Polilínea representativa de [lineas] (cada una con al menos dos puntos); vacía si no hay ninguna. */
    fun eje(lineas: List<List<PuntoGeo>>): List<PuntoGeo> {
        val cadenas = encadenar(lineas).sortedByDescending { largo(it) }
        if (cadenas.isEmpty()) return emptyList()
        val aceptadas = ArrayList<List<PuntoGeo>>()
        for (cadena in cadenas) if (!esSombra(cadena, aceptadas)) aceptadas.add(cadena)
        val principal = ArrayList(aceptadas.first())
        unirHuecos(principal, aceptadas.drop(1).toMutableList())
        return principal
    }

    private fun largo(linea: List<PuntoGeo>): Double = linea.zipWithNext { a, b -> PLANO.distancia(a, b) }.sum()

    private fun encadenar(lineas: List<List<PuntoGeo>>): List<List<PuntoGeo>> {
        val libres = lineas.sortedByDescending { largo(it) }.toMutableList()
        val cadenas = ArrayList<List<PuntoGeo>>()
        while (libres.isNotEmpty()) {
            val cadena = ArrayList(libres.removeAt(0))
            prolongar(cadena, libres)
            cadena.reverse()
            prolongar(cadena, libres)
            cadenas.add(cadena)
        }
        return cadenas
    }

    /** Añade al final de [cadena] los `way` de [libres] que la continúan, hasta que no quede ninguno. */
    private fun prolongar(
        cadena: ArrayList<PuntoGeo>,
        libres: MutableList<List<PuntoGeo>>,
    ) {
        while (true) {
            val fin = cadena.last()
            val rumbo = rumboDeLlegada(cadena)
            var mejor = -1
            var mejorInvertido = false
            var mejorDistancia = TOLERANCIA_ENLACE_M
            libres.forEachIndexed { i, linea ->
                for (invertido in BOOLEANOS) {
                    val inicio = if (invertido) linea.last() else linea.first()
                    val d = PLANO.distancia(fin, inicio)
                    if (d <= mejorDistancia && sigueAdelante(rumbo, linea, invertido)) {
                        mejor = i
                        mejorInvertido = invertido
                        mejorDistancia = d
                    }
                }
            }
            if (mejor < 0) return
            val linea = libres.removeAt(mejor).let { if (mejorInvertido) it.asReversed() else it }
            cadena.addAll(if (mejorDistancia < MISMO_PUNTO_M) linea.drop(1) else linea)
        }
    }

    private val BOOLEANOS = listOf(false, true)

    /** Rumbo con el que la cadena llega a su último punto (null si toda ella cabe en una ventana de 15 m). */
    private fun rumboDeLlegada(cadena: List<PuntoGeo>): Double? {
        val fin = cadena.last()
        val anterior = cadena.asReversed().firstOrNull { PLANO.distancia(it, fin) >= VENTANA_RUMBO_M }
        return anterior?.let { Rumbo.entre(it, fin) }
    }

    private fun sigueAdelante(
        rumbo: Double?,
        linea: List<PuntoGeo>,
        invertida: Boolean,
    ): Boolean {
        val puntos = if (invertida) linea.asReversed() else linea
        val inicio = puntos.first()
        val siguiente = puntos.firstOrNull { PLANO.distancia(inicio, it) >= VENTANA_RUMBO_M }
        if (rumbo == null || siguiente == null) return true
        return Rumbo.diferencia(rumbo, Rumbo.entre(inicio, siguiente)) <= MAX_GIRO_GRADOS
    }

    /** La [cadena] va pegada a alguna de las [aceptadas] (la otra calzada de una vía doble). */
    private fun esSombra(
        cadena: List<PuntoGeo>,
        aceptadas: List<List<PuntoGeo>>,
    ): Boolean {
        if (aceptadas.isEmpty()) return false
        val paso = maxOf(1, cadena.size / MAX_MUESTRAS_SOMBRA)
        val muestras = cadena.filterIndexed { i, _ -> i % paso == 0 }
        val cerca =
            muestras.count { p -> aceptadas.any { PLANO.distanciaAPolilinea(p, it) <= SOMBRA_DISTANCIA_M } }
        return cerca >= SOMBRA_FRACCION * muestras.size
    }

    private fun unirHuecos(
        principal: ArrayList<PuntoGeo>,
        pendientes: MutableList<List<PuntoGeo>>,
    ) {
        while (pendientes.isNotEmpty()) {
            val union =
                pendientes.indices
                    .flatMap { i -> BOOLEANOS.map { alFinal -> union(principal, pendientes[i], i, alFinal) } }
                    .minBy { it.distanciaM }
            if (union.distanciaM > MAX_HUECO_M) return
            val cadena = pendientes.removeAt(union.indice).let { if (union.invertir) it.asReversed() else it }
            if (union.alFinal) principal.addAll(cadena) else principal.addAll(0, cadena)
        }
    }

    /** Cómo pegar [cadena] al final o al principio de [principal] por su extremo más cercano. */
    private fun union(
        principal: List<PuntoGeo>,
        cadena: List<PuntoGeo>,
        indice: Int,
        alFinal: Boolean,
    ): Union {
        val extremo = if (alFinal) principal.last() else principal.first()
        val alInicio = PLANO.distancia(extremo, cadena.first())
        val alFin = PLANO.distancia(extremo, cadena.last())
        // Al final de la principal la cadena debe empezar junto a ella; al principio, acabar junto a ella.
        val invertir = if (alFinal) alFin < alInicio else alInicio < alFin
        return Union(indice, alFinal, invertir, minOf(alInicio, alFin))
    }
}
