package app.trombito.rallycopilot.ui.tramos

import androidx.lifecycle.ViewModel
import app.trombito.rallycopilot.domain.tramos.CronoEnCurso
import app.trombito.rallycopilot.domain.tramos.ProveedorCrono
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Presentación del cronómetro de la conducción: expone el [ProveedorCrono.crono] en vivo (null sin conducción en
 * curso). Lo que se pinta y cuándo lo deciden los composables de `ui/tramos` a partir de este estado.
 */
@HiltViewModel
class CronoViewModel
    @Inject
    constructor(
        proveedor: ProveedorCrono,
    ) : ViewModel() {
        val crono: StateFlow<CronoEnCurso?> = proveedor.crono
    }
