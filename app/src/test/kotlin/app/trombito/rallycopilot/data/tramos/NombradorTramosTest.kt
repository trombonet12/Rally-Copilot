package app.trombito.rallycopilot.data.tramos

import app.trombito.rallycopilot.data.vias.DatosVias
import app.trombito.rallycopilot.data.vias.FuenteVias
import app.trombito.rallycopilot.domain.busqueda.Buscador
import app.trombito.rallycopilot.domain.busqueda.ResultadoBusqueda
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.longitudMetros
import app.trombito.rallycopilot.motor.vias.IndiceVias
import app.trombito.rallycopilot.motor.vias.SentidoUnico
import app.trombito.rallycopilot.motor.vias.Via
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NombradorTramosTest {
    /** Traza de unos 2,2 km hacia el norte por el meridiano 2,9. */
    private val traza = List(21) { PuntoGeo(39.6 + it * 0.001, 2.9) }

    private fun via(
        id: Long,
        ref: String?,
        nombre: String?,
        desde: Int,
        hasta: Int,
    ) = Via(id, "secondary", null, null, null, SentidoUnico.NO, ref, nombre, traza.subList(desde, hasta + 1))

    private fun fuente(vararg vias: Via) =
        object : FuenteVias {
            override suspend fun datos() =
                if (vias.isEmpty()) null else DatosVias(vias.toList(), IndiceVias(vias.toList()))
        }

    private class BuscadorFalso(
        private val respuesta: (String) -> List<ResultadoBusqueda>,
    ) : Buscador {
        val consultas = mutableListOf<String>()

        override suspend fun buscar(
            texto: String,
            cerca: PuntoGeo?,
            limite: Int,
        ): List<ResultadoBusqueda> {
            consultas += texto
            return respuesta(texto)
        }
    }

    private fun nombrador(
        fuente: FuenteVias,
        buscador: Buscador = BuscadorFalso { emptyList() },
    ) = NombradorTramos(fuente, buscador)

    @Test
    fun `una carretera con referencia da la referencia y el municipio de su calle`() =
        runBlocking {
            val buscador =
                BuscadorFalso { listOf(ResultadoBusqueda(it, "calle", "Escorca", traza[10])) }
            val nombre =
                nombrador(fuente(via(1, "Ma-2141", "Carretera de Sa Calobra", 0, 20)), buscador).nombrar(traza)

            assertEquals("Ma-2141 · Escorca", nombre)
            assertEquals(listOf("Carretera de Sa Calobra"), buscador.consultas)
        }

    @Test
    fun `varias referencias se unen y sin referencia se usa el nombre de la calle`() =
        runBlocking {
            val dominante = via(1, "Ma-10", null, 0, 14)
            val corta = via(2, "Ma-11", null, 14, 20)
            assertEquals("Ma-10 / Ma-11", nombrador(fuente(dominante, corta)).nombrar(traza))

            val calle = via(3, null, "Carrer de Sa Torre", 0, 20)
            assertEquals("Carrer de Sa Torre", nombrador(fuente(calle)).nombrar(traza))
        }

    @Test
    fun `un municipio lejos de la traza no se usa y un fallo del buscador no impide el nombre`() =
        runBlocking {
            val lejos = PuntoGeo(39.9, 3.1)
            val buscadorLejano = BuscadorFalso { listOf(ResultadoBusqueda(it, "calle", "Alcúdia", lejos)) }
            val fuente = fuente(via(1, "Ma-2141", "Carretera de Sa Calobra", 0, 20))
            assertEquals("Ma-2141", nombrador(fuente, buscadorLejano).nombrar(traza))

            val roto = BuscadorFalso { throw IllegalStateException("índice cerrado") }
            assertEquals("Ma-2141", nombrador(fuente, roto).nombrar(traza))
        }

    @Test
    fun `sin vias o con una traza vacia no hay nombre automatico`() =
        runBlocking {
            assertNull(nombrador(fuente()).nombrar(traza))
            assertNull(nombrador(fuente(via(1, "Ma-1", null, 0, 20))).nombrar(listOf(traza.first())))
            assertNull(nombrador(fuente(via(1, "Ma-1", null, 0, 20))).nombrar(listOf(traza.first(), traza.first())))
        }

    @Test
    fun `el muestreo reparte los puntos a lo largo de la traza con su rumbo`() {
        val muestras = MuestreoTraza.puntos(traza, 20)

        assertEquals(20, muestras.size)
        val largo = traza.longitudMetros()
        assertEquals(largo / 40, traza.first().distanciaA(muestras.first().punto), 1.0)
        assertEquals(largo * 39 / 40, traza.first().distanciaA(muestras.last().punto), 1.0)
        assertTrue(muestras.zipWithNext().all { (a, b) -> b.punto.lat > a.punto.lat })
        assertTrue(muestras.all { it.rumboGrados < 0.5 || it.rumboGrados > 359.5 })
    }
}
