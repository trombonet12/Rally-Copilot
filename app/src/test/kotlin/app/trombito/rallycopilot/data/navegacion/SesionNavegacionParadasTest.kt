package app.trombito.rallycopilot.data.navegacion

import app.trombito.rallycopilot.data.recorridos.PuntoRecorrido
import app.trombito.rallycopilot.data.recorridos.Recorrido
import app.trombito.rallycopilot.data.recorridos.RepositorioRecorridos
import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.navegacion.Destino
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.navegacion.OrdenParadas
import app.trombito.rallycopilot.domain.ruta.Enrutador
import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.domain.ruta.ResultadoRuta
import app.trombito.rallycopilot.domain.ubicacion.FuenteUbicacion
import app.trombito.rallycopilot.domain.voz.Locutor
import app.trombito.rallycopilot.domain.voz.VozCopiloto
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.ruta.Maniobra
import app.trombito.rallycopilot.motor.ruta.Ruta
import app.trombito.rallycopilot.motor.ruta.TipoManiobra
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import app.trombito.rallycopilot.motor.voz.ModoCopiloto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

private const val METROS_POR_GRADO_LAT = 111_194.9
private const val MONOTONO_BASE_MS = 5_000_000L

private fun p(
    este: Double,
    norte: Double,
) = PuntoGeo(
    39.6 + norte / METROS_POR_GRADO_LAT,
    2.9 + este / (METROS_POR_GRADO_LAT * cos(Math.toRadians(39.6))),
)

private class FuenteReplay(
    private val puntos: List<PuntoGeo>,
) : FuenteUbicacion {
    override fun fijaciones(): Flow<Fijacion> =
        flow {
            for ((i, punto) in puntos.withIndex()) {
                emit(Fijacion(punto, MONOTONO_BASE_MS + i * 1000L, 10.0, null, 5.0))
                delay(1000)
            }
            awaitCancellation()
        }

    override fun tienePermiso() = true
}

/** Responde con las rutas que se le pongan en orden y anota a qué paradas se las pidieron. */
private class EnrutadorConParadas(
    private val respuestas: MutableList<ResultadoRuta>,
) : Enrutador {
    class Peticion(
        val origen: PuntoGeo,
        val paradas: List<PuntoGeo>,
        val destino: PuntoGeo,
    )

    val peticiones = mutableListOf<Peticion>()

    override suspend fun ruta(
        origen: PuntoGeo,
        destino: PuntoGeo,
        perfil: PerfilRuta,
    ): ResultadoRuta = rutaConParadas(origen, emptyList(), destino, perfil)

    override suspend fun rutaConParadas(
        origen: PuntoGeo,
        paradas: List<PuntoGeo>,
        destino: PuntoGeo,
        perfil: PerfilRuta,
    ): ResultadoRuta {
        peticiones += Peticion(origen, paradas, destino)
        delay(200)
        return respuestas.removeAt(0)
    }
}

private class RecorridosMudo : RepositorioRecorridos {
    override suspend fun empezar(destinoNombre: String?) = 1L

    override suspend fun anadirPuntos(
        id: Long,
        puntos: List<PuntoRecorrido>,
    ) = Unit

    override suspend fun terminar(id: Long): Recorrido? = null

    override suspend fun idsEnCurso() = emptyList<Long>()

    override fun listar() = emptyFlow<List<Recorrido>>()

    override suspend fun puntos(id: Long) = emptyList<PuntoRecorrido>()

    override suspend fun borrar(id: Long) = Unit
}

private class LocutorGrabador : Locutor {
    override val hablando: StateFlow<Boolean> = MutableStateFlow(false)
    val dichas = mutableListOf<String>()

    override fun usarVoz(v: VozCopiloto) = Unit

    override fun preparar(textos: List<String>) = Unit

    override fun decir(texto: String) {
        dichas += texto
    }

    override fun duracionEstimadaS(texto: String) = 1.0

    override fun probar(v: VozCopiloto) = Unit

