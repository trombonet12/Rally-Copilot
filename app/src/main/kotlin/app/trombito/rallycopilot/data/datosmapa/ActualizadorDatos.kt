package app.trombito.rallycopilot.data.datosmapa

import android.util.Log
import app.trombito.rallycopilot.data.db.AjustesMapasDao
import app.trombito.rallycopilot.data.db.ArchivoInstaladoEntity
import app.trombito.rallycopilot.data.db.DatosMapaDao
import app.trombito.rallycopilot.data.db.archivos
import app.trombito.rallycopilot.data.db.estado
import app.trombito.rallycopilot.domain.datos.AjustesMapas
import app.trombito.rallycopilot.domain.datos.MotivoError
import app.trombito.rallycopilot.motor.datos.ArchivoDatos
import app.trombito.rallycopilot.motor.datos.ArchivoLocal
import app.trombito.rallycopilot.motor.datos.Complemento
import app.trombito.rallycopilot.motor.datos.ManifestDatos
import app.trombito.rallycopilot.motor.datos.ManifestInvalido
import app.trombito.rallycopilot.motor.datos.PlanificadorDescarga
import app.trombito.rallycopilot.motor.datos.ProblemaEspacio
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import javax.inject.Inject

/** Resultado de una pasada de [ActualizadorDatos]. Los cortes de red no son un resultado: lanzan [IOException]. */
sealed interface ResultadoActualizacion {
    data object Instalado : ResultadoActualizacion

    data class Fallo(
        val motivo: MotivoError,
    ) : ResultadoActualizacion
}

/**
 * Una pasada completa: manifest, plan, descarga verificada de lo que falta e instalación atómica. No sabe nada de
 * WorkManager (lo envuelve [TrabajoDatosMapa]), así que se prueba directamente.
 */
