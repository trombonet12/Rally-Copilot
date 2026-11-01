package app.trombito.rallycopilot.motor.alertas

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.time.Instant

class LectorDatexTest {
    private fun real(): InputStream = checkNotNull(javaClass.getResourceAsStream("/alertas/datex-mallorca.xml"))

    private fun leerStax(entrada: InputStream): ResultadoDatex = LectorDatex.leer(LectorXmlStax(entrada))

    private fun ok(resultado: ResultadoDatex) = resultado as ResultadoDatex.Ok

    private val delFixture by lazy { ok(leerStax(real())) }

    private fun incidencia(id: String) = delFixture.incidencias.single { it.id == id }

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
    fun `un corte lineal lleva desde y hasta en ese orden aunque el XML traiga primero el hasta`() {
        val corte = incidencia("24889776")
        assertEquals(TipoIncidencia.CORTE, corte.tipo)
        assertEquals(listOf(PuntoGeo(39.70993, 2.60631), PuntoGeo(39.71891, 2.597133)), corte.puntos)
        assertEquals("Ma-1131", corte.carretera)
        assertEquals("Carretera cortada en Ma-1131", corte.descripcion)
        assertEquals(GravedadIncidencia.ALTA, corte.gravedad)
        assertEquals(Instant.parse("2026-10-21T07:03:39Z"), corte.inicio)
        assertNull(corte.fin)
    }

    @Test
    fun `unas obras con fecha de fin y limitacion de carril`() {
        val obras = incidencia("23596720")
        assertEquals(TipoIncidencia.OBRAS, obras.tipo)
        assertEquals("Ma-5018", obras.carretera)
        assertEquals("Obras en Ma-5018, circulación alterna", obras.descripcion)
        assertEquals(Instant.parse("2026-11-06T22:59:00Z"), obras.fin)
        assertEquals(Instant.parse("2026-09-28T11:52:00Z"), obras.inicio)
        assertTrue(obras.puntos.size >= 2)
    }

    @Test
    fun `unas obras con dos registros no duplican puntos`() {
        val obras = incidencia("18801036")
        assertEquals(TipoIncidencia.OBRAS, obras.tipo)
        assertEquals(obras.puntos.distinct(), obras.puntos)
        assertEquals(
            listOf(PuntoGeo(39.609173, 3.2984116), PuntoGeo(39.604004, 3.2923753), PuntoGeo(39.6092, 3.2984407)),
            obras.puntos,
        )
    }

    @Test
    fun `un vehiculo detenido es de tipo OTRA con un solo punto`() {
        val coche = incidencia("25217956")
        assertEquals(TipoIncidencia.OTRA, coche.tipo)
        assertEquals(listOf(PuntoGeo(39.56347, 2.68912)), coche.puntos)
        assertEquals("Vehículo detenido en Ma-19", coche.descripcion)
        assertNull(coche.gravedad)
    }

    @Test
    fun `las situaciones de fuera de Mallorca no salen`() {
        assertTrue(delFixture.incidencias.all { i -> i.puntos.any { LectorDatex.MALLORCA.contiene(it) } })
    }

    // ---- Sintéticos: tipos que el feed de hoy no trae en la isla ----

    private val cabecera =
        """<?xml version="1.0" encoding="UTF-8"?>
        <d2:payload xmlns:com="http://levelC/schema/3/common" xmlns:loc="http://levelC/schema/3/locationReferencing"
          xmlns:d2="http://levelC/schema/3/d2Payload" xmlns:sit="http://levelC/schema/3/situation"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
        <com:publicationTime>2026-11-01T14:19:18.632+01:00</com:publicationTime>"""

    private fun documento(situaciones: String) = "$cabecera$situaciones</d2:payload>"

    private fun situacion(
        id: String,
        registros: String,
        estado: String = "real",
        severidad: String? = null,
    ) = """<sit:situation id="$id">${severidad?.let { "<sit:overallSeverity>$it</sit:overallSeverity>" } ?: ""}
        <sit:headerInformation><com:informationStatus>$estado</com:informationStatus></sit:headerInformation>
        $registros</sit:situation>"""

