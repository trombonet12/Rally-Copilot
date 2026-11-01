package app.trombito.rallycopilot.domain.alertas

import app.trombito.rallycopilot.motor.alertas.Incidencia
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant

/** Por qué falló el último intento de refrescar las incidencias. */
enum class ErrorIncidencias {
    /** No se pudo conectar o la conexión se cortó. */
    SIN_RED,

    /** El servidor respondió con un código distinto de 200. */
    SERVIDOR,

    /** La respuesta no es un DATEX II legible. */
    DATOS,

    /** La respuesta pasa del tope de tamaño. */
    DEMASIADO_GRANDE,
}

/**
 * Incidencias de tráfico de Mallorca no caducadas, de la última copia buena. [obtenidasEn] es el instante en que se
 * descargó esa copia (`null` si nunca hubo una: la antigüedad se calcula con él); [errorUltimo] es el fallo del último
 * intento de refresco, o `null` si salió bien (o aún no se intentó).
 */
data class IncidenciasVigentes(
    val incidencias: List<Incidencia> = emptyList(),
    val obtenidasEn: Instant? = null,
    val errorUltimo: ErrorIncidencias? = null,
)

/** Incidencias de la DGT: una copia en disco que se refresca al abrir la app y al empezar una ruta. */
interface RepositorioIncidencias {
    val incidencias: StateFlow<IncidenciasVigentes>

    /**
     * Carga la copia en disco la primera vez y descarga una nueva si hace más de 5 minutos del último intento (1 minuto
     * si falló). Sin red deja la copia y anota el error en [IncidenciasVigentes.errorUltimo]. Si ya hay un refresco en
     * marcha, vuelve sin hacer nada. Nunca lanza por datos o red malos.
     */
    suspend fun refrescar()
}
