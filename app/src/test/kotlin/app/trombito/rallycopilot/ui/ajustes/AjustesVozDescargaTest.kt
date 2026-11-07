package app.trombito.rallycopilot.ui.ajustes

import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.datos.EstadoComplemento
import app.trombito.rallycopilot.domain.datos.EstadoDatosMapa
import app.trombito.rallycopilot.domain.datos.MotivoError
import app.trombito.rallycopilot.domain.datos.ProgresoDescarga
import app.trombito.rallycopilot.domain.voz.VozCopiloto
import app.trombito.rallycopilot.motor.datos.Complemento
import app.trombito.rallycopilot.ui.comun.BancoCompose
import app.trombito.rallycopilot.ui.comun.FalsoLocutor
import app.trombito.rallycopilot.ui.comun.FalsoRepositorioAjustesConduccion
import app.trombito.rallycopilot.ui.comun.FalsoRepositorioDatosMapa
import app.trombito.rallycopilot.ui.comun.LimpiezaCompose
import app.trombito.rallycopilot.ui.copiloto.ETIQUETA_VOZ_PROBAR_PREFIJO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Sharvard no va en el APK: su estado en Ajustes > Voz y cómo se pide la descarga. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AjustesVozDescargaTest {
    @get:Rule val limpieza = LimpiezaCompose()

    private val banco = BancoCompose()
    private val repositorio = FalsoRepositorioAjustesConduccion()
    private val datos = FalsoRepositorioDatosMapa()

    @Before
    fun preparar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun crear() = AjustesViewModel(repositorio, FalsoLocutor(), datos)

    private fun poner(
        estado: EstadoVozDescarga,
        acciones: AccionesAjustes = AccionesAjustes(),
    ) = banco.ponerAjustes(
        AjustesConduccion(voz = VozCopiloto.DAVEFX),
        SeccionAjustes.VOZ,
        acciones,
        vozSharvard = estado,
    )

    @Test
    fun `sharvard sin descargar ensena su tamano y el boton descargar y dave no`() {
        poner(EstadoVozDescarga(instalada = false, bytes = 21_993_140))

        assertTrue(banco.hay(ETIQUETA_VOZ_DESCARGAR_PREFIJO + "sharvard"))
        assertFalse(banco.hay(ETIQUETA_VOZ_DESCARGAR_PREFIJO + "davefx"))
        assertTrue(banco.textoDe(ETIQUETA_VOZ_ESTADO_PREFIJO + "sharvard").contains("21"))
    }

    @Test
    fun `pulsar descargar pide la descarga de sharvard`() {
        val pedidas = mutableListOf<VozCopiloto>()
        poner(EstadoVozDescarga(instalada = false), AccionesAjustes(onDescargarVoz = { pedidas += it }))

        banco.pulsar(banco.conEtiqueta(ETIQUETA_VOZ_DESCARGAR_PREFIJO + "sharvard"))

        assertEquals(listOf(VozCopiloto.SHARVARD), pedidas)
    }

    @Test
    fun `la vista previa de sharvard sin descargar pide descargarla primero y no suena`() {
        val probadas = mutableListOf<VozCopiloto>()
        poner(EstadoVozDescarga(instalada = false), AccionesAjustes(onProbarVoz = { probadas += it }))
        assertFalse(banco.hay(ETIQUETA_VOZ_PROBAR_PRIMERO))

        banco.pulsar(banco.conEtiqueta(ETIQUETA_VOZ_PROBAR_PREFIJO + "sharvard"))

        assertTrue(banco.hay(ETIQUETA_VOZ_PROBAR_PRIMERO))
        assertTrue(probadas.isEmpty())
    }

    @Test
    fun `la vista previa de dave y de sharvard instalada suenan`() {
        val probadas = mutableListOf<VozCopiloto>()
        poner(EstadoVozDescarga(instalada = true), AccionesAjustes(onProbarVoz = { probadas += it }))

        banco.pulsar(banco.conEtiqueta(ETIQUETA_VOZ_PROBAR_PREFIJO + "davefx"))
        banco.pulsar(banco.conEtiqueta(ETIQUETA_VOZ_PROBAR_PREFIJO + "sharvard"))

        assertEquals(listOf(VozCopiloto.DAVEFX, VozCopiloto.SHARVARD), probadas)
        assertFalse(banco.hay(ETIQUETA_VOZ_DESCARGAR_PREFIJO + "sharvard"))
        assertTrue(banco.hay(ETIQUETA_VOZ_ESTADO_PREFIJO + "sharvard"))
    }

    @Test
    fun `mientras se descarga se ve el porcentaje y no el boton`() {
        val progreso = ProgresoDescarga(bytesHechos = 5_000, bytesTotales = 20_000, esperandoRed = false)
        poner(EstadoVozDescarga(instalada = false, progreso = progreso))

        assertFalse(banco.hay(ETIQUETA_VOZ_DESCARGAR_PREFIJO + "sharvard"))
        assertTrue(banco.textoDe(ETIQUETA_VOZ_ESTADO_PREFIJO + "sharvard").contains("25"))
    }

    @Test
    fun `esperando red se dice y tampoco hay boton`() {
        val progreso = ProgresoDescarga(0, 0, esperandoRed = true)
        poner(EstadoVozDescarga(instalada = false, progreso = progreso))

        assertFalse(banco.hay(ETIQUETA_VOZ_DESCARGAR_PREFIJO + "sharvard"))
        assertTrue(banco.textoDe(ETIQUETA_VOZ_ESTADO_PREFIJO + "sharvard").contains("conexión"))
    }

    @Test
    fun `el estado sale de los datos de mapa`() {
        val instalada = EstadoDatosMapa(complementos = mapOf(Complemento.VOZ_SHARVARD to EstadoComplemento(true, 5)))
        assertTrue(EstadoVozDescarga.de(instalada).instalada)

        val sin = EstadoDatosMapa(error = MotivoError.SIN_RED)
        assertFalse(EstadoVozDescarga.de(sin).instalada)
        assertTrue(EstadoVozDescarga.de(sin).error)

        val bajando = ProgresoDescarga(1, 4, esperandoRed = false)
        val descargando =
            EstadoDatosMapa(
                error = MotivoError.SIN_RED,
                complementos = mapOf(Complemento.VOZ_SHARVARD to EstadoComplemento(descarga = bajando)),
            )
        assertEquals(25, EstadoVozDescarga.de(descargando).porcentaje)
        assertFalse("con una descarga en marcha no hay error", EstadoVozDescarga.de(descargando).error)
        assertNull(EstadoVozDescarga.de(EstadoDatosMapa()).porcentaje)
    }

    @Test
    fun `elegir sharvard sin tenerla la descarga y elegir dave no`() {
        val vm = crear()

        vm.onVozCopiloto(VozCopiloto.DAVEFX)
        assertTrue(datos.complementosDescargados.isEmpty())

        vm.onVozCopiloto(VozCopiloto.SHARVARD)

        assertEquals(listOf(Complemento.VOZ_SHARVARD), datos.complementosDescargados)
        assertEquals(VozCopiloto.SHARVARD, repositorio.mutable.value.voz)
    }

    @Test
    fun `elegir sharvard ya instalada o ya descargandose no pide otra descarga`() {
        datos.estadoMutable.value =
            EstadoDatosMapa(complementos = mapOf(Complemento.VOZ_SHARVARD to EstadoComplemento(instalado = true)))
        crear().onVozCopiloto(VozCopiloto.SHARVARD)
        datos.estadoMutable.value =
            EstadoDatosMapa(
                complementos =
                    mapOf(Complemento.VOZ_SHARVARD to EstadoComplemento(descarga = ProgresoDescarga(0, 0, true))),
            )
        crear().onDescargarVoz(VozCopiloto.SHARVARD)

        assertTrue(datos.complementosDescargados.isEmpty())
    }

    @Test
    fun `el boton descargar de dave no hace nada`() {
        crear().onDescargarVoz(VozCopiloto.DAVEFX)

        assertTrue(datos.complementosDescargados.isEmpty())
    }
}
