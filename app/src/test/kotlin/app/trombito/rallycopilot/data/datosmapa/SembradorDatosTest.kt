package app.trombito.rallycopilot.data.datosmapa

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.trombito.rallycopilot.data.db.DatosMapaDao
import app.trombito.rallycopilot.data.db.RallyDatabase
import app.trombito.rallycopilot.data.db.archivos
import app.trombito.rallycopilot.data.db.estado
import app.trombito.rallycopilot.motor.datos.ArchivoDatos
import app.trombito.rallycopilot.motor.datos.ArchivoLocal
import app.trombito.rallycopilot.motor.datos.ManifestDatos
import app.trombito.rallycopilot.motor.datos.PlanificadorDescarga
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream

/** Assets falsos con el contrato `datos/manifest-incluido.json` + `datos/<nombre>.asset`; cuenta las aperturas. */
private class AssetsFalsos {
    val archivos = mutableMapOf<String, ByteArray>()
    val aperturas = mutableListOf<String>()

    fun abrir(ruta: String): InputStream {
        aperturas += ruta
        val bytes = archivos[ruta] ?: throw FileNotFoundException(ruta)
        return ByteArrayInputStream(bytes)
    }

    fun archivosAbiertos() = aperturas.filter { !it.endsWith("manifest-incluido.json") }
}

@RunWith(RobolectricTestRunner::class)
class SembradorDatosTest {
    @get:Rule val carpeta = TemporaryFolder()

    private lateinit var base: RallyDatabase
    private lateinit var dao: DatosMapaDao
    private lateinit var mapa: File
    private lateinit var config: ConfigDatosMapa
    private val assets = AssetsFalsos()
    private var bytesLibres = Long.MAX_VALUE / 2

    @Before
    fun preparar() {
        val contexto = ApplicationProvider.getApplicationContext<Context>()
        base = Room.inMemoryDatabaseBuilder(contexto, RallyDatabase::class.java).allowMainThreadQueries().build()
        dao = base.datosMapaDao()
        mapa = File(carpeta.newFolder("raiz"), "mapa")
        config =
            ConfigDatosMapa(
                urlManifest = URL_MANIFEST,
                directorioMapa = mapa,
                userAgent = "RallyCopilot/test",
                bytesLibres = { bytesLibres },
            )
    }

    @After
    fun cerrar() = base.close()

    private fun sembrador() = SembradorDatos(dao, config, assets::abrir, Dispatchers.IO)

    private fun entrada(
        id: String,
        bytes: ByteArray,
        version: String,
    ): ArchivoDatos {
        val nombre = PaqueteDePrueba.NOMBRES.getValue(id)
        return ArchivoDatos(
            id = id,
            nombre = nombre,
            url = "https://github.com/trombonet12/rally-copilot-data/releases/download/$version/$nombre",
            bytes = bytes.size.toLong(),
            sha256 = PaqueteDePrueba.sha256(bytes),
            version = version,
            atribucion = "© OpenStreetMap contributors",
            licencia = "ODbL",
        )
    }

    /** Pone en los assets un APK con [version] de datos (sin satélite, como el contrato) y devuelve su manifest. */
    private fun incluir(
        version: String,
        marca: String = version,
        ids: List<String> = listOf("vectorial", "estilo", "vias"),
        corruptos: Set<String> = emptySet(),
    ): ManifestDatos {
        val contenidos = PaqueteDePrueba.contenidos(marca)
        val archivos = ids.map { entrada(it, contenidos.getValue(it), version) }
        val manifest = ManifestDatos(1, version, "2026-11-01T00:00:00Z", archivos)
        assets.archivos["datos/manifest-incluido.json"] = Json.encodeToString(manifest).toByteArray()
        archivos.forEach { a ->
            val original = contenidos.getValue(a.id)
            assets.archivos["datos/${a.nombre}.asset"] =
                if (a.id in corruptos) original.copyOf().also { it[0] = (it[0] + 1).toByte() } else original
        }
        return manifest
    }

    private fun leer(nombre: String) = File(mapa, nombre).readBytes()

