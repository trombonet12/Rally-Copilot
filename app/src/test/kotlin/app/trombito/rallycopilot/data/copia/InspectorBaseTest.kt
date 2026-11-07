package app.trombito.rallycopilot.data.copia

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** La lista blanca de [EstructuraRoom] tiene que cubrir todas las versiones del esquema de `app/schemas`. */
@RunWith(RobolectricTestRunner::class)
class InspectorBaseTest {
    private val esquemas: List<JsonObject> by lazy {
        val carpeta = File("schemas/app.trombito.rallycopilot.data.db.RallyDatabase")
        val archivos = carpeta.listFiles { f -> f.extension == "json" }.orEmpty()
        assertTrue("sin esquemas en ${carpeta.absolutePath}", archivos.isNotEmpty())
        archivos.map {
            Json
                .parseToJsonElement(it.readText())
                .jsonObject
                .getValue("database")
                .jsonObject
        }
    }

    private fun entidades(): List<JsonObject> =
        esquemas.flatMap { it.getValue("entities").jsonArray.map { e -> e.jsonObject } }

    @Test
    fun `las tablas de todas las versiones del esquema estan en la lista blanca`() {
        val tablas = entidades().map { it.getValue("tableName").jsonPrimitive.content }.toSet()
        assertEquals(emptySet<String>(), tablas - EstructuraRoom.TABLAS_ROOM)
    }

    @Test
    fun `los indices de todas las versiones del esquema estan en la lista blanca`() {
        val indices =
            entidades()
                .flatMap { (it["indices"] as? JsonArray).orEmpty().map { i -> i.jsonObject } }
                .map { it.getValue("name").jsonPrimitive.content }
                .toSet()
        assertEquals(emptySet<String>(), indices - EstructuraRoom.INDICES_ROOM)
    }

    @Test
    fun `ningun esquema declara vistas ni fts`() {
        assertTrue(esquemas.all { (it["views"] as? JsonArray).orEmpty().isEmpty() })
        assertTrue(entidades().all { it["ftsVersion"] == null })
    }
}
