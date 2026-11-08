package app.trombito.rallycopilot.data.alertas

import app.trombito.rallycopilot.domain.alertas.AlertasEnCurso
import app.trombito.rallycopilot.domain.alertas.ExcesoEnCurso
import app.trombito.rallycopilot.domain.alertas.IncidenciasVigentes
import app.trombito.rallycopilot.domain.alertas.RadarProximo
import app.trombito.rallycopilot.domain.alertas.RepositorioIncidencias
import app.trombito.rallycopilot.domain.vias.ProveedorViaActual
import app.trombito.rallycopilot.domain.vias.ViaActual
import app.trombito.rallycopilot.motor.alertas.Incidencia
import app.trombito.rallycopilot.motor.alertas.Radar
import app.trombito.rallycopilot.motor.alertas.TipoIncidencia
import app.trombito.rallycopilot.motor.alertas.TipoRadar
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.SentidoCamino
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import app.trombito.rallycopilot.motor.voz.Frase
import app.trombito.rallycopilot.motor.voz.TipoFrase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.math.cos

private const val METROS_POR_GRADO = PuntoGeo.RADIO_TIERRA_M * Math.PI / 180.0

/** Punto a [norte] metros al norte y [este] al este del origen de las pruebas. */
private fun punto(
    norte: Double,
    este: Double = 0.0,
) = PuntoGeo(
    39.6 + norte / METROS_POR_GRADO,
    2.9 + este / (METROS_POR_GRADO * cos(Math.toRadians(39.6))),
)

/** Ruta recta al norte de 6 km con un vértice cada 500 m. */
private fun rutaNorte(): List<PuntoGeo> = (0..12).map { punto(it * 500.0) }

private fun fijacion(
    p: PuntoGeo,
    segundo: Int,
    velocidadMs: Double = 20.0,
    rumbo: Double? = 0.0,
) = Fijacion(p, segundo * 1000L, velocidadMs, rumbo, 5.0)

private fun radarFijo(
    id: String,
    norte: Double,
    limite: Int? = 80,
    este: Double = 0.0,
) = Radar(id, TipoRadar.FIJO, punto(norte, este), null, limite, null, "test")

internal class RadaresFalso(
    private val lista: List<Radar>,
) : FuenteRadares {
    override suspend fun radares() = lista
}

internal class IncidenciasFalso : RepositorioIncidencias {
    val estado = MutableStateFlow(IncidenciasVigentes())
    var refrescos = 0
    override val incidencias: StateFlow<IncidenciasVigentes> = estado

    override suspend fun refrescar() {
        refrescos++
    }
}

internal class ViaFalsa(
    var via: ViaActual? = null,
) : ProveedorViaActual {
    override suspend fun viaEn(fijacion: Fijacion) = via
}

@OptIn(ExperimentalCoroutinesApi::class)
class SesionAlertasTest {
    private class Escenario(
        val sesion: SesionAlertas,
        val publicadas: MutableList<AlertasEnCurso?>,
        val incidencias: IncidenciasFalso,
        val via: ViaFalsa,
        val reloj: () -> Long,
    ) {
        val ultima: AlertasEnCurso get() = checkNotNull(publicadas.last())
    }

    private fun TestScope.escenario(
        radares: List<Radar> = emptyList(),
        ruta: List<PuntoGeo>? = rutaNorte(),
        via: ViaActual? = null,
        ahora: () -> Long = { AHORA_MS },
    ): Escenario {
        val publicadas = mutableListOf<AlertasEnCurso?>()
        val incidencias = IncidenciasFalso()
        val viaFalsa = ViaFalsa(via)
        val sesion = SesionAlertas(RadaresFalso(radares), incidencias, viaFalsa, { publicadas += it }, ahora)
        sesion.arrancar(backgroundScope, ruta)
        runCurrent()
        return Escenario(sesion, publicadas, incidencias, viaFalsa, ahora)
    }

