package app.trombito.rallycopilot.motor.alertas

import kotlin.math.roundToInt

/** Datos extra de una situación DATEX ya combinados (ver [Incidencia]); todo `null` si el feed no los trae. */
internal data class DetallesDatex(
    val pkDesde: Double?,
    val pkHasta: Double?,
    val sentido: String?,
    val municipio: String?,
    val carrilesAfectados: String?,
    val limiteTemporalKmh: Int?,
    val comentario: String?,
) {
    companion object {
        private val SENTIDOS =
            mapOf(
                "northBound" to "norte",
                "northEastBound" to "noreste",
                "eastBound" to "este",
                "southEastBound" to "sureste",
                "southBound" to "sur",
                "southWestBound" to "suroeste",
                "westBound" to "oeste",
                "northWestBound" to "noroeste",
                "bothWays" to "ambos sentidos",
                "allDirections" to "todos los sentidos",
                "clockwise" to "sentido horario",
                "anticlockwise" to "sentido antihorario",
                "inboundTowardsTown" to "hacia el centro",
                "outboundFromTown" to "desde el centro",
                "innerRing" to "anillo interior",
                "outerRing" to "anillo exterior",
            )

        private val CARRILES =
            mapOf(
                "allLanesCompleteCarriageway" to "todos los carriles",
                "rightLane" to "carril derecho",
                "leftLane" to "carril izquierdo",
                "middleLane" to "carril central",
                "hardShoulder" to "arcén",
                "emergencyLane" to "carril de emergencia",
                "busLane" to "carril bus",
                "crawlerLane" to "carril lento",
                "heavyVehicleLane" to "carril de vehículos pesados",
            )

        private val CALZADAS =
            mapOf(
                "entrySlipRoad" to "ramal de entrada",
                "exitSlipRoad" to "ramal de salida",
                "slipRoads" to "ramales",
                "flyover" to "paso elevado",
                "underpass" to "paso inferior",
            )

        private val CARRIL_NUMERADO = Regex("lane([1-9])")

        /** Combina los registros vigentes de una situación sin repetir valores. */
        fun de(registros: List<RegistroDatex>): DetallesDatex {
            val pks = registros.flatMap { it.listas["kilometerPoint"].orEmpty() }.mapNotNull { it.toDoubleOrNull() }
            val pkDesde = pks.minOrNull()
            val pkHasta = pks.maxOrNull()?.takeIf { it != pkDesde }
            val limites = registros.mapNotNull { limite(it.hojas["temporarySpeedLimit"]) }
            return DetallesDatex(
                pkDesde = pkDesde,
                pkHasta = pkHasta,
                sentido = unir(registros.mapNotNull(::sentido)),
                municipio = unir(registros.flatMap { it.listas["municipality"].orEmpty() }),
                carrilesAfectados = unir(registros.flatMap(::carriles)),
                limiteTemporalKmh = limites.minOrNull(),
                comentario =
                    registros
                        .mapNotNull(::comentario)
                        .distinct()
                        .joinToString(" ")
                        .ifEmpty { null },
            )
        }

        /** Destino de la vía (tiene prioridad) o, si no, el sentido de la marcha en español. */
        private fun sentido(r: RegistroDatex): String? =
            unir(r.listas["roadDestination"].orEmpty()) ?: SENTIDOS[r.hojas["tpegDirection"]]

        private fun carriles(r: RegistroDatex): List<String> =
            r.listas["laneUsage"].orEmpty().mapNotNull(::carril) + listOfNotNull(CALZADAS[r.hojas["carriageway"]])

        private fun carril(valor: String): String? =
            CARRILES[valor] ?: CARRIL_NUMERADO.matchEntire(valor)?.let { "carril ${it.groupValues[1]}" }

        /** Límite en km/h; `null` si no es un número positivo. */
        private fun limite(texto: String?): Int? = texto?.toDoubleOrNull()?.roundToInt()?.takeIf { it > 0 }

        /** El comentario en español; si no hay, el que no indica idioma; si no, el primero. */
        private fun comentario(r: RegistroDatex): String? =
            r.comentarios.entries
                .firstOrNull { it.key?.startsWith("es", ignoreCase = true) == true }
                ?.value
                ?: r.comentarios[null]
                ?: r.comentarios.values.firstOrNull()

        /** «a», «a y b», «a, b y c», sin repetidos; `null` si no hay nada. */
        private fun unir(valores: List<String>): String? {
            val unicos = valores.distinct()
            return when (unicos.size) {
                0 -> null
                1 -> unicos[0]
                else -> unicos.dropLast(1).joinToString(", ") + " y " + unicos.last()
            }
        }
    }
}
