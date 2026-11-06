package app.trombito.rallycopilot.domain.navegacion

import app.trombito.rallycopilot.domain.ruta.Enrutador
import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.domain.ruta.ResultadoRuta
import app.trombito.rallycopilot.domain.ubicacion.FuenteUbicacion
import app.trombito.rallycopilot.domain.ubicacion.posicionActual

/** Qué pasó al pedir una ruta a un destino sin pasar por la vista previa (por ejemplo, por voz). */
sealed interface ResultadoInicioRuta {
    /** Guiando hacia el destino; [tiempoS] y [distanciaM] son los de la ruta calculada (con las paradas pendientes). */
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
 * flujo en la UI.
 */
class IniciarRutaA(
    private val control: ControlNavegacion,
    private val enrutador: Enrutador,
    private val ubicacion: FuenteUbicacion,
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
                ResultadoInicioRuta.Iniciada(resultado.ruta.tiempoS, resultado.ruta.longitudM)
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
