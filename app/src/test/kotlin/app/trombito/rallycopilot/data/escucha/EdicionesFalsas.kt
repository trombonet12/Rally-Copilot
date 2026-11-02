package app.trombito.rallycopilot.data.escucha

import app.trombito.rallycopilot.domain.escucha.EdicionPendiente
import app.trombito.rallycopilot.domain.escucha.EdicionSinTramo
import app.trombito.rallycopilot.domain.escucha.RepositorioEdiciones
import app.trombito.rallycopilot.motor.edicion.NotasEditadas
import app.trombito.rallycopilot.motor.tramos.Tramo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map

/** Recoge lo que se publica; el resto del repositorio no hace nada. */
class EdicionesFalsas : RepositorioEdiciones {
    val publicadas = mutableListOf<EdicionPendiente>()
    private val visibles = MutableStateFlow<List<NotasEditadas>>(emptyList())
    val guardadas: List<NotasEditadas> get() = visibles.value
    val sinTramo = mutableListOf<EdicionSinTramo>()

    override suspend fun publicar(edicion: EdicionPendiente) {
        publicadas += edicion
    }

    override suspend fun guardar(edicion: NotasEditadas) {
        visibles.value = visibles.value + edicion
    }

    override suspend fun guardarSinTramo(edicion: EdicionSinTramo) {
        sinTramo += edicion
    }

    override fun observarDeTramo(tramoId: String): Flow<List<NotasEditadas>> =
        visibles.map { l -> l.filter { it.tramoId == tramoId } }

    override fun observarPendientes(): Flow<List<EdicionSinTramo>> = emptyFlow()

    override suspend fun borrarDeTramo(tramoId: String) {
        visibles.value = visibles.value.filterNot { it.tramoId == tramoId }
    }

    override suspend fun anclarPendientes(tramo: Tramo?) = Unit
}
