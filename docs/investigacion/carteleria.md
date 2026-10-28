# Placas de identificación de carreteras (colores y clasificación en Mallorca)

> Propósito: fijar qué color de placa lleva cada `ref` de OSM en el mapa (Ma-, AP-, A-, N-) y cómo deducir la categoría desde los datos · Audiencia: agente principal y usuario · Actualizado: 2026-10-28 · Relacionado: docs/DECISIONES.md § Librería de mapas y formato de teselas, ROADMAP.md § H2

## Contexto
- H2 pide «números de carretera en placas con los colores oficiales de su red». Solo Mallorca, offline: la placa se dibuja en el estilo MapLibre a partir de `ref` y `highway`, sin red.
- **Texto oficial no legible**: el Catálogo de señales (Anexo I del Reglamento General de Circulación, modificado por el RD 465/2025, BOE-A-2025-12199) define las «señales de identificación de carreteras» (S-400 a S-450, § 3.13 del Tomo I) solo con imágenes, y la Norma 8.1-IC (Orden FOM/534/2014, BOE-A-2014-3654) está publicada como 250 figuras escaneadas; el HTML del BOE no trae los colores en texto. El color legal se define como áreas del diagrama CIE 1931 (no como hex), según un editor de Waze que cita el Tomo I de 1992 (foro, no verificado). Por tanto **los hex de abajo no son oficiales**: son valores de uso común, marcados `VERIFICAR`.
- **Colores por red en España** (misma asignación en tres fuentes no oficiales: tabla de la wiki de OSM, lista de un editor de Waze que dice tomarla del Manual I de la DGT 2024 p. 120, y una lista de correo de OSM de 2007 que cita un BOE de 2003):

| Red | Fondo | Texto | Hex fondo (wiki OSM) | Hex fondo (editor Waze / «Manual DGT») |
|---|---|---|---|---|
| Itinerario europeo (E-) | Verde claro | Blanco | `#00cc00` | `#00A54F` |
| Autovía (A-) | Azul oscuro | Blanco | `#003399` | `#024DA1` |
| Autopista de peaje (AP-) | Azul | Blanco | sin valor en la wiki | `#05519F` |
| Nacional / Red del Estado (N-) | Rojo | Blanco | `#ff0000` | `#EE1C25` |
| Autonómica 1.er nivel | Naranja | Negro | `#ff9900` | `#F8931D` |
| Autonómica 2.º nivel | Marrón (wiki: «verde y marrón, la marrón no está normalizada»; lista de 2007: verde) | Blanco | `#996600` | `#63431A` |
| Autonómica 3.er nivel | Amarillo | Negro | `#ffff00` | `#FFDD00` |

  Un vídeo educativo de tuteorica.com sobre la señal S-420 describe la identificación de la N-432 con borde rojo, fondo blanco y letras rojas (otra presentación de la misma señal, no el escudo relleno típico de los mapas). Los bordes de cada red no los he podido verificar en texto oficial.
- **Mallorca**: no hay carreteras estatales ni autonómicas; todas las no municipales son del Consell de Mallorca y se llaman `Ma-…` (OSM wiki, discusión Ca:Illes Balears; ley 5/1990 de carreteras de Baleares, art. 5: redes primaria, secundaria, local y rural; primaria y secundaria de los consells insulars, local y rural de los ayuntamientos). El Pla Director Sectorial de Carreteres (PDSC) de Mallorca clasifica en **xarxa primària bàsica, primària complementària y secundària** (la discusión de OSM añade «no principal» como cuarta; Wikipedia solo trae tres; el PDF oficial `classificacio.pdf` del Consell no lo he podido abrir). Hay una revisión del PDSC en trámite (expediente 36E/2024, caib.es).
- **Clasificación por ref** (Wikipedia es/ca, «Red de carreteras de Mallorca», fuente secundaria que remite al PDSC; no verificada con el PDSC):
  - Primaria básica: Ma-1, Ma-11, Ma-13, Ma-15, Ma-19, Ma-20, Ma-30.
  - Primaria complementaria: Ma-1A, Ma-1B, Ma-1C, Ma-10, Ma-11A, Ma-11B, Ma-12, Ma-13A, Ma-14, Ma-15A a Ma-15F, Ma-19A.
  - Secundaria: códigos de 4 cifras Ma-1XXX a Ma-6XXX; la primera cifra es la zona (1 oeste, 2 norte/Raiguer, 3 centro, 4 este/sureste, 5 centro-sur, 6 sur). Ma-2141 (Sa Calobra) sería zona 2 (deducido del patrón; no aparece nombrada en el resumen que obtuve).
