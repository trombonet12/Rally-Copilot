package app.trombito.rallycopilot.data.tramos

import app.trombito.rallycopilot.data.vias.DatosVias
import app.trombito.rallycopilot.data.vias.FuenteVias
import app.trombito.rallycopilot.domain.busqueda.Buscador
import app.trombito.rallycopilot.domain.busqueda.ResultadoBusqueda
import app.trombito.rallycopilot.domain.tramos.NombreAutomatico
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
        private val lugar: (PuntoGeo) -> ResultadoBusqueda? = { null },
        private val respuesta: (String) -> List<ResultadoBusqueda>,
    ) : Buscador {
        val consultas = mutableListOf<String>()
        val lugaresPedidos = mutableListOf<Pair<PuntoGeo, Double>>()

        override suspend fun lugarMasCercano(
            punto: PuntoGeo,
            radioM: Double,
        ): ResultadoBusqueda? {
            lugaresPedidos += punto to radioM
            return lugar(punto)
        }

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

            val roto = BuscadorFalso { error("índice cerrado") }
            assertEquals("Ma-2141", nombrador(fuente, roto).nombrar(traza))
        }

    @Test
    fun `sin vias o con una traza vacia no hay nombre automatico`() =
        runBlocking {
            assertNull(nombrador(fuente()).nombrar(traza))
            assertNull(nombrador(fuente(via(1, "Ma-1", null, 0, 20))).nombrar(listOf(traza.first())))
            assertNull(nombrador(fuente(via(1, "Ma-1", null, 0, 20))).nombrar(listOf(traza.first(), traza.first())))
        }

    private fun lugar(nombre: String) = ResultadoBusqueda(nombre, "pueblo", nombre, traza.first())

    /** Un buscador que sabe que el inicio de la traza está en Palma y su final en Valldemossa. */
    private fun conExtremos() =
        BuscadorFalso(
            lugar = { p -> if (p.lat < 39.61) lugar("Palma") else lugar("Valldemossa") },
            respuesta = { emptyList() },
        )

    @Test
    fun `con lugares en el inicio y el final el nombre es Origen a Destino y las carreteras van de subtitulo`() =
        runBlocking {
            val buscador = conExtremos()
            val nombre = nombrador(fuente(via(1, "Ma-2141", "Carretera de Sa Calobra", 0, 20)), buscador).nombrar(traza)

            assertEquals("Palma → Valldemossa\nMa-2141", nombre)
            assertEquals("Palma → Valldemossa", NombreAutomatico.titulo(nombre!!))
            assertEquals("Ma-2141", NombreAutomatico.subtitulo(nombre))
            // Pregunta por el primer y el último punto de la traza, con un radio razonable (pueblos y aldeas).
            assertEquals(listOf(traza.first(), traza.last()), buscador.lugaresPedidos.map { it.first })
            assertTrue(buscador.lugaresPedidos.all { it.second in 1_000.0..10_000.0 })
        }

    @Test
    fun `varias carreteras se unen en el subtitulo`() =
        runBlocking {
            val a = via(1, "Ma-10", null, 0, 14)
            val b = via(2, "Ma-11", null, 14, 20)
            val nombre = nombrador(fuente(a, b), conExtremos()).nombrar(traza)

            assertEquals("Palma → Valldemossa\nMa-10 / Ma-11", nombre)
        }

    @Test
    fun `sin datos de vias con lugares el nombre es solo Origen a Destino`() =
        runBlocking {
            assertEquals("Palma → Valldemossa", nombrador(fuente(), conExtremos()).nombrar(traza))
        }

    @Test
    fun `vias sin referencia ni nombre no dan subtitulo`() =
        runBlocking {
            val sinNombre = via(1, null, null, 0, 20)
            assertEquals("Palma → Valldemossa", nombrador(fuente(sinNombre), conExtremos()).nombrar(traza))
        }

    @Test
    fun `si inicio y final caen en el mismo lugar o solo hay uno se nombra como siempre`() =
        runBlocking {
            val fuente = fuente(via(1, "Ma-2141", "Carretera de Sa Calobra", 0, 20))
            val mismo = BuscadorFalso(lugar = { lugar("Palma") }, respuesta = { emptyList() })
            assertEquals("Ma-2141", nombrador(fuente, mismo).nombrar(traza))

            val soloInicio = BuscadorFalso(lugar = { p -> if (p.lat < 39.61) lugar("Palma") else null }) { emptyList() }
            assertEquals("Ma-2141", nombrador(fuente, soloInicio).nombrar(traza))
            assertNull(nombrador(fuente(), soloInicio).nombrar(traza))
        }

    @Test
    fun `un fallo del indice al buscar el lugar no impide el nombre de siempre`() =
        runBlocking {
            val roto =
                object : Buscador {
                    override suspend fun buscar(
                        texto: String,
                        cerca: PuntoGeo?,
                        limite: Int,
                    ): List<ResultadoBusqueda> = emptyList()

                    override suspend fun lugarMasCercano(
                        punto: PuntoGeo,
                        radioM: Double,
                    ): ResultadoBusqueda? = error("índice cerrado")
                }
            val fuente = fuente(via(1, "Ma-2141", "Carretera de Sa Calobra", 0, 20))
            assertEquals("Ma-2141", nombrador(fuente, roto).nombrar(traza))
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
