package app.trombito.rallycopilot.ui.perfil

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import app.trombito.rallycopilot.domain.perfil.TableroPiloto
import app.trombito.rallycopilot.domain.perfil.UltimoViaje
import app.trombito.rallycopilot.motor.analisis.ResumenMes
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.ETIQUETA_SEGMENTO
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import app.trombito.rallycopilot.ui.theme.RallyCopilotTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Pantalla alta: la lista perezosa solo compone lo que cabe.
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2400dp-xxhdpi")
class TemporadaPerfilTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()

    private val tablero =
        TableroPiloto(
            viajes = 3,
            kmTotales = 42.0,
            primerViajeMs = diaMs(3),
            ultimoViaje = UltimoViaje(3L, diaMs(17), 20_000.0, null),
        )
    private val datos = temporadaDePrueba()
    private val estado = PerfilUiState(cargando = false, tablero = tablero, temporada = datos)

    private fun abrirTemporada(
        estadoInicial: PerfilUiState = estado,
        acciones: AccionesPerfil = AccionesPerfil(),
        falloCompartir: Boolean = false,
    ) {
        banco.poner {
            RallyCopilotTheme {
                PerfilScreen(
                    estadoInicial,
                    acciones,
                    falloCompartir = falloCompartir,
                    pestanaInicial = PestanaPerfil.TEMPORADA,
                )
            }
        }
    }

    @Test
    fun `por defecto se ve el tablero y la temporada espera en su pestana`() {
        banco.poner { RallyCopilotTheme { PerfilScreen(estado, AccionesPerfil()) } }
        assertTrue(banco.hay(ETIQUETA_PERFIL_CABECERA))
        assertFalse(banco.hay(ETIQUETA_TEMPORADA_FICHA))
        val pestanas = banco.textos(banco.conEtiqueta(ETIQUETA_PERFIL_PESTANAS))
        assertTrue(pestanas.toString(), "Tablero" in pestanas && "Temporada" in pestanas)
    }

    @Test
    fun `abierto en la temporada se ve la ficha y no el tablero`() {
        abrirTemporada()
        assertTrue(banco.hay(ETIQUETA_TEMPORADA_FICHA))
        assertFalse(banco.hay(ETIQUETA_PERFIL_CABECERA))
        assertEquals(true, banco.conEtiqueta("${ETIQUETA_SEGMENTO}1").config.getOrNull(SemanticsProperties.Selected))
    }

    @Test
    fun `la ficha dice el nivel y los puntos hacia el siguiente`() {
        abrirTemporada()
        val nivel = datos.temporada.nivel
        val esperado = "${nivel.nivel.nombre} · ${nivel.puntos}/${nivel.umbralSiguiente}"
        assertEquals(listOf(esperado), banco.textos(banco.conEtiqueta(ETIQUETA_TEMPORADA_NIVEL)))
        assertTrue("Piloto de club" in esperado)
        assertTrue(banco.hay(ETIQUETA_TEMPORADA_BARRA))
        val ficha = banco.textos(banco.conEtiqueta(ETIQUETA_TEMPORADA_FICHA))
        assertTrue(ficha.toString(), "Siguiente nivel: Piloto nacional" in ficha)
        assertTrue(ficha.toString(), ficha.any { it.contains("Nunca de la velocidad") })
        assertTrue(ficha.toString(), ficha.any { it.contains("420,0 km") && it.contains("3 viajes") })
    }

    @Test
    fun `la racha cuenta las semanas seguidas`() {
        abrirTemporada()
        val racha = banco.textos(banco.conEtiqueta(ETIQUETA_TEMPORADA_RACHA))
        assertTrue(racha.toString(), "3 semanas seguidas" in racha)
        assertTrue(racha.toString(), "Tu mejor racha: 3 semanas" in racha)
    }

    @Test
    fun `sin racha viva lo dice y anima a salir`() {
        val sinRacha = datos.copy(temporada = datos.temporada.copy(racha = datos.temporada.racha.copy(actual = 0)))
        abrirTemporada(estado.copy(temporada = sinRacha))
        val racha = banco.textos(banco.conEtiqueta(ETIQUETA_TEMPORADA_RACHA))
        assertTrue(racha.toString(), racha.any { it.startsWith("Sin racha") })
    }

    @Test
    fun `los kilometros por semana se dibujan y se describen para TalkBack`() {
        abrirTemporada()
        assertTrue(banco.hay(ETIQUETA_TEMPORADA_BARRAS_SEMANA))
        val descripcion = banco.descripcion(banco.conEtiqueta(ETIQUETA_TEMPORADA_SEMANAS)).orEmpty()
        assertTrue(descripcion, "últimas 4 semanas" in descripcion)
        assertTrue(descripcion, "420,0 km en total" in descripcion)
        assertTrue(descripcion, "la mejor 200,0 km" in descripcion)
    }

    @Test
    fun `los hitos conseguidos van con su fecha y el siguiente con su avance`() {
        abrirTemporada()
        val hitos = banco.textos(banco.conEtiqueta(ETIQUETA_TEMPORADA_HITOS))
        assertTrue(hitos.toString(), hitos.any { it.startsWith("100 km · ") })
        assertTrue(hitos.toString(), hitos.any { it.startsWith("250 km · ") })
        assertTrue(banco.hay(ETIQUETA_TEMPORADA_HITO_SIGUIENTE))
        val siguiente = banco.textos(banco.conEtiqueta(ETIQUETA_TEMPORADA_HITO_SIGUIENTE))
        assertTrue(siguiente.toString(), siguiente.any { it.startsWith("Siguiente: ") })
        assertTrue(siguiente.toString(), siguiente.any { Regex("""\d+ de \d+ \S+""").matches(it) })
    }

    @Test
    fun `el carne pone una placa por carretera con el contador de las repetidas`() {
        abrirTemporada()
        val carne = banco.textos(banco.conEtiqueta(ETIQUETA_TEMPORADA_CARNE))
        assertTrue(carne.toString(), "3 carreteras distintas" in carne)
        listOf("Ma-10", "Ma-2141", "Carrer Major").forEach { assertTrue("$it en $carne", it in carne) }
        assertTrue(carne.toString(), "×3" in carne)
    }

    @Test
    fun `la vitrina enseña el tramo, el tiempo, la mejora y la fecha`() {
        abrirTemporada()
        val vitrina = banco.textos(banco.conEtiqueta(ETIQUETA_TEMPORADA_RECORDS))
        assertTrue(vitrina.toString(), "Tramo de la sierra" in vitrina)
        assertTrue(vitrina.toString(), vitrina.any { it.contains("mejora 00:08,5") && it.contains("17 nov") })
    }

    @Test
    fun `sin records la vitrina lo explica`() {
        val sin = datos.copy(temporada = datos.temporada.copy(records = emptyList()))
        abrirTemporada(estado.copy(temporada = sin))
        assertTrue(banco.todosLosTextos().any { it.startsWith("Aquí aparecen los tiempos que mejoran") })
    }

    @Test
    fun `el resumen del mes enseña las cifras y el boton compartir lo entrega`() {
        val compartidos = mutableListOf<ResumenMes>()
        abrirTemporada(acciones = AccionesPerfil(onCompartirMes = { compartidos += it }))
        val mes = banco.textos(banco.conEtiqueta(ETIQUETA_TEMPORADA_MES))
        assertTrue(mes.toString(), "Resumen de noviembre 2026" in mes)
        assertTrue(mes.toString(), "420,0 km · 3 viajes" in mes)
        assertTrue(mes.toString(), mes.any { it.contains("3 carreteras nuevas") && it.contains("1 récord") })
        banco.pulsar(banco.conEtiqueta(ETIQUETA_TEMPORADA_COMPARTIR))
        assertEquals(listOf(datos.temporada.resumenMes), compartidos)
    }

    @Test
    fun `solo las carreteras con referencia salen como placas del mes`() {
        abrirTemporada()
        val mes = banco.textos(banco.conEtiqueta(ETIQUETA_TEMPORADA_MES))
        assertTrue("Ma-10" in mes)
        assertFalse(mes.toString(), "Carrer Major" in mes)
    }

    @Test
    fun `un mes sin viajes no se puede compartir`() {
        val vacio = datos.temporada.resumenMes.copy(viajes = 0, km = 0.0, carreterasNuevas = emptyList())
        val sinSalir = datos.copy(temporada = datos.temporada.copy(resumenMes = vacio))
        abrirTemporada(estado.copy(temporada = sinSalir))
        assertFalse(banco.hay(ETIQUETA_TEMPORADA_COMPARTIR))
        assertTrue(banco.todosLosTextos().any { it.startsWith("Este mes todavía no has salido") })
    }

    @Test
    fun `si falla la imagen lo cuenta bajo el boton`() {
        abrirTemporada(falloCompartir = true)
        assertTrue(banco.hay(ETIQUETA_TEMPORADA_COMPARTIR_FALLO))
        assertTrue("No se ha podido preparar la imagen. Inténtalo de nuevo." in banco.todosLosTextos())
    }

    @Test
    fun `mientras se calcula la temporada se ve el indicador`() {
        abrirTemporada(estado.copy(temporada = null))
        assertTrue(banco.hay(ETIQUETA_TEMPORADA_CARGANDO))
        assertFalse(banco.hay(ETIQUETA_TEMPORADA_FICHA))
    }

    @Test
    fun `un fallo de la temporada se cuenta sin cerrar la pantalla`() {
        abrirTemporada(estado.copy(temporada = null, temporadaError = true))
        assertTrue(banco.hay(ETIQUETA_TEMPORADA_ERROR))
        assertFalse(banco.hay(ETIQUETA_TEMPORADA_CARGANDO))
    }

    @Test
    fun `ningun control de la temporada queda sin nombre para TalkBack`() {
        abrirTemporada()
        assertTrue(banco.pulsablesSinNombre().toString(), banco.pulsablesSinNombre().isEmpty())
    }
}
