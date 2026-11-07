package app.trombito.rallycopilot.data.datosmapa

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import app.trombito.rallycopilot.data.db.AjustesMapasEntity
import app.trombito.rallycopilot.data.db.ArchivoInstaladoEntity
import app.trombito.rallycopilot.data.db.DatosMapaDao
import app.trombito.rallycopilot.data.db.RallyDatabase
import app.trombito.rallycopilot.data.db.archivos
import app.trombito.rallycopilot.data.db.estado
import app.trombito.rallycopilot.domain.datos.AjustesMapas
import app.trombito.rallycopilot.domain.datos.EstadoDatosMapa
import app.trombito.rallycopilot.domain.datos.MotivoError
import app.trombito.rallycopilot.motor.datos.Complemento
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
class RepositorioDatosMapaImplTest {
    @get:Rule val carpeta = TemporaryFolder()

    private lateinit var entorno: EntornoDatosMapa
    private lateinit var base: RallyDatabase
    private lateinit var dao: DatosMapaDao
    private lateinit var workManager: WorkManager
    private lateinit var repositorio: RepositorioDatosMapaImpl

    @Before
    fun preparar() {
        val contexto = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            contexto,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        workManager = WorkManager.getInstance(contexto)
        entorno = EntornoDatosMapa(carpeta.newFolder("entorno"))
        base = Room.inMemoryDatabaseBuilder(contexto, RallyDatabase::class.java).allowMainThreadQueries().build()
        dao = base.datosMapaDao()
        repositorio = RepositorioDatosMapaImpl(dao, base.ajustesMapasDao(), workManager, entorno.config, Dispatchers.IO)
    }

    @After
    fun cerrar() {
        base.close()
        entorno.close()
    }

    private fun trabajos(): List<WorkInfo> =
        workManager.getWorkInfosForUniqueWork(TrabajoDatosMapa.NOMBRE_TRABAJO).get()

    private fun esperar(condicion: () -> Boolean) {
        val limite = System.currentTimeMillis() + ESPERA_MS
        while (!condicion()) {
            check(System.currentTimeMillis() < limite) { "Tiempo agotado esperando la condición" }
            Thread.sleep(20)
        }
    }

    private fun esperarEstado(condicion: (EstadoDatosMapa) -> Boolean): EstadoDatosMapa =
        runBlocking { withTimeout(ESPERA_MS) { repositorio.estado.first(condicion) } }

    /** Registra lo sembrado desde el APK (300 bytes) y, si [conSatelite], el satélite (500 bytes). */
    private fun instalarArchivo(conSatelite: Boolean = true) =
        runBlocking {
            val sembrados =
                listOf(
                    ArchivoInstaladoEntity("vectorial", "mallorca.pmtiles", "b".repeat(64), 100, "v1"),
                    ArchivoInstaladoEntity("estilo", "estilo-mapa.zip", "c".repeat(64), 100, "v1"),
                    ArchivoInstaladoEntity("vias", "vias-mallorca.bin", "d".repeat(64), 100, "v1"),
                )
            val satelite = ArchivoInstaladoEntity("satelite", "pnoa-mallorca-z16.pmtiles", "a".repeat(64), 500, "v1")
            dao.registrarInstalacion(
                archivos = if (conSatelite) sembrados + satelite else sembrados,
                idsBorrados = emptyList(),
                version = "v1",
                avisar = false,
            )
        }

    private fun escribirEstilos(conSatelite: Boolean) {
        entorno.mapa.mkdirs()
        File(entorno.mapa, "vectorial.json")
            .writeText("""{"url":"pmtiles://{{DIR}}/mallorca.pmtiles","g":"{{DIR}}/glyphs"}""")
        File(entorno.mapa, "satelite.json").writeText("""{"url":"pmtiles://{{DIR}}/pnoa-mallorca-z16.pmtiles"}""")
        if (conSatelite) File(entorno.mapa, "pnoa-mallorca-z16.pmtiles").writeText("x")
    }

    @Test
    fun `sin datos instalados los estilos son null y el estado esta vacio`() {
        assertNull(runBlocking { repositorio.estilos() })
        assertEquals(EstadoDatosMapa(), esperarEstado { true })
    }