    @Test
    fun `la primera siembra instala y registra todo con su sha`() {
        val manifest = incluir("datos-2026.11.01-1")
        val contenidos = PaqueteDePrueba.contenidos("datos-2026.11.01-1")

        val resultado = runBlocking { sembrador().sembrar() }

        assertEquals(setOf("vectorial", "estilo", "vias"), resultado.sembrados)
        assertTrue(resultado.fallidos.isEmpty())
        assertArrayEquals(contenidos.getValue("vectorial"), leer("mallorca.pmtiles"))
        assertArrayEquals(contenidos.getValue("vias"), leer("vias-mallorca.bin"))
        assertTrue("El zip de estilo se extrae como en una descarga", File(mapa, "vectorial.json").isFile)
        assertTrue(File(mapa, "glyphs/Noto/0-255.pbf").isFile)
        assertFalse("No quedan restos de la siembra", File(mapa, ".sembrando").exists())
        runBlocking {
            val registrados = dao.archivos().associateBy { it.id }
            assertEquals(manifest.archivos.associate { it.id to it.sha256 }, registrados.mapValues { it.value.sha256 })
            assertEquals(manifest.archivos.sumOf { it.bytes }, registrados.values.sumOf { it.bytes })
            assertEquals("datos-2026.11.01-1", dao.estado()?.versionInstalada)
            assertNull("Sembrar no es una actualización: sin aviso", dao.estado()?.avisoActualizado)
        }
    }

    @Test
    fun `el segundo arranque no copia ni rehashea nada`() {
        incluir("datos-2026.11.01-1")
        runBlocking { sembrador().sembrar() }
        assets.aperturas.clear()

        val resultado = runBlocking { sembrador().sembrar() }

        assertTrue(resultado.omitido)
        assertTrue(resultado.sembrados.isEmpty())
        assertTrue("Solo se lee el manifest incluido", assets.archivosAbiertos().isEmpty())
    }

    @Test
    fun `un APK con datos nuevos vuelve a sembrar solo lo que cambia`() {
        incluir("datos-2026.11.01-1")
        runBlocking { sembrador().sembrar() }
        val nuevos = PaqueteDePrueba.contenidos("datos-2026.11.20-1")
        val manifest = incluir("datos-2026.11.20-1", ids = listOf("vectorial", "estilo", "vias"))
        // Las vias no cambian: mismos bytes en el APK nuevo que en el viejo.
        val viejas = PaqueteDePrueba.contenidos("datos-2026.11.01-1").getValue("vias")
        assets.archivos["datos/vias-mallorca.bin.asset"] = viejas
        val manifestConViasViejas =
            manifest.copy(
                archivos =
                    manifest.archivos.map {
                        if (it.id ==
                            "vias"
                        ) {
                            entrada("vias", viejas, "datos-2026.11.01-1")
                        } else {
                            it
                        }
                    },
            )
        assets.archivos["datos/manifest-incluido.json"] = Json.encodeToString(manifestConViasViejas).toByteArray()
        assets.aperturas.clear()

        val resultado = runBlocking { sembrador().sembrar() }

        assertEquals(setOf("vectorial", "estilo"), resultado.sembrados)
        assertArrayEquals(nuevos.getValue("vectorial"), leer("mallorca.pmtiles"))
        assertFalse("Las vías iguales no se copian", assets.aperturas.any { it.endsWith("vias-mallorca.bin.asset") })
    }

    @Test
    fun `los datos locales mas nuevos que los del APK no se pisan`() {
        val locales = PaqueteDePrueba.contenidos("datos-2026.12.01-1")
        val local = entrada("vectorial", locales.getValue("vectorial"), "datos-2026.12.01-1")
        File(mapa, local.nombre).apply { parentFile?.mkdirs() }.writeBytes(locales.getValue("vectorial"))
        runBlocking {
            dao.registrarInstalacion(listOf(local.aEntidad()), emptyList(), "datos-2026.12.01-1", avisar = false)
        }
        incluir("datos-2026.11.01-1")

        val resultado = runBlocking { sembrador().sembrar() }

        assertEquals(setOf("estilo", "vias"), resultado.sembrados)
        assertArrayEquals(locales.getValue("vectorial"), leer("mallorca.pmtiles"))
        runBlocking {
            assertEquals(local.sha256, dao.archivos().first { it.id == "vectorial" }.sha256)
            assertEquals("La versión instalada no baja", "datos-2026.12.01-1", dao.estado()?.versionInstalada)
        }
    }

