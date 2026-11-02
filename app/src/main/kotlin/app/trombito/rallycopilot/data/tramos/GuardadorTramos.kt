package app.trombito.rallycopilot.data.tramos

import android.database.sqlite.SQLiteException
import android.util.Log
import app.trombito.rallycopilot.domain.tramos.GuardaTraza
import app.trombito.rallycopilot.domain.tramos.NombraTraza
import app.trombito.rallycopilot.domain.tramos.RepositorioTramos
import app.trombito.rallycopilot.domain.tramos.ResultadoGuardado
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.tramos.TramoDesdeRecorrido
import java.util.UUID
import javax.inject.Inject

/**
 * Convierte una traza que no viene de conducir (un GPX/KML importado o una ruta dibujada) en un tramo guardado sin
 * pasadas: la limpia y simplifica como un recorrido ([TramoDesdeRecorrido]), le pone el nombre automático como a los
 * demás y lo guarda. Las notas no se guardan: se generan al conducirlo o reconocerlo.
 */
class GuardadorTramos
    @Inject
    constructor(
        private val repositorio: RepositorioTramos,
        private val nombra: NombraTraza,
    ) : GuardaTraza {
        override suspend fun guardar(
            traza: List<PuntoGeo>,
            nombre: String?,
        ): ResultadoGuardado {
            val tramo = TramoDesdeRecorrido.crear(UUID.randomUUID().toString(), traza) ?: return ResultadoGuardado.Corto
            val automatico = nombra.nombrar(tramo.traza)
            val propio = nombre?.trim()?.takeIf { it.isNotEmpty() }
            return try {
                repositorio.crearSinPasadas(tramo, propio, automatico, System.currentTimeMillis())
                ResultadoGuardado.Guardado(tramo.id, propio ?: automatico)
            } catch (e: SQLiteException) {
                Log.w("GuardadorTramos", "No se pudo guardar el tramo: ${e.javaClass.simpleName}")
                ResultadoGuardado.Fallo
            } catch (e: IllegalStateException) {
                Log.w("GuardadorTramos", "No se pudo guardar el tramo: ${e.javaClass.simpleName}")
                ResultadoGuardado.Fallo
            }
        }
    }
