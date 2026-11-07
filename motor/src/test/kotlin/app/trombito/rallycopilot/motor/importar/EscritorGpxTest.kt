package app.trombito.rallycopilot.motor.importar

import app.trombito.rallycopilot.motor.alertas.LectorXmlStax
import app.trombito.rallycopilot.motor.edicion.AccionEdicion
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.longitudMetros
import app.trombito.rallycopilot.motor.notas.Direccion
import app.trombito.rallycopilot.motor.notas.Modificador
import app.trombito.rallycopilot.motor.notas.Nota
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class EscritorGpxTest {
    /** Unos 3 km hacia el norte con elevación, con decimales «feos» para comprobar que no se pierde precisión. */
    private val traza = List(31) { PuntoGeo(39.6123456789 + it * 0.001, 2.9 + it * 0.00012345, 100.0 + it * 1.5) }

    private fun nota(
        direccion: Direccion = Direccion.DERECHA,
        grado: Int? = 3,
        modificadores: Set<Modificador> = emptySet(),
    ) = Nota(0.0, 40.0, direccion, grado, modificadores, distanciaSiguienteM = 100, cuidado = true)

    private val ediciones =
        listOf(
            EdicionArchivo(
                120.5,
                AccionEdicion.Sustituir(nota(Direccion.IZQUIERDA, 2, setOf(Modificador.LARGA))),
                1_000L,
            ),
            EdicionArchivo(800.0, AccionEdicion.Quitar, 2_000L),
            EdicionArchivo(1_500.25, AccionEdicion.Anadir(nota(grado = null)), 3_000L),
            EdicionArchivo(2_000.0, AccionEdicion.Cuidado, 4_000L),
        )

    private val tramo =
        TramoArchivo(
            nombre = "Palma → Valldemossa",
            traza = traza,
            sectores = listOf(700.0, 1_500.0, 2_200.0),
            microsectores = listOf(1_000.0, 2_000.0),
            notas =
                listOf(
                    NotaArchivo(traza[3], 120.5, "izquierda dos larga", esAviso = false),
                    NotaArchivo(traza[10], 800.0, "puente", esAviso = true),
                ),
            ediciones = ediciones,
        )

    private fun leer(xml: String) = LectorGpx.leer(LectorXmlStax(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8))))

    @Test
    fun `exportar y volver a leer devuelve la traza exacta con su elevacion y el nombre`() {
        val r = leer(EscritorGpx.escribir(tramo)) as ResultadoImportacion.Ok

        assertEquals("Palma → Valldemossa", r.nombre)
        assertEquals(traza, r.puntos)
    }

    @Test
    fun `exportar y volver a leer recupera sectores, microsectores y las cuatro clases de edicion`() {
        val r = leer(EscritorGpx.escribir(tramo)) as ResultadoImportacion.Ok

        val extension = checkNotNull(r.extension)
        assertEquals(tramo.sectores, extension.sectores)
        assertEquals(tramo.microsectores, extension.microsectores)
        assertEquals(ediciones, extension.ediciones)
    }

    @Test
    fun `un tramo sin cortes ni ediciones se lee con la extension vacia, no sin ella`() {
        val vacio = tramo.copy(sectores = emptyList(), microsectores = emptyList(), ediciones = emptyList())
        val r = leer(EscritorGpx.escribir(vacio)) as ResultadoImportacion.Ok

        assertEquals(ExtensionRally(emptyList(), emptyList(), emptyList()), r.extension)
    }

    @Test
    fun `las notas salen como waypoints con su frase y el archivo sigue siendo un GPX 1_1 valido para otras apps`() {
        val xml = EscritorGpx.escribir(tramo)

        assertTrue(xml.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"))
        assertTrue("version=\"1.1\"" in xml)
        assertTrue("<name>izquierda dos larga</name>" in xml)
        assertTrue("<type>nota</type>" in xml)
        assertTrue("<type>aviso</type>" in xml)
        // Otras apps usan los trk y no deben ver los waypoints como traza.
        val r = leer(xml) as ResultadoImportacion.Ok
        assertEquals(31, r.puntos.size)
        // Los waypoints van antes que el trk y la extensión propia al final, como pide el esquema GPX.
        assertTrue(xml.indexOf("<wpt") < xml.indexOf("<trk>"))
        assertTrue(xml.indexOf("</trk>") < xml.indexOf("<extensions>"))
    }

    @Test
    fun `los caracteres especiales y de control de los textos no rompen el XML`() {
        val raro =
            tramo.copy(
                nombre = "Ma-10 <&> \"Lluc\" 'x'\u0001\u000B fin",
                notas = listOf(tramo.notas.first().copy(frase = "a & b < c")),
            )
        val r = leer(EscritorGpx.escribir(raro)) as ResultadoImportacion.Ok

        assertEquals("Ma-10 <&> \"Lluc\" 'x' fin", r.nombre)
        assertEquals(31, r.puntos.size)
    }

    @Test
    fun `una edicion con posicion no finita o de clase desconocida se salta sin perder las demas`() {
        val xml =
            EscritorGpx.escribir(tramo).replace(
                "</rc:tramoRally>",
                """<rc:edicionRally m="NaN" t="1" tipo="QUITAR"/>""" +
                    """<rc:edicionRally m="5" t="1" tipo="INVENTADA"/>""" +
                    """<rc:edicionRally m="5" t="1" tipo="SUSTITUIR" nota="basura"/>""" +
                    "</rc:tramoRally>",
            )
        val r = leer(xml) as ResultadoImportacion.Ok

        assertEquals(ediciones, r.extension?.ediciones)
    }

    @Test
    fun `un GPX de otra app no trae extension`() {
        val strava =
            checkNotNull(
                javaClass.getResourceAsStream("/importar/strava.gpx"),
            ).use { LectorGpx.leer(LectorXmlStax(it)) }
        assertNull((strava as ResultadoImportacion.Ok).extension)
    }

    @Test
    fun `una extension de rally fuera de su sitio no cuenta`() {
        val xml =
            """<gpx><trk><trkseg><trkpt lat="39.6" lon="2.9"/><trkpt lat="39.7" lon="2.9"/></trkseg>""" +
                """<extensions><otra><tramoRally><sectorRally m="5"/></tramoRally></otra></extensions></trk></gpx>"""
        assertNull((leer(xml) as ResultadoImportacion.Ok).extension)
    }

    @Test
    fun `las coordenadas se escriben en decimal plano, nunca con exponente`() {
        val cerca = tramo.copy(traza = listOf(PuntoGeo(0.00005, 0.0001), PuntoGeo(0.001, 0.0002)))
        val xml = EscritorGpx.escribir(cerca)
        assertFalse("E-" in xml)
        val r = leer(xml) as ResultadoImportacion.Ok
        assertEquals(cerca.traza, r.puntos)
        assertTrue(cerca.traza.longitudMetros() > 0.0)
    }

    @Test
    fun `el nombre de archivo es corto, sin acentos ni simbolos y acaba en gpx`() {
        assertEquals("palma-valldemossa.gpx", EscritorGpx.nombreArchivo("Palma → Valldemossa"))
        assertEquals("ma-2141-escorca.gpx", EscritorGpx.nombreArchivo("Ma-2141 · Escorca"))
        assertEquals("tramo.gpx", EscritorGpx.nombreArchivo("   →→ "))
        assertEquals("montuiri.gpx", EscritorGpx.nombreArchivo("Montuïri"))
        assertTrue(EscritorGpx.nombreArchivo("x".repeat(300)).length <= 64)
    }
}
