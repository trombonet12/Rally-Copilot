package app.trombito.rallycopilot.data.alertas

import app.trombito.rallycopilot.domain.alertas.AlertasEnCurso
import app.trombito.rallycopilot.domain.alertas.ProveedorAlertas
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Alertas en vivo: las publica la [SesionAlertas] de la conducción en curso y las lee quien quiera (la UI). */
@Singleton
class AlertasEnVivoImpl
    @Inject
    constructor() : ProveedorAlertas {
        private val actuales = MutableStateFlow<AlertasEnCurso?>(null)

        override val alertas: StateFlow<AlertasEnCurso?> = actuales.asStateFlow()

        fun publicar(alertas: AlertasEnCurso?) {
            actuales.value = alertas
        }
    }