    private fun etiqueta(
        nombre: String,
        valor: String?,
    ) = valor?.let { "<$nombre>$it</$nombre>" } ?: ""

    @Suppress("LongParameterList")
    private fun registro(
        xsi: String = "sit:GenericSituationRecord",
        estado: String = "active",
        causa: String? = null,
        detalle: String? = null,
        gestion: String? = null,
        via: String? = "Ma-13",
        lat: Double = 39.70,
        lon: Double = 2.90,
        fin: String? = null,
        severidad: String? = null,
        extra: String = "",
    ) = """<sit:situationRecord xsi:type="$xsi" id="r" version="1">
          <sit:validity><com:validityStatus>$estado</com:validityStatus>
            <com:validityTimeSpecification><com:overallStartTime>2026-11-01T10:00:00.000+01:00</com:overallStartTime>
            ${etiqueta("com:overallEndTime", fin)}
            </com:validityTimeSpecification></sit:validity>
          ${etiqueta("sit:severity", severidad)}
          ${if (causa == null) {
        ""
    } else {
        "<sit:cause>${etiqueta(
            "sit:causeType",
            causa,
        )}${etiqueta("sit:detailedCauseType", detalle)}</sit:cause>"
    }}
          <sit:locationReference xsi:type="loc:PointLocation">
            <loc:supplementaryPositionalDescription><loc:roadInformation>
              ${etiqueta("loc:roadName", via)}
            </loc:roadInformation></loc:supplementaryPositionalDescription>
            <loc:tpegPointLocation><loc:point><loc:pointCoordinates>
              <loc:latitude>$lat</loc:latitude><loc:longitude>$lon</loc:longitude>
            </loc:pointCoordinates></loc:point></loc:tpegPointLocation>
          </sit:locationReference>
          ${etiqueta("sit:roadOrCarriagewayOrLaneManagementType", gestion)}
          $extra
        </sit:situationRecord>"""

    private fun leer(texto: String): ResultadoDatex = leerStax(ByteArrayInputStream(texto.toByteArray()))

    private fun unica(texto: String): Incidencia = ok(leer(texto)).incidencias.single()

    @Test
    fun `un accidente`() {
        val i =
            unica(
                documento(
                    situacion(
                        "1",
                        registro(causa = "accident", detalle = "<sit:accidentType>accident</sit:accidentType>"),
                    ),
                ),
            )
        assertEquals(TipoIncidencia.ACCIDENTE, i.tipo)
        assertEquals("Accidente en Ma-13", i.descripcion)
    }

    @Test
    fun `trafico lento es una retencion aunque la causa sea un accidente`() {
        val i =
            unica(
                documento(
                    situacion(
                        "1",
                        registro(
                            xsi = "sit:AbnormalTraffic",
                            causa = "accident",
                            detalle = "<sit:accidentType>accident</sit:accidentType>",
                            extra = "<sit:abnormalTrafficType>slowTraffic</sit:abnormalTrafficType>",
                        ),
                    ),
                ),
            )
        assertEquals(TipoIncidencia.RETENCION, i.tipo)
        assertEquals("Tráfico lento en Ma-13", i.descripcion)
    }

    @Test
    fun `desprendimientos son meteo y con carretera cortada gana el corte`() {
        val roca = "<sit:environmentalObstructionType>rockfalls</sit:environmentalObstructionType>"
        val meteo = unica(documento(situacion("1", registro(causa = "environmentalObstruction", detalle = roca))))
        assertEquals(TipoIncidencia.METEO, meteo.tipo)
        assertEquals("Desprendimientos en Ma-13", meteo.descripcion)
        val corte =
            unica(
                documento(
                    situacion(
                        "2",
                        registro(causa = "environmentalObstruction", detalle = roca, gestion = "roadClosed"),
                    ),
                ),
            )
        assertEquals(TipoIncidencia.CORTE, corte.tipo)
    }

    @Test
    fun `el tipo de la situacion es el mas grave de sus registros y la gravedad la mayor`() {
        val i =
            unica(
                documento(
                    situacion(
                        "1",
                        registro(causa = "roadMaintenance", gestion = "laneClosures", severidad = "medium") +
                            registro(causa = "accident", severidad = "high", lat = 39.71),
                    ),
                ),
            )
        assertEquals(TipoIncidencia.ACCIDENTE, i.tipo)
        assertEquals(GravedadIncidencia.ALTA, i.gravedad)
        assertEquals(2, i.puntos.size)
    }

