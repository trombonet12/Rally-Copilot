package app.trombito.rallycopilot.ui.mapa

import org.maplibre.android.maps.MapLibreMap

/**
 * Las [ConsultasToque] de un toque en [x], [y] (píxeles) sobre el mapa real: cada capa mira primero lo que tiene
 * pintado bajo el dedo (`queryRenderedFeatures`) y, si no responde (solo ve lo ya colocado en el último fotograma y
 * falla con la cámara en movimiento, el seguimiento del coche), mide en pantalla la distancia a sus marcadores con las
 * [preferencias] vigentes (≤ [MARGEN_TOQUE_DP]). La nota editable y la capa de calor solo usan lo pintado.
 */
@Suppress("LongParameterList") // el mapa, lo que pinta y el toque (x, y y densidad)
internal fun consultasDeMapa(
    mapa: MapLibreMap,
    preferencias: PreferenciasVistaMapa,
    capaCalor: CapaCalor,
    x: Float,
    y: Float,
    densidad: Float,
): ConsultasToque =
    ConsultasToque(
        incidencia = {
            CapaIncidencias.idEn(mapa, x, y, densidad)
                ?: CapaIncidencias.idCercano(mapa, preferencias.incidencias, x, y, densidad)
        },
        radar = {
            CapaRadares.radarEn(mapa, x, y, densidad)
                ?: CapaRadares.radarCercano(mapa, preferencias.radares.marcadores, x, y, densidad)
        },
        tramoRadar = {
            CapaRadares.tramoEn(mapa, x, y, densidad)
                ?: CapaRadares.tramoCercano(mapa, preferencias.radares.tramos, x, y, densidad)
        },
        bandera = {
            CapaTramos.banderaEn(mapa, x, y, densidad)
                ?: CapaTramos.banderaCercana(mapa, preferencias.tramos, x, y, densidad)
        },
        notaEditable = { CapaNotas.posicionEn(mapa, x, y, densidad) },
        notaMapa = {
            CapaNotasMapa.posicionEn(mapa, x, y, densidad)
                ?: CapaNotasMapa.posicionCercana(mapa, preferencias.notasMapa, x, y, densidad)
        },
        calor = { capaCalor.fichaEn(mapa, x, y, densidad) },
    )
