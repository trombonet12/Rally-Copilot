package app.trombito.rallycopilot.motor.vias

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/** Niveles (puentes, túneles, layer) y número de salida de las rotondas sobre el grafo, con geometrías sintéticas. */
class GrafoViasRotondasTest {
    private val metrosPorGradoLat = 111_194.9
    private val metrosPorGradoLon = metrosPorGradoLat * cos(Math.toRadians(39.6))

    private fun p(
        este: Double,
        norte: Double,
    ) = PuntoGeo(39.6 + norte / metrosPorGradoLat, 2.9 + este / metrosPorGradoLon)

    private fun via(
        id: Long,
        clase: String,
        sentido: SentidoUnico,
        vararg puntos: PuntoGeo,
    ) = Via(id, clase, null, null, null, sentido, null, null, puntos.toList())

    // --- Niveles: puentes y túneles no se cruzan con lo que pasa por debajo -----------------------------------

    private fun conNivel(
        id: Long,
        clase: String,
        bridge: String?,
        vararg puntos: PuntoGeo,
    ) = Via(id, clase, null, null, null, SentidoUnico.NO, null, null, puntos.toList(), bridge = bridge)

    @Test
    fun `un puente que comparte nodo con la carretera de debajo no es un cruce`() {
        val carretera = conNivel(1, "secondary", null, p(-500.0, 0.0), p(0.0, 0.0), p(500.0, 0.0))
        val puente = conNivel(2, "motorway", "yes", p(0.0, -300.0), p(0.0, 0.0), p(0.0, 300.0))
        val polilinea = listOf(p(-500.0, 0.0), p(500.0, 0.0))
        assertTrue(GrafoVias(listOf(carretera, puente)).cruces(polilinea).isEmpty())
        // Con las dos al mismo nivel sí es un cruce.
        val llana = conNivel(2, "motorway", null, p(0.0, -300.0), p(0.0, 0.0), p(0.0, 300.0))
        assertEquals(1, GrafoVias(listOf(carretera, llana)).cruces(polilinea).size)
    }

    @Test
    fun `el layer distinto tambien separa los niveles`() {
        val a = conNivel(1, "secondary", null, p(-500.0, 0.0), p(0.0, 0.0), p(500.0, 0.0))
        val b = conNivel(2, "secondary", null, p(0.0, -300.0), p(0.0, 0.0), p(0.0, 300.0)).copy(layer = -1)
        assertTrue(GrafoVias(listOf(a, b)).cruces(listOf(p(-500.0, 0.0), p(500.0, 0.0))).isEmpty())
    }

    @Test
    fun `donde la carretera entra en el puente un ramal sigue siendo un cruce`() {
        val antes = conNivel(1, "secondary", null, p(-500.0, 0.0), p(0.0, 0.0))
        val puente = conNivel(2, "secondary", "yes", p(0.0, 0.0), p(500.0, 0.0))
        val ramal = conNivel(3, "tertiary", null, p(0.0, 0.0), p(0.0, 200.0))
        val cruces = GrafoVias(listOf(antes, puente, ramal)).cruces(listOf(p(-500.0, 0.0), p(500.0, 0.0)))
        assertEquals(1, cruces.size)
    }

    @Test
    fun `conectadas es verdadero solo si las vias comparten un nodo`() {
        val a = via(1, "secondary", SentidoUnico.NO, p(0.0, 0.0), p(100.0, 0.0))
        val b = via(2, "secondary", SentidoUnico.NO, p(100.0, 0.0), p(100.0, 100.0))
        val c = via(3, "secondary", SentidoUnico.NO, p(0.0, 50.0), p(200.0, 50.0))
        val grafo = GrafoVias(listOf(a, b, c))
        assertTrue(grafo.conectadas(a, b))
        assertTrue(grafo.conectadas(b, a))
        assertTrue(grafo.conectadas(a, a))
        assertTrue(!grafo.conectadas(a, c))
        assertTrue(!grafo.conectadas(b, c))
    }

    // --- Rotondas: número de salida contado sobre el anillo -------------------------------------------------------

    private val radioAnillo = 30.0

    /** Nodo k del anillo (0 = este), cada 45 grados en sentido antihorario. */
    private fun nodo(k: Int) =
        p(
            radioAnillo * cos(Math.toRadians(45.0 * k)),
            radioAnillo * sin(Math.toRadians(45.0 * k)),
        )

    /** Punto a 100 m del centro en la dirección del nodo k: el otro extremo de un ramal. */
    private fun lejos(k: Int) = p(100.0 * cos(Math.toRadians(45.0 * k)), 100.0 * sin(Math.toRadians(45.0 * k)))

    private fun ramal(k: Int) = via(200L + k, "tertiary", SentidoUnico.NO, nodo(k), lejos(k))

    private fun anillo(
        id: Long,
        sentido: SentidoUnico,
        junction: String?,
        indices: IntRange,
    ) = Via(
        id,
        "residential",
        null,
        null,
        null,
        sentido,
        null,
        null,
        indices.map { nodo(it % 8) },
        junction = junction,
    )

    /** Anillo cerrado de 8 nodos con ramales de dos sentidos en los nodos de [brazos]. */
    private fun rotonda(
        brazos: List<Int>,
        junction: String? = "roundabout",
        sentido: SentidoUnico = SentidoUnico.NO,
        extra: List<Via> = emptyList(),
    ) = GrafoVias(listOf(anillo(100, sentido, junction, 0..8)) + brazos.map { ramal(it) } + extra)

