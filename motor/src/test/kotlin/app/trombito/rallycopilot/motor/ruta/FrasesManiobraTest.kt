package app.trombito.rallycopilot.motor.ruta

import org.junit.Assert.assertEquals
import org.junit.Test

class FrasesManiobraTest {
    private fun m(
        tipo: TipoManiobra,
        vias: List<String> = emptyList(),
        salida: Int? = null,
        instruccion: String = "Instruccion de Valhalla.",
    ) = Maniobra(tipo, instruccion, vias, 0, 1, 100.0, 10.0, salida)

    @Test
    fun `giros con via`() {
        assertEquals("cruce, derecha, Ma-10", FrasesManiobra.frase(m(TipoManiobra.DERECHA, listOf("Ma-10"))))
        assertEquals("cruce, izquierda, Ma-2141", FrasesManiobra.frase(m(TipoManiobra.IZQUIERDA, listOf("Ma-2141"))))
        assertEquals(
            "cruce, cerrada derecha, Ma-10",
            FrasesManiobra.frase(m(TipoManiobra.CERRADA_DERECHA, listOf("Ma-10"))),
        )
        assertEquals(
            "cruce, cerrada izquierda, Ma-10",
            FrasesManiobra.frase(m(TipoManiobra.CERRADA_IZQUIERDA, listOf("Ma-10"))),
        )
    }

    @Test
    fun `giros sin via`() {
        assertEquals("cruce, derecha", FrasesManiobra.frase(m(TipoManiobra.DERECHA)))
        assertEquals("cruce, izquierda", FrasesManiobra.frase(m(TipoManiobra.IZQUIERDA)))
        assertEquals("ligera derecha", FrasesManiobra.frase(m(TipoManiobra.LIGERA_DERECHA)))
        assertEquals("ligera izquierda", FrasesManiobra.frase(m(TipoManiobra.LIGERA_IZQUIERDA)))
    }

    @Test
    fun `prefiere la referencia de carretera al nombre`() {
        val vias = listOf("Carrer de Sant Miquel", "Ma-13", "Camí Vell")
        assertEquals("cruce, derecha, Ma-13", FrasesManiobra.frase(m(TipoManiobra.DERECHA, vias)))
        listOf("AP-1", "A-11", "ma-2141", "PM-710").forEach {
            assertEquals(
                "cruce, derecha, $it",
                FrasesManiobra.frase(m(TipoManiobra.DERECHA, listOf("Calle Mayor", it))),
            )
        }
    }

    @Test
    fun `sin referencia usa el primer nombre`() {
        val vias = listOf("Carrer de Sant Miquel", "Camí Vell")
        assertEquals("cruce, derecha, Carrer de Sant Miquel", FrasesManiobra.frase(m(TipoManiobra.DERECHA, vias)))
    }

    @Test
    fun `rotonda con ordinal femenino`() {
        val ordinales =
            listOf(
                "primera",
                "segunda",
                "tercera",
                "cuarta",
                "quinta",
                "sexta",
                "séptima",
                "octava",
                "novena",
                "décima",
            )
        ordinales.forEachIndexed { i, ordinal ->
            assertEquals(
                "rotonda, $ordinal salida, Ma-13",
                FrasesManiobra.frase(m(TipoManiobra.ROTONDA_ENTRADA, listOf("Ma-13"), salida = i + 1)),
            )
        }
        assertEquals("rotonda, tercera salida", FrasesManiobra.frase(m(TipoManiobra.ROTONDA_ENTRADA, salida = 3)))
    }

    @Test
    fun `rotonda sin numero de salida o fuera de rango se canta sin ordinal`() {
        val via = listOf("Ma-13")
        assertEquals("en la rotonda, sal hacia Ma-13", FrasesManiobra.frase(m(TipoManiobra.ROTONDA_ENTRADA, via)))
        assertEquals("en la rotonda, sal hacia Ma-13", FrasesManiobra.frase(m(TipoManiobra.ROTONDA_ENTRADA, via, 11)))
        assertEquals("en la rotonda, sal hacia Ma-13", FrasesManiobra.frase(m(TipoManiobra.ROTONDA_ENTRADA, via, 0)))
        assertEquals("toma la salida", FrasesManiobra.frase(m(TipoManiobra.ROTONDA_ENTRADA)))
        assertEquals("toma la salida", FrasesManiobra.frase(m(TipoManiobra.ROTONDA_ENTRADA, salida = 11)))
    }

    @Test
    fun `salidas bifurcaciones y otros`() {
        assertEquals("salida, Ma-1", FrasesManiobra.frase(m(TipoManiobra.SALIDA_AUTOVIA_DERECHA, listOf("Ma-1"))))
        assertEquals("salida, Ma-1", FrasesManiobra.frase(m(TipoManiobra.SALIDA_AUTOVIA_IZQUIERDA, listOf("Ma-1"))))
        assertEquals("salida", FrasesManiobra.frase(m(TipoManiobra.SALIDA_AUTOVIA_DERECHA)))
        assertEquals("mantente a la izquierda", FrasesManiobra.frase(m(TipoManiobra.BIFURCACION_IZQUIERDA)))
        assertEquals("mantente a la derecha", FrasesManiobra.frase(m(TipoManiobra.BIFURCACION_DERECHA)))
        assertEquals("cambio de sentido", FrasesManiobra.frase(m(TipoManiobra.CAMBIO_SENTIDO)))
        assertEquals("destino", FrasesManiobra.frase(m(TipoManiobra.DESTINO)))
        assertEquals("recto", FrasesManiobra.frase(m(TipoManiobra.RECTO)))
        assertEquals("recto, Ma-13", FrasesManiobra.frase(m(TipoManiobra.RECTO, listOf("Ma-13"))))
        assertEquals("incorpórate, Ma-1", FrasesManiobra.frase(m(TipoManiobra.INCORPORACION, listOf("Ma-1"))))
    }

    @Test
    fun `tipo otra devuelve la instruccion de valhalla`() {
        assertEquals("Instruccion de Valhalla.", FrasesManiobra.frase(m(TipoManiobra.OTRA, listOf("Ma-1"))))
    }

    @Test
    fun `sin via (aviso inminente) los giros no llevan el nombre de la carretera`() {
        assertEquals("cruce, derecha", FrasesManiobra.frase(m(TipoManiobra.DERECHA, listOf("Ma-10")), conVia = false))
        assertEquals(
            "salida",
            FrasesManiobra.frase(m(TipoManiobra.SALIDA_AUTOVIA_DERECHA, listOf("Ma-1")), conVia = false),
        )
        assertEquals("recto", FrasesManiobra.frase(m(TipoManiobra.RECTO, listOf("Ma-13")), conVia = false))
        assertEquals(
            "toma la salida",
            FrasesManiobra.frase(m(TipoManiobra.ROTONDA_ENTRADA, listOf("Ma-13")), conVia = false),
        )
    }

    @Test
    fun `la frase de recalculando es corta`() {
        assertEquals("recalculando", FrasesManiobra.RECALCULANDO)
    }
}