    @Test
    fun `los datos locales mas viejos se sustituyen`() {
        val viejos = PaqueteDePrueba.contenidos("datos-2026.10.01-1")
        val local = entrada("vectorial", viejos.getValue("vectorial"), "datos-2026.10.01-1")
        File(mapa, local.nombre).apply { parentFile?.mkdirs() }.writeBytes(viejos.getValue("vectorial"))
        runBlocking {
            dao.registrarInstalacion(listOf(local.aEntidad()), emptyList(), "datos-2026.10.01-1", avisar = false)
        }
        val manifest = incluir("datos-2026.11.01-1")

        val resultado = runBlocking { sembrador().sembrar() }

        assertTrue("vectorial" in resultado.sembrados)
        assertEquals(manifest.archivo("vectorial")?.sha256, PaqueteDePrueba.sha256(leer("mallorca.pmtiles")))
        runBlocking {
            assertEquals(manifest.archivo("vectorial")?.sha256, dao.archivos().first { it.id == "vectorial" }.sha256)
        }
    }

    @Test
    fun `un registro cuyo archivo ya no esta en disco se vuelve a sembrar`() {
        incluir("datos-2026.11.01-1")
        runBlocking { sembrador().sembrar() }
        File(mapa, ".sembrado").delete()
        File(mapa, "mallorca.pmtiles").delete()

        val resultado = runBlocking { sembrador().sembrar() }

        assertEquals(setOf("vectorial"), resultado.sembrados)
        assertTrue(File(mapa, "mallorca.pmtiles").isFile)
    }

    @Test
    fun `un asset con hash incorrecto no se instala y no rompe lo demas`() {
        incluir("datos-2026.11.01-1", corruptos = setOf("vectorial"))

        val resultado = runBlocking { sembrador().sembrar() }

        assertEquals(setOf("vectorial"), resultado.fallidos)
        assertEquals(setOf("estilo", "vias"), resultado.sembrados)
        assertFalse(File(mapa, "mallorca.pmtiles").exists())
        assertFalse("No quedan .part ni .ok", File(mapa, ".sembrando").exists())
        runBlocking { assertEquals(setOf("estilo", "vias"), dao.archivos().map { it.id }.toSet()) }
    }

    @Test
    fun `un asset que falta se salta sin romper`() {
        incluir("datos-2026.11.01-1")
        assets.archivos.remove("datos/vias-mallorca.bin.asset")

        val resultado = runBlocking { sembrador().sembrar() }

        assertEquals(setOf("vias"), resultado.fallidos)
        assertEquals(setOf("vectorial", "estilo"), resultado.sembrados)
    }

    @Test
    fun `sin manifest incluido no hace nada`() {
        val resultado = runBlocking { sembrador().sembrar() }

        assertTrue(resultado.omitido)
        runBlocking { assertTrue(dao.archivos().isEmpty()) }
    }

    @Test
    fun `un manifest incluido ilegible no rompe`() {
        assets.archivos["datos/manifest-incluido.json"] = "no es json".toByteArray()

        val resultado = runBlocking { sembrador().sembrar() }

        assertTrue(resultado.sembrados.isEmpty())
        runBlocking { assertTrue(dao.archivos().isEmpty()) }
    }

    @Test
    fun `sin espacio no siembra ni marca para reintentar en el proximo arranque`() {
        incluir("datos-2026.11.01-1")
        bytesLibres = 10

        val resultado = runBlocking { sembrador().sembrar() }

        assertTrue(resultado.sembrados.isEmpty())
        assertFalse(File(mapa, ".sembrado").exists())
        bytesLibres = Long.MAX_VALUE / 2
        assertEquals(3, runBlocking { sembrador().sembrar() }.sembrados.size)
    }

