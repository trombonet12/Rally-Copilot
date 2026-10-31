package app.trombito.rallycopilot.data.notas

import app.trombito.rallycopilot.domain.notas.NotasEnCurso
import app.trombito.rallycopilot.domain.notas.ProveedorNotas
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Notas en vivo: las publica la [NotasSesion] de la conducción en curso y las lee quien quiera (la UI, la voz). */
@Singleton
class NotasEnVivoImpl
    @Inject
    constructor() : ProveedorNotas {
        private val actuales = MutableStateFlow<NotasEnCurso?>(null)

        override val notas: StateFlow<NotasEnCurso?> = actuales.asStateFlow()

        fun publicar(notas: NotasEnCurso?) {
            actuales.value = notas
        }
    }
