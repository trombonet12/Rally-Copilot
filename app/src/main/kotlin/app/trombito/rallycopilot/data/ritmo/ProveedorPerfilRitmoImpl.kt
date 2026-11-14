package app.trombito.rallycopilot.data.ritmo

import app.trombito.rallycopilot.data.recorridos.Recorrido
import app.trombito.rallycopilot.data.recorridos.RepositorioRecorridos
import app.trombito.rallycopilot.di.ApplicationScope
import app.trombito.rallycopilot.domain.carreteras.CalculoCarreteras
import app.trombito.rallycopilot.domain.ritmo.ConstructorPerfilRitmo
import app.trombito.rallycopilot.domain.ritmo.ProveedorPerfilRitmo
import app.trombito.rallycopilot.motor.ruta.PerfilRitmo
import app.trombito.rallycopilot.motor.ruta.ViajeHistorico
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [PerfilRitmo] de Joan: la velocidad media real por carretera sale de [CalculoCarreteras] (que ya cachea el análisis
 * de cada recorrido) y el factor personal de los recorridos guiados que llegaron al destino pedido, sin cambiar de
 * destino ni de paradas por el camino, con su tiempo previsto (real = fin − inicio). El cálculo se lanza en [alcance]
 * (el de la app), así que si quien lo pide se cansa de esperar no se pierde: la próxima petición lo encuentra hecho.
 * Se cachea mientras la lista de recorridos terminados sea la misma, y se recalcula al cerrar uno nuevo (o borrar uno).
 * Si el cálculo falla devuelve el perfil de partida y no se cachea, para reintentarlo luego.
 */
@Singleton
class ProveedorPerfilRitmoImpl
    @Inject
    constructor(
        private val carreteras: CalculoCarreteras,
        private val recorridos: RepositorioRecorridos,
        @ApplicationScope private val alcance: CoroutineScope,
    ) : ProveedorPerfilRitmo {
        private class Calculo(
            val recorridos: List<Recorrido>,
            /** El perfil, o null si el cálculo falló. */
            val perfil: Deferred<PerfilRitmo?>,
        )

        private var vigente: Calculo? = null

        override suspend fun perfil(): PerfilRitmo {
            val lista = recorridos.listar().first()
            val calculo =
                synchronized(this) {
                    vigente?.takeIf { it.recorridos == lista }
                        ?: Calculo(lista, alcance.async { calcular(lista) }).also { vigente = it }
                }
            val perfil = calculo.perfil.await()
            if (perfil == null) {
                synchronized(this) { if (vigente === calculo) vigente = null }
                return PerfilRitmo()
            }
            return perfil
        }

        private suspend fun calcular(lista: List<Recorrido>): PerfilRitmo? {
            val resultado =
                runCatching {
                    val velocidades = carreteras.observar().first().velocidades
                    val viajes =
                        lista.filter { it.llegado }.map {
                            ViajeHistorico(
                                (it.finMs - it.inicioMs) / MS_POR_S,
                                it.tiempoPrevistoS,
                            )
                        }
                    ConstructorPerfilRitmo.construir(velocidades, viajes)
                }
            resultado.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            return resultado.getOrNull()
        }

        private companion object {
            const val MS_POR_S = 1_000.0
        }
    }
