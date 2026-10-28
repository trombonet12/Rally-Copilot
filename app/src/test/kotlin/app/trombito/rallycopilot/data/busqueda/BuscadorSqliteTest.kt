package app.trombito.rallycopilot.data.busqueda

import android.database.sqlite.SQLiteDatabase
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
        db.version = version
        db.close()
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
}
