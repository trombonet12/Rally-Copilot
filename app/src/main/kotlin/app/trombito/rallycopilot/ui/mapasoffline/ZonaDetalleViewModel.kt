package app.trombito.rallycopilot.ui.mapasoffline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.trombito.rallycopilot.domain.datos.PrecargaDetalle
import app.trombito.rallycopilot.domain.datos.RepositorioDatosMapa
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.precarga.EncuadreVisible
import app.trombito.rallycopilot.motor.precarga.EstimacionTamano
import app.trombito.rallycopilot.motor.precarga.EstimacionZona
import app.trombito.rallycopilot.motor.precarga.Rectangulo
import app.trombito.rallycopilot.motor.precarga.TeselasPrecarga
import app.trombito.rallycopilot.ui.comun.PARADA_MS
import app.trombito.rallycopilot.ui.mapa.CamaraMapa
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * Zona a descargar a z18 o z19. Sin esquinas elegidas es el encuadre visible del mapa; con una o dos esquinas tocadas
 * es el rectángulo que forman. [estimacion] es null mientras no se conoce el encuadre; [cabe] dice si el tamaño
 * estimado entra en lo que queda del límite de espacio.
 */
data class ZonaDetalleUiState(
    val esquinas: List<PuntoGeo> = emptyList(),
    val zona: Rectangulo? = null,
    val zoom: Int = TeselasPrecarga.ZOOM_MIN_ZONA,
    val estimacion: EstimacionZona? = null,
    val cabe: Boolean = true,
) {
    /** Hay al menos una tesela de Mallorca que bajar. */
    val puedeDescargar: Boolean get() = (estimacion?.teselas ?: 0L) > 0L

    /** El usuario ha tocado esquinas (la zona ya no es el encuadre visible). */
    val personalizada: Boolean get() = esquinas.isNotEmpty()

    /** El rectángulo de las esquinas como contorno cerrado para pintarlo; vacío si no hay dos esquinas. */
    val contorno: List<PuntoGeo>
        get() {
            val z = zona?.takeIf { esquinas.size >= 2 } ?: return emptyList()
            return listOf(
                PuntoGeo(z.sur, z.oeste),
                PuntoGeo(z.sur, z.este),
                PuntoGeo(z.norte, z.este),
                PuntoGeo(z.norte, z.oeste),
                PuntoGeo(z.sur, z.oeste),
            )
        }
}

@HiltViewModel
class ZonaDetalleViewModel
    @Inject
    constructor(
        repositorio: RepositorioDatosMapa,
        private val precarga: PrecargaDetalle,
    ) : ViewModel() {
        private val esquinas = MutableStateFlow<List<PuntoGeo>>(emptyList())
        private val camara = MutableStateFlow(CAMARA_INICIAL)
        private val tamanoDp = MutableStateFlow<Pair<Double, Double>?>(null)
        private val zoom = MutableStateFlow(TeselasPrecarga.ZOOM_MIN_ZONA)

        /** Lo que queda del límite de espacio: límite menos datos instalados menos el detalle ya bajado. */
        private val espacio =
            combine(repositorio.ajustes, repositorio.estado, precarga.estado) { ajustes, datos, detalle ->
                ajustes.limiteBytes - (datos.instalados?.bytes ?: 0L) - detalle.bytes
            }

        val uiState: StateFlow<ZonaDetalleUiState> =
            combine(esquinas, camara, tamanoDp, zoom, espacio) { puntos, c, tamano, z, libre ->
                val zona = zonaDe(puntos, c, tamano)
                val estimacion = zona?.let { EstimacionTamano.zona(it, z) }
                ZonaDetalleUiState(puntos, zona, z, estimacion, cabe = (estimacion?.bytes ?: 0L) <= libre)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(PARADA_MS), ZonaDetalleUiState())

        /** Toque en el mapa: primera esquina, segunda esquina; a la tercera se empieza otra vez. */
        fun onToque(punto: PuntoGeo) {
            esquinas.update { if (it.size >= 2) listOf(punto) else it + punto }
        }

        fun onLimpiar() {
            esquinas.value = emptyList()
        }

        fun onZoom(nivel: Int) {
            zoom.value = nivel.coerceIn(TeselasPrecarga.ZOOM_MIN_ZONA, TeselasPrecarga.ZOOM_MAX_ZONA)
        }

        fun onCamara(nueva: CamaraMapa) {
            camara.value = nueva
        }

        fun onTamano(
            anchoDp: Double,
            altoDp: Double,
        ) {
            if (anchoDp > 0 && altoDp > 0) tamanoDp.value = anchoDp to altoDp
        }

        /** Encola la descarga manual de la zona actual. Devuelve false si no hay nada que bajar. */
        fun onConfirmar(): Boolean {
            val estado = uiState.value
            val zona = estado.zona?.takeIf { estado.puedeDescargar }
            zona?.let { precarga.descargarZona(it, estado.zoom) }
            return zona != null
        }

        private fun zonaDe(
            puntos: List<PuntoGeo>,
            c: CamaraMapa,
            tamano: Pair<Double, Double>?,
        ): Rectangulo? =
            when {
                puntos.size >= 2 -> {
                    val (a, b) = puntos
                    Rectangulo(minOf(a.lon, b.lon), minOf(a.lat, b.lat), maxOf(a.lon, b.lon), maxOf(a.lat, b.lat))
                }

                puntos.size == 1 -> {
                    val p = puntos.first()
                    Rectangulo(p.lon, p.lat, p.lon, p.lat)
                }

                tamano != null -> {
                    EncuadreVisible.rectangulo(c.latitud, c.longitud, c.zoom, tamano.first, tamano.second)
                }

                else -> {
                    null
                }
            }

        companion object {
            internal val CAMARA_INICIAL = CamaraMapa(latitud = 39.5696, longitud = 2.6502, zoom = 14.0)
        }
    }
