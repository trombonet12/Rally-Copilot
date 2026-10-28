package app.trombito.rallycopilot.motor.datos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlanificadorDescargaTest {
    private val remoto =
        ManifestDatos(
            esquema = 1,
            version = "v2",
            generado = "2026-10-28T10:00:00Z",
            archivos =
                listOf(
                    archivo("vectorial", "aa", 400),
                    archivo("estilo", "bb", 10),
                    archivo("vias", "cc", 50),
                    archivo("satelite", "dd", 900),
                ),
        )
    private val igualQueRemoto = remoto.archivos.map { ArchivoLocal(it.id, it.sha256, it.bytes) }

    @Test
    fun `el limite por defecto son 2 GB`() {
        assertEquals(2_147_483_648L, LIMITE_POR_DEFECTO)
    }

    @Test
    fun `todo nuevo se descarga entero`() {
        val plan = PlanificadorDescarga.planificar(remoto, emptyList(), bytesLibres = 10_000, limiteBytes = 10_000)

        assertEquals(listOf("vectorial", "estilo", "vias", "satelite"), plan.aDescargar.map { it.id })
        assertEquals(emptyList<String>(), plan.aBorrar)
        assertEquals(1_360L, plan.bytesADescargar)
        assertEquals(1_360L, plan.bytesTrasActualizar)
        assertNull(plan.problema)
    }

    @Test
    fun `si nada cambia no se descarga ni se borra`() {
        val plan = PlanificadorDescarga.planificar(remoto, igualQueRemoto, bytesLibres = 0, limiteBytes = 1_360)

        assertEquals(emptyList<ArchivoDatos>(), plan.aDescargar)
        assertEquals(emptyList<String>(), plan.aBorrar)
        assertEquals(0L, plan.bytesADescargar)
        assertEquals(1_360L, plan.bytesTrasActualizar)
        assertNull(plan.problema)
    }

    @Test
    fun `si solo cambia uno solo se descarga ese`() {
        val locales = igualQueRemoto.map { if (it.id == "vias") it.copy(sha256 = "viejo", bytes = 40) else it }

        val plan = PlanificadorDescarga.planificar(remoto, locales, bytesLibres = 100, limiteBytes = 10_000)

        assertEquals(listOf("vias"), plan.aDescargar.map { it.id })
        assertEquals(emptyList<String>(), plan.aBorrar)
        assertEquals(50L, plan.bytesADescargar)
        assertEquals(1_360L, plan.bytesTrasActualizar)
        assertNull(plan.problema)
    }

    @Test
    fun `un archivo local que ya no esta en remoto se borra`() {
        val locales = igualQueRemoto + ArchivoLocal("antiguo", "ee", 77)

        val plan = PlanificadorDescarga.planificar(remoto, locales, bytesLibres = 0, limiteBytes = 10_000)

        assertEquals(emptyList<ArchivoDatos>(), plan.aDescargar)
        assertEquals(listOf("antiguo"), plan.aBorrar)
        assertEquals(1_360L, plan.bytesTrasActualizar)
        assertNull(plan.problema)
    }

    @Test
    fun `supera el limite si el total tras actualizar lo excede`() {
        val plan = PlanificadorDescarga.planificar(remoto, emptyList(), bytesLibres = 10_000, limiteBytes = 1_359)

        assertEquals(ProblemaEspacio.SUPERA_LIMITE, plan.problema)
        assertEquals(1_360L, plan.bytesTrasActualizar)
    }

    @Test
    fun `justo en el limite no es problema`() {
        val plan = PlanificadorDescarga.planificar(remoto, emptyList(), bytesLibres = 1_360, limiteBytes = 1_360)
        assertNull(plan.problema)
    }

    @Test
    fun `sin espacio libre si lo que se baja no cabe`() {
        val plan = PlanificadorDescarga.planificar(remoto, emptyList(), bytesLibres = 1_359, limiteBytes = 10_000)
        assertEquals(ProblemaEspacio.SIN_ESPACIO_LIBRE, plan.problema)
    }

    @Test
    fun `el archivo viejo sigue en disco mientras se baja el nuevo`() {
        val locales = igualQueRemoto.map { if (it.id == "satelite") it.copy(sha256 = "viejo", bytes = 850) else it }

        // Se descargan 900 B nuevos mientras el viejo (850 B) sigue en disco: hacen falta 900 libres, no 50.
        val sinHueco = PlanificadorDescarga.planificar(remoto, locales, bytesLibres = 899, limiteBytes = 10_000)
        assertEquals(ProblemaEspacio.SIN_ESPACIO_LIBRE, sinHueco.problema)
        assertEquals(900L, sinHueco.bytesADescargar)
        assertEquals(1_360L, sinHueco.bytesTrasActualizar)

        val conHueco = PlanificadorDescarga.planificar(remoto, locales, bytesLibres = 900, limiteBytes = 10_000)
        assertNull(conHueco.problema)
    }

    @Test
    fun `supera el limite tiene prioridad sobre sin espacio libre`() {
        val plan = PlanificadorDescarga.planificar(remoto, emptyList(), bytesLibres = 0, limiteBytes = 100)
        assertEquals(ProblemaEspacio.SUPERA_LIMITE, plan.problema)
    }

    private companion object {
        fun archivo(
            id: String,
            sha: String,
            bytes: Long,
        ) = ArchivoDatos(
            id = id,
            nombre = "$id.bin",
            url = "https://github.com/trombonet12/rally-copilot-data/releases/download/t/$id.bin",
            bytes = bytes,
            sha256 = sha,
            version = "v2",
            atribucion = "x",
            licencia = "y",
        )
    }
}