    @Test
    fun `los estilos sustituyen DIR por la ruta local en cada aparicion`() {
        escribirEstilos(conSatelite = true)

        val estilos = requireNotNull(runBlocking { repositorio.estilos() })

        val raiz = "file://" + entorno.mapa.absolutePath.replace('\\', '/')
        assertEquals("""{"url":"pmtiles://$raiz/mallorca.pmtiles","g":"$raiz/glyphs"}""", estilos.vectorial)
        assertEquals("""{"url":"pmtiles://$raiz/pnoa-mallorca-z16.pmtiles"}""", estilos.satelite)
        assertFalse(estilos.vectorial.contains("{{DIR}}"))
    }

    private fun escribirEstiloSateliteRealista() {
        val pnoa = """"pnoa":{"type":"raster","url":"pmtiles://{{DIR}}/pnoa-mallorca-z16.pmtiles","tileSize":256}"""
        val calles = """"protomaps":{"type":"vector","url":"pmtiles://{{DIR}}/mallorca.pmtiles"}"""
        val capas =
            """[{"id":"pnoa","type":"raster","source":"pnoa"},""" +
                """{"id":"calles","type":"line","source":"protomaps"}]"""
        File(entorno.mapa, "satelite.json")
            .writeText("""{"version":8,"sources":{$pnoa,$calles},"layers":$capas}""")
    }

    @Test
    fun `con el z17 instalado el estilo satelite lleva su fuente y su capa encima de la del z16`() {
        escribirEstilos(conSatelite = true)
        escribirEstiloSateliteRealista()
        File(entorno.mapa, "pnoa-mallorca-z17.pmtiles").writeText("z17")

        val satelite = requireNotNull(runBlocking { repositorio.estilos() }?.satelite)
        val estilo = Json.parseToJsonElement(satelite).jsonObject

        val raiz = "file://" + entorno.mapa.absolutePath.replace('\\', '/')
        val fuente =
            estilo
                .getValue("sources")
                .jsonObject
                .getValue("pnoa-z17")
                .jsonObject
        assertEquals("pmtiles://$raiz/pnoa-mallorca-z17.pmtiles", fuente.getValue("url").jsonPrimitive.content)
        assertEquals(17, fuente.getValue("minzoom").jsonPrimitive.int)
        assertEquals(17, fuente.getValue("maxzoom").jsonPrimitive.int)
        assertEquals(256, fuente.getValue("tileSize").jsonPrimitive.int)
        val capas =
            estilo.getValue("layers").jsonArray.map {
                it.jsonObject
                    .getValue("id")
                    .jsonPrimitive.content
            }
        assertEquals(listOf("pnoa", "pnoa-z17", "calles"), capas)
        assertEquals(
            17,
            estilo
                .getValue("layers")
                .jsonArray[1]
                .jsonObject
                .getValue("minzoom")
                .jsonPrimitive.int,
        )
    }

    @Test
    fun `con el archivo de detalle el estilo satelite lleva su fuente mbtiles y es valido`() {
        escribirEstilos(conSatelite = true)
        escribirEstiloSateliteRealista()
        File(entorno.mapa, "satelite-detalle.mbtiles").writeText("db")

        val satelite = requireNotNull(runBlocking { repositorio.estilos() }?.satelite)

        val ruta = entorno.mapa.absolutePath.replace(File.separatorChar, '/')
        val fuente =
            Json
                .parseToJsonElement(satelite)
                .jsonObject
                .getValue("sources")
                .jsonObject
                .getValue("pnoa-detalle")
                .jsonObject
        assertEquals("mbtiles://$ruta/satelite-detalle.mbtiles", fuente.getValue("url").jsonPrimitive.content)
        assertEquals(18, fuente.getValue("minzoom").jsonPrimitive.int)
        assertEquals(19, fuente.getValue("maxzoom").jsonPrimitive.int)
    }

    @Test
    fun `borrar el satelite borra tambien el detalle z18 a z19`() {
        val precarga =
            app.trombito.rallycopilot.data.detalle
                .FalsaPrecarga()
        val conPrecarga =
            RepositorioDatosMapaImpl(
                dao,
                base.ajustesMapasDao(),
                workManager,
                entorno.config,
                Dispatchers.IO,
                precarga = precarga,
            )
        instalarArchivo()

        runBlocking { conPrecarga.borrar() }

        assertTrue(precarga.borrado)
    }

    @Test
    fun `sin el archivo de detalle el estilo satelite no lo menciona`() {
        escribirEstilos(conSatelite = true)
        escribirEstiloSateliteRealista()

        val satelite = requireNotNull(runBlocking { repositorio.estilos() }?.satelite)

        assertFalse(satelite.contains("pnoa-detalle"))
    }