    @Test
    fun `guiado avisa del radar fijo con la ruta y publica el radar proximo`() =
        runTest {
            val e = escenario(radares = listOf(radarFijo("f1", 2000.0)))

            val lejos = e.sesion.alFijar(fijacion(punto(1000.0), 0), 20.0, 1000.0, null)
            assertTrue(lejos.isEmpty())
            assertNull(e.ultima.radarProximo)

            val cerca = e.sesion.alFijar(fijacion(punto(1750.0), 1), 20.0, 1750.0, null)

            val frase = cerca.single()
            assertEquals("radar-f1", frase.id)
            assertEquals(TipoFrase.ALERTA, frase.tipo)
            assertTrue(frase.texto, frase.texto.startsWith("radar a ") && frase.texto.endsWith("ochenta"))
            assertEquals(2000.0, checkNotNull(frase.puntoM), 2.0)
            val proximo = checkNotNull(e.ultima.radarProximo)
            assertEquals(TipoRadar.FIJO, proximo.tipo)
            assertEquals(250.0, proximo.distanciaM, 2.0)
            assertEquals(80, proximo.limiteKmh)
        }

    @Test
    fun `al pasar un radar fijo publica la velocidad de paso unos 4 segundos`() =
        runTest {
            val e = escenario(radares = listOf(radarFijo("f1", 2000.0, limite = 50)))
            val posiciones = listOf(1950.0, 1970.0, 1990.0, 2010.0, 2030.0, 2050.0, 2070.0, 2090.0, 2110.0)
            val pasos =
                posiciones.mapIndexed { i, m ->
                    e.sesion.alFijar(fijacion(punto(m), i), 20.0, m, null)
                    e.ultima.pasoRadar
                }
            // El paso se detecta en la primera fijación que ya se aleja (2030 m, i = 4) y dura 4 s (hasta i = 7).
            assertEquals(List(4) { null }, pasos.take(4))
            val paso = checkNotNull(pasos[4])
            assertEquals("f1", paso.radarId)
            assertEquals(72.0, paso.velocidadKmh, 0.01)
            assertEquals(50, paso.limiteKmh)
            assertEquals(paso, pasos[7])
            assertNull(pasos[8])
        }

    @Test
    fun `un fijo del sentido contrario se avisa como tal y el proximo lo marca`() =
        runTest {
            val contrario = radarFijo("f1", 2000.0).copy(rumboGrados = 180.0)
            val e = escenario(radares = listOf(contrario))
            val frases = e.sesion.alFijar(fijacion(punto(1750.0), 0), 20.0, 1750.0, null)
            assertEquals("radar, sentido contrario", frases.single().texto)
            val proximo = checkNotNull(e.ultima.radarProximo)
            assertEquals("f1", proximo.id)
            assertTrue(proximo.sentidoContrario)
        }

    @Test
    fun `a menos de 100 m suena el segundo aviso del radar fijo`() =
        runTest {
            val e = escenario(radares = listOf(radarFijo("f1", 2000.0)))
            val frases = e.sesion.alFijar(fijacion(punto(1930.0), 0), 20.0, 1930.0, null)
            assertEquals("radar-f1-cerca", frases.single().id)
            assertEquals("f1", checkNotNull(e.ultima.radarProximo).id)
        }

    @Test
    fun `sin radares no hay avisos ni error y el exceso sigue funcionando`() =
        runTest {
            val e = escenario(radares = emptyList(), ruta = null, via = ViaActual("Ma-1", "Ma-1", 50))

            // El exceso se publica al instante; el aviso de voz espera a 4 s seguidos.
            val primera = e.sesion.alFijar(fijacion(punto(500.0), 0), 20.0, null, null)
            assertTrue(primera.isEmpty())
            assertNull(e.ultima.radarProximo)
            assertEquals(ExcesoEnCurso(50, rojo = true, exceso = true), e.ultima.exceso)
        }

    @Test
    fun `el exceso avisa tras 4 s seguidos, con el mismo id mientras dura y no se repite en 90 s`() =
        runTest {
            val e = escenario(via = ViaActual(null, null, 50))
            // 72 km/h durante 6 s: el aviso llega a los 4 s y mantiene el id.
            val frases = (0..5).flatMap { s -> e.sesion.alFijar(fijacion(punto(s * 20.0), s), 20.0, s * 20.0, null) }
            assertEquals(List(2) { "exceso-1" }, frases.map { it.id })
            assertEquals("límite cincuenta", frases.last().texto)

            // 45 km/h durante 12 s corta el episodio; otro exceso sostenido a menos de 90 s no avisa.
            for (s in 6..17) e.sesion.alFijar(fijacion(punto(s * 12.0), s, 12.5), 12.5, s * 12.0, null)
            assertEquals(ExcesoEnCurso(50, rojo = false, exceso = false), e.ultima.exceso)
            val nuevo = (18..24).flatMap { s -> e.sesion.alFijar(fijacion(punto(s * 20.0), s), 20.0, s * 20.0, null) }
            assertTrue(nuevo.isEmpty())
        }

