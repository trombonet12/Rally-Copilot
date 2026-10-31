package app.trombito.rallycopilot.data.notas

import app.trombito.rallycopilot.data.recorridos.PuntoRecorrido
import app.trombito.rallycopilot.data.recorridos.Recorrido
import app.trombito.rallycopilot.data.recorridos.RepositorioRecorridos
import app.trombito.rallycopilot.domain.ruta.Enrutador
import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.domain.ruta.ResultadoRuta
import app.trombito.rallycopilot.domain.ubicacion.FuenteUbicacion
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.notas.ContextoVias
import app.trombito.rallycopilot.motor.ruta.Maniobra
import app.trombito.rallycopilot.motor.ruta.Ruta
import app.trombito.rallycopilot.motor.ruta.TipoManiobra
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import app.trombito.rallycopilot.motor.vias.DatosNotasVias
import app.trombito.rallycopilot.motor.vias.GrafoVias
import app.trombito.rallycopilot.motor.vias.IndiceVias
import app.trombito.rallycopilot.motor.vias.NodoAviso
import app.trombito.rallycopilot.motor.vias.SentidoUnico
import app.trombito.rallycopilot.motor.vias.Via
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.cos
import kotlin.math.sin

private const val METROS_POR_GRADO_LAT = 111_194.9

/** Punto a [este] y [norte] metros del origen de las pruebas (en Mallorca). */
internal fun p(
    este: Double,
    norte: Double,
) = PuntoGeo(
    39.6 + norte / METROS_POR_GRADO_LAT,
    2.9 + este / (METROS_POR_GRADO_LAT * cos(Math.toRadians(39.6))),
)

/** 600 m al este, una curva a izquierdas de 90° y radio 60 m, y 600 m al norte. */
internal fun trazadoConCurva(): List<PuntoGeo> {
    val recta1 = (0..60).map { p(it * 10.0, 0.0) }
    val arco =
        (1..18).map {
            val theta = Math.toRadians(-90.0 + it * 5.0)
            p(600.0 + 60.0 * cos(theta), 60.0 + 60.0 * sin(theta))
        }
    val recta2 = (1..60).map { p(660.0, 60.0 + it * 10.0) }
    return recta1 + arco + recta2
}

internal fun recta(
    desdeEste: Double,
    desdeNorte: Double,
    haciaEste: Boolean,
    largoM: Double,
    pasoM: Double = 50.0,
): List<PuntoGeo> =
    (0..(largoM / pasoM).toInt()).map {
        if (haciaEste) p(desdeEste + it * pasoM, desdeNorte) else p(desdeEste, desdeNorte + it * pasoM)
    }

internal fun via(
    id: Long,
    puntos: List<PuntoGeo>,
    maxspeed: String? = null,
) = Via(id, "primary", maxspeed, null, null, SentidoUnico.NO, "MA-$id", null, puntos)

/** Contexto de [vias]; con [nodos] (aunque sea vacío) hay datos de `vias-notas`, sin ellos solo curvas. */
internal fun contextoDe(
    vias: List<Via>,
    nodos: List<NodoAviso>? = null,
): ContextoNotas {
    val indice = IndiceVias(vias)
    val grafo = GrafoVias(vias)
    val notas = nodos?.let { ContextoVias(indice, grafo, DatosNotasVias(emptyMap(), it, emptyMap())) }
    return ContextoNotas(indice, grafo, notas)
}

internal fun rutaDe(puntos: List<PuntoGeo>): Ruta {
    val ultimo = puntos.size - 1
    val salida = Maniobra(TipoManiobra.SALIDA, "salida", emptyList(), 0, ultimo, 1000.0, 100.0, null)
    val destino = Maniobra(TipoManiobra.DESTINO, "destino", emptyList(), ultimo, ultimo, 0.0, 0.0, null)
    return Ruta(puntos, listOf(salida, destino), RutaMedida(puntos).longitudM, 100.0)
}

/** Fijaciones a [vMs] m/s cada segundo siguiendo [puntos], con el rumbo de la polilínea. */
internal fun fijacionesPor(
    puntos: List<PuntoGeo>,
    vMs: Double = 20.0,
    desdeM: Double = 0.0,
    hastaM: Double? = null,
): List<Fijacion> {
    val medida = RutaMedida(puntos)
    val fin = hastaM ?: medida.longitudM
    val pasos = ((fin - desdeM) / vMs).toInt()
    return (0..pasos).map { k ->
        val s = desdeM + k * vMs
        Fijacion(medida.puntoEn(s), 5_000_000L + k * 1000L, vMs, medida.rumboEn(s), 5.0)
    }
}

internal fun fijacionEn(
    punto: PuntoGeo,
    rumbo: Double,
    indice: Int,
) = Fijacion(punto, 5_000_000L + indice * 1000L, 20.0, rumbo, 5.0)

/** Emite cada fijación a 1 Hz de tiempo virtual y luego espera (o termina). */
internal class FuenteFalsa(
    private val fijaciones: List<Fijacion>,
    private val terminaAlAcabar: Boolean = false,
) : FuenteUbicacion {
    override fun fijaciones(): Flow<Fijacion> =
        flow {
            for (f in fijaciones) {
                emit(f)
                delay(1000)
            }
            if (!terminaAlAcabar) awaitCancellation()
        }

    override fun tienePermiso() = true
}

internal class EnrutadorFalso(
    private val respuestas: MutableList<ResultadoRuta>,
) : Enrutador {
    override suspend fun ruta(
        origen: PuntoGeo,
        destino: PuntoGeo,
        perfil: PerfilRuta,
    ): ResultadoRuta {
        delay(200)
        return respuestas.removeAt(0)
    }
}

internal class RecorridosFalso(
    private val guardados: List<List<PuntoRecorrido>> = emptyList(),
) : RepositorioRecorridos {
    override suspend fun empezar(destinoNombre: String?) = 1L

    override suspend fun anadirPuntos(
        id: Long,
        puntos: List<PuntoRecorrido>,
    ) = Unit

    override suspend fun terminar(id: Long): Recorrido? = null

    override suspend fun idsEnCurso() = emptyList<Long>()

    override fun listar(): Flow<List<Recorrido>> =
        flow { emit(guardados.indices.map { Recorrido(it + 100L, 0, 0, 10_000.0, null) }) }

    override suspend fun puntos(id: Long) = guardados[(id - 100).toInt()]

    override suspend fun borrar(id: Long) = Unit
}

internal class ContextoFalso(
    private val contexto: ContextoNotas?,
) : FuenteContextoNotas {
    var llamadas = 0

    override suspend fun contexto(): ContextoNotas? {
        llamadas++
        return contexto
    }
}

internal class CalibradorFalso(
    private val factor: Double? = null,
) : CalibradorTabla {
    override suspend fun factor() = factor
}
