package app.trombito.rallycopilot.data.copia

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.trombito.rallycopilot.data.db.RallyDatabase
import app.trombito.rallycopilot.data.db.RecorridoEntity
import app.trombito.rallycopilot.domain.copia.CopiaInvalidaException
import app.trombito.rallycopilot.domain.copia.MotivoCopiaInvalida
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Nombres cortos a propósito (MAX_PATH en Windows con los directorios de Robolectric). */
@RunWith(RobolectricTestRunner::class)
class CopiaSeguridadTest {
    private val contexto = ApplicationProvider.getApplicationContext<Context>()
    private val nombre = "c.db"
    private val abiertas = mutableListOf<RallyDatabase>()
    private lateinit var archivo: File
    private lateinit var base: RallyDatabase

    @Before
    fun preparar() {
        contexto.deleteDatabase(nombre)
        archivo = contexto.getDatabasePath(nombre)
        archivo.parentFile?.mkdirs()
        base = abrir()
    }

    @After
    fun cerrar() {
        abiertas.forEach { if (it.isOpen) it.close() }
        contexto.deleteDatabase(nombre)
    }

    private fun abrir(): RallyDatabase =
        Room
            .databaseBuilder(contexto, RallyDatabase::class.java, nombre)
            .allowMainThreadQueries()
            .build()
            .also { abiertas += it }

    private fun copia(maxBytes: Long = 200L * 1024 * 1024) =
        CopiaSeguridad(base, archivo, InfoApp("1.2.3", 42), ahora = { FECHA }, maxBytes = maxBytes)

    private fun insertar(distancia: Double): Long =
        runBlocking {
            base.recorridosDao().insertar(
                RecorridoEntity(inicioMs = 1, finMs = 2, distanciaM = distancia, destinoNombre = "x"),
            )
        }

    private fun exportar(): ByteArray =
        ByteArrayOutputStream()
            .also { salida ->
                runBlocking {
                    copia().exportar(salida)
                }
            }.toByteArray()

    private fun importar(
        zip: ByteArray,
        copia: CopiaSeguridad = copia(),
    ) = runBlocking { copia.importar(ByteArrayInputStream(zip)) }

    private fun motivoDeImportar(
        zip: ByteArray,
        copia: CopiaSeguridad = copia(),
    ): MotivoCopiaInvalida =
        try {
            importar(zip, copia)
            error("debía rechazarse")
        } catch (e: CopiaInvalidaException) {
            e.motivo
        }

