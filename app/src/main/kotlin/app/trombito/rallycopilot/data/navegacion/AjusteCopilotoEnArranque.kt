package app.trombito.rallycopilot.data.navegacion

import app.trombito.rallycopilot.domain.ajustes.RepositorioAjustesConduccion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Último valor conocido del ajuste «Copiloto» (`activarConCopiloto`), disponible sin bloquear.
 *
 * `ServicioNavegacion.onStartCommand` (hilo principal) debe declarar el primer plano, con el tipo `microphone` o sin
 * él, antes de volver al sistema: el tipo solo se concede si se pide en ese momento con la app visible, y esperar a
 * Room allí obligaba a un `runBlocking`. En su lugar, [seguir] se arranca con la aplicación (fuera del hilo
 * principal) y mantiene este valor al día; el servicio solo lo lee. Antes de la primera lectura vale `false` (sin
 * micrófono) y el vigilante del servicio lo corrige si luego resulta que estaba activo.
 */
@Singleton
class AjusteCopilotoEnArranque
    @Inject
    constructor(
        private val ajustes: RepositorioAjustesConduccion,
    ) {
        @Volatile
        var activo: Boolean = false
            private set

        /** Empieza a seguir el ajuste en [alcance] (la cancelación del alcance lo detiene). */
        fun seguir(alcance: CoroutineScope): Job =
            alcance.launch {
                ajustes.ajustes
                    .map { it.activarConCopiloto }
                    .distinctUntilChanged()
                    .collect { activo = it }
            }
    }