    @Test
    fun `sin el z17 el estilo satelite queda como esta en el paquete`() {
        escribirEstilos(conSatelite = true)
        escribirEstiloSateliteRealista()

        val satelite = requireNotNull(runBlocking { repositorio.estilos() }?.satelite)

        assertFalse(satelite.contains("pnoa-z17"))
        assertFalse(satelite.contains("{{DIR}}"))
    }

    @Test
    fun `sin el satelite instalado el estilo satelite es null`() {
        escribirEstilos(conSatelite = false)

        val estilos = requireNotNull(runBlocking { repositorio.estilos() })

        assertNull(estilos.satelite)
        assertNotNull(estilos.vectorial)
    }

    @Test
    fun `un estilo que apunta a la red se rechaza y se registra el error`() {
        escribirEstilos(conSatelite = true)
        File(entorno.mapa, "vectorial.json").writeText("""{"url":"https://evil.example/mallorca.json"}""")

        assertNull(runBlocking { repositorio.estilos() })

        assertEquals(MotivoError.MANIFEST_INVALIDO.name, runBlocking { dao.estado()?.ultimoError })
    }

    @Test
    fun `un estilo satelite que apunta a la red tambien invalida los estilos`() {
        escribirEstilos(conSatelite = true)
        File(entorno.mapa, "satelite.json").writeText("""{"sprite":"http://evil.example/s"}""")

        assertNull(runBlocking { repositorio.estilos() })
    }

    @Test
    fun `descargar encola un unico trabajo solo WiFi y borra el error anterior`() {
        runBlocking { dao.establecerError(MotivoError.SIN_RED.name) }

        repositorio.descargar()
        repositorio.descargar()
        esperar { trabajos().isNotEmpty() }

        val trabajo = trabajos().single()
        assertEquals(WorkInfo.State.ENQUEUED, trabajo.state)
        assertEquals(NetworkType.UNMETERED, trabajo.constraints.requiredNetworkType)
        assertTrue("manual" in trabajo.tags)
        esperar { runBlocking { dao.estado()?.ultimoError } == null }
    }

    @Test
    fun `con solo WiFi desactivado el trabajo pide cualquier red`() {
        runBlocking { repositorio.guardarAjustes(AjustesMapas(soloWifi = false)) }

        repositorio.descargar()
        esperar { trabajos().isNotEmpty() }

        assertEquals(NetworkType.CONNECTED, trabajos().single().constraints.requiredNetworkType)
    }

    @Test
    fun `cambiar solo WiFi reencola el trabajo pendiente con la restriccion nueva`() {
        repositorio.descargar()
        esperar { trabajos().isNotEmpty() }

        runBlocking { repositorio.guardarAjustes(AjustesMapas(soloWifi = false)) }

        esperar {
            trabajos().any {
                it.state == WorkInfo.State.ENQUEUED && it.constraints.requiredNetworkType == NetworkType.CONNECTED
            }
        }
    }

    @Test
    fun `comprobar actualizacion sin datos no encola nada y con datos encola el automatico`() {
        repositorio.comprobarActualizacion()
        Thread.sleep(300)
        assertTrue(trabajos().isEmpty())

        instalarArchivo()
        repositorio.comprobarActualizacion()
        esperar { trabajos().isNotEmpty() }

        assertTrue("automatico" in trabajos().single().tags)
    }

    @Test
    fun `el estado combina lo instalado con la descarga que espera red`() {
        instalarArchivo()
        repositorio.descargar()

        val estado = esperarEstado { it.descarga != null }

        assertEquals(800L, estado.instalados?.bytes)
        assertEquals("v1", estado.instalados?.version)
        assertTrue(estado.instalados?.conSatelite == true)
        assertTrue(estado.descarga?.esperandoRed == true)
    }

    @Test
    fun `cancelar quita el trabajo y conserva los parciales`() {
        val parcial = File(entorno.config.directorioNuevo, "mallorca.pmtiles.part")
        parcial.parentFile?.mkdirs()
        parcial.writeText("parcial")
        repositorio.descargar()
        esperar { trabajos().isNotEmpty() }

        repositorio.cancelar()

        esperar { trabajos().all { it.state == WorkInfo.State.CANCELLED } }
        assertTrue(parcial.isFile)
        assertNull(esperarEstado { it.descarga == null }.descarga)
    }

