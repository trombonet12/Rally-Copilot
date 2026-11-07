package app.trombito.rallycopilot.data.importar

import android.net.Uri
import app.trombito.rallycopilot.data.escucha.EdicionesFalsas
import app.trombito.rallycopilot.data.tramos.GuardadorTramoArchivo
import app.trombito.rallycopilot.data.tramos.GuardadorTramos
import app.trombito.rallycopilot.domain.importar.MotivoFalloImportar
import app.trombito.rallycopilot.domain.importar.ResultadoImportar
import app.trombito.rallycopilot.domain.importar.ResultadoPreparar
import app.trombito.rallycopilot.ui.comun.FalsoRepositorioTramos
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.InputStream

@RunWith(RobolectricTestRunner::class)
class ImportadorTramosTest {
    private val repositorio = FalsoRepositorioTramos()
    private var automatico: String? = "Ma-13 · Santa María"
    private val guardador = GuardadorTramos(repositorio) { automatico }
    private val guardadorArchivo = GuardadorTramoArchivo(repositorio, { automatico }, EdicionesFalsas())

    /** Abridor falso: cada Uri se resuelve a un documento fabricado a mano. */
    private class AbridorFalso(
        private val documentos: Map<String, () -> DocumentoAbierto?>,
    ) : AbridorDocumentos {
        val abiertos = mutableListOf<String>()

        override fun abrir(uri: Uri): DocumentoAbierto? {
            abiertos += uri.toString()
            return documentos[uri.toString()]?.invoke()
        }
    }

    private fun recurso(nombre: String): ByteArray =
        checkNotNull(javaClass.getResourceAsStream("/importar/$nombre")).use(InputStream::readBytes)

    private fun doc(
        bytes: ByteArray,
        nombre: String?,
        tamano: Long? = bytes.size.toLong(),
    ) = { DocumentoAbierto(ByteArrayInputStream(bytes), nombre, tamano) }

    private fun importar(
        uri: String,
        documento: (() -> DocumentoAbierto?)?,
    ): ResultadoImportar {
        val abridor = AbridorFalso(if (documento == null) emptyMap() else mapOf(uri to documento))
        return runBlocking { ImportadorTramos(abridor, guardador, guardadorArchivo).importar(uri) }
    }

    @Test
    fun `un GPX de Strava crea un tramo sin pasadas con el nombre del trk`() {
        // 200 puntos de la Ma-13 son unos 5 km: de sobra para ser tramo.
        val r = importar("content://doc/1", doc(recurso("strava.gpx"), "Morning_Ride.gpx"))
        assertTrue(r.toString(), r is ResultadoImportar.Importado)
        val guardado = repositorio.sinPasadas.single()
        assertEquals("Mañana por la Ma-13", guardado.nombre)
        assertEquals("Ma-13 · Santa María", guardado.nombreAutomatico)
        assertTrue(guardado.tramo.traza.size >= 2)
        assertEquals((r as ResultadoImportar.Importado).tramoId, guardado.tramo.id)
        assertTrue(repositorio.pasadasPorTramo[guardado.tramo.id].isNullOrEmpty())
    }

    @Test
    fun `un KML se reconoce por el contenido aunque el nombre no tenga extension`() {
        val r = importar("content://doc/2", doc(recurso("ma-13.kml"), "descarga"))
        assertEquals("Ma-13", (r as ResultadoImportar.Importado).nombre)
    }

    @Test
    fun `sin nombre en el archivo se usa el nombre del archivo sin extension`() {
        val gpx = """<gpx><trk><trkseg>${puntosRecta()}</trkseg></trk></gpx>""".toByteArray()
        val r = importar("content://doc/3", doc(gpx, "ruta_del_domingo.gpx"))
        assertEquals("ruta del domingo", (r as ResultadoImportar.Importado).nombre)
        assertEquals("ruta del domingo", repositorio.sinPasadas.single().nombre)
    }

    @Test
    fun `sin nombre de ningun tipo queda el automatico`() {
        val gpx = """<gpx><trk><trkseg>${puntosRecta()}</trkseg></trk></gpx>""".toByteArray()
        val r = importar("content://doc/4", doc(gpx, null))
        assertEquals("Ma-13 · Santa María", (r as ResultadoImportar.Importado).nombre)
        assertNull(repositorio.sinPasadas.single().nombre)
    }

    @Test
    fun `un archivo de mas de 20 MB declarado se rechaza sin leerlo`() {
        val r =
            importar("content://doc/5", doc(recurso("strava.gpx"), "a.gpx", tamano = ImportadorTramos.TOPE_BYTES + 1))
        assertEquals(ResultadoImportar.Fallo(MotivoFalloImportar.DEMASIADO_GRANDE), r)
        assertTrue(repositorio.sinPasadas.isEmpty())
    }

    @Test
    fun `un archivo que miente sobre su tamano se corta al leer`() {
        val enorme =
            object : InputStream() {
                private val cabecera = "<gpx><trk><trkseg>".toByteArray()
                private var i = 0L

                override fun read(): Int {
                    val b = if (i < cabecera.size) cabecera[i.toInt()].toInt() else ' '.code
                    i++
                    return b
                }
            }
        val r = importar("content://doc/6", { DocumentoAbierto(enorme, "a.gpx", null) })
        assertEquals(ResultadoImportar.Fallo(MotivoFalloImportar.DEMASIADO_GRANDE), r)
    }

