package app.trombito.rallycopilot.data.datosmapa

import app.trombito.rallycopilot.motor.datos.ArchivoDatos
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class DescargadorArchivoTest {
    @get:Rule val carpeta = TemporaryFolder()

    private lateinit var entorno: EntornoDatosMapa
    private lateinit var descargador: DescargadorArchivo
    private lateinit var parcial: File
    private val contenido = PaqueteDePrueba.aleatorio(semilla = 42, tamano = 300_000)

    @Before
    fun preparar() {
        entorno = EntornoDatosMapa(carpeta.newFolder("entorno"))
        descargador = DescargadorArchivo(ClienteHttp(entorno.config))
        parcial = File(carpeta.root, "mallorca.pmtiles.part")
    }

    @After
    fun cerrar() = entorno.close()

    private fun publicar(
        ignorarRange: Boolean = false,
        bytes: ByteArray = contenido,
    ): Pair<ArchivoDatos, ServidorFalso.Recurso> {
        val archivo = entorno.archivo("vectorial", contenido, "v1")
        val recurso = entorno.servidor.servir(entorno.rutaDe(archivo), ServidorFalso.Recurso(bytes, ignorarRange))
        return archivo to recurso
    }

    private fun descargar(
        archivo: ArchivoDatos,
        progreso: MutableList<Long> = mutableListOf(),
    ) = runBlocking { descargador.descargar(archivo, parcial) { progreso += it } }

    @Test
    fun `descarga completa verifica los bytes y el hash`() {
        val (archivo, _) = publicar()
        val progreso = mutableListOf<Long>()

        descargar(archivo, progreso)

        assertArrayEquals(contenido, parcial.readBytes())
        assertEquals(contenido.size.toLong(), progreso.last())
        assertEquals(progreso.sorted(), progreso)
        val peticion = entorno.servidor.peticionesA(entorno.rutaDe(archivo)).single()
        assertNull("La primera petición no lleva Range", peticion.range)
        assertEquals("RallyCopilot/test", peticion.userAgent)
    }

    @Test
    fun `tras un corte de red conserva el parcial y reanuda con Range`() {
        val (archivo, recurso) = publicar()
        recurso.cortarTras = 100_000

        try {
            descargar(archivo)
            fail("Debía fallar por el corte de red")
        } catch (expected: IOException) {
            // esperado
        }
        val tamanoParcial = parcial.length()
        assertTrue("Debe quedar parte descargada", tamanoParcial in 1 until contenido.size)

        descargar(archivo)

        assertArrayEquals(contenido, parcial.readBytes())
        val peticiones = entorno.servidor.peticionesA(entorno.rutaDe(archivo))
        assertEquals(2, peticiones.size)
        assertEquals("bytes=$tamanoParcial-", peticiones[1].range)
    }

    @Test
    fun `si el servidor ignora Range reinicia la descarga y queda bien`() {
        val (archivo, _) = publicar(ignorarRange = true)
        parcial.writeBytes(contenido.copyOf(50_000))

        descargar(archivo)

        assertArrayEquals(contenido, parcial.readBytes())
        val peticion = entorno.servidor.peticionesA(entorno.rutaDe(archivo)).single()
        assertEquals("bytes=50000-", peticion.range)
    }

    @Test
    fun `un hash incorrecto borra el parcial y falla`() {
        val archivo = entorno.archivo("vectorial", contenido, "v1")
        val otroContenido = contenido.copyOf().also { it[10] = (it[10] + 1).toByte() }
        entorno.servidor.servir(entorno.rutaDe(archivo), otroContenido)

        try {
            descargar(archivo)
            fail("Debía fallar por hash incorrecto")
        } catch (expected: HashIncorrectoException) {
            // esperado
        }
        assertFalse(parcial.exists())
        assertEquals(1, entorno.servidor.peticionesA(entorno.rutaDe(archivo)).size)
    }

    @Test
    fun `un parcial viejo de otra version se descarta y se reintenta desde cero una vez`() {
        val (archivo, _) = publicar()
        parcial.writeBytes(PaqueteDePrueba.aleatorio(semilla = 7, tamano = 80_000))

        descargar(archivo)

        assertArrayEquals(contenido, parcial.readBytes())
        val peticiones = entorno.servidor.peticionesA(entorno.rutaDe(archivo))
        assertEquals(listOf("bytes=80000-", null), peticiones.map { it.range })
    }

    @Test
    fun `sigue una redireccion como las de GitHub y conserva el Range`() {
        val (archivo, _) = publicar()
        val ruta = entorno.rutaDe(archivo)
        entorno.servidor.servir("/real/archivo", ServidorFalso.Recurso(contenido))
        entorno.servidor.redirigir(ruta, "/real/archivo")
        parcial.writeBytes(contenido.copyOf(1_000))

        descargar(archivo)

        assertArrayEquals(contenido, parcial.readBytes())
        val peticion = entorno.servidor.peticionesA("/real/archivo").single()
        assertEquals("bytes=1000-", peticion.range)
    }

    @Test
    fun `un Content-Length distinto del manifest se rechaza antes de escribir`() {
        val archivo = entorno.archivo("vectorial", contenido, "v1")
        entorno.servidor.servir(entorno.rutaDe(archivo), contenido + ByteArray(1_000))

        assertThrows(HashIncorrectoException::class.java) { descargar(archivo) }

        assertFalse(parcial.exists())
    }

    @Test
    fun `un Content-Range con otro total se rechaza antes de escribir`() {
        val archivo = entorno.archivo("vectorial", contenido, "v1")
        entorno.servidor.servir(entorno.rutaDe(archivo), contenido + ByteArray(1_000))
        parcial.writeBytes(contenido.copyOf(10_000))

        assertThrows(HashIncorrectoException::class.java) { descargar(archivo) }

        assertFalse(parcial.exists())
    }

    @Test
    fun `sin Content-Length corta en cuanto se superan los bytes del manifest`() {
        val archivo = entorno.archivo("vectorial", contenido, "v1")
        val demasiado = contenido + ByteArray(200_000)
        entorno.servidor.servir(entorno.rutaDe(archivo), ServidorFalso.Recurso(demasiado, sinContentLength = true))
        val progreso = mutableListOf<Long>()

        assertThrows(HashIncorrectoException::class.java) { descargar(archivo, progreso) }

        assertTrue("No debe escribir más de lo anunciado", progreso.all { it <= archivo.bytes })
        assertFalse(parcial.exists())
    }

    @Test
    fun `sin Content-Length y con el tamaño exacto descarga bien`() {
        val (archivo, _) = publicar()
        entorno.servidor.servir(entorno.rutaDe(archivo), ServidorFalso.Recurso(contenido, sinContentLength = true))

        descargar(archivo)

        assertArrayEquals(contenido, parcial.readBytes())
    }

    @Test
    fun `un error HTTP es un fallo de red recuperable y no deja parcial`() {
        val archivo = entorno.archivo("vectorial", contenido, "v1")

        try {
            descargar(archivo)
            fail("Debía fallar con 404")
        } catch (e: IOException) {
            assertTrue(e.message.orEmpty().contains("404"))
        }
        assertFalse(parcial.exists())
    }
}