    @Test
    fun `entre el limite y el diez por ciento va en rojo pero sin aviso`() =
        runTest {
            val e = escenario(via = ViaActual(null, null, 50))

            val frases = e.sesion.alFijar(fijacion(punto(100.0), 0, velocidadMs = 14.7), 14.7, 100.0, null)

            assertTrue(frases.isEmpty())
            assertEquals(ExcesoEnCurso(50, rojo = true, exceso = false), e.ultima.exceso)
        }

    @Test
    fun `en libre avisa del radar que queda delante en linea recta con el eje de las notas`() =
        runTest {
            val e = escenario(radares = listOf(radarFijo("f1", 1000.0)), ruta = null)

            val frases = e.sesion.alFijar(fijacion(punto(800.0), 0), 20.0, null, 340.0)

            val frase = frases.single()
            assertEquals("radar-f1", frase.id)
            assertEquals(540.0, checkNotNull(frase.puntoM), 2.0)
            val proximo: RadarProximo = checkNotNull(e.ultima.radarProximo)
            assertEquals(TipoRadar.FIJO, proximo.tipo)
            assertEquals(200.0, proximo.distanciaM, 2.0)
        }

    @Test
    fun `en libre sin notas el eje de las frases arranca en cero`() =
        runTest {
            val e = escenario(radares = listOf(radarFijo("f1", 1000.0)), ruta = null)

            val frase = e.sesion.alFijar(fijacion(punto(800.0), 0), 20.0, null, null).single()

            assertEquals(200.0, checkNotNull(frase.puntoM), 2.0)
        }

    // Vía en L: 1 km al norte y luego 1 km al este; el radar está en el tramo este, fuera de la recta del coche.
    private fun vial(
        invertida: Boolean,
        sentido: SentidoCamino,
    ): ViaActual {
        val ida = listOf(punto(0.0), punto(1000.0), punto(1000.0, 1000.0))
        return ViaActual(null, null, null, if (invertida) ida.reversed() else ida, sentido)
    }

    private fun radarEnElCodo() = radarFijo("curva", 1000.0, este = 300.0)

    @Test
    fun `en libre usa la geometria de la via para ver un radar tras la curva`() =
        runTest {
            val e = escenario(radares = listOf(radarEnElCodo()), ruta = null, via = vial(false, SentidoCamino.AMBOS))

            val frase = e.sesion.alFijar(fijacion(punto(920.0), 0, 29.0), 29.0, null, 100.0).single()

            assertEquals("radar-curva", frase.id)
            // Faltan 80 m hasta la curva y 300 m del tramo este: 380 m por la vía.
            assertEquals(480.0, checkNotNull(frase.puntoM), 3.0)
            assertEquals(380.0, checkNotNull(e.ultima.radarProximo).distanciaM, 3.0)
        }

    @Test
    fun `en libre orienta la via en doble sentido segun el rumbo del coche`() =
        runTest {
            // La misma vía dibujada al revés: el coche va hacia el norte, contra el orden de los puntos.
            val e = escenario(radares = listOf(radarEnElCodo()), ruta = null, via = vial(true, SentidoCamino.AMBOS))

            val frase = e.sesion.alFijar(fijacion(punto(920.0), 0, 29.0), 29.0, null, 100.0).single()

            assertEquals("radar-curva", frase.id)
            assertEquals(480.0, checkNotNull(frase.puntoM), 3.0)
        }

    @Test
    fun `en libre una via de sentido unico dibujada al reves se recorre contra el orden`() =
        runTest {
            val e = escenario(radares = listOf(radarEnElCodo()), ruta = null, via = vial(true, SentidoCamino.ATRAS))

            val frase = e.sesion.alFijar(fijacion(punto(920.0), 0, 29.0), 29.0, null, 100.0).single()

            assertEquals(480.0, checkNotNull(frase.puntoM), 3.0)
        }

    @Test
    fun `en libre yendo en sentido contrario al de la curva no avisa de ese radar`() =
        runTest {
            val e = escenario(radares = listOf(radarEnElCodo()), ruta = null, via = vial(false, SentidoCamino.AMBOS))

            val frases = e.sesion.alFijar(fijacion(punto(920.0), 0, 29.0, rumbo = 180.0), 29.0, null, 100.0)

            assertTrue(frases.isEmpty())
        }

