package app.trombito.rallycopilot.ui.tutorial

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lo que ya se ha enseñado del tutorial: la bienvenida y cada pista en contexto. Son banderas sueltas, no datos del
 * piloto: viven en un archivo de preferencias propio (ni Room ni la copia de seguridad de los tramos).
 */
@Singleton
class AlmacenTutorial
    @Inject
    constructor(
        @ApplicationContext contexto: Context,
    ) {
        private val preferencias: SharedPreferences =
            contexto.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)

        fun bienvenidaVista(): Boolean = preferencias.getBoolean(CLAVE_BIENVENIDA, false)

        fun marcarBienvenidaVista() {
            preferencias.edit().putBoolean(CLAVE_BIENVENIDA, true).apply()
        }

        fun pistasVistas(): Set<Pista> =
            Pista.entries.filterTo(mutableSetOf()) { preferencias.getBoolean(clave(it), false) }

        fun marcarPistaVista(pista: Pista) {
            preferencias.edit().putBoolean(clave(pista), true).apply()
        }

        /** «Ver el tutorial otra vez»: la bienvenida se enseña de nuevo y las pistas vuelven a salir. */
        fun reiniciar() {
            val edicion = preferencias.edit().remove(CLAVE_BIENVENIDA)
            Pista.entries.forEach { edicion.remove(clave(it)) }
            edicion.apply()
        }

        private fun clave(pista: Pista) = PREFIJO_PISTA + pista.clave

        private companion object {
            const val ARCHIVO = "tutorial"
            const val CLAVE_BIENVENIDA = "bienvenida_vista"
            const val PREFIJO_PISTA = "pista_"
        }
    }
