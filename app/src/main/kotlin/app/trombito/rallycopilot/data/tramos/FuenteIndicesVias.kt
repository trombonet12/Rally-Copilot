package app.trombito.rallycopilot.data.tramos

import app.trombito.rallycopilot.data.vias.CargadorPk
import app.trombito.rallycopilot.data.vias.FuenteVias
import app.trombito.rallycopilot.motor.vias.EjesCarreteras
import app.trombito.rallycopilot.motor.vias.IndicePuntosKm
import app.trombito.rallycopilot.motor.vias.IndiceVias
import app.trombito.rallycopilot.motor.vias.TroceadorVias
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Los índices que necesita el troceado: las vías y, si hay archivo de hitos, los puntos kilométricos. Los [ejes] de
 * las carreteras (PK y sentido de cada tramo) se construyen perezosamente una vez y se comparten.
 */
class IndicesDeVias(
    val vias: IndiceVias,
    val pk: IndicePuntosKm?,
) {
    val ejes: EjesCarreteras by lazy { EjesCarreteras(vias.vias, pk) }

    /** Un troceador de viajes con estos índices; [minLongitudM] por defecto es el del troceado normal. */
    fun troceador(minLongitudM: Double? = null): TroceadorVias =
        if (minLongitudM == null) {
            TroceadorVias(vias, pk, ejes = ejes)
        } else {
            TroceadorVias(vias, pk, minLongitudM = minLongitudM, ejes = ejes)
        }
}

/** De dónde salen los índices del troceado. */
fun interface FuenteIndicesVias {
    /** Null si no hay vías instaladas (sin ellas no se puede trocear: se reintenta más adelante). */
    suspend fun indices(): IndicesDeVias?
}

/** Los índices de los datos instalados, los mismos que usan la vía actual y las notas. */
@Singleton
class FuenteIndicesViasOsm
    @Inject
    constructor(
        private val vias: FuenteVias,
        private val pk: CargadorPk,
    ) : FuenteIndicesVias {
        private class Memoria(
            val vias: IndiceVias,
            val pk: IndicePuntosKm?,
            val indices: IndicesDeVias,
        )

        @Volatile
        private var memoria: Memoria? = null

        /** Reutiliza los mismos [IndicesDeVias] (y sus ejes) mientras los datos cargados no cambien. */
        override suspend fun indices(): IndicesDeVias? {
            val datos = vias.datos() ?: return null
            val indicePk = pk.indice()
            val actual = memoria
            return if (actual != null && actual.vias === datos.indice && actual.pk === indicePk) {
                actual.indices
            } else {
                IndicesDeVias(datos.indice, indicePk).also { memoria = Memoria(datos.indice, indicePk, it) }
            }
        }
    }
