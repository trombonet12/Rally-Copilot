package app.trombito.rallycopilot.data.escucha

import app.trombito.rallycopilot.domain.ajustes.AjustesConduccion
import app.trombito.rallycopilot.domain.ajustes.Orientacion
import app.trombito.rallycopilot.domain.ajustes.RepositorioAjustesConduccion
import app.trombito.rallycopilot.domain.datos.CapaMapa
import app.trombito.rallycopilot.motor.edicion.Indicacion
import app.trombito.rallycopilot.motor.edicion.Orden
import app.trombito.rallycopilot.motor.notas.NivelAnticipacion
import app.trombito.rallycopilot.motor.voz.ModoCopiloto
import app.trombito.rallycopilot.motor.voz.RespuestasVoz
import kotlinx.coroutines.flow.first

/** Guarda el cambio de [modificar] sobre los ajustes actuales y devuelve [confirmacion] (lo que se dice). */
internal suspend fun RepositorioAjustesConduccion.cambiar(
    confirmacion: String,
    modificar: (AjustesConduccion) -> AjustesConduccion,
): String {
    guardar(modificar(ajustes.first()))
    return confirmacion
}

/**
 * Las órdenes de voz que cambian un ajuste de la conducción, con su confirmación corta (`null` = no se dice nada). Los
 * ajustes se guardan en el repositorio, así que se notan al instante (el copiloto, el mapa y la hoja los observan):
 *
 * - [Orden.MasPronto] / [Orden.MasTarde]: un nivel de anticipación más pronto o más tarde (con tope en los extremos).
 * - [Orden.Silencio] / [Orden.Habla]: el modo SILENCIO y el modo en que estaba antes (COMPLETO si no se sabe).
 * - [Orden.Modo]: «modo notas», «solo avisos», «completo».
 * - [Orden.Volumen]: pasos de [PASO_VOLUMEN] % dentro del rango de `AjustesConduccion`.
 * - [Orden.NotasSimplificadas] y [Orden.Avisar] (cruces, puentes, rotondas, salidas).
 * - [Orden.Satelite], [Orden.NorteArriba] y [Orden.Zoom]: el mapa ([MapaPorVoz]).
 */
