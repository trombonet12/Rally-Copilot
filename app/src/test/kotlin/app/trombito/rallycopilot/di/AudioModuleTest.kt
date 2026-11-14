package app.trombito.rallycopilot.di

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regresión: el colector de ajustes de la ruta de audio vive en el scope de proceso. Si el sistema ya no está (p. ej.
 * Robolectric desmontado entre pruebas) y `checkPermission` revienta, la excepción no puede escapar a
 * `Dispatchers.Default` ni tumbar el flujo: se trata como permiso no concedido.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AudioModuleTest {
    private val sistemaCaido: Context =
        object : ContextWrapper(ApplicationProvider.getApplicationContext()) {
            override fun checkPermission(
                permission: String,
                pid: Int,
                uid: Int,
            ): Int = throw NullPointerException("ActivityThread.getInstrumentation() es null")
        }

    @Test
    fun `si comprobar el permiso falla se trata como no concedido`() {
        assertFalse(AudioModule.permisoBluetooth(sistemaCaido))
    }
}
