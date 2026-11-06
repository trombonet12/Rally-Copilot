package app.trombito.rallycopilot.ui.ajustes

import androidx.annotation.StringRes
import app.trombito.rallycopilot.R
import app.trombito.rallycopilot.motor.edicion.Orden

/**
 * Los grupos de la chuleta de voz, en el orden en que se enseñan. Sus frases salen de `FrasesOrdenes` (ver
 * [ChuletaVoz]): [grupoDe] reparte cada orden en uno de ellos; las notas dictadas y las órdenes sueltas de [EDICION]
 * y [CONTROL] vienen de `string-array` en `strings_tutorial.xml` y un test comprueba que el intérprete las entiende.
 */
internal enum class GrupoVoz(
    @StringRes val titulo: Int,
) {
    RUTA(R.string.ayuda_voz_grupo_ruta),
    CONTROL(R.string.ayuda_voz_grupo_control),
    NAVEGACION(R.string.ayuda_voz_grupo_navegacion),
    MAPA(R.string.ayuda_voz_grupo_mapa),
    EDICION(R.string.ayuda_voz_grupo_edicion),
    ;

    companion object {
        /** El grupo donde se enseña [orden]; lo que no encaja en ninguno otro se enseña con la edición. */
        fun de(orden: Orden): GrupoVoz =
            when (orden) {
                is Orden.Pregunta -> RUTA

                is Orden.Modo, Orden.Silencio, is Orden.Volumen, is Orden.NotasSimplificadas, is Orden.Avisar,
                is Orden.Confirma, Orden.Ayuda,
                -> CONTROL

                is Orden.IrA, is Orden.ParadaEn, Orden.VolverACasa, Orden.CancelarRuta, is Orden.MasCercano,
                Orden.Otro,
                -> NAVEGACION

                is Orden.Satelite, is Orden.NorteArriba, is Orden.Zoom, Orden.Salida, Orden.ParaCrono,
                Orden.RadarAqui,
                -> MAPA

                else -> EDICION
            }
    }
}

/** Lo que el copiloto no puede hacer, una línea cada uno. */
internal val LIMITES_VOZ =
    listOf(
        R.string.ayuda_voz_limite_dictado,
        R.string.ayuda_voz_limite_lugares,
        R.string.ayuda_voz_limite_nombres,
        R.string.ayuda_voz_limite_idiomas,
    )
