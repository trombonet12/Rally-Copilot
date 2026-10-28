# Preguntas abiertas

> Propósito: dudas de producto, interfaz o alcance que decide Joan · Audiencia: Joan · Actualizado: 2026-10-28 · Relacionado: [ROADMAP.md](ROADMAP.md), [docs/DECISIONES.md](docs/DECISIONES.md)

Cada pregunta: contexto, opciones y **recomendación**. Mientras no hay respuesta se sigue con lo que no depende de
ella. Las preguntas se hacen en el chat de 4 en 4 hasta acabarlas; la respuesta pasa a DECISIONES.md y la pregunta
se marca como respondida. Ordenadas por el hito que bloquean.

Estado: **0 abiertas** · 18 respondidas (2026-10-28, en el chat). Las respuestas están en [docs/DECISIONES.md](docs/DECISIONES.md#respuestas-de-joan-a-questionsmd-2026-10-28).

## Bloquean H1–H2

### Alojamiento de los datos de Mallorca
- **Respondida 2026-10-28:** repo público `rally-copilot-data` con GitHub Actions mensual. Actualización: aviso + descarga con WiFi.
- **Contexto:** sin backend propio, la app necesita descargar teselas vectoriales, grafo de rutas (Valhalla), elevación y radares (decenas o centenas de MB). Hay que generarlos en algún sitio y servirlos estáticos. Ver [datos-mallorca.md](docs/investigacion/datos-mallorca.md).
- **Opciones:** A) repo **público** `trombonet12/rally-copilot-data` con GitHub Actions mensual y Releases (gratis, sin límite de ancho de banda, < 2 GiB por archivo); B) Cloudflare R2 (gratis hasta 10 GB, requiere cuenta y token); C) generar todo en el móvil desde fuentes públicas (lento, frágil y pesado).
- **Recomendación:** A. Solo contiene datos abiertos (OSM, IGN, DGT); el código de la app sigue en su repo. Actualización mensual automática + disparo manual.
- **Sub-preguntas:** ¿la app actualiza los datos sola cuando hay versión nueva (con WiFi) o solo al pulsar «Actualizar»? Recomendación: aviso + descarga con WiFi.

### Fuente del satélite y tamaño
- **Respondida 2026-10-28:** base z0–16 empaquetada en el repo de datos; detalle z17–19 del IGN. Por defecto 2 GB y solo WiFi.
- **Contexto:** la base satélite z0–16 de Mallorca son ≈25 000 teselas y ≈450 MB (medido). Puede descargarla cada móvil directamente del IGN (WMTS PNOA) o descargarse empaquetada de nuestro repo de datos. Ver [mapas-y-teselas.md](docs/investigacion/mapas-y-teselas.md).
- **Opciones:** A) el móvil la descarga del IGN tesela a tesela (sin coste de alojamiento; ~25 000 peticiones; condiciones de descarga masiva por confirmar con el CNIG); B) empaquetada en PMTiles en el repo de datos (una descarga, más rápida; somos redistribuidores con atribución CC BY).
- **Recomendación:** B para la base z0–16 y A para los detalles z17–19 (corredor y zonas manuales).
- **Ajustes por defecto propuestos:** límite de espacio 2 GB; «solo WiFi» **activado**. ¿De acuerdo?

### Límite de velocidad cuando OSM no lo tiene
- **Respondida 2026-10-28:** B, **no mostrar nada** si OSM no tiene el dato.
- **Contexto:** OSM tiene `maxspeed` en el 95 % de autovías pero solo en el 34 % de terciarias y el 8 % de caminos sin clasificar de Mallorca.
- **Opciones:** A) mostrar el límite genérico por tipo de vía (RGC: 90 en convencional, 50 urbano…) marcado como «estimado»; B) no mostrar nada si no hay dato.
- **Recomendación:** A, con un estilo visual distinto (p. ej. borde discontinuo).

### Búsqueda de destino
- **Respondida 2026-10-28:** B, lugares, calles y POIs **+ coordenadas y enlaces** (OSM/Google Maps).
- **Contexto:** la búsqueda offline sale de OSM. Mallorca tiene buena cobertura de pueblos, calas, playas y puntos de interés, pero los números de portal son parciales.
- **Opciones:** A) lugares + calles + POIs (+ portales donde existan); B) además coordenadas y pegar enlaces de mapas.
- **Recomendación:** A en H2; B más adelante si hace falta.

### Tipo de ruta
- **Respondida 2026-10-28:** B, más rápida + opción «Sinuosa».
- **Contexto:** Valhalla puede calcular la más rápida o penalizar autovías y favorecer carreteras secundarias.
- **Opciones:** A) solo la más rápida; B) más rápida + «ruta sinuosa» que prefiere carreteras con curvas (muy en línea con el producto).
- **Recomendación:** B, con la sinuosa como opción explícita al elegir destino.

