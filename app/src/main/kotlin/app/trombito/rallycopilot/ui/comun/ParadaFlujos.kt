package app.trombito.rallycopilot.ui.comun

/**
 * Milisegundos que un `stateIn(…, SharingStarted.WhileSubscribed(PARADA_MS), …)` de un ViewModel sigue activo tras la
 * última suscripción: cubre un giro de pantalla sin reiniciar el flujo y lo corta si la pantalla se va de verdad.
 */
const val PARADA_MS = 5_000L
