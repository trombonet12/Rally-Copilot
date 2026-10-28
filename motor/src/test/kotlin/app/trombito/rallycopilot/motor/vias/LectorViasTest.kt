package app.trombito.rallycopilot.motor.vias

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.test.assertFailsWith

class LectorViasTest {
    private val cabecera = "#vias-mallorca v1"
    private val coords = "39600000,2900000;39601000,2901000"

    private fun linea(vararg campos: String) = campos.joinToString("\t")

    private val completa =
        linea("123", "primary", "80", "90", "70", "yes", "Ma-13", "Carretera de Sa Pobla", coords)

    @Test
    fun `lee una via completa con todos sus campos`() {
        val r = LectorVias.leer(sequenceOf(cabecera, completa))
        assertEquals(0, r.descartadas)
        val via = r.vias.single()
        assertEquals(123L, via.id)
        assertEquals("primary", via.clase)
        assertEquals("80", via.maxspeed)
        assertEquals("90", via.maxspeedAdelante)
        assertEquals("70", via.maxspeedAtras)
        assertEquals(SentidoUnico.ADELANTE, via.sentidoUnico)
        assertEquals("Ma-13", via.ref)
        assertEquals("Carretera de Sa Pobla", via.nombre)
        assertEquals(2, via.puntos.size)
        assertEquals(39.6, via.puntos[0].lat, 1e-9)
        assertEquals(2.9, via.puntos[0].lon, 1e-9)
        assertEquals(39.601, via.puntos[1].lat, 1e-9)
        assertEquals(2.901, via.puntos[1].lon, 1e-9)
    }

    @Test
    fun `los campos vacios son null y oneway se interpreta`() {
        val sinDatos = LectorVias.leer(sequenceOf(cabecera, linea("1", "residential", "", "", "", "", "", "", coords)))
        val via = sinDatos.vias.single()
        assertNull(via.maxspeed)
        assertNull(via.maxspeedAdelante)
        assertNull(via.maxspeedAtras)
        assertNull(via.ref)
        assertNull(via.nombre)
        assertEquals(SentidoUnico.NO, via.sentidoUnico)

        fun sentido(oneway: String) =
            LectorVias
                .leer(sequenceOf(cabecera, linea("1", "road", "", "", "", oneway, "", "", coords)))
                .vias
                .single()
                .sentidoUnico
        assertEquals(SentidoUnico.ADELANTE, sentido("yes"))
        assertEquals(SentidoUnico.ADELANTE, sentido("true"))
        assertEquals(SentidoUnico.ADELANTE, sentido("1"))
        assertEquals(SentidoUnico.ATRAS, sentido("-1"))
        assertEquals(SentidoUnico.NO, sentido("no"))
        assertEquals(SentidoUnico.NO, sentido("reversible"))
    }

    @Test
    fun `descarta y cuenta las lineas malformadas sin perder las buenas`() {
        val r =
            LectorVias.leer(
                sequenceOf(
                    cabecera,
                    completa,
                    linea("2", "primary", "", "", "", "", "", coords), // 8 campos
                    linea("3", "primary", "", "", "", "", "", "", "", coords), // 10 campos
                    linea("abc", "primary", "", "", "", "", "", "", coords), // id no numérico
                    linea("4", "primary", "", "", "", "", "", "", "39600000,2900000"), // 1 punto
                    linea("5", "primary", "", "", "", "", "", "", "39600000,2900000;x,y"), // coords no numéricas
                    linea("6", "primary", "", "", "", "", "", "", "39600000;39601000"), // par incompleto
                    linea("7", "primary", "", "", "", "", "", "", "99000000,2900000;39601000,2901000"), // lat fuera
                    "",
                    linea("8", "tertiary", "", "", "", "", "", "", coords),
                ),
            )
        assertEquals(listOf(123L, 8L), r.vias.map { it.id })
        assertEquals(7, r.descartadas)
    }

    @Test
    fun `tolera finales de linea CRLF`() {
        val r = LectorVias.leer(sequenceOf("$cabecera\r", completa + "\r"))
        assertEquals(1, r.vias.size)
        assertEquals(0, r.descartadas)
    }

    @Test
    fun `sin cabecera o con otra version falla`() {
        assertFailsWith<IllegalArgumentException> { LectorVias.leer(emptySequence()) }
        assertFailsWith<IllegalArgumentException> { LectorVias.leer(sequenceOf(completa)) }
        assertFailsWith<IllegalArgumentException> { LectorVias.leer(sequenceOf("#vias-mallorca v2", completa)) }
    }

    @Test
    fun `solo cabecera da una lista vacia`() {
        val r = LectorVias.leer(sequenceOf(cabecera))
        assertEquals(0, r.vias.size)
        assertEquals(0, r.descartadas)
    }
}