    @Test
    fun `cuenta las salidas de una rotonda de cuatro ramales desde la entrada sur`() {
        val grafo = rotonda(listOf(6, 0, 2, 4))
        // Entra por el sur (nodo 6) y gira en sentido antihorario: este, norte, oeste y vuelta al sur.
        assertEquals(1, grafo.ordinalSalidaRotonda(nodo(6), nodo(0)))
        assertEquals(2, grafo.ordinalSalidaRotonda(nodo(6), nodo(2)))
        assertEquals(3, grafo.ordinalSalidaRotonda(nodo(6), nodo(4)))
        assertEquals(4, grafo.ordinalSalidaRotonda(nodo(6), nodo(6)))
    }

    @Test
    fun `una rotonda de cinco ramales cuenta todos`() {
        val grafo = rotonda(listOf(6, 0, 2, 4, 5))
        assertEquals(1, grafo.ordinalSalidaRotonda(nodo(6), nodo(0)))
        assertEquals(2, grafo.ordinalSalidaRotonda(nodo(6), nodo(2)))
        assertEquals(3, grafo.ordinalSalidaRotonda(nodo(6), nodo(4)))
        assertEquals(4, grafo.ordinalSalidaRotonda(nodo(6), nodo(5)))
        assertEquals(5, grafo.ordinalSalidaRotonda(nodo(6), nodo(6)))
    }

    @Test
    fun `una entrada de sentido unico hacia la rotonda no cuenta y una salida si`() {
        // Dibujada desde fuera hacia el anillo con oneway=yes: solo entra.
        val soloEntra =
            rotonda(listOf(6, 0, 2, 4), extra = listOf(via(300, "tertiary", SentidoUnico.ADELANTE, lejos(1), nodo(1))))
        assertEquals(2, soloEntra.ordinalSalidaRotonda(nodo(6), nodo(2)))
        // Dibujada desde el anillo hacia fuera con oneway=yes: solo sale, y es una salida más.
        val soloSale =
            rotonda(listOf(6, 0, 2, 4), extra = listOf(via(301, "tertiary", SentidoUnico.ADELANTE, nodo(1), lejos(1))))
        assertEquals(3, soloSale.ordinalSalidaRotonda(nodo(6), nodo(2)))
        assertEquals(2, soloSale.ordinalSalidaRotonda(nodo(6), nodo(1)))
        // oneway=-1 sobre la dibujada hacia fuera equivale a entrar solamente.
        val atras =
            rotonda(listOf(6, 0, 2, 4), extra = listOf(via(302, "tertiary", SentidoUnico.ATRAS, nodo(1), lejos(1))))
        assertEquals(2, atras.ordinalSalidaRotonda(nodo(6), nodo(2)))
    }

    @Test
    fun `respeta el sentido del anillo dibujado al reves con oneway -1`() {
        val grafo = rotonda(listOf(6, 0, 2, 4), sentido = SentidoUnico.ATRAS)
        // Va en sentido de índices decrecientes: del nodo 6 al 5, 4 (oeste, 1.ª), 2 (norte, 2.ª), 0 (este, 3.ª).
        assertEquals(1, grafo.ordinalSalidaRotonda(nodo(6), nodo(4)))
        assertEquals(2, grafo.ordinalSalidaRotonda(nodo(6), nodo(2)))
        assertEquals(3, grafo.ordinalSalidaRotonda(nodo(6), nodo(0)))
    }

    @Test
    fun `un anillo partido en dos vias se recorre de una a otra`() {
        val primera = anillo(101, SentidoUnico.ADELANTE, "roundabout", 0..4)
        val segunda = anillo(102, SentidoUnico.ADELANTE, "roundabout", 4..8)
        val grafo = GrafoVias(listOf(primera, segunda) + listOf(6, 0, 2, 4).map { ramal(it) })
        assertEquals(2, grafo.ordinalSalidaRotonda(nodo(6), nodo(2)))
        assertEquals(3, grafo.ordinalSalidaRotonda(nodo(6), nodo(4)))
        // Y a la inversa, de la segunda a la primera.
        assertEquals(1, grafo.ordinalSalidaRotonda(nodo(2), nodo(4)))
        assertEquals(2, grafo.ordinalSalidaRotonda(nodo(2), nodo(6)))
    }

    @Test
    fun `tolera unos metros entre el punto de la ruta y el nodo del anillo`() {
        val grafo = rotonda(listOf(6, 0, 2, 4))
        val casi = p(3.0, -radioAnillo + 2.0)
        assertEquals(2, grafo.ordinalSalidaRotonda(casi, nodo(2)))
        assertNull(grafo.ordinalSalidaRotonda(p(500.0, 500.0), nodo(2)))
        assertNull(grafo.ordinalSalidaRotonda(nodo(6), p(500.0, 500.0)))
    }

    @Test
    fun `con datos sin junction un lazo pequeno cuenta como rotonda y con junction no`() {
        assertEquals(2, rotonda(listOf(6, 0, 2, 4), junction = null).ordinalSalidaRotonda(nodo(6), nodo(2)))
        // Hay otra rotonda etiquetada: los datos ya traen junction y el lazo sin etiqueta no es un anillo.
        val otra =
            Via(
                900,
                "residential",
                null,
                null,
                null,
                SentidoUnico.ADELANTE,
                null,
                null,
                listOf(p(900.0, 900.0), p(900.0, 910.0)),
                junction = "roundabout",
            )
        val grafo = rotonda(listOf(6, 0, 2, 4), junction = null, extra = listOf(otra))
        assertNull(grafo.ordinalSalidaRotonda(nodo(6), nodo(2)))
    }
}
