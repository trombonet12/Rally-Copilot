package app.trombito.rallycopilot.ui.mistramos

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import app.trombito.rallycopilot.domain.tramos.DatosVia
import app.trombito.rallycopilot.domain.tramos.FiltroTramos
import app.trombito.rallycopilot.domain.tramos.ResumenTramo
import app.trombito.rallycopilot.motor.vias.SentidoVia
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import app.trombito.rallycopilot.ui.comun.resumenFalso
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
class MisTramosCarreterasTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()

    private fun via(
        ref: String,
        desde: Double?,
        hasta: Double?,
        sentido: SentidoVia = SentidoVia.CRECIENTE,
        destino: String? = null,
    ) = DatosVia(ref, desde, hasta, sentido, destino, null)

    private val manacor =
        resumenFalso(
            "v1",
            "Ma-15 · km 20,1→32,4 · hacia Manacor",
            pasadas = 3,
            mejorMs = 252_300L,
            mediaMs = 260_000L,
            longitudM = 12_300.0,
            via = via("MA15", 20.1, 32.4, destino = "Manacor"),
            carretera = "Ma-15",
        )
    private val manacorCorto =
        resumenFalso(
            "v0",
            "Ma-15 · km 5,0→8,2 · hacia Manacor",
            pasadas = 1,
            mejorMs = 120_000L,
            mediaMs = 120_000L,
            longitudM = 3_200.0,
            via = via("MA15", 5.0, 8.2, destino = "Manacor"),
            carretera = "Ma-15",
        )
    private val palma =
        resumenFalso(
            "v2",
            "Ma-15 · km 32,4→20,1 · hacia Palma",
            pasadas = 2,
            mejorMs = 250_000L,
            mediaMs = 255_000L,
            longitudM = 12_300.0,
            via = via("MA15", 32.4, 20.1, SentidoVia.DECRECIENTE, "Palma"),
            carretera = "Ma-15",
        )
    private val ma2 =
        resumenFalso(
            "v3",
            "Ma-2 · 4,0 km",
            pasadas = 1,
            mejorMs = 90_000L,
            longitudM = 4_000.0,
            via = via("MA2", null, null),
            carretera = "Ma-2",
        )
    private val viaje = resumenFalso("j1", "Palma → Manacor", pasadas = 1)

    private fun estado(
        vararg tramos: ResumenTramo,
        pestana: PestanaTramos = PestanaTramos.CARRETERAS,
    ) = MisTramosUiState(cargando = false, pestana = pestana, tramos = tramos.toList())

    private fun seleccionada(etiqueta: String) =
        banco.conEtiqueta(etiqueta).config.getOrNull(SemanticsProperties.Selected)

    @Test
    fun `las dos pestanas estan con texto y la actual va seleccionada`() {
        banco.poner { RallyCopilotTheme { MisTramosScreen(estado(manacor), AccionesMisTramos()) } }
        assertEquals(
            listOf("tramos-pestana-viajes", "tramos-pestana-carreteras"),
            banco.etiquetas(ETIQUETA_PESTANA_PREFIJO),
        )
        val textos = banco.todosLosTextos()
        assertTrue("Viajes" in textos && "Carreteras" in textos)
        assertEquals(true, seleccionada("tramos-pestana-carreteras"))
        assertEquals(false, seleccionada("tramos-pestana-viajes"))
    }

    @Test
    fun `tocar una pestana la notifica`() {
        val elegidas = mutableListOf<PestanaTramos>()
        banco.poner {
            RallyCopilotTheme {
                MisTramosScreen(
                    estado(viaje, pestana = PestanaTramos.VIAJES),
                    AccionesMisTramos(onPestana = { elegidas += it }),
                )
            }
        }
        banco.pulsar(banco.conEtiqueta("tramos-pestana-carreteras"))
        assertEquals(listOf(PestanaTramos.CARRETERAS), elegidas)
    }

    @Test
    fun `carreteras se agrupan por carretera y sentido con su cabecera`() {
        banco.poner {
            RallyCopilotTheme { MisTramosScreen(estado(palma, manacor, ma2, manacorCorto), AccionesMisTramos()) }
        }
        val textos = banco.todosLosTextos()
        assertTrue("Ma-15 · hacia Manacor" in textos)
        assertTrue("Ma-15 · hacia Palma" in textos)
        // Sin destino guardado no se enseña jerga de kilómetros: solo la carretera.
        assertTrue("Ma-2" in textos)
        assertFalse(textos.any { "km crecientes" in it || "km decrecientes" in it })
        // Ma-2 antes que Ma-15 (orden natural) y, en la Ma-15, el sentido creciente antes que el decreciente.
        assertEquals(
            listOf("MA2|CRECIENTE", "MA15|CRECIENTE", "MA15|DECRECIENTE"),
            banco.etiquetas(ETIQUETA_CABECERA_PREFIJO).map { it.removePrefix(ETIQUETA_CABECERA_PREFIJO) },
        )
    }

    @Test
    fun `cada fila de carretera muestra km, pasadas, mejor tiempo y media`() {
        banco.poner { RallyCopilotTheme { MisTramosScreen(estado(manacor, manacorCorto), AccionesMisTramos()) } }
        val fila = banco.textos(banco.conEtiqueta("$ETIQUETA_FILA_PREFIJO${manacor.id}"))
        assertTrue("km 20,1→32,4" in fila)
        assertTrue("12,3 km · 3 pasadas" in fila)
        assertTrue("04:12,3" in fila)
        assertTrue("Media 04:20,0" in fila)
        // Con una sola pasada la media sería el mismo tiempo: no se repite.
        assertFalse(banco.hay("$ETIQUETA_MEDIA_PREFIJO${manacorCorto.id}"))
        // Dentro del grupo, por kilómetro de salida: el km 5 antes que el km 20.
        val filas = banco.etiquetas(ETIQUETA_FILA_PREFIJO)
        assertEquals(listOf("v0", "v1"), filas.map { it.removePrefix(ETIQUETA_FILA_PREFIJO) })
    }

    @Test
    fun `un tramo de carretera con nombre propio lo muestra y deja los km debajo`() {
        val propio = manacor.copy(nombrePropio = "Mi subida", nombre = "Mi subida")
        banco.poner { RallyCopilotTheme { MisTramosScreen(estado(propio), AccionesMisTramos()) } }
        val fila = banco.textos(banco.conEtiqueta("$ETIQUETA_FILA_PREFIJO${propio.id}"))
        assertTrue("Mi subida" in fila)
        assertTrue("km 20,1→32,4" in fila)
    }

    @Test
    fun `sin carreteras explica que se crean solas al conducir`() {
        banco.poner { RallyCopilotTheme { MisTramosScreen(estado(), AccionesMisTramos()) } }
        val textos = banco.todosLosTextos()
        assertTrue("Aún no hay carreteras" in textos)
        assertTrue(textos.any { "Se crean solas al conducir" in it })
        assertTrue(banco.hay(ETIQUETA_TRAMOS_VACIO))
        assertFalse(banco.hay(ETIQUETA_TRAMOS_LISTA))
    }

    @Test
    fun `los filtros siguen en la pestana de carreteras y su vacio es el del filtro`() {
        banco.poner {
            RallyCopilotTheme {
                MisTramosScreen(estado().copy(filtro = FiltroTramos.FAVORITOS), AccionesMisTramos())
            }
        }
        assertEquals(3, banco.etiquetas(ETIQUETA_FILTRO_PREFIJO).size)
        assertTrue("Sin favoritos" in banco.todosLosTextos())
    }

    @Test
    fun `sin destino la cabecera es solo la carretera, sin jerga de kilometros, y un tramo sin via va a otras`() {
        val sinDestino = palma.copy(via = palma.via!!.copy(destino = null))
        val suelto = ma2.copy(id = "v9", via = null, carretera = null)
        val grupos = agruparCarreteras(listOf(suelto, sinDestino))
        assertEquals(listOf("MA15|DECRECIENTE", "|DESCONOCIDO"), grupos.map { it.clave })
        assertNull(grupos.first().destino)
        banco.poner { RallyCopilotTheme { MisTramosScreen(estado(sinDestino, suelto), AccionesMisTramos()) } }
        val textos = banco.todosLosTextos()
        assertTrue("Ma-15" in textos)
        assertFalse(textos.any { "decrecientes" in it })
        assertTrue("Otras carreteras" in textos)
    }
}
