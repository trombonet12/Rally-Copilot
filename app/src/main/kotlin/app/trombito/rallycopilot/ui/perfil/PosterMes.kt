package app.trombito.rallycopilot.ui.perfil

import android.content.res.Resources
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.motor.analisis.NivelProgreso
import app.trombito.rallycopilot.motor.analisis.ResumenMes
import app.trombito.rallycopilot.ui.comun.FormatoEs
import app.trombito.rallycopilot.ui.comun.TipoPlaca
import app.trombito.rallycopilot.ui.comun.tipoPlaca
import java.time.format.TextStyle
import java.util.Locale

private val ES: Locale = Locale.forLanguageTag("es-ES")

/** Como mucho este número de hitos y de placas en el póster: el resto no cabe y se cuenta en las cifras. */
internal const val MAX_HITOS_POSTER = 3
internal const val MAX_PLACAS_POSTER = 5

/** Una cifra del póster: el [valor] grande y su [etiqueta]. */
internal data class CifraPoster(
    val valor: String,
    val etiqueta: String,
)

/**
 * Todo el texto que se dibuja en el póster del resumen del mes, ya resuelto. Es lo único que entra en la imagen:
 * kilómetros, viajes y cuentas, el nivel, los títulos de los hitos del mes y las referencias de carretera (Ma-10,
 * A-11…)
 * de las carreteras estrenadas. Nunca hay mapa, coordenadas, destinos, nombres de tramo ni nombres de calle o lugar.
 */
internal data class ContenidoPoster(
    val marca: String,
    val titulo: String,
    val kilometros: String,
    val etiquetaKilometros: String,
    val cifras: List<CifraPoster>,
    val nivel: String,
    val puntos: String,
    val progreso: Float,
    val hitos: List<String>,
    val refsCarreteras: List<String>,
    val pie: String,
) {
    /** Todos los textos que se dibujan, para comprobar qué lleva la imagen. */
    fun textos(): List<String> =
        buildList {
            add(marca)
            add(titulo)
            add(kilometros)
            add(etiquetaKilometros)
            cifras.forEach {
                add(it.valor)
                add(it.etiqueta)
            }
            add(nivel)
            add(puntos)
            addAll(hitos)
            addAll(refsCarreteras)
            add(pie)
        }

    companion object {
        fun de(
            resumen: ResumenMes,
            recursos: Resources,
        ): ContenidoPoster {
            val mes = resumen.mes.month.getDisplayName(TextStyle.FULL, ES)
            return ContenidoPoster(
                marca = recursos.getString(R.string.perfil_poster_marca),
                titulo = recursos.getString(R.string.perfil_poster_titulo, mes, resumen.mes.year),
                kilometros = FormatoEs.decimal(resumen.km, 1),
                etiquetaKilometros = recursos.getString(R.string.perfil_poster_kilometros),
                cifras =
                    listOf(
                        CifraPoster(resumen.viajes.toString(), recursos.getString(R.string.perfil_poster_viajes)),
                        CifraPoster(
                            resumen.carreterasNuevas.size.toString(),
                            recursos.getString(R.string.perfil_poster_carreteras),
                        ),
                        CifraPoster(
                            resumen.hitosConseguidos.size.toString(),
                            recursos.getString(R.string.perfil_poster_hitos),
                        ),
                        CifraPoster(
                            resumen.records.size.toString(),
                            recursos.getString(R.string.perfil_poster_records),
                        ),
                    ),
                nivel = resumen.nivel.nivel.nombre,
                puntos = textoPuntos(resumen.nivel, recursos),
                progreso = resumen.nivel.progreso.toFloat(),
                hitos = resumen.hitosConseguidos.take(MAX_HITOS_POSTER).map { it.titulo },
                refsCarreteras = resumen.refsNuevas().take(MAX_PLACAS_POSTER),
                pie = recursos.getString(R.string.perfil_poster_pie),
            )
        }

        private fun textoPuntos(
            nivel: NivelProgreso,
            recursos: Resources,
        ): String {
            val umbral = nivel.umbralSiguiente
            return if (umbral != null) {
                recursos.getString(R.string.perfil_temporada_progreso, nivel.puntos, umbral)
            } else {
                recursos.getString(R.string.perfil_temporada_puntos, nivel.puntos)
            }
        }
    }
}

/**
 * Las carreteras estrenadas que tienen una referencia con forma de placa (Ma-10, A-11, AP-7…). Las que solo tienen
 * nombre (una calle, un paseo) se descartan: pueden ser un lugar exacto y la imagen se comparte fuera de la app.
 */
internal fun ResumenMes.refsNuevas(): List<String> = carreterasNuevas.filter { tipoPlaca(it) != TipoPlaca.NEUTRA }
