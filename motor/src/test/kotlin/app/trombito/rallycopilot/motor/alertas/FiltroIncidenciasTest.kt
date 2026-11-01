package app.trombito.rallycopilot.motor.alertas

import app.trombito.rallycopilot.motor.alertas.GeoPruebas.punto
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.voz.TipoFrase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class FiltroIncidenciasTest {
    private val ruta = GeoPruebas.rutaNorte(largoM = 20_000.0, pasoM = 500.0)
    private val v120 = 33.34

    private fun inc(
        id: String,
        vararg puntos: PuntoGeo,
        tipo: TipoIncidencia = TipoIncidencia.OBRAS,
        fin: Instant? = null,
    ) = Incidencia(id, tipo, puntos.toList(), "Ma-13", "descripcion", null, fin, null)

    private fun filtro(
        vararg incidencias: Incidencia,
        conRuta: Boolean = true,
        ahora: Instant? = null,
        tipos: Set<TipoIncidencia> = TipoIncidencia.entries.toSet(),
    ) = FiltroIncidencias(incidencias.toList(), if (conRuta) ruta else null, ahora, tipos)

    // ---- Qué incidencias importan ----

    @Test
    fun `las de la ruta por delante entran siempre, aunque esten a 15 km`() {
        val f = filtro(inc("lejos", punto(15_000.0, 20.0)))
        val r = f.relevantes(punto(0.0), posicionM = 0.0).single()
        assertTrue(r.enRuta)
        assertEquals(15_000.0, r.distanciaM, 1.0)
    }

    @Test
    fun `a 50 m o menos de la ruta cuenta como de la ruta y a mas no`() {
        assertTrue(filtro(inc("a", punto(5000.0, 49.0))).relevantes(punto(0.0), 0.0).single().enRuta)
        // A 51 m ya no es de la ruta; a 5 km del coche tampoco entra por cercania.
        assertEquals(emptyList<IncidenciaCercana>(), filtro(inc("b", punto(5000.0, 51.0))).relevantes(punto(0.0), 0.0))
    }

    @Test
    fun `las que no son de la ruta entran si estan a 3 km o menos del coche`() {
        val f = filtro(inc("cerca", punto(0.0, 2990.0)), inc("lejos", punto(0.0, 3010.0)))
        val r = f.relevantes(punto(0.0), 0.0)
        assertEquals(listOf("cerca"), r.map { it.incidencia.id })
        assertFalse(r.single().enRuta)
        assertEquals(2990.0, r.single().distanciaM, 5.0)
    }

    @Test
    fun `una de la ruta que ya queda detras se trata como las demas (3 km)`() {
        val f = filtro(inc("detras", punto(5000.0, 10.0)))
        val cerca = f.relevantes(punto(6500.0), posicionM = 6500.0).single()
        assertFalse(cerca.enRuta)
        assertEquals(1500.0, cerca.distanciaM, 5.0)
        assertEquals(emptyList<IncidenciaCercana>(), f.relevantes(punto(9000.0), posicionM = 9000.0))
    }

    @Test
    fun `una incidencia en tramo usa la distancia al segmento y no solo a sus extremos`() {
        // Paralela a la ruta, 2 km al este, de 0 a 10 km: el coche en el km 5 la tiene a 2 km (los extremos, a 5 km).
        val f = filtro(inc("paralela", punto(0.0, 2000.0), punto(10_000.0, 2000.0)))
        val r = f.relevantes(punto(5000.0), 5000.0).single()
        assertFalse(r.enRuta)
        assertEquals(2000.0, r.distanciaM, 5.0)
    }

    @Test
    fun `una incidencia en tramo sobre la ruta, dentro es distancia cero y antes es la distancia a su inicio`() {
        val f = filtro(inc("tramo", punto(4000.0, 5.0), punto(6000.0, 5.0)))
        val dentro = f.relevantes(punto(5000.0), 5000.0).single()
        assertTrue(dentro.enRuta)
        assertEquals(0.0, dentro.distanciaM, 0.0)
        assertEquals(500.0, f.relevantes(punto(3500.0), 3500.0).single().distanciaM, 1.0)
    }

    @Test
    fun `el extremo del tramo en orden inverso al de la marcha tambien vale`() {
        val f = filtro(inc("tramo", punto(6000.0, 5.0), punto(4000.0, 5.0)))
        assertEquals(500.0, f.relevantes(punto(3500.0), 3500.0).single().distanciaM, 1.0)
    }

    @Test
    fun `primero las de la ruta, luego las otras, cada grupo por distancia`() {
        val f =
            filtro(
                inc("otra-cerca", punto(0.0, 500.0)),
                inc("ruta-lejos", punto(9000.0, 5.0)),
                inc("otra-lejos", punto(0.0, 2000.0)),
                inc("ruta-cerca", punto(2000.0, 5.0)),
            )
        assertEquals(
            listOf("ruta-cerca", "ruta-lejos", "otra-cerca", "otra-lejos"),
            f.relevantes(punto(0.0), 0.0).map { it.incidencia.id },
        )
    }

    @Test
    fun `sin ruta solo cuenta el radio de 3 km`() {
        val f = filtro(inc("a", punto(2000.0)), inc("b", punto(5000.0)), conRuta = false)
        assertEquals(listOf("a"), f.relevantes(punto(0.0)).map { it.incidencia.id })
        assertTrue(f.candidatas(0.0, v120).isEmpty())
    }

    @Test
    fun `con ruta pero sin posicion en la traza tampoco hay ruta`() {
        val f = filtro(inc("a", punto(2000.0, 5.0)), inc("b", punto(5000.0, 5.0)))
        val r = f.relevantes(punto(0.0), posicionM = null)
        assertEquals(listOf("a"), r.map { it.incidencia.id })
        assertFalse(r.single().enRuta)
    }

    @Test
    fun `las caducadas no entran`() {
        val ahora = Instant.parse("2026-11-01T12:00:00Z")
        val f =
            filtro(
                inc("vieja", punto(2000.0, 5.0), fin = Instant.parse("2026-11-01T11:59:59Z")),
                inc("vigente", punto(2000.0, 5.0), fin = Instant.parse("2026-11-01T12:00:01Z")),
                ahora = ahora,
            )
        assertEquals(listOf("vigente"), f.relevantes(punto(0.0), 0.0).map { it.incidencia.id })
    }

    // ---- Avisos por voz ----

    @Test
    fun `al acercarse a unas obras de la ruta suena obras a quinientos`() {
        val f = filtro(inc("o1", punto(5000.0, 20.0)))
        val frase = f.candidatas(4510.0, v120).single()
        assertEquals("obras a quinientos", frase.texto)
        assertEquals("incidencia-o1", frase.id)
        assertEquals(TipoFrase.ALERTA, frase.tipo)
        assertEquals(5000.0, frase.puntoM!!, 1.0)
    }

    @Test
    fun `cada tipo tiene su palabra`() {
        val esperado =
            mapOf(
                TipoIncidencia.OBRAS to "obras a quinientos",
                TipoIncidencia.ACCIDENTE to "accidente a quinientos",
                TipoIncidencia.RETENCION to "retención a quinientos",
                TipoIncidencia.CORTE to "carretera cortada a quinientos",
                TipoIncidencia.METEO to "peligro a quinientos",
                TipoIncidencia.OTRA to "incidencia a quinientos",
            )
        for ((tipo, texto) in esperado) {
            val f = filtro(inc("x", punto(5000.0, 5.0), tipo = tipo))
            assertEquals(tipo.name, texto, f.candidatas(4510.0, v120).single().texto)
        }
    }

    @Test
    fun `usa la misma ventana que los radares (300-800 m segun la velocidad)`() {
        val f = filtro(inc("o", punto(5000.0, 5.0)))
        assertEquals(1, f.candidatas(4710.0, 0.0).size)
        assertEquals(0, f.candidatas(4690.0, 0.0).size)
        assertEquals(1, f.candidatas(4210.0, 60.0).size)
        assertEquals(0, f.candidatas(4190.0, 60.0).size)
    }

    @Test
    fun `dentro de la incidencia o ya pasada no suena y a menos de 50 m no dice distancia`() {
        val f = filtro(inc("t", punto(4000.0, 5.0), punto(6000.0, 5.0)))
        assertTrue(f.candidatas(5000.0, v120).isEmpty())
        assertTrue(f.candidatas(6500.0, v120).isEmpty())
        assertEquals("obras", f.candidatas(3970.0, v120).single().texto)
    }

    @Test
    fun `las que no son de la ruta no suenan`() {
        val f = filtro(inc("fuera", punto(5000.0, 500.0)))
        assertTrue(f.candidatas(4600.0, v120).isEmpty())
    }

    @Test
    fun `solo suenan los tipos permitidos pero todos son relevantes`() {
        val f =
            filtro(
                inc("o", punto(5000.0, 5.0), tipo = TipoIncidencia.OBRAS),
                inc("a", punto(5100.0, 5.0), tipo = TipoIncidencia.ACCIDENTE),
                tipos = setOf(TipoIncidencia.ACCIDENTE, TipoIncidencia.CORTE),
            )
        assertEquals(listOf("incidencia-a"), f.candidatas(4700.0, v120).map { it.id })
        assertEquals(2, f.relevantes(punto(4700.0), 4700.0).size)
    }

    @Test
    fun `varias incidencias salen en orden de ruta`() {
        val f = filtro(inc("b", punto(5300.0, 5.0)), inc("a", punto(5100.0, 5.0)))
        assertEquals(listOf("incidencia-a", "incidencia-b"), f.candidatas(4950.0, v120).map { it.id })
    }

    @Test
    fun `una ruta de un solo punto es un error de uso`() {
        val ex = runCatching { FiltroIncidencias(emptyList(), listOf(punto(0.0))) }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }
}