    @Test
    fun `solo se aceptan content Uri`() {
        val abridor = AbridorFalso(emptyMap())
        val r =
            runBlocking {
                ImportadorTramos(
                    abridor,
                    guardador,
                    guardadorArchivo,
                ).importar("file:///data/data/x/databases/a.gpx")
            }
        assertEquals(ResultadoImportar.Fallo(MotivoFalloImportar.NO_SE_PUEDE_ABRIR), r)
        assertTrue("No debe ni intentar abrirlo", abridor.abiertos.isEmpty())
    }

    @Test
    fun `un Uri que no se puede abrir es NO_SE_PUEDE_ABRIR`() {
        assertEquals(
            ResultadoImportar.Fallo(MotivoFalloImportar.NO_SE_PUEDE_ABRIR),
            importar("content://doc/7", null),
        )
    }

    @Test
    fun `un archivo que no es GPX ni KML es FORMATO_DESCONOCIDO`() {
        val r = importar("content://doc/8", doc("hola, esto no es XML".toByteArray(), "notas.txt"))
        assertEquals(ResultadoImportar.Fallo(MotivoFalloImportar.FORMATO_DESCONOCIDO), r)
    }

    @Test
    fun `un GPX truncado es ARCHIVO_DANADO`() {
        val roto = recurso("strava.gpx").copyOf(2_000)
        val r = importar("content://doc/9", doc(roto, "a.gpx"))
        assertEquals(ResultadoImportar.Fallo(MotivoFalloImportar.ARCHIVO_DANADO), r)
    }

    @Test
    fun `un GPX sin traza es SIN_TRAZA y uno de menos de un kilometro es CORTO`() {
        val vacio =
            importar("content://doc/10", doc("<gpx><wpt lat=\"39.6\" lon=\"2.9\"/></gpx>".toByteArray(), "a.gpx"))
        assertEquals(ResultadoImportar.Fallo(MotivoFalloImportar.SIN_TRAZA), vacio)
        val corto =
            """<gpx><trk><trkseg><trkpt lat="39.600" lon="2.9"/><trkpt lat="39.601" lon="2.9"/></trkseg></trk></gpx>"""
        assertEquals(
            ResultadoImportar.Fallo(MotivoFalloImportar.CORTO),
            importar("content://doc/11", doc(corto.toByteArray(), "a.gpx")),
        )
    }

    @Test
    fun `un DOCTYPE con entidades se rechaza`() {
        val xml =
            """<?xml version="1.0"?><!DOCTYPE gpx [<!ENTITY a "aaaa">]><gpx><trk><name>&a;</name></trk></gpx>"""
        val r = importar("content://doc/12", doc(xml.toByteArray(), "a.gpx"))
        assertEquals(ResultadoImportar.Fallo(MotivoFalloImportar.ARCHIVO_DANADO), r)
    }

    @Test
    fun `si la base falla es NO_GUARDADO`() {
        repositorio.fallarAlGuardar = true
        val r = importar("content://doc/13", doc(recurso("strava.gpx"), "a.gpx"))
        assertEquals(ResultadoImportar.Fallo(MotivoFalloImportar.NO_GUARDADO), r)
    }

    private fun preparar(
        uri: String,
        documento: (() -> DocumentoAbierto?)?,
    ): ResultadoPreparar {
        val abridor = AbridorFalso(if (documento == null) emptyMap() else mapOf(uri to documento))
        return runBlocking { ImportadorTramos(abridor, guardador, guardadorArchivo).preparar(uri) }
    }

    @Test
    fun `preparar lee el archivo y da su nombre y longitud sin guardar nada hasta confirmar`() {
        val r = preparar("content://doc/20", doc(recurso("strava.gpx"), "Morning_Ride.gpx"))

        val lista = r as ResultadoPreparar.Lista
        assertEquals("Mañana por la Ma-13", lista.nombre)
        assertTrue("Unos 5 km: ${lista.longitudM}", lista.longitudM in 3_000.0..8_000.0)
        assertTrue("No guarda hasta confirmar", repositorio.sinPasadas.isEmpty())

        val guardado = runBlocking { lista.guardar() }

        assertEquals("Mañana por la Ma-13", (guardado as ResultadoImportar.Importado).nombre)
        assertEquals(1, repositorio.sinPasadas.size)
    }

    @Test
    fun `preparar sin nombre en el archivo usa el nombre del documento y los fallos salen sin preguntar`() {
        val gpx = """<gpx><trk><trkseg>${puntosRecta()}</trkseg></trk></gpx>""".toByteArray()
        val lista = preparar("content://doc/21", doc(gpx, "ruta_del_domingo.gpx")) as ResultadoPreparar.Lista
        assertEquals("ruta del domingo", lista.nombre)

        assertEquals(
            ResultadoPreparar.Fallo(MotivoFalloImportar.NO_SE_PUEDE_ABRIR),
            preparar("content://doc/22", null),
        )
        assertEquals(
            ResultadoPreparar.Fallo(MotivoFalloImportar.FORMATO_DESCONOCIDO),
            preparar("content://doc/23", doc("hola".toByteArray(), "a.txt")),
        )
        val corto =
            """<gpx><trk><trkseg><trkpt lat="39.600" lon="2.9"/><trkpt lat="39.601" lon="2.9"/></trkseg></trk></gpx>"""
        assertEquals(
            ResultadoPreparar.Fallo(MotivoFalloImportar.CORTO),
            preparar("content://doc/24", doc(corto.toByteArray(), "a.gpx")),
        )
        assertTrue(repositorio.sinPasadas.isEmpty())
    }

    /** Unos 2,2 km hacia el norte por el meridiano 2,9. */
    private fun puntosRecta() = (0..20).joinToString("") { """<trkpt lat="${39.6 + it * 0.001}" lon="2.9"/>""" }
}
