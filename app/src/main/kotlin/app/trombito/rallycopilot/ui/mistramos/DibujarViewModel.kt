package app.trombito.rallycopilot.ui.mistramos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.trombito.rallycopilot.domain.ruta.Enrutador
import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.domain.ruta.ResultadoRuta
import app.trombito.rallycopilot.domain.tramos.GuardaTraza
import app.trombito.rallycopilot.domain.tramos.ResultadoGuardado
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Qué falló al dibujar o guardar la ruta. */
enum class ErrorDibujo {
    /** No están instalados los datos de rutas. */
    SIN_DATOS,

    /** No se pudo calcular la ruta entre los puntos. */
    RUTA,

    /** La ruta mide menos de un kilómetro. */
    CORTA,

    /** La base de datos no pudo guardarla. */
    NO_GUARDADA,
}

/**
 * Estado del modo dibujo. [puntos] son los toques del usuario; [ruta] es la geometría calculada entre ellos por las
 * carreteras (vacía mientras hay menos de dos puntos). [guardado] es el id del tramo creado: la pantalla navega a él.
 */
data class DibujarUiState(
    val puntos: List<PuntoGeo> = emptyList(),
    val ruta: List<PuntoGeo> = emptyList(),
    val longitudM: Double = 0.0,
    val calculando: Boolean = false,
    val guardando: Boolean = false,
    val error: ErrorDibujo? = null,
    val guardado: String? = null,
) {
    /** Se puede guardar: hay ruta calculada y no se está calculando ni guardando. */
    val puedeGuardar: Boolean get() = ruta.size >= 2 && !calculando && !guardando && guardado == null
}

/**
 * Dibujar una ruta tocando el mapa: cada toque añade un punto (el primero es la salida, el último la meta y los demás
 * son paradas intermedias); la ruta entre ellos se calcula sin red con [Enrutador.rutaConParadas] y «Guardar» la
 * convierte en un tramo sin pasadas con [GuardaTraza].
 */
@HiltViewModel
class DibujarViewModel
    @Inject
    constructor(
        private val enrutador: Enrutador,
        private val guardador: GuardaTraza,
    ) : ViewModel() {
        private val dibujo = MutableStateFlow(DibujarUiState())
        val uiState: StateFlow<DibujarUiState> = dibujo

        private var calculo: Job? = null

        fun onToque(punto: PuntoGeo) = cambiarPuntos(dibujo.value.puntos + punto)

        /** Quita el último punto. */
        fun onDeshacer() {
            val puntos = dibujo.value.puntos
            if (puntos.isNotEmpty()) cambiarPuntos(puntos.dropLast(1))
        }

        /** Borra todos los puntos. */
        fun onBorrarTodo() = cambiarPuntos(emptyList())

        fun onErrorVisto() = dibujo.update { it.copy(error = null) }

        fun onGuardar() {
            val actual = dibujo.value
            if (!actual.puedeGuardar) return
            dibujo.update { it.copy(guardando = true, error = null) }
            viewModelScope.launch {
                val resultado = guardador.guardar(actual.ruta, null)
                dibujo.update {
                    when (resultado) {
                        is ResultadoGuardado.Guardado -> it.copy(guardando = false, guardado = resultado.tramoId)
                        ResultadoGuardado.Corto -> it.copy(guardando = false, error = ErrorDibujo.CORTA)
                        ResultadoGuardado.Fallo -> it.copy(guardando = false, error = ErrorDibujo.NO_GUARDADA)
                    }
                }
            }
        }

        private fun cambiarPuntos(nuevos: List<PuntoGeo>) {
            calculo?.cancel()
            if (nuevos.size < 2) {
                dibujo.update {
                    it.copy(puntos = nuevos, ruta = emptyList(), longitudM = 0.0, calculando = false, error = null)
                }
                return
            }
            dibujo.update { it.copy(puntos = nuevos, calculando = true, error = null) }
            calculo =
                viewModelScope.launch {
                    val resultado =
                        enrutador.rutaConParadas(
                            nuevos.first(),
                            nuevos.subList(1, nuevos.size - 1),
                            nuevos.last(),
                            PerfilRuta.MAS_RAPIDA,
                        )
                    dibujo.update {
                        when (resultado) {
                            is ResultadoRuta.Ok -> {
                                it.copy(
                                    ruta = resultado.ruta.puntos,
                                    longitudM = resultado.ruta.longitudM,
                                    calculando = false,
                                )
                            }

                            ResultadoRuta.SinDatos -> {
                                it.copy(
                                    ruta = emptyList(),
                                    longitudM = 0.0,
                                    calculando = false,
                                    error = ErrorDibujo.SIN_DATOS,
                                )
                            }

                            is ResultadoRuta.Error -> {
                                it.copy(
                                    ruta = emptyList(),
                                    longitudM = 0.0,
                                    calculando = false,
                                    error = ErrorDibujo.RUTA,
                                )
                            }
                        }
                    }
                }
        }
    }
