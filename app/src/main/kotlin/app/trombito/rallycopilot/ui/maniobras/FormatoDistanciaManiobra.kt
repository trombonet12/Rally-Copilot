package app.trombito.rallycopilot.ui.maniobras

import app.trombito.rallycopilot.ui.comun.FormatoEs

private const val PASO_FINO_M = 10
private const val PASO_GRUESO_M = 50

/**
 * Distancia para el banner: por debajo de 200 m se redondea a 10 m, hasta 1 km a 50 m («350 m») y desde 1 km se da
 * en km con una decimal y coma («1,2 km»).
 */
fun formatearDistanciaManiobra(metros: Double): String =
    FormatoEs.distanciaRedondeada(metros, PASO_FINO_M, PASO_GRUESO_M)
