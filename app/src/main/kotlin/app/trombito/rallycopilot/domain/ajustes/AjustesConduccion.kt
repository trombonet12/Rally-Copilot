package app.trombito.rallycopilot.domain.ajustes

import app.trombito.rallycopilot.domain.datos.CapaMapa
import app.trombito.rallycopilot.domain.voz.VozCopiloto
import app.trombito.rallycopilot.motor.notas.NivelAnticipacion
import app.trombito.rallycopilot.motor.voz.ModoCopiloto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** Icono de la posición en el mapa (sin círculo de fondo). Joan lo elige en Ajustes. */
enum class IconoPosicion { FLECHA, FLECHA_WRC, COCHE_RALLY, CASCO }

/** Estilo gráfico de los iconos de maniobra del guiado: chevron latón, placa roja inclinada o nota de rally. */
enum class EstiloManiobras { CHEVRON, PLACA, NOTA }

/** Orientación del mapa al conducir: rumbo arriba (gira con el coche) o norte arriba. */
enum class Orientacion { RUMBO, NORTE }

/**
 * Preferencias de la pantalla de conducción. [nivelZoom] va de -2 (más lejos) a 2 (más cerca) y desplaza el zoom
 * automático por velocidad (`ZoomPorVelocidad` del motor). [anticipacion] es cuánto antes de la curva se canta la nota;
 * [notasSimplificadas] reduce las notas a las curvas relevantes. [modoCopiloto] es cuánto habla el copiloto y [voz] la
 * voz elegida (`null` = aún no se ha elegido: se pregunta en el primer uso y mientras tanto el copiloto no habla).
 */
data class AjustesConduccion(
    val nivelZoom: Int = 0,
    val icono: IconoPosicion = IconoPosicion.FLECHA_WRC,
    val orientacion: Orientacion = Orientacion.RUMBO,
    val capa: CapaMapa = CapaMapa.VECTORIAL,
    val relieve: Boolean = false,
    val estiloManiobras: EstiloManiobras = EstiloManiobras.PLACA,
    val anticipacion: NivelAnticipacion = NivelAnticipacion.NORMAL,
    val notasSimplificadas: Boolean = false,
    val modoCopiloto: ModoCopiloto = ModoCopiloto.POR_DEFECTO,
    val voz: VozCopiloto? = null,
) {
    init {
        require(nivelZoom in NIVEL_MIN..NIVEL_MAX) { "nivelZoom fuera de rango: $nivelZoom" }
    }

    companion object {
        const val NIVEL_MIN = -2
        const val NIVEL_MAX = 2
    }
}

interface RepositorioAjustesConduccion {
    val ajustes: Flow<AjustesConduccion>

    suspend fun guardar(ajustes: AjustesConduccion)

    /** Cambia solo el modo del copiloto. */
    suspend fun elegirModoCopiloto(modo: ModoCopiloto) {
        guardar(ajustes.first().copy(modoCopiloto = modo))
    }

    /** Cambia solo la voz del copiloto. */
    suspend fun elegirVoz(voz: VozCopiloto) {
        guardar(ajustes.first().copy(voz = voz))
    }
}
