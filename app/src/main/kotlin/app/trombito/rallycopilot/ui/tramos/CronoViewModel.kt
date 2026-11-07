package app.trombito.rallycopilot.ui.tramos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.ajustes.RepositorioAjustesConduccion
import app.trombito.rallycopilot.domain.tramos.ControlSalidaCrono
import app.trombito.rallycopilot.domain.tramos.CronoEnCurso
import app.trombito.rallycopilot.domain.tramos.ProveedorCrono
import app.trombito.rallycopilot.domain.voz.Locutor
import app.trombito.rallycopilot.domain.voz.PrioridadVoz
import app.trombito.rallycopilot.motor.voz.ModoCopiloto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Presentación del cronómetro de la conducción: expone el [ProveedorCrono.crono] en vivo (null sin conducción en
 * curso) y, con la salida manual, da la salida ([darSalida]) y dice las palabras de la cuenta atrás ([decir]) con el
 * copiloto: con prioridad urgente (pasan delante de notas y avisos en cola) y solo si hay voz elegida y no está en
 * silencio; si no, la cuenta atrás es solo luces. Lo que se pinta y cuándo lo deciden los composables de
 * `ui/tramos` a partir de este estado.
 */
@HiltViewModel
class CronoViewModel
    @Inject
    constructor(
        proveedor: ProveedorCrono,
        private val control: ControlSalidaCrono,
        private val locutor: Locutor,
        ajustes: RepositorioAjustesConduccion,
    ) : ViewModel() {
        private val preferencias = ajustes.ajustes.stateIn(viewModelScope, SharingStarted.Eagerly, null)

        val crono: StateFlow<CronoEnCurso?> = proveedor.crono

        /** Fija la salida del cronómetro de la conducción en curso (al llegar la cuenta atrás al «ya»). */
        fun darSalida() {
            control.darSalida()
        }

        /** Una palabra de la cuenta atrás con la voz elegida (urgente); sin voz o en silencio, nada. */
        fun decir(texto: String) {
            if (!puedeHablar(preferencias.value)) return
            locutor.decir(texto, PrioridadVoz.URGENTE)
        }

        private fun puedeHablar(p: AjustesConduccion?): Boolean =
            p != null && p.voz != null && p.modoCopiloto != ModoCopiloto.SILENCIO
    }
