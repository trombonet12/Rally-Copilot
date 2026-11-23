package app.trombito.rallycopilot.data.resumen

import app.trombito.rallycopilot.data.recorridos.RepositorioRecorridos
import app.trombito.rallycopilot.di.DispatcherDefault
import app.trombito.rallycopilot.domain.resumen.CalculoResumenViaje
import app.trombito.rallycopilot.domain.resumen.FuenteContextoVias
import app.trombito.rallycopilot.domain.resumen.ViajeResumido
import app.trombito.rallycopilot.domain.tramos.FiltroTramos
import app.trombito.rallycopilot.domain.tramos.RepositorioTramos
import app.trombito.rallycopilot.motor.analisis.PuntoTiempo
import app.trombito.rallycopilot.motor.analisis.ResumenViaje
import app.trombito.rallycopilot.motor.analisis.TramoHecho
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.roundToLong

/**
 * Arma el [ResumenViaje] de un recorrido terminado: sus puntos, el tiempo previsto con que se guió, el contexto de
 * vías (de dónde sale el histórico, sin este viaje) y los subtramos que se cronometraron en él comparados con el mejor
 * tiempo anterior. Todo en [dispatcher] (CPU); no escribe nada.
 */
class CalculadorResumenViaje
    @Inject
    constructor(
        private val recorridos: RepositorioRecorridos,
        private val tramos: RepositorioTramos,
        private val vias: FuenteContextoVias,
        @DispatcherDefault private val dispatcher: CoroutineDispatcher,
    ) : CalculoResumenViaje {
        override suspend fun resumenDe(recorridoId: Long): ViajeResumido? =
            withContext(dispatcher) {
                val recorrido = recorridos.listar().first().firstOrNull { it.id == recorridoId }
                val puntos =
                    recorrido?.let {
                        recorridos.puntos(recorridoId).map { p ->
                            PuntoTiempo(PuntoGeo(p.lat, p.lon), p.tiempoMs, p.velocidadMs)
                        }
                    }
                if (recorrido == null || puntos == null || puntos.size < MIN_PUNTOS) return@withContext null
                val contexto = vias.contextoSin(recorridoId)
                val previstoS = recorrido.tiempoPrevistoS?.takeIf { it > 0.0 }?.roundToLong()
                ViajeResumido(
                    recorridoId = recorridoId,
                    inicioMs = recorrido.inicioMs,
                    destinoNombre = recorrido.destinoNombre,
                    resumen =
                        ResumenViaje.de(
                            puntos = puntos,
                            previstoS = previstoS,
                            coincidenciaDe = contexto.coincidenciaDe,
                            historicoVias = contexto.historico,
                            tramos = tramosHechos(recorridoId),
                        ),
                )
            }

        /** Los subtramos del viaje por hora de salida; es récord el que mejora el mejor tiempo anterior. */
        private suspend fun tramosHechos(recorridoId: Long): List<TramoHecho> {
            val subtramos = tramos.observarSubtramosDeRecorrido(recorridoId).first()
            if (subtramos.isEmpty()) return emptyList()
            val nombres = tramos.observarResumenes(FiltroTramos.TODOS).first().associate { it.id to it.nombre }
            return subtramos.sortedBy { it.inicio }.mapNotNull { s ->
                val nombre = nombres[s.tramoRefId] ?: return@mapNotNull null
                TramoHecho(
                    nombre = nombre,
                    tiempoMs = s.tiempoMs,
                    deltaMs = s.mejorMs?.let { s.tiempoMs - it },
                    esRecord = s.mejorMs != null && s.tiempoMs < s.mejorMs,
                )
            }
        }

        private companion object {
            const val MIN_PUNTOS = 2
        }
    }