class ActualizadorDatos
    @Inject
    constructor(
        private val dao: DatosMapaDao,
        private val ajustes: AjustesMapasDao,
        private val config: ConfigDatosMapa,
        /** La siembra de los datos del APK termina antes de comparar con el manifest remoto. */
        private val siembra: EsperaDatosSembrados = EsperaDatosSembrados.Ninguna,
    ) {
        private val http = ClienteHttp(config)
        private val descargador = DescargadorArchivo(http)
        private val instalador = InstaladorDatos(config)

        /**
         * Qué se baja: el paquete base (lo del APK, siempre al día) más lo bajo demanda que ya está instalado (se
         * actualiza) y los [complementos] pedidos. Sin pedir ninguno no se estrena el satélite, el relieve ni la voz.
         *
         * @param automatico true si lo lanzó la comprobación al abrir la app: si ya había datos, deja un aviso.
         * @param complementos los complementos bajo demanda que se piden ahora (los instalados se actualizan siempre).
         * @param soloComplementos solo baja y actualiza los [complementos] pedidos, sin tocar el paquete base ni el
         * resto (se usa con el relieve y la voz: pedir 20 MB no debe arrastrar una actualización de 70 MB).
         * @param alProgreso bytes hechos y bytes totales a descargar (solo lo que falta, no el paquete entero).
         * @throws IOException si se corta la red (los `.part` se conservan para reanudar).
         */
        suspend fun actualizar(
            automatico: Boolean,
            complementos: Set<Complemento> = emptySet(),
            soloComplementos: Boolean = false,
            alProgreso: suspend (hechos: Long, totales: Long) -> Unit = { _, _ -> },
        ): ResultadoActualizacion {
            val resultado =
                try {
                    // Una sola descarga a la vez: comparten .nuevo/ y el registro de lo instalado.
                    CERROJO.withLock { ejecutar(Pedido(automatico, complementos, soloComplementos), alProgreso) }
                } catch (e: ManifestInvalido) {
                    fallo(MotivoError.MANIFEST_INVALIDO, e)
                } catch (e: HashIncorrectoException) {
                    fallo(MotivoError.HASH_INCORRECTO, e)
                } catch (e: ZipInseguroException) {
                    fallo(MotivoError.DESCONOCIDO, e)
                }
            if (resultado is ResultadoActualizacion.Fallo) registrarError(resultado.motivo)
            return resultado
        }

        suspend fun registrarError(motivo: MotivoError) = dao.establecerError(motivo.name)

        private fun fallo(
            motivo: MotivoError,
            causa: Exception,
        ): ResultadoActualizacion.Fallo {
            Log.w(ETIQUETA_LOG, "Actualización de datos fallida ($motivo)", causa)
            return ResultadoActualizacion.Fallo(motivo)
        }

        private suspend fun ejecutar(
            pedido: Pedido,
            alProgreso: suspend (Long, Long) -> Unit,
        ): ResultadoActualizacion {
            siembra.esperar()
            val publicado = http.leerManifestRemoto(config)
            val instalados = dao.archivos()
            val remoto = seleccionar(publicado.manifest, instalados, pedido)
            // Si el manifest no trae lo pedido (publicación vieja o incompleta) no es un éxito silencioso.
            val faltaLoPedido =
                pedido.soloComplementos && pedido.complementos.any { c -> remoto.archivos.none { c.incluye(it.id) } }
            return if (faltaLoPedido) {
                ResultadoActualizacion.Fallo(MotivoError.MANIFEST_INVALIDO)
            } else {
                aplicar(publicado, remoto, instalados, pedido, alProgreso)
            }
        }

        /**
         * Lo bajo demanda (satélite ≈1,5 GB, relieve, voz Sharvard) solo se estrena si el usuario lo pide: ni una
         * actualización automática ni «Descargar mapas» tras una siembra fallida (faltan los datos base) estrenan uno;
         * sí actualizan los que ya hay instalados. Con [Pedido.soloComplementos], solo lo pedido.
         */
        private fun seleccionar(
            completo: ManifestDatos,
            instalados: List<ArchivoInstaladoEntity>,
            pedido: Pedido,
        ): ManifestDatos {
            val idsInstalados = instalados.map { it.id }.toSet()
            val faltaBase = IDS_SEMBRADOS.any { id -> instalados.none { it.id == id } }
            val pedidos = if (faltaBase && !pedido.soloComplementos) emptySet() else pedido.complementos
            return completo.copy(
                archivos =
                    completo.archivos.filter {
                        val complemento = Complemento.de(it.id)
                        if (pedido.soloComplementos) {
                            complemento != null && complemento in pedidos
                        } else {
                            complemento == null || it.id in idsInstalados || complemento in pedidos
                        }
                    },
            )
        }

        private suspend fun aplicar(
            publicado: ManifestRemoto,
            remoto: ManifestDatos,
            instalados: List<ArchivoInstaladoEntity>,
            pedido: Pedido,
            alProgreso: suspend (Long, Long) -> Unit,
        ): ResultadoActualizacion {
            val completo = publicado.manifest
            val limite = (ajustes.ajustes()?.aDominio() ?: AjustesMapas()).limiteBytes
            // Sin el manifest extra no se sabe nada de sus archivos: lo instalado de ahí se deja como está. Con solo
            // complementos, el plan solo ve los que se piden.
            val locales =
                instalados
                    .filter { publicado.extraDisponible || !it.id.esSateliteExtra(completo) }
                    .filter { !pedido.soloComplementos || Complemento.de(it.id) in pedido.complementos }
                    .map { ArchivoLocal(it.id, it.sha256, it.bytes) }
            // Lo ya bajado en .nuevo/ (de un intento anterior) ya ocupa disco: no cuenta contra el espacio libre.
            val libres = config.bytesLibres() + instalador.bytesEnCurso(remoto.archivos)
            val plan = PlanificadorDescarga.planificar(remoto, locales, libres, limite)
            val problema =
                when (plan.problema) {
                    ProblemaEspacio.SUPERA_LIMITE -> MotivoError.SUPERA_LIMITE
                    ProblemaEspacio.SIN_ESPACIO_LIBRE -> MotivoError.SIN_ESPACIO
                    null -> null
                }
            if (problema != null) return ResultadoActualizacion.Fallo(problema)

            descargarTodo(plan.aDescargar, plan.bytesADescargar, alProgreso)

            val nombresABorrar = instalados.filter { it.id in plan.aBorrar }.map { it.nombre }
            instalador.instalar(plan.aDescargar, nombresABorrar, completo.archivos.map { it.nombre })
            // Solo un complemento: el paquete base no se ha actualizado, así que su versión no cambia.
            val versionBase = dao.estado()?.versionInstalada?.takeIf { pedido.soloComplementos && it.isNotBlank() }
            val hayAviso = !pedido.soloComplementos && pedido.automatico && instalados.isNotEmpty()
            dao.registrarInstalacion(
                archivos = plan.aDescargar.map { it.aEntidad() },
                idsBorrados = plan.aBorrar,
                version = versionBase ?: remoto.version,
                avisar = hayAviso && plan.aDescargar.isNotEmpty(),
            )
            return ResultadoActualizacion.Instalado
        }

        /** Qué se pide en una pasada (ver [actualizar]). */
        private class Pedido(
            val automatico: Boolean,
            val complementos: Set<Complemento>,
            val soloComplementos: Boolean,
        )

        private suspend fun descargarTodo(
            archivos: List<ArchivoDatos>,
            totales: Long,
            alProgreso: suspend (Long, Long) -> Unit,
        ) {
            var completados = 0L
            alProgreso(0, totales)
            for (archivo in archivos) {
                if (instalador.estaVerificado(archivo)) {
                    completados += archivo.bytes
                    alProgreso(completados, totales)
                    continue
                }
                val base = completados
                descargador.descargar(archivo, instalador.parcial(archivo)) { hechos ->
                    alProgreso(base + hechos, totales)
                }
                instalador.marcarVerificado(archivo)
                completados += archivo.bytes
            }
            alProgreso(totales, totales)
        }
    }

private const val ETIQUETA_LOG = "ActualizadorDatos"

private val CERROJO = Mutex()

/** Los ids de los archivos de satélite empiezan así (`satelite`, y los que añada el z17). */
internal const val PREFIJO_SATELITE = "satelite"

/** Las teselas del satélite instaladas son `pnoa-mallorca*.pmtiles` (z16 o z17: el nombre cambia con el paquete). */
internal const val PREFIJO_TESELAS_SATELITE = "pnoa-mallorca"

/** Un id de satélite que el manifest principal no lista: viene del extra (`satelite-z17`). */
private fun String.esSateliteExtra(principal: ManifestDatos) =
    startsWith(PREFIJO_SATELITE) && principal.archivo(this) == null

internal fun ArchivoDatos.aEntidad() =
    ArchivoInstaladoEntity(id = id, nombre = nombre, sha256 = sha256, bytes = bytes, version = version)
