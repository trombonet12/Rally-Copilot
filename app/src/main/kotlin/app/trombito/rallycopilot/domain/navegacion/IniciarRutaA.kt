package app.trombito.rallycopilot.domain.navegacion

import app.trombito.rallycopilot.domain.ritmo.ProveedorPerfilRitmo
import app.trombito.rallycopilot.domain.ritmo.SinPerfilRitmo
import app.trombito.rallycopilot.domain.ritmo.tiempoEstimadoS
import app.trombito.rallycopilot.domain.ruta.Enrutador
import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.domain.ruta.ResultadoRuta
import app.trombito.rallycopilot.domain.ubicacion.FuenteUbicacion
import app.trombito.rallycopilot.domain.ubicacion.posicionActual
import app.trombito.rallycopilot.motor.ruta.PerfilRitmo
import kotlinx.coroutines.withTimeoutOrNull

/** Qué pasó al pedir una ruta a un destino sin pasar por la vista previa (por ejemplo, por voz). */
sealed interface ResultadoInicioRuta {
    /**
     * Guiando hacia el destino; [tiempoS] es el tiempo estimado con el ritmo aprendido (no el de Valhalla) y [distanciaM]
     * la de la ruta calculada (con las paradas pendientes).
     */
    data class Iniciada(
        val tiempoS: Double,
        val distanciaM: Double,
    ) : ResultadoInicioRuta

    /** No hay sesión de conducción abierta (el servicio solo se arranca desde el mapa). */
    data object SinConduccion : ResultadoInicioRuta

    /** Sin fijación GPS a tiempo. */
    data object SinPosicion : ResultadoInicioRuta

    /** No están instalados los datos de rutas. */
    data object SinDatos : ResultadoInicioRuta

    data class Error(
        val motivo: String,
    ) : ResultadoInicioRuta
}

/**
 * «Ir a [destino]» sin vista previa: calcula la ruta desde la posición actual (la más rápida; guiando, con el perfil y
 * las paradas pendientes del guiado) y empieza el guiado, o, si ya se guía, cambia el destino final conservando las
 * paradas ([ControlNavegacion.cambiarDestino]). Lo usan las órdenes de voz; la vista previa del mapa tiene su propio
 * flujo en la UI. El tiempo que devuelve (y que dice la voz) es el estimado con el [perfilRitmo], como en la vista
 * previa.
 */
class IniciarRutaA(
    private val control: ControlNavegacion,
    private val enrutador: Enrutador,
    private val ubicacion: FuenteUbicacion,
    private val perfilRitmo: ProveedorPerfilRitmo = SinPerfilRitmo,
) {
    @Suppress("ReturnCount") // cada salida temprana es un resultado distinto (sin sesión, sin posición)
    suspend operator fun invoke(destino: Destino): ResultadoInicioRuta {
        val estado = control.estado.value
        if (estado is EstadoNavegacion.Inactiva) return ResultadoInicioRuta.SinConduccion
        val origen = ubicacion.posicionActual() ?: return ResultadoInicioRuta.SinPosicion
        val guiando = estado as? EstadoNavegacion.Guiando
        val perfil = guiando?.perfil ?: PerfilRuta.MAS_RAPIDA
        val paradas = guiando?.paradas.orEmpty()
        return when (
            val resultado =
                enrutador.rutaConParadas(
                    origen,
                    paradas.map { it.punto },
                    destino.punto,
                    perfil,
                )
        ) {
            is ResultadoRuta.Ok -> {
                if (guiando != null) {
                    control.cambiarDestino(destino)
                } else {
                    control.empezar(destino, perfil, resultado.ruta)
                }
                val perfil = withTimeoutOrNull(ESPERA_PERFIL_MS) { perfilRitmo.perfil() } ?: PerfilRitmo()
                ResultadoInicioRuta.Iniciada(resultado.ruta.tiempoEstimadoS(perfil), resultado.ruta.longitudM)
            }

            ResultadoRuta.SinDatos -> {
                ResultadoInicioRuta.SinDatos
            }

            is ResultadoRuta.Error -> {
                ResultadoInicioRuta.Error(resultado.motivo)
            }
        }
    }
}

/** Lo máximo que se espera al perfil de ritmo; pasado, el tiempo se estima con el perfil de partida. */
private const val ESPERA_PERFIL_MS = 1_500L
