# Radares e incidencias de tráfico en Mallorca (DGT / NAP / OSM)

> Propósito: decidir de dónde salen radares (fijos, de tramo, móviles) e incidencias dinámicas (obras, cortes, retenciones, accidentes) y cómo consumirlos sin backend · Audiencia: agente principal y usuario · Actualizado: 2026-10-28 · Relacionado: docs/DECISIONES.md § Incidencias y radares, ROADMAP.md § H5

## Contexto
- Requisito (prompt §4.6): radares fijos/de tramo guardados offline con los mapas; incidencias refrescadas solo al abrir la app y al iniciar ruta; sin Waze ni Google; sin backend propio.
- Los datos de esta sección con "(medido)" los he descargado y analizado el 2026-10-28 desde las URLs oficiales del NAP y del PDF de la DGT; el resto viene de las fichas del NAP.
- Quién gestiona el tráfico en Mallorca: el feed de la DGT incluye vías `Ma-xx` de Mallorca (medido, ver abajo) y las infracciones de la Ma-10 las sanciona la DGT (prensa, no oficial). El Consell de Mallorca es titular de la mayoría de carreteras `Ma-` y planifica obras, pero **no he encontrado ningún portal de datos abiertos ni API del Consell con obras/cortes** (búsquedas 2026-10-28; sin resultados) → VERIFICAR preguntando al Consell si publica algo.

## Alternativas
| Alternativa | Pros | Contras | Licencia | Madurez / versión verificada |
|---|---|---|---|---|
| NAP DGT, incidencias DATEX II v3.7 (`https://nap.dgt.es/datex2/v3/dgt/SituationPublication/datex2_v37.xml`) | Cubre Baleares (área ES53 en la ficha; medido: 18 situaciones en Mallorca hoy: obras, cortes de carril/calzada, tráfico lento, obstáculos). Refresco 1 min, acceso HTTPS pull, sin autenticación (medido) | Un solo fichero nacional: 6,2 MB sin comprimir (medido), ~200 KB con gzip. Excluye País Vasco y Cataluña (irrelevante). Perfil propio DGT, hay que parsear XML. Sin texto libre (`generalPublicComment` ausente) | CC BY según la ficha; "condiciones de uso" apunta al aviso legal de dgt.es, que **no** trata la reutilización → VERIFICAR con nap@dgt.es | v3.7 vigente; v3.6 y v3 también publicados; el dataset antiguo "Incidencias DGT" está DEPRECATED desde 12/01/2026 |
| NAP DGT, radares fijos (`.../PredefinedLocationsPublication/radares/content.xml`, DATEX II v1) | Coordenadas WGS84, carretera, sentido. Medido: 13 cabinas en Mallorca (Ma-1, 12, 13, 14, 15, 19, 2200, 4020), 2,0 MB nacional | `publicationTime` del fichero = 2025-12-18 pese a declarar refresco horario (medido) → posible fichero congelado, VERIFICAR. Sin radares de tramo en Mallorca (medido: 47 tramos nacionales, 0 en la isla) aunque la prensa habla de tramos nuevos. "Cabina" no implica radar activo | CC BY (ficha) | Metadatos de 2016, versión 1.0 |
| PDF "Informe de cinemómetros" de la DGT (`INFORME_CINEMOMETROS_WEB_OK_20250321_extra.pdf`) | Única fuente oficial de **radares móviles** (tramos): medido, 24 filas Mallorca (Ma-10, Ma-11, Ma-13A, Ma-19A, Ma-6014…) y 14 fijos; 0 de tramo en Baleares. Fecha de informe 21/03/2025 | Solo carretera + PK (o rango) + sentido, sin coordenadas; PDF cuya URL lleva fecha (no estable), se parsea con `pdftotext`. Los tramos son "zona probable", no certeza (prensa) | Sin licencia expresa localizada → VERIFICAR | Informe de 2025-03; desactualizado frente al feed |
| OSM: `highway=speed_camera`, `enforcement=maxspeed` (relaciones `type=enforcement`) | Medido vía Overpass: 79 elementos en Mallorca (37 nodos, 42 relaciones; 22 con `maxspeed=90`, 15 con 50). Viaja con los mapas, apto offline, geometría casada con la red | Calidad desigual, no distingue fijo/tramo/móvil con fiabilidad (ningún elemento con `camera:type`); puede incluir cámaras no oficiales o retiradas | ODbL (atribución, share-alike en la base derivada) | Actualizable a diario con Overpass |
| Consell de Mallorca / Govern balear | Sería la fuente local de obras en carreteras `Ma-` | No hay API ni dataset localizado; BOIB y webs solo en texto | — | No existe (VERIFICAR) |
| Feeds por eventos del NAP (conos de pruebas deportivas, JSON/GitHub wiki) | Útil para cortes de Mallorca 312 / pruebas deportivas | Cobertura nacional, ficha remite a una wiki de GitHub; no probado | CC BY 4.0 (ficha) | VERIFICAR; opcional |

