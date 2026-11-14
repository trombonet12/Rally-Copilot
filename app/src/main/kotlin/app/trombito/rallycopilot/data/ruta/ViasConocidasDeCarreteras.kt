package app.trombito.rallycopilot.data.ruta

import app.trombito.rallycopilot.data.vias.FuenteVias
import app.trombito.rallycopilot.domain.carreteras.CalculoCarreteras
import app.trombito.rallycopilot.domain.ruta.ViasConocidas
import app.trombito.rallycopilot.motor.analisis.AgregadoCarreteras
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.geo.Rumbo
import app.trombito.rallycopilot.motor.vias.Via
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import kotlin.math.ceil

/**
 * [ViasConocidas] provisional sobre «Mis carreteras»: las vías conducidas son las del agregado de
 * [CalculoCarreteras] y la ruta se asigna a las vías de OSM con el mismo emparejado que usa el agregado. Sin vías
 * instaladas o sin recorridos devuelve 0 (la ruta se elige como siempre). El agregado usa la caché por recorrido de
 * `CalculadorCarreteras`, así que consultar varias rutas seguidas solo calcula lo nuevo.
 */
class ViasConocidasDeCarreteras
    @Inject
    constructor(
        private val carreteras: CalculoCarreteras,
        private val vias: FuenteVias,
    ) : ViasConocidas {
        override suspend fun metrosConocidos(puntos: List<PuntoGeo>): Double {
            val datos = vias.datos()
            val conocidas = if (datos == null) emptySet() else idsConducidos()
            return if (datos == null || conocidas.isEmpty()) {
                0.0
            } else {
                medir(puntos, conocidas) { punto, rumbo ->
                    datos.indice.cercana(punto, rumbo, AgregadoCarreteras.RADIO_VIA_M)?.via
                }
            }
        }

        override suspend fun hayHistorial(): Boolean = vias.datos() != null && idsConducidos().isNotEmpty()

        private suspend fun idsConducidos(): Set<Long> {
            val resumen = carreteras.observar().first().resumen
            return resumen.vias.mapTo(HashSet()) { it.via.id }
        }

        companion object {
            /** Paso de remuestreo de la ruta, el mismo que el agregado de Mis carreteras. */
            private const val PASO_M = AgregadoCarreteras.PASO_M
            private const val MITAD = 0.5

            /**
             * Metros de [puntos] cuya vía ([viaDe]) está en [conocidas]: cada segmento se reparte en trozos de unos
             * [PASO_M] metros y se mira la vía del punto medio de cada uno, con el rumbo del segmento.
             */
            internal fun medir(
                puntos: List<PuntoGeo>,
                conocidas: Set<Long>,
                viaDe: AgregadoCarreteras.ViaDe,
            ): Double {
                var conocidosM = 0.0
                for ((a, b) in puntos.zipWithNext()) {
                    val largo = a.distanciaA(b)
                    if (largo <= 0.0) continue
                    val n = ceil(largo / PASO_M).toInt().coerceAtLeast(1)
                    val rumbo = Rumbo.entre(a, b)
                    for (i in 0 until n) {
                        val t = (i + MITAD) / n
                        val punto = PuntoGeo(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)
                        val via: Via? = viaDe.via(punto, rumbo)
                        if (via != null && via.id in conocidas) conocidosM += largo / n
                    }
                }
                return conocidosM
            }
        }
    }
