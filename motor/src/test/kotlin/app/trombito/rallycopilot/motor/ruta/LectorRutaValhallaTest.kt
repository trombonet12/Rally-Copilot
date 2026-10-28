package app.trombito.rallycopilot.motor.ruta

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LectorRutaValhallaTest {
    private val puntos =
        listOf(
            PuntoGeo(39.569815, 2.650123),
            PuntoGeo(39.570501, 2.651987),
            PuntoGeo(39.571244, 2.653310),
            PuntoGeo(39.572001, 2.652004),
        )

    private fun man(
        type: Int,
        instruccion: String,
        km: Double,
        inicio: Int,
        fin: Int,
    ) = """{"type": $type, "instruction": "$instruccion", "time": ${km * 100}, "length": $km,
        "begin_shape_index": $inicio, "end_shape_index": $fin}"""

    private fun json(cadena: String): String = cadena.replace("\\", "\\\\")

    private fun leg(
        shape: List<PuntoGeo>,
        maniobras: String,
    ) = """{"maneuvers": [$maniobras], "summary": {"length": 0.5, "time": 60.0}, "shape": "${json(
        Polyline6.codificar(shape),
    )}"}"""

    private fun respuesta(vararg legs: String) =
        """{"trip": {"locations": [], "legs": [${legs.joinToString(
            ",",
        )}], "status_message": "Found route between points",
            "status": 0, "units": "kilometers", "language": "es-ES",
            "summary": {"length": 1.0, "time": 120.0}}}"""

    private val girar =
        """{"type": 10, "instruction": "Gira a la derecha por Ma-10.", "verbal_succinct_transition_instruction": "Gira.",
            "street_names": ["Ma-10", "Carretera de Valldemossa"], "time": 40.5, "length": 0.35,
            "begin_shape_index": 1, "end_shape_index": 3, "travel_mode": "drive", "travel_type": "car"}"""
    private val salida =
        """{"type": 1, "instruction": "Conduce al este por Carrer de Sant Miquel.", "street_names": ["Carrer de Sant Miquel"],
            "time": 20.0, "length": 0.15, "begin_shape_index": 0, "end_shape_index": 1}"""
    private val destino =
        """{"type": 4, "instruction": "Has llegado a tu destino.", "time": 0.0, "length": 0.0,
            "begin_shape_index": 3, "end_shape_index": 3}"""

    @Test
    fun `lee una leg con sus maniobras`() {
        val ruta = LectorRutaValhalla.leer(respuesta(leg(puntos, "$salida,$girar,$destino")))
        assertEquals(4, ruta.puntos.size)
        assertEquals(puntos[2].lat, ruta.puntos[2].lat, 1e-9)
        assertEquals(3, ruta.maniobras.size)
        val primera = ruta.maniobras[0]
        assertEquals(TipoManiobra.SALIDA, primera.tipo)
        assertEquals(listOf("Carrer de Sant Miquel"), primera.vias)
        assertEquals(0, primera.inicioIndice)
        assertEquals(1, primera.finIndice)
        assertEquals(150.0, primera.longitudM, 1e-6)
        assertEquals(20.0, primera.tiempoS, 1e-9)
        assertNull(primera.salidaRotonda)
        val giro = ruta.maniobras[1]
        assertEquals(TipoManiobra.DERECHA, giro.tipo)
        assertEquals("Gira a la derecha por Ma-10.", giro.instruccionValhalla)
        assertEquals(listOf("Ma-10", "Carretera de Valldemossa"), giro.vias)
        assertEquals(1, giro.inicioIndice)
        assertEquals(3, giro.finIndice)
        assertEquals(350.0, giro.longitudM, 1e-6)
        assertEquals(40.5, giro.tiempoS, 1e-9)
        assertEquals(TipoManiobra.DESTINO, ruta.maniobras[2].tipo)
        assertTrue(ruta.maniobras[2].vias.isEmpty())
        assertEquals(500.0, ruta.longitudM, 1e-6)
        assertEquals(60.5, ruta.tiempoS, 1e-9)
    }

    @Test
    fun `lee las salidas de rotonda`() {
        val rotonda =
            """{"type": 26, "instruction": "Entra en la rotonda.", "street_names": ["Ma-13"], "time": 5.0, "length": 0.05,
                "begin_shape_index": 1, "end_shape_index": 2, "roundabout_exit_count": 3}"""
        val ruta = LectorRutaValhalla.leer(respuesta(leg(puntos, "$salida,$rotonda,$destino")))
        assertEquals(TipoManiobra.ROTONDA_ENTRADA, ruta.maniobras[1].tipo)
        assertEquals(3, ruta.maniobras[1].salidaRotonda)
    }

    @Test
    fun `concatena legs sin repetir el punto de union y desplaza los indices`() {
        val primera = puntos.take(3)
        val segunda = puntos.drop(2)
        val legA =
            leg(
                primera,
                man(1, "A", 0.1, 0, 2),
            )
        val legB =
            leg(
                segunda,
                man(15, "B", 0.2, 0, 1) + "," + man(4, "C", 0.0, 1, 1),
            )
        val ruta = LectorRutaValhalla.leer(respuesta(legA, legB))
        assertEquals(4, ruta.puntos.size)
        assertEquals(listOf(0 to 2, 2 to 3, 3 to 3), ruta.maniobras.map { it.inicioIndice to it.finIndice })
        assertEquals(listOf("A", "B", "C"), ruta.maniobras.map { it.instruccionValhalla })
        assertEquals(300.0, ruta.longitudM, 1e-6)
        assertEquals(30.0, ruta.tiempoS, 1e-9)
    }

    @Test
    fun `json roto es ruta invalida`() {
        assertInvalida("esto no es json")
        assertInvalida("[]")
        assertInvalida("{}")
        assertInvalida("""{"trip": {"legs": []}}""")
    }

    @Test
    fun `error de valhalla es ruta invalida con su mensaje`() {
        val e =
            org.junit.Assert.assertThrows(RutaInvalida::class.java) {
                LectorRutaValhalla.leer(
                    """{"error_code": 171, "error": "No suitable edges near location", "status_code": 400}""",
                )
            }
        assertTrue(e.message.orEmpty().contains("No suitable edges"))
    }

    @Test
    fun `indices fuera de rango o maniobra sin campos son ruta invalida`() {
        val fuera = man(1, "x", 0.1, 0, 9)
        assertInvalida(respuesta(leg(puntos, fuera)))
        val invertido = man(1, "x", 0.1, 3, 1)
        assertInvalida(respuesta(leg(puntos, invertido)))
        assertInvalida(respuesta(leg(puntos, """{"type": 1, "instruction": "x"}""")))
    }

    @Test
    fun `shape con menos de dos puntos es ruta invalida`() {
        assertInvalida(respuesta(leg(puntos.take(1), "")))
    }

    private fun assertInvalida(texto: String) {
        org.junit.Assert.assertThrows(RutaInvalida::class.java) { LectorRutaValhalla.leer(texto) }
    }
}
