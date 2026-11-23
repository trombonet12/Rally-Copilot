package app.trombito.rallycopilot.data.tramos

import app.trombito.rallycopilot.domain.busqueda.Buscador
import app.trombito.rallycopilot.domain.busqueda.ResultadoBusqueda
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LugaresCercanosTest {
    private val salida = PuntoGeo(39.60, 2.60)
    private val meta = PuntoGeo(39.70, 2.70)

    private class BuscadorContado(
        private val lugares: List<ResultadoBusqueda>,
    ) : Buscador {
        var consultas = 0

        override suspend fun buscar(
            texto: String,
            cerca: PuntoGeo?,
            limite: Int,
        ): List<ResultadoBusqueda> = emptyList()

        override suspend fun lugaresPoblados(): List<ResultadoBusqueda> {
            consultas++
            return lugares
        }
    }

    private fun lugar(
        nombre: String,
        categoria: String,
        punto: PuntoGeo,
    ) = ResultadoBusqueda(nombre, categoria, null, punto)

    @Test
    fun `elige el pueblo de la meta y carga los lugares una sola vez`() =
        runBlocking {
            val buscador = BuscadorContado(listOf(lugar("Inca", "pueblo", PuntoGeo(39.701, 2.701))))
            val lugares = LugaresCercanos(buscador)

            assertEquals("Inca", lugares.destinoDe(salida, meta))
            assertEquals("Inca", lugares.destinoDe(salida, meta))
            assertEquals(1, buscador.consultas)
        }

    @Test
    fun `una aldea pegada a la meta gana a un pueblo lejano pero no a uno casi igual de cerca`() =
        runBlocking {
            val lejos = lugar("Pueblo lejano", "pueblo", PuntoGeo(39.715, 2.70))
            val aldea = lugar("Aldea", "aldea", PuntoGeo(39.7005, 2.70))
            val ciudad = lugar("Ciudad", "ciudad", PuntoGeo(39.708, 2.70))
            // Ciudad a ~890 m, aldea a ~55 m: 55 + 1.500 > 890, gana la ciudad.
            val conCiudad = LugaresCercanos(BuscadorContado(listOf(lejos, aldea, ciudad)))
            assertEquals("Ciudad", conCiudad.destinoDe(salida, meta))
            // Sin la ciudad, la aldea a 55 m le gana al pueblo a 1.670 m.
            assertEquals("Aldea", LugaresCercanos(BuscadorContado(listOf(lejos, aldea))).destinoDe(salida, meta))
        }

    @Test
    fun `sin lugares o con categorias desconocidas es el rumbo y no se guarda como cargado`() =
        runBlocking {
            val buscador = BuscadorContado(listOf(lugar("Playa", "playa", meta)))
            val lugares = LugaresCercanos(buscador)

            assertEquals("el noreste", lugares.destinoDe(salida, meta))
            assertEquals("el noreste", lugares.destinoDe(salida, meta))
            assertEquals(2, buscador.consultas)
        }
}
