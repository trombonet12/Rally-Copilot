package app.trombito.rallycopilot.ui.mapa

import app.trombito.rallycopilot.domain.carreteras.CalculoCarreteras
import app.trombito.rallycopilot.domain.carreteras.MisCarreteras
import app.trombito.rallycopilot.motor.analisis.CurvasPorGrado
import app.trombito.rallycopilot.motor.analisis.Marcha
import app.trombito.rallycopilot.motor.analisis.ResumenCarreteras
import app.trombito.rallycopilot.motor.analisis.VelocidadVia
import app.trombito.rallycopilot.motor.analisis.ViaConducida
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.vias.SentidoUnico
import app.trombito.rallycopilot.motor.vias.Via
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.concurrent.Executors

private class CarreterasFalsas(
    inicial: MisCarreteras,
) : CalculoCarreteras {
    val estado = MutableStateFlow(inicial)
    var observaciones = 0

    override fun observar(): Flow<MisCarreteras> =
        flow {
            observaciones++
            estado.collect { emit(it) }
        }
}

/** Los ajustes de la capa de calor se guardan y las vías se juntan con su velocidad media. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CalorViewModelTest {
    private val via =
        Via(
            7,
            "secondary",
            null,
            null,
            null,
            SentidoUnico.NO,
            " Ma-10 ",
            "",
            listOf(PuntoGeo(39.7, 2.9), PuntoGeo(39.71, 2.91)),
        )
    private val sinTiempos = MisCarreteras(resumen(), hayVias = true)
    private val conMedia =
        MisCarreteras(
            resumen(),
            hayVias = true,
            velocidades = listOf(VelocidadVia(via, 3, Marcha(10_000.0, 600_000L), Marcha())),
        )

    private fun resumen() =
        ResumenCarreteras(
            recorridos = 3,
            metrosTotales = 30_000.0,
            curvas = CurvasPorGrado.VACIO,
            vias = listOf(ViaConducida(via, pasadas = 3, metros = 12_340.0, curvas = 5)),
            carreteras = emptyList(),
        )

    @Before
    fun preparar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun almacen() = AlmacenCalor(RuntimeEnvironment.getApplication())

    private fun crear(calculo: CarreterasFalsas) = CalorViewModel(calculo, almacen())

    @Test
    fun `por defecto la capa esta encendida y en modo uso`() {
        val estado = crear(CarreterasFalsas(sinTiempos)).estado.value
        assertEquals(CalorUi(activo = true, modo = ModoCalor.USO, ficha = null), estado)
    }

    @Test
    fun `el interruptor y el modo persisten entre arranques`() {
        val modelo = crear(CarreterasFalsas(sinTiempos))
        modelo.onActivo(false)
        modelo.onModo(ModoCalor.VELOCIDAD)
        val otro = crear(CarreterasFalsas(sinTiempos)).estado.value
        assertFalse(otro.activo)
        assertEquals(ModoCalor.VELOCIDAD, otro.modo)
    }

    @Test
    fun `un modo guardado que ya no existe vuelve a uso`() {
        val aplicacion = RuntimeEnvironment.getApplication()
        val preferencias = aplicacion.getSharedPreferences("calor", 0)
        preferencias.edit().putString("modo", "NUBES").commit()
        assertEquals(ModoCalor.USO, almacen().modo())
    }

    @Test
    fun `los tramos juntan el uso de la via con su velocidad media`() {
        val modelo = crear(CarreterasFalsas(conMedia))
        val recoleccion = backgroundCollect(modelo)
        val tramo = modelo.tramos.value.single()
        assertEquals(7L, tramo.viaId)
        assertEquals("Ma-10", tramo.ref)
        assertNull("un nombre en blanco no es nombre", tramo.nombre)
        assertEquals(3, tramo.pasadas)
        assertEquals(12.34, tramo.km, 1e-9)
        assertEquals(60.0, tramo.velMediaKmh!!, 1e-6)
        recoleccion()
    }

    @Test
    fun `sin tiempos la via sale sin velocidad`() {
        val modelo = crear(CarreterasFalsas(sinTiempos))
        val recoleccion = backgroundCollect(modelo)
        val tramo = modelo.tramos.value.single()
        assertNull(tramo.velMediaKmh)
        recoleccion()
    }

    @Test
    fun `con la capa apagada no se calcula nada`() {
        val calculo = CarreterasFalsas(conMedia)
        val modelo = crear(calculo)
        modelo.onActivo(false)
        val recoleccion = backgroundCollect(modelo)
        assertTrue(modelo.tramos.value.isEmpty())
        assertEquals(0, calculo.observaciones)
        modelo.onActivo(true)
        assertEquals(1, modelo.tramos.value.size)
        assertEquals(1, calculo.observaciones)
        recoleccion()
    }

    @Test
    fun `la ficha se abre al tocar una carretera y se cierra al tocar otra cosa o apagar la capa`() {
        val modelo = crear(CarreterasFalsas(conMedia))
        val ficha = FichaCalor("Ma-10", null, 3, 12.3, 60.0)
        modelo.onFicha(ficha)
        assertEquals(ficha, modelo.estado.value.ficha)
        modelo.onFicha(null)
        assertNull(modelo.estado.value.ficha)
        modelo.onFicha(ficha)
        modelo.onActivo(false)
        assertNull(modelo.estado.value.ficha)
        modelo.onFicha(ficha)
        modelo.onCerrarFicha()
        assertNull(modelo.estado.value.ficha)
    }

    @Test
    fun `el geojson se construye fuera del hilo principal y llega serializado`() {
        val hilos = mutableListOf<String>()
        val ejecutor = Executors.newSingleThreadExecutor { Thread(it, "calor-json") }
        try {
            val modelo =
                CalorViewModel(CarreterasFalsas(conMedia), almacen(), ejecutor.asCoroutineDispatcher()) { t, m ->
                    hilos += Thread.currentThread().name
                    calorGeoJsonTexto(t, m)
                }
            val json = runBlocking { withTimeout(5_000) { modelo.geoJson.first { it != CALOR_GEOJSON_VACIO } } }
            assertTrue(json.contains("\"FeatureCollection\""))
            assertTrue(json.contains("Ma-10"))
            assertTrue(hilos.isNotEmpty())
            assertTrue("se construyó en $hilos", hilos.all { it.startsWith("calor-json") })
        } finally {
            ejecutor.shutdownNow()
        }
    }

    @Test
    fun `cambiar de modo recalcula el geojson y sin cambios no se repite`() {
        var llamadas = 0
        val modelo =
            CalorViewModel(CarreterasFalsas(conMedia), almacen(), Dispatchers.Main) { t, m ->
                llamadas++
                calorGeoJsonTexto(t, m)
            }
        val recoleccion = CoroutineScope(Dispatchers.Main).launch { modelo.geoJson.collect { } }
        val enUso = modelo.geoJson.value
        val antes = llamadas
        modelo.onModo(ModoCalor.VELOCIDAD)
        assertEquals(antes + 1, llamadas)
        assertTrue(modelo.geoJson.value != enUso)
        modelo.onModo(ModoCalor.VELOCIDAD)
        assertEquals(antes + 1, llamadas)
        recoleccion.cancel()
    }

    /** Suscribe a los tramos (que solo se calculan con alguien mirando) y devuelve cómo dejar de mirar. */
    private fun backgroundCollect(modelo: CalorViewModel): () -> Unit {
        val trabajo =
            CoroutineScope(Dispatchers.Main).launch { modelo.tramos.collect { } }
        return { trabajo.cancel() }
    }
}
