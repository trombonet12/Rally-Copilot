package app.trombito.rallycopilot.data.datosmapa

import androidx.work.WorkInfo
import app.trombito.rallycopilot.data.db.AjustesMapasEntity
import app.trombito.rallycopilot.data.db.ArchivoInstaladoEntity
import app.trombito.rallycopilot.data.db.EstadoDatosEntity
import app.trombito.rallycopilot.domain.datos.AjustesMapas
import app.trombito.rallycopilot.domain.datos.DatosInstalados
import app.trombito.rallycopilot.domain.datos.EstadoComplemento
import app.trombito.rallycopilot.domain.datos.EstadoDatosMapa
import app.trombito.rallycopilot.domain.datos.MotivoError
import app.trombito.rallycopilot.domain.datos.ProgresoDescarga
import app.trombito.rallycopilot.motor.datos.Complemento
import app.trombito.rallycopilot.motor.datos.LectorManifest

private const val UN_GB = 1024L * 1024 * 1024

/** Los ids del paquete que vienen en el APK (los obligatorios del manifest salvo el satélite). */
internal val IDS_SEMBRADOS: List<String> =
    LectorManifest.IDS_OBLIGATORIOS.filterNot { it.startsWith(PREFIJO_SATELITE) }

/** Etiqueta de WorkManager con la que un trabajo de descarga dice qué complemento pide (`complemento:RELIEVE`). */
internal const val PREFIJO_ETIQUETA_COMPLEMENTO = "complemento:"

/**
 * Una fila guardada con 1 GB (la opción más pequeña de antes) se lee como 2 GB: el paquete sembrado ya ocupa más de
 * 1 GB y el límite mínimo que se ofrece ahora es 2 GB.
 */
fun AjustesMapasEntity.aDominio() =
    AjustesMapas(soloWifi = soloWifi, limiteBytes = if (limiteBytes == UN_GB) 2 * UN_GB else limiteBytes)

fun AjustesMapas.aEntidad() = AjustesMapasEntity(soloWifi = soloWifi, limiteBytes = limiteBytes)

/**
 * Combina lo guardado en Room con el estado del trabajo de descarga (si lo hay) en el estado que ve la UI.
 * Hay datos instalados cuando están todos los sembrados (vectorial, estilo, vías); el satélite es opcional y se
 * detecta por cualquier id que empiece por `satelite`.
 */
fun construirEstado(
    archivos: List<ArchivoInstaladoEntity>,
    estado: EstadoDatosEntity?,
    trabajo: WorkInfo?,
    trabajosComplementos: List<WorkInfo> = emptyList(),
): EstadoDatosMapa {
    val ids = archivos.map { it.id }.toSet()
    val instalados =
        archivos.takeIf { lista -> IDS_SEMBRADOS.all { id -> lista.any { it.id == id } } }?.let {
            DatosInstalados(
                version = estado?.versionInstalada.orEmpty(),
                bytes = it.sumOf { a -> a.bytes },
                conSatelite = it.any { a -> a.id.startsWith(PREFIJO_SATELITE) },
                conRelieve = Complemento.RELIEVE.estaCompleto(ids),
                conVozSharvard = Complemento.VOZ_SHARVARD.estaCompleto(ids),
            )
        }
    return EstadoDatosMapa(
        instalados = instalados,
        descarga = trabajo?.aProgreso(),
        error = estado?.ultimoError?.let { nombre -> MotivoError.entries.firstOrNull { it.name == nombre } },
        avisoActualizado = estado?.avisoActualizado,
        complementos = estadoComplementos(archivos, ids, trabajosComplementos),
    )
}

/** Relieve y voz Sharvard: instalados (con lo que ocupan) y descarga en curso según las etiquetas de los trabajos. */
private fun estadoComplementos(
    archivos: List<ArchivoInstaladoEntity>,
    ids: Set<String>,
    trabajos: List<WorkInfo>,
): Map<Complemento, EstadoComplemento> =
    listOf(Complemento.RELIEVE, Complemento.VOZ_SHARVARD)
        .associateWith { c ->
            val progresos =
                trabajos.filter { PREFIJO_ETIQUETA_COMPLEMENTO + c.name in it.tags }.mapNotNull { it.aProgreso() }
            EstadoComplemento(
                instalado = c.estaCompleto(ids),
                bytes = archivos.filter { c.incluye(it.id) }.sumOf { it.bytes },
                // Una descarga en marcha manda sobre otra que espera su turno.
                descarga = progresos.firstOrNull { !it.esperandoRed } ?: progresos.firstOrNull(),
            )
        }.filterValues { it.instalado || it.descarga != null }

/**
 * RUNNING = descargando; ENQUEUED/BLOCKED = esperando red (o WiFi), también durante el backoff de un reintento.
 * Cualquier otro estado (terminado, cancelado) no es una descarga en curso.
 */
private fun WorkInfo.aProgreso(): ProgresoDescarga? =
    when (state) {
        WorkInfo.State.RUNNING -> {
            ProgresoDescarga(
                bytesHechos = progress.getLong(TrabajoDatosMapa.CLAVE_HECHOS, 0),
                bytesTotales = progress.getLong(TrabajoDatosMapa.CLAVE_TOTALES, 0),
                esperandoRed = false,
            )
        }

        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> {
            ProgresoDescarga(bytesHechos = 0, bytesTotales = 0, esperandoRed = true)
        }

        else -> {
            null
        }
    }
