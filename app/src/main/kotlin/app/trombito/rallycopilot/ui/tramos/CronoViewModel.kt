package app.trombito.rallycopilot.ui.tramos

import androidx.lifecycle.ViewModel
import app.trombito.rallycopilot.domain.tramos.ControlSalidaCrono
import app.trombito.rallycopilot.domain.tramos.CronoEnCurso
import app.trombito.rallycopilot.domain.tramos.ProveedorCrono
import app.trombito.rallycopilot.domain.voz.Locutor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Presentación del cronómetro de la conducción: expone el [ProveedorCrono.crono] en vivo (null sin conducción en
 * curso) y, con la salida manual, da la salida ([darSalida]) y dice las palabras de la cuenta atrás ([decir]) con el
 * copiloto. Lo que se pinta y cuándo lo deciden los composables de `ui/tramos` a partir de este estado.
 */
@HiltViewModel
class CronoViewModel
    @Inject
    constructor(
        proveedor: ProveedorCrono,
        private val control: ControlSalidaCrono,
        private val locutor: Locutor,
    ) : ViewModel() {
        val crono: StateFlow<CronoEnCurso?> = proveedor.crono

        /** Fija la salida del cronómetro de la conducción en curso (al llegar la cuenta atrás al «ya»). */
        fun darSalida() {
            control.darSalida()
        }

        /** Una palabra de la cuenta atrás, dicha por el copiloto con la voz elegida. */
        fun decir(texto: String) {
            locutor.decir(texto)
        }
    }
