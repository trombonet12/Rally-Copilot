package app.trombito.rallycopilot.ui.mistramos

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.trombito.rallycopilot.domain.tramos.TramoExportado
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class CompartirTramoTest {
    private val contexto = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun carpeta() = File(contexto.cacheDir, "compartir")

    @Test
    fun `el archivo se escribe en la cache con el contenido en UTF-8`() {
        val archivo =
            escribirArchivoParaCompartir(contexto, TramoExportado("palma-montuiri.gpx", "<gpx>Montuïri</gpx>"))

        assertNotNull(archivo)
        assertEquals(File(carpeta(), "palma-montuiri.gpx"), archivo)
        assertEquals("<gpx>Montuïri</gpx>", archivo?.readText(Charsets.UTF_8))
    }

    @Test
    fun `lo compartido antes se borra para no acumular recorridos en la cache`() {
        escribirArchivoParaCompartir(contexto, TramoExportado("uno.gpx", "<gpx/>"))
        escribirArchivoParaCompartir(contexto, TramoExportado("dos.gpx", "<gpx/>"))

        assertFalse(File(carpeta(), "uno.gpx").exists())
        assertTrue(File(carpeta(), "dos.gpx").exists())
    }

    @Test
    fun `la autoridad del proveedor es la del paquete y el proveedor no esta exportado`() {
        assertEquals("${contexto.packageName}.compartir", autoridadCompartir(contexto))
        val info =
            contexto.packageManager.getPackageInfo(
                contexto.packageName,
                android.content.pm.PackageManager.GET_PROVIDERS,
            )
        val proveedor = info.providers.orEmpty().single { it.authority == autoridadCompartir(contexto) }
        assertFalse(proveedor.exported)
        assertTrue(proveedor.grantUriPermissions)
    }

    @Test
    fun `el envio es un SEND de GPX con el archivo y permiso temporal de lectura`() {
        val uri = Uri.parse("content://${contexto.packageName}.compartir/compartir/tramo.gpx")
        val selector = intentCompartir(uri, "tramo.gpx", "Compartir tramo")

        assertEquals(Intent.ACTION_CHOOSER, selector.action)
        assertEquals("Compartir tramo", selector.getStringExtra(Intent.EXTRA_TITLE))
        @Suppress("DEPRECATION")
        val envio = selector.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertEquals(Intent.ACTION_SEND, envio?.action)
        assertEquals(TIPO_GPX, envio?.type)
        @Suppress("DEPRECATION")
        assertEquals(uri, envio?.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertTrue((envio?.flags ?: 0) and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(uri, envio?.clipData?.getItemAt(0)?.uri)
        assertTrue(envio?.clipData is ClipData)
    }
}
