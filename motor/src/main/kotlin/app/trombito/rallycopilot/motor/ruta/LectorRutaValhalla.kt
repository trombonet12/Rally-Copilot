package app.trombito.rallycopilot.motor.ruta

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Lee la respuesta de `route` de Valhalla (`trip.legs[].shape` en polyline6 y `maneuvers[]`). Con paradas hay una leg
 * por tramo y el final de cada una, salvo la última, es una parada ([Ruta.indicesParadas]). Las longitudes de Valhalla
 * vienen en kilómetros (o millas si `trip.units` es `miles`) y aquí se convierten a metros.
 */
object LectorRutaValhalla {
    private const val METROS_POR_KM = 1000.0
    private const val METROS_POR_MILLA = 1609.344

    /** @throws RutaInvalida si el JSON no se puede leer, Valhalla devolvió un error o los índices no cuadran. */
    fun leer(json: String): Ruta =
        try {
            analizar(Json.parseToJsonElement(json))
        } catch (e: SerializationException) {
            throw RutaInvalida("Ruta ilegible: ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            throw RutaInvalida("Ruta ilegible: ${e.message}", e)
        } catch (e: IllegalStateException) {
            throw RutaInvalida("Ruta ilegible: ${e.message}", e)
        }

    private fun analizar(raiz: JsonElement): Ruta {
        val objeto = raiz.jsonObject
        objeto["error"]?.let { invalida("Valhalla devolvió un error: ${it.jsonPrimitive.content}") }
        val trip = objeto["trip"]?.jsonObject ?: invalida("La respuesta no tiene «trip»")
        val legs = trip["legs"]?.jsonArray
        if (legs == null || legs.isEmpty()) invalida("La ruta no tiene legs")
        val metrosPorUnidad = if (trip["units"]?.jsonPrimitive?.content == "miles") METROS_POR_MILLA else METROS_POR_KM
        val puntos = ArrayList<PuntoGeo>()
        val maniobras = ArrayList<Maniobra>()
        val paradas = ArrayList<Int>()
        for ((numero, leg) in legs.withIndex()) {
            anadirLeg(leg.jsonObject, puntos, maniobras, metrosPorUnidad)
            // El final de cada leg menos el último es una parada (location de tipo break).
            if (numero < legs.size - 1) paradas.add(puntos.lastIndex)
        }
        if (maniobras.isEmpty()) invalida("La ruta no tiene maniobras")
        return Ruta(
            puntos,
            fusionarRotondas(maniobras),
            maniobras.sumOf { it.longitudM },
            maniobras.sumOf { it.tiempoS },
            paradas,
        )
    }

    /** Suma a [puntos] y [maniobras] los de una [leg]; los índices de sus maniobras se desplazan a la ruta entera. */
    private fun anadirLeg(
        leg: JsonObject,
        puntos: MutableList<PuntoGeo>,
        maniobras: MutableList<Maniobra>,
        metrosPorUnidad: Double,
    ) {
        val shape = Polyline6.decodificar(leg["shape"]?.jsonPrimitive?.content ?: invalida("Leg sin shape"))
        if (shape.size < 2) invalida("Leg con menos de dos puntos")
        // La leg siguiente empieza donde acaba la anterior: no se repite el punto de unión.
        val repetido = puntos.isNotEmpty() && puntos.last() == shape.first()
        val base = if (repetido) puntos.size - 1 else puntos.size
        puntos.addAll(if (repetido) shape.drop(1) else shape)
        val delLeg = leg["maneuvers"]?.jsonArray ?: JsonArray(emptyList())
        delLeg.mapTo(maniobras) { maniobra(it.jsonObject, base, shape.size, metrosPorUnidad) }
    }

    /**
     * Valhalla emite la entrada a una rotonda (sin vía) seguida de la salida (con la vía). Se funden en una sola
     * maniobra de entrada que abarca ambas, con la vía de la salida (o de la entrada si la salida no trae).
     */
    private fun fusionarRotondas(lista: List<Maniobra>): List<Maniobra> {
        val resultado = ArrayList<Maniobra>(lista.size)
        var i = 0
        while (i < lista.size) {
            val actual = lista[i]
            val siguiente = lista.getOrNull(i + 1)
            if (actual.tipo == TipoManiobra.ROTONDA_ENTRADA && siguiente?.tipo == TipoManiobra.ROTONDA_SALIDA) {
                resultado.add(
                    actual.copy(
                        vias = siguiente.vias.ifEmpty { actual.vias },
                        salidaAnilloIndice = siguiente.inicioIndice,
                        finIndice = siguiente.finIndice,
                        longitudM = actual.longitudM + siguiente.longitudM,
                        tiempoS = actual.tiempoS + siguiente.tiempoS,
                    ),
                )
                i += 2
            } else {
                resultado.add(actual)
                i++
            }
        }
        return resultado
    }

    private fun maniobra(
        o: JsonObject,
        base: Int,
        puntosLeg: Int,
        metrosPorUnidad: Double,
    ): Maniobra {
        val inicio = campo(o, "begin_shape_index").int
        val fin = campo(o, "end_shape_index").int
        if (inicio !in 0 until puntosLeg || fin !in inicio until puntosLeg) {
            invalida("Índices de maniobra fuera de rango: $inicio..$fin con $puntosLeg puntos")
        }
        val vias = o["street_names"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        return Maniobra(
            tipo = TipoManiobra.desdeValhalla(campo(o, "type").int),
            instruccionValhalla = o["instruction"]?.jsonPrimitive?.content.orEmpty(),
            vias = vias,
            inicioIndice = base + inicio,
            finIndice = base + fin,
            longitudM = campo(o, "length").double * metrosPorUnidad,
            tiempoS = campo(o, "time").double,
            // Se ignora roundabout_exit_count: Valhalla 3.9.1 devuelve siempre 2. Lo calcula CorrectorRotondas.
            salidaRotonda = null,
        )
    }

    private fun invalida(mensaje: String): Nothing = throw RutaInvalida(mensaje)

    private fun campo(
        o: JsonObject,
        nombre: String,
    ): JsonPrimitive = o[nombre]?.jsonPrimitive ?: invalida("Maniobra sin «$nombre»")
}