    @Test
    fun `la gravedad baja y media se reconocen y la desconocida es null`() {
        fun gravedad(s: String) = unica(documento(situacion("1", registro(severidad = s)))).gravedad
        assertEquals(GravedadIncidencia.BAJA, gravedad("low"))
        assertEquals(GravedadIncidencia.BAJA, gravedad("lowest"))
        assertEquals(GravedadIncidencia.MEDIA, gravedad("medium"))
        assertEquals(GravedadIncidencia.ALTA, gravedad("highest"))
        assertNull(gravedad("unknown"))
    }

    @Test
    fun `la gravedad de la situacion tambien vale`() {
        val i = unica(documento(situacion("1", registro(), severidad = "highest")))
        assertEquals(GravedadIncidencia.ALTA, i.gravedad)
    }

    @Test
    fun `sin carretera la descripcion no la nombra`() {
        val i = unica(documento(situacion("1", registro(via = null, causa = "accident"))))
        assertNull(i.carretera)
        assertEquals("Accidente", i.descripcion)
    }

    @Test
    fun `fin solo si todos los registros lo traen`() {
        val con = registro(fin = "2026-12-01T10:00:00.000+01:00")
        val sin = registro(lat = 39.71)
        assertNull(unica(documento(situacion("1", con + sin))).fin)
        assertEquals(
            Instant.parse("2026-12-01T09:00:00Z"),
            unica(documento(situacion("2", con + registro(lat = 39.71, fin = "2026-11-20T10:00:00.000+01:00")))).fin,
        )
    }

    @Test
    fun `una fecha ilegible no tira la incidencia`() {
        val i = unica(documento(situacion("1", registro(fin = "ayer por la tarde"))))
        assertNull(i.fin)
    }

    @Test
    fun `se descartan las pruebas, las suspendidas y las de Menorca`() {
        val texto =
            documento(
                situacion("prueba", registro(), estado = "test") +
                    situacion("suspendida", registro(estado = "suspended")) +
                    situacion("menorca", registro(lat = 39.95, lon = 4.10)) +
                    situacion("ibiza", registro(lat = 38.98, lon = 1.43)) +
                    situacion("buena", registro()),
            )
        val r = ok(leer(texto))
        assertEquals(listOf("buena"), r.incidencias.map { it.id })
        assertEquals(5, r.situacionesLeidas)
    }

    @Test
    fun `los bordes del recuadro de Mallorca cuentan como dentro`() {
        assertTrue(LectorDatex.MALLORCA.contiene(PuntoGeo(39.26, 2.30)))
        assertTrue(LectorDatex.MALLORCA.contiene(PuntoGeo(39.97, 3.50)))
        assertFalse(LectorDatex.MALLORCA.contiene(PuntoGeo(39.97, 3.51)))
        assertFalse(LectorDatex.MALLORCA.contiene(PuntoGeo(39.25, 2.90)))
    }

    @Test
    fun `una situacion sin coordenadas utilizables se ignora sin romper el resto`() {
        val malas =
            registro(lat = 39.7, lon = 2.9)
                .replace("<loc:latitude>39.7</loc:latitude>", "<loc:latitude>x</loc:latitude>")
        val r = ok(leer(documento(situacion("mala", malas) + situacion("buena", registro()))))
        assertEquals(listOf("buena"), r.incidencias.map { it.id })
    }

    @Test
    fun `los elementos desconocidos y los atributos extra no molestan`() {
        val extra =
            "<sit:inventado><sit:profundo>1</sit:profundo></sit:inventado>" +
                "<loc:from><x:y xmlns:x=\"urn:x\"/></loc:from>"
        val i = unica(documento(situacion("1", registro(causa = "roadMaintenance", extra = extra))))
        assertEquals(TipoIncidencia.OBRAS, i.tipo)
        assertEquals(1, i.puntos.size)
    }

