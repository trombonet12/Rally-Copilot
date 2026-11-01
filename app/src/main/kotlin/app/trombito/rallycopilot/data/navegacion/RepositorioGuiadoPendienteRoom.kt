package app.trombito.rallycopilot.data.navegacion

import app.trombito.rallycopilot.data.db.GuiadoPendienteDao
import app.trombito.rallycopilot.data.db.GuiadoPendienteEntity
import app.trombito.rallycopilot.domain.navegacion.Destino
import app.trombito.rallycopilot.domain.navegacion.GuiadoPendiente
import app.trombito.rallycopilot.domain.navegacion.RepositorioGuiadoPendiente
import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Guiado pendiente en Room (una fila). [reloj] (ms epoch) sella el momento de guardar y decide la caducidad
 * ([CADUCIDAD_MS]) cada vez que llega un valor.
 */
class RepositorioGuiadoPendienteRoom(
    private val dao: GuiadoPendienteDao,
    private val dispatcher: CoroutineDispatcher,
    private val reloj: () -> Long = System::currentTimeMillis,
) : RepositorioGuiadoPendiente {
    override val pendiente: Flow<GuiadoPendiente?> =
        dao.observar().map { fila -> fila?.aDominio()?.takeIf { reloj() - it.desdeMs <= CADUCIDAD_MS } }

    override suspend fun guardar(
        destino: Destino,
        perfil: PerfilRuta,
        paradas: List<Destino>,
    ) {
        withContext(dispatcher) {
            dao.guardar(
                GuiadoPendienteEntity(
                    destinoNombre = destino.nombre,
                    lat = destino.punto.lat,
                    lon = destino.punto.lon,
                    perfil = perfil.name,
                    desdeMs = reloj(),
                    paradas = codificarParadas(paradas),
                ),
            )
        }
    }

    override suspend fun borrar() {
        withContext(dispatcher) { dao.borrar() }
    }

    companion object {
        /** Más tiempo que esto y el guiado se considera abandonado. */
        const val CADUCIDAD_MS = 12L * 60 * 60 * 1000
    }
}

private fun GuiadoPendienteEntity.aDominio(): GuiadoPendiente? {
    val perfil = PerfilRuta.entries.firstOrNull { it.name == perfil } ?: return null
    return GuiadoPendiente(Destino(destinoNombre, PuntoGeo(lat, lon)), perfil, desdeMs, decodificarParadas(paradas))
}

/** Las paradas como JSON compacto (`[{"n":"Lluc","lat":39.8,"lon":2.9}]`); lista vacía = cadena vacía. */
internal fun codificarParadas(paradas: List<Destino>): String =
    if (paradas.isEmpty()) {
        ""
    } else {
        JsonArray(
            paradas.map {
                buildJsonObject {
                    put("n", it.nombre)
                    put("lat", it.punto.lat)
                    put("lon", it.punto.lon)
                }
            },
        ).toString()
    }

/** Inversa de [codificarParadas]; un texto ilegible o con filas incompletas da las paradas que se puedan leer. */
internal fun decodificarParadas(texto: String): List<Destino> {
    if (texto.isEmpty()) return emptyList()
    return try {
        Json.parseToJsonElement(texto).jsonArray.mapNotNull { fila ->
            val o = fila.jsonObject
            val lat = o["lat"]?.jsonPrimitive?.doubleOrNull
            val lon = o["lon"]?.jsonPrimitive?.doubleOrNull
            val nombre = o["n"]?.jsonPrimitive?.content
            if (lat == null || lon == null || nombre == null) null else Destino(nombre, PuntoGeo(lat, lon))
        }
    } catch (_: SerializationException) {
        emptyList()
    } catch (_: IllegalArgumentException) {
        emptyList()
    }
}