    private fun entradas(zip: ByteArray): Map<String, ByteArray> {
        val resultado = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(zip)).use { entrada ->
            generateSequence { entrada.nextEntry }.forEach { resultado[it.name] = entrada.readBytes() }
        }
        return resultado
    }

    private fun zip(entradas: List<Pair<String, ByteArray>>): ByteArray {
        val salida = ByteArrayOutputStream()
        ZipOutputStream(salida).use { z ->
            entradas.forEach { (nombre, bytes) ->
                z.putNextEntry(ZipEntry(nombre))
                z.write(bytes)
                z.closeEntry()
            }
        }
        return salida.toByteArray()
    }

    private fun manifest(esquema: Int): ByteArray =
        (
            "{\"esquema\":$esquema,\"versionName\":\"1\",\"versionCode\":1,\"fechaMs\":1,\"fecha\":\"x\"," +
                "\"recorridos\":0,\"tramos\":0,\"pasadas\":0}"
        ).toByteArray()

    /** La base del zip tras aplicarle [cambio] (p. ej. otra user_version). */
    private fun baseModificada(
        zipValido: ByteArray,
        cambio: (SQLiteDatabase) -> Unit,
    ): ByteArray {
        val temporal = File(archivo.parentFile, "t.db")
        temporal.writeBytes(entradas(zipValido).getValue("rally.db"))
        SQLiteDatabase.openDatabase(temporal.path, null, SQLiteDatabase.OPEN_READWRITE).use(cambio)
        val bytes = temporal.readBytes()
        temporal.delete()
        return bytes
    }

    @Test
    fun `ida y vuelta conserva los datos`() {
        val a = insertar(100.0)
        val b = insertar(200.0)
        val zip = exportar()
        insertar(300.0)

        val resumen = importar(zip)
        assertEquals(2, resumen.recorridos)
        assertEquals(42L, resumen.versionCode)
        assertEquals(FECHA, resumen.fechaMs)
        assertTrue(!base.isOpen)

        base = abrir()
        assertEquals(100.0, runBlocking { base.recorridosDao().recorrido(a) }?.distanciaM)
        assertEquals(200.0, runBlocking { base.recorridosDao().recorrido(b) }?.distanciaM)
        assertNull(runBlocking { base.recorridosDao().recorrido(b + 1) })
    }

    @Test
    fun `el zip lleva solo la base y el manifiesto`() {
        insertar(1.0)
        val contenido = entradas(exportar())
        assertEquals(setOf("rally.db", "manifest.json"), contenido.keys)
        val texto = contenido.getValue("manifest.json").decodeToString()
        assertTrue(texto, "\"esquema\":13" in texto.replace(" ", ""))
        assertTrue(texto, "\"versionName\":\"1.2.3\"" in texto.replace(" ", ""))
        assertTrue(texto, "\"recorridos\":1" in texto.replace(" ", ""))
    }

    @Test
    fun `una ruta con dos puntos se rechaza`() {
        val bueno = entradas(exportar())
        val malo = zip(listOf("../rally.db" to bueno.getValue("rally.db"), "manifest.json" to manifest(12)))
        assertEquals(MotivoCopiaInvalida.ARCHIVO_INVALIDO, motivoDeImportar(malo))
    }

    @Test
    fun `una entrada inesperada se rechaza`() {
        val bueno = entradas(exportar())
        val malo =
            zip(
                listOf(
                    "rally.db" to bueno.getValue("rally.db"),
                    "manifest.json" to bueno.getValue("manifest.json"),
                    "mapa/mapa.pmtiles" to ByteArray(4),
                ),
            )
        assertEquals(MotivoCopiaInvalida.ARCHIVO_INVALIDO, motivoDeImportar(malo))
    }

    @Test
    fun `sin manifiesto o sin zip se rechaza`() {
        val bueno = entradas(exportar())
        assertEquals(
            MotivoCopiaInvalida.ARCHIVO_INVALIDO,
            motivoDeImportar(zip(listOf("rally.db" to bueno.getValue("rally.db")))),
        )
        assertEquals(MotivoCopiaInvalida.ARCHIVO_INVALIDO, motivoDeImportar("no soy un zip".toByteArray()))
    }

    @Test
    fun `un esquema futuro se rechaza`() {
        val bueno = exportar()
        val futura = baseModificada(bueno) { it.version = 99 }
        val malo = zip(listOf("rally.db" to futura, "manifest.json" to manifest(99)))
        assertEquals(MotivoCopiaInvalida.ESQUEMA_FUTURO, motivoDeImportar(malo))
        // El manifiesto miente, pero la base lo delata.
        val mentira = zip(listOf("rally.db" to futura, "manifest.json" to manifest(12)))
        assertEquals(MotivoCopiaInvalida.ESQUEMA_FUTURO, motivoDeImportar(mentira))
    }

    @Test
    fun `una base corrupta se rechaza y no se toca la actual`() {
        val id = insertar(5.0)
        val bueno = exportar()
        val bytes = entradas(bueno).getValue("rally.db")
        val basura = zip(listOf("rally.db" to ByteArray(bytes.size) { 7 }, "manifest.json" to manifest(12)))
        assertEquals(MotivoCopiaInvalida.BASE_DANADA, motivoDeImportar(basura))

        val rota = bytes.copyOf()
        for (i in 4096 until rota.size) rota[i] = 0x5A
        val danada = zip(listOf("rally.db" to rota, "manifest.json" to manifest(12)))
        assertEquals(MotivoCopiaInvalida.BASE_DANADA, motivoDeImportar(danada))

        assertTrue(base.isOpen)
        assertNotNull(runBlocking { base.recorridosDao().recorrido(id) })
    }

    @Test
    fun `pasarse del tamaño maximo se rechaza`() {
        val bueno = exportar()
        assertEquals(MotivoCopiaInvalida.DEMASIADO_GRANDE, motivoDeImportar(bueno, copia(maxBytes = 1000)))
    }

    @Test
    fun `una base de otra app o de otro hash se rechaza`() {
        val bueno = exportar()
        val otroHash = baseModificada(bueno) { it.execSQL("UPDATE room_master_table SET identity_hash = 'x'") }
        assertEquals(
            MotivoCopiaInvalida.INCOMPATIBLE,
            motivoDeImportar(zip(listOf("rally.db" to otroHash, "manifest.json" to manifest(12)))),
        )
        val ajena =
            baseModificada(bueno) {
                it.execSQL("DROP TABLE room_master_table")
            }
        assertEquals(
            MotivoCopiaInvalida.INCOMPATIBLE,
            motivoDeImportar(zip(listOf("rally.db" to ajena, "manifest.json" to manifest(12)))),
        )
    }

    @Test
    fun `un esquema menor se acepta para migrar al abrir`() {
        val bueno = exportar()
        val vieja = baseModificada(bueno) { it.version = 11 }
        val resumen = importar(zip(listOf("rally.db" to vieja, "manifest.json" to manifest(11))))
        assertEquals(11, resumen.esquema)
        val comprobada = SQLiteDatabase.openDatabase(archivo.path, null, SQLiteDatabase.OPEN_READONLY)
        comprobada.use { assertEquals(11, it.version) }
        assertTrue(!File(archivo.path + "-wal").exists())
    }

    private companion object {
        const val FECHA = 1_762_000_000_000L
    }
}
