package app.trombito.rallycopilot.ui.comun

import org.junit.Assert.assertEquals
import org.junit.Test

class PlacaCarreteraTest {
    private fun assertTipo(
        esperado: TipoPlaca,
        ref: String,
    ) = assertEquals(ref, esperado, tipoPlaca(ref))

    @Test
    fun `Ma con una o dos cifras es naranja, con sufijo de letra incluido`() {
        assertTipo(TipoPlaca.NARANJA, "Ma-19")
        assertTipo(TipoPlaca.NARANJA, "Ma-13")
        assertTipo(TipoPlaca.NARANJA, "Ma-1")
        assertTipo(TipoPlaca.NARANJA, "Ma-11A")
    }

    @Test
    fun `Ma con tres o cuatro cifras es amarilla, con sufijo de letra incluido`() {
        assertTipo(TipoPlaca.AMARILLA, "Ma-2141")
        assertTipo(TipoPlaca.AMARILLA, "Ma-1110a")
        assertTipo(TipoPlaca.AMARILLA, "Ma-100")
    }

    @Test
    fun `AP y A son azules`() {
        assertTipo(TipoPlaca.AZUL, "AP-7")
        assertTipo(TipoPlaca.AZUL, "A-2")
    }

    @Test
    fun `sin cifras u otro prefijo es neutra`() {
        assertTipo(TipoPlaca.NEUTRA, "Ma-")
        assertTipo(TipoPlaca.NEUTRA, "PM-1")
        assertTipo(TipoPlaca.NEUTRA, "A-")
        assertTipo(TipoPlaca.NEUTRA, "Ma-12345")
        assertTipo(TipoPlaca.NEUTRA, "")
    }

    @Test
    fun `no distingue mayusculas ni espacios`() {
        assertTipo(TipoPlaca.NARANJA, "MA-19")
        assertTipo(TipoPlaca.NARANJA, "ma-19")
        assertTipo(TipoPlaca.NARANJA, "  Ma-19 ")
        assertTipo(TipoPlaca.NARANJA, "Ma 19")
        assertTipo(TipoPlaca.AMARILLA, " ma-2141 ")
        assertTipo(TipoPlaca.AZUL, "ap-7")
    }
}
