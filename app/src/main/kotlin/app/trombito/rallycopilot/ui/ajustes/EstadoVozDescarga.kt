package app.trombito.rallycopilot.ui.ajustes

import app.trombito.rallycopilot.domain.datos.EstadoDatosMapa
import app.trombito.rallycopilot.domain.datos.ProgresoDescarga
import app.trombito.rallycopilot.motor.datos.Complemento

/**
 * Cómo está la voz Sharvard, que no va en el APK: instalada (funciona sin red), descargándose o por descargar con el
 * tamaño que se bajará. Dave siempre está disponible y no usa esto.
 */
data class EstadoVozDescarga(
    val instalada: Boolean = true,
    val bytes: Long = Complemento.VOZ_SHARVARD.bytesAproximados,
    val progreso: ProgresoDescarga? = null,
    /** La última descarga falló y no hay otra en marcha: el botón pasa a «reintentar». */
    val error: Boolean = false,
) {
    val descargando: Boolean get() = progreso != null

    /** Porcentaje entero de la descarga; null si aún no se sabe el total o está esperando red. */
    val porcentaje: Int?
        get() =
            progreso
                ?.takeIf { !it.esperandoRed && it.bytesTotales > 0 }
                ?.let { (it.bytesHechos.coerceIn(0, it.bytesTotales) * PORCIENTO / it.bytesTotales).toInt() }

    companion object {
        private const val PORCIENTO = 100L

        /** El estado de Sharvard a partir del de los datos de mapa. */
        fun de(datos: EstadoDatosMapa): EstadoVozDescarga {
            val sharvard = datos.complemento(Complemento.VOZ_SHARVARD)
            return EstadoVozDescarga(
                instalada = sharvard.instalado,
                progreso = sharvard.descarga,
                error = datos.error != null && !sharvard.instalado && !sharvard.descargando,
            )
        }
    }
}
