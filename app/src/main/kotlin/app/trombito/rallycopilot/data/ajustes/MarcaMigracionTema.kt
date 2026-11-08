package app.trombito.rallycopilot.data.ajustes

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Marca de «el tema por defecto pasó de Oscuro a Día y noche». La migración (ver [RepositorioAjustesConduccionImpl])
 * se hace una sola vez; esta marca recuerda que ya se hizo para no deshacer una elección posterior de Joan.
 */
interface MarcaMigracionTema {
    fun hecha(): Boolean

    fun marcar()
}

/** [MarcaMigracionTema] en `SharedPreferences` (lectura y escritura síncronas de un solo booleano). */
class MarcaMigracionTemaPreferencias
    @Inject
    constructor(
        @ApplicationContext contexto: Context,
    ) : MarcaMigracionTema {
        private val preferencias = contexto.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)

        override fun hecha(): Boolean = preferencias.getBoolean(CLAVE, false)

        override fun marcar() {
            preferencias.edit().putBoolean(CLAVE, true).apply()
        }

        private companion object {
            const val ARCHIVO = "migraciones"
            const val CLAVE = "tema_dia_y_noche_por_defecto"
        }
    }
