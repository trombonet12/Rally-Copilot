package app.trombito.rallycopilot.ui.ajustes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.trombito.rallycopilot.domain.copia.CopiaDatos
import app.trombito.rallycopilot.domain.copia.CopiaInvalidaException
import app.trombito.rallycopilot.domain.copia.MotivoCopiaInvalida
import app.trombito.rallycopilot.domain.copia.ReiniciadorApp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

/** Cómo va la copia de seguridad que se ve en Ajustes. */
sealed interface EstadoCopia {
    data object Inactiva : EstadoCopia

    /** Exportando o importando: bloquea las filas y enseña el progreso. */
    data object Trabajando : EstadoCopia

    data object Exportada : EstadoCopia

    /** Importada: la app se reinicia en un momento. */
    data object Importada : EstadoCopia

    data class Fallo(
        val motivo: MotivoCopiaInvalida,
    ) : EstadoCopia
}

/**
 * Exportar e importar la copia de seguridad. Los flujos los abre la pantalla (SAF) y se pasan como lambdas, así el
 * ViewModel no sabe de `Uri` ni de `ContentResolver`. Tras importar bien se avisa y se reinicia la app.
 */
@HiltViewModel
class CopiaViewModel
    @Inject
    constructor(
        private val copia: CopiaDatos,
        private val reiniciador: ReiniciadorApp,
    ) : ViewModel() {
        private val _estado = MutableStateFlow<EstadoCopia>(EstadoCopia.Inactiva)
        val estado: StateFlow<EstadoCopia> = _estado.asStateFlow()

        fun exportar(abrir: () -> OutputStream?) {
            if (!empezar()) return
            viewModelScope.launch {
                _estado.value =
                    try {
                        val salida = abrir() ?: throw IOException("no se pudo abrir la salida")
                        salida.use { copia.exportar(it) }
                        EstadoCopia.Exportada
                    } catch (ignored: IOException) {
                        EstadoCopia.Fallo(MotivoCopiaInvalida.NO_SE_PUEDE_ABRIR)
                    } catch (e: CopiaInvalidaException) {
                        EstadoCopia.Fallo(e.motivo)
                    }
            }
        }

        fun importar(abrir: () -> InputStream?) {
            if (!empezar()) return
            viewModelScope.launch {
                try {
                    val entrada = abrir() ?: throw IOException("no se pudo abrir la entrada")
                    entrada.use { copia.importar(it) }
                    _estado.value = EstadoCopia.Importada
                    // La base ya se cerró: se reinicia aunque se salga de Ajustes durante el aviso.
                    withContext(NonCancellable) {
                        delay(PAUSA_AVISO_MS)
                        reiniciador.reiniciar()
                    }
                } catch (ignored: IOException) {
                    _estado.value = EstadoCopia.Fallo(MotivoCopiaInvalida.NO_SE_PUEDE_ABRIR)
                } catch (e: CopiaInvalidaException) {
                    _estado.value = EstadoCopia.Fallo(e.motivo)
                }
            }
        }

        /** El aviso (snackbar) ya se enseñó. */
        fun avisoVisto() {
            if (_estado.value is EstadoCopia.Exportada || _estado.value is EstadoCopia.Fallo) {
                _estado.value = EstadoCopia.Inactiva
            }
        }

        private fun empezar(): Boolean {
            val libre = _estado.value !is EstadoCopia.Trabajando && _estado.value !is EstadoCopia.Importada
            if (libre) _estado.value = EstadoCopia.Trabajando
            return libre
        }

        private companion object {
            const val PAUSA_AVISO_MS = 1_800L
        }
    }
