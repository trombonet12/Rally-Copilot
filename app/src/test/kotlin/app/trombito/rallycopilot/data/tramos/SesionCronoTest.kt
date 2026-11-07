package app.trombito.rallycopilot.data.tramos

import app.trombito.rallycopilot.data.db.PasadaEntity
import app.trombito.rallycopilot.data.db.TramoEntity
import app.trombito.rallycopilot.data.escucha.EdicionesFalsas
import app.trombito.rallycopilot.data.navegacion.CopilotoVoz
import app.trombito.rallycopilot.data.navegacion.SesionNavegacion
import app.trombito.rallycopilot.data.navegacion.SolicitudNavegacion
import app.trombito.rallycopilot.data.recorridos.PuntoRecorrido
import app.trombito.rallycopilot.data.recorridos.Recorrido
import app.trombito.rallycopilot.data.recorridos.RepositorioRecorridos
import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.ruta.Enrutador
import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.domain.ruta.ResultadoRuta
import app.trombito.rallycopilot.domain.tramos.CronoEnCurso
import app.trombito.rallycopilot.domain.tramos.EstadisticasPerfil
import app.trombito.rallycopilot.domain.tramos.FiltroTramos
import app.trombito.rallycopilot.domain.tramos.OrigenPasada
import app.trombito.rallycopilot.domain.tramos.PasadaDetallada
import app.trombito.rallycopilot.domain.tramos.RepositorioTramos
import app.trombito.rallycopilot.domain.tramos.ResumenTramo
import app.trombito.rallycopilot.domain.tramos.TipoCorte
import app.trombito.rallycopilot.domain.tramos.TramoCronometrable
import app.trombito.rallycopilot.domain.tramos.TramoGuardado
import app.trombito.rallycopilot.domain.ubicacion.FuenteUbicacion
import app.trombito.rallycopilot.domain.voz.Locutor
import app.trombito.rallycopilot.domain.voz.PrioridadVoz
import app.trombito.rallycopilot.domain.voz.VozCopiloto
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.distanciaRecorrido
import app.trombito.rallycopilot.motor.geo.longitudMetros
import app.trombito.rallycopilot.motor.tramos.Pasada
import app.trombito.rallycopilot.motor.tramos.Tramo
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import app.trombito.rallycopilot.motor.voz.ModoCopiloto
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.cos
import kotlin.math.hypot

private const val METROS_POR_GRADO = 111_194.9

