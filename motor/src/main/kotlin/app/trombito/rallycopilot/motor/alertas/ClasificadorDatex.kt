package app.trombito.rallycopilot.motor.alertas

import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.RANGO_LATITUD
import app.trombito.rallycopilot.motor.geo.RANGO_LONGITUD
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/** Un `situationRecord` DATEX en construcción: solo se guardan las hojas que interesan (la primera de cada nombre). */
internal class RegistroDatex(
    val xsiTipo: String?,
) {
    val hojas = HashMap<String, String>()
    private val desde = ArrayList<PuntoGeo>()
    private val puntuales = ArrayList<PuntoGeo>()
    private val hasta = ArrayList<PuntoGeo>()

    /** Puntos en orden desde, puntuales, hasta (en el XML el `to` suele venir antes que el `from`). */
    val puntos: List<PuntoGeo> get() = desde + puntuales + hasta

    fun hoja(
        nombre: String,
        texto: String,
    ) {
        if (nombre !in HOJAS || texto.isEmpty()) return
        // `roadOrCarriagewayOrLaneManagementType` sale dos veces: «other» en la causa y el real en el registro.
        if (nombre !in hojas || hojas[nombre] == "other") hojas[nombre] = texto
    }

    fun punto(
        rol: String?,
        lat: Double?,
        lon: Double?,
    ) {
        if (lat == null || lon == null || !coordenadaValida(lat, lon)) return
        val punto = PuntoGeo(lat, lon)
        when (rol) {
            "from" -> desde.add(punto)
            "to" -> hasta.add(punto)
            else -> puntuales.add(punto)
        }
    }

    private fun coordenadaValida(
        lat: Double,
        lon: Double,
    ): Boolean = lat in RANGO_LATITUD && lon in RANGO_LONGITUD

    /** `false` si el registro no está vigente (solo se descarta `suspended`; sin estado se da por vigente). */
    val vigente: Boolean get() = hojas["validityStatus"] != "suspended"

    private companion object {
        val HOJAS =
            setOf(
                "validityStatus",
                "overallStartTime",
                "overallEndTime",
                "severity",
                "causeType",
                "roadOrCarriagewayOrLaneManagementType",
                "accidentType",
                "obstructionType",
                "vehicleObstructionType",
                "environmentalObstructionType",
                "infrastructureDamageType",
                "roadMaintenanceType",
                "abnormalTrafficType",
                "speedManagementType",
                "nonWeatherRelatedRoadConditionType",
                "generalInstructionToRoadUsersType",
                "roadName",
            )
    }
}

/** Convierte los registros DATEX de una situación en una [Incidencia] (tipo, descripción corta, gravedad, fechas). */
internal object ClasificadorDatex {
    private val DETALLES =
        listOf(
            "accidentType",
            "obstructionType",
            "vehicleObstructionType",
            "environmentalObstructionType",
            "infrastructureDamageType",
            "abnormalTrafficType",
            "speedManagementType",
            "nonWeatherRelatedRoadConditionType",
            "generalInstructionToRoadUsersType",
        )

    /** De más a menos grave: el tipo de una situación con varios registros es el primero de esta lista que aparezca. */
    private val PRIORIDAD =
        listOf(
            TipoIncidencia.CORTE,
            TipoIncidencia.ACCIDENTE,
            TipoIncidencia.METEO,
            TipoIncidencia.RETENCION,
            TipoIncidencia.OBRAS,
            TipoIncidencia.OTRA,
        )

    private val GESTION =
        mapOf(
            "laneClosures" to "carril cortado",
            "carriagewayClosures" to "calzada cortada",
            "narrowLanes" to "carril estrecho",
            "singleAlternateLineTraffic" to "circulación alterna",
            "lanesDeviated" to "carril desviado",
            "newRoadworksLayout" to "nueva disposición",
            "intermittentShortTermClosures" to "cortes intermitentes",
            "doNotUseSpecifiedLanesOrCarriageways" to "carriles cerrados",
            "weightRestrictionInOperation" to "limitación de peso",
        )

