package app.trombito.rallycopilot.ui

import androidx.lifecycle.ViewModel
import app.trombito.rallycopilot.domain.datos.RepositorioDatosMapa
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * ViewModel de la actividad: al abrir la app pide al repositorio que compruebe si hay datos de mapa nuevos. Se hace una
 * sola vez por instancia, así que girar la pantalla no repite la comprobación.
 */
@HiltViewModel
class ActualizacionDatosViewModel
    @Inject
    constructor(
        private val repositorio: RepositorioDatosMapa,
    ) : ViewModel() {
        private var comprobado = false

        fun comprobarActualizacion() {
            if (comprobado) return
            comprobado = true
            repositorio.comprobarActualizacion()
        }
    }