### Indicaciones de giro por voz
- **Respondida 2026-10-28:** B, estilo copiloto; texto de Valhalla solo de respaldo.
- **Contexto:** Valhalla genera las instrucciones en español («En 300 metros, gire a la derecha hacia Ma-10»). El copiloto habla con estilo de rally.
- **Opciones:** A) leer el texto de Valhalla tal cual; B) frases propias en estilo copiloto («cruce, derecha, Ma-10») generadas a partir de la maniobra.
- **Recomendación:** B (coherente con el personaje y con clips pregenerados); A como respaldo para maniobras raras.

### Grabación automática e inicio del servicio
- **Respondida 2026-10-28:** A, empieza al pulsar Conducir, con notificación fija.
- **Contexto:** Play solo permite el servicio de ubicación en primer plano si arranca por una acción del usuario, salvo que se pida ubicación en segundo plano (revisión estricta y justificación en vídeo).
- **Opciones:** A) el tramo y la grabación empiezan al pulsar Conducir/Empezar, con una notificación persistente mientras dura; B) detección automática de movimiento con la app cerrada (requiere `ACCESS_BACKGROUND_LOCATION`).
- **Recomendación:** A. «Grabación automática» = grabar siempre que se conduce desde la app, sin tener que pulsar «Grabar».

### Inicio y fin de tramo
- **Respondida 2026-10-28:** pulsar Terminar · llegar al destino · descartar tramos < 1 km. **Sin** cierre automático por parada.
- **Contexto:** el prompt dice que un tramo empieza al iniciar la navegación y termina al llegar o parar.
- **Pregunta:** ¿qué es «parar»? Opciones: A) pulsar Terminar; B) además, detenerse más de N minutos (p. ej. 3) y que el tramo se cierre solo; C) además, tramos cortos (< 1 km) se descartan.
- **Recomendación:** A + B (3 min, configurable) + C.

### Móvil de pruebas
- **Respondida 2026-10-28:** LineageOS **sin GMS**.
- **Contexto:** Trombito apunta a LineageOS sin Google Play Services; la ubicación se diseña sin GMS por si acaso.
- **Pregunta:** ¿qué móvil y ROM usarás con Rally Copilot? ¿Tiene GMS? (Solo afecta a cómo probamos; el diseño ya funciona sin GMS.)

## Bloquean H3–H4

### Escala de grados
- **Respondida 2026-10-28:** calibrar con **tu velocidad real** (límite de la vía hasta tener datos; perfil global + por tramo). Se cantan **todos los grados**, también el 6; horquilla sin grado.
- **Contexto:** los grados salen de la velocidad de paso por curva (`v = √(μ·g·R)`) y se calibran con la velocidad máxima de la vía. Tabla propuesta: 1 < 45 km/h · 2 45–65 · 3 65–85 · 4 85–110 · 5 110–140 · 6 > 140, y horquilla aparte (sin grado). Ver [notas-de-rally.md](docs/investigacion/notas-de-rally.md).
- **Preguntas:** ¿calibrar contra el límite de la vía (recomendado por defecto) o contra tu velocidad real (en modo reconocimiento)? ¿Cantar los grados 5–6 u omitirlos? ¿«Horquilla» sin grado?
- **Recomendación:** límite de la vía por defecto y recalibrado con el modo reconocimiento; cantar 5 pero omitir 6 salvo que la curva sea larga o haya cresta; horquilla sin grado.

### Glosario y estilo de lectura
- **Respondida 2026-10-28:** «derecha cuatro» (dirección-grado); distancia después; avisos de precaución **generosos**.
- **Contexto:** borrador de glosario en [notas-de-rally.md](docs/investigacion/notas-de-rally.md): «derecha cuatro larga», «izquierda dos cierra», «horquilla izquierda», «y», «cincuenta», «cuidado», «no cortes», «rasante», «cambio de rasante», «bache», «estrecha», «puente». Se refinará con las transcripciones de §5.2.
- **Preguntas:** ¿orden dirección-grado («derecha cuatro») o grado-dirección («cuatro derecha»)? ¿Distancias después de la nota («derecha cuatro, cincuenta») o antes? ¿Avisos de precaución automáticos solo en casos claros (curva que cierra tras cresta, cruces) o más generosos?
- **Recomendación:** dirección-grado; distancia después; precaución solo en casos claros. Revisarlo contigo cuando haya transcripciones reales.

