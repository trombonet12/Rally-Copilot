package app.trombito.rallycopilot.ui.mapa

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.notas.TipoAviso
import app.trombito.rallycopilot.ui.alertas.ClaseRadar
import app.trombito.rallycopilot.ui.alertas.MarcadorRadar
import app.trombito.rallycopilot.ui.alertas.RadaresUi
import app.trombito.rallycopilot.ui.alertas.SeleccionMapa
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToquesMapaTest {
    private val punto = PuntoGeo(39.6, 2.9)
    private val ficha = FichaCalor("Ma-10", "Carretera de Sóller", 5, 1.0, 54.0)
    private val radares =
        RadaresUi(
            marcadores =
                listOf(
                    MarcadorRadar("f1", ClaseRadar.FIJO, 39.6, 2.9, 80),
                    MarcadorRadar("t-ini", ClaseRadar.TRAMO, 39.7, 2.9, 80, tramoId = "t"),
                ),
        )
    private val notas =
        NotasEnMapa(
            curvas = listOf(CurvaEnMapa(120.5, 39.6, 2.9, 3, izquierda = true, frase = "Izquierda 3")),
            avisos = listOf(AvisoEnMapa(300.0, 39.6, 2.9, TipoAviso.ESTRECHA)),
        )

    /** Consultas que no encuentran nada salvo lo que se indique; anota cuáles se preguntaron y en qué orden. */
    @Suppress("LongParameterList") // una consulta por capa
    private class Consultas(
        val incidencia: String? = null,
        val radar: String? = null,
        val tramoRadar: String? = null,
        val bandera: SeleccionMapa.Bandera? = null,
        val notaEditable: Double? = null,
        val notaMapa: Double? = null,
        val calor: FichaCalor? = null,
    ) {
        val preguntadas = mutableListOf<String>()

        fun construir() =
            ConsultasToque(
                incidencia = { incidencia.also { preguntadas += "incidencia" } },
                radar = { radar.also { preguntadas += "radar" } },
                tramoRadar = { tramoRadar.also { preguntadas += "tramoRadar" } },
                bandera = { bandera.also { preguntadas += "bandera" } },
                notaEditable = { notaEditable.also { preguntadas += "notaEditable" } },
                notaMapa = { notaMapa.also { preguntadas += "notaMapa" } },
                calor = { calor.also { preguntadas += "calor" } },
            )
    }

    private fun despachar(consultas: Consultas) = despacharToque(consultas.construir(), radares, notas, punto)

    @Test
    fun `sin nada bajo el dedo se pregunta a todas las capas en orden y no se toca nada`() {
        val c = Consultas()
        assertEquals(ResultadoToque.Nada, despachar(c))
        assertEquals(
            listOf("incidencia", "radar", "tramoRadar", "bandera", "notaEditable", "notaMapa", "calor"),
            c.preguntadas,
        )
    }

    @Test
    fun `la incidencia gana a todo lo demas y no se consulta nada mas`() {
        val c = Consultas(incidencia = "i1", radar = "f1", calor = ficha)
        assertEquals(ResultadoToque.Selecciona(SeleccionMapa.Incidencia("i1"), "incidencia"), despachar(c))
        assertEquals(listOf("incidencia"), c.preguntadas)
    }

    @Test
    fun `un radar fijo se selecciona por su id`() {
        assertEquals(
            ResultadoToque.Selecciona(SeleccionMapa.Radar("f1"), "radar"),
            despachar(Consultas(radar = "f1")),
        )
    }

    @Test
    fun `el marcador de entrada de un tramo selecciona el tramo`() {
        assertEquals(
            ResultadoToque.Selecciona(SeleccionMapa.TramoRadar("t"), "radar_tramo"),
            despachar(Consultas(radar = "t-ini")),
        )
    }

    @Test
    fun `la linea de un radar de tramo se selecciona con el punto tocado como ancla`() {
        assertEquals(
            ResultadoToque.Selecciona(SeleccionMapa.TramoRadar("t", punto), "radar_tramo"),
            despachar(Consultas(tramoRadar = "t")),
        )
    }

    @Test
    fun `la bandera de un tramo se selecciona con su tramo y si es meta`() {
        val bandera = SeleccionMapa.Bandera("tramo-1", esMeta = true)
        assertEquals(ResultadoToque.Selecciona(bandera, "bandera"), despachar(Consultas(bandera = bandera)))
    }

    @Test
    fun `la nota editable va antes que la del mapa y abre el editor`() {
        val c = Consultas(notaEditable = 120.5, notaMapa = 120.5)
        assertEquals(ResultadoToque.EditaNota(120.5), despachar(c))
        assertTrue("notaMapa" !in c.preguntadas)
    }

    @Test
    fun `la nota del mapa se ajusta a la posicion conocida`() {
        val tocada = despachar(Consultas(notaMapa = 120.5000001))
        assertEquals(ResultadoToque.Selecciona(SeleccionMapa.Nota(120.5), "nota"), tocada)
        assertEquals(
            ResultadoToque.Selecciona(SeleccionMapa.Nota(777.0), "nota"),
            despachar(Consultas(notaMapa = 777.0)),
        )
    }

    @Test
    fun `la capa de calor es la ultima`() {
        assertEquals(ResultadoToque.Ficha(ficha), despachar(Consultas(calor = ficha)))
        assertEquals(
            ResultadoToque.Selecciona(SeleccionMapa.Radar("f1"), "radar"),
            despachar(Consultas(radar = "f1", calor = ficha)),
        )
    }

    @Test
    fun `la linea de diagnostico lleva la capa y el resultado pero ningun id ni coordenada`() {
        assertEquals(
            "toque capa=radar resultado=bocadillo",
            lineaDiagnosticoToque(ResultadoToque.Selecciona(SeleccionMapa.Radar("f1"), "radar")),
        )
        assertEquals("toque capa=ninguna", lineaDiagnosticoToque(ResultadoToque.Nada))
        assertEquals("toque capa=nota_editable resultado=editor", lineaDiagnosticoToque(ResultadoToque.EditaNota(3.0)))
        assertEquals(
            "toque capa=calor resultado=ficha",
            lineaDiagnosticoToque(ResultadoToque.Ficha(ficha)),
        )
    }

    // --- Respaldo por proximidad en pantalla

    private val posiciones = mapOf("a" to (100f to 500f), "b" to (180f to 500f))

    private fun cercano(
        x: Float,
        y: Float,
        margen: Float = 72f,
    ) = masCercano(posiciones.keys.toList(), x, y, margen) { posiciones[it] }

    @Test
    fun `el candidato mas cercano al dedo dentro del margen gana`() {
        assertEquals("a", cercano(110f, 510f))
        assertEquals("b", cercano(150f, 500f))
        assertNull(cercano(500f, 500f))
    }

    @Test
    fun `un candidato que no se puede proyectar se ignora`() {
        assertNull(masCercano(listOf("a"), 0f, 0f, 100f) { null })
    }

    @Test
    fun `la distancia a una linea es la del segmento mas cercano`() {
        val linea = listOf(0f to 0f, 100f to 0f, 100f to 100f)
        assertEquals(10f, distanciaALinea(linea, 50f, 10f), 1e-4f)
        assertEquals(5f, distanciaALinea(linea, 105f, 50f), 1e-4f)
        // Pasado el extremo, cuenta la distancia al extremo.
        assertEquals(10f, distanciaALinea(linea, 100f, 110f), 1e-4f)
        assertEquals(Float.POSITIVE_INFINITY, distanciaALinea(emptyList(), 0f, 0f), 0f)
        assertEquals(5f, distanciaALinea(listOf(3f to 4f), 0f, 0f), 1e-4f)
    }
}