    override fun cerrar() = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
class SesionNavegacionParadasTest {
    private val parada1 = Destino("Parada 1", p(1000.0, 0.0))
    private val parada2 = Destino("Parada 2", p(1000.0, 500.0))
    private val final = Destino("Final", p(1000.0, 1000.0))

    private fun man(
        tipo: TipoManiobra,
        inicio: Int,
        fin: Int,
    ) = Maniobra(tipo, tipo.name, emptyList(), inicio, fin, 500.0, 50.0, null)

    /** 1000 m al este (parada 1), 500 m al norte (parada 2) y otros 500 m al norte (final): 3 tramos, 2 paradas. */
    private fun rutaConDosParadas() =
        Ruta(
            puntos = listOf(p(0.0, 0.0), parada1.punto, parada2.punto, final.punto),
            maniobras =
                listOf(
                    man(TipoManiobra.SALIDA, 0, 1),
                    man(TipoManiobra.DESTINO, 1, 1),
                    man(TipoManiobra.SALIDA, 1, 2),
                    man(TipoManiobra.DESTINO, 2, 2),
                    man(TipoManiobra.SALIDA, 2, 3),
                    man(TipoManiobra.DESTINO, 3, 3),
                ),
            longitudM = 2000.0,
            tiempoS = 200.0,
            indicesParadas = listOf(1, 2),
        )

    /** Ruta recalculada que sale de [origen] y pasa por [paradas] hasta el final. */
    private fun rutaDesde(
        origen: PuntoGeo,
        paradas: List<Destino>,
    ): Ruta {
        val puntos = listOf(origen) + paradas.map { it.punto } + final.punto
        val maniobras =
            listOf(man(TipoManiobra.SALIDA, 0, 1)) +
                paradas.indices.flatMap { i ->
                    listOf(man(TipoManiobra.DESTINO, i + 1, i + 1), man(TipoManiobra.SALIDA, i + 1, i + 2))
                } + man(TipoManiobra.DESTINO, puntos.lastIndex, puntos.lastIndex)
        return Ruta(puntos, maniobras, 2000.0, 200.0, paradas.indices.map { it + 1 })
    }

    /** Punto de la ruta a [s] metros de su inicio. */
    private fun enRuta(s: Double) = if (s <= 1000.0) p(s, 0.0) else p(1000.0, s - 1000.0)

    private class Escenario(
        val estados: MutableList<EstadoNavegacion>,
        val enrutador: EnrutadorConParadas,
        val locutor: LocutorGrabador,
        val ordenes: Channel<OrdenParadas>,
        val sesion: SesionNavegacion,
    ) {
        val guiando get() = estados.filterIsInstance<EstadoNavegacion.Guiando>().last()
    }

    private fun escenario(
        puntos: List<PuntoGeo>,
        respuestas: List<ResultadoRuta> = emptyList(),
    ): Escenario {
        val estados = mutableListOf<EstadoNavegacion>()
        val enrutador = EnrutadorConParadas(respuestas.toMutableList())
        val locutor = LocutorGrabador()
        val ordenes = Channel<OrdenParadas>(Channel.UNLIMITED)
        val sesion =
            SesionNavegacion(
                FuenteReplay(puntos),
                enrutador,
                RecorridosMudo(),
                reloj = { 0L },
                publicar = { estados += it },
                motivoSinGps = "sin GPS",
                copiloto =
                    CopilotoVoz(
                        locutor,
                        flowOf(AjustesConduccion(modoCopiloto = ModoCopiloto.COMPLETO, voz = VozCopiloto.DAVEFX)),
                    ),
                ordenes = ordenes,
            )
        return Escenario(estados, enrutador, locutor, ordenes, sesion)
    }

    private fun CoroutineScope.lanzar(
        e: Escenario,
        ruta: Ruta = rutaConDosParadas(),
        paradas: List<Destino> = listOf(parada1, parada2),
    ): Job = launch { e.sesion.correr(SolicitudNavegacion.Guiada(final, PerfilRuta.SINUOSA, ruta, paradas)) }

