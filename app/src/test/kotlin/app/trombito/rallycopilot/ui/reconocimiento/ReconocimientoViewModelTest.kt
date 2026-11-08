package app.trombito.rallycopilot.ui.reconocimiento

import app.trombito.rallycopilot.domain.navegacion.ControlNavegacion
import app.trombito.rallycopilot.domain.navegacion.Destino
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.navegacion.OrdenParadas
import app.trombito.rallycopilot.domain.navegacion.PasadaReconocimiento
import app.trombito.rallycopilot.domain.notas.NotasEnCurso
import app.trombito.rallycopilot.domain.notas.OrigenNotas
import app.trombito.rallycopilot.domain.notas.ProveedorNotas
import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.motor.edicion.CambioNota
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.notas.Direccion
import app.trombito.rallycopilot.motor.notas.ElementoHoja
import app.trombito.rallycopilot.motor.notas.HojaNotas
import app.trombito.rallycopilot.motor.notas.Nota
import app.trombito.rallycopilot.motor.ruta.Ruta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Control que imita al real en lo que toca al reconocimiento: pasada y estado van juntos, estado primero. */
private class ControlReconocimientoFalso : ControlNavegacion {
    private val _estado = MutableStateFlow<EstadoNavegacion>(EstadoNavegacion.Inactiva)
    private val _pasadaReconocimiento = MutableStateFlow<PasadaReconocimiento?>(null)
    override val estado: StateFlow<EstadoNavegacion> = _estado
    override val pasadaReconocimiento: StateFlow<PasadaReconocimiento?> = _pasadaReconocimiento
    val segundas = mutableListOf<String>()
    var terminadas = 0

    override fun empezarLibre() {
        _estado.value = EstadoNavegacion.Libre(0L, 0.0)
        _pasadaReconocimiento.value = null
    }

    override fun empezarReconocimiento(tramoId: String) {
        _estado.value = EstadoNavegacion.Libre(0L, 0.0, tramoId)
        _pasadaReconocimiento.value = PasadaReconocimiento(tramoId, 1)
    }

    override fun empezarSegundaPasada(tramoId: String) {
        segundas += tramoId
        _estado.value = EstadoNavegacion.Libre(0L, 0.0, tramoId)
        _pasadaReconocimiento.value = PasadaReconocimiento(tramoId, 2)
    }

    override fun empezar(
        destino: Destino,
        perfil: PerfilRuta,
        ruta: Ruta,
        paradas: List<Destino>,
    ) = error("no se usa")

    override fun editarParadas(orden: OrdenParadas) = Unit

    override fun terminar() {
        terminadas++
        _estado.value = EstadoNavegacion.Inactiva
        _pasadaReconocimiento.value = null
    }

    override fun reiniciarTrip() = false
}

private class ProveedorNotasFalso : ProveedorNotas {
    val mutable = MutableStateFlow<NotasEnCurso?>(null)
    override val notas: StateFlow<NotasEnCurso?> = mutable
}

@OptIn(ExperimentalCoroutinesApi::class)
class ReconocimientoViewModelTest {
    private val control = ControlReconocimientoFalso()
    private val proveedor = ProveedorNotasFalso()

    // Un kilómetro de traza: la meta cae a partir de ~960 m.
    private val traza = listOf(PuntoGeo(39.000, 2.9), PuntoGeo(39.009, 2.9))

    private fun nota(
        posicionM: Double,
        grado: Int,
    ) = ElementoHoja.DeNota(Nota(posicionM, posicionM + 40.0, Direccion.DERECHA, grado))

    private val sinCorregir = HojaNotas(listOf(nota(100.0, 4), nota(400.0, 3)))
    private val corregida1 = HojaNotas(listOf(nota(100.0, 3), nota(250.0, 2), nota(400.0, 3)))
    private val corregida2 = HojaNotas(listOf(nota(100.0, 3), nota(250.0, 2), nota(400.0, 2)))

    @Before
    fun preparar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun emitir(
        hoja: HojaNotas,
        posicionM: Double,
        vMs: Double = 20.0,
    ) {
        proveedor.mutable.value =
            NotasEnCurso(hoja, posicionM, vMs, OrigenNotas.LIBRE, traza = traza, tramoReconocimiento = "t1")
    }

    private fun crear() = ReconocimientoViewModel(control, proveedor)

