package app.trombito.rallycopilot.motor.tramos

import org.junit.Assert.assertEquals

/** Compara dos números con una tolerancia (los tiempos del motor van en `Long` ms y JUnit no tiene esa sobrecarga). */
fun assertCerca(
    esperado: Number,
    real: Number,
    tolerancia: Double,
) = assertEquals(esperado.toDouble(), real.toDouble(), tolerancia)
