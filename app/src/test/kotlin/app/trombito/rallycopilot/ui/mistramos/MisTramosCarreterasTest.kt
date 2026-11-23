package app.trombito.rallycopilot.ui.mistramos

import app.trombito.rallycopilot.domain.tramos.DatosVia
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
            mejorMs = 700_000L,
            mediaMs = 720_000L,
            longitudM = 12_300.0,
            via = via("MA15", 20.1, 32.4, destino = "Manacor"),
            carretera = "Ma-15",
        )
    private val manacorCorto =
        resumenFalso(
            "v0",
            "Ma-15 · km 5,0→8,2 · hacia Manacor",
            pasadas = 1,
            mejorMs = 240_000L,
            mediaMs = 240_000L,
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

    private fun estado(vararg tramos: ResumenTramo) = MisTramosUiState(cargando = false, tramos = tramos.toList())

    @Test
    fun `Mis tramos junta viajes y tramos de carretera sin pestanas y con el nombre completo`() {
        banco.poner { RallyCopilotTheme { MisTramosScreen(estado(viaje, manacor, palma), AccionesMisTramos()) } }
        // Ningún tramo queda sin acceso: salen los tres, en el orden que trae la lista.
        assertEquals(
            listOf("j1", "v1", "v2"),
            banco.etiquetas(ETIQUETA_FILA_PREFIJO).map { it.removePrefix(ETIQUETA_FILA_PREFIJO) },
        )
        assertTrue(banco.etiquetas("tramos-pestana-").isEmpty())
        assertTrue(banco.etiquetas(ETIQUETA_CABECERA_PREFIJO).isEmpty())
        val fila = banco.textos(banco.conEtiqueta("$ETIQUETA_FILA_PREFIJO${manacor.id}"))
        assertTrue("Ma-15 · km 20,1→32,4 · hacia Manacor" in fila)
        // 12,3 km en 11:40,0 son 63,3 km/h (la mejor pasada).
        assertTrue("12,3 km · 63 km/h" in fila)
        assertTrue("11:40,0" in fila)
    }

    @Test
    fun `bajo la cabecera de su carretera la fila se titula por sus kilometros`() {
        banco.poner {
            RallyCopilotTheme {
                ListaCarreteras(
                    agruparCarreteras(listOf(manacor, manacorCorto)),
                    emptyMap(),
                    AccionesMisTramos(),
                    false,
                )
            }
        }
        assertTrue("Ma-15 · hacia Manacor" in banco.todosLosTextos())
        val fila = banco.textos(banco.conEtiqueta("$ETIQUETA_FILA_PREFIJO${manacor.id}"))
        assertTrue("km 20,1→32,4" in fila)
        assertTrue("12,3 km · 63 km/h" in fila)
        // Dentro del grupo, por kilómetro de salida: el km 5 antes que el km 20.
        assertEquals(
            listOf("v0", "v1"),
            banco.etiquetas(ETIQUETA_FILA_PREFIJO).map { it.removePrefix(ETIQUETA_FILA_PREFIJO) },
        )
    }

    @Test
    fun `las carreteras se agrupan por carretera y sentido con su cabecera`() {
        banco.poner {
            RallyCopilotTheme {
                ListaCarreteras(
                    agruparCarreteras(listOf(palma, manacor, ma2, manacorCorto)),
                    emptyMap(),
                    AccionesMisTramos(),
                    false,
                )
            }
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
    fun `un tramo de carretera con nombre propio lo muestra tambien bajo su cabecera`() {
        val propio = manacor.copy(nombrePropio = "Mi subida", nombre = "Mi subida")
        banco.poner {
            RallyCopilotTheme {
                ListaCarreteras(agruparCarreteras(listOf(propio)), emptyMap(), AccionesMisTramos(), false)
            }
        }
        assertTrue("Mi subida" in banco.textos(banco.conEtiqueta("$ETIQUETA_FILA_PREFIJO${propio.id}")))
    }

    @Test
    fun `sin destino la cabecera es solo la carretera, sin jerga de kilometros, y un tramo sin via va a otras`() {
        val sinDestino = palma.copy(via = palma.via!!.copy(destino = null))
        val suelto = ma2.copy(id = "v9", via = null, carretera = null)
        val grupos = agruparCarreteras(listOf(suelto, sinDestino))
        assertEquals(listOf("MA15|DECRECIENTE", "|DESCONOCIDO"), grupos.map { it.clave })
        assertNull(grupos.first().destino)
        banco.poner { RallyCopilotTheme { ListaCarreteras(grupos, emptyMap(), AccionesMisTramos(), false) } }
        val textos = banco.todosLosTextos()
        assertTrue("Ma-15" in textos)
        assertFalse(textos.any { "decrecientes" in it })
        assertTrue("Otras carreteras" in textos)
    }
}
