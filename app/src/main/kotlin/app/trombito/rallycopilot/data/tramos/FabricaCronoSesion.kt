package app.trombito.rallycopilot.data.tramos

import android.util.Log
import app.trombito.rallycopilot.domain.ajustes.RepositorioAjustesConduccion
import app.trombito.rallycopilot.domain.escucha.RepositorioEdiciones
import app.trombito.rallycopilot.domain.tramos.RepositorioTramos
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Crea la [SesionCrono] de cada conducción; el cronómetro se publica en [CronoEnVivoImpl]. */
@Singleton
class FabricaCronoSesion
    @Inject
    constructor(
        private val repositorio: RepositorioTramos,
        private val salida: CronoEnVivoImpl,
        private val nombrador: NombradorTramos,
        private val ediciones: RepositorioEdiciones,
        private val ajustes: RepositorioAjustesConduccion,
    ) {
        fun crear(): SesionCrono =
            salida.enlazar { publicar ->
                SesionCrono(
                    repositorio,
                    publicar,
                    System::currentTimeMillis,
                    nombrar = nombrador::nombrar,
                    anclarEdiciones = ediciones::anclarPendientes,
                    alFallar = { Log.w("SesionCrono", "Fallo en el cronometraje: ${it.javaClass.simpleName}") },
                    salidaManual = ajustes.ajustes.map { it.salidaManual }.distinctUntilChanged(),
                )
            }
    }
