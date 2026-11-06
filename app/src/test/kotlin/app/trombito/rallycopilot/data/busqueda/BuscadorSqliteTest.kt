package app.trombito.rallycopilot.data.busqueda

import android.database.sqlite.SQLiteDatabase
import app.trombito.rallycopilot.motor.busqueda.ConsultaBusqueda
import app.trombito.rallycopilot.motor.busqueda.NormalizadorNombre
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class BuscadorSqliteTest {
    @get:Rule
    val carpeta = TemporaryFolder()

    private lateinit var archivo: File
    private lateinit var buscador: BuscadorSqlite

    private class Fila(
        val nombre: String,
        val categoria: String,
        val municipio: String,
        val lat: Double,
        val lon: Double,
        val importancia: Int,
    )

    private val filas =
        listOf(
            Fila("Sóller", "pueblo", "Sóller", 39.766, 2.715, 80),
            Fila("Sollerich", "calle", "Palma", 39.580, 2.660, 10),
            Fila("Port de Sóller", "aldea", "Sóller", 39.795, 2.695, 60),
            Fila("Carrer de Sóller", "calle", "Palma", 39.570, 2.650, 15),
            Fila("Carrer Major", "calle", "Palma", 39.570, 2.650, 15),
            Fila("Carrer Major", "calle", "Sóller", 39.766, 2.715, 15),
            Fila("Carrer Major", "calle", "Pollença", 39.877, 3.016, 15),
            Fila("Cala d'Or", "pueblo", "", 39.380, 3.230, 80),
            Fila("Bar 50%", "restaurante", "Palma", 39.57, 2.65, 30),
            Fila("Bar 500", "restaurante", "Palma", 39.57, 2.65, 30),
        )

    private fun crearIndice(
        destino: File,
        version: Int = 2,
        lugares: List<Fila> = filas,
        conPalabras: Boolean = false,
    ) {
        val db = SQLiteDatabase.openOrCreateDatabase(destino, null)
        db.execSQL(
            "CREATE TABLE lugares (nombre TEXT NOT NULL, nombre_norm TEXT NOT NULL, tipo TEXT NOT NULL, " +
                "categoria TEXT NOT NULL, municipio TEXT NOT NULL, lat REAL NOT NULL, lon REAL NOT NULL, " +
                "importancia INTEGER NOT NULL)",
        )
        for (f in lugares) {
            db.execSQL(
                "INSERT INTO lugares VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any>(
                    f.nombre,
                    NormalizadorNombre.normalizar(f.nombre),
                    "x:y",
                    f.categoria,
                    f.municipio,
                    f.lat,
                    f.lon,
                    f.importancia,
                ),
            )
        }
        db.execSQL("CREATE INDEX lugares_nombre_norm ON lugares (nombre_norm)")
        if (conPalabras) anadirPalabras(db)
        db.version = version
        db.close()
    }

    /** Esquema nuevo: `municipio_norm` y `palabras` (como `escribir_sqlite` de busqueda.py). */
    private fun anadirPalabras(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE lugares ADD COLUMN municipio_norm TEXT NOT NULL DEFAULT ''")
        db.execSQL(
            "CREATE TABLE palabras (palabra TEXT NOT NULL, lugar INTEGER NOT NULL, PRIMARY KEY (palabra, lugar)) " +
                "WITHOUT ROWID",
        )
        val lugares = ArrayList<Pair<Long, String>>()
        db.rawQuery("SELECT rowid, nombre_norm, municipio FROM lugares", null).use { c ->
            while (c.moveToNext()) {
                lugares += c.getLong(0) to (c.getString(1) + " " + NormalizadorNombre.normalizar(c.getString(2)))
            }
        }
        for ((rowid, texto) in lugares) {
            val palabras = texto.split(' ').filter { it.isNotEmpty() && it !in ConsultaBusqueda.PALABRAS_VACIAS }
            for (palabra in palabras.distinct()) {
                db.execSQL("INSERT INTO palabras VALUES (?, ?)", arrayOf<Any>(palabra, rowid))
            }
        }
    }

    @Before
    fun preparar() {
        archivo = File(carpeta.newFolder("mapa"), "busqueda-mallorca.sqlite")
        buscador = BuscadorSqlite(archivo, Dispatchers.Unconfined)
    }

    private fun buscar(
        texto: String,
        cerca: PuntoGeo? = null,
        limite: Int = 20,
    ) = runBlocking { buscador.buscar(texto, cerca, limite) }

    @Test
    fun `los nombres de lugares son municipios y localidades sin calles ni POIs, de mas a menos importantes`() {
        crearIndice(archivo)

        val nombres = runBlocking { buscador.nombresDeLugares(10) }

        assertEquals(listOf("Cala d'Or", "Sóller", "Port de Sóller"), nombres)
        assertEquals(listOf("Cala d'Or"), runBlocking { buscador.nombresDeLugares(1) })
        assertTrue(runBlocking { buscador.nombresDeLugares(0) }.isEmpty())
    }

    @Test
    fun `sin indice no hay nombres de lugares`() {
        assertTrue(runBlocking { buscador.nombresDeLugares(10) }.isEmpty())
    }

    @Test
    fun `sin indice instalado no devuelve nada`() {
        assertTrue(buscar("soller").isEmpty())
    }

    @Test
    fun `prefijo primero y despues la palabra intermedia, cada grupo por importancia`() {
        crearIndice(archivo)

        val nombres = buscar("soll").map { it.nombre }

        assertEquals(listOf("Sóller", "Sollerich", "Port de Sóller", "Carrer de Sóller"), nombres)
    }

    @Test
    fun `ignora diacriticos y mayusculas en la consulta`() {
        crearIndice(archivo)

        val esperado = buscar("Sóller")
        assertEquals(4, esperado.size)
        assertEquals(esperado, buscar("Soller"))
        assertEquals(esperado, buscar("  SOLLER "))
        assertEquals("Sóller", esperado.first().nombre)
    }

    @Test
    fun `normaliza apostrofos como el indice`() {
        crearIndice(archivo)

        val resultado = buscar("cala d'or")

        assertEquals(listOf("Cala d'Or"), resultado.map { it.nombre })
        assertNull(resultado.single().municipio)
        assertEquals("pueblo", resultado.single().categoria)
        assertEquals(PuntoGeo(39.380, 3.230), resultado.single().punto)
    }

    @Test
    fun `devuelve municipio y punto`() {
        crearIndice(archivo)

        val soller = buscar("soller").first()

        assertEquals("Sóller", soller.municipio)
        assertEquals(PuntoGeo(39.766, 2.715), soller.punto)
    }

    @Test
    fun `con cerca desempata por cercania`() {
        crearIndice(archivo)

        val cercaDePollenca = buscar("carrer major", cerca = PuntoGeo(39.90, 3.00)).map { it.municipio }
        val cercaDePalma = buscar("carrer major", cerca = PuntoGeo(39.57, 2.65)).map { it.municipio }

        assertEquals(listOf("Pollença", "Sóller", "Palma"), cercaDePollenca)
        assertEquals("Palma", cercaDePalma.first())
    }

    @Test
    fun `la cercania no pasa por delante de la importancia`() {
        crearIndice(archivo)

        val nombres = buscar("soll", cerca = PuntoGeo(39.580, 2.660)).map { it.nombre }

        assertEquals("Sóller", nombres.first())
    }

    @Test
    fun `respeta el limite`() {
        crearIndice(archivo)

        assertEquals(listOf("Sóller", "Sollerich"), buscar("soller", limite = 2).map { it.nombre })
        assertEquals(3, buscar("soller", limite = 3).size)
    }

    @Test
    fun `menos de 2 letras devuelve vacio`() {
        crearIndice(archivo)

        assertTrue(buscar("s").isEmpty())
        assertTrue(buscar(" - ").isEmpty())
        assertTrue(buscar("").isEmpty())
    }

    @Test
    fun `los comodines de LIKE en la consulta se tratan como texto`() {
        crearIndice(archivo)

        assertEquals(listOf("Bar 50%"), buscar("50%").map { it.nombre })
        assertTrue(buscar("%%").isEmpty())
        assertTrue(buscar("a_").isEmpty())
    }

    @Test
    fun `un user_version distinto da vacio`() {
        crearIndice(archivo, version = 3)

        assertTrue(buscar("soller").isEmpty())
    }

    @Test
    fun `reabre el indice cuando cambia lastModified`() {
        crearIndice(archivo, version = 3)
        archivo.setLastModified(1_000_000L)
        assertTrue(buscar("soller").isEmpty())

        // Misma fecha: sigue en caché
        SQLiteDatabase.openDatabase(archivo.path, null, SQLiteDatabase.OPEN_READWRITE).use { it.version = 2 }
        archivo.setLastModified(1_000_000L)
        assertTrue(buscar("soller").isEmpty())

        // Fecha nueva: reabre y ya vale
        archivo.setLastModified(2_000_000L)
        assertEquals(4, buscar("soller").size)

        // Y al revés: datos que dejan de valer
        SQLiteDatabase.openDatabase(archivo.path, null, SQLiteDatabase.OPEN_READWRITE).use { it.version = 3 }
        archivo.setLastModified(3_000_000L)
        assertTrue(buscar("soller").isEmpty())
    }

    @Test
    fun `si el archivo desaparece devuelve vacio`() {
        crearIndice(archivo)
        assertEquals(4, buscar("soller").size)

        runBlocking { buscador.cerrar() }
        assertTrue(archivo.delete())

        assertTrue(buscar("soller").isEmpty())
    }

    private val lugaresPalabras =
        listOf(
            Fila("Hospital Universitari Son Espases", "hospital", "Palma", 39.604, 2.631, 36),
            Fila("Hospital Son Llàtzer", "hospital", "Palma", 39.608, 2.703, 36),
            Fila("Hospital de Manacor", "hospital", "Manacor", 39.560, 3.210, 36),
            Fila("Hospital d'Inca", "hospital", "Inca", 39.720, 2.910, 36),
            Fila("Carrer de l'Hospital", "calle", "Sóller", 39.766, 2.715, 15),
            Fila("Son Espases", "barrio", "Palma", 39.600, 2.640, 50),
            Fila("Bar Hospital Son Espases", "restaurante", "Palma", 39.604, 2.632, 30),
            Fila("Castell de Bellver", "castillo", "Palma", 39.5636, 2.6244, 35),
            Fila("Castillo de Alaró", "castillo", "Alaró", 39.7, 2.8, 35),
            Fila("Carrer de Bellver", "calle", "Palma", 39.57, 2.65, 15),
            Fila("Carrer de Colom", "calle", "Palma", 39.571, 2.652, 15),
            Fila("Carrer de Colom", "calle", "Inca", 39.720, 2.912, 15),
            Fila("Can Colom", "restaurante", "Palma", 39.572, 2.653, 32),
            Fila("Bar Carrer Colom", "restaurante", "Palma", 39.573, 2.654, 30),
            Fila("Sóller", "pueblo", "Sóller", 39.766, 2.715, 80),
            Fila("Plaça Major", "calle", "Palma", 39.5696, 2.6502, 15),
            Fila("Platja de Palma", "playa", "Palma", 39.52, 2.74, 40),
        )

    private fun indiceNuevo() = crearIndice(archivo, lugares = lugaresPalabras, conPalabras = true)

    @Test
    fun `hospital son espases encuentra el hospital primero`() {
        indiceNuevo()

        val nombres = buscar("hospital son espases").map { it.nombre }

        assertEquals(listOf("Hospital Universitari Son Espases", "Bar Hospital Son Espases"), nombres)
    }

    @Test
    fun `castillo bellver usa el sinonimo castell`() {
        indiceNuevo()

        assertEquals("Castell de Bellver", buscar("castillo bellver").first().nombre)
        assertEquals("Castell de Bellver", buscar("castell de bellver").first().nombre)
    }

    @Test
    fun `calle colom palma pone la calle antes que el negocio`() {
        indiceNuevo()

        val resultado = buscar("calle colom palma")

        assertEquals(listOf("Carrer de Colom", "Bar Carrer Colom"), resultado.map { it.nombre })
        assertEquals("Palma", resultado.first().municipio)
    }

    @Test
    fun `sin la palabra calle manda la importancia y el municipio filtra`() {
        indiceNuevo()

        val nombres = buscar("colom palma").map { it.nombre }

        assertEquals(listOf("Can Colom", "Bar Carrer Colom", "Carrer de Colom"), nombres)
    }

    @Test
    fun `las palabras casan en cualquier orden y como prefijo`() {
        indiceNuevo()

        assertEquals(setOf("Carrer de Colom", "Bar Carrer Colom"), buscar("palma col carr").map { it.nombre }.toSet())
        assertEquals("Platja de Palma", buscar("playa palma").single().nombre)
        assertTrue(buscar("colom manacor").isEmpty())
    }

    @Test
    fun `una categoria sola da los mas cercanos de ese tipo`() {
        indiceNuevo()

        val desdePalma = buscar("hospital", cerca = PuntoGeo(39.5696, 2.6502), limite = 3).map { it.nombre }
        val desdeInca = buscar("hospital", cerca = PuntoGeo(39.72, 2.91), limite = 2).map { it.nombre }

        assertEquals(
            listOf("Hospital Universitari Son Espases", "Hospital Son Llàtzer", "Hospital d'Inca"),
            desdePalma,
        )
        assertEquals("Hospital d'Inca", desdeInca.first())
    }

    @Test
    fun `la categoria sola se completa con la busqueda por palabras sin repetir`() {
        indiceNuevo()

        val nombres = buscar("hospital", cerca = PuntoGeo(39.5696, 2.6502)).map { it.nombre }

        assertEquals(
            listOf(
                "Hospital Universitari Son Espases",
                "Hospital Son Llàtzer",
                "Hospital d'Inca",
                "Hospital de Manacor",
            ),
            nombres.take(4),
        )
        assertEquals(setOf("Carrer de l'Hospital", "Bar Hospital Son Espases"), nombres.drop(4).toSet())
        assertEquals(6, nombres.size)
    }

    @Test
    fun `una categoria sin cerca ordena por importancia`() {
        indiceNuevo()

        assertEquals(
            setOf("Castell de Bellver", "Castillo de Alaró"),
            buscar("castell", limite = 5).take(2).map { it.nombre }.toSet(),
        )
    }

    @Test
    fun `consultas raras no rompen`() {
        indiceNuevo()

        assertTrue(buscar("%%", cerca = PuntoGeo(39.5, 2.6)).isEmpty())
        assertTrue(buscar("a' OR 1=1 --").isEmpty())
        assertTrue(buscar("de la").isEmpty())
    }

    @Test
    fun `un indice viejo sin palabras sigue funcionando`() {
        crearIndice(archivo, lugares = lugaresPalabras)

        assertEquals("Hospital Universitari Son Espases", buscar("hospital universitari").first().nombre)
        assertEquals("Castell de Bellver", buscar("castell").first().nombre)
    }
}
