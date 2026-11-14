package app.trombito.rallycopilot.data.tramos

import app.trombito.rallycopilot.data.vias.CargadorPk
import app.trombito.rallycopilot.data.vias.FuenteVias
import app.trombito.rallycopilot.motor.vias.IndicePuntosKm
import app.trombito.rallycopilot.motor.vias.IndiceVias
import javax.inject.Inject
import javax.inject.Singleton

/** Los índices que necesita el troceado: las vías y, si hay archivo de hitos, los puntos kilométricos. */
class IndicesDeVias(
    val vias: IndiceVias,
    val pk: IndicePuntosKm?,
)

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
        override suspend fun indices(): IndicesDeVias? = vias.datos()?.let { IndicesDeVias(it.indice, pk.indice()) }
    }
