package app.trombito.rallycopilot.motor.voz

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.notas.Aviso
import app.trombito.rallycopilot.motor.notas.Direccion
import app.trombito.rallycopilot.motor.notas.ElementoHoja
import app.trombito.rallycopilot.motor.notas.Nota
import app.trombito.rallycopilot.motor.notas.TipoAviso
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class IdsEstablesNotasTest {
    private val base = PuntoGeo(39.7, 2.9)

    /** Punto a [metros] al este de [base] (a esta latitud un grado de longitud mide unos 85,6 km). */
    private fun alEste(metros: Double) = PuntoGeo(base.lat, base.lon + metros / METROS_POR_GRADO_LON)

    private fun curva(
        posicionM: Double,
        direccion: Direccion = Direccion.DERECHA,
    ) = ElementoHoja.DeNota(Nota(posicionM, posicionM + 30.0, direccion, grado = 3))

    @Test
    fun `la misma curva con otra posicion en la hoja da el mismo id`() {
        val ids = IdsEstablesNotas()
        val primera = ids.id(curva(960.0), base)
        val segunda = ids.id(curva(120.0), alEste(3.0))
        assertEquals(primera, segunda)
    }

    @Test
    fun `curvas separadas 30 metros tienen ids distintos`() {
        val ids = IdsEstablesNotas()
        assertNotEquals(ids.id(curva(100.0), base), ids.id(curva(130.0), alEste(30.0)))
    }

    @Test
    fun `misma posicion con otra direccion o tipo es otro elemento`() {
        val ids = IdsEstablesNotas()
        val derecha = ids.id(curva(100.0, Direccion.DERECHA), base)
        val izquierda = ids.id(curva(100.0, Direccion.IZQUIERDA), base)
        val puente = ids.id(ElementoHoja.DeAviso(Aviso(100.0, TipoAviso.PUENTE)), base)
        val tunel = ids.id(ElementoHoja.DeAviso(Aviso(100.0, TipoAviso.TUNEL)), base)
        assertEquals(4, setOf(derecha, izquierda, puente, tunel).size)
    }

    @Test
    fun `el id se mantiene al preguntar de nuevo`() {
        val ids = IdsEstablesNotas()
        val primera = ids.id(curva(100.0), base)
        ids.id(curva(500.0), alEste(400.0))
        assertEquals(primera, ids.id(curva(100.0), base))
    }

    private companion object {
        const val METROS_POR_GRADO_LON = 85_500.0
    }
}
