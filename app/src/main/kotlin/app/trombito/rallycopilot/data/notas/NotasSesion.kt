package app.trombito.rallycopilot.data.notas

import android.util.Log
import app.trombito.rallycopilot.data.recorridos.RepositorioRecorridos
import app.trombito.rallycopilot.di.DispatcherDefault
import app.trombito.rallycopilot.domain.notas.NotasEnCurso
import app.trombito.rallycopilot.domain.notas.OrigenNotas
import app.trombito.rallycopilot.motor.edicion.AccionEdicion
import app.trombito.rallycopilot.motor.edicion.FusionNotas
import app.trombito.rallycopilot.motor.edicion.NotasEditadas
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.notas.HojaNotas
import app.trombito.rallycopilot.motor.notas.PrediccionVia
import app.trombito.rallycopilot.motor.notas.RutaMedida
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import app.trombito.rallycopilot.motor.vias.Coincidencia
import app.trombito.rallycopilot.motor.vias.SeguidorVia
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Crea la [NotasSesion] de cada conducción; las notas se publican en [NotasEnVivoImpl]. */
@Singleton
class FabricaNotasSesion internal constructor(
    private val contexto: FuenteContextoNotas,
    private val calibrador: CalibradorTabla,
    private val salida: (NotasEnCurso?) -> Unit,
    private val trabajo: CoroutineDispatcher,
    private val alFallar: (Throwable) -> Unit,
) {
    @Inject
    constructor(
        contexto: FuenteContextoNotas,
        recorridos: RepositorioRecorridos,
        notas: NotasEnVivoImpl,
        @DispatcherDefault dispatcher: CoroutineDispatcher,
    ) : this(
        contexto,
        CalibradorRecorridos(recorridos),
        notas::publicar,
        dispatcher,
        { Log.w("NotasSesion", "Fallo en las notas: ${it.javaClass.simpleName}") },
    )

    fun crear(): NotasSesion = NotasSesion(contexto, calibrador, salida, trabajo, alFallar)
}

/** Una edición de notas situada en el mapa por [punto]; se lleva a la hoja vigente proyectándola sobre su ruta. */
class EdicionEnMapa(
    val punto: PuntoGeo,
    val accion: AccionEdicion,
    val marcaTiempoMs: Long,
)

/**
 * Notas en vivo de una conducción. Las posiciones llegan de la corrutina de la sesión (una fijación por segundo, sin
 * trabajo pesado: solo se proyecta la posición); la hoja se genera en [trabajo] y la calibración y la carga de datos
 * corren en segundo plano. Un fallo al generar no afecta a la conducción: se avisa a [alFallar] y se sigue sin hoja.
 *
 * Guiado: [iniciarGuiado] / [nuevaRuta] generan la hoja de la ruta y [posicionGuiada] da el progreso. Libre:
 * [iniciarLibre] y [posicionLibre] predicen la carretera con el [PrediccionVia] y regeneran la hoja tras recorrer
 * [ReglasPrediccion.RECORRIDO_REGENERAR_M] de la predicción, al salirse de ella o tras un cruce dudoso.
 *
 * Ediciones ([fijarEdiciones]): las notas editadas siempre prevalecen, así que cada hoja que se publica es la
 * automática ya fusionada ([FusionNotas]) con ellas; al regenerar la automática se vuelven a aplicar. Reconocimiento
 * ([iniciarReconocimiento]): la hoja es la de la traza de un tramo y la posición se proyecta sobre ella.
 */
