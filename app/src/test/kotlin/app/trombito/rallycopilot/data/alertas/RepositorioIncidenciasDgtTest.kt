package app.trombito.rallycopilot.data.alertas

import app.trombito.rallycopilot.data.datosmapa.ClienteHttp
import app.trombito.rallycopilot.data.datosmapa.ConfigDatosMapa
import app.trombito.rallycopilot.data.datosmapa.EntornoDatosMapa
import app.trombito.rallycopilot.data.datosmapa.ServidorFalso
import app.trombito.rallycopilot.domain.alertas.ErrorIncidencias
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.URL
import java.time.Instant
import java.util.zip.GZIPOutputStream

/** Un DATEX II mínimo: una situación puntual en Mallorca por cada `(id, fin)`; `fin` null = sin fecha de fin. */
internal fun datex(vararg situaciones: Pair<String, String?>): String {
    val cuerpo =
        situaciones.joinToString("") { (id, fin) ->
            val finXml = fin?.let { "<com:overallEndTime>$it</com:overallEndTime>" }.orEmpty()
            """
            <sit:situation id="$id">
              <sit:headerInformation><com:informationStatus>real</com:informationStatus></sit:headerInformation>
              <sit:situationRecord xsi:type="sit:RoadOrCarriagewayOrLaneManagement" id="r$id" version="1">
                <sit:validity>
                  <com:validityStatus>active</com:validityStatus>
                  <com:validityTimeSpecification>
                    <com:overallStartTime>2026-10-01T10:00:00.000+02:00</com:overallStartTime>$finXml
                  </com:validityTimeSpecification>
                </sit:validity>
                <sit:cause>
                  <sit:causeType>roadMaintenance</sit:causeType>
                  <sit:detailedCauseType><sit:roadMaintenanceType>roadworks</sit:roadMaintenanceType></sit:detailedCauseType>
                </sit:cause>
                <sit:locationReference xsi:type="loc:PointLocation">
                  <loc:tpegPointLocation>
                    <loc:point xsi:type="loc:TpegNonJunctionPoint">
                      <loc:pointCoordinates><loc:latitude>39.60</loc:latitude><loc:longitude>2.90</loc:longitude></loc:pointCoordinates>
                    </loc:point>
                  </loc:tpegPointLocation>
                </sit:locationReference>
              </sit:situationRecord>
            </sit:situation>
            """.trimIndent()
        }
    return """<?xml version="1.0" encoding="UTF-8"?>
<d2:payload xmlns:com="http://levelC/schema/3/common" xmlns:loc="http://levelC/schema/3/locationReferencing"
  xmlns:d2="http://levelC/schema/3/d2Payload" xmlns:sit="http://levelC/schema/3/situation"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
<com:publicationTime>2026-11-01T14:19:18.632+01:00</com:publicationTime>
$cuerpo
</d2:payload>"""
}