    @Test
    fun `una publicacion valida sin situaciones da lista vacia`() {
        val r = ok(leer(documento("")))
        assertEquals(emptyList<Incidencia>(), r.incidencias)
        assertEquals(0, r.situacionesLeidas)
    }

    // ---- Errores tipados ----

    @Test
    fun `un XML truncado da error de XML mal formado y no una excepcion`() {
        val bytes = real().readBytes()
        val r = leerStax(ByteArrayInputStream(bytes.copyOf(bytes.size / 2)))
        assertEquals(MotivoErrorDatex.XML_MAL_FORMADO, (r as ResultadoDatex.Error).motivo)
        assertNotNull(r.detalle)
    }

    @Test
    fun `texto que no es XML, vacio o con etiquetas sin cerrar dan error de XML mal formado`() {
        for (basura in listOf("", "no soy xml", "<html><body>503 Service Unavailable", "<a><b></a>")) {
            val r = leer(basura)
            assertEquals(basura, MotivoErrorDatex.XML_MAL_FORMADO, (r as ResultadoDatex.Error).motivo)
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
        val r = leerStax(roto)
        assertEquals(MotivoErrorDatex.ENTRADA_ILEGIBLE, (r as ResultadoDatex.Error).motivo)
    }

    @Test
    fun `no resuelve entidades externas`() {
        val xxe =
            """<?xml version="1.0"?><!DOCTYPE d [<!ENTITY e SYSTEM "file:///etc/passwd">]>
            <d2:payload xmlns:d2="http://levelC/schema/3/d2Payload" xmlns:com="http://levelC/schema/3/common">
            <com:publicationTime>&e;</com:publicationTime></d2:payload>"""
        val r = leer(xxe)
        // Sea ok vacío o error, nunca lanza ni lee el archivo.
        assertTrue(r is ResultadoDatex.Error || (r as ResultadoDatex.Ok).incidencias.isEmpty())
    }

    @Test
    fun `el texto de despues de un hijo no se pega al valor del padre`() {
        val estado =
            situacion("1", registro()).replace(
                "<com:informationStatus>real</com:informationStatus>",
                "<com:informationStatus><com:x>test</com:x></com:informationStatus>",
            )
        // El padre no tiene texto propio: no es una situación de prueba.
        assertEquals(listOf("1"), ok(leer(documento(estado))).incidencias.map { it.id })
    }

    @Test
    fun `un texto de mas de 64 KB da error de XML mal formado`() {
        val enorme = "x".repeat(LectorDatex.TOPE_TEXTO + 1)
        val r = leer(documento("").replace("</d2:payload>", "<com:basura>$enorme</com:basura></d2:payload>"))
        assertEquals(MotivoErrorDatex.XML_MAL_FORMADO, (r as ResultadoDatex.Error).motivo)
    }

    @Test
    fun `un texto justo en el tope se admite`() {
        val justo = "x".repeat(LectorDatex.TOPE_TEXTO)
        val r = leer(documento("").replace("</d2:payload>", "<com:basura>$justo</com:basura></d2:payload>"))
        assertTrue(r is ResultadoDatex.Ok)
    }

    @Test
    fun `mas de 64 niveles de anidamiento da error de XML mal formado`() {
        val n = LectorDatex.TOPE_PROFUNDIDAD + 1
        val hondo = "<a>".repeat(n) + "</a>".repeat(n)
        val r = leer(documento(hondo))
        assertEquals(MotivoErrorDatex.XML_MAL_FORMADO, (r as ResultadoDatex.Error).motivo)
    }

    @Test
    fun `una profundidad razonable se admite`() {
        val n = 20
        val r = leer(documento("<a>".repeat(n) + "</a>".repeat(n)))
        assertTrue(r is ResultadoDatex.Ok)
    }

    @Test
    fun `una incidencia caduca cuando llega su fin`() {
        val obras = incidencia("23596720")
        assertFalse(obras.caducadaEn(Instant.parse("2026-11-06T22:58:59Z")))
        assertTrue(obras.caducadaEn(Instant.parse("2026-11-06T22:59:00Z")))
        assertFalse(incidencia("25217956").caducadaEn(Instant.parse("2099-01-01T00:00:00Z")))
    }
}
