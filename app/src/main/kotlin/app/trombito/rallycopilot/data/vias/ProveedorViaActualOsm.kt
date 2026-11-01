package app.trombito.rallycopilot.data.vias

import app.trombito.rallycopilot.domain.vias.ProveedorViaActual
import app.trombito.rallycopilot.domain.vias.ViaActual
import app.trombito.rallycopilot.motor.geo.SentidoCamino
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import app.trombito.rallycopilot.motor.vias.IndiceVias
import app.trombito.rallycopilot.motor.vias.LimiteVelocidad
import app.trombito.rallycopilot.motor.vias.SeguidorVia
import app.trombito.rallycopilot.motor.vias.SentidoUnico
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Vía y límite de velocidad a partir de las vías que da [fuente] (compartidas con la generación de notas). Si no hay
 * vías, [viaEn] devuelve null. El [SeguidorVia] (con estado) se rehace cuando la fuente entrega un índice nuevo.
 */
@Singleton
class ProveedorViaActualOsm
    @Inject
    constructor(
        private val fuente: FuenteVias,
    ) : ProveedorViaActual {
        private val mutex = Mutex()
        private var indice: IndiceVias? = null
        private var seguidor: SeguidorVia? = null

        override suspend fun viaEn(fijacion: Fijacion): ViaActual? =
            mutex.withLock {
                val seguidor = seguidorActual() ?: return@withLock null
                val coincidencia = seguidor.actualizar(fijacion.punto, fijacion.rumboGrados) ?: return@withLock null
                val via = coincidencia.via
                ViaActual(
                    via.nombre,
                    via.ref,
                    LimiteVelocidad.aplicable(via, coincidencia.adelante),
                    via.puntos,
                    when (via.sentidoUnico) {
                        SentidoUnico.NO -> SentidoCamino.AMBOS
                        SentidoUnico.ADELANTE -> SentidoCamino.ADELANTE
                        SentidoUnico.ATRAS -> SentidoCamino.ATRAS
                    },
                )
            }

        private suspend fun seguidorActual(): SeguidorVia? {
            val nuevo = fuente.datos()?.indice
            if (nuevo == null) {
                indice = null
                seguidor = null
            } else if (nuevo !== indice) {
                indice = nuevo
                seguidor = SeguidorVia(nuevo)
            }
            return seguidor
        }
    }
