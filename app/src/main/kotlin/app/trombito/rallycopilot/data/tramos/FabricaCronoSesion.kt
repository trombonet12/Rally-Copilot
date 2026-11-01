package app.trombito.rallycopilot.data.tramos

import android.util.Log
import app.trombito.rallycopilot.domain.tramos.RepositorioTramos
import javax.inject.Inject
import javax.inject.Singleton

/** Crea la [SesionCrono] de cada conducción; el cronómetro se publica en [CronoEnVivoImpl]. */
@Singleton
class FabricaCronoSesion
    @Inject
    constructor(
        private val repositorio: RepositorioTramos,
        private val salida: CronoEnVivoImpl,
    ) {
        fun crear(): SesionCrono =
            SesionCrono(
                repositorio,
                salida::publicar,
                System::currentTimeMillis,
                alFallar = { Log.w("SesionCrono", "Fallo en el cronometraje: ${it.javaClass.simpleName}") },
            )
    }