    // Predicción en L: 200 m al norte y luego al este. La vía actual (OSM) acaba en el codo, así que el radar del
    // segundo tramo (150 m al este del codo) ni está en la vía ni en la recta del coche.
    private fun prediccionEnL() = listOf(punto(0.0), punto(200.0), punto(200.0, 1000.0))

    private fun viaCortaHastaElCodo() =
        ViaActual(null, null, null, listOf(punto(0.0), punto(200.0)), SentidoCamino.AMBOS)

    @Test
    fun `en libre con prediccion avisa del radar del segundo tramo a la distancia de aviso`() =
        runTest {
            val e =
                escenario(
                    radares = listOf(radarFijo("p1", 200.0, este = 150.0)),
                    ruta = null,
                    via = viaCortaHastaElCodo(),
                )

            // 100 km/h (27,8 m/s): aviso desde 400 m; el radar queda a 350 m por la predicción.
            val frase = e.sesion.alFijar(fijacion(punto(0.0), 0, 27.8), 27.8, null, 0.0, prediccionEnL()).single()

            assertEquals("radar-p1", frase.id)
            assertEquals(350.0, checkNotNull(frase.puntoM), 3.0)
            assertEquals(350.0, checkNotNull(e.ultima.radarProximo).distanciaM, 3.0)
        }

    @Test
    fun `en libre sin prediccion el radar del segundo tramo no se ve todavia`() =
        runTest {
            val e =
                escenario(
                    radares = listOf(radarFijo("p1", 200.0, este = 150.0)),
                    ruta = null,
                    via = viaCortaHastaElCodo(),
                )

            val frases = e.sesion.alFijar(fijacion(punto(0.0), 0, 27.8), 27.8, null, 0.0, null)

            assertTrue(frases.isEmpty())
        }

    @Test
    fun `en libre con prediccion avisa de las incidencias de la carretera predicha`() =
        runTest {
            val e = escenario(ruta = null)
            e.incidencias.estado.value =
                IncidenciasVigentes(listOf(incidencia("i1", 200.0, 150.0)), Instant.ofEpochMilli(AHORA_MS), null)
            runCurrent()

            val frases = e.sesion.alFijar(fijacion(punto(0.0), 0, 27.8), 27.8, null, 0.0, prediccionEnL())

            assertEquals(listOf("incidencia-i1"), frases.map { it.id })
            assertEquals(350.0, checkNotNull(frases.single().puntoM), 3.0)
            assertTrue(
                e.ultima.incidencias
                    .single()
                    .enRuta,
            )
        }

    @Test
    fun `guiado mide la media del tramo y avisa una sola vez mientras la media pasa del limite`() =
        runTest {
            val inicio = Radar("t1-i", TipoRadar.TRAMO_INICIO, punto(1000.0), null, 50, "t1", "test")
            val fin = Radar("t1-f", TipoRadar.TRAMO_FIN, punto(3000.0), null, 50, "t1", "test")
            val e = escenario(radares = listOf(inicio, fin))
            val dichas = mutableListOf<Frase>()

            // 30 m/s (108 km/h) cada segundo desde 800 m: entrada, y media por encima de 50 km/h.
            for (s in 0..12) {
                val p = 800.0 + s * 30.0
                dichas += e.sesion.alFijar(fijacion(punto(p), s, 30.0), 30.0, p, null)
            }

            assertTrue(dichas.any { it.id == "radar-t1-i" && it.texto.startsWith("tramo de radar") })
            assertEquals(
                listOf("tramo-media-t1-1"),
                dichas.map { it.id }.filter { it.startsWith("tramo-media") }.distinct(),
            )
            val tramo = checkNotNull(e.ultima.tramo)
            assertEquals(108.0, checkNotNull(tramo.mediaKmh), 1.0)
            assertEquals(50, tramo.limiteKmh)
            assertTrue(tramo.superaLimite)
        }

