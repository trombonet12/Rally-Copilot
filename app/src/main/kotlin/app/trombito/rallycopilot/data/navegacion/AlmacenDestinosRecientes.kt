package app.trombito.rallycopilot.data.navegacion

import android.content.Context
import android.content.SharedPreferences
import app.trombito.rallycopilot.domain.navegacion.Destino
import app.trombito.rallycopilot.domain.navegacion.DestinosRecientes
import app.trombito.rallycopilot.domain.navegacion.MAX_DESTINOS_RECIENTES
import app.trombito.rallycopilot.domain.navegacion.conReciente
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [DestinosRecientes] en `SharedPreferences` (sin Room): un texto con un destino por línea, `lat<TAB>lon<TAB>nombre`.
 * Son atajos, no datos del piloto: no entran en la copia de seguridad. Un texto dañado se ignora línea a línea.
 */
@Singleton
class AlmacenDestinosRecientes
    internal constructor(
        private val preferencias: SharedPreferences,
    ) : DestinosRecientes {
        @Inject
        constructor(
            @ApplicationContext contexto: Context,
        ) : this(contexto.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE))

        private val estado = MutableStateFlow(leer())

        override val recientes: Flow<List<Destino>> = estado.asStateFlow()

        @Synchronized
        override fun registrar(destino: Destino) {
            val nueva = estado.value.conReciente(destino)
            estado.value = nueva
            preferencias.edit().putString(CLAVE, codificar(nueva)).apply()
        }

        private fun leer(): List<Destino> = decodificar(preferencias.getString(CLAVE, null))

        internal companion object {
            const val ARCHIVO = "destinos_recientes"
            const val CLAVE = "lista"
            private const val SEPARADOR = '\t'
            private const val SALTO = '\n'
            private const val PARTES = 3

            fun codificar(destinos: List<Destino>): String =
                destinos.joinToString(SALTO.toString()) {
                    val nombre = it.nombre.map { c -> if (c == SEPARADOR || c == SALTO || c == '\r') ' ' else c }
                    "${it.punto.lat}$SEPARADOR${it.punto.lon}$SEPARADOR${nombre.joinToString("")}"
                }

            fun decodificar(texto: String?): List<Destino> =
                texto
                    .orEmpty()
                    .lines()
                    .mapNotNull { linea ->
                        val partes = linea.split(SEPARADOR, limit = PARTES)
                        if (partes.size < PARTES || partes[2].isBlank()) return@mapNotNull null
                        val lat = partes[0].toDoubleOrNull()?.takeIf { it in -90.0..90.0 } ?: return@mapNotNull null
                        val lon = partes[1].toDoubleOrNull()?.takeIf { it in -180.0..180.0 } ?: return@mapNotNull null
                        Destino(partes[2], PuntoGeo(lat, lon))
                    }.take(MAX_DESTINOS_RECIENTES)
        }
    }
