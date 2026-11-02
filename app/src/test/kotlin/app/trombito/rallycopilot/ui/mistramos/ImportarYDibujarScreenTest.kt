package app.trombito.rallycopilot.ui.mistramos

import app.trombito.rallycopilot.domain.importar.MotivoFalloImportar
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import app.trombito.rallycopilot.ui.theme.RallyCopilotTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ImportarYDibujarScreenTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()

    private val vacio = MisTramosUiState(cargando = false)

    @Test
    fun `sin callbacks no hay botones de importar ni de dibujar`() {
        banco.poner { RallyCopilotTheme { MisTramosScreen(vacio, AccionesMisTramos()) } }
        assertFalse(banco.hay(ETIQUETA_TRAMOS_IMPORTAR))
        assertFalse(banco.hay(ETIQUETA_TRAMOS_DIBUJAR))
    }

    @Test
    fun `los botones de importar y dibujar llaman a sus acciones`() {
        var importar = 0
        var dibujar = 0
        banco.poner {
            RallyCopilotTheme {
                MisTramosScreen(vacio, AccionesMisTramos(onImportar = { importar++ }, onDibujar = { dibujar++ }))
            }
        }
        banco.pulsar(banco.conEtiqueta(ETIQUETA_TRAMOS_IMPORTAR))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_TRAMOS_DIBUJAR))
        assertEquals(1, importar)
        assertEquals(1, dibujar)
    }

    @Test
    fun `importar muestra el progreso y el aviso de importado o de fallo se da por visto`() {
        banco.poner {
            RallyCopilotTheme {
                MisTramosScreen(
                    vacio,
                    AccionesMisTramos(),
                    importacion = EstadoImportacion.Importando,
                )
            }
        }
        assertTrue(banco.hay(ETIQUETA_TRAMOS_IMPORTANDO))

        val acciones = AccionesMisTramos()
        banco.poner {
            RallyCopilotTheme {
                MisTramosScreen(vacio, acciones, importacion = EstadoImportacion.Hecho("t", "Ma-13 · Santa María"))
            }
        }
        banco.esperar(500)
        assertTrue(banco.todosLosTextos().any { "Ma-13 · Santa María" in it && "importado" in it })

        banco.poner {
            RallyCopilotTheme {
                MisTramosScreen(vacio, acciones, importacion = EstadoImportacion.Fallo(MotivoFalloImportar.CORTO))
            }
        }
        banco.esperar(500)
        assertTrue(banco.todosLosTextos().any { "menos de un kilómetro" in it })
    }

    private fun dibujo(
        estado: DibujarUiState,
        acciones: AccionesDibujar = AccionesDibujar(),
    ) = banco.poner { RallyCopilotTheme { DibujarScreen(estado, acciones, mapa = {}) } }

    private val a = PuntoGeo(39.70, 2.80)
    private val b = PuntoGeo(39.71, 2.80)

    @Test
    fun `sin puntos solo se ve la ayuda y deshacer, borrar y guardar estan desactivados`() {
        dibujo(DibujarUiState())
        assertTrue(banco.todosLosTextos().any { "Toca el mapa" in it })
        assertFalse(banco.estaHabilitado(banco.conEtiqueta(ETIQUETA_DIBUJAR_DESHACER)))
        assertFalse(banco.estaHabilitado(banco.conEtiqueta(ETIQUETA_DIBUJAR_BORRAR)))
        assertFalse(banco.estaHabilitado(banco.conEtiqueta(ETIQUETA_DIBUJAR_GUARDAR)))
    }

    @Test
    fun `con ruta calculada se ve la longitud y los tres iconos llaman a sus acciones`() {
        var deshacer = 0
        var borrar = 0
        var guardar = 0
        dibujo(
            DibujarUiState(puntos = listOf(a, b), ruta = listOf(a, b), longitudM = 4_200.0),
            AccionesDibujar(onDeshacer = { deshacer++ }, onBorrarTodo = { borrar++ }, onGuardar = { guardar++ }),
        )
        assertTrue(banco.todosLosTextos().any { "4,2 km" in it && "2 puntos" in it })
        banco.pulsar(banco.conEtiqueta(ETIQUETA_DIBUJAR_DESHACER))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_DIBUJAR_BORRAR))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_DIBUJAR_GUARDAR))
        assertEquals(listOf(1, 1, 1), listOf(deshacer, borrar, guardar))
    }

    @Test
    fun `mientras calcula no se puede guardar y un error se muestra`() {
        dibujo(DibujarUiState(puntos = listOf(a, b), calculando = true))
        assertTrue(banco.hay(ETIQUETA_DIBUJAR_CALCULANDO))
        assertFalse(banco.estaHabilitado(banco.conEtiqueta(ETIQUETA_DIBUJAR_GUARDAR)))
        dibujo(DibujarUiState(puntos = listOf(a, b), error = ErrorDibujo.RUTA))
        assertTrue(banco.todosLosTextos().any { "No se pudo calcular la ruta" in it })
    }

    @Test
    fun `la ruta dibujada marca salida, paradas numeradas y meta`() {
        assertNull(rutaDibujada(DibujarUiState()))
        val tres = listOf(a, b, PuntoGeo(39.72, 2.80))
        val ruta = checkNotNull(rutaDibujada(DibujarUiState(puntos = tres)))
        assertEquals(tres, ruta.puntos)
        assertEquals(tres.last(), ruta.destino)
        assertEquals(listOf(b), ruta.paradas.map { it.punto })
        assertEquals(1, ruta.paradas.single().numero)
        assertEquals(a, ruta.elegido)
        val uno = checkNotNull(rutaDibujada(DibujarUiState(puntos = listOf(a))))
        assertTrue("Con un punto no hay línea", uno.puntos.isEmpty())
        assertNull(uno.destino)
    }
}