internal fun gzip(texto: String): ByteArray {
    val salida = ByteArrayOutputStream()
    GZIPOutputStream(salida).use { it.write(texto.toByteArray()) }
    return salida.toByteArray()
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RepositorioIncidenciasDgtTest {
    @get:Rule val carpeta = TemporaryFolder()

    private lateinit var entorno: EntornoDatosMapa
    private lateinit var directorio: File
    private var ahoraMs = AHORA_MS

    private val rutaDgt = URL(URL_DATEX_DGT).path

    @Before
    fun preparar() {
        entorno = EntornoDatosMapa(carpeta.newFolder("entorno"))
        directorio = File(carpeta.root, "alertas")
    }

    @After
    fun cerrar() = entorno.close()

    private fun repositorio(
        topeBytes: Long = RepositorioIncidenciasDgt.TOPE_BYTES,
        config: ConfigDatosMapa = entorno.config,
    ) = RepositorioIncidenciasDgt(
        ClienteHttp.paraDgt(config),
        URL_DATEX_DGT,
        directorio,
        { ahoraMs },
        UnconfinedTestDispatcher(),
        topeBytes,
    )

    private fun servir(
        xml: String,
        codigo: Int = 200,
        comoGzip: Boolean = false,
    ) {
        val bytes = if (comoGzip) gzip(xml) else xml.toByteArray()
        val cabeceras = if (comoGzip) mapOf("Content-Encoding" to "gzip") else emptyMap()
        entorno.servidor.servir(rutaDgt, ServidorFalso.Recurso(bytes, codigo = codigo, cabeceras = cabeceras))
    }

    private fun peticiones() = entorno.servidor.peticionesA(rutaDgt).size

    private fun ids(repo: RepositorioIncidenciasDgt) =
        repo.incidencias.value.incidencias
            .map { it.id }

    @Test
    fun `descarga, lee en streaming y guarda la copia con su instante`() =
        runTest {
            servir(datex("1" to null, "2" to "2026-12-01T10:00:00.000+01:00"), comoGzip = true)
            val repo = repositorio()

            repo.refrescar()

            val vigentes = repo.incidencias.value
            assertEquals(listOf("1", "2"), ids(repo))
            assertEquals(Instant.ofEpochMilli(AHORA_MS), vigentes.obtenidasEn)
            assertNull(vigentes.errorUltimo)
            assertTrue(File(directorio, "incidencias-dgt.xml.gz").isFile)
            assertEquals("$AHORA_MS", File(directorio, "incidencias-dgt.instante").readText())
            assertFalse(File(directorio, "incidencias-dgt.xml.gz.tmp").exists())
        }

    @Test
    fun `una app nueva sin red arranca con la copia y su antiguedad`() =
        runTest {
            servir(datex("1" to null))
            repositorio().refrescar()
            entorno.servidor.apagar()
            ahoraMs += 3 * HORA_MS

            val nuevo = repositorio()
            nuevo.refrescar()

            val vigentes = nuevo.incidencias.value
            assertEquals(listOf("1"), ids(nuevo))
            assertEquals(Instant.ofEpochMilli(AHORA_MS), vigentes.obtenidasEn)
            assertEquals(ErrorIncidencias.SIN_RED, vigentes.errorUltimo)
        }

    @Test
    fun `sin red conserva la copia buena y anota el error`() =
        runTest {
            servir(datex("1" to null))
            val repo = repositorio()
            repo.refrescar()
            entorno.servidor.apagar()
            ahoraMs += CINCO_MIN_MS

            repo.refrescar()

            assertEquals(listOf("1"), ids(repo))
            assertEquals(ErrorIncidencias.SIN_RED, repo.incidencias.value.errorUltimo)
            assertEquals(Instant.ofEpochMilli(AHORA_MS), repo.incidencias.value.obtenidasEn)
        }

    @Test
    fun `un error del servidor no borra la copia`() =
        runTest {
            servir(datex("1" to null))
            val repo = repositorio()
            repo.refrescar()
            servir("", codigo = 503)
            ahoraMs += CINCO_MIN_MS

            repo.refrescar()

            assertEquals(ErrorIncidencias.SERVIDOR, repo.incidencias.value.errorUltimo)
            assertEquals(listOf("1"), ids(repo))
        }

    @Test
    fun `datos que no son DATEX no sustituyen a la copia buena`() =
        runTest {
            servir(datex("1" to null))
            val repo = repositorio()
            repo.refrescar()
            servir("<html><body>Mantenimiento</body></html>")
            ahoraMs += CINCO_MIN_MS

            repo.refrescar()

            assertEquals(ErrorIncidencias.DATOS, repo.incidencias.value.errorUltimo)
            assertEquals(listOf("1"), ids(repo))
            entorno.servidor.apagar()
            val otraVez = repositorio().also { it.refrescar() }
            assertEquals(listOf("1"), ids(otraVez))
        }

    @Test
    fun `las caducadas no se muestran ni de la descarga ni de la copia`() =
        runTest {
            val pasado = Instant.ofEpochMilli(AHORA_MS - HORA_MS).toString()
            val futuro = Instant.ofEpochMilli(AHORA_MS + HORA_MS).toString()
            servir(datex("viva" to null, "caducada" to pasado, "por-caducar" to futuro))
            val repo = repositorio()

            repo.refrescar()

            assertEquals(listOf("viva", "por-caducar"), ids(repo))

            // La caducidad se vuelve a aplicar al leer la copia, con el reloj de ese momento.
            entorno.servidor.apagar()
            ahoraMs += 2 * HORA_MS
            val sinRed = repositorio().also { it.refrescar() }
            assertEquals(listOf("viva"), ids(sinRed))
        }

    @Test
    fun `una respuesta que pasa del tope se rechaza y no se guarda`() =
        runTest {
            servir(datex("1" to null, "2" to null, "3" to null))
            val repo = repositorio(topeBytes = 500)

            repo.refrescar()

            assertEquals(ErrorIncidencias.DEMASIADO_GRANDE, repo.incidencias.value.errorUltimo)
            assertTrue(
                repo.incidencias.value.incidencias
                    .isEmpty(),
            )
            assertFalse(File(directorio, "incidencias-dgt.xml.gz").exists())
            assertFalse(File(directorio, "incidencias-dgt.xml.gz.tmp").exists())
        }

    @Test
    fun `una bomba de gzip tambien se corta por el tope de descomprimido`() =
        runTest {
            servir("<x>" + "a".repeat(200_000) + "</x>", comoGzip = true)
            val repo = repositorio(topeBytes = 50_000)

            repo.refrescar()

            assertEquals(ErrorIncidencias.DEMASIADO_GRANDE, repo.incidencias.value.errorUltimo)
        }

    @Test
    fun `como mucho una descarga cada 5 minutos y 1 minuto tras un fallo`() =
        runTest {
            servir(datex("1" to null))
            val repo = repositorio()
            repo.refrescar()
            ahoraMs += CINCO_MIN_MS - 1
            repo.refrescar()
            assertEquals(1, peticiones())

            ahoraMs += 1
            repo.refrescar()
            assertEquals(2, peticiones())

            servir("", codigo = 500)
            ahoraMs += CINCO_MIN_MS
            repo.refrescar()
            assertEquals(3, peticiones())
            ahoraMs += 59_000
            repo.refrescar()
            assertEquals(3, peticiones())
            ahoraMs += 1_000
            repo.refrescar()
            assertEquals(4, peticiones())
        }

    @Test
    fun `un OutOfMemoryError durante la descarga no tumba la app y conserva la copia`() =
        runTest {
            servir(datex("1" to null))
            val repo = repositorio()
            repo.refrescar()
            ahoraMs += CINCO_MIN_MS
            val sinMemoria = entorno.config.copy(abrirConexion = { throw OutOfMemoryError("prueba") })

            val otro = repositorio(config = sinMemoria)
            otro.refrescar()

            assertEquals(ErrorIncidencias.DEMASIADO_GRANDE, otro.incidencias.value.errorUltimo)
            assertEquals(listOf("1"), ids(otro))
            assertFalse(File(directorio, "incidencias-dgt.xml.gz.tmp").exists())
        }

    @Test
    fun `un feed con DOCTYPE y entidades se rechaza como datos no validos`() =
        runTest {
            servir(
                """<?xml version="1.0"?><!DOCTYPE d [<!ENTITY a "0123456789"><!ENTITY b "&a;&a;&a;&a;&a;&a;&a;&a;">]>
                <d2:payload xmlns:d2="http://levelC/schema/3/d2Payload" xmlns:com="http://levelC/schema/3/common">
                <com:publicationTime>&b;</com:publicationTime></d2:payload>""",
            )
            val repo = repositorio()

            repo.refrescar()

            assertEquals(ErrorIncidencias.DATOS, repo.incidencias.value.errorUltimo)
            assertTrue(ids(repo).isEmpty())
        }

    @Test
    fun `solo habla https con nap dgt es`() {
        val cliente = ClienteHttp.paraDgt(entorno.config)
        assertThrows(IOException::class.java) { cliente.flujo("http://nap.dgt.es/datex2/x.xml", 100) }
        assertThrows(IOException::class.java) { cliente.flujo("https://github.com/datex2/x.xml", 100) }
        assertThrows(IOException::class.java) { cliente.flujo("https://nap.dgt.es.evil.example/x.xml", 100) }
        assertEquals(0, entorno.servidor.peticiones.size)
    }

    private companion object {
        const val AHORA_MS = 1_793_000_000_000L
        const val HORA_MS = 3_600_000L
        const val CINCO_MIN_MS = 5 * 60_000L
    }
}
