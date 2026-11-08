package app.trombito.rallycopilot.ui.widget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.trombito.rallycopilot.domain.tramos.RepositorioTramos
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Recibe los atajos que llegan a la actividad (arranque en frío y `onNewIntent`) y entrega, ya resueltos, los destinos
 * que AppNavHost convierte en navegación. El canal guarda lo pedido hasta que el NavHost empieza a escuchar.
 */
@HiltViewModel
class AtajosViewModel
    @Inject
    constructor(
        private val repositorio: RepositorioTramos,
    ) : ViewModel() {
        private val canal = Channel<DestinoAtajo>(Channel.BUFFERED)

        val destinos: Flow<DestinoAtajo> = canal.receiveAsFlow()

        fun pedir(atajo: Atajo) {
            viewModelScope.launch { canal.send(resolverAtajo(atajo, repositorio)) }
        }
    }