    @Test
    fun `replay de dos paradas las activa una tras otra y llega al final`() =
        runTest {
            // Un tren de fijaciones cada 50 m a lo largo de los 2000 m de ruta.
            val e = escenario((0..40).map { enRuta(it * 50.0) })
            val trabajo = lanzar(e)

            runCurrent()
            assertEquals(listOf(parada1, parada2), e.guiando.paradas)
            assertEquals(0, e.guiando.paradasHechas)
            assertEquals(parada1, e.guiando.proximaParada)

            // 9 fijaciones: 400 m. Faltan 600 m a la primera parada.
            advanceTimeBy(8_500)
            assertEquals(600.0, e.guiando.distanciaAProximaM!!, 5.0)
            assertEquals(30.0, e.guiando.tiempoAProximaS!!, 1.0)
            assertEquals(1600.0, e.guiando.progreso!!.distanciaRestanteM, 5.0)

            // 21 fijaciones: 1000 m, justo en la parada 1.
            advanceTimeBy(12_000)
            assertEquals(listOf(parada2), e.guiando.paradas)
            assertEquals(1, e.guiando.paradasHechas)
            assertEquals(parada2, e.guiando.proximaParada)
            assertTrue(e.locutor.dichas.any { it == "parada alcanzada, siguiente parada" })

            // 1500 m: parada 2; solo queda el final.
            advanceTimeBy(10_000)
            assertEquals(emptyList<Destino>(), e.guiando.paradas)
            assertEquals(2, e.guiando.paradasHechas)
            assertNull(e.guiando.proximaParada)
            assertNull(e.guiando.distanciaAProximaM)
            assertTrue(e.locutor.dichas.any { it == "parada alcanzada, siguiente destino" })
            assertFalse(e.estados.last() is EstadoNavegacion.Llegada)

            advanceTimeBy(10_000)
            assertEquals(EstadoNavegacion.Llegada(final), e.estados.last())
            trabajo.cancel()
        }

    @Test
    fun `pasar de largo por una parada la da por alcanzada y activa la siguiente`() =
        runTest {
            // La fijacion salta de 900 m a 1250 m sin pasar a menos de 30 m de la parada 1 (a 100 m / 250 m de ella).
            val e = escenario(listOf(enRuta(0.0), enRuta(900.0), p(1030.0, 250.0), p(1030.0, 300.0)))
            val trabajo = lanzar(e)

            advanceTimeBy(1_500)
            assertEquals(0, e.guiando.paradasHechas)
            advanceTimeBy(1_000)
            assertEquals(1, e.guiando.paradasHechas)
            assertEquals(listOf(parada2), e.guiando.paradas)
            assertEquals(parada2, e.guiando.proximaParada)
            trabajo.cancel()
        }

    @Test
    fun `un recalculo tras alcanzar la primera parada conserva solo la pendiente`() =
        runTest {
            val fuera = p(1300.0, 100.0)
            val nueva = rutaDesde(fuera, listOf(parada2))
            val e =
                escenario(
                    listOf(enRuta(0.0), enRuta(500.0), enRuta(985.0), p(1000.0, 20.0)) +
                        List(10) { fuera } + listOf(p(1010.0, 480.0), p(1000.0, 520.0)),
                    listOf(ResultadoRuta.Ok(nueva, 50)),
                )
            val trabajo = lanzar(e)

            advanceTimeBy(8_500)
            val peticion = e.enrutador.peticiones.single()
            assertEquals(listOf(parada2.punto), peticion.paradas)
            assertEquals(final.punto, peticion.destino)
            assertEquals(fuera.lat, peticion.origen.lat, 1e-6)
            assertEquals(listOf(parada2), e.guiando.paradas)
            assertEquals(1, e.guiando.paradasHechas)
            assertFalse(e.guiando.recalculando)

            // La parada 2 de la ruta nueva se alcanza a ~22 m.
            advanceTimeBy(6_000)
            assertEquals(2, e.guiando.paradasHechas)
            assertEquals(emptyList<Destino>(), e.guiando.paradas)
            trabajo.cancel()
        }