    @Test
    fun `mientras la siembra no termina el estado esta preparando y despues ya no`() {
        val lista = MutableStateFlow(false)
        val siembra =
            object : EsperaDatosSembrados {
                override suspend fun esperar() = Unit

                override val listo = lista
            }
        val conSiembra =
            RepositorioDatosMapaImpl(
                dao,
                base.ajustesMapasDao(),
                workManager,
                entorno.config,
                Dispatchers.IO,
                siembra,
            )

        assertTrue(runBlocking { withTimeout(ESPERA_MS) { conSiembra.estado.first { it.preparando } } }.preparando)
        lista.value = true
        assertFalse(runBlocking { withTimeout(ESPERA_MS) { conSiembra.estado.first { !it.preparando } } }.preparando)
        assertFalse("Sin siembra configurada nunca está preparando", esperarEstado { true }.preparando)
    }

    @Test
    fun `sembrado sin satelite el estado esta instalado y sin satelite`() {
        instalarArchivo(conSatelite = false)

        val instalados = requireNotNull(esperarEstado { true }.instalados)

        assertEquals(300L, instalados.bytes)
        assertFalse(instalados.conSatelite)
    }

    @Test
    fun `sin lo sembrado completo no hay datos instalados aunque haya algun registro`() {
        runBlocking {
            dao.registrarInstalacion(
                archivos = listOf(ArchivoInstaladoEntity("vias", "vias-mallorca.bin", "d".repeat(64), 100, "v1")),
                idsBorrados = emptyList(),
                version = "v1",
                avisar = false,
            )
        }

        assertNull(esperarEstado { true }.instalados)
    }

    @Test
    fun `borrar quita solo el satelite y respeta lo sembrado y los ajustes`() {
        escribirEstilos(conSatelite = true)
        File(entorno.mapa, "mallorca.pmtiles").writeText("sembrado")
        File(entorno.mapa, "pnoa-mallorca-z17.pmtiles").writeText("z17")
        File(entorno.config.directorioNuevo, "pnoa-mallorca-z16.pmtiles.part").also {
            it.parentFile?.mkdirs()
            it.writeText("parcial")
        }
        instalarArchivo()
        runBlocking { repositorio.guardarAjustes(AjustesMapas(soloWifi = false)) }

        runBlocking { repositorio.borrar() }

        assertFalse(File(entorno.mapa, "pnoa-mallorca-z16.pmtiles").exists())
        assertFalse(File(entorno.mapa, "pnoa-mallorca-z17.pmtiles").exists())
        assertFalse(entorno.config.directorioNuevo.exists())
        assertEquals("mallorca.pmtiles sigue", "sembrado", File(entorno.mapa, "mallorca.pmtiles").readText())
        assertTrue(File(entorno.mapa, "vectorial.json").isFile)
        assertEquals(listOf("estilo", "vectorial", "vias"), runBlocking { dao.archivos() }.map { it.id })
        val instalados = requireNotNull(esperarEstado { true }.instalados)
        assertFalse(instalados.conSatelite)
        assertEquals("v1", instalados.version)
        assertNull(runBlocking { repositorio.estilos() }?.satelite)
        assertFalse("Los ajustes se conservan", runBlocking { repositorio.ajustes.first() }.soloWifi)
    }

    @Test
    fun `el tamano del satelite es la suma de sus entradas en el manifest remoto`() {
        val publicados = entorno.publicar("datos-2026.11.01-1")

        val esperado = publicados.filter { it.id.startsWith("satelite") }.sumOf { it.bytes }

        assertTrue(esperado > 0)
        assertEquals(esperado, runBlocking { repositorio.tamanoSatelite() })
    }

    @Test
    fun `el tamano del satelite suma varias entradas satelite y no cuenta lo demas`() {
        val v = "datos-2026.11.01-1"
        val contenidos = PaqueteDePrueba.contenidos(v)
        val extra = entorno.archivo("satelite-z17", PaqueteDePrueba.aleatorio(8, 7_000), v)
        val principal = entorno.archivo("satelite", contenidos.getValue("satelite"), v)
        val resto = listOf("vectorial", "estilo", "vias").map { entorno.archivo(it, contenidos.getValue(it), v) }
        val json =
            (resto + principal + extra).joinToString(
                prefix = """{"esquema":1,"version":"$v","generado":"2026-11-01T00:00:00Z","archivos":[""",
                postfix = "]}",
            ) { a ->
                """{"id":"${a.id}","nombre":"${a.nombre}","url":"${a.url}","bytes":${a.bytes},""" +
                    """"sha256":"${a.sha256}","version":"${a.version}","atribucion":"x","licencia":"y"}"""
            }
        entorno.publicarManifestCrudo(json)

        assertEquals(principal.bytes + extra.bytes, runBlocking { repositorio.tamanoSatelite() })
    }

