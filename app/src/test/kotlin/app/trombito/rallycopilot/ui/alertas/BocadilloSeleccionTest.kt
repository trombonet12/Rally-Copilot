package app.trombito.rallycopilot.ui.alertas

import app.trombito.rallycopilot.domain.alertas.IncidenciasVigentes
import app.trombito.rallycopilot.motor.alertas.TipoIncidencia
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.notas.TipoAviso
import app.trombito.rallycopilot.ui.mapa.AvisoEnMapa
import app.trombito.rallycopilot.ui.mapa.CurvaEnMapa
import app.trombito.rallycopilot.ui.mapa.NotasEnMapa
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BocadilloSeleccionTest {
    private val fijo = MarcadorRadar("f1", ClaseRadar.FIJO, 39.60, 2.90, 80, rumboGrados = 50.0)
    private val ini = MarcadorRadar("t-ini", ClaseRadar.TRAMO, 39.62, 2.92, 80, tramoId = "t")
    private val tramo = TramoRadarMapa("t", 80, listOf(PuntoGeo(39.62, 2.92), PuntoGeo(39.64, 2.94)), 3_200.0)
    private val radares = RadaresUi(listOf(fijo, ini), listOf(tramo))
    private val bandera = BanderaMapa("tr", false, 39.70, 2.80, nombre = "Sóller", mejorMs = 239_200L)
    private val notas =
        NotasEnMapa(
            curvas = listOf(CurvaEnMapa(120.0, 39.71, 2.81, 3, true, frase = "Izquierda 3 larga", icono = 7)),
            avisos = listOf(AvisoEnMapa(300.0, 39.72, 2.82, TipoAviso.ESTRECHA)),
        )
    private val elementos = ElementosTocables(radares = radares, banderas = listOf(bandera), notas = notas)

    private fun bocadillo(seleccion: SeleccionMapa) = bocadilloDeSeleccion(seleccion, elementos)

    @Test
    fun `un radar fijo saca su limite y el sentido que vigila anclado a su marcador`() {
        val b = checkNotNull(bocadillo(SeleccionMapa.Radar("f1")))
        assertEquals(ContenidoBocadillo.RadarTocado(ClaseRadar.FIJO, 80, 50.0), b.contenido)
        assertEquals(39.60, b.lat, 0.0)
        assertEquals(2.90, b.lon, 0.0)
    }

    @Test
    fun `un radar de tramo sale anclado a su entrada o al punto tocado de la linea`() {
        val entrada = checkNotNull(bocadillo(SeleccionMapa.TramoRadar("t")))
        assertEquals(ContenidoBocadillo.TramoRadarTocado(80, 3_200.0), entrada.contenido)
        assertEquals(39.62, entrada.lat, 0.0)
        val tocado = checkNotNull(bocadillo(SeleccionMapa.TramoRadar("t", PuntoGeo(39.63, 2.93))))
        assertEquals(39.63, tocado.lat, 0.0)
        assertEquals(entrada.id, tocado.id)
    }

    @Test
    fun `la bandera lleva el nombre y el mejor tiempo de su tramo en su punto`() {
        val b = checkNotNull(bocadillo(SeleccionMapa.Bandera("tr", esMeta = false)))
        assertEquals(ContenidoBocadillo.Bandera(esMeta = false, nombre = "Sóller", mejorMs = 239_200L), b.contenido)
        assertEquals(39.70, b.lat, 0.0)
        assertNull(bocadillo(SeleccionMapa.Bandera("tr", esMeta = true)))
    }

    @Test
    fun `la curva y el aviso salen con su frase y la curva manda si coinciden`() {
        val curva = checkNotNull(bocadillo(SeleccionMapa.Nota(120.0)))
        assertEquals(ContenidoBocadillo.NotaDelMapa("Izquierda 3 larga", 7), curva.contenido)
        assertEquals(39.71, curva.lat, 0.0)
        val aviso = checkNotNull(bocadillo(SeleccionMapa.Nota(300.0)))
        assertEquals("Estrecha", (aviso.contenido as ContenidoBocadillo.NotaDelMapa).frase)
        assertNull(bocadillo(SeleccionMapa.Nota(999.0)))
    }

    @Test
    fun `un elemento que ya no existe no saca bocadillo`() {
        assertNull(bocadillo(SeleccionMapa.Radar("nada")))
        assertNull(bocadillo(SeleccionMapa.TramoRadar("nada")))
        assertNull(bocadillo(SeleccionMapa.Incidencia("nada")))
    }

    @Test
    fun `la incidencia lleva la antiguedad de los datos al minuto`() {
        val obra = MarcadorIncidencia("i1", TipoIncidencia.OBRAS, 39.7, 2.8, "Obras", "Ma-10", enRuta = false)
        val e = elementos.copy(incidencias = listOf(obra), antiguedadIncidenciasMs = 5 * 60_000L + 41_000L)
        val contenido = bocadilloDeSeleccion(SeleccionMapa.Incidencia("i1"), e)?.contenido
        assertEquals(5 * 60_000L, (contenido as ContenidoBocadillo.Incidencia).antiguedadMs)
        val sinCopia = bocadilloDeSeleccion(SeleccionMapa.Incidencia("i1"), e.copy(antiguedadIncidenciasMs = null))
        assertNull((sinCopia?.contenido as ContenidoBocadillo.Incidencia).antiguedadMs)
    }

    // --- Dentro del presentador: la seleccion sobrevive al refresco mientras el elemento siga existiendo

    private fun presentar(
        seleccion: SeleccionMapa?,
        radares: RadaresUi = this.radares,
        elementos: ElementosTocables = this.elementos,
        vigentes: IncidenciasVigentes = IncidenciasVigentes(),
    ) = presentarAlertas(null, vigentes, AHORA_MS, seleccion, radares, elementos)

    @Test
    fun `la seleccion sobrevive al refresco de los marcadores si el elemento sigue`() {
        val seleccion = SeleccionMapa.Radar("f1")
        val antes = presentar(seleccion)
        assertEquals(seleccion, antes.seleccionada)
        val refrescados = radares.copy(marcadores = listOf(fijo.copy(limiteKmh = 60), ini))
        val despues = presentar(seleccion, radares = refrescados)
        assertEquals(seleccion, despues.seleccionada)
        assertEquals(antes.bocadillo?.id, despues.bocadillo?.id)
        assertEquals(60, (despues.bocadillo?.contenido as ContenidoBocadillo.RadarTocado).limiteKmh)
    }

    @Test
    fun `si el elemento desaparece del refresco la seleccion y el bocadillo se van`() {
        val seleccion = SeleccionMapa.Radar("f1")
        val sinFijo = presentar(seleccion, radares = radares.copy(marcadores = listOf(ini)))
        assertNull(sinFijo.seleccionada)
        assertNull(sinFijo.bocadillo)
    }

    @Test
    fun `la nota y la bandera tocadas se mantienen mientras sigan en lo que pinta el mapa`() {
        val nota = SeleccionMapa.Nota(120.0)
        val bandera = SeleccionMapa.Bandera("tr", esMeta = false)
        assertEquals(nota, presentar(nota).seleccionada)
        assertEquals(bandera, presentar(bandera).seleccionada)
        val vacio = ElementosTocables()
        assertNull(presentar(nota, elementos = vacio).seleccionada)
        assertNull(presentar(bandera, elementos = vacio).bocadillo)
    }

    @Test
    fun `lo tocado manda sobre el bocadillo de aproximacion`() {
        val ui = presentar(SeleccionMapa.Radar("f1"))
        assertEquals("seleccion-radar-f1", ui.bocadillo?.id)
    }
}
