package app.trombito.rallycopilot.motor.importar

import app.trombito.rallycopilot.motor.alertas.LectorXmlStax
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class LectorKmlTest {
    private fun leer(xml: String) = LectorKml.leer(LectorXmlStax(ByteArrayInputStream(xml.toByteArray())))

    private fun ok(r: ResultadoImportacion) = r as ResultadoImportacion.Ok

    private fun kml(cuerpo: String) =
        """<kml xmlns="http://www.opengis.net/kml/2.2" xmlns:gx="http://www.google.com/kml/ext/2.2">
            <Document>$cuerpo</Document></kml>"""

    private fun linea(
        nombre: String,
        coords: String,
    ) = "<Placemark><name>$nombre</name><LineString><coordinates>$coords</coordinates></LineString></Placemark>"

    @Test
    fun `el KML exportado de la Ma-13 trae sus 364 puntos y el nombre del Placemark de la linea`() {
        val r = ok(LectorKml.leer(LectorXmlStax(checkNotNull(javaClass.getResourceAsStream("/importar/ma-13.kml")))))
        assertEquals(364, r.puntos.size)
        assertEquals("Ma-13", r.nombre)
        assertTrue("Las alturas a 0 se descartan", r.puntos.all { it.alt == null })
    }

    @Test
    fun `la linea coincide con el GPX del que se exporto`() {
        val kml = ok(LectorKml.leer(LectorXmlStax(checkNotNull(javaClass.getResourceAsStream("/importar/ma-13.kml")))))
        val gpx =
            ok(LectorGpx.leer(LectorXmlStax(java.io.File("../docs/rutas/ma-13-santa-maria-palma.gpx").inputStream())))
        assertEquals(gpx.puntos.first().lat, kml.puntos.first().lat, 1e-6)
        assertEquals(gpx.puntos.last().lon, kml.puntos.last().lon, 1e-6)
    }

    @Test
    fun `el orden es lon lat alt y la altura real se conserva`() {
        val r = ok(leer(kml(linea("A", "2.90,39.60,100 2.91,39.61,120"))))
        assertEquals(39.60, r.puntos[0].lat, 1e-9)
        assertEquals(2.91, r.puntos[1].lon, 1e-9)
        assertEquals(120.0, r.puntos[1].alt ?: 0.0, 1e-9)
    }

    @Test
    fun `las coordenadas separadas por saltos de linea y tabuladores se leen`() {
        val r = ok(leer(kml(linea("A", "\n\t2.90,39.60\n\t2.91,39.61\r\n 2.92,39.62\n"))))
        assertEquals(3, r.puntos.size)
    }

    @Test
    fun `varios Placemark lejanos dan el mas largo con su nombre`() {
        val r = ok(leer(kml(linea("corto", "2.90,39.60 2.90,39.601") + linea("largo", "2.9,39.7 2.9,39.72"))))
        assertEquals("largo", r.nombre)
    }

    @Test
    fun `varios Placemark contiguos se concatenan`() {
        val r = ok(leer(kml(linea("a", "2.9,39.60 2.9,39.61") + linea("b", "2.9,39.6101 2.9,39.62"))))
        assertEquals(4, r.puntos.size)
        assertEquals("a", r.nombre)
    }

    @Test
    fun `un gx Track se lee con sus gx coord`() {
        val cuerpo =
            """<Placemark><name>Track</name><gx:Track><when>2026-10-03T08:00:00Z</when><when>2026-10-03T08:00:03Z</when>
            <gx:coord>2.90 39.60 150</gx:coord><gx:coord>2.91 39.61 160</gx:coord></gx:Track></Placemark>"""
        val r = ok(leer(kml(cuerpo)))
        assertEquals(2, r.puntos.size)
        assertEquals(39.61, r.puntos[1].lat, 1e-9)
        assertEquals(160.0, r.puntos[1].alt ?: 0.0, 1e-9)
    }

    @Test
    fun `una MultiGeometry con dos LineString contiguas se une`() {
        val cuerpo =
            """<Placemark><name>M</name><MultiGeometry>
            <LineString><coordinates>2.9,39.60 2.9,39.61</coordinates></LineString>
            <LineString><coordinates>2.9,39.6101 2.9,39.62</coordinates></LineString></MultiGeometry></Placemark>"""
        assertEquals(4, ok(leer(kml(cuerpo))).puntos.size)
    }

    @Test
    fun `los poligonos y los puntos se ignoran y el nombre cae al del Document`() {
        val cuerpo =
            """<name>Documento</name>
            <Placemark><Point><coordinates>2.9,39.6</coordinates></Point></Placemark>
            <Placemark><Polygon><outerBoundaryIs><LinearRing><coordinates>2.9,39.6 2.9,39.7 3.0,39.7 2.9,39.6</coordinates>
            </LinearRing></outerBoundaryIs></Polygon></Placemark>
            <Placemark><LineString><coordinates>2.9,39.60 2.9,39.61</coordinates></LineString></Placemark>"""
        val r = ok(leer(kml(cuerpo)))
        assertEquals(2, r.puntos.size)
        assertEquals("Documento", r.nombre)
    }

    @Test
    fun `los tokens rotos se saltan`() {
        val r = ok(leer(kml(linea("A", "2.9,39.60 basura 2.9,x 2.9,95 2.91,39.61"))))
        assertEquals(2, r.puntos.size)
    }

    @Test
    fun `sin lineas es SIN_TRAZA y un XML ajeno tambien`() {
        val sinLinea = leer(kml("<Placemark><Point><coordinates>2.9,39.6</coordinates></Point></Placemark>"))
        assertEquals(MotivoErrorImportacion.SIN_TRAZA, (sinLinea as ResultadoImportacion.Error).motivo)
        val ajeno = leer("<gpx><trk/></gpx>")
        assertEquals(MotivoErrorImportacion.SIN_TRAZA, (ajeno as ResultadoImportacion.Error).motivo)
    }

    @Test
    fun `un KML roto es XML_MAL_FORMADO`() {
        val r = leer("""<kml><Document><Placemark><LineString><coordinates>2.9,39.6 2.9,39.7""")
        assertEquals(MotivoErrorImportacion.XML_MAL_FORMADO, (r as ResultadoImportacion.Error).motivo)
    }

    @Test
    fun `una linea de un solo punto no es traza`() {
        val r = leer(kml(linea("A", "2.9,39.6")))
        assertNull((r as? ResultadoImportacion.Ok)?.nombre)
        assertTrue(r is ResultadoImportacion.Error)
    }
}