    @Test
    fun `el tamano del satelite suma el z16 del manifest y el z17 del manifest extra`() {
        val archivos = entorno.publicar("datos-2026.11.01-1")
        val z17 = entorno.publicarExtra("datos-2026.11.01-1").single()

        val z16 = archivos.first { it.id == "satelite" }

        assertEquals(z16.bytes + z17.bytes, runBlocking { repositorio.tamanoSatelite() })
    }

    @Test
    fun `con el z16 instalado el tamano pendiente es solo el del z17`() {
        val archivos = entorno.publicar("datos-2026.11.01-1")
        val z17 = entorno.publicarExtra("datos-2026.11.01-1").single()
        val z16 = archivos.first { it.id == "satelite" }
        runBlocking {
            dao.registrarInstalacion(
                listOf(ArchivoInstaladoEntity("satelite", z16.nombre, z16.sha256, z16.bytes, "v1")),
                emptyList(),
                "v1",
                avisar = false,
            )
        }

        assertEquals(z17.bytes, runBlocking { repositorio.tamanoSatelite() })
    }

    @Test
    fun `con z16 y z17 instalados no falta nada`() {
        val archivos = entorno.publicar("datos-2026.11.01-1")
        val z17 = entorno.publicarExtra("datos-2026.11.01-1").single()
        val z16 = archivos.first { it.id == "satelite" }
        runBlocking {
            dao.registrarInstalacion(
                listOf(
                    ArchivoInstaladoEntity("satelite", z16.nombre, z16.sha256, z16.bytes, "v1"),
                    ArchivoInstaladoEntity("satelite-z17", z17.nombre, z17.sha256, z17.bytes, "v1"),
                ),
                emptyList(),
                "v1",
                avisar = false,
            )
        }

        assertEquals(0L, runBlocking { repositorio.tamanoSatelite() })
    }

    @Test
    fun `con el manifest extra ilegible el tamano es el del z16`() {
        val archivos = entorno.publicar("datos-2026.11.01-1")
        entorno.publicarManifestExtraCrudo("no es json")

        assertEquals(archivos.first { it.id == "satelite" }.bytes, runBlocking { repositorio.tamanoSatelite() })
    }

    @Test
    fun `una fila de ajustes guardada con 1 GB se lee como 2 GB`() {
        val unGb = 1024L * 1024 * 1024
        runBlocking { base.ajustesMapasDao().guardarAjustes(AjustesMapasEntity(soloWifi = true, limiteBytes = unGb)) }

        assertEquals(2 * unGb, runBlocking { repositorio.ajustes.first() }.limiteBytes)
    }

    @Test
    fun `sin manifest remoto el tamano del satelite es null`() {
        assertNull(runBlocking { repositorio.tamanoSatelite() })
    }

    @Test
    fun `descartar el aviso lo limpia`() {
        runBlocking {
            dao.registrarInstalacion(emptyList(), emptyList(), "v2", avisar = true)
            assertEquals("v2", repositorio.estado.first().avisoActualizado)

            repositorio.descartarAviso()

            assertNull(repositorio.estado.first().avisoActualizado)
        }
    }

    private fun trabajosComplementos(): List<WorkInfo> =
        workManager.getWorkInfosForUniqueWork(TrabajoDatosMapa.NOMBRE_TRABAJO_COMPLEMENTOS).get()

    private fun instalarComplementos(vararg ids: String) =
        runBlocking {
            dao.registrarInstalacion(
                archivos = ids.map { ArchivoInstaladoEntity(it, "$it.bin", "e".repeat(64), 1_000, "v1") },
                idsBorrados = emptyList(),
                version = "v1",
                avisar = false,
            )
        }

    private fun repositorioConElegidos(vararg elegidos: Complemento) =
        RepositorioDatosMapaImpl(
            dao,
            base.ajustesMapasDao(),
            workManager,
            entorno.config,
            Dispatchers.IO,
            complementosElegidos = { elegidos.toSet() },
        )

