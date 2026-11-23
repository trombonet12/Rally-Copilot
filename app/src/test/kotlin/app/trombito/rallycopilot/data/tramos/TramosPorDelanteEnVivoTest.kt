package app.trombito.rallycopilot.data.tramos

import app.trombito.rallycopilot.domain.notas.NotasEnCurso
import app.trombito.rallycopilot.domain.notas.OrigenNotas
import app.trombito.rallycopilot.domain.notas.ProveedorNotas
import app.trombito.rallycopilot.domain.tramos.RepositorioTramos
import app.trombito.rallycopilot.domain.tramos.TramoCronometrable
import app.trombito.rallycopilot.domain.tramos.TramosPorDelante
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.longitudMetros
import app.trombito.rallycopilot.motor.notas.HojaNotas
import app.trombito.rallycopilot.motor.tramos.Tramo
import app.trombito.rallycopilot.ui.comun.FalsoRepositorioTramos
import app.trombito.rallycopilot.ui.comun.resumenFalso
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TramosPorDelanteEnVivoTest {
    private fun punto(m: Double) = PuntoGeo(39.5 + m / METROS_POR_GRADO, 2.9)

    private fun carretera(largoM: Double) = (0..(largoM / 100.0).toInt()).map { punto(it * 100.0) }

    private fun tramo(
        id: String,
        desdeM: Double,
        hastaM: Double,
    ): Tramo {
        val traza = (0..((hastaM - desdeM) / 100.0).toInt()).map { punto(desdeM + it * 100.0) }
        return Tramo(id, traza, traza.longitudMetros())
    }

    private class NotasFalsas : ProveedorNotas {
        val actual = MutableStateFlow<NotasEnCurso?>(null)
        override val notas: StateFlow<NotasEnCurso?> = actual
    }

    private fun notasEn(
        traza: List<PuntoGeo>,
        posicionM: Double,
    ) = NotasEnCurso(HojaNotas(emptyList()), posicionM, 20.0, OrigenNotas.GUIADO, traza)

    /** Repositorio que cuenta cuántas veces se leen los tramos para cronometrar. */
    private class Contado(
        val base: FalsoRepositorioTramos,
    ) : RepositorioTramos by base {
        var lecturas = 0

        override suspend fun cronometrables(): List<TramoCronometrable> {
            lecturas++
            return base.cronometrables()
        }
    }

    private fun repositorio(vararg tramos: Tramo) =
        FalsoRepositorioTramos(
            resumenes = tramos.map { resumenFalso(it.id) },
            cronometrablesIniciales = tramos.map { TramoCronometrable(it, null) },
        )

    @Test
    fun `emite los tramos de la ruta guiada y la distancia a su meta`() =
        runTest {
            val notas = NotasFalsas()
            val repo = repositorio(tramo("A", 1_000.0, 3_000.0))
            val proveedor = TramosPorDelanteEnVivo(repo, notas, UnconfinedTestDispatcher(testScheduler))
            val recibidos = mutableListOf<TramosPorDelante>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { proveedor.porDelante.toList(recibidos) }

            notas.actual.value = notasEn(carretera(6_000.0), 0.0)

            val ultimo = recibidos.last()
            assertEquals(listOf("A"), ultimo.tramos.map { it.tramoId })
            assertEquals(3_000.0, ultimo.finDeTramoM!!, 30.0)
        }

    @Test
    fun `sin tramos guardados o sin conduccion emite vacio`() =
        runTest {
            val notas = NotasFalsas()
            val proveedor = TramosPorDelanteEnVivo(repositorio(), notas, UnconfinedTestDispatcher(testScheduler))
            val recibidos = mutableListOf<TramosPorDelante>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { proveedor.porDelante.toList(recibidos) }

            notas.actual.value = notasEn(carretera(6_000.0), 0.0)
            notas.actual.value = null

            assertTrue(recibidos.isNotEmpty())
            assertTrue(recibidos.all { it.tramos.isEmpty() && it.finDeTramoM == null })
        }

    @Test
    fun `los tramos se vuelven a cargar solo cuando cambia el conjunto de tramos`() =
        runTest {
            val a = tramo("A", 1_000.0, 3_000.0)
            val base = repositorio(a)
            val contado = Contado(base)
            val notas = NotasFalsas()
            val proveedor = TramosPorDelanteEnVivo(contado, notas, UnconfinedTestDispatcher(testScheduler))
            val recibidos = mutableListOf<TramosPorDelante>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { proveedor.porDelante.toList(recibidos) }
            notas.actual.value = notasEn(carretera(6_000.0), 0.0)
            assertEquals(1, contado.lecturas)

            // Una pasada nueva cambia el resumen del tramo pero no el conjunto de ids: no se recarga.
            base.resumenesMutable.value = listOf(resumenFalso("A", pasadas = 5))
            assertEquals(1, contado.lecturas)

            // Un tramo nuevo sí.
            base.resumenesMutable.value = listOf(resumenFalso("A"), resumenFalso("B"))
            assertEquals(2, contado.lecturas)
        }

    private companion object {
        /** Metros por grado de latitud con el radio medio de `PuntoGeo`. */
        const val METROS_POR_GRADO = 111_194.93
    }
}
