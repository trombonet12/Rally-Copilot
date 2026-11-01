# Preguntas abiertas

> Propósito: dudas de producto, interfaz o alcance que decide Joan · Audiencia: Joan · Actualizado: 2026-11-01 · Relacionado: [ROADMAP.md](ROADMAP.md), [docs/DECISIONES.md](docs/DECISIONES.md)

Cada pregunta: contexto, opciones y **recomendación**. Mientras no hay respuesta se sigue con lo que no depende de
ella. Las preguntas se hacen en el chat de 4 en 4 hasta acabarlas; la respuesta pasa a DECISIONES.md y la pregunta
se marca como respondida. Ordenadas por el hito que bloquean.

Estado: **0 abiertas** · todas respondidas en el chat (2026-10-28, 2026-10-31 y 2026-11-01). Las respuestas están en [docs/DECISIONES.md](docs/DECISIONES.md#respuestas-de-joan-a-questionsmd-2026-10-28).

## Respondidas al empezar H4 (2026-11-01, en el chat)
- **Modo inicial del copiloto:** Copiloto completo (notas, maniobras y alertas; delta y parciales cuando exista H6).
- **Modo «Solo alertas»:** también dice las maniobras de navegación.
- **Voz por defecto:** la elige Joan en el primer uso (diálogo davefx / sharvard con «Probar»); luego se cambia en Ajustes.
- **Distancia hasta la nota:** sí, en la tarjeta grande de la franja (ver abajo).

## Abiertas en H3 (todas respondidas)

### Franja de notas en modo libre
- **Respondida 2026-11-01:** **C**: la barra «¿A dónde?» se oculta mientras hay notas y aparece al tocar el mapa.
- **Contexto:** en guiado la franja va bajo el banner de maniobra; en libre no hay banner y va bajo la barra «¿A dónde?» (captura `docs/capturas/h3/05-franja-notas-libre.png`).
- **Opciones:** A) bajo la barra (como ahora); B) encima de la barra, arriba del todo; C) ocultar la barra mientras hay notas y mostrarla al tocar el mapa.
- **Recomendación:** A; la barra es pequeña y la franja queda a la misma altura que en guiado.

### Distancia hasta la nota en la franja
- **Respondida 2026-11-01:** **B**: la tarjeta grande muestra la distancia desde el coche hasta la nota.
- **Contexto:** la franja enseña «DERECHA 3 LARGA · 50» (50 = distancia a la siguiente nota, como la cantaría el copiloto) y resalta la nota cuando toca leerla; la distancia desde el coche hasta la nota solo la dice TalkBack.
- **Opciones:** A) no mostrarla (como un roadbook); B) mostrarla pequeña en la primera nota («en 120 m»).
- **Recomendación:** A, para no mezclar dos distancias en la misma tarjeta.

### Vocabulario: «compresión» y grado uno simplificado
- **Respondida 2026-11-01:** **A**: añadir «compresión» y «muy cerrada».
- **Contexto:** la revisión de copiloto propone avisar de las vaguadas fuertes como «compresión» (hoy no se dicen) y, en notas simplificadas, decir «muy cerrada» para el grado uno (hoy uno y dos son «cerrada»).
- **Opciones:** A) añadir las dos; B) solo «compresión»; C) solo «muy cerrada»; D) ninguna por ahora (se revisa con las transcripciones de H11).
- **Recomendación:** A.

### Notas en un tramo urbano
- **Respondida 2026-11-01:** **B**: con límite ≤ 30 km/h no se cantan curvas, solo maniobras y avisos.
- **Contexto:** en los primeros km de Sóller la Ma-10 cruza calles; tras filtrar, quedan cruces y stops solo cuando son de tu vía o la ruta gira. Las notas de curvas se siguen cantando a cualquier velocidad.
- **Opciones:** A) igual en ciudad que en carretera; B) no cantar curvas con límite ≤ 30 km/h (calles), solo maniobras y avisos.
- **Recomendación:** B, para no saturar en pueblos.

## Respondidas al empezar H3 (2026-10-31, en el chat)
- **Notas en texto (H3):** franja bajo el banner de maniobra con las 2–3 notas siguientes en estilo roadbook WRC (la primera grande). Alternativas: nota grande en lugar del banner, columna lateral.
- **Modo libre:** sí hay notas, prediciendo la vía (mismo ref o la más recta); más allá de un cruce dudoso no se canta. Alternativa: solo con destino.
- **Calibración:** perfil global con los recorridos grabados ya en H3 (sin datos, el límite de la vía); el perfil por tramo en H6.
- **Ajustes en H3:** anticipación (5 niveles) y notas simplificadas ya en H3; se pulen en H10.
- **Muerte del proceso:** al reabrir el mapa, botón «Seguir a <destino>» con el mismo destino y perfil (no arranca solo en segundo plano).
- **H11 sin material de voz:** saltar a H12 y dejar H11 pendiente.
- **Cierre de cada hito:** parar solo para confirmar push + versión menor nueva (H3 → 0.3…) y presentar dudas; después seguir con el siguiente hito.