    @Test
    fun `flujo completo pasada 1 resumen pasada 2 resumen y fin`() {
        val vm = crear()
        vm.empezar("t1")
        emitir(sinCorregir, 10.0)
        emitir(corregida1, 500.0)
        assertNull(vm.resumen.value)

        // En meta pero en marcha: el resumen espera.
        emitir(corregida1, 990.0, vMs = 20.0)
        assertNull(vm.resumen.value)
        // Parado: sale con lo cambiado en la 1.ª.
        emitir(corregida1, 995.0, vMs = 0.0)
        val primero = checkNotNull(vm.resumen.value)
        assertEquals(1, primero.numero)
        assertTrue(primero.hayOtraPasada)
        assertFalse(primero.terminada)
        assertEquals(2, primero.cambios.size)
        assertTrue(primero.cambios[0] is CambioNota.Cambiada)
        assertEquals(100.0, primero.cambios[0].posicionM, 0.0)
        assertTrue(primero.cambios[1] is CambioNota.Anadida)

        vm.onHacerSegundaPasada()
        assertNull(vm.resumen.value)
        assertEquals(listOf("t1"), control.segundas)
        assertEquals(PasadaReconocimiento("t1", 2), control.pasadaReconocimiento.value)

        // La 2.ª parte de las notas corregidas; solo cuenta lo nuevo.
        emitir(corregida1, 5.0)
        emitir(corregida2, 600.0)
        assertNull(vm.resumen.value)
        emitir(corregida2, 998.0, vMs = 0.0)
        val segundo = checkNotNull(vm.resumen.value)
        assertEquals(2, segundo.numero)
        assertFalse(segundo.hayOtraPasada)
        assertEquals(1, segundo.cambios.size)
        assertEquals(400.0, segundo.cambios.single().posicionM, 0.0)

        vm.onTerminar()
        assertNull(vm.resumen.value)
        assertEquals(1, control.terminadas)
        assertEquals(EstadoNavegacion.Inactiva, control.estado.value)
    }

    @Test
    fun `la segunda pasada hereda las notas finales de la primera aunque su primera hoja salga sin correcciones`() {
        val vm = crear()
        vm.empezar("t1")
        emitir(sinCorregir, 10.0)
        emitir(corregida1, 995.0, vMs = 0.0)
        vm.onHacerSegundaPasada()

        // Carrera de arranque: la primera hoja de la 2.ª aún no trae las correcciones y luego se fusionan.
        emitir(sinCorregir, 5.0)
        emitir(corregida1, 6.0)
        emitir(corregida1, 995.0, vMs = 0.0)

        assertTrue(checkNotNull(vm.resumen.value).cambios.isEmpty())
    }

    @Test
    fun `las notas viejas en meta al arrancar la segunda pasada no la dan por acabada`() {
        val vm = crear()
        vm.empezar("t1")
        emitir(sinCorregir, 10.0)
        emitir(corregida1, 995.0, vMs = 0.0)
        vm.onHacerSegundaPasada()

        // La sesión vieja aún publica su última hoja, en meta y parada, antes de que la nueva dé la suya.
        emitir(corregida1, 995.0, vMs = 0.0)
        assertNull(vm.resumen.value)

        // Ya en la 2.ª pasada: sale del principio y llega.
        emitir(corregida1, 5.0)
        emitir(corregida1, 995.0, vMs = 0.0)
        assertNotNull(vm.resumen.value)
    }

    @Test
    fun `terminar a mitad de la pasada da el resumen sin esperar a meta ni a parar`() {
        val vm = crear()
        vm.empezar("t1")
        emitir(sinCorregir, 10.0)
        emitir(corregida1, 500.0, vMs = 20.0)

        control.terminar()

        val resumen = checkNotNull(vm.resumen.value)
        assertTrue(resumen.terminada)
        assertEquals(2, resumen.cambios.size)
        assertTrue(resumen.hayOtraPasada)
    }

    @Test
    fun `desde un resumen de pasada terminada se puede hacer la segunda`() {
        val vm = crear()
        vm.empezar("t1")
        emitir(sinCorregir, 10.0)
        emitir(corregida1, 500.0)
        control.terminar()

        vm.onHacerSegundaPasada()

        assertNull(vm.resumen.value)
        assertEquals(listOf("t1"), control.segundas)
        assertEquals(PasadaReconocimiento("t1", 2), control.pasadaReconocimiento.value)
    }

    @Test
    fun `terminar desde el resumen no vuelve a sacarlo`() {
        val vm = crear()
        vm.empezar("t1")
        emitir(sinCorregir, 10.0)
        emitir(corregida1, 995.0, vMs = 0.0)
        assertNotNull(vm.resumen.value)

        vm.onTerminar()

        assertNull(vm.resumen.value)
    }

    @Test
    fun `si otra sesion sustituye al reconocimiento no hay resumen`() {
        val vm = crear()
        vm.empezar("t1")
        emitir(sinCorregir, 10.0)
        emitir(corregida1, 500.0)

        control.empezarLibre()

        assertNull(vm.resumen.value)
    }

    @Test
    fun `una pasada sin correcciones lo dice con la lista vacia`() {
        val vm = crear()
        vm.empezar("t1")
        emitir(sinCorregir, 10.0)
        emitir(sinCorregir, 995.0, vMs = 0.0)

        assertTrue(checkNotNull(vm.resumen.value).cambios.isEmpty())
    }

    @Test
    fun `lejos de la meta parado no saca el resumen`() {
        val vm = crear()
        vm.empezar("t1")
        emitir(sinCorregir, 10.0)
        emitir(corregida1, 300.0, vMs = 0.0)

        assertNull(vm.resumen.value)
    }
}
