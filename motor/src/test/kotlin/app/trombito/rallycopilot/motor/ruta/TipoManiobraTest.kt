package app.trombito.rallycopilot.motor.ruta

import org.junit.Assert.assertEquals
import org.junit.Test

class TipoManiobraTest {
    @Test
    fun `tabla oficial de valhalla`() {
        val esperado =
            mapOf(
                0 to TipoManiobra.OTRA,
                1 to TipoManiobra.SALIDA,
                2 to TipoManiobra.SALIDA,
                3 to TipoManiobra.SALIDA,
                4 to TipoManiobra.DESTINO,
                5 to TipoManiobra.DESTINO,
                6 to TipoManiobra.DESTINO,
                7 to TipoManiobra.RECTO,
                8 to TipoManiobra.RECTO,
                9 to TipoManiobra.LIGERA_DERECHA,
                10 to TipoManiobra.DERECHA,
                11 to TipoManiobra.CERRADA_DERECHA,
                12 to TipoManiobra.CAMBIO_SENTIDO,
                13 to TipoManiobra.CAMBIO_SENTIDO,
                14 to TipoManiobra.CERRADA_IZQUIERDA,
                15 to TipoManiobra.IZQUIERDA,
                16 to TipoManiobra.LIGERA_IZQUIERDA,
                17 to TipoManiobra.RECTO,
                18 to TipoManiobra.SALIDA_AUTOVIA_DERECHA,
                19 to TipoManiobra.SALIDA_AUTOVIA_IZQUIERDA,
                20 to TipoManiobra.SALIDA_AUTOVIA_DERECHA,
                21 to TipoManiobra.SALIDA_AUTOVIA_IZQUIERDA,
                22 to TipoManiobra.RECTO,
                23 to TipoManiobra.BIFURCACION_DERECHA,
                24 to TipoManiobra.BIFURCACION_IZQUIERDA,
                25 to TipoManiobra.INCORPORACION,
                26 to TipoManiobra.ROTONDA_ENTRADA,
                27 to TipoManiobra.ROTONDA_SALIDA,
                28 to TipoManiobra.FERRY,
                29 to TipoManiobra.FERRY,
                37 to TipoManiobra.INCORPORACION,
                38 to TipoManiobra.INCORPORACION,
            )
        esperado.forEach { (tipo, valor) -> assertEquals("tipo $tipo", valor, TipoManiobra.desdeValhalla(tipo)) }
    }

    @Test
    fun `transporte publico y desconocidos son otra`() {
        listOf(30, 31, 32, 33, 34, 35, 36, 39, 40, 99, -1).forEach {
            assertEquals("tipo $it", TipoManiobra.OTRA, TipoManiobra.desdeValhalla(it))
        }
    }
}
