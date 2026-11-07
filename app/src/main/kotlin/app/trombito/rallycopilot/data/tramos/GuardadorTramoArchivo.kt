package app.trombito.rallycopilot.data.tramos

import android.database.sqlite.SQLiteException
import android.util.Log
import app.trombito.rallycopilot.domain.escucha.RepositorioEdiciones
import app.trombito.rallycopilot.domain.tramos.GuardaTramoArchivo
import app.trombito.rallycopilot.domain.tramos.NombraTraza
import app.trombito.rallycopilot.domain.tramos.NombreAutomatico
import app.trombito.rallycopilot.domain.tramos.RepositorioTramos
import app.trombito.rallycopilot.domain.tramos.ResultadoGuardado
import app.trombito.rallycopilot.domain.tramos.TramoImportable
import app.trombito.rallycopilot.motor.edicion.NotasEditadas
import app.trombito.rallycopilot.motor.geo.longitudMetros
import app.trombito.rallycopilot.motor.tramos.Tramo
import app.trombito.rallycopilot.motor.tramos.TramoDesdeRecorrido
import java.util.UUID
import javax.inject.Inject

/**
 * Guarda el tramo de un GPX que exportó Rally Copilot tal como era: la traza no se vuelve a simplificar (las ediciones
 * de notas van en metros sobre ella y tienen que seguir cayendo en su sitio), los cortes se conservan si son válidos
 * para esa traza (si no, quedan los de por defecto) y las ediciones se guardan ligadas al tramo nuevo, descartando
 * las que caen fuera de él. Si algo falla al guardar las ediciones, el tramo recién creado se borra.
 */
class GuardadorTramoArchivo
    @Inject
    constructor(
        private val repositorio: RepositorioTramos,
        private val nombra: NombraTraza,
        private val ediciones: RepositorioEdiciones,
    ) : GuardaTramoArchivo {
        override suspend fun guardar(
            tramo: TramoImportable,
            nombre: String?,
        ): ResultadoGuardado {
            val longitud = tramo.traza.longitudMetros()
            if (tramo.traza.size < 2 || longitud < TramoDesdeRecorrido.LONGITUD_MIN_M) return ResultadoGuardado.Corto
            val id = UUID.randomUUID().toString()
            val nuevo = crearTramo(id, tramo, longitud)
            val automatico = nombra.nombrar(nuevo.traza)
            val propio = nombre?.trim()?.takeIf { it.isNotEmpty() }
            return try {
                repositorio.crearSinPasadas(nuevo, propio, automatico, System.currentTimeMillis())
                guardarEdiciones(id, tramo, nuevo.longitudM)
                ResultadoGuardado.Guardado(id, propio ?: automatico?.let(NombreAutomatico::titulo))
            } catch (e: SQLiteException) {
                fallo(id, e)
            } catch (e: IllegalStateException) {
                fallo(id, e)
            }
        }

        private fun crearTramo(
            id: String,
            tramo: TramoImportable,
            longitud: Double,
        ): Tramo =
            try {
                Tramo(id, tramo.traza, longitud, tramo.sectores, tramo.microsectores)
            } catch (_: IllegalArgumentException) {
                Tramo(id, tramo.traza, longitud)
            }

        private suspend fun guardarEdiciones(
            id: String,
            tramo: TramoImportable,
            longitud: Double,
        ) {
            for (e in tramo.ediciones) {
                if (e.posicionM in 0.0..longitud) {
                    ediciones.guardar(NotasEditadas(id, e.posicionM, e.accion, e.marcaTiempoMs))
                }
            }
        }

        private suspend fun fallo(
            id: String,
            e: Exception,
        ): ResultadoGuardado {
            Log.w("GuardadorTramoArchivo", "No se pudo guardar el tramo: ${e.javaClass.simpleName}")
            try {
                repositorio.borrar(id)
            } catch (_: SQLiteException) {
                // Ya falló al guardar: no hay más que hacer con un tramo a medias que la base no deja borrar.
            } catch (_: IllegalStateException) {
                // Igual que arriba.
            }
            return ResultadoGuardado.Fallo
        }
    }
