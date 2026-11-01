package app.trombito.rallycopilot.motor.analisis

import app.trombito.rallycopilot.motor.tramos.MarcaTiempo
import app.trombito.rallycopilot.motor.tramos.Pasada
import app.trombito.rallycopilot.motor.tramos.assertCerca
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PerfilPilotoTest {
    private val dia = 86_400_000L

    private fun pasada(
        tramoId: String,
        distanciaM: Double,
        totalMs: Long,
    ) = Pasada(tramoId, listOf(MarcaTiempo(0.0, 0L), MarcaTiempo(distanciaM, totalMs)), totalMs)

    private fun registrada(
        id: String,
        tramoId: String,
        distanciaM: Double,
        totalMs: Long,
        dias: Int,
    ) = PasadaRegistrada(id, pasada(tramoId, distanciaM, totalMs), dias * dia)

    // A: 4 km. Pasadas (por fecha): 200 s, 190 s, 195 s, 180 s. B: 2 km, una de 100 s. C: sin pasadas.
    private val a =
        TramoRegistrado(
            "a",
            "Ma-2141 · Escorca",
            4000.0,
            listOf(
                registrada("a3", "a", 4000.0, 195_000L, 3),
                registrada("a1", "a", 4000.0, 200_000L, 1),
                registrada("a4", "a", 4000.0, 180_000L, 4),
                registrada("a2", "a", 4000.0, 190_000L, 2),
            ),
        )
    private val b =
        TramoRegistrado("b", "Ma-10 Pollença → Lluc", 2000.0, listOf(registrada("b1", "b", 2000.0, 100_000L, 5)))
    private val c = TramoRegistrado("c", "Sin pasadas", 1500.0, emptyList())
    private val recorridos =
        listOf(
            RecorridoRegistrado(1 * dia, 12_500.0),
            RecorridoRegistrado(3 * dia, 30_000.0),
            RecorridoRegistrado(5 * dia, 7_500.0),
        )

    @Test
    fun `agrega kilometros tramos y pasadas`() {
        val perfil = PerfilPiloto.de(listOf(a, b, c), recorridos)
        assertCerca(50.0, perfil.kmTotales, 1e-9)
        assertEquals(3, perfil.numeroTramos)
        assertEquals(5, perfil.numeroPasadas)
        assertEquals(listOf("a", "b", "c"), perfil.tramos.map { it.tramoId })
    }

    @Test
    fun `mejor tiempo medias y velocidades por tramo`() {
        val perfil = PerfilPiloto.de(listOf(a, b, c), recorridos)
        val ra = perfil.tramos[0]
        assertEquals(4, ra.numeroPasadas)
        assertEquals(180_000L, ra.mejorMs)
        assertEquals("a4", ra.mejorPasadaId)
        assertEquals(4 * dia, ra.mejorFechaMs)
        assertEquals(191_250L, ra.mediaMs)
        assertCerca(4000.0 / 191.25 * 3.6, ra.velMediaKmh!!, 1e-6)
        assertCerca(4000.0 / 180.0 * 3.6, ra.velMejorKmh!!, 1e-6)

        val rc = perfil.tramos[2]
        assertEquals(0, rc.numeroPasadas)
        assertNull(rc.mejorMs)
        assertNull(rc.mediaMs)
        assertNull(rc.velMediaKmh)
        assertTrue(rc.evolucion.isEmpty())
    }

    @Test
    fun `velocidad media global ponderada por distancia`() {
        val perfil = PerfilPiloto.de(listOf(a, b, c), recorridos)
        // 4 pasadas de 4 km (765 s en total) y una de 2 km en 100 s.
        val metros = 4 * 4000.0 + 2000.0
        val segundos = (195.0 + 200.0 + 180.0 + 190.0) + 100.0
        assertCerca(metros / segundos * 3.6, perfil.velMediaKmh!!, 1e-6)
    }

    @Test
    fun `la evolucion va por fecha y marca los records`() {
        val evolucion =
            PerfilPiloto
                .de(listOf(a), recorridos)
                .tramos
                .single()
                .evolucion
        assertEquals(listOf("a1", "a2", "a3", "a4"), evolucion.map { it.pasadaId })
        assertEquals(listOf(1L, 2L, 3L, 4L).map { it * dia }, evolucion.map { it.fechaMs })
        assertEquals(listOf(200_000L, 190_000L, 195_000L, 180_000L), evolucion.map { it.tiempoMs })
        assertEquals(listOf(true, true, false, true), evolucion.map { it.esRecord })
    }

    @Test
    fun `el historial es reciente primero con la diferencia contra el mejor`() {
        val historial = PerfilPiloto.de(listOf(a, b, c), recorridos).historial
        assertEquals(listOf("b1", "a4", "a3", "a2", "a1"), historial.map { it.pasadaId })
        val a3 = historial.first { it.pasadaId == "a3" }
        assertEquals(15_000L, a3.deltaMejorMs)
        assertFalse(a3.esRecord)
        val a4 = historial.first { it.pasadaId == "a4" }
        assertEquals(0L, a4.deltaMejorMs)
        assertTrue(a4.esRecord)
        assertEquals("Ma-2141 · Escorca", a4.nombre)
        assertEquals(2, PerfilPiloto.de(listOf(a, b, c), recorridos, historialMax = 2).historial.size)
    }

    @Test
    fun `un empate de tiempos deja como mejor la pasada mas antigua`() {
        val empate =
            TramoRegistrado(
                "e",
                "E",
                1000.0,
                listOf(registrada("nueva", "e", 1000.0, 50_000L, 9), registrada("vieja", "e", 1000.0, 50_000L, 2)),
            )
        val resumen = PerfilPiloto.de(listOf(empate), emptyList()).tramos.single()
        assertEquals("vieja", resumen.mejorPasadaId)
        assertEquals(listOf(true, false), resumen.evolucion.map { it.esRecord })
    }

    @Test
    fun `sin datos el perfil esta vacio`() {
        val perfil = PerfilPiloto.de(emptyList(), emptyList())
        assertEquals(0.0, perfil.kmTotales, 0.0)
        assertEquals(0, perfil.numeroPasadas)
        assertNull(perfil.velMediaKmh)
        assertTrue(perfil.historial.isEmpty())
    }
}
