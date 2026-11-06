package app.trombito.rallycopilot.domain.ajustes

import app.trombito.rallycopilot.domain.datos.CapaMapa
import app.trombito.rallycopilot.domain.voz.VozCopiloto
import app.trombito.rallycopilot.motor.notas.NivelAnticipacion
import app.trombito.rallycopilot.motor.voz.FiltroIndicaciones
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
 * [activarConCopiloto] escucha sin parar, solo mientras se conduce, la palabra «Copiloto» para recibir una orden de voz
 * (desactivado por defecto: gasta batería y mantiene el micrófono abierto). [volumenVoz] es la ganancia propia del
 * copiloto en porcentaje (50–150, 100 = sin cambio), aparte del volumen del móvil; [tema] es oscuro, claro o el del
 * sistema. [cronometrar] activa los tramos cronometrados (desactivado: no se crean tramos ni pasadas ni se detectan los
 * guardados, pero las notas y la navegación siguen); [notasGrandes] cambia la pantalla de conducción al modo roadbook
 * (notas enormes, casi sin mapa). [avisarCruces], [avisarSalidasAutopista] y [avisarRotondas] dicen qué indicaciones se
 * dicen y se ven (todas por defecto; ver `FiltroIndicaciones` del motor). [salidaManual] hace que el cronómetro de la
 * conducción no arranque solo al moverse el coche sino cuando Joan pulsa «Salida» (cuenta atrás); desactivado = salida
 * automática.
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
    val activarConCopiloto: Boolean = false,
    val volumenVoz: Int = VOLUMEN_VOZ_NORMAL,
    val tema: Tema = Tema.PREDETERMINADO,
    val cronometrar: Boolean = true,
    val notasGrandes: Boolean = false,
    val avisarCruces: Boolean = true,
    val avisarSalidasAutopista: Boolean = true,
    val avisarRotondas: Boolean = true,
    val salidaManual: Boolean = false,
) {
    init {
        require(nivelZoom in NIVEL_MIN..NIVEL_MAX) { "nivelZoom fuera de rango: $nivelZoom" }
        require(volumenVoz in VOLUMEN_VOZ_MIN..VOLUMEN_VOZ_MAX) { "volumenVoz fuera de rango: $volumenVoz" }
    }

    /** Las preferencias de indicaciones como filtro del motor. */
    val filtroIndicaciones: FiltroIndicaciones
        get() = FiltroIndicaciones(avisarCruces, avisarSalidasAutopista, avisarRotondas)

    companion object {
        const val NIVEL_MIN = -2
        const val NIVEL_MAX = 2
        const val VOLUMEN_VOZ_MIN = 50
        const val VOLUMEN_VOZ_NORMAL = 100
        const val VOLUMEN_VOZ_MAX = 150
    }
}

interface RepositorioAjustesConduccion {
    val ajustes: Flow<AjustesConduccion>

    suspend fun guardar(ajustes: AjustesConduccion)

    /** Cambia solo el modo del copiloto. */
    suspend fun elegirModoCopiloto(modo: ModoCopiloto) {
        guardar(ajustes.first().copy(modoCopiloto = modo))
    }

    /** Activa o desactiva la escucha de la palabra «Copiloto» durante la conducción. */
    suspend fun elegirActivarConCopiloto(activar: Boolean) {
        guardar(ajustes.first().copy(activarConCopiloto = activar))
    }

    /** Cambia solo la voz del copiloto. */
    suspend fun elegirVoz(voz: VozCopiloto) {
        guardar(ajustes.first().copy(voz = voz))
    }
}