/** La traza real de Sa Calobra (docs/rutas); los tests de `app` corren con el directorio de trabajo en `app/`. */
private val CALOBRA: List<PuntoGeo> =
    Regex("""<trkpt\s+lat="(-?[\d.]+)"\s+lon="(-?[\d.]+)"""")
        .findAll(File("../docs/rutas/ma-2141-sa-calobra.gpx").readText())
        .map { PuntoGeo(it.groupValues[1].toDouble(), it.groupValues[2].toDouble()) }
        .toList()

/** Mueve [p] [metros] en la dirección de [desde] a [hacia] (plano local; de sobra para un par de cientos de metros). */
private fun desplazar(
    p: PuntoGeo,
    desde: PuntoGeo,
    hacia: PuntoGeo,
    metros: Double,
): PuntoGeo {
    val norte = (hacia.lat - desde.lat) * METROS_POR_GRADO
    val este = (hacia.lon - desde.lon) * METROS_POR_GRADO * cos(Math.toRadians(desde.lat))
    val largo = hypot(norte, este)
    return PuntoGeo(
        p.lat + metros * norte / largo / METROS_POR_GRADO,
        p.lon + metros * este / largo / (METROS_POR_GRADO * cos(Math.toRadians(p.lat))),
    )
}

/** Punto de la traza a [d] metros de su inicio; antes o después de ella prolonga el primer o el último tramo. */
private fun puntoA(
    traza: List<PuntoGeo>,
    d: Double,
): PuntoGeo =
    if (d <= 0.0) {
        desplazar(traza.first(), traza[0], traza[1], d)
    } else {
        puntoInterior(traza, d)
            ?: desplazar(traza.last(), traza[traza.size - 2], traza.last(), d - traza.longitudMetros())
    }

private fun puntoInterior(
    traza: List<PuntoGeo>,
    d: Double,
): PuntoGeo? {
    var resto = d
    for (i in 0 until traza.lastIndex) {
        val largo = traza[i].distanciaA(traza[i + 1])
        if (resto <= largo) {
            val t = resto / largo
            return PuntoGeo(
                traza[i].lat + t * (traza[i + 1].lat - traza[i].lat),
                traza[i].lon + t * (traza[i + 1].lon - traza[i].lon),
            )
        }
        resto -= largo
    }
    return null
}

/** Una conducción a [kmh] constante con una fijación por segundo, con [margenM] de carretera antes y después. */
private fun conduccion(
    kmh: Double,
    margenM: Double = 0.0,
    hastaM: Double = CALOBRA.longitudMetros(),
    base: Long = 5_000_000L,
): List<Fijacion> {
    val v = kmh / 3.6
    val pasos = ((hastaM + 2 * margenM) / v).toInt()
    return (0..pasos).map { k -> Fijacion(puntoA(CALOBRA, -margenM + v * k), base + k * 1000L, v, null, 5.0) }
}

/** Fuente que da las fijaciones a 1 Hz de tiempo virtual (la primera tras 1 s: la sesión ya ha cargado los tramos). */
private class FuenteDeRuta(
    private val fijaciones: List<Fijacion>,
) : FuenteUbicacion {
    override fun fijaciones(): Flow<Fijacion> =
        flow {
            delay(1000)
            for (f in fijaciones) {
                emit(f)
                delay(1000)
            }
        }

    override fun tienePermiso() = true
}

private object EnrutadorNulo : Enrutador {
    override suspend fun ruta(
        origen: PuntoGeo,
        destino: PuntoGeo,
        perfil: PerfilRuta,
    ): ResultadoRuta = error("el modo libre no calcula rutas")
}

/** Recorridos en memoria con la regla real: menos de 1 km se descarta. */
private class RecorridosEnMemoria(
    private val alTerminar: () -> Unit = {},
) : RepositorioRecorridos {
    private val puntosPorId = HashMap<Long, MutableList<PuntoRecorrido>>()
    private var siguiente = 1L

    override suspend fun empezar(destinoNombre: String?): Long = siguiente++.also { puntosPorId[it] = mutableListOf() }

    override suspend fun anadirPuntos(
        id: Long,
        puntos: List<PuntoRecorrido>,
    ) {
        puntosPorId.getValue(id) += puntos
    }

    override suspend fun terminar(id: Long): Recorrido? {
        alTerminar()
        val puntos = puntosPorId.getValue(id)
        val distancia = puntos.map { PuntoGeo(it.lat, it.lon) }.distanciaRecorrido()
        return if (distancia < RepositorioRecorridos.DISTANCIA_MINIMA_M) {
            puntosPorId.remove(id)
            null
        } else {
            Recorrido(id, puntos.first().tiempoMs, puntos.last().tiempoMs, distancia, null)
        }
    }

    override suspend fun idsEnCurso() = emptyList<Long>()

    override fun listar() = emptyFlow<List<Recorrido>>()

    override suspend fun puntos(id: Long) = puntosPorId[id].orEmpty()

    override suspend fun borrar(id: Long) = Unit
}

/** Tramos en memoria que guardan lo mismo que Room (con el mismo codec), para probar el viaje de ida y vuelta. */
private class TramosEnMemoria : RepositorioTramos {
    val tramos = LinkedHashMap<String, TramoEntity>()
    val pasadas = ArrayList<PasadaEntity>()

    /** Cuántos guardados de pasada fallan aún (simula la base caída). */
    var fallosDeGuardado = 0

    override fun observar(): Flow<List<TramoGuardado>> = emptyFlow()

    override fun observarResumenes(filtro: FiltroTramos): Flow<List<ResumenTramo>> = emptyFlow()

    override fun observarPerfil(): Flow<EstadisticasPerfil> = emptyFlow()

    override suspend fun tramo(tramoId: String): Tramo? = tramos[tramoId]?.aTramo()

    override suspend fun pasadasDetalladas(tramoId: String): List<PasadaDetallada> = emptyList()

    override suspend fun mejorPasadaDetallada(tramoId: String): PasadaDetallada? = null

    override suspend fun renombrar(
        tramoId: String,
        nombre: String?,
    ) = Unit

    override suspend fun marcarFavorito(
        tramoId: String,
        favorito: Boolean,
    ) = Unit

    override fun observarMejorPasada(tramoId: String): Flow<Pasada?> = emptyFlow()

    override suspend fun cronometrables(): List<TramoCronometrable> =
        tramos.values.mapNotNull { fila -> fila.aTramo()?.let { TramoCronometrable(it, mejorPasada(fila.id)) } }

    override suspend fun mejorPasada(tramoId: String): Pasada? {
        val tramo = tramos[tramoId]?.aTramo() ?: return null
        return pasadas.filter { it.tramoId == tramoId }.minByOrNull { it.totalMs }?.aPasada(tramo.cortesExactosM())
    }

    override suspend fun crear(
        tramo: Tramo,
        nombre: String?,
        nombreAutomatico: String?,
        creadoEnMs: Long,
        primera: Pasada,
        origen: OrigenPasada,
    ) {
        tramos[tramo.id] = tramo.aEntidad(nombre, creadoEnMs, nombreAutomatico)
        guardarPasada(primera, origen)
    }

    override suspend fun crearSinPasadas(
        tramo: Tramo,
        nombre: String?,
        nombreAutomatico: String?,
        creadoEnMs: Long,
    ) {
        tramos[tramo.id] = tramo.aEntidad(nombre, creadoEnMs, nombreAutomatico)
    }

    override suspend fun guardarPasada(
        pasada: Pasada,
        origen: OrigenPasada,
    ): Long {
        if (fallosDeGuardado > 0) {
            fallosDeGuardado--
            error("base caída")
        }
        val tramo = checkNotNull(tramos[pasada.tramoId]?.aTramo())
        pasadas += pasada.aEntidad(origen, tramo.cortesExactosM()).copy(id = pasadas.size + 1L)
        return pasadas.size.toLong()
    }

    override suspend fun guardarCortes(
        tramoId: String,
        sectores: List<Double>,
        microsectores: List<Double>,
    ) = Unit

    override suspend fun borrar(tramoId: String) {
        tramos.remove(tramoId)
        pasadas.removeAll { it.tramoId == tramoId }
    }
}

private class LocutorAnotador : Locutor {
    override val hablando: StateFlow<Boolean> = MutableStateFlow(false)
    val dichas = mutableListOf<String>()

    override fun usarVoz(v: VozCopiloto) = Unit

    override fun preparar(textos: List<String>) = Unit

    override fun decir(
        texto: String,
        prioridad: PrioridadVoz,
        caducaMs: Long?,
    ) {
        dichas += texto
    }

    override fun duracionEstimadaS(texto: String) = 1.0

    override fun probar(v: VozCopiloto) = Unit

    override fun cerrar() = Unit
}

private class Conducida(
    val publicados: List<CronoEnCurso?>,
    val dichas: List<String>,
) {
    /** Lo publicado mientras duró la conducción (sin el `null` final). */
    val estados: List<CronoEnCurso> get() = publicados.filterNotNull()
}

class SesionCronoTest {
    private val tramos = TramosEnMemoria()
    private var pasadasAlTerminar = -1
    private val recorridos = RecorridosEnMemoria { pasadasAlTerminar = tramos.pasadas.size }
    private var idsNuevos = 0
    private val anclado = mutableListOf<String?>()
    private val ediciones = EdicionesFalsas()

    /** Conduce una sesión libre completa con las [fijaciones]; [modo] da voz al copiloto (null: sin copiloto). */
    @Suppress("LongParameterList") // un parámetro por escenario: modo, solicitud, cronometrar y salida
    private suspend fun TestScope.conducir(
        fijaciones: List<Fijacion>,
        modo: ModoCopiloto? = null,
        solicitud: SolicitudNavegacion = SolicitudNavegacion.Libre,
        cronometrar: Boolean = true,
        salidaManual: Flow<Boolean> = flowOf(false),
        darSalidaEnMs: Long? = null,
    ): Conducida {
        val publicados = mutableListOf<CronoEnCurso?>()
        val locutor = LocutorAnotador()
        val crono =
            SesionCrono(
                tramos,
                { publicados += it },
                { testScheduler.currentTime },
                { "tramo-${++idsNuevos}" },
                nombrar = { "Ma-2141 · Escorca" },
                anclarEdiciones = { anclado += it?.id },
                salidaManual = salidaManual,
            )
        // La salida manual la da Joan a mitad de la conducción: aquí, a los [darSalidaEnMs] de tiempo virtual.
        darSalidaEnMs?.let {
            launch {
                delay(it)
                crono.darSalida(testScheduler.currentTime)
            }
        }
        val copiloto =
            modo?.let { CopilotoVoz(locutor, flowOf(AjustesConduccion(modoCopiloto = it, voz = VozCopiloto.DAVEFX))) }
        SesionNavegacion(
            FuenteDeRuta(fijaciones),
            EnrutadorNulo,
            recorridos,
            reloj = { testScheduler.currentTime },
            publicar = {},
            motivoSinGps = "sin GPS",
            copiloto = copiloto,
            crono = crono,
            tramos = tramos,
            cronometrar = { cronometrar },
            ediciones = ediciones,
        ).correr(solicitud)
        return Conducida(publicados, locutor.dichas)
    }

    @Test
    fun `una primera conduccion de mas de un kilometro crea un tramo con su primera pasada`() =
        runTest {
            val c = conducir(conduccion(40.0))

            assertEquals(listOf("tramo-1"), tramos.tramos.keys.toList())
            assertEquals(1, tramos.pasadas.size)
            val pasada = checkNotNull(tramos.mejorPasada("tramo-1"))
            val esperadoMs = (CALOBRA.longitudMetros() - Tramo.META_ANTES_M) / (40.0 / 3.6) * 1000.0
            assertEquals(esperadoMs, pasada.totalMs.toDouble(), 3_000.0)
            // Sin tramo conocido solo hay un cronómetro de la conducción, y al cerrar no publica nada más.
            assertTrue(c.estados.all { it.tramo == null && !it.hayMejorPasada && it.deltaMs == null })
            assertTrue(c.estados.last().transcurridoMs > 1_000_000L)
            assertNull(c.publicados.last())
        }

    @Test
    fun `al cerrar la conduccion las ediciones sin tramo se anclan al tramo nuevo`() =
        runTest {
            conducir(conduccion(40.0))

            assertEquals(listOf<String?>("tramo-1"), anclado)
        }

    @Test
    fun `una pasada de reconocimiento no crea tramos ni guarda pasadas ni ancla ediciones`() =
        runTest {
            conducir(conduccion(40.0))
            val tramosAntes = tramos.tramos.size
            val pasadasAntes = tramos.pasadas.size
            anclado.clear()

            val c = conducir(conduccion(10.0), solicitud = SolicitudNavegacion.Reconocimiento("tramo-1"))

            assertEquals(tramosAntes, tramos.tramos.size)
            assertEquals(pasadasAntes, tramos.pasadas.size)
            assertTrue(anclado.isEmpty())
            assertTrue(c.estados.isEmpty())
        }

    @Test
    fun `el tramo nuevo guarda su nombre automatico y cada pasada su recorrido y su intervalo de puntos`() =
        runTest {
            conducir(conduccion(40.0))
            val primera = tramos.pasadas.single()
            val tramo = checkNotNull(tramos.tramos["tramo-1"])

            assertEquals("Ma-2141 · Escorca", tramo.nombreAutomatico)
            assertNull(tramo.nombre)
            assertEquals(1L, primera.recorridoId)
            val desde = checkNotNull(primera.puntosDesdeMs)
            val hasta = checkNotNull(primera.puntosHastaMs)
            assertEquals(primera.totalMs, hasta - desde)
            val puntos = recorridos.puntos(1L)
            assertTrue(desde >= puntos.first().tiempoMs && hasta <= puntos.last().tiempoMs)
            assertTrue(puntos.last().tiempoMs - hasta <= 15_000L)

            // Una segunda pasada (completada en vivo) también guarda de qué conducción sale.
            conducir(conduccion(60.0, margenM = 100.0, base = 9_000_000L))
            val segunda = tramos.pasadas.last()
            assertEquals(2L, segunda.recorridoId)
            assertEquals(segunda.totalMs, checkNotNull(segunda.puntosHastaMs) - checkNotNull(segunda.puntosDesdeMs))
            assertTrue(checkNotNull(segunda.puntosDesdeMs) >= 9_000_000L)
            // Sin crear otro tramo, el nombre del primero no se toca.
            assertEquals(listOf("tramo-1"), tramos.tramos.keys.toList())
        }

    @Test
    fun `al cruzar la meta la pasada queda guardada antes de cerrar y el resultado trae sus ids`() =
        runTest {
            conducir(conduccion(40.0))

            val c = conducir(conduccion(60.0, margenM = 100.0, base = 9_000_000L))

            // Al cerrar el recorrido (antes de cerrar el cronómetro) las dos pasadas ya estaban en la base.
            assertEquals(2, pasadasAlTerminar)
            val guardada = tramos.pasadas.last()
            assertEquals(2L, guardada.recorridoId)
            val resultados = c.estados.mapNotNull { it.resultado }
            // Primero se publica la meta sin ids y en cuanto se guarda, con ellos.
            assertNull(resultados.first().pasadaId)
            val ultimo = resultados.last()
            assertEquals("tramo-1", ultimo.tramoId)
            assertEquals(guardada.id, ultimo.pasadaId)
            assertEquals(guardada.totalMs, ultimo.totalMs)
        }

    @Test
    fun `cerrar no duplica la pasada ya guardada al cruzar la meta`() =
        runTest {
            conducir(conduccion(40.0))

            conducir(conduccion(60.0, margenM = 100.0, base = 9_000_000L))

            assertEquals(2, tramos.pasadas.size)
            assertEquals(1, tramos.pasadas.count { it.recorridoId == 2L })
            val ids = tramos.pasadas.map { it.id }
            assertEquals(ids.size, ids.toSet().size)
        }

    @Test
    fun `si falla el guardado al cruzar la meta el resultado va sin ids, la conduccion sigue y cerrar la reintenta`() =
        runTest {
            conducir(conduccion(40.0))
            tramos.fallosDeGuardado = 1

            val c = conducir(conduccion(60.0, margenM = 100.0, base = 9_000_000L))

            val resultado = checkNotNull(c.estados.last { it.resultado != null }.resultado)
            assertNull(resultado.tramoId)
            assertNull(resultado.pasadaId)
            assertNull(c.publicados.last())
            assertEquals(1, pasadasAlTerminar)
            assertEquals(2, tramos.pasadas.size)
        }

    @Test
    fun `si la base no se recupera no se guarda la pasada pero la conduccion termina sin ids`() =
        runTest {
            conducir(conduccion(40.0))
            tramos.fallosDeGuardado = 99

            val c = conducir(conduccion(60.0, margenM = 100.0, base = 9_000_000L))

            val resultado = checkNotNull(c.estados.last { it.resultado != null }.resultado)
            assertNull(resultado.pasadaId)
            assertEquals(1, tramos.pasadas.size)
            assertNull(c.publicados.last())
        }

    @Test
    fun `una conduccion de menos de un kilometro no crea tramo`() =
        runTest {
            conducir(conduccion(40.0, hastaM = 800.0))

            assertTrue(tramos.tramos.isEmpty())
            assertTrue(tramos.pasadas.isEmpty())
        }

    @Test
    fun `la segunda conduccion por el tramo lo detecta, publica el delta y guarda la pasada sin duplicarlo`() =
        runTest {
            conducir(conduccion(40.0))
            val lenta = checkNotNull(tramos.mejorPasada("tramo-1"))

            val c = conducir(conduccion(60.0, margenM = 100.0, base = 9_000_000L))

            assertEquals(listOf("tramo-1"), tramos.tramos.keys.toList())
            assertEquals(2, tramos.pasadas.size)
            val rapida = checkNotNull(tramos.mejorPasada("tramo-1"))
            assertTrue(rapida.totalMs < lenta.totalMs)

            // Tramo conocido: HUD con delta negativo en vivo, parciales de sectores y microsectores y la marca.
            val enTramo = c.estados.filter { it.tramo != null && it.resultado == null }
            assertTrue(enTramo.isNotEmpty())
            assertTrue(enTramo.all { it.hayMejorPasada && it.tramo?.id == "tramo-1" })
            val deltas = enTramo.mapNotNull { it.deltaMs }
            assertTrue(deltas.size > 50)
            assertTrue("delta ${deltas.last()}", deltas.last() < -10_000L)
            val ultimo = enTramo.last()
            assertEquals(3, ultimo.parciales.count { it.tipo == TipoCorte.SECTOR })
            assertTrue(ultimo.parciales.count { it.tipo == TipoCorte.MICROSECTOR } >= 10)
            assertEquals(ultimo.parciales.last(), ultimo.ultimaMarca)
            assertTrue(ultimo.parciales.all { (it.deltaAcumuladoMs ?: 0L) < 0L })
            assertTrue(ultimo.progresoM > 1_000.0)

            // Al cruzar la meta: resultado con el total, el delta final y nuevo mejor.
            val resultado = checkNotNull(c.estados.last { it.resultado != null }.resultado)
            assertEquals(rapida.totalMs, resultado.totalMs)
            assertEquals(rapida.totalMs - lenta.totalMs, resultado.deltaMs)
            assertTrue(resultado.nuevoMejor)
            assertEquals(lenta.totalMs, resultado.mejorAnteriorMs)
            assertNull(c.publicados.last())
        }

    @Test
    fun `un trayecto que solo recorre parte de un tramo guardado no lo completa y crea un tramo nuevo`() =
        runTest {
            conducir(conduccion(40.0))

            val c = conducir(conduccion(60.0, margenM = 100.0, hastaM = 6_000.0, base = 9_000_000L))

            assertEquals(listOf("tramo-1", "tramo-2"), tramos.tramos.keys.toList())
            assertEquals(1, tramos.pasadas.count { it.tramoId == "tramo-1" })
            assertEquals(1, tramos.pasadas.count { it.tramoId == "tramo-2" })
            assertTrue(c.estados.none { it.resultado != null })
            assertTrue(c.estados.any { it.tramo != null && it.hayMejorPasada })
        }

    @Test
    fun `una tercera conduccion mas lenta no es mejor pero se guarda y se compara con la mejor`() =
        runTest {
            conducir(conduccion(60.0))
            val mejor = checkNotNull(tramos.mejorPasada("tramo-1"))

            val c = conducir(conduccion(40.0, margenM = 100.0, base = 9_000_000L))

            assertEquals(1, tramos.tramos.size)
            assertEquals(2, tramos.pasadas.size)
            assertEquals(mejor.totalMs, checkNotNull(tramos.mejorPasada("tramo-1")).totalMs)
            val resultado = checkNotNull(c.estados.last { it.resultado != null }.resultado)
            assertFalse(resultado.nuevoMejor)
            assertTrue(checkNotNull(resultado.deltaMs) > 10_000L)
            assertEquals(mejor.totalMs, resultado.mejorAnteriorMs)
        }

    @Test
    fun `las frases de delta suenan en modo completo y no en modo notas`() =
        runTest {
            conducir(conduccion(40.0))

            val completo = conducir(conduccion(60.0, margenM = 100.0, base = 9_000_000L), ModoCopiloto.COMPLETO)
            val notas = conducir(conduccion(60.0, margenM = 100.0, base = 9_500_000L), ModoCopiloto.NOTAS)

            // Tres sectores y la meta, todos más rápidos que la mejor pasada guardada (la de 40 km/h)...
            assertEquals(4, completo.dichas.size)
            assertTrue(completo.dichas.all { it.startsWith("menos") })
            // ...y en la tercera conducción la mejor ya es la de 60 km/h, pero sin voz de crono en NOTAS.
            assertEquals(emptyList<String>(), notas.dichas)
            assertEquals(1, tramos.tramos.size)
            assertEquals(3, tramos.pasadas.size)
        }

    @Test
    fun `el cronometro de una conduccion sin tramo cuenta desde el inicio de la sesion`() =
        runTest {
            val c = conducir(conduccion(40.0, hastaM = 800.0))

            val tiempos = c.estados.map { it.transcurridoMs }
            assertEquals(tiempos.sorted(), tiempos)
            assertTrue(tiempos.last() >= 20_000L)
            assertTrue(c.estados.all { it.tramo == null })
            assertNotNull(c.publicados.lastOrNull { it != null })
        }

    @Test
    fun `con cronometrar desactivado no se crean tramos ni pasadas ni se publica cronometro`() =
        runTest {
            val c = conducir(conduccion(40.0), cronometrar = false)

            assertTrue(tramos.tramos.isEmpty())
            assertTrue(tramos.pasadas.isEmpty())
            assertTrue(c.publicados.isEmpty())
            assertTrue(anclado.isEmpty())
        }

    @Test
    fun `con cronometrar desactivado lo dictado sin tramo se descarta al cerrar`() =
        runTest {
            conducir(conduccion(40.0), cronometrar = false)

            assertEquals(listOf<String?>(null), ediciones.anclajes)
        }

    @Test
    fun `con cronometrar activado lo dictado sin tramo se ancla al tramo nuevo`() =
        runTest {
            conducir(conduccion(40.0))

            assertEquals(listOf<String?>("tramo-1"), anclado)
        }

    @Test
    fun `con cronometrar desactivado no se detectan los tramos guardados`() =
        runTest {
            conducir(conduccion(40.0))
            val antes = tramos.pasadas.size

            val c = conducir(conduccion(40.0), cronometrar = false)

            assertEquals(1, tramos.tramos.size)
            assertEquals(antes, tramos.pasadas.size)
            assertTrue(c.publicados.isEmpty())
        }

    @Test
    fun `el cronometro no cuenta el rato parado y empieza al moverse`() =
        runTest {
            val parado = (0 until 30).map { Fijacion(CALOBRA.first(), 5_000_000L + it * 1000L, 0.0, null, 5.0) }
            val movimiento = conduccion(40.0, hastaM = 800.0, base = 5_030_000L)
            val c = conducir(parado + movimiento)

            // Mientras está parado no hay cronómetro; después cuenta desde el primer movimiento.
            assertTrue(c.estados.first().transcurridoMs < 5_000L)
            assertFalse(c.estados.first().enMarcha)
            assertTrue(c.estados.last().enMarcha)
            val duracionMs = (movimiento.size - 1) * 1000L
            assertEquals(
                duracionMs.toDouble(),
                c.estados
                    .last()
                    .transcurridoMs
                    .toDouble(),
                3_000.0,
            )
        }

    @Test
    fun `parado la sesion publica listo sin tiempo y al salir pasa a en marcha`() =
        runTest {
            val parado = (0 until 30).map { Fijacion(CALOBRA.first(), 5_000_000L + it * 1000L, 0.0, null, 5.0) }
            val c = conducir(parado + conduccion(40.0, hastaM = 800.0, base = 5_030_000L))

            val listos = c.estados.takeWhile { !it.enMarcha }
            assertTrue(listos.size >= 29)
            assertTrue(listos.all { it.tramo == null && it.transcurridoMs == 0L && it.resultado == null })
            assertTrue(c.estados.drop(listos.size).all { it.enMarcha })
            assertTrue(c.estados.drop(listos.size).isNotEmpty())
        }

    @Test
    fun `la deriva del GPS en parado no arranca el cronometro`() =
        runTest {
            val deriva =
                (0 until 60).map {
                    val punto = puntoA(CALOBRA, if (it % 2 == 0) 0.0 else 30.0)
                    Fijacion(punto, 5_000_000L + it * 1000L, 8.0 / 3.6, null, 15.0)
                }
            val c = conducir(deriva)

            assertTrue(c.estados.isNotEmpty())
            assertTrue(c.estados.none { it.enMarcha })
            assertTrue(c.estados.all { it.transcurridoMs == 0L })
            // Sin salida no se crea tramo ni pasada con la deriva.
            assertTrue(tramos.tramos.isEmpty())
            assertTrue(tramos.pasadas.isEmpty())
        }

    @Test
    fun `el tramo nuevo empieza en la salida y no en el rato parado`() =
        runTest {
            val parado = (0 until 40).map { Fijacion(CALOBRA.first(), 5_000_000L + it * 1000L, 0.0, null, 5.0) }
            val c = conducir(parado + conduccion(40.0, base = 5_040_000L))

            val pasada = checkNotNull(tramos.mejorPasada("tramo-1"))
            val esperadoMs = (CALOBRA.longitudMetros() - Tramo.META_ANTES_M) / (40.0 / 3.6) * 1000.0
            assertEquals(esperadoMs, pasada.totalMs.toDouble(), 3_000.0)
            // El tramo creado arranca donde se empezó a mover, no con cientos de puntos repetidos del aparcamiento.
            val traza = checkNotNull(tramos.tramo("tramo-1")).traza
            assertTrue(traza.first().distanciaA(CALOBRA.first()) < 30.0)
            assertNull(c.publicados.last())
        }

    @Test
    fun `con salida manual el movimiento no arranca el cronometro y se espera la salida`() =
        runTest {
            val c = conducir(conduccion(40.0, hastaM = 800.0), salidaManual = flowOf(true))

            assertTrue(c.estados.isNotEmpty())
            assertTrue(c.estados.none { it.enMarcha })
            assertTrue(c.estados.all { it.esperaSalidaManual && it.transcurridoMs == 0L })
            // Sin salida no hay tramo ni pasada, aunque el coche se haya movido.
            assertTrue(tramos.tramos.isEmpty())
            assertTrue(tramos.pasadas.isEmpty())
        }

    @Test
    fun `con salida automatica no se espera ninguna salida`() =
        runTest {
            val parado = (0 until 10).map { Fijacion(CALOBRA.first(), 5_000_000L + it * 1000L, 0.0, null, 5.0) }
            val c = conducir(parado + conduccion(40.0, hastaM = 800.0, base = 5_010_000L))

            assertTrue(c.estados.none { it.esperaSalidaManual })
        }

    @Test
    fun `darSalida pone en marcha el cronometro desde ese instante y el tramo nuevo empieza ahi`() =
        runTest {
            val c = conducir(conduccion(40.0), salidaManual = flowOf(true), darSalidaEnMs = 30_000L)

            val esperando = c.estados.takeWhile { !it.enMarcha }
            assertTrue(esperando.size >= 25)
            assertTrue(esperando.all { it.esperaSalidaManual && it.transcurridoMs == 0L })
            val enMarcha = c.estados.drop(esperando.size)
            assertTrue(enMarcha.isNotEmpty())
            assertTrue(enMarcha.none { it.esperaSalidaManual })
            // Cuenta desde la salida (a los 30 s), no desde que empezó la conducción.
            val duracionMs = c.estados.size * 1000L
            assertTrue(enMarcha.last().transcurridoMs in 1L until duracionMs - 25_000L)
            // El tramo creado arranca donde estaba el coche a los 30 s (unos 320 m de carretera), no en el inicio.
            val inicio = checkNotNull(tramos.tramo("tramo-1")).traza.first()
            val cercano = CALOBRA.indices.minBy { CALOBRA[it].distanciaA(inicio) }
            val saltadoM = CALOBRA.take(cercano + 1).longitudMetros()
            assertTrue("el tramo arranca a $saltadoM m del inicio de la carretera", saltadoM in 250.0..400.0)
        }

    @Test
    fun `cambiar de manual a automatica en vivo hace que el movimiento arranque el cronometro`() =
        runTest {
            val ajuste =
                flow {
                    emit(true)
                    delay(10_000)
                    emit(false)
                }
            val c = conducir(conduccion(40.0, hastaM = 800.0), salidaManual = ajuste)

            assertTrue(c.estados.first().esperaSalidaManual)
            assertFalse(c.estados.first().enMarcha)
            assertTrue(c.estados.last().enMarcha)
            assertFalse(c.estados.last().esperaSalidaManual)
        }

    @Test
    fun `darSalida publica en marcha al momento y una segunda salida no cambia la primera`() =
        runTest {
            val publicados = mutableListOf<CronoEnCurso?>()
            var ahora = 10_000L
            val crono = SesionCrono(tramos, { publicados += it }, { ahora }, salidaManual = flowOf(true))
            crono.arrancar(this)
            testScheduler.runCurrent()
            for (k in 0 until 5) {
                crono.alFijar(Fijacion(CALOBRA.first(), 1_000L + k * 1000L, 12.0, null, 5.0))
            }
            assertFalse(checkNotNull(publicados.last()).enMarcha)
            assertTrue(checkNotNull(publicados.last()).esperaSalidaManual)

            ahora = 12_000L
            crono.darSalida(11_000L)
            val en = checkNotNull(publicados.last())
            assertTrue(en.enMarcha)
            assertFalse(en.esperaSalidaManual)
            assertEquals(1_000L, en.transcurridoMs)

            ahora = 15_000L
            crono.darSalida(14_000L)
            crono.alFijar(Fijacion(CALOBRA.first(), 7_000L, 12.0, null, 5.0))
            assertEquals(4_000L, checkNotNull(publicados.last()).transcurridoMs)
        }
}
