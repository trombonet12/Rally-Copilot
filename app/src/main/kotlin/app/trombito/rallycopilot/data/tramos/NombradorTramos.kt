package app.trombito.rallycopilot.data.tramos

import android.database.sqlite.SQLiteException
import app.trombito.rallycopilot.data.vias.FuenteVias
import app.trombito.rallycopilot.domain.busqueda.Buscador
import app.trombito.rallycopilot.domain.tramos.NombraTraza
import app.trombito.rallycopilot.motor.analisis.NombreTramo
import app.trombito.rallycopilot.motor.analisis.PasoVia
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.longitudMetros
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos

/**
 * Nombre automático de un tramo: mira las vías de OSM por las que pasa la traza en [MUESTRAS] puntos repartidos, busca
 * en el índice de búsqueda el municipio de sus calles con más recorrido y compone el texto con [NombreTramo].
 * Todo es opcional: sin vías ni índice devuelve null (el tramo queda sin nombre automático). Nunca falla.
 */
@Singleton
class NombradorTramos
    @Inject
    constructor(
        private val vias: FuenteVias,
        private val buscador: Buscador,
    ) : NombraTraza {
        /** Un trozo seguido de traza sobre la misma vía y los metros que recorre. */
        private class Trozo(
            val ref: String?,
            val nombre: String?,
            var metros: Double,
        )

        override suspend fun nombrar(traza: List<PuntoGeo>): String? {
            val muestras = MuestreoTraza.puntos(traza, MUESTRAS)
            val trozos = if (muestras.isEmpty()) emptyList() else trozosPorVias(muestras, traza.longitudMetros())
            return if (trozos.isEmpty()) {
                null
            } else {
                val municipios = municipiosPorCalle(trozos, muestras)
                NombreTramo.de(trozos.map { PasoVia(it.ref, it.nombre, municipios[it.nombre]) })
            }
        }

        private suspend fun trozosPorVias(
            muestras: List<MuestreoTraza.Muestra>,
            largoM: Double,
        ): List<Trozo> {
            val indice = vias.datos()?.indice ?: return emptyList()
            val metros = largoM / muestras.size
            val trozos = ArrayList<Trozo>()
            for (m in muestras) {
                val via = indice.cercana(m.punto, m.rumboGrados, RADIO_VIA_M)?.via ?: continue
                val ultimo = trozos.lastOrNull()
                if (ultimo != null && ultimo.ref == via.ref && ultimo.nombre == via.nombre) {
                    ultimo.metros += metros
                } else {
                    trozos += Trozo(via.ref, via.nombre, metros)
                }
            }
            return trozos
        }

        /** Municipio de cada una de las [CALLES_A_PROBAR] calles con más recorrido (las demás quedan sin él). */
        private suspend fun municipiosPorCalle(
            trozos: List<Trozo>,
            muestras: List<MuestreoTraza.Muestra>,
        ): Map<String?, String> {
            val centro = muestras[muestras.size / 2].punto
            val calles =
                trozos
                    .filter { !it.nombre.isNullOrBlank() }
                    .groupBy { it.nombre }
                    .entries
                    .sortedByDescending { (_, grupo) -> grupo.sumOf { it.metros } }
                    .mapNotNull { it.key }
                    .take(CALLES_A_PROBAR)
            val resultado = HashMap<String?, String>()
            for (calle in calles) municipioDeCalle(calle, centro, muestras)?.let { resultado[calle] = it }
            return resultado
        }

        private suspend fun municipioDeCalle(
            calle: String,
            centro: PuntoGeo,
            muestras: List<MuestreoTraza.Muestra>,
        ): String? {
            val resultado =
                try {
                    buscador.buscar(calle, centro, LIMITE_BUSQUEDA)
                } catch (_: SQLiteException) {
                    emptyList()
                } catch (_: IllegalStateException) {
                    emptyList()
                }
            return resultado
                .firstOrNull { r ->
                    r.municipio != null && muestras.any { it.punto.distanciaA(r.punto) <= RADIO_MUNICIPIO_M }
                }?.municipio
        }

        private companion object {
            const val MUESTRAS = 20
            const val RADIO_VIA_M = 30.0
            const val CALLES_A_PROBAR = 4
            const val LIMITE_BUSQUEDA = 5
            const val RADIO_MUNICIPIO_M = 300.0
        }
    }

/** Reparto de puntos a lo largo de una traza, con el rumbo del tramo de traza en que cae cada uno. */
internal object MuestreoTraza {
    class Muestra(
        val punto: PuntoGeo,
        val rumboGrados: Double,
    )

    /** [n] puntos a mitad de otros tantos trozos iguales de la traza (en orden); vacío si la traza no mide nada. */
    fun puntos(
        traza: List<PuntoGeo>,
        n: Int,
    ): List<Muestra> {
        require(n > 0) { "n debe ser positivo: $n" }
        val total = traza.longitudMetros()
        if (traza.size < 2 || total <= 0.0) return emptyList()
        val resultado = ArrayList<Muestra>(n)
        var segmento = 0
        var antesM = 0.0
        for (i in 0 until n) {
            val objetivo = total * (i + 0.5) / n
            while (segmento < traza.size - 2 && antesM + traza[segmento].distanciaA(traza[segmento + 1]) < objetivo) {
                antesM += traza[segmento].distanciaA(traza[segmento + 1])
                segmento++
            }
            val a = traza[segmento]
            val b = traza[segmento + 1]
            val largo = a.distanciaA(b)
            val t = if (largo > 0.0) ((objetivo - antesM) / largo).coerceIn(0.0, 1.0) else 0.0
            resultado += Muestra(PuntoGeo(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t), rumbo(a, b))
        }
        return resultado
    }

    private fun rumbo(
        a: PuntoGeo,
        b: PuntoGeo,
    ): Double {
        val este = (b.lon - a.lon) * cos(Math.toRadians(a.lat))
        val norte = b.lat - a.lat
        return (Math.toDegrees(atan2(este, norte)) + GRADOS_VUELTA) % GRADOS_VUELTA
    }

    private const val GRADOS_VUELTA = 360.0
}