    @Test
    fun `descargar el relieve va a su cola con cualquier red y sin tocar la del paquete`() {
        instalarArchivo(conSatelite = false)

        repositorio.descargar(Complemento.RELIEVE)
        esperar { trabajosComplementos().isNotEmpty() }

        val trabajo = trabajosComplementos().single()
        assertEquals(NetworkType.CONNECTED, trabajo.constraints.requiredNetworkType)
        assertTrue("complemento:RELIEVE" in trabajo.tags)
        assertTrue(trabajos().isEmpty())
        val estado = esperarEstado { it.complemento(Complemento.RELIEVE).descarga != null }
        assertTrue(estado.complemento(Complemento.RELIEVE).descarga?.esperandoRed == true)
        assertNull(estado.descarga)
    }

    @Test
    fun `un satelite esperando WiFi no frena la descarga de la voz`() {
        repositorio.descargar()
        repositorio.descargar(Complemento.VOZ_SHARVARD)
        esperar { trabajos().isNotEmpty() && trabajosComplementos().isNotEmpty() }

        assertEquals(NetworkType.UNMETERED, trabajos().single().constraints.requiredNetworkType)
        assertEquals(NetworkType.CONNECTED, trabajosComplementos().single().constraints.requiredNetworkType)
    }

    @Test
    fun `pedir dos veces la misma voz no duplica el trabajo`() {
        repositorio.descargar(Complemento.VOZ_SHARVARD)
        esperar { trabajosComplementos().isNotEmpty() }

        repositorio.descargar(Complemento.VOZ_SHARVARD)
        Thread.sleep(300)

        assertEquals(1, trabajosComplementos().count { !it.state.isFinished })
    }

    @Test
    fun `la peticion del usuario sustituye a la descarga automatica que espera WiFi`() {
        instalarArchivo(conSatelite = false)
        repositorio.descargarConWifi(Complemento.VOZ_SHARVARD)
        esperar { trabajosComplementos().isNotEmpty() }
        assertEquals(NetworkType.UNMETERED, trabajosComplementos().single().constraints.requiredNetworkType)

        repositorio.descargar(Complemento.VOZ_SHARVARD)

        esperar {
            trabajosComplementos().any {
                it.state == WorkInfo.State.ENQUEUED && it.constraints.requiredNetworkType == NetworkType.CONNECTED
            }
        }
    }

    @Test
    fun `al abrir la app la voz sharvard elegida y sin instalar se baja sola con WiFi`() {
        instalarArchivo(conSatelite = false)
        instalarComplementos("relieve", "curvas")

        repositorioConElegidos(Complemento.VOZ_SHARVARD, Complemento.RELIEVE).comprobarActualizacion()
        esperar { trabajosComplementos().isNotEmpty() }

        val trabajo = trabajosComplementos().single()
        assertTrue("complemento:VOZ_SHARVARD" in trabajo.tags)
        assertEquals(NetworkType.UNMETERED, trabajo.constraints.requiredNetworkType)
    }

    @Test
    fun `con la voz elegida ya instalada no se vuelve a bajar`() {
        instalarArchivo(conSatelite = false)
        instalarComplementos("voz-sharvard", "voz-sharvard-tokens")

        repositorioConElegidos(Complemento.VOZ_SHARVARD).comprobarActualizacion()
        esperar { trabajos().isNotEmpty() }
        Thread.sleep(300)

        assertTrue(trabajosComplementos().isEmpty())
    }

    @Test
    fun `el estado dice si el relieve y la voz estan instalados y cuanto ocupan`() {
        instalarArchivo(conSatelite = false)
        assertFalse(esperarEstado { true }.complemento(Complemento.RELIEVE).instalado)

        instalarComplementos("relieve", "curvas", "voz-sharvard")

        val estado = esperarEstado { it.instalados?.conRelieve == true }
        assertTrue(estado.complemento(Complemento.RELIEVE).instalado)
        assertEquals(2_000L, estado.complemento(Complemento.RELIEVE).bytes)
        assertFalse("falta el archivo de tokens", estado.instalados?.conVozSharvard == true)
        assertFalse(estado.complemento(Complemento.VOZ_SHARVARD).instalado)
    }