class AjustesPorVoz(
    private val ajustes: RepositorioAjustesConduccion,
) {
    private val mapa = MapaPorVoz(ajustes)
    private var modoAntesDeSilencio: ModoCopiloto? = null

    suspend fun aplicar(orden: Orden.Ajuste): String? =
        when (orden) {
            Orden.MasPronto -> HECHO.also { moverAnticipacion(-1) }
            Orden.MasTarde -> HECHO.also { moverAnticipacion(1) }
            Orden.Silencio -> callar()
            Orden.Habla -> hablar()
            is Orden.Modo -> cambiarModo(orden.modo)
            is Orden.Volumen -> cambiarVolumen(orden.subir)
            is Orden.NotasSimplificadas -> simplificar(orden.activar)
            is Orden.Avisar -> avisar(orden)
            is Orden.Satelite -> mapa.satelite(orden.activar)
            is Orden.NorteArriba -> mapa.norteArriba(orden.activar)
            is Orden.Zoom -> mapa.zoom(orden.acercar)
        }

    private suspend fun moverAnticipacion(paso: Int) {
        val actuales = ajustes.ajustes.first()
        val niveles = NivelAnticipacion.entries
        val nuevo = niveles[(actuales.anticipacion.ordinal + paso).coerceIn(0, niveles.lastIndex)]
        if (nuevo != actuales.anticipacion) ajustes.guardar(actuales.copy(anticipacion = nuevo))
    }

    private suspend fun callar(): String? {
        val modo = ajustes.ajustes.first().modoCopiloto
        if (modo != ModoCopiloto.SILENCIO) {
            modoAntesDeSilencio = modo
            ajustes.elegirModoCopiloto(ModoCopiloto.SILENCIO)
        }
        return null
    }

    private suspend fun hablar(): String {
        if (ajustes.ajustes.first().modoCopiloto == ModoCopiloto.SILENCIO) {
            ajustes.elegirModoCopiloto(modoAntesDeSilencio ?: ModoCopiloto.POR_DEFECTO)
        }
        modoAntesDeSilencio = null
        return HECHO
    }

    private suspend fun cambiarModo(modo: ModoCopiloto): String? {
        if (modo == ModoCopiloto.SILENCIO) return callar()
        modoAntesDeSilencio = null
        ajustes.elegirModoCopiloto(modo)
        return RespuestasVoz.modo(modo)
    }

    private suspend fun cambiarVolumen(subir: Boolean): String {
        val actuales = ajustes.ajustes.first()
        val paso = if (subir) PASO_VOLUMEN else -PASO_VOLUMEN
        val nuevo =
            (actuales.volumenVoz + paso).coerceIn(AjustesConduccion.VOLUMEN_VOZ_MIN, AjustesConduccion.VOLUMEN_VOZ_MAX)
        if (nuevo != actuales.volumenVoz) ajustes.guardar(actuales.copy(volumenVoz = nuevo))
        return RespuestasVoz.volumen(nuevo, subir, alLimite = nuevo == actuales.volumenVoz)
    }

    private suspend fun simplificar(activar: Boolean): String =
        ajustes.cambiar(if (activar) "notas simples" else "notas completas") {
            it.copy(notasSimplificadas = activar)
        }

    private suspend fun avisar(orden: Orden.Avisar): String {
        val nombre =
            when (orden.indicacion) {
                Indicacion.CRUCES -> "cruces"
                Indicacion.PUENTES -> "puentes"
                Indicacion.ROTONDAS -> "rotondas"
                Indicacion.SALIDAS -> "salidas"
            }
        return ajustes.cambiar("${if (orden.activar) "con" else "sin"} $nombre") {
            when (orden.indicacion) {
                Indicacion.CRUCES -> it.copy(avisarCruces = orden.activar)
                Indicacion.PUENTES -> it.copy(avisarPuentes = orden.activar)
                Indicacion.ROTONDAS -> it.copy(avisarRotondas = orden.activar)
                Indicacion.SALIDAS -> it.copy(avisarSalidasAutopista = orden.activar)
            }
        }
    }

    companion object {
        const val HECHO = "hecho"

        /** Lo que sube o baja el volumen de la voz cada vez (puntos porcentuales). */
        const val PASO_VOLUMEN = 10
    }
}

/** Las órdenes de voz sobre el mapa: capa, orientación y zoom (un nivel, con tope en `NIVEL_MIN..NIVEL_MAX`). */
class MapaPorVoz(
    private val ajustes: RepositorioAjustesConduccion,
) {
    suspend fun satelite(activar: Boolean): String =
        ajustes.cambiar(if (activar) "satélite" else "mapa") {
            it.copy(capa = if (activar) CapaMapa.SATELITE else CapaMapa.VECTORIAL)
        }

    suspend fun norteArriba(activar: Boolean): String =
        ajustes.cambiar(if (activar) "norte arriba" else "rumbo arriba") {
            it.copy(orientacion = if (activar) Orientacion.NORTE else Orientacion.RUMBO)
        }

    suspend fun zoom(acercar: Boolean): String {
        val actuales = ajustes.ajustes.first()
        val paso = if (acercar) 1 else -1
        val nuevo = (actuales.nivelZoom + paso).coerceIn(AjustesConduccion.NIVEL_MIN, AjustesConduccion.NIVEL_MAX)
        if (nuevo != actuales.nivelZoom) ajustes.guardar(actuales.copy(nivelZoom = nuevo))
        return RespuestasVoz.zoom(acercar, alLimite = nuevo == actuales.nivelZoom)
    }
}
