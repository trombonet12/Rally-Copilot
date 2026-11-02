package app.trombito.rallycopilot.ui.mapasoffline

import app.trombito.rallycopilot.domain.datos.AjustesMapas
import app.trombito.rallycopilot.domain.datos.DatosInstalados
import app.trombito.rallycopilot.domain.datos.EstadoDatosMapa
import app.trombito.rallycopilot.domain.datos.EstadoDetalle
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
    /** Bytes de satélite que faltan por bajar según los manifests remotos (0 = todo instalado); null si no se sabe. */
    val bytesSatelite: Long? = null,
    /** Detalle z18-19 del IGN descargado (corredores y zonas) y su descarga en curso. */
    val detalle: EstadoDetalle = EstadoDetalle(),
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
     * Con lo sembrado desde el APK instalado, la pantalla ofrece el satélite si no hay ninguno (aun sin red) o si los
     * manifests remotos listan algún archivo de satélite que falta (p. ej. el z17 con el z16 ya puesto), con el tamaño
     * de lo que falta. Mientras se descarga o falta lo sembrado no se ofrece.
     */
    val ofreceSatelite: Boolean
        get() = fase == FaseMapas.INSTALADO && datos.instalados?.let(::faltaSatelite) == true

    private fun faltaSatelite(instalados: DatosInstalados) = !instalados.conSatelite || (bytesSatelite ?: 0L) > 0L

    /**
     * Bytes que se bajarían por la red al desactivar «Solo con WiFi»: lo pendiente de la descarga en curso; si no hay
     * ninguna, el satélite (si es lo que falta) o el tamaño del paquete instalado. Null si no se conoce.
     */
    val bytesPendientes: Long?
        get() {
            val actual = progreso
            return when {
                actual != null -> (actual.bytesTotales - actual.bytesHechos).coerceAtLeast(0).takeIf { it > 0 }
                else -> datos.instalados?.let { if (faltaSatelite(it)) bytesSatelite else it.bytes }
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
