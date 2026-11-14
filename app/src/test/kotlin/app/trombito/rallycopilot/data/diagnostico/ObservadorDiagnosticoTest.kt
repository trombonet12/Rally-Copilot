package app.trombito.rallycopilot.data.diagnostico

import app.trombito.rallycopilot.domain.navegacion.Destino
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.domain.tramos.CronoEnCurso
import app.trombito.rallycopilot.domain.tramos.ResultadoCrono
import app.trombito.rallycopilot.domain.tramos.TramoCronometrado
import app.trombito.rallycopilot.domain.ubicacion.PosicionEstimada
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.ruta.Ruta
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ObservadorDiagnosticoTest {
    private val registro = RegistroEnMemoria()
    private val destino = Destino("Sóller", PuntoGeo(39.7, 2.7))
    private val ruta = Ruta(listOf(PuntoGeo(39.6, 2.6), PuntoGeo(39.7, 2.7)), emptyList(), 12_340.0, 900.0)

    private fun guiando(
        r: Ruta = ruta,
        recalculando: Boolean = false,
    ) = EstadoNavegacion.Guiando(destino, PerfilRuta.SINUOSA, r, null, recalculando)

    private fun observador(
        estado: MutableStateFlow<EstadoNavegacion>,
        crono: MutableStateFlow<CronoEnCurso?>,
        alcance: TestScope,
    ) = ObservadorDiagnostico(
        estado = estado,
        crono = crono,
        posiciones = { emptyFlow<PosicionEstimada>() },
        registro = registro,
        alcance = alcance,
        relojMonotonicoMs = { 0L },
    )

    @Test
    fun `anota inicio ruta recalculo llegada y fin sin coordenadas`() {
        val alcance = TestScope(UnconfinedTestDispatcher())
        val estado = MutableStateFlow<EstadoNavegacion>(EstadoNavegacion.Inactiva)
        observador(estado, MutableStateFlow(null), alcance).iniciar()
        estado.value = guiando()
        estado.value = guiando(recalculando = true)
        estado.value = guiando(r = ruta.copy(longitudM = 9_000.0))
        estado.value = EstadoNavegacion.Llegada(destino)
        estado.value = EstadoNavegacion.Inactiva
        val texto = registro.lineas.joinToString("\n")
        assertTrue(texto, "navegacion inicio modo=guiado perfil=SINUOSA" in texto)
        assertTrue(texto, "ruta calculada km=12.34 previsto_s=900" in texto)
        assertTrue(texto, "recalculo iniciado" in texto)
        assertTrue(texto, "ruta recalculada km=9.00" in texto)
        assertTrue(texto, "navegacion llegada" in texto)
        assertTrue("el nombre del destino no se registra: $texto", "Sóller" !in texto)
        assertTrue(texto, "navegacion fin modo=llegada" in texto)
        assertTrue(registro.lineas.none { FiltroRegistro.sospechosa(it) })
        alcance.cancel()
    }

    @Test
    fun `anota el tramo completado con tiempo y delta`() =
        runTest {
            val alcance = TestScope(UnconfinedTestDispatcher(testScheduler))
            val crono = MutableStateFlow<CronoEnCurso?>(null)
            observador(MutableStateFlow(EstadoNavegacion.Inactiva), crono, alcance).iniciar()
            val tramo = TramoCronometrado("t1", "Ma-10 Sóller", 5_000.0, 5_000.0, emptyList(), emptyList())
            crono.value = CronoEnCurso(tramo, 1_000, 0)
            crono.value =
                CronoEnCurso(tramo, 200_000, 0, resultado = ResultadoCrono(200_000, -1_500, true, 201_500))
            assertTrue(registro.lineas.any { "tramo iniciado nombre=Ma-10 Sóller longitud_m=5000" in it })
            assertEquals(
                1,
                registro.lineas.count {
                    "tramo completado nombre=Ma-10 Sóller tiempo_ms=200000 delta_ms=-1500" in it
                },
            )
            alcance.cancel()
        }
}