## Recomendación
1. **Incidencias**: descargar el XML DATEX II v3.7 del NAP con `Accept-Encoding: gzip` (~200 KB) al abrir la app y al iniciar ruta, filtrar en el móvil por bounding box de Mallorca (aprox. lat 39,2–40,1; lon 2,2–3,6) y por `lse:province = Illes Balears`, y mostrar solo lo vigente (`validityStatus=active`). Parser en streaming (StAX/XmlPullParser). Caché del último estado válido para uso offline con marca de antigüedad.
2. **Radares fijos**: base = XML de radares de la DGT (13 puntos) fusionado con OSM (nodos y relaciones `enforcement`), deduplicando por distancia (~50 m) y carretera; el resultado se **precompila en tiempo de build/descarga de mapas** a un JSON pequeño (<50 KB) guardado con los mapas.
3. **Radares móviles**: datos curados a mano desde el PDF de la DGT (24 tramos Mallorca) como "zona de radar móvil probable" por carretera y rango de PK; el PK→geometría requiere casar con OSM y es aproximado. Mostrarlos como aviso suave, no como radar confirmado.
4. **Radares de tramo**: hoy sin datos oficiales para Mallorca; activar la función con OSM/NAP si aparecen (VERIFICAR contra prensa que cita tramos nuevos).
5. Atribución visible en Ajustes: "Fuente: DGT (CC BY)" y "© OpenStreetMap contributors (ODbL)".

## Consecuencias
- Positivas: sin backend, ~200 KB por refresco, cobertura real de Mallorca verificada con datos de hoy, radares utilizables offline.
- Negativas / costes: parser DATEX II v3.7 específico (cambios de versión se anuncian con 2 meses según la ficha); la lista de radares puede estar desfasada (fichero 2025-12, PDF 2025-03); posible bloqueo del NAP si se abusa (no hay límite documentado).
- Seguimiento: VERIFICAR licencia/registro con nap@dgt.es (la ficha dice que registrarse solo da avisos por correo; no se exigió clave en las pruebas); VERIFICAR si el fichero de radares se regenera; añadir un test con un XML real de Mallorca como fixture (fichero ~20 situaciones); decidir si hace falta suscribirse a los avisos de cambio de versión del NAP.

## ¿Afecta al producto?
Sí → entradas en QUESTIONS.md: § Radares móviles (¿mostrar "zona probable" sin ubicación exacta?), § Radares de tramo (¿aviso de inicio/fin y velocidad media?), § Frecuencia de refresco (¿manual además de al abrir/iniciar ruta?), § Tipos de incidencia a avisar por voz (¿solo cortes/obras en la ruta o también tráfico lento?).

## Fuentes
- https://nap.dgt.es/en/dataset/incidencias-dgt-datex2-v3-7 (consultada 2026-10-28)
- https://nap.dgt.es/en/dataset/radares-fijos-dgt (2026-10-28)
- https://nap.dgt.es/en/organization/4a0c29d3-8a3e-484c-91d6-93e02f32e397 (lista de 19 datasets DGT, 2026-10-28)
- https://nap.dgt.es/en/dataset/incidencias-dgt (deprecado 12/01/2026; 2026-10-28)
- https://nap.dgt.es/en/dataset/tramos-de-elevado-riesgo-para-motocicletas y https://nap.dgt.es/en/dataset/localizacion-de-conos-correspondientes-a-pruebas-deportivas (2026-10-28)
- https://www.dgt.es/export/sites/web-DGT/.galleries/downloads/conoce-el-estado-del-trafico/informacion-e-incidencias-de-trafico/INFORME_CINEMOMETROS_WEB_OK_20250321_extra.pdf (descargado y parseado 2026-10-28)
- https://www.dgt.es/contenido/aviso-legal/ (2026-10-28; no trata reutilización)
- Descargas directas: https://nap.dgt.es/datex2/v3/dgt/SituationPublication/datex2_v37.xml y https://nap.dgt.es/datex2/dgt/PredefinedLocationsPublication/radares/content.xml (2026-10-28); Overpass API https://overpass-api.de/api/interpreter (consulta propia, 2026-10-28)
- No verificadas (prensa): https://www.mallorcadiario.com/carreteras-serra-tramuntana-al-limite-problematicas; https://www.mallorcadiario.com/cuatro-nuevos-radares-de-la-dgt-en-mallorca; https://www.mallorca.com/es/magazin/mallorca-servicio/trafico-en-mallorca (2026-10-28)
