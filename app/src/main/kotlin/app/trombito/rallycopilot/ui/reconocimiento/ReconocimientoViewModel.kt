package app.trombito.rallycopilot.ui.reconocimiento

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.trombito.rallycopilot.domain.navegacion.ControlNavegacion
import app.trombito.rallycopilot.domain.navegacion.EstadoNavegacion
import app.trombito.rallycopilot.domain.navegacion.PasadaReconocimiento
import app.trombito.rallycopilot.domain.notas.NotasEnCurso
import app.trombito.rallycopilot.domain.notas.ProveedorNotas
import app.trombito.rallycopilot.motor.edicion.CambioNota
import app.trombito.rallycopilot.motor.edicion.DiferenciasNotas
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.notas.HojaNotas
import app.trombito.rallycopilot.motor.notas.RutaMedida
import app.trombito.rallycopilot.ui.edicion.estaParado
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A menos de esto (m) del final de la traza del tramo se ha llegado a meta. */
internal const val META_M = 40.0

/**
 * Resumen de lo que cambió en la pasada [numero] del reconocimiento de [tramoId]: [cambios] entre las notas con las
 * que empezó y con las que acabó. [terminada] es que se acabó antes de llegar a meta (el usuario terminó): la sesión
 * ya no está en marcha.
 */
data class ResumenPasada(
    val tramoId: String,
    val numero: Int,
    val cambios: List<CambioNota>,
    val terminada: Boolean,
) {
    /** Queda la 2.ª pasada por hacer. */
    val hayOtraPasada: Boolean get() = numero < PasadaReconocimiento.SEGUNDA
}

/**
 * Reconocimiento guiado en dos pasadas. Arranca la 1.ª ([empezar]) y, al llegar a meta con el coche parado o al
 * terminar la pasada, publica el [resumen] de lo que cambió; desde ahí se hace la 2.ª pasada (repasar con las
 * correcciones) o se termina. El resumen nunca sale en marcha: al llegar a meta espera a que el coche pare.
 *
 * Compara la hoja de notas con la que empezó la pasada (la 2.ª parte de la que acabó la 1.ª) con la última
 * publicada: las correcciones dictadas o editadas ya vienen fusionadas en ella.
 */
@HiltViewModel
class ReconocimientoViewModel
    @Inject
    constructor(
        private val control: ControlNavegacion,
        notas: ProveedorNotas,
    ) : ViewModel() {
        private val _resumen = MutableStateFlow<ResumenPasada?>(null)

        /** El resumen de la pasada que acaba de terminar; null mientras no toca enseñarlo. */
        val resumen: StateFlow<ResumenPasada?> = _resumen.asStateFlow()

        // Todo lo siguiente se toca solo desde el colector (hilo principal).
        private var activa: PasadaReconocimiento? = null
        private var inicio: HojaNotas? = null
        private var ultima: HojaNotas? = null
        private var tramoDeUltima: String? = null
        private var llegada = false
        private var recorrido = false
        private var trazaMedida: Pair<List<PuntoGeo>, Double>? = null

        init {
            viewModelScope.launch {
                combine(control.pasadaReconocimiento, control.estado, notas.notas) { pasada, estado, enCurso ->
                    Triple(pasada, estado, enCurso)
                }.collect { (pasada, estado, enCurso) -> alCambiar(pasada, estado, enCurso) }
            }
        }

        fun empezar(tramoId: String) = control.empezarReconocimiento(tramoId)

        /** «Hacer 2.ª pasada»: cierra el resumen y repasa el tramo con las correcciones de la 1.ª. */
        fun onHacerSegundaPasada() {
            val resumen = _resumen.value ?: return
            _resumen.value = null
            llegada = false
            recorrido = false
            control.empezarSegundaPasada(resumen.tramoId)
        }

        /** «Terminar»: cierra el resumen y acaba el reconocimiento si seguía en marcha. */
        fun onTerminar() {
            _resumen.value = null
            activa = null
            llegada = false
            recorrido = false
            if (control.estado.value !is EstadoNavegacion.Inactiva) control.terminar()
        }

        private fun alCambiar(
            pasada: PasadaReconocimiento?,
            estado: EstadoNavegacion,
            enCurso: NotasEnCurso?,
        ) {
            if (pasada == null) {
                alAcabar(estado)
            } else if (estado !is EstadoNavegacion.Inactiva) {
                // Con la sesión ya parada y la pasada aún por borrarse (solo un instante) no hay nada que seguir.
                if (pasada != activa) empezarPasada(pasada)
                activa = pasada
                if (enCurso != null && enCurso.tramoReconocimiento == pasada.tramoId) seguir(pasada, enCurso)
            }
        }

        /** Sin pasada: solo cuenta como «terminar» si la sesión se ha parado; si la sustituye otra, no hay resumen. */
        private fun alAcabar(estado: EstadoNavegacion) {
            val previa = activa
            if (previa != null && estado is EstadoNavegacion.Inactiva) resumir(previa, terminada = true)
            activa = null
        }

        private fun seguir(
            pasada: PasadaReconocimiento,
            enCurso: NotasEnCurso,
        ) {
            ultima = enCurso.hoja
            tramoDeUltima = pasada.tramoId
            if (inicio == null) inicio = enCurso.hoja
            // Solo vale la llegada de una pasada que ha salido: las notas viejas de la sesión anterior (en meta) no.
            if (!enMeta(enCurso)) {
                recorrido = true
            } else if (recorrido) {
                llegada = true
            }
            if (llegada && _resumen.value == null && estaParado(enCurso)) resumir(pasada, terminada = false)
        }

        /** La 2.ª pasada parte de las notas con que acabó la 1.ª (ya con las correcciones). */
        private fun empezarPasada(pasada: PasadaReconocimiento) {
            _resumen.value = null
            llegada = false
            recorrido = false
            val heredada = ultima.takeIf { pasada.numero > 1 && tramoDeUltima == pasada.tramoId }
            inicio = heredada
            ultima = heredada
            if (heredada == null) tramoDeUltima = null
        }

        private fun resumir(
            pasada: PasadaReconocimiento,
            terminada: Boolean,
        ) {
            val antes = inicio ?: return
            val despues = ultima ?: return
            _resumen.value =
                ResumenPasada(pasada.tramoId, pasada.numero, DiferenciasNotas.entre(antes, despues), terminada)
        }

        private fun enMeta(enCurso: NotasEnCurso): Boolean {
            val traza = enCurso.traza?.takeIf { it.size >= 2 } ?: return false
            val medida =
                trazaMedida?.takeIf { it.first === traza } ?: (traza to RutaMedida(traza).longitudM).also {
                    trazaMedida = it
                }
            return enCurso.posicionM >= medida.second - META_M
        }
    }
