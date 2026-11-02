package app.trombito.rallycopilot.ui.reconocimiento

import androidx.lifecycle.ViewModel
import app.trombito.rallycopilot.domain.navegacion.ControlNavegacion
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Arranca la pasada de reconocimiento del tramo elegido. */
@HiltViewModel
class ReconocimientoViewModel
    @Inject
    constructor(
        private val control: ControlNavegacion,
    ) : ViewModel() {
        fun empezar(tramoId: String) = control.empezarReconocimiento(tramoId)
    }
