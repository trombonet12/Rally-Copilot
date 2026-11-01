package app.trombito.rallycopilot.ui.mapasoffline

import app.trombito.rallycopilot.domain.datos.AjustesMapas
import app.trombito.rallycopilot.domain.datos.EstadoDatosMapa
import app.trombito.rallycopilot.domain.datos.ProgresoDescarga

/** Lo que muestra la pantalla de Mapas offline, deducido de [EstadoDatosMapa]. */
enum class FaseMapas {
    CARGANDO,

    /** Siembra de los datos del APK en curso: «Preparando mapas…», sin botón de descargar. */
    PREPARANDO,

    /** Sin datos (siembra fallida o primera vez sin APK con datos): «Descargar mapas» baja todo menos el satélite. */
    SIN_DATOS,
    INSTALADO,
    DESCARGANDO,
}

data class MapasOfflineUiState(
    val cargando: Boolean = true,
    val datos: EstadoDatosMapa = EstadoDatosMapa(),
    val ajustes: AjustesMapas = AjustesMapas(),
    /** Último progreso con total conocido de la descarga activa: se sigue mostrando mientras espera red. */
    val ultimoProgreso: ProgresoDescarga? = null,
    /** Tamaño del satélite según el manifest remoto; null mientras no se sabe (o sin red). */
    val bytesSatelite: Long? = null,
) {
    val fase: FaseMapas
        get() =
            when {
                cargando -> FaseMapas.CARGANDO
                datos.preparando -> FaseMapas.PREPARANDO
                datos.descarga != null -> FaseMapas.DESCARGANDO
                datos.instalados != null -> FaseMapas.INSTALADO
                else -> FaseMapas.SIN_DATOS
            }

    /** El progreso a mostrar: el actual si ya trae total; si no, el último conocido de esta misma descarga. */
    val progreso: ProgresoDescarga?
        get() {
            val actual = datos.descarga ?: return null
            return if (actual.bytesTotales > 0) actual else ultimoProgreso
        }

    /**
     * Con lo sembrado desde el APK instalado y sin satélite, lo único que se descarga es el satélite: la pantalla lo
     * ofrece con su tamaño. Mientras se descarga o falta lo sembrado no se ofrece.
     */
    val ofreceSatelite: Boolean
        get() = fase == FaseMapas.INSTALADO && datos.instalados?.conSatelite == false

    /**
     * Bytes que se bajarían por la red al desactivar «Solo con WiFi»: lo pendiente de la descarga en curso; si no hay
     * ninguna, el satélite (si es lo que falta) o el tamaño del paquete instalado. Null si no se conoce.
     */
    val bytesPendientes: Long?
        get() {
            val actual = progreso
            return when {
                actual != null -> (actual.bytesTotales - actual.bytesHechos).coerceAtLeast(0).takeIf { it > 0 }
                else -> datos.instalados?.let { if (it.conSatelite) it.bytes else bytesSatelite }
            }
        }

    /** Porcentaje entero de la descarga, o null si aún no se conoce el total. */
    val porcentaje: Int? get() = progreso?.porcentaje()
}

internal fun ProgresoDescarga.porcentaje(): Int? =
    if (bytesTotales <= 0) null else ((bytesHechos.coerceIn(0, bytesTotales) * PORCIENTO) / bytesTotales).toInt()

private const val PORCIENTO = 100L

/** Límites de espacio que se ofrecen, en GB (por defecto, 4). */
val LIMITES_GB = listOf(2, 4, 8)

const val BYTES_POR_GB = 1024L * 1024 * 1024
