package app.trombito.rallycopilot.motor.alertas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream

/** Datos extra de cada incidencia (pk, sentido, municipio, carriles, límite temporal, comentario). */
class DatosExtraDatexTest {
    private val delFixture by lazy {
        val xml = checkNotNull(javaClass.getResourceAsStream("/alertas/datex-mallorca.xml"))
        (LectorDatex.leer(LectorXmlStax(xml)) as ResultadoDatex.Ok).incidencias
    }

    private fun incidencia(id: String) = delFixture.single { it.id == id }

    @Test
    fun `unas obras lineales de dos registros dan pk menor y mayor, sentidos sin repetir y municipio unico`() {
        val i = incidencia("18801036")
        assertEquals(56.5, i.pkDesde!!, 1e-9)
        assertEquals(57.1, i.pkHasta!!, 1e-9)
        assertEquals("suroeste y noreste", i.sentido)
        assertEquals("Sant Llorenç des Cardassar", i.municipio)
        assertEquals("todos los carriles", i.carrilesAfectados)
        assertNull(i.limiteTemporalKmh)
        assertNull(i.comentario)
    }

    @Test
    fun `un corte lineal con el to antes del from ordena los pk de menor a mayor y el sentido unknown es null`() {
        val i = incidencia("24889776")
        assertEquals(0.0, i.pkDesde!!, 1e-9)
        assertEquals(2.2, i.pkHasta!!, 1e-9)
        assertNull(i.sentido)
        assertEquals("Valldemossa", i.municipio)
    }

    @Test
    fun `una incidencia puntual tiene un solo pk y pkHasta es null`() {
        val i = incidencia("19195859")
        assertEquals(7.5, i.pkDesde!!, 1e-9)
        assertNull(i.pkHasta)
        assertEquals("suroeste", i.sentido)
        assertEquals("Puigpunyent", i.municipio)
        assertEquals("carril derecho", i.carrilesAfectados)
    }

    @Test
    fun `dos registros con el mismo carril no lo repiten y los sentidos opuestos se combinan`() {
        val i = incidencia("22943388")
        assertEquals("carril derecho", i.carrilesAfectados)
        assertEquals("suroeste y norte", i.sentido)
        assertEquals("Palma", i.municipio)
    }

    @Test
    fun `un vehiculo detenido sin carriles trae sentido y pk pero no carriles`() {
        val i = incidencia("25217956")
        assertEquals("oeste", i.sentido)
        assertEquals(4.046, i.pkDesde!!, 1e-9)
        assertNull(i.carrilesAfectados)
        assertNull(i.limiteTemporalKmh)
    }

    @Test
    fun `cada direccion cardinal del fixture sale en español`() {
        assertEquals("noreste", incidencia("25218001").sentido)
        assertEquals("noroeste", incidencia("25218728").sentido)
        assertEquals("sureste", incidencia("25219136").sentido)
        assertEquals("oeste y este", incidencia("23596720").sentido)
    }

    // ---- Sintéticos: lo que el feed de hoy no trae en la isla ----

    private val cabecera =
        """<?xml version="1.0" encoding="UTF-8"?>
        <d2:payload xmlns:com="http://levelC/schema/3/common" xmlns:loc="http://levelC/schema/3/locationReferencing"
          xmlns:d2="http://levelC/schema/3/d2Payload" xmlns:sit="http://levelC/schema/3/situation"
          xmlns:lse="urn:lse" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
        <com:publicationTime>2026-11-01T14:19:18.632+01:00</com:publicationTime>"""

