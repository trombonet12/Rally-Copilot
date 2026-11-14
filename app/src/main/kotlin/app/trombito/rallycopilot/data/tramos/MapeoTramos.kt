package app.trombito.rallycopilot.data.tramos

import app.trombito.rallycopilot.data.db.PasadaEntity
import app.trombito.rallycopilot.data.db.TIPO_VIA
import app.trombito.rallycopilot.data.db.TIPO_VIAJE
import app.trombito.rallycopilot.data.db.TramoEntity
import app.trombito.rallycopilot.domain.tramos.DatosVia
import app.trombito.rallycopilot.domain.tramos.OrigenPasada
import app.trombito.rallycopilot.domain.tramos.TipoTramo
import app.trombito.rallycopilot.motor.ruta.Polyline6
import app.trombito.rallycopilot.motor.tramos.Pasada
import app.trombito.rallycopilot.motor.tramos.Tramo

/** El tramo guardado, o null si la fila no se puede leer (datos dañados): un tramo roto no debe romper el resto. */
internal fun TramoEntity.aTramo(): Tramo? =
    try {
        Tramo(
            id = id,
            traza = Polyline6.decodificar(traza),
            longitudM = longitudM,
            sectores = CodecTramos.decodificarCortes(sectores),
            microsectores = CodecTramos.decodificarCortes(microsectores),
        )
    } catch (_: IllegalArgumentException) {
        null
    }

/** El tipo del tramo guardado; una cadena desconocida se trata como viaje. */
internal fun TramoEntity.aTipo(): TipoTramo = if (tipo == TIPO_VIA) TipoTramo.VIA else TipoTramo.VIAJE

/** La entidad del tramo; con [via] es un tramo de vía (`VIA`) y sin ella un viaje entero (`VIAJE`). */
internal fun Tramo.aEntidad(
    nombre: String?,
    creadoEnMs: Long,
    nombreAutomatico: String? = null,
    via: DatosVia? = null,
) = TramoEntity(
    id = id,
    nombre = nombre,
    traza = Polyline6.codificar(traza),
    longitudM = longitudM,
    sectores = CodecTramos.codificarCortes(sectores),
    microsectores = CodecTramos.codificarCortes(microsectores),
    creadoEn = creadoEnMs,
    nombreAutomatico = nombreAutomatico,
    tipo = if (via == null) TIPO_VIAJE else TIPO_VIA,
    viaRef = via?.viaRef,
    pkDesde = via?.pkDesde,
    pkHasta = via?.pkHasta,
    sentido = via?.sentido?.name,
    destino = via?.destino,
    recorridoOrigenId = via?.recorridoOrigenId,
)

/** La pasada guardada, o null si la fila no se puede leer. [cortesM]: ver [CodecTramos.decodificarMarcas]. */
internal fun PasadaEntity.aPasada(cortesM: Collection<Double>): Pasada? =
    try {
        Pasada(tramoId, CodecTramos.decodificarMarcas(marcas, cortesM), totalMs)
    } catch (_: IllegalArgumentException) {
        null
    }

/** Distancias de los cortes de [Tramo] que sus pasadas guardan exactas: sectores, microsectores y meta. */
internal fun Tramo.cortesExactosM(): List<Double> = sectores + microsectores + metaM

internal fun Pasada.aEntidad(
    origen: OrigenPasada,
    obligatoriasM: Collection<Double>,
) = PasadaEntity(
    tramoId = tramoId,
    inicio = origen.inicioMs,
    totalMs = totalMs,
    marcas = CodecTramos.codificarMarcas(CodecTramos.compactar(tiempos, obligatoriasM)),
    recorridoId = origen.recorridoId,
    puntosDesdeMs = origen.puntosDesdeMs,
    puntosHastaMs = origen.puntosHastaMs,
)
