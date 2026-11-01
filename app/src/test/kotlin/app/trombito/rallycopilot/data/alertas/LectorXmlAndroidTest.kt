package app.trombito.rallycopilot.data.alertas

import app.trombito.rallycopilot.motor.alertas.EventoXml
import app.trombito.rallycopilot.motor.alertas.GravedadIncidencia
import app.trombito.rallycopilot.motor.alertas.LectorDatex
import app.trombito.rallycopilot.motor.alertas.MotivoErrorDatex
import app.trombito.rallycopilot.motor.alertas.ResultadoDatex
import app.trombito.rallycopilot.motor.alertas.TipoIncidencia
import app.trombito.rallycopilot.motor.alertas.XmlMalFormadoException
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.time.Instant

/**
 * El DATEX II real leído con el adaptador de Android (`XmlPullParser`), que debe dar lo mismo que el test del motor con
 * StAX (`LectorDatexTest`): el fixture es una copia de `motor/src/test/resources/alertas/datex-mallorca.xml`.
 */
@RunWith(RobolectricTestRunner::class)
class LectorXmlAndroidTest {
    private fun real(): InputStream = checkNotNull(javaClass.getResourceAsStream("/alertas/datex-mallorca.xml"))

    private fun leer(entrada: InputStream) = LectorDatex.leer(LectorXmlAndroid(entrada))

    private fun leer(texto: String) = leer(ByteArrayInputStream(texto.toByteArray()))

    private val delFixture by lazy { leer(real()) as ResultadoDatex.Ok }

    @Test
    fun `el fixture real trae 12 situaciones y solo 9 son de Mallorca`() {
        assertEquals(12, delFixture.situacionesLeidas)
        assertEquals(
            listOf(
                "18801036",
                "19195859",
                "22943388",
                "23596720",
                "24889776",
                "25217956",
                "25218001",
                "25218728",
                "25219136",
            ),
            delFixture.incidencias.map { it.id }.sorted(),
        )
    }

    @Test
    fun `un corte lineal lleva desde y hasta en ese orden y los atributos con espacio de nombres se leen`() {
        val corte = delFixture.incidencias.single { it.id == "24889776" }
        assertEquals(TipoIncidencia.CORTE, corte.tipo)
        assertEquals(listOf(PuntoGeo(39.70993, 2.60631), PuntoGeo(39.71891, 2.597133)), corte.puntos)
        assertEquals("Ma-1131", corte.carretera)
        assertEquals("Carretera cortada en Ma-1131", corte.descripcion)
        assertEquals(GravedadIncidencia.ALTA, corte.gravedad)
        assertEquals(Instant.parse("2026-10-21T07:03:39Z"), corte.inicio)
        assertNull(corte.fin)
    }

    @Test
    fun `unas obras con fecha de fin y unas con dos registros sin puntos duplicados`() {
        val obras = delFixture.incidencias.single { it.id == "23596720" }
        assertEquals(TipoIncidencia.OBRAS, obras.tipo)
        assertEquals("Obras en Ma-5018, circulación alterna", obras.descripcion)
        assertEquals(Instant.parse("2026-11-06T22:59:00Z"), obras.fin)
        val dobles = delFixture.incidencias.single { it.id == "18801036" }
        assertEquals(
            listOf(PuntoGeo(39.609173, 3.2984116), PuntoGeo(39.604004, 3.2923753), PuntoGeo(39.6092, 3.2984407)),
            dobles.puntos,
        )
        val coche = delFixture.incidencias.single { it.id == "25217956" }
        assertEquals(TipoIncidencia.OTRA, coche.tipo)
        assertEquals("Vehículo detenido en Ma-19", coche.descripcion)
    }

    @Test
    fun `un XML truncado, texto basura o etiquetas sin cerrar dan XML mal formado y no una excepcion`() {
        val bytes = real().readBytes()
        val truncado = leer(ByteArrayInputStream(bytes.copyOf(bytes.size / 2)))
        assertEquals(MotivoErrorDatex.XML_MAL_FORMADO, (truncado as ResultadoDatex.Error).motivo)
        assertNotNull(truncado.detalle)
        for (basura in listOf("", "no soy xml", "<html><body>503 Service Unavailable", "<a><b></a>")) {
            assertEquals(basura, MotivoErrorDatex.XML_MAL_FORMADO, (leer(basura) as ResultadoDatex.Error).motivo)
        }
    }

    @Test
    fun `un XML correcto que no es DATEX da otro error`() {
        val r = leer("<html><body>hola</body></html>")
        assertEquals(MotivoErrorDatex.NO_ES_DATEX, (r as ResultadoDatex.Error).motivo)
    }

    @Test
    fun `un fallo de lectura se informa como entrada ilegible`() {
        val roto =
            object : InputStream() {
                override fun read(): Int = throw IOException("sin red")
            }
        assertEquals(MotivoErrorDatex.ENTRADA_ILEGIBLE, (leer(roto) as ResultadoDatex.Error).motivo)
    }

    @Test
    fun `no resuelve entidades externas`() {
        val xxe =
            """<?xml version="1.0"?><!DOCTYPE d [<!ENTITY e SYSTEM "file:///etc/passwd">]>
            <d2:payload xmlns:d2="http://levelC/schema/3/d2Payload" xmlns:com="http://levelC/schema/3/common">
            <com:publicationTime>&e;</com:publicationTime></d2:payload>"""
        val r = leer(xxe)
        assertTrue(r is ResultadoDatex.Error || (r as ResultadoDatex.Ok).incidencias.isEmpty())
    }

    /** Un «billion laughs» pequeño (10^4 caracteres): en grande agota la memoria del móvil. */
    private val billionLaughs =
        """<?xml version="1.0"?><!DOCTYPE d [
        <!ENTITY a "0123456789">
        <!ENTITY b "&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;">
        <!ENTITY c "&b;&b;&b;&b;&b;&b;&b;&b;&b;&b;">
        <!ENTITY d "&c;&c;&c;&c;&c;&c;&c;&c;&c;&c;">]>
        <d2:payload xmlns:d2="http://levelC/schema/3/d2Payload" xmlns:com="http://levelC/schema/3/common">
        <com:publicationTime>&d;</com:publicationTime></d2:payload>"""

    @Test
    fun `un DOCTYPE con entidades internas se rechaza sin expandirlas`() {
        val r = leer(billionLaughs)
        assertEquals(MotivoErrorDatex.XML_MAL_FORMADO, (r as ResultadoDatex.Error).motivo)
    }

    @Test
    fun `el lector no entrega texto expandido de una entidad definida en el DOCTYPE`() {
        val lector = LectorXmlAndroid(ByteArrayInputStream(billionLaughs.toByteArray()))
        var texto = 0
        val fallo =
            runCatching {
                while (true) {
                    when (lector.siguiente()) {
                        EventoXml.TEXTO -> texto += lector.texto.length
                        EventoXml.FIN_DOCUMENTO -> break
                        else -> Unit
                    }
                }
            }.exceptionOrNull()
        assertTrue(fallo is XmlMalFormadoException)
        assertEquals(0, texto)
    }

    @Test
    fun `las entidades predefinidas y las referencias numericas siguen funcionando`() {
        val lector = LectorXmlAndroid(ByteArrayInputStream("<d>a&amp;b&#65;&lt;c&quot;</d>".toByteArray()))
        val texto = StringBuilder()
        while (true) {
            when (lector.siguiente()) {
                EventoXml.TEXTO -> texto.append(lector.texto)
                EventoXml.FIN_DOCUMENTO -> break
                else -> Unit
            }
        }
        assertEquals("a&bA<c\"", texto.toString())
    }
}