- **Color real de las placas de Mallorca: hay conflicto y no se ha resuelto**:
  - Discusión de OSM (2010 y 2016, usuarios): «Les primàries estan senyalitzades en color taronja, les secundàries en groc» (mapa del Consell). Mismo texto en una página de usuario. Sin fuente oficial.
  - Dibujos de Wikimedia Commons (hechos por usuarios, **no oficiales**), leídos del SVG: `ESP_Ma-13.svg` fondo `#00317f`, borde y letras blancos; `ESP_Ma-15.svg` fondo `#f97205`, borde y letras `#050006`; `ESP_Ma-1110.svg` (4 cifras, secundaria) fondo `#f97205` (naranja, contradice «secundarias en amarillo»); `ESP_Ma-10.svg` (primaria complementaria) fondo `#f4b70d` (amarillo, contradice «primarias en naranja»).
  - Un hilo de Waze (2026, foro) cita la Ma-1110 como ejemplo de identificador de 7 caracteres y una búsqueda web resumió que su placa fue amarilla y debería ser naranja; no verificado.
  - Según la discusión de OSM (2010), las autovías de Mallorca llevan cartel azul y las vías de doble calzada como Valldemossa o Manacor no son autovía legal y llevan cartel blanco.
- **Datos OSM medidos** (Overpass, 2026-10-28, bbox 39.2,2.3,40.0,3.5; `overpass-api.de` dio 502/429 a ratos y funcionó `overpass.openstreetmap.fr`):
  - Relaciones `route=road` con `ref=Ma-*`: solo 6 (Ma-1, Ma-13, Ma-19, Ma-20, Ma-2021, Ma-2100). **Ninguna tiene `network`**; tienen `symbol` apuntando a ficheros de Commons. Las relaciones no sirven para clasificar.
  - `highway` por ref (listas de refs distintas por clase, consulta exacta): `motorway` → Ma-1, Ma-13, Ma-19, Ma-20; `trunk` → Ma-1, Ma-15, Ma-19, Ma-30, Ma-1110; `primary` → Ma-1, Ma-1C, Ma-11, Ma-12, Ma-13, Ma-14, Ma-15, Ma-19 (y variantes A/C) y unas 50 refs de 4 cifras (Ma-1110, Ma-2020… Ma-6030); `secondary` → Ma-1A, Ma-1C, Ma-10, Ma-11, Ma-14, Ma-15A–E, Ma-19 y unas 60 refs de 4 cifras.
  - Recuentos exactos de vías (`out count`): `trunk` con «Ma-» = 474; `tertiary` con «Ma-» = 1730. El de `motorway` falló (502) y el resumen automático de la lista decía «unas 390-400» (no exacto); primary y secondary dieron 429.
  - Conclusión: **`highway` no distingue primaria de secundaria del Consell** (Ma-1110 aparece como `trunk`, `primary` y `secondary`; refs de 4 cifras en `primary`; Ma-15 en `trunk`, `primary` y `secondary`). El patrón de la `ref` sí.
  - Valores raros: `ref="Ma-11;Ma-10"` (varias refs en una vía), sufijos en minúscula (`Ma-2200a`, `Ma-4014a`).

## Alternativas
| Alternativa | Pros | Contras | Licencia | Madurez / versión verificada |
|---|---|---|---|---|
| A. Categoría por patrón de `ref` (+ `highway=motorway` → azul) | Offline, sin datos extra; coincide con la clasificación del PDSC según Wikipedia | Si el PDSC cambia (revisión 36E/2024) hay que actualizar la tabla; refs mal etiquetadas | OSM ODbL | Comprobado con Overpass 2026-10-28 |
| B. Categoría por `highway` de OSM | Sin lógica propia | Incoherente en Mallorca (mismo ref en 3-4 valores de `highway`) | ODbL | Descartada por los datos |
| C. Categoría por `network` de la relación | Estándar OSM | No hay `network` en las 6 relaciones Ma-* | ODbL | Descartada por los datos |
| D. Tabla `ref → categoría` generada en CI desde el PDSC oficial | Exacta | Hay que localizar y parsear `classificacio.pdf` del Consell (no abierto) | Datos del Consell (licencia VERIFICAR) | Por investigar |
| E. Color único para todas las `Ma-` | Trivial | Pierde la distinción visual | n/a | n/a |

## Recomendación
**Alternativa A**, con la tabla de colores de abajo, aplicada en el estilo MapLibre (filtro por regex de `ref`) o en la generación de teselas (campo `rc_cat` calculado en CI). Orden de reglas:
1. `highway` ∈ {`motorway`, `motorway_link`} con cualquier `ref` → **azul** (autopista/autovía).
2. Si la `ref` tiene varias (`;`), tomar la primera.
3. `ref` ~ `^AP-\d+` → azul AP; `^A-\d+` → azul; `^N-\d+` → rojo; `^E-\d+` → verde (no hay estatales en Mallorca; reglas por si aparece alguna).
4. `ref` ~ `^Ma-\d{1,2}[A-Za-z]?$` → **primaria** (naranja); incluye Ma-10 (ver duda).
5. `ref` ~ `^Ma-[1-6]\d{3}[A-Za-z]?$` → **secundaria** (amarillo).
6. Otra `ref` o sin `ref` → sin placa.

