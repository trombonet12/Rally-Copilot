package app.trombito.rallycopilot.motor.importar

import app.trombito.rallycopilot.motor.alertas.LectorXmlStax
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

class LectorGpxTest {
    private fun leer(xml: String) = LectorGpx.leer(LectorXmlStax(ByteArrayInputStream(xml.toByteArray())))

    private fun recurso(nombre: String): InputStream = checkNotNull(javaClass.getResourceAsStream("/importar/$nombre"))

    private fun ok(r: ResultadoImportacion) = r as ResultadoImportacion.Ok

    private fun gpx(cuerpo: String) = """<gpx version="1.1" xmlns="http://www.topografix.com/GPX/1/1">$cuerpo</gpx>"""

    @Test
    fun `un GPX tipo Strava trae 200 puntos con elevacion y el nombre del trk`() {
        val r = ok(LectorGpx.leer(LectorXmlStax(recurso("strava.gpx"))))
        assertEquals("Mañana por la Ma-13", r.nombre)
        assertEquals(200, r.puntos.size)
        assertEquals(120.0, r.puntos.first().alt ?: 0.0, 1e-9)
        assertEquals(120.0 + 199 * 0.4, r.puntos.last().alt ?: 0.0, 0.05)
    }

    @Test
    fun `un GPX con prefijo de espacio de nombres y solo ruta usa los rtept y el nombre de la ruta`() {
        val r = ok(LectorGpx.leer(LectorXmlStax(recurso("wikiloc-ruta.gpx"))))
        assertEquals("Ruta Wikiloc", r.nombre)
        assertEquals(122, r.puntos.size)
    }

    @Test
    fun `los gpx reales del repositorio se leen enteros`() {
        val real = java.io.File("../docs/rutas/ma-13-santa-maria-palma.gpx")
        val r = ok(LectorGpx.leer(LectorXmlStax(real.inputStream())))
        assertEquals(364, r.puntos.size)
        assertEquals("Ma-13", r.nombre)
        assertTrue(r.puntos.all { it.alt == null })
    }

    @Test
    fun `los trkseg de un mismo trk se concatenan aunque haya un salto`() {
        val r =
            ok(
                leer(
                    gpx(
                        """<trk><name>T</name>
                        <trkseg><trkpt lat="39.60" lon="2.90"/><trkpt lat="39.61" lon="2.90"/></trkseg>
                        <trkseg><trkpt lat="39.70" lon="2.90"/><trkpt lat="39.71" lon="2.90"/></trkseg></trk>""",
                    ),
                ),
            )
        assertEquals(4, r.puntos.size)
    }

    @Test
    fun `varios trk lejanos dan el mas largo y los contiguos se unen`() {
        val lejanos =
            gpx(
                """<trk><name>corto</name><trkseg><trkpt lat="39.60" lon="2.90"/><trkpt lat="39.601" lon="2.90"/></trkseg></trk>
                <trk><name>largo</name><trkseg>
                <trkpt lat="39.70" lon="2.90"/><trkpt lat="39.72" lon="2.90"/></trkseg></trk>""",
            )
        val r = ok(leer(lejanos))
        assertEquals("largo", r.nombre)
        assertEquals(2, r.puntos.size)
        val contiguos =
            gpx(
                """<trk><name>a</name><trkseg><trkpt lat="39.60" lon="2.90"/><trkpt lat="39.61" lon="2.90"/></trkseg></trk>
                <trk><name>b</name><trkseg>
                <trkpt lat="39.6101" lon="2.90"/><trkpt lat="39.62" lon="2.90"/></trkseg></trk>""",
            )
        assertEquals(4, ok(leer(contiguos)).puntos.size)
    }