## Abiertas en H2

### Arranque del modo libre (sin destino)
- **Respondida 2026-10-31:** **B**: arranca solo al abrir el mapa y se para al salir.
- **Contexto:** el modo libre arranca el servicio en primer plano (notificación fija) y graba el recorrido. «Conducir» abre el mapa.
- **Opciones:** A) un botón con icono junto a la barra «¿A dónde?» lo arranca y «Terminar» lo para; B) arranca solo al abrir el mapa con permiso de ubicación y se para al salir; C) arranca solo al superar ~15 km/h.
- **Recomendación:** A (sigo con A mientras no respondas): no deja la notificación ni graba sin que lo pidas.

### Enlaces cortos de Google Maps (`maps.app.goo.gl`)
- **Respondida 2026-10-31:** **A**: con red se resuelve la redirección; sin red, aviso.
- **Contexto:** no llevan coordenadas; hay que seguir la redirección por internet. La app es offline por defecto.
- **Opciones:** A) con red se resuelve la redirección (una petición a Google) y sin red se avisa; B) nunca se resuelven: se pide pegar el enlace completo o las coordenadas.
- **Recomendación:** B en H2 (sin peticiones a terceros); A más adelante si lo usas a menudo. Sigo con B.

### Colores de las placas Ma-
- **Respondida 2026-10-31:** **A**, por número y no por tipo de vía: Ma- de 1-2 cifras naranja (también las autovías Ma-19, Ma-13…), el resto de Ma- amarillo; también en el panel bajo la velocidad.
- **Contexto:** las fuentes se contradicen: unas dan las Ma- de dos cifras (Ma-10, Ma-13…) en naranja y las de cuatro (Ma-2141) en amarillo; algunos dibujos de Commons lo dan al revés. Ver [carteleria.md](docs/investigacion/carteleria.md).
- **Opciones:** A) dos cifras naranja, cuatro amarillo; B) al revés; C) mándame una foto de una placa de cada.
- **Recomendación:** A (sigo con A mientras no respondas).

### Relieve en el paquete base
- **Respondida 2026-10-31:** **A**: en el paquete base.
- **Contexto:** el relieve y las curvas de nivel suman unos 30–80 MB (estimado) sobre los ~400 MB actuales.
- **Opciones:** A) en el paquete base; B) descarga opcional aparte en Mapas offline.
- **Recomendación:** A, por sencillez (sigo con A mientras no respondas).

## Respondidas en H1

### Acceso al mapa antes de H2
- **Respondida 2026-10-28:** A, Conducir abre el mapa.
- **Contexto:** en H1 hay mapa, posición, velocidad y límite, pero aún no navegación ni tramos (H2/H6). Hace falta una puerta de entrada provisional al mapa desde el menú de inicio.
- **Opciones:** A) «Conducir» abre el mapa en modo libre (lo que será el modo sin destino de H2); B) entrada aparte «Mapa» que desaparece en H10; C) el mapa como pantalla de inicio y el menú encima.
- **Recomendación:** A. Se sigue con A mientras no respondas (es reversible); «Mapas offline» y «Ajustes» también se activan.

### Primera descarga de mapas
- **Respondida 2026-10-28:** A, la inicia el usuario.
- **Contexto:** hoy la primera descarga (~400 MB) la inicia el usuario desde Mapas offline; las actualizaciones posteriores se comprueban solas al abrir la app y se bajan con WiFi.
- **Opciones:** A) primera descarga manual (como ahora); B) empezar sola con WiFi la primera vez que se abre la app.
- **Recomendación:** A: 400 MB es mucho para hacerlo sin preguntar, y la tarjeta del mapa ya lleva a Mapas offline.

### Orientación del mapa al conducir
- **Respondida 2026-10-28:** C, botón con rumbo arriba por defecto.
- **Contexto:** el seguimiento usa `TRACKING_GPS` de MapLibre, que gira el mapa con el rumbo (rumbo arriba). En H2 llega la navegación.
- **Opciones:** A) rumbo arriba (como un navegador); B) norte arriba; C) botón para alternar (por defecto rumbo arriba).
- **Recomendación:** C.

### Rótulo de la vía bajo la velocidad y aviso de datos móviles
- **Respondida 2026-10-28:** mantener el rótulo y confirmar con diálogo.
- **Contexto:** el panel muestra la referencia o el nombre de la vía («Ma-2141») bajo la velocidad (no estaba pedido). Al desactivar «Solo con WiFi» no se pide confirmación.
- **Opciones:** rótulo: mantener / quitar. Datos móviles: confirmar con un diálogo («puede gastar ~400 MB») / no confirmar.
- **Recomendación:** mantener el rótulo (ayuda a saber de qué vía es el límite) y confirmar con diálogo.

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
