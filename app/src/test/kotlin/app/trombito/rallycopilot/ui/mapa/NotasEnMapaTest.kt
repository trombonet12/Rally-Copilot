package app.trombito.rallycopilot.ui.mapa

import app.trombito.rallycopilot.domain.notas.NotasEnCurso
import app.trombito.rallycopilot.domain.notas.OrigenNotas
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.notas.Aviso
import app.trombito.rallycopilot.motor.notas.Direccion
import app.trombito.rallycopilot.motor.notas.ElementoHoja
import app.trombito.rallycopilot.motor.notas.HojaNotas
import app.trombito.rallycopilot.motor.notas.Nota
import app.trombito.rallycopilot.motor.notas.TipoAviso
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotasEnMapaTest {
    private fun curva(
        inicio: Double,
        fin: Double = inicio + 40.0,
        grado: Int? = 3,
        direccion: Direccion = Direccion.DERECHA,
    ) = ElementoHoja.DeNota(Nota(inicio, fin, direccion, grado))

    private fun aviso(
        posicion: Double,
        tipo: TipoAviso = TipoAviso.ESTRECHA,
    ) = ElementoHoja.DeAviso(Aviso(posicion, tipo))

    private fun enCurso(
        elementos: List<ElementoHoja>,
        posicionM: Double = 0.0,
        puntoEn: ((Double) -> PuntoGeo)? = { PuntoGeo(39.6, 2.9 + it / 100_000.0) },
    ) = NotasEnCurso(HojaNotas(elementos), posicionM, 10.0, OrigenNotas.GUIADO, puntoEn = puntoEn)

    @Test
    fun `el apice es el punto medio entre el inicio y el final de la curva`() {
        assertEquals(120.0, apiceM(Nota(100.0, 140.0, Direccion.IZQUIERDA, 4)), 1e-9)
        assertEquals(500.0, apiceM(Nota(500.0, 500.0, Direccion.DERECHA, null)), 1e-9)
    }

    @Test
    fun `la curva se pone en su apice y no en su inicio`() {
        val mapa = notasEnMapa(enCurso(listOf(curva(100.0, 140.0, grado = 2, direccion = Direccion.IZQUIERDA))))
        val c = mapa.curvas.single()
        assertEquals(2, c.grado)
        assertTrue(c.izquierda)
        assertEquals(100.0, c.posicionM, 1e-9)
        assertEquals(2.9 + 120.0 / 100_000.0, c.lon, 1e-9)
    }

    @Test
    fun `solo entran los elementos de la ventana de 1500 metros por delante`() {
        val hoja =
            listOf(
                curva(10.0),
                aviso(900.0),
                curva(1_480.0),
                curva(1_600.0),
                aviso(1_700.0),
            )
        val mapa = notasEnMapa(enCurso(hoja, posicionM = 0.0))
        assertEquals(listOf(10.0, 1_480.0), mapa.curvas.map { it.posicionM })
        assertEquals(listOf(900.0), mapa.avisos.map { it.posicionM })
    }

    @Test
    fun `lo ya pasado desaparece salvo los ultimos metros`() {
        val hoja = listOf(curva(100.0), aviso(300.0), curva(900.0), curva(960.0), aviso(985.0))
        val mapa = notasEnMapa(enCurso(hoja, posicionM = 1_000.0))
        assertEquals(listOf(960.0), mapa.curvas.map { it.posicionM })
        assertEquals(listOf(985.0), mapa.avisos.map { it.posicionM })
    }

    @Test
    fun `los avisos unidos y los de puente o rasante tambien salen`() {
        val nota = Nota(400.0, 440.0, Direccion.DERECHA, 3)
        val elemento =
            ElementoHoja.DeNota(
                nota,
                avisosUnidos = listOf(Aviso(390.0, TipoAviso.CRUCE)),
                sobrePuente = Aviso(410.0, TipoAviso.PUENTE),
                sobreRasante = Aviso(420.0, TipoAviso.RASANTE),
            )
        val mapa = notasEnMapa(enCurso(listOf(elemento)))
        assertEquals(listOf(TipoAviso.CRUCE, TipoAviso.PUENTE, TipoAviso.RASANTE), mapa.avisos.map { it.tipo })
    }

    @Test
    fun `sin hoja ni forma de colocar las posiciones no hay nada`() {
        assertEquals(NotasEnMapa(), notasEnMapa(null))
        assertEquals(NotasEnMapa(), notasEnMapa(enCurso(listOf(curva(100.0)), puntoEn = null)))
    }

    @Test
    fun `los features llevan texto color orden e icono`() {
        val mapa =
            notasEnMapa(
                enCurso(
                    listOf(
                        curva(100.0, grado = 5, direccion = Direccion.IZQUIERDA),
                        curva(300.0, grado = null, direccion = Direccion.DERECHA),
                        aviso(200.0, TipoAviso.ESTRECHA),
                    ),
                ),
            )
        val curvas = featuresCurvas(mapa.curvas).features().orEmpty()
        assertEquals(listOf("5", "H"), curvas.map { it.getStringProperty(PROPIEDAD_TEXTO) })
        assertEquals(
            listOf(COLOR_CURVA_IZQUIERDA, COLOR_CURVA_DERECHA),
            curvas.map { it.getStringProperty(PROPIEDAD_COLOR_TEXTO) },
        )
        assertEquals(100.0, curvas.first().getNumberProperty(PROPIEDAD_ORDEN).toDouble(), 1e-9)
        val avisos = featuresAvisos(mapa.avisos).features().orEmpty()
        assertEquals(listOf(imagenAviso(TipoAviso.ESTRECHA)), avisos.map { it.getStringProperty(PROPIEDAD_ICONO) })
    }

    @Test
    fun `cada tipo de aviso tiene su imagen y el texto de la horquilla es H`() {
        assertEquals(TipoAviso.entries.size, TipoAviso.entries.map(::imagenAviso).toSet().size)
        assertEquals("H", textoCurva(null))
        assertEquals("1", textoCurva(1))
    }
}
