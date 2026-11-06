package app.trombito.rallycopilot.ui.mistramos

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.SavedStateHandle
import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.datos.EstilosMapa
import app.trombito.rallycopilot.domain.edicion.EditorTramo
import app.trombito.rallycopilot.domain.edicion.TramoEditable
import app.trombito.rallycopilot.motor.edicion.AccionEdicion
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.notas.Direccion
import app.trombito.rallycopilot.motor.notas.ElementoHoja
import app.trombito.rallycopilot.motor.notas.HojaNotas
import app.trombito.rallycopilot.motor.notas.Nota
import app.trombito.rallycopilot.motor.tramos.Tramo
import app.trombito.rallycopilot.ui.comun.FalsoRepositorioAjustesConduccion
import app.trombito.rallycopilot.ui.comun.FalsoRepositorioDatosMapa
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class EditarTramoViewModelTest {
    private val traza = List(21) { PuntoGeo(39.6 + it * 0.00085, 2.9) }
    private val tramo = Tramo("t1", traza, traza.zipWithNext { a, b -> a.distanciaA(b) }.sum())
    private val curva = Nota(300.0, 340.0, Direccion.DERECHA, 3)

    private class EditorFalso(
        val datos: MutableStateFlow<TramoEditable?>,
    ) : EditorTramo {
        val editadas = mutableListOf<Pair<Double, AccionEdicion>>()
        val cortes = mutableListOf<Pair<List<Double>, List<Double>>>()
        var restaurado = false

        override fun observar(tramoId: String): Flow<TramoEditable?> = datos

        override suspend fun editar(
            tramoId: String,
            posicionM: Double,
            accion: AccionEdicion,
        ) {
            editadas += posicionM to accion
        }

        override suspend fun restaurar(tramoId: String) {
            restaurado = true
        }

        override suspend fun guardarCortes(
            tramoId: String,
            sectores: List<Double>,
            microsectores: List<Double>,
        ) {
            cortes += sectores to microsectores
        }
    }

    private val editor =
        EditorFalso(
            MutableStateFlow(TramoEditable(tramo, HojaNotas(listOf(ElementoHoja.DeNota(curva))), emptyList(), 0)),
        )

    private var estilos: EstilosMapa? = EstilosMapa("{\"version\":8}", null)

    private fun viewModel() =
        EditarTramoViewModel(
            SavedStateHandle(mapOf("tramoId" to "t1")),
            editor,
            FalsoRepositorioAjustesConduccion(AjustesConduccion()),
            FalsoRepositorioDatosMapa(estilosDisponibles = estilos),
        )

    /** Empieza a recoger el estado (el ViewModel solo lo calcula con un suscriptor) y devuelve el último valor. */
    private fun <T> StateFlow<T>.collectAhora(): () -> T {
        CoroutineScope(UnconfinedTestDispatcher()).launch { collect {} }
        return { value }
    }

    @Before
    fun preparar() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun limpiar() = Dispatchers.resetMain()

    @Test
    fun `tocar una nota abre su editor y guardar la sustituye en su posicion`() {
        val vm = viewModel()
        val recogida = vm.uiState.collectAhora()
        vm.onTocarNota(curva)
        assertEquals(curva, recogida().editor?.nota)

        val nueva = Nota(0.0, 0.0, Direccion.IZQUIERDA, 1)
        vm.onAplicar(nueva)

        assertEquals(listOf(300.0 to AccionEdicion.Sustituir(nueva)), editor.editadas)
        assertNull(recogida().editor)
    }

    @Test
    fun `tocar un punto vacio anade una nota y quitar la quita`() {
        val vm = viewModel()
        val recogida = vm.uiState.collectAhora()
        vm.onTocarPunto(900.0)
        assertNull(recogida().editor?.nota)
        val nueva = Nota(0.0, 0.0, Direccion.DERECHA, 5)
        vm.onAplicar(nueva)
        vm.onTocarNota(curva)
        vm.onQuitar()

        assertEquals(listOf(900.0 to AccionEdicion.Anadir(nueva), 300.0 to AccionEdicion.Quitar), editor.editadas)
    }

    @Test
    fun `arrastrar un corte lo mueve y se guarda al soltar con los microsectores del tramo`() {
        val vm = viewModel()
        val recogida = vm.uiState.collectAhora()
        val primero = tramo.sectores[0]
        vm.onMoverCorte(0, primero + 100.0)
        assertEquals(primero + 100.0, recogida().sectoresM[0], 1e-6)
        assertEquals(emptyList<Pair<List<Double>, List<Double>>>(), editor.cortes)

        vm.onSoltarCorte()

        assertEquals(
            listOf(listOf(primero + 100.0, tramo.sectores[1], tramo.sectores[2]) to tramo.microsectores),
            editor.cortes,
        )
    }

    @Test
    fun `los microsectores por kilometro van de 0 a 4 y restaurar deja las automaticas`() {
        val vm = viewModel()
        val recogida = vm.uiState.collectAhora()
        vm.onMicrosectores(9)
        assertEquals(4, recogida().microsectoresPorKm)
        vm.onMicrosectores(0)
        assertEquals(0, recogida().microsectoresPorKm)
        assertEquals(emptyList<Double>(), editor.cortes.last().second)
        assertNotNull(editor.cortes.last().first)

        vm.onRestaurar()

        assertEquals(true, editor.restaurado)
    }

    @Test
    fun `mas sube los microsectores por kilometro y menos los baja, sin salirse de 0 a 4`() {
        assertEquals(2, microsectoresTrasPulsar(1, masMicrosectores = true))
        assertEquals(0, microsectoresTrasPulsar(1, masMicrosectores = false))
        assertEquals(4, microsectoresTrasPulsar(4, masMicrosectores = true))
        assertEquals(0, microsectoresTrasPulsar(0, masMicrosectores = false))
        assertEquals(1000, separacionMicrosectoresM(1))
        assertEquals(250, separacionMicrosectoresM(4))
        assertNull(separacionMicrosectoresM(0))
    }

    @Test
    fun `subir los microsectores pone mas puntos en el trazo en vivo y bajarlos, menos`() {
        val vm = viewModel()
        val recogida = vm.uiState.collectAhora()
        vm.onMicrosectores(1)
        val con1 = recogida().microsectoresM.size
        vm.onMicrosectores(microsectoresTrasPulsar(recogida().microsectoresPorKm, masMicrosectores = true))
        val con2 = recogida().microsectoresM.size
        assertEquals(2, recogida().microsectoresPorKm)
        assertTrue("más por km, más puntos ($con1 -> $con2)", con2 > con1)
        vm.onMicrosectores(microsectoresTrasPulsar(recogida().microsectoresPorKm, masMicrosectores = false))
        assertEquals(con1, recogida().microsectoresM.size)
    }

    @Test
    fun `un tramo que no existe se avisa`() {
        editor.datos.value = null
        val recogida = viewModel().uiState.collectAhora()

        assertEquals(false, recogida().encontrado)
    }

    @Test
    fun `el lienzo pasa de metros a pixeles y de un toque a metros`() {
        val lienzo = LienzoTramo(traza, 400f, 400f, 20f)
        val mitad = lienzo.pixelEn(lienzo.longitudM / 2)
        val proyeccion = checkNotNull(lienzo.proyectar(mitad + Offset(3f, 0f)))

        assertEquals(lienzo.longitudM / 2, proyeccion.metros, lienzo.longitudM * 0.01)
        assertEquals(lienzo.trazo.first(), lienzo.pixelEn(-50.0))
        assertEquals(lienzo.trazo.last().y, lienzo.pixelEn(1e9).y, 0.5f)
    }

    @Test
    fun `elegir una nota la deja elegida y solo pide centrar el mapa si viene de la lista`() {
        val vm = viewModel()
        val recogida = vm.uiState.collectAhora()
        assertNull(recogida().seleccionM)
        assertEquals(0, recogida().centrarVersion)

        vm.onSeleccionar(curva, centrar = false)
        assertEquals(300.0, recogida().seleccionM!!, 1e-9)
        assertEquals(0, recogida().centrarVersion)
        assertEquals(false, recogida().centrarSeleccion)

        vm.onSeleccionar(curva, centrar = true)
        assertEquals(1, recogida().centrarVersion)
        assertEquals(true, recogida().centrarSeleccion)
        vm.onSeleccionar(curva, centrar = true)
        assertEquals(2, recogida().centrarVersion)
        // Elegir otra vez desde el mapa no mueve la cámara
        vm.onSeleccionar(curva, centrar = false)
        assertEquals(false, recogida().centrarSeleccion)
        // Elegir no abre el editor
        assertNull(recogida().editor)
    }

    @Test
    fun `lleva el estilo del mapa instalado y sin mapa lo deja vacio`() {
        assertEquals("{\"version\":8}", viewModel().uiState.collectAhora()().estiloMapaJson)
        estilos = null
        assertNull(viewModel().uiState.collectAhora()().estiloMapaJson)
    }
}