@Suppress("TooManyFunctions") // un estado por conducción: guiado, libre, reconocimiento, ediciones y generación
class NotasSesion internal constructor(
    private val contexto: FuenteContextoNotas,
    private val calibrador: CalibradorTabla,
    private val salida: (NotasEnCurso?) -> Unit,
    trabajo: CoroutineDispatcher,
    alFallar: (Throwable) -> Unit,
) {
    private val raiz = SupervisorJob()
    private val alcance = CoroutineScope(raiz + trabajo + CoroutineExceptionHandler { _, e -> alFallar(e) })
    private val candado = Any()
    private val cargaContexto: Deferred<ContextoNotas?> =
        alcance.async(start = CoroutineStart.LAZY) {
            contexto.contexto().also { c ->
                synchronized(candado) { seguidorVia = c?.let { SeguidorVia(it.indice, grafo = it.grafo) } }
            }
        }

    // Todo lo siguiente se toca solo con el candado.
    private var origen = OrigenNotas.GUIADO
    private var cerrada = false
    private var factor: Double? = null
    private var seguidorVia: SeguidorVia? = null
    private var hoja: HojaNotas? = null
    private var ruta: RutaMedida? = null
    private var cortadaEnCruceDudoso = false
    private var regenerarYa = false
    private var puntosGuiado: List<PuntoGeo> = emptyList()
    private var posicionM = 0.0
    private var vMs: Double? = null
    private var ultimoPunto: PuntoGeo? = null
    private var generacion: Job? = null
    private var epoca = 0
    private var ultimoIntentoMs = Long.MIN_VALUE / 2
    private var emitida: NotasEnCurso? = null
    private var tramoReconocimiento: String? = null
    private var ediciones: List<EdicionEnMapa> = emptyList()
    private var fusion: Fusion? = null

    /** Hoja ya fusionada de una hoja automática, su ruta y la lista de ediciones concretas. */
    private class Fusion(
        val hoja: HojaNotas,
        val ruta: RutaMedida?,
        val ediciones: List<EdicionEnMapa>,
        val resultado: HojaNotas,
    )

    /** Con el candado: publica las notas y las recuerda para [instantanea]. */
    private val emitir: (NotasEnCurso?) -> Unit = {
        emitida = it
        salida(it)
    }

    /** Conducción guiada: genera la hoja de [puntos] (la polilínea de la ruta). */
    fun iniciarGuiado(puntos: List<PuntoGeo>) {
        synchronized(candado) {
            origen = OrigenNotas.GUIADO
            puntosGuiado = puntos
            lanzarGuiado()
        }
        arrancarSegundoPlano()
    }

    /** La ruta guiada ha cambiado (recálculo): la hoja vieja deja de valer y se genera la nueva. */
    fun nuevaRuta(puntos: List<PuntoGeo>) {
        synchronized(candado) {
            if (cerrada) return
            puntosGuiado = puntos
            hoja = null
            ruta = null
            posicionM = 0.0
            emitir(null)
            lanzarGuiado()
        }
    }

    /** Progreso a lo largo de la ruta (el que da el seguidor de ruta). */
    fun posicionGuiada(
        progresoM: Double,
        velocidadMs: Double?,
    ) {
        synchronized(candado) {
            if (cerrada) return
            posicionM = progresoM
            vMs = velocidadMs
            publicar()
        }
    }

    /** Conducción libre: la hoja sale de la carretera predicha. */
    fun iniciarLibre() {
        synchronized(candado) { origen = OrigenNotas.LIBRE }
        arrancarSegundoPlano()
    }

    /** Una fijación en modo libre; regenera la predicción si toca. */
    fun posicionLibre(fijacion: Fijacion) {
        synchronized(candado) {
            val seguidor = seguidorVia
            if (cerrada || seguidor == null) return
            ultimoPunto = fijacion.punto
            vMs = fijacion.velocidadMs
            val coincidencia = seguidor.actualizar(fijacion.punto, fijacion.rumboGrados)
            val actual = ruta
            val proyeccion = actual?.proyectar(fijacion.punto)
            if (proyeccion != null) posicionM = proyeccion.posicionM
            val fuera =
                actual != null && proyeccion != null &&
                    ReglasPrediccion.fuera(actual, proyeccion, cortadaEnCruceDudoso)
            val dentro = actual != null && !fuera
            if (coincidencia != null && fijacion.rumboGrados != null) {
                val toca =
                    actual == null || proyeccion == null || regenerarYa ||
                        ReglasPrediccion.debeRegenerar(actual, proyeccion, cortadaEnCruceDudoso)
                if (toca) intentarPrediccion(coincidencia, fijacion.tiempoMs)
            }
            if (dentro) publicar() else emitir(null)
        }
    }

    /** Reconocimiento del tramo [tramoId]: la hoja sale de su [traza] y es la que se sigue durante toda la pasada. */
    fun iniciarReconocimiento(
        tramoId: String,
        traza: List<PuntoGeo>,
    ) {
        synchronized(candado) {
            origen = OrigenNotas.LIBRE
            tramoReconocimiento = tramoId
            puntosGuiado = traza
            lanzarGuiado()
        }
        arrancarSegundoPlano()
    }

    /** Una fijación en reconocimiento: la posición es la proyección sobre la traza del tramo. */
    fun posicionReconocimiento(fijacion: Fijacion) {
        synchronized(candado) {
            val actual = ruta
            if (cerrada || actual == null) return
            val proyeccion = actual.proyectar(fijacion.punto)
            vMs = fijacion.velocidadMs
            if (proyeccion.distanciaM > FUERA_RECONOCIMIENTO_M) {
                emitir(null)
            } else {
                posicionM = proyeccion.posicionM
                publicar()
            }
        }
    }

    /**
     * Las ediciones vigentes (de las que se aplican a la hoja en curso: las del tramo activo y las dictadas sin tramo).
     * Se publica ya la hoja fusionada; cada regeneración de la automática las vuelve a aplicar.
     */
    fun fijarEdiciones(nuevas: List<EdicionEnMapa>) {
        synchronized(candado) {
            ediciones = nuevas
            if (!cerrada && hoja != null) publicar()
        }
    }

    /** Fin de la conducción: cancela todo y publica null. Se puede llamar más de una vez. */
    fun terminar() {
        synchronized(candado) {
            if (cerrada) return
            cerrada = true
            hoja = null
            ruta = null
            emitir(null)
        }
        raiz.cancel()
    }

    private fun arrancarSegundoPlano() {
        cargaContexto.start()
        alcance.launch {
            val calculado = calibrador.factor()
            synchronized(candado) {
                factor = calculado
                if (calculado != null && !cerrada) {
                    if (origen == OrigenNotas.GUIADO) lanzarGuiado() else regenerarYa = true
                }
            }
        }
    }

    /** Lo último publicado (el copiloto de voz lo lee en cada fijación); null si no hay notas vigentes. */
    val instantanea: NotasEnCurso? get() = synchronized(candado) { emitida }

    private fun publicar() {
        val actual = hoja ?: return
        emitir(
            NotasEnCurso(
                hojaConEdiciones(actual),
                posicionM,
                vMs,
                origen,
                ruta?.puntos,
                ruta?.let { it::puntoEn },
                tramoReconocimiento,
            ),
        )
    }

    /** Con el candado: la hoja [automatica] con las ediciones aplicadas (en caché mientras nada cambie). */
    private fun hojaConEdiciones(automatica: HojaNotas): HojaNotas {
        val medida = ruta
        val lista = ediciones
        if (lista.isEmpty() || medida == null) return automatica
        val vigente = fusion?.takeIf { it.hoja === automatica && it.ruta === medida && it.ediciones === lista }
        return vigente?.resultado ?: fusionar(automatica, medida, lista)
    }

    /** Con el candado: aplica [lista] a [automatica] sobre [medida] y recuerda el resultado. */
    private fun fusionar(
        automatica: HojaNotas,
        medida: RutaMedida,
        lista: List<EdicionEnMapa>,
    ): HojaNotas {
        val enHoja =
            lista.mapNotNull { e ->
                val proyeccion = medida.proyectar(e.punto)
                if (proyeccion.distanciaM > RADIO_EDICION_M) {
                    null
                } else {
                    NotasEditadas(TRAMO_DE_LA_HOJA, proyeccion.posicionM, e.accion, e.marcaTiempoMs)
                }
            }
        val resultado = FusionNotas.aplicar(automatica, { it }, enHoja)
        fusion = Fusion(automatica, medida, lista, resultado)
        return resultado
    }

    /** Con el candado: genera la hoja de [puntosGuiado] cancelando la generación anterior. */
    private fun lanzarGuiado() {
        generacion?.cancel()
        val mia = ++epoca
        val puntos = puntosGuiado
        val factorUsado = factor
        generacion =
            alcance.launch {
                val datos = cargaContexto.await()
                val medida = if (puntos.size >= 2) RutaMedida(puntos) else null
                val nueva = generarHoja(medida, datos, factorUsado)
                ensureActive()
                synchronized(candado) {
                    if (!cerrada && mia == epoca) {
                        hoja = nueva
                        ruta = medida
                        publicar()
                    }
                }
            }
    }

    /** Con el candado: predice la carretera desde [coincidencia] y genera su hoja, si no hay otra en marcha. */
    private fun intentarPrediccion(
        coincidencia: Coincidencia,
        tiempoMs: Long,
    ) {
        if (generacion?.isActive == true || tiempoMs - ultimoIntentoMs < REINTENTO_MS) return
        ultimoIntentoMs = tiempoMs
        regenerarYa = false
        val mia = ++epoca
        val factorUsado = factor
        generacion =
            alcance.launch {
                val datos = cargaContexto.await() ?: return@launch
                val prediccion =
                    PrediccionVia(datos.grafo).predecir(
                        coincidencia.via,
                        coincidencia.posicionEnViaM,
                        coincidencia.adelante,
                        PREDICCION_M,
                    )
                val medida = if (prediccion.puntos.size >= 2) RutaMedida(prediccion.puntos) else null
                val nueva = medida?.let { generarHoja(it, datos, factorUsado) }
                ensureActive()
                synchronized(candado) {
                    if (!cerrada && mia == epoca) {
                        hoja = nueva
                        ruta = medida
                        cortadaEnCruceDudoso = prediccion.cortadaEnCruceDudoso
                        val punto = ultimoPunto
                        if (medida != null && punto != null) posicionM = medida.proyectar(punto).posicionM
                        publicar()
                    }
                }
            }
    }

    private companion object {
        const val PREDICCION_M = 2000.0
        const val REINTENTO_MS = 3_000L

        /** Una edición a más de esto (m) de la ruta de la hoja no es de esta ruta y no se aplica. */
        const val RADIO_EDICION_M = 40.0

        /** En reconocimiento, a más de esto (m) de la traza del tramo no se sigue la hoja. */
        const val FUERA_RECONOCIMIENTO_M = 150.0

        /** Las ediciones ya van en metros de la hoja: comparten un tramo ficticio. */
        const val TRAMO_DE_LA_HOJA = "hoja"
    }
}