    @Test
    fun `los waypoints se ignoran si hay traza y se usan si no hay nada mas`() {
        val conTraza =
            gpx(
                """<wpt lat="39.9" lon="2.9"><name>Bar</name></wpt>
                <trk><trkseg><trkpt lat="39.60" lon="2.90"/><trkpt lat="39.61" lon="2.90"/></trkseg></trk>""",
            )
        assertEquals(2, ok(leer(conTraza)).puntos.size)
        val soloWpt = gpx("""<wpt lat="39.60" lon="2.9"/><wpt lat="39.62" lon="2.9"/><wpt lat="39.64" lon="2.9"/>""")
        assertEquals(3, ok(leer(soloWpt)).puntos.size)
    }

    @Test
    fun `los puntos con coordenadas ilegibles o fuera de rango se saltan`() {
        val r =
            ok(
                leer(
                    gpx(
                        """<trk><trkseg><trkpt lat="39.60" lon="2.90"/><trkpt lat="x" lon="2.90"/>
                        <trkpt lat="95" lon="2.90"/><trkpt lat="39.61" lon="2.90"/><trkpt lon="2.9"/></trkseg></trk>""",
                    ),
                ),
            )
        assertEquals(2, r.puntos.size)
    }

    @Test
    fun `sin elevacion o con elevacion rota la altura es null`() {
        val r =
            ok(
                leer(
                    gpx(
                        """<trk><trkseg><trkpt lat="39.60" lon="2.90"><ele>abc</ele></trkpt>
                        <trkpt lat="39.61" lon="2.90"/></trkseg></trk>""",
                    ),
                ),
            )
        assertNull(r.puntos[0].alt)
        assertNull(r.puntos[1].alt)
    }

    @Test
    fun `el nombre sale de metadata si el trk no lo trae`() {
        val r =
            ok(
                leer(
                    gpx(
                        """<metadata><name>Mi ruta</name></metadata>
                        <trk><trkseg><trkpt lat="39.60" lon="2.90"/><trkpt lat="39.61" lon="2.90"/></trkseg></trk>""",
                    ),
                ),
            )
        assertEquals("Mi ruta", r.nombre)
    }

    @Test
    fun `una traza de un solo punto es un error SIN_TRAZA`() {
        val r = leer(gpx("""<trk><trkseg><trkpt lat="39.60" lon="2.90"/></trkseg></trk>"""))
        assertEquals(MotivoErrorImportacion.SIN_TRAZA, (r as ResultadoImportacion.Error).motivo)
    }

    @Test
    fun `un XML que no es GPX es SIN_TRAZA`() {
        val r = leer("""<html><body>hola</body></html>""")
        assertEquals(MotivoErrorImportacion.SIN_TRAZA, (r as ResultadoImportacion.Error).motivo)
    }

    @Test
    fun `un XML truncado o roto es XML_MAL_FORMADO`() {
        val roto = leer(gpx("""<trk><trkseg><trkpt lat="39.60" lon="2.90"/><trkpt lat="39.61" lon="2.9""").dropLast(12))
        assertEquals(MotivoErrorImportacion.XML_MAL_FORMADO, (roto as ResultadoImportacion.Error).motivo)
        val vacio = leer("")
        assertEquals(MotivoErrorImportacion.XML_MAL_FORMADO, (vacio as ResultadoImportacion.Error).motivo)
    }

    @Test
    fun `una entidad no definida no se expande y es un error`() {
        val xml =
            """<?xml version="1.0"?><!DOCTYPE gpx [<!ENTITY a "aaaa">]><gpx><trk><name>&a;&a;</name></trk></gpx>"""
        val r = leer(xml)
        assertTrue(r is ResultadoImportacion.Error || (r as ResultadoImportacion.Ok).nombre != "aaaaaaaa")
    }

    @Test
    fun `un fallo del flujo es ENTRADA_ILEGIBLE`() {
        val roto =
            object : InputStream() {
                override fun read(): Int = throw IOException("disco")
            }
        val r = LectorGpx.leer(LectorXmlStax(roto))
        assertEquals(MotivoErrorImportacion.ENTRADA_ILEGIBLE, (r as ResultadoImportacion.Error).motivo)
    }
}
