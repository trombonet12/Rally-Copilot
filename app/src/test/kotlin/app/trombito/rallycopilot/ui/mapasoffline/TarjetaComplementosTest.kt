package app.trombito.rallycopilot.ui.mapasoffline

import app.trombito.rallycopilot.domain.datos.DatosInstalados
import app.trombito.rallycopilot.domain.datos.EstadoComplemento
import app.trombito.rallycopilot.domain.datos.EstadoDatosMapa
import app.trombito.rallycopilot.domain.datos.ProgresoDescarga
import app.trombito.rallycopilot.motor.datos.Complemento
import app.trombito.rallycopilot.ui.comun.BancoCompose
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

/** Mapas offline: el relieve y la voz Sharvard, que no van en el APK, se descargan y se borran desde aquí. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class TarjetaComplementosTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()
    private val base = DatosInstalados("2026.11.1", 150_000_000, conSatelite = false)

    private fun poner(
        datos: EstadoDatosMapa,
        acciones: AccionesMapasOffline = AccionesMapasOffline(),
    ) = banco.poner { RallyCopilotTheme { MapasOfflineScreen(MapasOfflineUiState(false, datos), acciones) } }

    private fun textos() = banco.todosLosTextos()

    @Test
    fun `sin relieve ni voz cada uno ofrece descargar con su tamano`() {
        val pedidos = mutableListOf<Complemento>()
        poner(
            EstadoDatosMapa(instalados = base),
            AccionesMapasOffline(onDescargarComplemento = { pedidos += it }),
        )

        assertTrue(banco.hay(ETIQUETA_COMPLEMENTOS))
        assertTrue(textos().any { it.contains("No instalado") })
        banco.pulsar(banco.conEtiqueta(ETIQUETA_COMPLEMENTO_DESCARGAR_PREFIJO + "relieve"))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_COMPLEMENTO_DESCARGAR_PREFIJO + "voz_sharvard"))

        assertEquals(listOf(Complemento.RELIEVE, Complemento.VOZ_SHARVARD), pedidos)
    }

    @Test
    fun `instalado dice cuanto ocupa y ofrece borrar con confirmacion`() {
        val borrados = mutableListOf<Complemento>()
        val estado =
            EstadoDatosMapa(
                instalados = base.copy(conRelieve = true),
                complementos = mapOf(Complemento.RELIEVE to EstadoComplemento(instalado = true, bytes = 69_331_524)),
            )
        poner(estado, AccionesMapasOffline(onBorrarComplemento = { borrados += it }))

        assertFalse(banco.hay(ETIQUETA_COMPLEMENTO_DESCARGAR_PREFIJO + "relieve"))
        assertTrue(textos().any { it.contains("Instalado") })
        banco.pulsar(banco.conEtiqueta(ETIQUETA_COMPLEMENTO_BORRAR_PREFIJO + "relieve"))
        assertTrue("antes de borrar pide confirmar", borrados.isEmpty())
        banco.pulsar(banco.conEtiqueta(ETIQUETA_COMPLEMENTO_BORRAR_CONFIRMAR))

        assertEquals(listOf(Complemento.RELIEVE), borrados)
    }

    @Test
    fun `descargando enseña el porcentaje y esperando red lo dice, sin boton en ninguno`() {
        val bajando = ProgresoDescarga(bytesHechos = 25, bytesTotales = 100, esperandoRed = false)
        val esperando = ProgresoDescarga(0, 0, esperandoRed = true)
        poner(
            EstadoDatosMapa(
                instalados = base,
                complementos =
                    mapOf(
                        Complemento.RELIEVE to EstadoComplemento(descarga = bajando),
                        Complemento.VOZ_SHARVARD to EstadoComplemento(descarga = esperando),
                    ),
            ),
        )

        assertTrue(textos().any { it.contains("25") })
        assertTrue(textos().any { it.contains("Esperando conexión") })
        assertFalse(banco.hay(ETIQUETA_COMPLEMENTO_DESCARGAR_PREFIJO + "relieve"))
        assertFalse(banco.hay(ETIQUETA_COMPLEMENTO_DESCARGAR_PREFIJO + "voz_sharvard"))
    }

    @Test
    fun `esperando WiFi a proposito lo dice con claridad`() {
        val conWifi = ProgresoDescarga(0, 0, esperandoRed = true, soloWifi = true)
        poner(
            EstadoDatosMapa(
                instalados = base,
                complementos = mapOf(Complemento.RELIEVE to EstadoComplemento(descarga = conWifi)),
            ),
        )

        assertTrue(textos().any { it.contains("Se descargará con WiFi") })
        assertFalse(textos().any { it.contains("Esperando conexión") })
    }

    @Test
    fun `esperando WiFi a proposito ofrece descargar ahora con datos y el tamano real`() {
        val pedidos = mutableListOf<Complemento>()
        val conWifi = ProgresoDescarga(0, 0, esperandoRed = true, soloWifi = true)
        val datos =
            EstadoDatosMapa(
                instalados = base,
                complementos = mapOf(Complemento.RELIEVE to EstadoComplemento(descarga = conWifi)),
            )
        banco.poner {
            RallyCopilotTheme {
                MapasOfflineScreen(
                    MapasOfflineUiState(false, datos, bytesComplementos = mapOf(Complemento.RELIEVE to 40_000_000L)),
                    AccionesMapasOffline(onDescargarComplemento = { pedidos += it }),
                )
            }
        }

        val boton = banco.textos(banco.conEtiqueta(ETIQUETA_COMPLEMENTO_AHORA_PREFIJO + "relieve")).single()
        assertTrue(boton, boton.startsWith("Descargar ahora (") && boton.endsWith(" con datos)") && "38" in boton)
        banco.pulsar(banco.conEtiqueta(ETIQUETA_COMPLEMENTO_AHORA_PREFIJO + "relieve"))
        assertEquals(listOf(Complemento.RELIEVE), pedidos)
        assertFalse("la voz no espera WiFi", banco.hay(ETIQUETA_COMPLEMENTO_AHORA_PREFIJO + "voz_sharvard"))
    }

    @Test
    fun `sin tamano leido el boton de descargar ahora usa el orientativo`() {
        val conWifi = ProgresoDescarga(0, 0, esperandoRed = true, soloWifi = true)
        poner(
            EstadoDatosMapa(
                instalados = base,
                complementos = mapOf(Complemento.RELIEVE to EstadoComplemento(descarga = conWifi)),
            ),
        )

        val boton = banco.textos(banco.conEtiqueta(ETIQUETA_COMPLEMENTO_AHORA_PREFIJO + "relieve")).single()
        assertTrue(boton, "66" in boton)
    }

    @Test
    fun `esperando conexion sin WiFi a proposito y descargando no ofrecen descargar ahora`() {
        val esperando = ProgresoDescarga(0, 0, esperandoRed = true)
        val bajando = ProgresoDescarga(10, 100, esperandoRed = false, soloWifi = true)
        poner(
            EstadoDatosMapa(
                instalados = base,
                complementos =
                    mapOf(
                        Complemento.RELIEVE to EstadoComplemento(descarga = esperando),
                        Complemento.VOZ_SHARVARD to EstadoComplemento(descarga = bajando),
                    ),
            ),
        )

        assertFalse(banco.hay(ETIQUETA_COMPLEMENTO_AHORA_PREFIJO + "relieve"))
        assertFalse(banco.hay(ETIQUETA_COMPLEMENTO_AHORA_PREFIJO + "voz_sharvard"))
    }

    @Test
    fun `sin los mapas base instalados no se ofrecen los complementos`() {
        poner(EstadoDatosMapa())

        assertFalse(banco.hay(ETIQUETA_COMPLEMENTOS))
    }
}