    private val BASE_OTRA =
        mapOf(
            "vehicleStuck" to "Vehículo detenido",
            "vehicleOnFire" to "Vehículo en llamas",
            "vehicleWithOverwideLoad" to "Vehículo con carga ancha",
            "objectOnTheRoad" to "Objeto en la calzada",
            "spillageOnTheRoad" to "Vertido en la calzada",
            "damagedRoadSurface" to "Firme en mal estado",
            "forestFire" to "Incendio forestal",
        )

    private val BASE_METEO =
        mapOf(
            "rockfalls" to "Desprendimientos",
            "flooding" to "Inundación",
            "avalanches" to "Aludes",
        )

    private val BASE_RETENCION =
        mapOf(
            "queuingTraffic" to "Cola",
            "stationaryTraffic" to "Tráfico detenido",
            "heavyTraffic" to "Tráfico denso",
        )

    /** Tipo de un registro. */
    fun tipo(registro: RegistroDatex): TipoIncidencia {
        val h = registro.hojas
        val causa = h["causeType"]
        return when {
            h["roadOrCarriagewayOrLaneManagementType"] == "roadClosed" -> TipoIncidencia.CORTE
            registro.xsiTipo == "AbnormalTraffic" || "abnormalTrafficType" in h -> TipoIncidencia.RETENCION
            causa == "accident" || "accidentType" in h -> TipoIncidencia.ACCIDENTE
            esMeteo(h) -> TipoIncidencia.METEO
            causa == "roadMaintenance" || "roadMaintenanceType" in h -> TipoIncidencia.OBRAS
            else -> TipoIncidencia.OTRA
        }
    }

    private fun esMeteo(h: Map<String, String>): Boolean {
        val causa = h["causeType"]
        val entorno = causa == "environmentalObstruction" && h["environmentalObstructionType"] != "forestFire"
        return entorno || causa == "poorEnvironmentConditions" || causa == "weatherRelatedRoadConditions"
    }

    /** Texto corto en español: «Obras en Ma-15, calzada cortada», «Accidente», «Carretera cortada en Ma-1131». */
    fun descripcion(
        registro: RegistroDatex,
        tipo: TipoIncidencia,
    ): String {
        val h = registro.hojas
        val via = h["roadName"]?.let { " en $it" } ?: ""
        val matiz = if (tipo == TipoIncidencia.CORTE) null else matiz(h)
        return base(h, tipo) + via + (matiz?.let { ", $it" } ?: "")
    }

    private fun base(
        h: Map<String, String>,
        tipo: TipoIncidencia,
    ): String =
        when (tipo) {
            TipoIncidencia.CORTE -> "Carretera cortada"
            TipoIncidencia.ACCIDENTE -> "Accidente"
            TipoIncidencia.OBRAS -> "Obras"
            TipoIncidencia.RETENCION -> BASE_RETENCION[h["abnormalTrafficType"]] ?: "Tráfico lento"
            TipoIncidencia.METEO -> BASE_METEO[h["environmentalObstructionType"]] ?: "Condiciones adversas"
            TipoIncidencia.OTRA -> BASE_OTRA[DETALLES.firstNotNullOfOrNull { h[it] }] ?: "Incidencia"
        }

    private fun matiz(h: Map<String, String>): String? =
        GESTION[h["roadOrCarriagewayOrLaneManagementType"]]
            ?: if ("speedManagementType" in h) "limitación de velocidad" else null

    /** El tipo más grave de entre los [tipos]. */
    fun masGrave(tipos: Collection<TipoIncidencia>): TipoIncidencia = PRIORIDAD.first { it in tipos }

    fun gravedad(texto: String?): GravedadIncidencia? =
        when (texto) {
            "lowest", "low" -> GravedadIncidencia.BAJA
            "medium" -> GravedadIncidencia.MEDIA
            "high", "highest" -> GravedadIncidencia.ALTA
            else -> null
        }

    /** Fecha ISO-8601 con zona a instante; `null` si no se entiende. */
    fun instante(texto: String?): Instant? =
        try {
            texto?.let { OffsetDateTime.parse(it).toInstant() }
        } catch (_: DateTimeParseException) {
            null
        }
}
