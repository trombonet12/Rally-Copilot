# Política de privacidad de Rally Copilot

> Propósito: política de privacidad pública de la app (enlazada desde Google Play) · Audiencia: usuarios de la app · Actualizado: 2026-11-25

## Resumen
Rally Copilot no tiene cuentas, ni anuncios, ni analítica, ni servidores propios. Tu posición, tus recorridos y tus
tiempos se quedan en tu móvil: la app no los envía a nadie. Internet solo se usa para descargar datos de mapas y refrescar
incidencias de tráfico, y esas peticiones no llevan tu posición ni ningún dato tuyo (ver «Red»).

## Qué guarda la app y dónde
- **Ubicación precisa:** se usa para guiarte, generar las notas de rally, medir los tramos y mostrarte en el mapa.
  Se procesa solo en el móvil y no se envía a ningún servidor.
- **Recorridos, tramos, tiempos, notas editadas y ajustes:** se guardan en una base de datos local del móvil y no salen
  de él. La app tiene desactivada la copia de seguridad de Android (`allowBackup`): esa copia no guarda nada de Rally
  Copilot.
- **Copia de seguridad propia:** solo sale del almacenamiento privado de la app si tú la exportas (Ajustes > Datos) o si
  compartes un tramo en GPX con el menú de Android. Eres tú quien elige el destino.
- **Registros de diagnóstico y copia automática:** la app puede dejar un registro de diagnóstico y una copia
  automática de tus datos en su carpeta externa, accesible por USB. Se controla con un interruptor en Ajustes > Datos.
  No se envían a ningún sitio: solo los lee quien conecte el móvil por USB. El registro de diagnóstico filtra las
  coordenadas antes de escribir (guarda velocidades, precisiones, distancias, tiempos y nombres de vía). La copia
  automática sí contiene tus recorridos, igual que la copia que exportas a mano.

## Permisos
- **Ubicación (precisa y aproximada):** solo con la app en uso, incluso con la pantalla apagada mientras guía o
  cronometra un tramo. La app no pide ubicación en segundo plano. Mientras dura, el sistema muestra una notificación
  permanente.
- **Micrófono:** solo se usa si activas «Copiloto» o pulsas el micrófono para dar una orden. El reconocimiento de voz
  se hace en el propio móvil, sin conexión (Vosk). La app no guarda audio ni transcripciones y no los envía.
- **Bluetooth (conexión):** solo para enviar la voz del copiloto al intercomunicador del casco y escuchar tus órdenes por
  él. Si lo deniegas, todo sigue por el altavoz y el micrófono del móvil.
- **Notificaciones y servicio en primer plano:** avisan de la navegación en marcha, de las descargas de mapas y de
  la escucha de «Copiloto».
- **Internet y estado de la red:** para las descargas descritas en «Red».

## Red
La app funciona sin conexión. Solo se conecta en estos casos:
- **Datos de mapas de Mallorca:** se descargan de GitHub, del repositorio público `rally-copilot-data` (mapas, vías,
  radares, índice de búsqueda, relieve).
- **Incidencias de tráfico:** se consultan al Punto de Acceso Nacional (NAP) de la DGT.
- **Mapa de satélite:** cuando usas esa capa y no tienes la zona descargada, la app pide teselas de las ortofotos PNOA
  al servidor del Instituto Geográfico Nacional (IGN). El servidor ve qué zona del mapa se pide y tu dirección IP.
- **Enlaces cortos de mapas:** si pegas un enlace corto, la app lo sigue por internet para obtener las coordenadas.

Salvo la zona de las teselas de satélite, estas peticiones no incluyen tu ubicación ni ningún identificador de la app.
Como en cualquier conexión a internet, los servidores ven tu dirección IP. Las rutas se calculan en el móvil.

## Terceros
Sin publicidad, sin analítica, sin cuentas y sin servidores propios. Los servicios con los que se conecta la app son
GitHub (datos de mapas), la DGT (incidencias) y el IGN (satélite), y se rigen por sus propias políticas. Si instalas la app
desde Google Play, Play gestiona la descarga y las actualizaciones.

## Contacto
Trombonet12© and Co. Interactive Systems Solutions · trombonet12@trombonet12.es · https://www.trombonet12.es/
