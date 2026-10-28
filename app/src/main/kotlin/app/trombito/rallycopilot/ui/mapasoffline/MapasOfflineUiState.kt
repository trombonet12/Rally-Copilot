package app.trombito.rallycopilot.ui.mapasoffline

import app.trombito.rallycopilot.domain.datos.AjustesMapas
import app.trombito.rallycopilot.domain.datos.EstadoDatosMapa
import app.trombito.rallycopilot.domain.datos.ProgresoDescarga

/** Lo que muestra la pantalla de Mapas offline, deducido de [EstadoDatosMapa]. */
enum class FaseMapas { CARGANDO, SIN_DATOS, INSTALADO, DESCARGANDO }

data class MapasOfflineUiState(
    val cargando: Boolean = true,
    val datos: EstadoDatosMapa = EstadoDatosMapa(),
    val ajustes: AjustesMapas = AjustesMapas(),
    /** Último progreso con total conocido de la descarga activa: se sigue mostrando mientras espera red. */
    val ultimoProgreso: ProgresoDescarga? = null,
) {
    val fase: FaseMapas
        get() =
            when {
                cargando -> FaseMapas.CARGANDO
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

    /** Porcentaje entero de la descarga, o null si aún no se conoce el total. */
    val porcentaje: Int? get() = progreso?.porcentaje()
}

internal fun ProgresoDescarga.porcentaje(): Int? =
    if (bytesTotales <= 0) null else ((bytesHechos.coerceIn(0, bytesTotales) * PORCIENTO) / bytesTotales).toInt()

private const val PORCIENTO = 100L

/** Límites de espacio que se ofrecen, en GB (por defecto, 2). */
val LIMITES_GB = listOf(1, 2, 4, 8)

const val BYTES_POR_GB = 1024L * 1024 * 1024
