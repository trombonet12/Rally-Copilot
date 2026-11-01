package app.trombito.rallycopilot.motor.alertas

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.test.assertFailsWith

class LectorRadaresTest {
    private val cabecera = "#radares v1"

    private fun linea(vararg campos: String) = campos.joinToString("\t")

    private fun leer(vararg lineas: String) = LectorRadares.leer(sequenceOf(cabecera) + lineas.asSequence())

    @Test
    fun `lee un radar fijo con todos sus campos`() {
        val r = leer(linea("FIJO", "dgt-1", "39.6012", "2.9034", "270", "90", "", "dgt"))
        assertEquals(0, r.descartadas)
        val radar = r.radares.single()
        assertEquals("dgt-1", radar.id)
        assertEquals(TipoRadar.FIJO, radar.tipo)
        assertEquals(PuntoGeo(39.6012, 2.9034), radar.punto)
        assertEquals(270.0, radar.rumboGrados!!, 1e-9)
        assertEquals(90, radar.limiteKmh)
        assertNull(radar.tramoId)
        assertEquals("dgt", radar.fuente)
    }

    @Test
    fun `rumbo y limite vacios son null (ambos sentidos, limite desconocido)`() {
        val radar = leer(linea("FIJO", "osm-9", "39.5", "2.8", "", "", "", "osm")).radares.single()
        assertNull(radar.rumboGrados)
        assertNull(radar.limiteKmh)
    }

    @Test
    fun `lee los cuatro tipos y el tramo_id de los tramos`() {
        val r =
            leer(
                linea("TRAMO_INICIO", "t1-ini", "39.60", "2.90", "10", "80", "t1", "dgt"),
                linea("TRAMO_FIN", "t1-fin", "39.62", "2.92", "10", "80", "t1", "dgt"),
                linea("ZONA_MOVIL", "z1", "39.70", "2.99", "", "", "", "dgt-pdf"),
                linea("FIJO", "f1", "39.71", "2.98", "", "", "", "osm"),
            )
        assertEquals(
            listOf(TipoRadar.TRAMO_INICIO, TipoRadar.TRAMO_FIN, TipoRadar.ZONA_MOVIL, TipoRadar.FIJO),
            r.radares.map { it.tipo },
        )
        assertEquals(listOf("t1", "t1", null, null), r.radares.map { it.tramoId })
    }

    @Test
    fun `el rumbo 360 se normaliza a 0`() {
        val radar = leer(linea("FIJO", "a", "39.5", "2.8", "360", "", "", "x")).radares.single()
        assertEquals(0.0, radar.rumboGrados!!, 1e-9)
    }

    @Test
    fun `acepta BOM, fin de linea de Windows, comentarios y lineas en blanco`() {
        val r =
            LectorRadares.leer(
                sequenceOf(
                    "﻿$cabecera\r",
                    "# generado el 2026-11-01",
                    "",
                    linea("FIJO", "a", "39.5", "2.8", "", "80", "", "dgt") + "\r",
                ),
            )
        assertEquals(1, r.radares.size)
        assertEquals(0, r.descartadas)
        assertEquals("dgt", r.radares.single().fuente)
    }

    @Test
    fun `descarta y cuenta las lineas malformadas sin tumbar el resto`() {
        val r =
            leer(
                linea("FIJO", "ok", "39.5", "2.8", "", "80", "", "dgt"),
                linea("FIJO", "pocos", "39.5", "2.8"),
                linea("RADAR", "tipo-raro", "39.5", "2.8", "", "", "", "x"),
                linea("FIJO", "", "39.5", "2.8", "", "", "", "x"),
                linea("FIJO", "lat-mala", "abc", "2.8", "", "", "", "x"),
                linea("FIJO", "lat-fuera", "95.0", "2.8", "", "", "", "x"),
                linea("FIJO", "rumbo-malo", "39.5", "2.8", "norte", "", "", "x"),
                linea("FIJO", "rumbo-fuera", "39.5", "2.8", "400", "", "", "x"),
                linea("FIJO", "limite-malo", "39.5", "2.8", "", "rapido", "", "x"),
                linea("FIJO", "limite-cero", "39.5", "2.8", "", "0", "", "x"),
                linea("TRAMO_INICIO", "sin-tramo", "39.5", "2.8", "", "80", "", "x"),
                linea("FIJO", "ok", "39.6", "2.9", "", "", "", "dgt"),
            )
        assertEquals(listOf("ok"), r.radares.map { it.id })
        assertEquals(11, r.descartadas)
    }

    @Test
    fun `cabecera ausente o de otra version lanza IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> { LectorRadares.leer(emptySequence()) }
        assertFailsWith<IllegalArgumentException> { LectorRadares.leer(sequenceOf("#radares v2")) }
        assertFailsWith<IllegalArgumentException> { LectorRadares.leer(sequenceOf("FIJO\ta\t39.5\t2.8")) }
    }

    @Test
    fun `solo cabecera da lista vacia`() {
        val r = LectorRadares.leer(sequenceOf(cabecera))
        assertEquals(emptyList<Radar>(), r.radares)
        assertEquals(0, r.descartadas)
    }
}
