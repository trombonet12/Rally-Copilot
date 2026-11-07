package app.trombito.rallycopilot.data.tramos

import app.trombito.rallycopilot.domain.tramos.ResultadoGuardado
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.ui.comun.FalsoRepositorioTramos
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardadorTramosTest {
    private val repositorio = FalsoRepositorioTramos()
    private var ahora = 1_700_000_000_000L
    private val guardador = GuardadorTramos(repositorio, { null }, { ahora })

    /** Unos 3 km hacia el norte: de sobra para ser un tramo. */
    private val traza = List(31) { PuntoGeo(39.6 + it * 0.001, 2.9) }

    @Test
    fun `la fecha de creacion sale del reloj inyectado`() {
        val resultado = runBlocking { guardador.guardar(traza, "Mi tramo") }
        assertTrue(resultado.toString(), resultado is ResultadoGuardado.Guardado)
        assertEquals(1_700_000_000_000L, repositorio.sinPasadas.single().creadoEnMs)

        ahora += 5_000L
        runBlocking { guardador.guardar(traza, "Otro") }
        assertEquals(listOf(1_700_000_000_000L, 1_700_000_005_000L), repositorio.sinPasadas.map { it.creadoEnMs })
    }

    @Test
    fun `una traza corta no guarda nada ni consulta el reloj`() {
        var consultas = 0
        val g = GuardadorTramos(repositorio, { null }, { consultas++.toLong() })
        val corta = listOf(PuntoGeo(39.6, 2.9), PuntoGeo(39.6001, 2.9))
        assertEquals(ResultadoGuardado.Corto, runBlocking { g.guardar(corta, null) })
        assertTrue(repositorio.sinPasadas.isEmpty())
        assertEquals(0, consultas)
    }
}
