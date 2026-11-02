package app.trombito.rallycopilot.data.notas

import app.trombito.rallycopilot.domain.notas.NotasEnCurso
import app.trombito.rallycopilot.motor.edicion.AccionEdicion
import app.trombito.rallycopilot.motor.edicion.InterpreteOrdenes
import app.trombito.rallycopilot.motor.edicion.Orden
import app.trombito.rallycopilot.motor.notas.Direccion
import app.trombito.rallycopilot.motor.notas.Modificador
import app.trombito.rallycopilot.motor.notas.Nota
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Las notas editadas prevalecen sobre las automáticas y sobreviven a regenerarlas. */
@OptIn(ExperimentalCoroutinesApi::class)
class NotasSesionEdicionesTest {
    private val publicadas = mutableListOf<NotasEnCurso?>()
    private val fallos = mutableListOf<Throwable>()
    private val curva = trazadoConCurva()

    private fun TestScope.sesion() =
        NotasSesion(
            ContextoFalso(contextoDe(listOf(via(1, curva)))),
            CalibradorFalso(),
            { publicadas += it },
            StandardTestDispatcher(testScheduler),
            { fallos += it },
        )

    private val ultima get() = checkNotNull(publicadas.filterNotNull().lastOrNull())

    /** Sustituir en el punto medio de la curva automática (a ~660 m de la salida) con un grado distinto. */
    private fun sustituirCurva(grado: Int) =
        EdicionEnMapa(
            p(660.0, 60.0),
            AccionEdicion.Sustituir(Nota(0.0, 0.0, Direccion.IZQUIERDA, grado)),
            marcaTiempoMs = 10L,
        )

    @Test
    fun `una nota editada sobrevive a regenerar las automaticas del tramo`() =
        runTest {
            val sesion = sesion()
            sesion.iniciarGuiado(curva)
            advanceUntilIdle()
            sesion.posicionGuiada(100.0, 20.0)
            val automatica =
                checkNotNull(
                    ultima.hoja.notas
                        .single()
                        .grado,
                )

            sesion.fijarEdiciones(listOf(sustituirCurva(if (automatica == 1) 2 else 1)))
            val editado =
                checkNotNull(
                    ultima.hoja.notas
                        .single()
                        .grado,
                )
            assertTrue("editó el grado $automatica", editado != automatica)

            // Regenerar: nueva ruta (recálculo) y otra vez la misma hoja automática.
            sesion.nuevaRuta(curva)
            advanceUntilIdle()
            sesion.posicionGuiada(120.0, 20.0)

            assertEquals(
                editado,
                ultima.hoja.notas
                    .single()
                    .grado,
            )
            assertTrue(fallos.toString(), fallos.isEmpty())
            sesion.terminar()
        }

    @Test
    fun `quitar una nota editada la saca de la hoja y una edicion lejos de la ruta no cuenta`() =
        runTest {
            val sesion = sesion()
            sesion.iniciarGuiado(curva)
            advanceUntilIdle()
            sesion.posicionGuiada(100.0, 20.0)

            sesion.fijarEdiciones(listOf(EdicionEnMapa(p(660.0, 60.0), AccionEdicion.Quitar, 1L)))
            assertTrue(ultima.hoja.notas.isEmpty())

            sesion.fijarEdiciones(listOf(EdicionEnMapa(p(5_000.0, 5_000.0), AccionEdicion.Quitar, 1L)))
            assertEquals(1, ultima.hoja.notas.size)
            sesion.terminar()
        }

    @Test
    fun `dictar derecha tres larga en reconocimiento crea esa nota en la posicion actual`() =
        runTest {
            val traza = recta(0.0, 0.0, haciaEste = true, largoM = 1_000.0)
            val sesion = sesion()
            sesion.iniciarReconocimiento("tramo-1", traza)
            advanceUntilIdle()
            val fijacion = Fijacion(p(400.0, 3.0), 5_000_000L, 5.0, 90.0, 5.0)
            sesion.posicionReconocimiento(fijacion)
            assertEquals("tramo-1", ultima.tramoReconocimiento)
            assertEquals(400.0, ultima.posicionM, 2.0)
            assertTrue(ultima.hoja.notas.isEmpty())

            val orden = checkNotNull(InterpreteOrdenes.interpretar("derecha tres larga")) as Orden.Nota
            val accion = AccionEdicion.Sustituir(orden.aNota(ultima.posicionM))
            sesion.fijarEdiciones(listOf(EdicionEnMapa(fijacion.punto, accion, 1L)))

            val nota = ultima.hoja.notas.single()
            assertEquals(Direccion.DERECHA, nota.direccion)
            assertEquals(3, nota.grado)
            assertEquals(setOf(Modificador.LARGA), nota.modificadores)
            assertEquals(400.0, nota.posicionM, 2.0)
            sesion.terminar()
        }

    @Test
    fun `en reconocimiento lejos de la traza no se publican notas`() =
        runTest {
            val sesion = sesion()
            sesion.iniciarReconocimiento("tramo-1", recta(0.0, 0.0, haciaEste = true, largoM = 1_000.0))
            advanceUntilIdle()

            sesion.posicionReconocimiento(Fijacion(p(400.0, 900.0), 5_000_000L, 5.0, 90.0, 5.0))

            assertTrue(publicadas.lastOrNull() == null)
            sesion.terminar()
        }
}
