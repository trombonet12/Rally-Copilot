package app.trombito.rallycopilot.ui.mistramos

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.trombito.rallycopilot.domain.importar.ImportaArchivo
import app.trombito.rallycopilot.domain.importar.MotivoFalloImportar
import app.trombito.rallycopilot.domain.importar.ResultadoImportar
import app.trombito.rallycopilot.domain.importar.ResultadoPreparar
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

    /** Un archivo de otra app ya leído, a la espera de que el usuario confirme: [longitudM] es lo que mide la ruta. */
    data class Pendiente(
        val nombre: String?,
        val longitudM: Double,
    ) : EstadoImportacion

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
 * («Abrir con», «Compartir») piden ir a «Mis tramos» por [irAMisTramos], se leen sin guardar y quedan [Pendiente] hasta
 * que el usuario confirma ([confirmar]) o los descarta ([cancelar]); el que elige el usuario con el selector propio se
 * importa directamente, sin preguntar.
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

        /** El archivo entrante ya leído que espera confirmación (su traza sigue en memoria hasta decidir). */
        private var pendiente: ResultadoPreparar.Lista? = null

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

        /**
         * Lee el [uri] que ha entregado otra app, lleva al usuario a «Mis tramos» y le pregunta antes de guardarlo; si
         * no se puede leer, avisa del motivo. Se ignora si ya hay una importación en curso.
         */
        fun importarEntrante(uri: Uri) {
            peticionesDeNavegacion.trySend(Unit)
            if (_estado.value == EstadoImportacion.Importando) return
            _estado.value = EstadoImportacion.Importando
            pendiente = null
            viewModelScope.launch {
                _estado.value =
                    when (val r = importador.preparar(uri.toString())) {
                        is ResultadoPreparar.Lista -> {
                            pendiente = r
                            EstadoImportacion.Pendiente(r.nombre, r.longitudM)
                        }

                        is ResultadoPreparar.Fallo -> {
                            EstadoImportacion.Fallo(r.motivo)
                        }
                    }
            }
        }

        /** El usuario confirma importar el archivo [Pendiente]. */
        fun confirmar() {
            val preparado = pendiente ?: return
            pendiente = null
            _estado.value = EstadoImportacion.Importando
            viewModelScope.launch {
                _estado.value =
                    when (val r = preparado.guardar()) {
                        is ResultadoImportar.Importado -> EstadoImportacion.Hecho(r.tramoId, r.nombre)
                        is ResultadoImportar.Fallo -> EstadoImportacion.Fallo(r.motivo)
                    }
            }
        }

        /** El usuario descarta el archivo [Pendiente]: no se guarda nada. */
        fun cancelar() {
            pendiente = null
            if (_estado.value is EstadoImportacion.Pendiente) _estado.value = EstadoImportacion.Inactivo
        }

        /** El aviso del resultado ya se mostró. */
        fun onVisto() {
            if (_estado.value != EstadoImportacion.Importando) _estado.value = EstadoImportacion.Inactivo
        }
    }
