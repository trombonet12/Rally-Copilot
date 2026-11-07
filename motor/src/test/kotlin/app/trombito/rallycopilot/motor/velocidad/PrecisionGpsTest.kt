package app.trombito.rallycopilot.motor.velocidad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrecisionGpsTest {
    @Test
    fun `las constantes valen lo decidido`() {
        assertEquals(30.0, PrecisionGps.FIJACION_UTIL_M, 0.0)
        assertEquals(20.0, PrecisionGps.SALIDA_M, 0.0)
        assertEquals(20.0, PrecisionGps.RUMBO_MAX_GRADOS, 0.0)
    }

    @Test
    fun `30 m justos es util y mas no`() {
        assertTrue(PrecisionGps.esUtil(30.0))
        assertFalse(PrecisionGps.esUtil(30.1))
    }

    @Test
    fun `sin precision conocida es util y NaN no`() {
        assertTrue(PrecisionGps.esUtil(null))
        assertFalse(PrecisionGps.esUtil(Double.NaN))
    }
}
