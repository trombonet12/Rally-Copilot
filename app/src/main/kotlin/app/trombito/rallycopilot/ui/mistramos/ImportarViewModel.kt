package app.trombito.rallycopilot.ui.mistramos

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.trombito.rallycopilot.domain.importar.ImportaArchivo
import app.trombito.rallycopilot.domain.importar.MotivoFalloImportar
import app.trombito.rallycopilot.domain.importar.ResultadoImportar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Estado de la importación de un GPX/KML, que sigue vivo hasta que «Mis tramos» muestra el aviso. */
sealed interface EstadoImportacion {
    data object Inactivo : EstadoImportacion

    data object Importando : EstadoImportacion

    data class Hecho(
        val tramoId: String,
        val nombre: String?,
    ) : EstadoImportacion

    data class Fallo(
        val motivo: MotivoFalloImportar,
    ) : EstadoImportacion
}

/**
 * Importa archivos GPX/KML como tramos. Es de la actividad (no de una pantalla): los archivos que llegan desde fuera
 * («Abrir con», «Compartir») se importan al abrir la app y piden ir a «Mis tramos» por [irAMisTramos].
 */
@HiltViewModel
class ImportarViewModel
    @Inject
    constructor(
        private val importador: ImportaArchivo,
    ) : ViewModel() {
        private val _estado = MutableStateFlow<EstadoImportacion>(EstadoImportacion.Inactivo)
        val estado: StateFlow<EstadoImportacion> = _estado

        private val peticionesDeNavegacion = Channel<Unit>(Channel.CONFLATED)

        /** Emite cuando un archivo llegado de fuera debe mostrarse en «Mis tramos». */
        val irAMisTramos: Flow<Unit> = peticionesDeNavegacion.receiveAsFlow()

        /** Importa el [uri] elegido en «Mis tramos»; se ignora si ya hay una importación en curso. */
        fun importar(uri: Uri) {
            if (_estado.value == EstadoImportacion.Importando) return
            _estado.value = EstadoImportacion.Importando
            viewModelScope.launch {
                _estado.value =
                    when (val r = importador.importar(uri.toString())) {
                        is ResultadoImportar.Importado -> EstadoImportacion.Hecho(r.tramoId, r.nombre)
                        is ResultadoImportar.Fallo -> EstadoImportacion.Fallo(r.motivo)
                    }
            }
        }

        /** Importa el [uri] que ha entregado otra app y lleva al usuario a «Mis tramos». */
        fun importarEntrante(uri: Uri) {
            peticionesDeNavegacion.trySend(Unit)
            importar(uri)
        }

        /** El aviso del resultado ya se mostró. */
        fun onVisto() {
            if (_estado.value != EstadoImportacion.Importando) _estado.value = EstadoImportacion.Inactivo
        }
    }