    @Test
    fun `anadir una parada recalcula desde la posicion actual con todas las pendientes en orden`() =
        runTest {
            val extra = Destino("Extra", p(1500.0, 300.0))
            val nueva = rutaDesde(enRuta(300.0), listOf(parada1, parada2, extra))
            val e = escenario(List(30) { enRuta(300.0) }, listOf(ResultadoRuta.Ok(nueva, 50)))
            val trabajo = lanzar(e)

            advanceTimeBy(1_500)
            e.ordenes.trySend(OrdenParadas.Anadir(extra))
            runCurrent()
            // De inmediato la UI ve la lista nueva y «recalculando».
            assertEquals(listOf(parada1, parada2, extra), e.guiando.paradas)
            assertTrue(e.guiando.recalculando)

            advanceTimeBy(1_000)
            val peticion = e.enrutador.peticiones.single()
            assertEquals(listOf(parada1.punto, parada2.punto, extra.punto), peticion.paradas)
            assertEquals(enRuta(300.0).lat, peticion.origen.lat, 1e-6)
            assertFalse(e.guiando.recalculando)
            assertEquals(listOf(parada1, parada2, extra), e.guiando.paradas)
            assertEquals(3, e.guiando.ruta.indicesParadas.size)
            trabajo.cancel()
        }

    @Test
    fun `quitar y mover paradas recalculan y una orden invalida no hace nada`() =
        runTest {
            val soloDos = rutaDesde(enRuta(300.0), listOf(parada2))
            val e = escenario(List(30) { enRuta(300.0) }, listOf(ResultadoRuta.Ok(soloDos, 50)))
            val trabajo = lanzar(e)

            advanceTimeBy(1_500)
            e.ordenes.trySend(OrdenParadas.Quitar(7))
            e.ordenes.trySend(OrdenParadas.Mover(0, 0))
            runCurrent()
            assertTrue(e.enrutador.peticiones.isEmpty())

            e.ordenes.trySend(OrdenParadas.Quitar(0))
            advanceTimeBy(1_000)
            assertEquals(listOf(listOf(parada2.punto)), e.enrutador.peticiones.map { it.paradas })
            assertEquals(listOf(parada2), e.guiando.paradas)
            trabajo.cancel()
        }

    @Test
    fun `si el recalculo de una edicion falla las paradas vuelven a las de la ruta vigente`() =
        runTest {
            val e = escenario(List(30) { enRuta(300.0) }, listOf(ResultadoRuta.Error("sin ruta")))
            val trabajo = lanzar(e)

            advanceTimeBy(1_500)
            e.ordenes.trySend(OrdenParadas.Quitar(1))
            runCurrent()
            assertEquals(listOf(parada1), e.guiando.paradas)

            advanceTimeBy(1_000)
            assertEquals(listOf(parada1, parada2), e.guiando.paradas)
            assertFalse(e.guiando.recalculando)
            assertEquals(2, e.guiando.ruta.indicesParadas.size)
            trabajo.cancel()
        }

    @Test
    fun `dos ediciones seguidas dejan una sola ruta nueva con la lista final`() =
        runTest {
            val extra = Destino("Extra", p(1500.0, 300.0))
            val nueva = rutaDesde(enRuta(300.0), listOf(parada1, parada2, extra))
            val e =
                escenario(List(30) { enRuta(300.0) }, listOf(ResultadoRuta.Ok(nueva, 50), ResultadoRuta.Ok(nueva, 50)))
            val trabajo = lanzar(e)

            advanceTimeBy(1_500)
            e.ordenes.trySend(OrdenParadas.Anadir(extra))
            runCurrent()
            e.ordenes.trySend(OrdenParadas.Mover(2, 0))
            advanceTimeBy(1_000)

            assertEquals(listOf(extra, parada1, parada2), e.guiando.paradas)
            assertEquals(
                listOf(extra.punto, parada1.punto, parada2.punto),
                e.enrutador.peticiones
                    .last()
                    .paradas,
            )
            assertFalse(e.guiando.recalculando)
            assertNotNull(e.guiando.progreso)
            trabajo.cancel()
        }
}
