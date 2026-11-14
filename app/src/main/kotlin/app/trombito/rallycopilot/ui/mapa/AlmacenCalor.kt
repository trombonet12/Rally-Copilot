package app.trombito.rallycopilot.ui.mapa

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lo que Joan elige de la capa de calor del mapa: si está encendida (por defecto sí) y el modo. Son dos ajustes de
 * interfaz sueltos: viven en un archivo de preferencias propio (ni Room ni la copia de seguridad de los tramos).
 */
@Singleton
class AlmacenCalor
    @Inject
    constructor(
        @ApplicationContext contexto: Context,
    ) {
        private val preferencias: SharedPreferences =
            contexto.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)

        fun activo(): Boolean = preferencias.getBoolean(CLAVE_ACTIVO, true)

        fun guardarActivo(activo: Boolean) {
            preferencias.edit().putBoolean(CLAVE_ACTIVO, activo).apply()
        }

        /** El modo guardado; un valor que ya no existe (versión nueva o vieja) vuelve a [ModoCalor.USO]. */
        fun modo(): ModoCalor =
            ModoCalor.entries.firstOrNull { it.name == preferencias.getString(CLAVE_MODO, null) } ?: ModoCalor.USO

        fun guardarModo(modo: ModoCalor) {
            preferencias.edit().putString(CLAVE_MODO, modo.name).apply()
        }

        private companion object {
            const val ARCHIVO = "calor"
            const val CLAVE_ACTIVO = "activo"
            const val CLAVE_MODO = "modo"
        }
    }