    @Test
    fun `esperar suspende hasta que la siembra acaba`() {
        incluir("datos-2026.11.01-1")
        val sembrador = sembrador()

        runBlocking { sembrador.esperar() }

        assertTrue(sembrador.listo.value)
        assertTrue(File(mapa, "mallorca.pmtiles").isFile)
        runBlocking { assertEquals(3, dao.archivos().size) }
    }

    @Test
    fun `esperar termina aunque la siembra falle`() {
        assets.archivos["datos/manifest-incluido.json"] = "no es json".toByteArray()
        val sembrador = sembrador()

        runBlocking { sembrador.esperar() }

        assertTrue(sembrador.listo.value)
    }

    @Test
    fun `el planificador no vuelve a bajar lo sembrado y solo pide el satelite`() {
        val manifest = incluir("datos-2026.11.01-1")
        runBlocking { sembrador().sembrar() }
        val satelite = entrada("satelite", PaqueteDePrueba.aleatorio(9, 5_000), "datos-2026.11.01-1")
        val remoto = manifest.copy(archivos = manifest.archivos + satelite)
        val locales = runBlocking { dao.archivos() }.map { ArchivoLocal(it.id, it.sha256, it.bytes) }

        val plan = PlanificadorDescarga.planificar(remoto, locales, Long.MAX_VALUE / 2, Long.MAX_VALUE / 2)

        assertEquals(listOf("satelite"), plan.aDescargar.map { it.id })
        assertTrue(plan.aBorrar.isEmpty())
    }

    @Test
    fun `la actualizacion automatica con lo sembrado no baja el satelite y la manual solo el satelite`() {
        carpeta.newFolder("entorno").let { raiz ->
            EntornoDatosMapa(raiz).use { entorno ->
                val contenidos = PaqueteDePrueba.contenidos("datos-2026.11.01-1")
                incluir("datos-2026.11.01-1")
                val sembrador = SembradorDatos(dao, entorno.config, assets::abrir, Dispatchers.IO)
                val actualizador = ActualizadorDatos(dao, base.ajustesMapasDao(), entorno.config, sembrador)
                entorno.publicar("datos-2026.11.01-1", contenidos)

                // La siembra no se ha pedido: el actualizador la espera él mismo.
                val auto = runBlocking { actualizador.actualizar(automatico = true) }
                assertEquals(ResultadoActualizacion.Instalado, auto)
                runBlocking { assertFalse(dao.archivos().any { it.id == "satelite" }) }
                assertFalse(File(entorno.mapa, "pnoa-mallorca-z16.pmtiles").exists())

                val progreso = mutableListOf<Pair<Long, Long>>()
                val manual = runBlocking { actualizador.actualizar(automatico = false) { h, t -> progreso += h to t } }
                assertEquals(ResultadoActualizacion.Instalado, manual)
                assertEquals(contenidos.getValue("satelite").size.toLong(), progreso.last().second)
                runBlocking { assertTrue(dao.archivos().any { it.id == "satelite" }) }
                assertArrayEquals(
                    contenidos.getValue("satelite"),
                    File(entorno.mapa, "pnoa-mallorca-z16.pmtiles").readBytes(),
                )
            }
        }
    }

    @Test
    fun `esMasVieja compara por grupos de digitos`() {
        assertEquals(true, esMasVieja("datos-2026.10.31-5", "datos-2026.11.01-1"))
        assertEquals(true, esMasVieja("datos-2026.10.31-5", "datos-2026.10.31-6"))
        assertEquals(true, esMasVieja("datos-2026.10.31-9", "datos-2026.10.31-10"))
        assertEquals(false, esMasVieja("datos-2026.11.01-1", "datos-2026.10.31-5"))
        assertEquals(false, esMasVieja("datos-2026.11.01-1", "datos-2026.11.01-1"))
        assertEquals(true, esMasVieja("v1", "v2"))
        assertNull(esMasVieja("sin-numero", "datos-2026.11.01-1"))
    }
}