    private fun unica(
        ubicacion: String = "",
        registro: String = "",
    ): Incidencia {
        val xml =
            """$cabecera<sit:situation id="1"><sit:headerInformation><com:informationStatus>real
            </com:informationStatus></sit:headerInformation>
            <sit:situationRecord xsi:type="sit:GenericSituationRecord" id="r" version="1">
              <sit:locationReference>
                <loc:supplementaryPositionalDescription>$ubicacion</loc:supplementaryPositionalDescription>
                <loc:tpegPointLocation><loc:point><loc:pointCoordinates>
                  <loc:latitude>39.7</loc:latitude><loc:longitude>2.9</loc:longitude>
                </loc:pointCoordinates></loc:point></loc:tpegPointLocation>
              </sit:locationReference>
              $registro
            </sit:situationRecord></sit:situation></d2:payload>"""
        val r = LectorDatex.leer(LectorXmlStax(ByteArrayInputStream(xml.toByteArray()))) as ResultadoDatex.Ok
        return r.incidencias.single()
    }

    @Test
    fun `una incidencia sin ninguno de los datos extra los deja todos en null`() {
        val i = unica()
        assertNull(i.pkDesde)
        assertNull(i.pkHasta)
        assertNull(i.sentido)
        assertNull(i.municipio)
        assertNull(i.carrilesAfectados)
        assertNull(i.limiteTemporalKmh)
        assertNull(i.comentario)
    }

    @Test
    fun `el limite temporal de velocidad se redondea a km por hora y el cero se descarta`() {
        assertEquals(40, unica(registro = "<sit:temporarySpeedLimit>40.0</sit:temporarySpeedLimit>").limiteTemporalKmh)
        assertNull(unica(registro = "<sit:temporarySpeedLimit>0.0</sit:temporarySpeedLimit>").limiteTemporalKmh)
        assertNull(unica(registro = "<sit:temporarySpeedLimit>rápido</sit:temporarySpeedLimit>").limiteTemporalKmh)
    }

    @Test
    fun `el destino de la via tiene prioridad sobre la direccion`() {
        val i =
            unica(
                ubicacion = "<loc:roadInformation><loc:roadDestination>PALMA</loc:roadDestination></loc:roadInformation>",
                registro = "<loc:tpegDirection>eastBound</loc:tpegDirection>",
            )
        assertEquals("PALMA", i.sentido)
    }

    @Test
    fun `ambos sentidos y arcen con ramal de salida`() {
        val i =
            unica(
                ubicacion =
                    "<loc:carriageway><loc:carriageway>exitSlipRoad</loc:carriageway>" +
                        "<loc:lane><loc:laneUsage>hardShoulder</loc:laneUsage></loc:lane></loc:carriageway>",
                registro = "<loc:tpegDirection>bothWays</loc:tpegDirection>",
            )
        assertEquals("ambos sentidos", i.sentido)
        assertEquals("arcén y ramal de salida", i.carrilesAfectados)
    }

    @Test
    fun `varios carriles se listan y el valor desconocido se ignora`() {
        val i =
            unica(
                ubicacion =
                    "<loc:carriageway><loc:lane><loc:laneUsage>lane1</loc:laneUsage></loc:lane>" +
                        "<loc:lane><loc:laneUsage>leftLane</loc:laneUsage></loc:lane>" +
                        "<loc:lane><loc:laneUsage>inventado</loc:laneUsage></loc:lane></loc:carriageway>",
            )
        assertEquals("carril 1 y carril izquierdo", i.carrilesAfectados)
    }

    @Test
    fun `el comentario publico prefiere el español entre varios idiomas`() {
        val i =
            unica(
                registro =
                    """<sit:generalPublicComment><sit:comment><com:values>
                    <com:value lang="en">Roadworks ahead</com:value>
                    <com:value lang="es">Obras más adelante</com:value>
                    </com:values></sit:comment></sit:generalPublicComment>""",
            )
        assertEquals("Obras más adelante", i.comentario)
    }

    @Test
    fun `el comentario sin idioma se conserva y un value fuera del comentario no cuenta`() {
        val i =
            unica(
                registro =
                    """<sit:generalPublicComment><sit:comment><com:values>
                    <com:value>Precaución</com:value></com:values></sit:comment></sit:generalPublicComment>
                    <sit:otraCosa><com:values><com:value lang="es">no es comentario</com:value></com:values>
                    </sit:otraCosa>""",
            )
        assertEquals("Precaución", i.comentario)
    }
}
