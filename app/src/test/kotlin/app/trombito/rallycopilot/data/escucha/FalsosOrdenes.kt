package app.trombito.rallycopilot.data.escucha

import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.escucha.ContextoConduccion
import app.trombito.rallycopilot.domain.escucha.MarcaConPosicion
import app.trombito.rallycopilot.domain.escucha.ProveedorContextoConduccion
import app.trombito.rallycopilot.domain.escucha.RepositorioMarcas
import app.trombito.rallycopilot.domain.navegacion.Destino
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.navegacion.IniciarRutaA
import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.domain.tramos.ControlSalidaCrono
import app.trombito.rallycopilot.domain.tramos.CronoEnCurso
import app.trombito.rallycopilot.domain.tramos.ProveedorCrono
import app.trombito.rallycopilot.domain.ubicacion.FuenteUbicacion
import app.trombito.rallycopilot.domain.vias.ProveedorViaActual
import app.trombito.rallycopilot.domain.vias.ViaActual
import app.trombito.rallycopilot.domain.voz.Locutor
import app.trombito.rallycopilot.domain.voz.PrioridadVoz
import app.trombito.rallycopilot.domain.voz.VozCopiloto
import app.trombito.rallycopilot.motor.edicion.Marca
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.ruta.EstadoRuta
import app.trombito.rallycopilot.motor.ruta.Ruta
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import app.trombito.rallycopilot.ui.comun.FalsoRepositorioAjustesConduccion
import app.trombito.rallycopilot.ui.navegacion.FalsoBuscador
import app.trombito.rallycopilot.ui.navegacion.FalsoControlNavegacion
import app.trombito.rallycopilot.ui.navegacion.FalsoEnrutador
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

/** Locutor que anota lo que dice; `hablando` se puede mover desde el test. */
class LocutorGrabador(
    override val hablando: MutableStateFlow<Boolean> = MutableStateFlow(false),
) : Locutor {
    val dichas = java.util.Collections.synchronizedList(mutableListOf<String>())

    /** Prioridad de cada frase de [dichas], en el mismo orden. */
    val prioridades = java.util.Collections.synchronizedList(mutableListOf<PrioridadVoz>())

    override fun usarVoz(v: VozCopiloto) = Unit

    override fun preparar(textos: List<String>) = Unit

    override fun decir(
        texto: String,
        prioridad: PrioridadVoz,
        caducaMs: Long?,
    ) {
        dichas += texto
        prioridades += prioridad
    }

    override fun duracionEstimadaS(texto: String): Double = 0.0

    override fun probar(v: VozCopiloto) = Unit

    override fun cerrar() = Unit
}

class MarcasFalsas : RepositorioMarcas {
    val guardadas = mutableListOf<Pair<Marca, PuntoGeo?>>()

    override suspend fun guardar(
        marca: Marca,
        posicion: PuntoGeo?,
    ) {
        guardadas += marca to posicion
    }

    override suspend fun deTramo(tramoId: String) = emptyList<MarcaConPosicion>()
}

class CronoFalso : ProveedorCrono {
    val estado = MutableStateFlow<CronoEnCurso?>(null)
    override val crono: StateFlow<CronoEnCurso?> = estado
}

class SalidaFalsa : ControlSalidaCrono {
    var salidas = 0

    override fun darSalida() {
        salidas++
    }
}

/** Ubicación con una sola fijación disponible al instante; sin fijación el flujo se queda vacío (termina). */
class UbicacionFalsa(
    var fijacion: Fijacion? = Fijacion(PuntoGeo(39.7, 2.9), 1L, 15.0, 90.0, 5.0),
) : FuenteUbicacion {
    override fun fijaciones(): Flow<Fijacion> = fijacion?.let { flowOf(it) } ?: emptyFlow()

    override fun tienePermiso(): Boolean = true
}

class ViasFalsas(
    var via: ViaActual? = null,
) : ProveedorViaActual {
    override suspend fun viaEn(fijacion: Fijacion): ViaActual? = via
}

/** Guiado hacia un destino con el progreso dado (distancia en m, tiempo en s y hora de llegada en ms epoch). */
fun guiandoCon(
    distanciaM: Double,
    tiempoS: Double,
    llegadaMs: Long,
): EstadoNavegacion.Guiando {
    val progreso =
        EstadoRuta(
            progresoM = 0.0,
            distanciaRestanteM = distanciaM,
            tiempoRestanteS = tiempoS,
            llegadaMs = llegadaMs,
            maniobraActual = null,
            distanciaAManiobraM = 0.0,
            maniobraSiguiente = null,
            distanciaALaRutaM = 0.0,
            llegado = false,
        )
    return EstadoNavegacion.Guiando(
        Destino("Palma", PuntoGeo(39.57, 2.65)),
        PerfilRuta.MAS_RAPIDA,
        Ruta(emptyList(), emptyList(), distanciaM, tiempoS),
        progreso,
        recalculando = false,
    )
}

/** Todo lo necesario para ejecutar órdenes en un test, con valores por defecto y los fakes a la vista. */
class BancoOrdenes(
    ambito: CoroutineScope,
    inicial: AjustesConduccion = AjustesConduccion(voz = VozCopiloto.DAVEFX),
    pasoCuentaAtrasMs: Long = ConduccionPorVoz.PASO_CUENTA_ATRAS_MS,
) {
    val ajustes = FalsoRepositorioAjustesConduccion(inicial)
    val locutor = LocutorGrabador()
    val memoria = MemoriaFrases()
    val ediciones = EdicionesFalsas()
    val marcas = MarcasFalsas()
    val crono = CronoFalso()
    val salida = SalidaFalsa()
    val ubicacion = UbicacionFalsa()
    val vias = ViasFalsas()
    val buscador = FalsoBuscador()
    val enrutador = FalsoEnrutador()
    val control = FalsoControlNavegacion()
    var contexto: ContextoConduccion? = ContextoConduccion(1_250.0, "tramo-1", PuntoGeo(39.7, 2.9))
    var estado: EstadoNavegacion = EstadoNavegacion.Inactiva
    var ahoraMs = 777L
    var terminadas = 0

    private val preguntas =
        RespondedorPreguntas(
            estado = { estado },
            crono = crono,
            vias = vias,
            ubicacion = ubicacion,
            ahoraMs = { ahoraMs },
            zona = { java.time.ZoneOffset.UTC },
        )
    val conduccion =
        ConduccionPorVoz(
            estado = { estado },
            terminar = { terminadas++ },
            crono = crono,
            salida = salida,
            locutor = locutor,
            ambito = ambito,
            pasoCuentaAtrasMs = pasoCuentaAtrasMs,
        )
    val navegacion =
        NavegacionPorVoz(
            buscador = buscador,
            iniciar = IniciarRutaA(control, enrutador, ubicacion),
            control = control,
            ajustes = ajustes,
            ubicacion = ubicacion,
            ahoraMs = { ahoraMs },
        )
    val ejecutor =
        EjecutorOrdenes(
            ajustes,
            locutor,
            memoria,
            ediciones,
            marcas,
            ProveedorContextoConduccion { contexto },
            { ahoraMs },
            preguntas,
            conduccion,
            navegacion,
        )
}
