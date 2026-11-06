package app.trombito.rallycopilot.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.trombito.rallycopilot.data.ruta.EnrutadorValhalla
import app.trombito.rallycopilot.domain.ruta.PerfilRuta
import app.trombito.rallycopilot.domain.ruta.ResultadoRuta
import app.trombito.rallycopilot.motor.geo.PuntoGeo
import app.trombito.rallycopilot.motor.ruta.FrasesManiobra
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Solo debug. Calcula una ruta y escribe el resultado en logcat (tag `RutaDebug`), sin coordenadas:
 * `adb shell am broadcast -n app.trombito.rallycopilot.dev/app.trombito.rallycopilot.debug.RutaDebugReceiver
 *  -a app.trombito.rallycopilot.dev.DEBUG_RUTA --ef olat 39.5696 --ef olon 2.6502 --ef dlat 39.7667 --ef dlon 2.7150
 *  --es perfil SINUOSA [--ez sin_tuneles true  (un único cálculo con exclude_tunnels, sin comparar)]`
 */
class RutaDebugReceiver : BroadcastReceiver() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Acceso {
        fun enrutador(): EnrutadorValhalla
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACCION) return
        val origen = punto(intent, "olat", "olon")
        val destino = punto(intent, "dlat", "dlon")
        if (origen == null || destino == null) {
            Log.w(ETIQUETA, "Faltan extras olat/olon/dlat/dlon (--ef) o están fuera de rango")
            return
        }
        val perfil = perfil(intent.getStringExtra("perfil"))
        val sinTuneles = intent.getBooleanExtra("sin_tuneles", false)
        val enrutador = EntryPointAccessors.fromApplication(context.applicationContext, Acceso::class.java).enrutador()
        val pendiente = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val resultado =
                    if (sinTuneles) {
                        enrutador.calcular(origen, destino, perfil, excluirTuneles = true)
                    } else {
                        enrutador.ruta(origen, destino, perfil)
                    }
                escribir(perfil, sinTuneles, resultado)
            } finally {
                pendiente.finish()
            }
        }
    }

    private fun escribir(
        perfil: PerfilRuta,
        sinTuneles: Boolean,
        resultado: ResultadoRuta,
    ) {
        when (resultado) {
            ResultadoRuta.SinDatos -> {
                Log.w(ETIQUETA, "Sin datos: falta valhalla-mallorca.tar en filesDir/mapa")
            }

            is ResultadoRuta.Error -> {
                Log.e(ETIQUETA, "Error ($perfil): ${resultado.motivo}")
            }

            is ResultadoRuta.Ok -> {
                val ruta = resultado.ruta
                Log.i(
                    ETIQUETA,
                    "OK perfil=$perfil soloSinTuneles=$sinTuneles calculo=${resultado.calculoMs} ms " +
                        "longitud=${"%.1f".format(ruta.longitudM / METROS_POR_KM)} km " +
                        "duracion=${"%.1f".format(ruta.tiempoS / SEGUNDOS_POR_MIN)} min " +
                        "puntos=${ruta.puntos.size} maniobras=${ruta.maniobras.size}",
                )
                val vias = ruta.maniobras.flatMap { it.vias }.distinct()
                Log.i(ETIQUETA, "Vias: ${vias.joinToString(" | ")}")
                ruta.maniobras.forEachIndexed { i, m ->
                    val km = "%.1f".format(m.longitudM / METROS_POR_KM)
                    Log.i(
                        ETIQUETA,
                        "${i + 1}. [${m.tipo}] ${FrasesManiobra.frase(m)} ($km km) salidaRotonda=${m.salidaRotonda}" +
                            " valhalla=\"${m.instruccionValhalla}\"",
                    )
                }
            }
        }
    }

    private fun punto(
        intent: Intent,
        claveLat: String,
        claveLon: String,
    ): PuntoGeo? {
        if (!intent.hasExtra(claveLat) || !intent.hasExtra(claveLon)) return null
        val lat = numero(intent, claveLat)
        val lon = numero(intent, claveLon)
        return if (lat in -LAT_MAX..LAT_MAX && lon in -LON_MAX..LON_MAX) PuntoGeo(lat, lon) else null
    }

    /** `--ef` llega como Float y `--ed` como Double: se acepta cualquiera. */
    private fun numero(
        intent: Intent,
        clave: String,
    ): Double = (intent.extras?.get(clave) as? Number)?.toDouble() ?: Double.NaN

    private fun perfil(texto: String?): PerfilRuta =
        PerfilRuta.entries.firstOrNull { it.name.equals(texto, ignoreCase = true) } ?: PerfilRuta.MAS_RAPIDA

    private companion object {
        const val ACCION = "app.trombito.rallycopilot.dev.DEBUG_RUTA"
        const val ETIQUETA = "RutaDebug"
        const val METROS_POR_KM = 1000.0
        const val SEGUNDOS_POR_MIN = 60.0
        const val LAT_MAX = 90.0
        const val LON_MAX = 180.0
    }
}
