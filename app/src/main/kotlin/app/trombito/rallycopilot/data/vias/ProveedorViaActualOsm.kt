package app.trombito.rallycopilot.data.vias

import app.trombito.rallycopilot.domain.vias.ProveedorViaActual
import app.trombito.rallycopilot.domain.vias.ViaActual
import app.trombito.rallycopilot.motor.geo.SentidoCamino
import app.trombito.rallycopilot.motor.velocidad.Fijacion
import app.trombito.rallycopilot.motor.vias.IndiceVias
import app.trombito.rallycopilot.motor.vias.LimiteVelocidad
import app.trombito.rallycopilot.motor.vias.SeguidorVia
import app.trombito.rallycopilot.motor.vias.SentidoUnico
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
        private var indice: IndiceVias? = null
        private var seguidor: SeguidorVia? = null

        /**
         * Sin cambio de hilo ni espera por fijación (REN-16): usa las vías ya en memoria y solo pide [FuenteVias.datos]
         * (que espera a la siembra, lee y revalida el archivo) mientras no estén cargadas.
         */
        override suspend fun viaEn(fijacion: Fijacion): ViaActual? =
            coincidir(fuente.enMemoria() ?: fuente.datos(), fijacion)

        /** El [SeguidorVia] tiene estado y lo llaman el mapa y el servicio: se serializa con un candado corto. */
        @Synchronized
        private fun coincidir(
            datos: DatosVias?,
            fijacion: Fijacion,
        ): ViaActual? {
            val coincidencia = seguidorPara(datos)?.actualizar(fijacion.punto, fijacion.rumboGrados) ?: return null
            val via = coincidencia.via
            return ViaActual(
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

        private fun seguidorPara(datos: DatosVias?): SeguidorVia? {
            val nuevo = datos?.indice
            if (datos == null || nuevo == null) {
                indice = null
                seguidor = null
            } else if (nuevo !== indice) {
                indice = nuevo
                seguidor = SeguidorVia(nuevo, grafo = datos.grafo)
            }
            return seguidor
        }
    }