    @Test
    fun `borrar la voz libera sus archivos y respeta el resto`() {
        instalarArchivo()
        instalarComplementos("relieve", "curvas", "voz-sharvard", "voz-sharvard-tokens")
        entorno.mapa.mkdirs()
        listOf(
            "voz-sharvard.bin",
            "voz-sharvard-tokens.bin",
            "relieve.bin",
            "mallorca.pmtiles",
            "pnoa-mallorca-z16.pmtiles",
        ).forEach { File(entorno.mapa, it).writeText("x") }

        runBlocking { repositorio.borrar(Complemento.VOZ_SHARVARD) }

        assertFalse(File(entorno.mapa, "voz-sharvard.bin").exists())
        assertFalse(File(entorno.mapa, "voz-sharvard-tokens.bin").exists())
        assertTrue(File(entorno.mapa, "relieve.bin").exists())
        assertTrue(File(entorno.mapa, "mallorca.pmtiles").exists())
        assertTrue(File(entorno.mapa, "pnoa-mallorca-z16.pmtiles").exists())
        assertEquals(
            listOf("curvas", "estilo", "relieve", "satelite", "vectorial", "vias"),
            runBlocking { dao.archivos() }.map { it.id },
        )
        assertFalse(esperarEstado { true }.complemento(Complemento.VOZ_SHARVARD).instalado)
    }

    @Test
    fun `borrar el relieve y las curvas no toca el satelite`() {
        instalarArchivo()
        instalarComplementos("relieve", "curvas")
        entorno.mapa.mkdirs()
        listOf(
            "relieve.bin",
            "curvas.bin",
            "pnoa-mallorca-z16.pmtiles",
        ).forEach { File(entorno.mapa, it).writeText("x") }

        runBlocking { repositorio.borrar(Complemento.RELIEVE) }

        assertFalse(File(entorno.mapa, "relieve.bin").exists())
        assertFalse(File(entorno.mapa, "curvas.bin").exists())
        assertTrue(File(entorno.mapa, "pnoa-mallorca-z16.pmtiles").exists())
        val instalados = requireNotNull(esperarEstado { true }.instalados)
        assertFalse(instalados.conRelieve)
        assertTrue(instalados.conSatelite)
    }

    @Test
    fun `sin los archivos de relieve el estilo no apunta a ellos y con ellos si`() {
        entorno.mapa.mkdirs()
        val fuentes =
            """"protomaps":{"type":"vector","url":"pmtiles://{{DIR}}/mallorca.pmtiles"},""" +
                """"relieve":{"type":"raster-dem","url":"pmtiles://{{DIR}}/relieve-mallorca.pmtiles"},""" +
                """"curvas":{"type":"vector","url":"pmtiles://{{DIR}}/curvas-mallorca.pmtiles"}"""
        val capas =
            """[{"id":"calles","type":"line","source":"protomaps"},""" +
                """{"id":"relieve-sombra","type":"hillshade","source":"relieve"},""" +
                """{"id":"curvas-linea","type":"line","source":"curvas"}]"""
        File(entorno.mapa, "vectorial.json").writeText("""{"version":8,"sources":{$fuentes},"layers":$capas}""")

        val sin = requireNotNull(runBlocking { repositorio.estilos() }).vectorial
        assertFalse(sin.contains("relieve"))
        assertFalse(sin.contains("curvas"))
        assertTrue(sin.contains("protomaps"))

        File(entorno.mapa, "relieve-mallorca.pmtiles").writeText("r")
        File(entorno.mapa, "curvas-mallorca.pmtiles").writeText("c")

        val con = requireNotNull(runBlocking { repositorio.estilos() }).vectorial
        assertTrue(con.contains("relieve-sombra"))
        assertTrue(con.contains("curvas-linea"))
    }

    @Test
    fun `el tamano pendiente de la voz es la suma de sus dos archivos del manifest`() {
        val voz =
            mapOf(
                "voz-sharvard" to PaqueteDePrueba.aleatorio(23, 80_000),
                "voz-sharvard-tokens" to PaqueteDePrueba.aleatorio(24, 900),
            )
        entorno.publicar("datos-2026.11.01-1", PaqueteDePrueba.contenidos("v1") + voz)

        assertEquals(80_900L, runBlocking { repositorio.tamanoPendiente(Complemento.VOZ_SHARVARD) })
        instalarComplementos("voz-sharvard")
        assertEquals(900L, runBlocking { repositorio.tamanoPendiente(Complemento.VOZ_SHARVARD) })
    }

    private companion object {
        const val ESPERA_MS = 10_000L
    }
}
