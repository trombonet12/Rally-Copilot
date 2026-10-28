package app.trombito.rallycopilot.data.datosmapa

import androidx.work.WorkInfo
import app.trombito.rallycopilot.data.db.AjustesMapasEntity
import app.trombito.rallycopilot.data.db.ArchivoInstaladoEntity
import app.trombito.rallycopilot.data.db.EstadoDatosEntity
import app.trombito.rallycopilot.domain.datos.AjustesMapas
import app.trombito.rallycopilot.domain.datos.DatosInstalados
import app.trombito.rallycopilot.domain.datos.EstadoDatosMapa
import app.trombito.rallycopilot.domain.datos.MotivoError
import app.trombito.rallycopilot.domain.datos.ProgresoDescarga

private const val ID_SATELITE = "satelite"

fun AjustesMapasEntity.aDominio() = AjustesMapas(soloWifi = soloWifi, limiteBytes = limiteBytes)

fun AjustesMapas.aEntidad() = AjustesMapasEntity(soloWifi = soloWifi, limiteBytes = limiteBytes)

/** Combina lo guardado en Room con el estado del trabajo de descarga (si lo hay) en el estado que ve la UI. */
fun construirEstado(
    archivos: List<ArchivoInstaladoEntity>,
    estado: EstadoDatosEntity?,
    trabajo: WorkInfo?,
): EstadoDatosMapa {
    val instalados =
        archivos.takeIf { it.isNotEmpty() }?.let {
            DatosInstalados(
                version = estado?.versionInstalada.orEmpty(),
                bytes = it.sumOf { a -> a.bytes },
                conSatelite = it.any { a -> a.id == ID_SATELITE },
            )
        }
    return EstadoDatosMapa(
        instalados = instalados,
        descarga = trabajo?.aProgreso(),
        error = estado?.ultimoError?.let { nombre -> MotivoError.entries.firstOrNull { it.name == nombre } },
        avisoActualizado = estado?.avisoActualizado,
    )
}

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