| Categoría | Ejemplos | Fondo | Texto | Borde |
|---|---|---|---|---|
| Autopista/autovía (`motorway`, AP-, A-) | Ma-13, Ma-19, Ma-20, Ma-1 (tramo autopista) | `#024DA1` | `#FFFFFF` | `#FFFFFF` |
| Nacional (N-) | (ninguna en Mallorca) | `#EE1C25` | `#FFFFFF` | `#FFFFFF` |
| Itinerario europeo (E-) | (ninguna) | `#00A54F` | `#FFFFFF` | `#FFFFFF` |
| Primaria Consell (`Ma-d`, `Ma-dd`, con sufijo) | Ma-10, Ma-15, Ma-12, Ma-11 | `#F8931D` | `#000000` | `#000000` |
| Secundaria Consell (`Ma-dddd`) | Ma-2141, Ma-1110, Ma-3010 | `#FFDD00` | `#000000` | `#000000` |

Los hex de azul, rojo, verde, naranja y amarillo son los del «Manual DGT 2024» según un editor de Waze (foro) y concuerdan en tono con la wiki de OSM; **VERIFICAR** contra el Catálogo. El borde blanco en fondos oscuros y negro en naranja/amarillo sale de los SVG de Commons (no oficiales). Si Joan confirma colores distintos para Mallorca (conflicto de Commons), solo cambia esta tabla.

## Consecuencias
- Positivas: sin dato externo ni red; una tabla de 5 filas y 4 regex; el estilo y las notas (H3/H4: «Ma-10») comparten la misma función de categoría.
- Negativas / costes: los colores de Mallorca descansan en fuentes no oficiales y contradictorias; la clasificación puede cambiar con la revisión del PDSC; refs raras (`Ma-11;Ma-10`, minúsculas) necesitan normalizar (sufijo en mayúscula, primera ref).
- Seguimiento: (a) Joan confirma en carretera o con fotos el color de Ma-10, Ma-15, Ma-2141 y Ma-13; (b) pedir al Consell o localizar el `classificacio.pdf` del PDSC y comparar con la lista de Wikipedia; (c) abrir el Catálogo (BOE-A-2025-12199, Tomo I § 3.13) en imagen para fijar bordes y hex; (d) reintentar Overpass para el recuento exacto de `motorway`, `primary` y `secondary`.

## ¿Afecta al producto?
Sí → QUESTIONS.md § Colores de placa de Mallorca: ¿las carreteras `Ma-` de dos cifras (Ma-10, Ma-15…) son naranjas y las de cuatro cifras (Ma-2141, Ma-1110…) amarillas en la realidad? Las fuentes se contradicen; recomendación: naranja/amarillo como en la tabla y que Joan corrija con una foto.

## Fuentes
- https://wiki.openstreetmap.org/wiki/ES:Key:ref:colour (tabla de colores España, 2026-10-28; comunitaria)
- https://wiki.openstreetmap.org/wiki/Talk:Ca:Illes_Balears (Mallorca: primarias naranja, secundarias amarillo; niveles del plan; 2026-10-28; comunitaria)
- https://lists.openstreetmap.org/pipermail/talk-es/2007-February/000038.html (colores por tipo, cita BOE 2003; 2026-10-28; comunitaria)
- https://www.waze.com/discuss/t/problemas-escudos-placas-identificadores-carreteras-espanolas/404920 (hex citados del Manual I DGT 2024; foro, no verificado; 2026-10-28)
- https://www.boe.es/buscar/act.php?id=BOE-A-2025-12199 (Catálogo, definición S-400 a S-450 sin colores en texto; 2026-10-28)
- https://www.boe.es/buscar/act.php?id=BOE-A-2014-3654 (Norma 8.1-IC, texto en imágenes; 2026-10-28)
- https://www.boe.es/eli/es-ib/l/1990/05/24/5/con/20211218 (Ley 5/1990 de carreteras de Baleares, art. 5; 2026-10-28)
- https://es.wikipedia.org/wiki/Red_de_carreteras_de_Mallorca ; https://ca.wikipedia.org/wiki/Xarxa_de_carreteres_de_Mallorca (listas por categoría; 2026-10-28)
- https://upload.wikimedia.org/wikipedia/commons/b/b1/ESP_Ma-13.svg ; .../2/26/ESP_Ma-10.svg ; .../2/2a/ESP_Ma-15.svg ; .../5/54/ESP_Ma-1110.svg (colores leídos del SVG, dibujos de usuario; 2026-10-28)
- https://www.caib.es/sites/serveiassessoramentambiental/ca/____36e2024_revisia_del_pla_director_sectorial_de_carreteres_de_mallorca (revisión del PDSC; 2026-10-28)
- Overpass API https://overpass-api.de/api/interpreter y https://overpass.openstreetmap.fr/api/interpreter (consultas propias, 2026-10-28)
