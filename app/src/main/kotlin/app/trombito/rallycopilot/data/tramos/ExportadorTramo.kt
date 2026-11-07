package app.trombito.rallycopilot.data.tramos

import android.database.sqlite.SQLiteException
import android.util.Log
import app.trombito.rallycopilot.domain.edicion.EditorTramo
import app.trombito.rallycopilot.domain.escucha.RepositorioEdiciones
import app.trombito.rallycopilot.domain.tramos.ExportaTramo
import app.trombito.rallycopilot.domain.tramos.FiltroTramos
import app.trombito.rallycopilot.domain.tramos.RepositorioTramos
import app.trombito.rallycopilot.domain.tramos.TramoExportado
import app.trombito.rallycopilot.motor.importar.EdicionArchivo
import app.trombito.rallycopilot.motor.importar.EscritorGpx
import app.trombito.rallycopilot.motor.importar.NotaArchivo
import app.trombito.rallycopilot.motor.importar.TramoArchivo
import app.trombito.rallycopilot.motor.notas.ElementoHoja
import app.trombito.rallycopilot.motor.notas.RutaMedida
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Exporta un tramo guardado como GPX ([EscritorGpx]): su traza, su nombre, los cortes de sector y la hoja de notas ya
 * fusionada con las ediciones de Joan (que prevalecen) como waypoints legibles, más las ediciones tal cual para que al
 * importarlo vuelvan a ser ediciones. Solo lee: no escribe nada ni usa la red.
 */
class ExportadorTramo
    @Inject
    constructor(
        private val tramos: RepositorioTramos,
        private val editor: EditorTramo,
        private val ediciones: RepositorioEdiciones,
    ) : ExportaTramo {
        override suspend fun exportar(tramoId: String): TramoExportado? =
            try {
                construir(tramoId)
            } catch (e: SQLiteException) {
                Log.w("ExportadorTramo", "No se pudo exportar el tramo: ${e.javaClass.simpleName}")
                null
            } catch (e: IllegalStateException) {
                Log.w("ExportadorTramo", "No se pudo exportar el tramo: ${e.javaClass.simpleName}")
                null
            }

        private suspend fun construir(tramoId: String): TramoExportado? {
            val editable = editor.observar(tramoId).first() ?: return null
            val tramo = editable.tramo
            val nombre =
                tramos
                    .observarResumenes(FiltroTramos.TODOS)
                    .first()
                    .firstOrNull { it.id == tramoId }
                    ?.nombre ?: NOMBRE_POR_DEFECTO
            val editadas = ediciones.observarDeTramo(tramoId).first()
            return withContext(Dispatchers.Default) {
                val ruta = RutaMedida(tramo.traza)
                val notas =
                    editable.hoja.elementos.map {
                        NotaArchivo(
                            ruta.puntoEn(it.posicionM),
                            it.posicionM,
                            it.frase,
                            esAviso = it is ElementoHoja.DeAviso,
                        )
                    }
                val archivo =
                    TramoArchivo(
                        nombre = nombre,
                        traza = tramo.traza,
                        sectores = tramo.sectores,
                        microsectores = tramo.microsectores,
                        notas = notas,
                        ediciones = editadas.map { EdicionArchivo(it.posicionM, it.accion, it.marcaTiempoMs) },
                    )
                TramoExportado(EscritorGpx.nombreArchivo(nombre), EscritorGpx.escribir(archivo))
            }
        }

        private companion object {
            const val NOMBRE_POR_DEFECTO = "Tramo"
        }
    }