    @Test
    fun `en libre mide la media del tramo entre el radar de inicio y el de fin`() =
        runTest {
            val inicio = Radar("t1-i", TipoRadar.TRAMO_INICIO, punto(1000.0), null, 50, "t1", "test")
            val fin = Radar("t1-f", TipoRadar.TRAMO_FIN, punto(3000.0), null, 50, "t1", "test")
            val e = escenario(radares = listOf(inicio, fin), ruta = null)

            // Dentro del tramo: la media ya se publica y supera el limite.
            for (s in 0..12) e.sesion.alFijar(fijacion(punto(800.0 + s * 30.0), s, 30.0), 30.0, null, null)
            val enCurso = checkNotNull(e.ultima.tramo)
            assertEquals(108.0, checkNotNull(enCurso.mediaKmh), 1.0)
            assertEquals(50, enCurso.limiteKmh)
            assertTrue(enCurso.superaLimite)
            assertTrue(!enCurso.terminado)

            // Al pasar por el radar de fin se cierra y se retiene la media final unos segundos.
            for (s in 13..78) e.sesion.alFijar(fijacion(punto(800.0 + s * 30.0), s, 30.0), 30.0, null, null)
            val final = checkNotNull(e.ultima.tramo)
            assertTrue(final.terminado)
            assertEquals(108.0, checkNotNull(final.mediaKmh), 1.0)
        }

    @Test
    fun `en libre sin pasar por el inicio no hay media de tramo`() =
        runTest {
            val inicio = Radar("t1-i", TipoRadar.TRAMO_INICIO, punto(1000.0), null, 50, "t1", "test")
            val fin = Radar("t1-f", TipoRadar.TRAMO_FIN, punto(3000.0), null, 50, "t1", "test")
            val e = escenario(radares = listOf(inicio, fin), ruta = null)

            for (s in 0..12) e.sesion.alFijar(fijacion(punto(1200.0 + s * 30.0), s, 30.0), 30.0, null, null)

            assertNull(e.ultima.tramo)
        }

    private fun incidencia(
        id: String,
        norte: Double,
        este: Double = 0.0,
        fin: Instant? = null,
    ) = Incidencia(id, TipoIncidencia.OBRAS, listOf(punto(norte, este)), "Ma-1", "Obras en Ma-1", null, fin, null)

    @Test
    fun `refresca las incidencias al empezar y avisa de las de la ruta`() =
        runTest {
            val e = escenario()
            e.incidencias.estado.value =
                IncidenciasVigentes(listOf(incidencia("i1", 3000.0)), Instant.ofEpochMilli(AHORA_MS - 120_000), null)
            runCurrent()

            val frases = e.sesion.alFijar(fijacion(punto(2750.0), 0), 20.0, 2750.0, null)

            assertEquals(1, e.incidencias.refrescos)
            assertEquals(listOf("incidencia-i1"), frases.map { it.id })
            assertTrue(frases.single().texto, frases.single().texto.startsWith("obras"))
            val relevante = e.ultima.incidencias.single()
            assertTrue(relevante.enRuta)
            assertEquals(120_000L, e.ultima.antiguedadIncidenciasMs)
        }

    @Test
    fun `en libre las incidencias cercanas van al mapa pero no se cantan`() =
        runTest {
            val e = escenario(ruta = null)
            e.incidencias.estado.value = IncidenciasVigentes(listOf(incidencia("cerca", 1500.0, 0.0)), null, null)
            runCurrent()

            val frases = e.sesion.alFijar(fijacion(punto(1400.0), 0), 20.0, null, null)

            assertTrue(frases.isEmpty())
            assertEquals(listOf("cerca"), e.ultima.incidencias.map { it.incidencia.id })
            assertNull(e.ultima.antiguedadIncidenciasMs)
        }

    @Test
    fun `las incidencias caducadas se retiran aunque el feed no cambie`() =
        runTest {
            var ahora = AHORA_MS
            val e = escenario(ruta = null, ahora = { ahora })
            e.incidencias.estado.value =
                IncidenciasVigentes(
                    listOf(incidencia("i1", 1500.0, fin = Instant.ofEpochMilli(AHORA_MS + 30_000))),
                    null,
                    null,
                )
            runCurrent()
            e.sesion.alFijar(fijacion(punto(1400.0), 0), 20.0, null, null)
            assertEquals(1, e.ultima.incidencias.size)

            ahora += 120_000
            e.sesion.alFijar(fijacion(punto(1420.0), 1), 20.0, null, null)

            assertTrue(e.ultima.incidencias.isEmpty())
        }

    @Test
    fun `cerrar publica sin alertas`() =
        runTest {
            val e = escenario()
            e.sesion.alFijar(fijacion(punto(100.0), 0), 20.0, 100.0, null)
            assertNotNull(e.publicadas.last())

            e.sesion.cerrar()

            assertNull(e.publicadas.last())
        }

    private companion object {
        const val AHORA_MS = 1_793_000_000_000L
    }
}
