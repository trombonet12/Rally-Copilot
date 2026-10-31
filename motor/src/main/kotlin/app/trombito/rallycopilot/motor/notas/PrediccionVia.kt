package app.trombito.rallycopilot.motor.notas

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.vias.GrafoVias
import app.trombito.rallycopilot.motor.vias.SentidoUnico
import app.trombito.rallycopilot.motor.vias.Via
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos

/**
 * Carretera prevista por delante: `puntos` (desde la posición actual), si se cortó en un cruce donde no se puede
 * saber por dónde seguirá el conductor, y los `ref` distintos recorridos en orden.
 */
data class Prediccion(
    val puntos: List<PuntoGeo>,
    val cortadaEnCruceDudoso: Boolean,
    val refs: List<String>,
)

/**
 * Predice la carretera que viene en modo libre (sin destino) recorriendo el [GrafoVias] en el sentido de la marcha.
 * En cada nodo con varias salidas (respetando `sentidoUnico` y sin dar la vuelta) elige la que mantiene el último
 * `ref` no vacío; si no, la de menor giro, y corta si la segunda mejor está a menos de 30° de giro de la mejor.
 */
class PrediccionVia(
    private val grafo: GrafoVias,
) {
    /**
     * Predice hasta [maxM] metros desde [posicionEnViaM] (metros desde el primer punto de [via]); [adelante] indica
     * si se marcha en el sentido de dibujo de la vía.
     */
    fun predecir(
        via: Via,
        posicionEnViaM: Double,
        adelante: Boolean,
        maxM: Double = 2000.0,
    ): Prediccion {
        require(maxM >= 0.0) { "maxM no puede ser negativo: $maxM" }
        return Marcha(grafo, via, adelante, maxM).recorrer(posicionEnViaM)
    }

    private class Salida(
        val via: Via,
        val indice: Int,
        val sentido: Int,
    )

    private class Giro(
        val salida: Salida,
        val grados: Double,
    )

    private class Marcha(
        private val grafo: GrafoVias,
        via: Via,
        adelante: Boolean,
        private var restanteM: Double,
    ) {
        private var actual = via
        private var sentido = if (adelante) 1 else -1
        private var siguiente = 0
        private var refActual = refDe(via)
        private val puntos = ArrayList<PuntoGeo>()
        private val refs = ArrayList<String>()
        private var dudoso = false

        fun recorrer(posicionM: Double): Prediccion {
            refActual?.let { refs.add(it) }
            if (actual.puntos.size >= 2) {
                arrancar(posicionM)
                var pasos = 0
                while (pasos++ < MAX_PASOS && pasoAlSiguienteNodo()) Unit
            }
            return Prediccion(puntos, dudoso, refs)
        }

        private fun arrancar(posicionM: Double) {
            val pts = actual.puntos
            val objetivo = posicionM.coerceAtLeast(0.0)
            var acumulada = 0.0
            var i = 0
            while (i < pts.size - 2 && acumulada + pts[i].distanciaA(pts[i + 1]) <= objetivo) {
                acumulada += pts[i].distanciaA(pts[i + 1])
                i++
            }
            val largo = pts[i].distanciaA(pts[i + 1])
            val t = if (largo > 0.0) ((objetivo - acumulada) / largo).coerceIn(0.0, 1.0) else 0.0
            puntos.add(interpolar(pts[i], pts[i + 1], t))
            siguiente = if (sentido > 0) i + 1 else i
        }

        /** Avanza hasta el siguiente punto de la vía; devuelve false si la predicción termina. */
        private fun pasoAlSiguienteNodo(): Boolean {
            val destino = actual.puntos[siguiente]
            val largo = puntos.last().distanciaA(destino)
            if (largo >= restanteM) {
                if (restanteM > 0.0 && largo > 0.0) puntos.add(interpolar(puntos.last(), destino, restanteM / largo))
                return false
            }
            puntos.add(destino)
            restanteM -= largo
            val salidas = salidasDelNodo(destino)
            val elegida = if (salidas.size > 1) elegir(destino, salidas) else salidas.firstOrNull()
            elegida?.let { adoptar(it) }
            return elegida != null
        }

        private fun salidasDelNodo(nodo: PuntoGeo): List<Salida> {
            val previo = actual.puntos[siguiente - sentido]
            val lista = ArrayList<Salida>()
            anadir(lista, Salida(actual, siguiente, sentido), previo)
            for (arista in grafo.aristas(nodo)) {
                if (arista.via.id == actual.id && arista.indice == siguiente) continue
                anadir(lista, Salida(arista.via, arista.indice, 1), previo)
                anadir(lista, Salida(arista.via, arista.indice, -1), previo)
            }
            return lista
        }

        private fun anadir(
            lista: MutableList<Salida>,
            salida: Salida,
            previo: PuntoGeo,
        ) {
            val destino = salida.indice + salida.sentido
            val permitido =
                if (salida.sentido > 0) {
                    salida.via.sentidoUnico != SentidoUnico.ATRAS
                } else {
                    salida.via.sentidoUnico != SentidoUnico.ADELANTE
                }
            if (permitido && destino in salida.via.puntos.indices && !mismoPunto(salida.via.puntos[destino], previo)) {
                lista.add(salida)
            }
        }

        private fun elegir(
            nodo: PuntoGeo,
            salidas: List<Salida>,
        ): Salida? {
            val rumboEntrada = rumbo(puntoLejano(actual, siguiente, -sentido), nodo)
            val giros = salidas.map { Giro(it, giroAbsoluto(nodo, rumboEntrada, it)) }.sortedWith(ORDEN_GIROS)
            val mismaRef = giros.firstOrNull { refActual != null && refDe(it.salida.via) == refActual }
            val elegida =
                when {
                    mismaRef != null -> mismaRef.salida
                    giros[1].grados - giros[0].grados < UMBRAL_DUDOSO_GRADOS -> null
                    else -> giros[0].salida
                }
            if (elegida == null) dudoso = true
            return elegida
        }

        private fun giroAbsoluto(
            nodo: PuntoGeo,
            rumboEntrada: Double,
            salida: Salida,
        ): Double = abs(normalizar(rumbo(nodo, puntoLejano(salida.via, salida.indice, salida.sentido)) - rumboEntrada))

        private fun adoptar(salida: Salida) {
            actual = salida.via
            sentido = salida.sentido
            siguiente = salida.indice + salida.sentido
            val ref = refDe(actual)
            if (ref != null) {
                refActual = ref
                if (refs.lastOrNull() != ref) refs.add(ref)
            }
        }
    }

    private companion object {
        const val MAX_PASOS = 200_000
        const val UMBRAL_DUDOSO_GRADOS = 30.0
        const val DISTANCIA_RUMBO_M = 10.0
        const val GRADOS_VUELTA = 360.0
        const val GRADOS_MEDIA_VUELTA = 180.0

        val ORDEN_GIROS =
            compareBy<Giro>({ it.grados }, { it.salida.via.id }, { it.salida.indice }, { it.salida.sentido })

        fun refDe(via: Via): String? = via.ref?.trim()?.takeIf { it.isNotEmpty() }

        fun mismoPunto(
            a: PuntoGeo,
            b: PuntoGeo,
        ): Boolean = a.lat == b.lat && a.lon == b.lon

        fun interpolar(
            a: PuntoGeo,
            b: PuntoGeo,
            t: Double,
        ): PuntoGeo = PuntoGeo(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)

        /** Primer punto de la vía a [DISTANCIA_RUMBO_M] o más desde `indice` en `sentido` (o el extremo de la vía). */
        fun puntoLejano(
            via: Via,
            indice: Int,
            sentido: Int,
        ): PuntoGeo {
            val inicio = via.puntos[indice]
            var i = indice
            while (i + sentido in via.puntos.indices &&
                inicio.distanciaA(via.puntos[i]) < DISTANCIA_RUMBO_M
            ) {
                i += sentido
            }
            return via.puntos[i]
        }

        /** Rumbo de a a b en grados (0 norte, horario), con proyección local equirectangular. */
        fun rumbo(
            a: PuntoGeo,
            b: PuntoGeo,
        ): Double = Math.toDegrees(atan2((b.lon - a.lon) * cos(Math.toRadians(a.lat)), b.lat - a.lat))

        fun normalizar(grados: Double): Double {
            var g = grados % GRADOS_VUELTA
            if (g > GRADOS_MEDIA_VUELTA) g -= GRADOS_VUELTA
            if (g <= -GRADOS_MEDIA_VUELTA) g += GRADOS_VUELTA
            return g
        }
    }
}
