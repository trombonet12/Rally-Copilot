package app.trombito.rallycopilot.domain.navegacion

import app.trombito.rallycopilot.data.escucha.UbicacionFalsa
import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.domain.ruta.ResultadoRuta
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.ui.navegacion.FalsoControlNavegacion
import app.trombito.rallycopilot.ui.navegacion.FalsoEnrutador
import app.trombito.rallycopilot.ui.navegacion.PALMA
import app.trombito.rallycopilot.ui.navegacion.SOLLER
import app.trombito.rallycopilot.ui.navegacion.rutaDePrueba
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** «Ir a un destino» sin vista previa (lo que usan las órdenes de voz). */
class IniciarRutaATest {
    private val control = FalsoControlNavegacion().also { it.publicar(EstadoNavegacion.Libre(0L, 0.0)) }
    private val enrutador = FalsoEnrutador()
    private val ubicacion = UbicacionFalsa()
    private val iniciar = IniciarRutaA(control, enrutador, ubicacion)
    private val destino = Destino("Sóller", SOLLER)

    private fun ok(tiempoS: Double = 1_500.0) =
        ResultadoRuta.Ok(rutaDePrueba(longitudM = 18_000.0, tiempoS = tiempoS), 5L)

    @Test
    fun `en modo libre calcula la mas rapida desde la posicion y empieza el guiado`() =
        runBlocking {
            enrutador.respuestas[PerfilRuta.MAS_RAPIDA] = ok()

            val resultado = iniciar(destino)

            assertEquals(ResultadoInicioRuta.Iniciada(1_500.0, 18_000.0), resultado)
            assertEquals(listOf(Triple(PuntoGeo(39.7, 2.9), SOLLER, PerfilRuta.MAS_RAPIDA)), enrutador.peticiones)
            assertEquals(destino, control.empezadas.single().first)
            assertEquals(PerfilRuta.MAS_RAPIDA, control.empezadas.single().second)
            assertTrue(control.ordenes.isEmpty())
        }

    @Test
    fun `guiando cambia el destino conservando perfil y paradas`() =
        runBlocking {
            val parada = Destino("Inca", PuntoGeo(39.72, 2.91))
            control.publicar(
                EstadoNavegacion.Guiando(
                    Destino("Palma", PALMA),
                    PerfilRuta.SINUOSA,
                    rutaDePrueba(),
                    progreso = null,
                    recalculando = false,
                    paradas = listOf(parada),
                ),
            )
            enrutador.respuestas[PerfilRuta.SINUOSA] = ok(2_400.0)

            val resultado = iniciar(destino)

            assertEquals(ResultadoInicioRuta.Iniciada(2_400.0, 18_000.0), resultado)
            assertEquals(listOf(listOf(parada.punto)), enrutador.peticionesConParadas)
            assertEquals(listOf(OrdenParadas.CambiarDestino(destino)), control.ordenes)
            assertTrue(control.empezadas.isEmpty())
        }

    @Test
    fun `sin sesion de conduccion no hace nada`() =
        runBlocking {
            control.terminar()

            assertEquals(ResultadoInicioRuta.SinConduccion, iniciar(destino))
            assertTrue(enrutador.peticiones.isEmpty())
            assertTrue(control.empezadas.isEmpty())
        }

    @Test
    fun `sin posicion no calcula la ruta`() =
        runBlocking {
            ubicacion.fijacion = null

            assertEquals(ResultadoInicioRuta.SinPosicion, iniciar(destino))
            assertTrue(enrutador.peticiones.isEmpty())
        }

    @Test
    fun `sin datos de rutas o con error de calculo no empieza`() =
        runBlocking {
            assertEquals(ResultadoInicioRuta.SinDatos, iniciar(destino))

            enrutador.respuestas[PerfilRuta.MAS_RAPIDA] = ResultadoRuta.Error("sin camino")
            assertEquals(ResultadoInicioRuta.Error("sin camino"), iniciar(destino))
            assertTrue(control.empezadas.isEmpty())
        }
}