### Voces sintéticas y tamaño del APK
- **Respondida 2026-10-28:** dos voces dentro, con clips + TTS para lo imprevisto.
- **Contexto:** dos voces masculinas Piper `es_ES` (davefx, sharvard) de ≈21–36 MB cada una + clips pregenerados (≈2–3 MB por voz). Ver [voz-tts-y-clonado.md](docs/investigacion/voz-tts-y-clonado.md).
- **Preguntas:** ¿tamaño máximo de APK/AAB aceptable? ¿Las dos voces dentro del APK o una dentro y otra descargable? ¿Solo clips (frases cerradas) o también TTS para frases imprevistas (nombres de carreteras, avisos de la DGT)?
- **Recomendación:** dos voces dentro (≈80 MB total), clips + TTS para lo imprevisto.

### Licencia de la app y espeak-ng
- **Respondida 2026-10-28:** A, publicar como **GPL-3.0** (`LICENSE` añadido; repo público).
- **Contexto:** Piper y sherpa-onnx usan espeak-ng (GPL-3.0) para pasar texto a fonemas. Distribuir la app con él puede obligar a publicarla como GPL-3.0 (lo hacen apps como SherpaTTS).
- **Opciones:** A) publicar Rally Copilot como GPL-3.0 (el código ya está en GitHub); B) evitar espeak: voces que no lo usan (p. ej. Supertonic, VERIFICAR) o solo clips pregenerados en el PC, sin TTS en el móvil; C) consultar a un jurista.
- **Recomendación:** decidirlo antes de H4. Si no te importa abrir el código, A es lo más simple; si no, B con clips + una voz sin espeak.

## Bloquean H5–H8

### Cobertura de incidencias en Mallorca
- **Respondida 2026-10-28:** zonas de radar móvil en pantalla **y por voz**; avisar también de incidencias cercanas fuera de ruta; **sin** botón de refresco manual.
- **Contexto:** el NAP de la DGT sí cubre Mallorca (18 incidencias hoy). Radares fijos: 13 oficiales + 79 en OSM. Móviles: solo tramos probables del PDF de la DGT (24 en Mallorca). De tramo: ninguno oficial hoy. Ver [incidencias-dgt.md](docs/investigacion/incidencias-dgt.md).
- **Preguntas:** ¿avisar de radares móviles como «zona de radar móvil probable»? ¿Qué incidencias se dicen por voz: solo las que están en tu ruta, o también retenciones cercanas? ¿Botón de refresco manual además del automático?
- **Recomendación:** zonas probables solo en pantalla (sin voz); por voz solo lo que está en la ruta o en la vía actual; sí al refresco manual.

### Reconocimiento de voz offline
- **Respondida 2026-10-28:** ambas: botón (pantalla o volante) y palabra de activación opcional.
- **Contexto:** la edición por voz y el modo reconocimiento (H8) necesitan entender órdenes sin conexión (sherpa-onnx ASR con gramática del glosario, como Trombito).
- **Pregunta:** ¿cómo se activa la escucha? A) botón grande en pantalla / botón del volante del coche (si lo hay por Bluetooth); B) palabra de activación («Copiloto…»).
- **Recomendación:** A en H8; B como mejora posterior (más batería y falsos positivos).

### Dirección visual
- **Respondida 2026-10-28:** seguir así.
- **Contexto:** H0 fija modo oscuro carbón, latón como color principal, rojo de la marca como acento y titulares en negrita cursiva (captura de la pantalla de inicio en el emulador). El HUD tendrá cronómetro, barra de progreso y delta verde/rojo.
- **Pregunta:** ¿te encaja esta dirección o tienes referencias concretas (juego WRC concreto, retransmisión de Red Bull/WRC+, etc.) que quieras imitar en H10?
- **Recomendación:** seguir así y pasarte capturas en cada hito con UI.

## Bloquean H11–H12

### Voz de Moya
- **Respondida 2026-10-28:** **solo material existente** (sin sesión de grabación).
- **Contexto:** la mejor calidad sale de una sesión de lectura de ~300 frases del glosario (20–60 min de audio limpio). Los vídeos onboard sirven sobre todo para el vocabulario.
- **Preguntas:** ¿es posible una sesión de grabación con Luis Moya? ¿La autorización escrita cubre síntesis de su voz, distribución en Play y duración indefinida?
- **Recomendación:** pedir sesión de estudio y autorización escrita con esos tres puntos antes de H11.

### Play Console
- **Respondida 2026-10-28:** **más adelante**. Hoy se distribuye solo por GitHub Release + Obtainium; keystore nueva y propia de Rally Copilot.
- **Pregunta:** ¿usamos la misma cuenta de desarrollador que Sous Chef? ¿Quieres probadores en la prueba cerrada (hacen falta 12 durante 14 días para producción) o te basta con la interna?
- **Recomendación:** misma cuenta; interna para ti y cerrada solo si piensas publicar en producción.
