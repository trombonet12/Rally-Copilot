# Auditoría del menú y de las pantallas (2026-11d)

> Propósito: cómo se llega a cada pantalla, qué se ve hoy y qué la haría más amena, visual y bonita, con un plan priorizado · Audiencia: Joan y agentes · Actualizado: 2026-11-23 · Relacionado: [2026-11c-perfil.md](2026-11c-perfil.md)

Auditoría de solo lectura (Fable) de la navegación y de todas las pantallas de `ui/**`, del tema (`ui/theme/`) y de los componentes comunes (`ui/comun/`), contrastada con las capturas de `docs/capturas/`. Petición de Joan (2026-11-23): «una auditoría del menú y de cómo se usan todas las pantallas de la aplicación para ver si podemos hacer algo más ameno, más visual y más bonito».

Rutas abreviadas: `ui/…` = `app/src/main/kotlin/app/trombito/rallycopilot/ui/…`; `strings_*.xml` = `app/src/main/res/values/`.

**Fuera de esta auditoría** (ya en curso o auditado): Mis tramos y Perfil ([2026-11c-perfil.md](2026-11c-perfil.md), H30–H32), Ajustes en una columna (H29) y el HUD de conducción (H22). Solo se citan para decir cómo encajan.

**Límites de lo comprobado**
- Las capturas son de `h14`, `h15` y `h16-h21` (v1.2–v1.5). Son anteriores a H22 y a los cambios de H26–H29. Donde una captura puede estar desfasada, se dice.
- No se ha ejecutado la app. Los toques se han contado leyendo `ui/AppNavHost.kt`.
- Ajustes estaba a medio cambiar: sus toques se cuentan con el código del árbol en ese momento.

## 1. Mapa del flujo actual

### 1.1 Cómo se llega a cada pantalla

Toques contados desde Inicio. La navegación es una pila simple: no hay barra inferior ni cajón (`ui/AppNavHost.kt:91-93`). Inicio tiene cuatro entradas (`ui/inicio/EntradaInicio.kt:10-17`, `ui/AppNavHost.kt:244-254`).

| Pantalla | Camino | Toques |
|---|---|---|
| Mapa (Conducir) | Inicio › CONDUCIR | 1 |
| Ir a casa | Conducir › chip «Ir a casa» › IR | 3 |
| Ruta a un destino | Conducir › «¿A dónde?» › escribir › resultado › IR | 4 + teclado |
| Hoja del copiloto | Conducir › botón del casco | 2 |
| Capas del mapa | Conducir › botón de capas | 2 |
| Mis tramos | Inicio › Mis tramos | 1 |
| Detalle de tramo | Mis tramos › fila | 2 |
| Análisis de pasada | Detalle › pasada (también desde la tarjeta de meta y desde Perfil) | 3 |
| Editar notas | Detalle › «Editar notas» | 3 |
| Reconocer | Detalle › «Reconocer» (salta al mapa) | 3 |
| Mis carreteras | Mis tramos › icono de curvas | 2 |
| Dibujar ruta / Importar | Mis tramos › icono de la barra | 2 |
| Perfil del piloto | Inicio › Perfil | 1 |
| Ajustes › sección | Inicio › Ajustes › sección | 2 |
| Mapas offline | Ajustes › Datos › Mapas offline (también desde el mapa sin datos) | 3 |
| Descargar zona | Mapas offline › «Descargar zona» | 4 |
| Qué significan las notas | Ajustes › Ayuda › fila | 3 |
| Qué le puedes decir | Ajustes › Ayuda › fila | 3 |
| Tutorial otra vez | Ajustes › Ayuda › fila | 3 |
| Acerca de | Ajustes › Ayuda › fila | 3 |

### 1.2 Qué es confuso o redundante

1. **Inicio es solo un lanzador.** No enseña ningún estado (mapas, voz, GPS, temporada) y todo lo que no es conducir obliga a volver a él. Lo único vivo es la línea «Última salida» (`ui/inicio/InicioScreen.kt:238-271`).
2. **«Última salida» parece un enlace y no lo es.** Enseña la miniatura del tramo y sus tiempos, pero no es pulsable. El estado ya lleva el `tramoId` (`ui/inicio/InicioUiState.kt:7`) y nadie lo usa para navegar (`ui/inicio/InicioRoute.kt:16-17`).
3. **Del mapa solo se sale con la ✕ de la hoja o con atrás** (`ui/AppNavHost.kt:52`, `:72`; `ui/mapa/HojaConduccion.kt:131-135`). Con crono o grabación pide un segundo toque. Es coherente, pero la ✕ significa «cerrar el mapa» en libre y «terminar la ruta» guiando, con el mismo dibujo.
4. **La ayuda de las notas está lejos de donde se ven las notas.** Solo se llega por Ajustes › Ayuda (`ui/AppNavHost.kt:197`). Desde el mapa, la hoja del copiloto o el editor de notas no hay acceso.
5. **«Reconocer» está escondido** en el detalle de cada tramo (`ui/mistramos/AccionesNotasTramo.kt:44-50`; lo dice el propio comentario de `ui/inicio/EntradaInicio.kt:6-8`). Al pulsarlo se salta al mapa sin pantalla intermedia (`ui/AppNavHost.kt:134-137`): no se anuncia qué va a pasar salvo que se haya leído el diálogo del icono de información (`ui/mistramos/AccionesNotasTramo.kt:58-67`).
6. **Mis tramos tiene tres iconos seguidos sin rótulo** en la barra (carreteras, importar, dibujar: `ui/mistramos/MisTramosScreen.kt:102-105`, `ui/mistramos/ImportarUi.kt:64-72`). Cumple la regla de «iconos, no rótulos», pero son acciones poco frecuentes y no se distinguen de un vistazo. La fusión de «Mis carreteras» ya está decidida en H30.
7. **No hay transiciones propias entre pantallas.** El `NavHost` no define animaciones (`ui/AppNavHost.kt:91`) y entrar al mapa, a un tramo o a Ajustes se ve igual. La única entrada animada de la app es la de Inicio (`ui/inicio/InicioScreen.kt:127-148`).
8. **El atajo «Repetir último tramo» existe solo fuera de la app** (icono y widget: `ui/AppNavHost.kt:53-55`, `:226-240`). Dentro, repetir un tramo exige Mis tramos › tramo › y no hay un «ir a la salida».

## 2. Pantalla a pantalla

### 2.1 Inicio (`ui/inicio/InicioScreen.kt`)

Capturas: `docs/capturas/h15/01-inicio-oscuro-vertical.png`, `h15/26-tras-terminar-oscuro-vertical.png`, `h14/01-inicio-oscuro-horizontal.png`, `h14/01-inicio-claro-vertical.png`.

**Qué pasa hoy**
- En vertical el contenido va centrado (`ui/inicio/InicioScreen.kt:112`) y ocupa la mitad de la pantalla: queda vacío el cuarto de arriba y el quinto de abajo.
- La mascota es una imagen fija de 120 dp (`:173-177`, `:69`). Es el único sitio, con Acerca de, donde aparece Trombito (`ui/ajustes/AcercaDeScreen.kt:128`).
- CONDUCIR es una tarjeta redondeada normal con borde ámbar (`:202-234`). No lleva ningún rasgo de retransmisión (sesgo, bisel, franja), aunque el componente lo ofrece: `TarjetaWrc` tiene `biselada` y `acentoRojo` (`ui/comun/TarjetaWrc.kt:80-81`) y ninguna pantalla usa el bisel.
- «Última salida» es una línea gris de 14 sp con tres datos separados por puntos (`:249-270`, `strings_inicio.xml:8`). Los tiempos van en texto normal, no con la cifra cursiva del crono.
- Las tres tarjetas de abajo son iguales y mudas: icono y nombre (`:283-314`).

**Qué la haría más amena y visual** (solo interfaz)
- «Última salida» como tarjeta pulsable que abre el tramo: miniatura más grande, nombre en una línea y el tiempo con la cifra WRC. Cambio para Joan: de Inicio a su último tramo en un toque en vez de tres.
- CONDUCIR con forma de rótulo de retransmisión (el bisel que ya existe) y la franja roja como acento, para que el menú se parezca al HUD.
- Subir la cabecera y repartir el espacio: mascota arriba, CONDUCIR en el centro, menú abajo al alcance del pulgar.
- Trombito con un gesto corto al entrar (parpadeo o inclinación del casco, una vez, sin bucle). Es una imagen: el movimiento más barato es de escala y giro sobre el recurso actual.
- Estado vacío del primer uso: hoy es una frase gris (`:240-247`). Puede ser la misma tarjeta de «Última salida» con una silueta de tramo punteada y «Tu primer tramo saldrá aquí».

**Lo que toca producto** se recoge en §4 (P1 y P2).

### 2.2 Mapa en reposo (`ui/mapa/MapaScreen.kt`, `ui/mapa/HojaConduccion.kt`, `ui/navegacion/BarraDestino.kt`)

Capturas: `h15/07-mapa-parado-oscuro-vertical.png`, `h15/08-mapa-parado-botones-oscuro-vertical.png`, `h14/23-buscar-soller-resultados-oscuro-vertical.png`, `h14/20-mapa-toque-largo-tarjeta-oscuro-vertical.png`. Anteriores a H22 y a H26; la columna de botones y la hoja pueden haber cambiado.

**Qué pasa hoy**
- **Carga**: indicador circular de Material suelto y «Preparando el mapa…» (`ui/mapa/MapaScreen.kt:569-582`). No usa `CargandoWrc`, que es el mismo patrón ya hecho (`ui/comun/CargandoWrc.kt:53`).
- **Sin mapas** (primer uso sin siembra o mapas dañados): una tarjeta de texto con un botón (`ui/mapa/MapaScreen.kt:585-617`). Es la primera pantalla que vería alguien sin datos y no tiene icono ni ilustración.
- **Permiso de ubicación**: otra tarjeta de texto, con superficie del tema y no con el carbón del resto de tarjetas del mapa (`ui/mapa/MapaScreen.kt:793-836`).
- **Resultados de búsqueda**: todas las filas llevan el mismo icono de chincheta (`ui/navegacion/BarraDestino.kt:324`). La categoría («pueblo», «aldea») va solo como texto gris (`:281-293`). Las acciones son rótulos de texto, «Ir» y «+ Parada» (`:343-358`), contra la preferencia de iconos.
- **Sin resultados**: usa bien el estado vacío común (`:252-257`).
- **Punto elegido con pulsación larga**: enseña siempre las coordenadas bajo el nombre (`ui/navegacion/TarjetaPunto.kt:108-113`). Con nombre de calle, las coordenadas no aportan.
- **«Ir a casa»** es el único acceso rápido bajo la barra (`ui/mapa/MapaScreen.kt:696-708`). No hay destinos recientes.

**Qué la haría más amena y visual**
- Carga y «sin mapas» con el mismo estado ilustrado que el resto (ver §3.2, pieza 3). Para Joan: la primera impresión deja de parecer un error.
- Icono por categoría en los resultados (pueblo, playa, puerto de montaña, gasolinera…) usando la categoría que ya trae cada resultado. Se reconoce el tipo de sitio sin leer.
- «Ir» y «+ Parada» como botones de icono de 48 dp con su descripción para TalkBack, que ya existe (`ui/navegacion/BarraDestino.kt:346`, `:354`).
- Ocultar las coordenadas cuando hay nombre.
- Destinos recientes bajo la barra: es producto (§4, P4).

### 2.3 Vista previa de rutas (`ui/navegacion/VistaPreviaRutas.kt`)

Capturas: `h15/15-vista-previa-ruta-soller-oscuro-vertical.png`, `h16-h21/03-ruta-tres-chips.png`. El «Misma ruta» de la segunda ya se está quitando en H26.

**Qué pasa hoy**
- Tres tarjetas con icono, nombre, tiempo, km y «km de curvas», todo en texto del mismo peso salvo el tiempo (`:229-243`, `:257-282`). Lo que diferencia una ruta de otra, las curvas, es la línea más pequeña y gris (`:297-305`).
- El panel es una superficie del tema al 94 % (`:73-75`), no la tarjeta carbón del mapa. En tema claro queda clara sobre un mapa claro, mientras el HUD sigue oscuro.
- Mientras calcula, cada tarjeta enseña un circulito de Material (`:253-255`).
- Sin ruta posible: icono, frase y «Probar otra vez» (`:160-185`). Correcto, pero no dice qué hacer si faltan mapas.
- En el mapa solo se pinta la ruta elegida (por las capturas); las otras no se ven hasta tocarlas. **Por comprobar** en `ui/mapa/RutaEnMapa.kt`.

**Qué la haría más amena y visual**
- Una barra de proporción de curvas en cada tarjeta (tramo ámbar sobre gris: «19,8 de 32,9 km»). El dato ya existe (`curvasM` y `distanciaM`, `:266`, `:280`). Para Joan: ve cuál es la revirada sin leer cifras.
- Tiempo con la cifra WRC y hora de llegada debajo (se calcula con el tiempo que ya hay).
- Esqueleto con brillo mientras calcula, en vez del circulito (`ui/comun/CargandoWrc.kt:83-103`).
- Las alternativas en gris tenue en el mapa y la elegida en rojo: tocar la línea elige la ruta. Si hoy no se pintan, es un cambio M.
- Con H28 (tramos conocidos en ruta), una fila «Pasa por 2 tramos tuyos» encaja aquí.

### 2.4 Llegada, meta y fin de viaje

Capturas: `h14/33-llegada-casa-oscuro-vertical.png`, `h14/31-mapa-crono-tramo-conocido-delta-oscuro-vertical.png`.

- La **tarjeta de meta** es la pieza más WRC de la app: paralelogramo, borde latón, cifra tabular y «NUEVO MEJOR» (`ui/tramos/TarjetaMeta.kt:80`, `:104-105`, `:238-244`). Es la referencia de estilo para el resto.
- La **tarjeta de llegada** es más plana: rectángulo de radio 14 con bandera y texto (`ui/navegacion/guiado/TarjetaLlegada.kt:43`, `:63-71`). No comparte forma con la de meta.
- Propuesta: la llegada con la misma forma y cifras que la meta. La pantalla de resumen de viaje de H28 es el sitio natural para la celebración (Trombito, bandera a cuadros), con el coche ya parado.

### 2.5 Hoja del copiloto y capas

Capturas: `h15/19-mapa-hoja-copiloto-oscuro-vertical.png`, `h15/22-capas-oscuro-vertical.png`.

- La hoja del copiloto ya enseña la vista previa de la nota y deja probarla (`ui/mapa/AjustesRapidos.kt:123-130`). Encaja con la regla de «ver en vivo».
- Es larga: cinco secciones con scroll dentro del 60 % de la pantalla (`:57`, `:87-103`, `:160-174`). En la captura la tercera sección ya queda cortada.
- Propuesta: agrupar en dos pestañas de icono («Qué canta» y «Cómo suena») o plegar lo menos usado (salida manual, intercom). Decide Joan cuáles son «lo que se toca conduciendo» (§4, P5).
- Capas: panel de iconos sin rótulo, correcto según la preferencia de Joan. Sin hallazgos nuevos.

### 2.6 Detalle de tramo, editor y dibujo

Capturas: `h14/04-detalle-tramo-oscuro-vertical.png`, `h14/05-editar-tramo-oscuro-vertical.png`. El contenido del detalle se rehace en H30–H31; aquí solo forma y flujo.

**Editor de notas** (`ui/mistramos/EditarTramoScreen.kt`)
- Fondo plano, sin la trama del resto (`:147`).
- La ayuda es un diálogo de texto (`:196-209`) y debajo del mapa hay otra línea de explicación en letra de 11 sp (`:331-336`).
- La leyenda son chips hechos a mano (`:347-369`), distintos de `ChipIconoWrc`.
- Las filas de nota son una fila propia con fondo y borde (`:446-455`), no `TarjetaWrc`.
- «Este tramo ya no existe» es un texto suelto (`:163-169`), sin el estado vacío común.
- Propuesta: la fila de nota con el mismo icono de curva que el carrete de conducción (`ui/navegacion/notas/IconosNota.kt`), no solo la píldora «D4». Para Joan: edita viendo lo mismo que ve conduciendo.

**Dibujar ruta y Descargar zona** (`ui/mistramos/DibujarScreen.kt`, `ui/mapasoffline/ZonaDetalleScreen.kt`)
- Son la misma pantalla dos veces: barra translúcida arriba, mapa y panel abajo (`DibujarScreen.kt:59-80`, `ZonaDetalleScreen.kt:61-81`). Cada una define su propio fondo al 92 %.
- El panel no es la hoja común (`ui/comun/HojaInferiorWrc.kt:32`).
- La instrucción es una línea gris pequeña (`strings_mistramos.xml:42`, `strings_mapa.xml:84-85`).
- Zona usa chips de Material (`ZonaDetalleScreen.kt:111`) y Dibujar un circulito suelto (`DibujarScreen.kt:87-90`).
- Propuesta: una sola plantilla «mapa con panel» (§3.2, pieza 6) y un contador grande («3 puntos · 12,4 km», «1 240 teselas · 86 MB») como dato protagonista.

### 2.7 Análisis de pasada (`ui/analisis/`)

Capturas: `h14/06-analisis-pasada-oscuro-vertical.png`, `h14/06b-analisis-pasada-scroll1-oscuro-vertical.png`. El contenido ya se trató en [2026-11c-perfil.md](2026-11c-perfil.md) § 1.6.

- Usa el estado vacío antiguo, con otro tamaño de icono y título en mayúsculas (`ui/analisis/AnalisisScreen.kt:88-100`, `ui/comun/EstadoVacio.kt:21`, `:49`). El resto de pantallas usa `EstadoVacioWrc`.
- Chips de Material para comparar (`ui/analisis/CabeceraAnalisis.kt:196-204`) y fondo plano (`ui/analisis/AnalisisScreen.kt:67`).
- Las cifras de la tabla de sectores van en redonda normal, mientras el total de arriba va en cursiva WRC. **Por comprobar** en `ui/analisis/TablaParciales.kt`; se ve en la captura `06b`.

### 2.8 Reconocimiento (`ui/reconocimiento/ResumenReconocimiento.kt`)

- El resumen de la pasada es una tarjeta carbón con barra roja y una lista de frases («km 1,2: …») (`:60-71`, `:111-125`, `:128-143`).
- Propuesta: cada cambio como fila con el icono de la nota de antes y el de después, y una flecha entre ambos. Para Joan: revisa de un vistazo, parado.
- Falta una entrada clara al modo: ver §1.2 punto 5 y §4, P3.

### 2.9 Mapas offline (`ui/mapasoffline/`)

Capturas: `h15/33-mapas-offline-oscuro-vertical.png`, `h1/01-descarga.png`.

- Cinco tarjetas apiladas con el mismo peso (`ui/mapasoffline/MapasOfflineScreen.kt:140-149`).
- Dos botones ámbar a todo el ancho, uno tras otro («Descargar satélite», «Descargar zona»): no se sabe cuál es el principal.
- El uso de espacio es una barra fina con «133 MB de 4 GB» (`ui/mapasoffline/TarjetasMapas.kt:60`, `:100`).
- El selector de límite usa el botón segmentado de Material con sus colores por defecto (`ui/mapasoffline/TarjetasMapas.kt:259`): el elegido sale azul oscuro (`secondaryContainer`, `ui/theme/Color.kt:20`), el único azul de la app.
- Título con paréntesis: «Más datos (se descargan al usarlos)».
- Propuesta:
  - Cabecera con la silueta de Mallorca y lo instalado como capas con marca (mapa, relieve, satélite, detalle, voz).
  - Un solo botón principal por estado.
  - El límite de espacio con el chip común.
  - Para Joan: ve qué tiene y qué falta sin leer cinco tarjetas.

### 2.10 Ayuda, tutorial y Acerca de

Capturas: `h14/11-ayuda-notas-oscuro-vertical.png`, `h14/12-ayuda-voz-oscuro-vertical.png`, `h14/00-bienvenida-*.png`, `h14/09-ajustes-acerca-de-oscuro-vertical.png`.

- **Qué significan las notas** es de lo mejor resuelto: iconos reales, cifra WRC y chips. Solo enseña curvas a derechas (`ui/ajustes/AyudaNotasScreen.kt:57-62`).
- **Qué le puedes decir** es una lista plana de más de treinta frases, todas con el mismo micrófono delante (`ui/ajustes/AyudaVozScreen.kt:64-83`, `:104-114`). El icono repetido no informa.
  - Propuesta: frases como chips en dos columnas, con el icono de lo que hacen (el tutorial ya lo hace así: `ui/tutorial/IlustracionVoz.kt:69`), y los pares «Sin cruces / Con cruces» en una sola fila.
- **Bienvenida**: seis páginas con ilustración animada propia y buen ritmo (`ui/tutorial/BienvenidaScreen.kt:58-66`, `:111-115`).
  - Trombito no sale en ninguna. Es el copiloto y no se presenta.
  - La página de permisos son tres tarjetas con tres botones «Conceder» iguales (captura `00-bienvenida-6`).
- **Acerca de**: el nombre va en negrita recta (`ui/ajustes/AcercaDeScreen.kt:132-136`) y en Inicio en cursiva Black (`ui/inicio/InicioScreen.kt:178-184`): el logotipo cambia de una pantalla a otra. Diez filas de licencias plegables seguidas (`:59-71`).
- **Elegir voz** en el primer uso es un diálogo de Material estándar (`ui/copiloto/DialogoVozCopiloto.kt:64-100`). Es el momento en que el copiloto «habla por primera vez» y no tiene ninguna presentación.

### 2.11 Cómo encaja con lo excluido

- **Ajustes (H29)**: la portada en una columna debería usar la misma fila con icono, título, valor y flecha que ya usa Ayuda (`ui/ajustes/AjustesAyuda.kt:27-58`), y la misma cabecera que el resto.
- **Perfil y Mis tramos (H30–H32)**: las tarjetas de conclusión del tablero son el mejor candidato para estrenar la «tarjeta de dato» común (§3.2, pieza 2). Conviene crearla antes de H30 para no hacerla dos veces.
- **HUD (H22)**: es la referencia de estilo. La propuesta de este documento es llevar su lenguaje (sesgo, latón, cifra tabular) a los menús, no tocarlo.

## 3. Sistema visual

### 3.1 Inconsistencias

1. **Dos lenguajes de forma.**
   - El HUD usa paralelogramos y latón (`ui/maniobras/BannerManiobra.kt:70`, `ui/tramos/HudCrono.kt:164`, `ui/tramos/TarjetaMeta.kt:80`).
   - Los menús usan tarjetas redondeadas de Material sin ningún rasgo propio.
   - El bisel pensado para unirlos existe y no se usa: `FormaBiselada` solo se nombra en su definición y en `ui/comun/TarjetaWrc.kt:88`.
2. **Fondo.** La trama de carbono (`ui/comun/FondoWrc.kt:38`) está en Inicio, Ajustes, Ayuda, Acerca de, Mapas offline y Bienvenida. Mis tramos, Detalle, Editor, Análisis, Perfil y Mis carreteras usan el fondo plano (`ui/mistramos/MisTramosScreen.kt:100`, `ui/mistramos/DetalleTramoScreen.kt:131`, `ui/mistramos/EditarTramoScreen.kt:147`, `ui/analisis/AnalisisScreen.kt:67`, `ui/perfil/PerfilScreen.kt:70`, `ui/miscarreteras/MisCarreterasScreen.kt:78`). Al pasar de Ajustes a Mis tramos la app cambia de textura.
3. **Tarjetas sobre el mapa: cuatro recetas.**
   - Carbón fijo con `TarjetaWrc` variante MAPA (`ui/comun/TarjetaWrc.kt:55-64`).
   - Superficie del tema al 94 % (`ui/navegacion/VistaPreviaRutas.kt:75`, `ui/navegacion/TarjetaPunto.kt:74`, `ui/mapa/MapaScreen.kt:800`).
   - Superficie al 92 % (`ui/mistramos/DibujarScreen.kt:77`, `ui/mapasoffline/ZonaDetalleScreen.kt:78`).
   - Carbón con borde a mano (`ui/navegacion/guiado/TarjetaLlegada.kt:68-69`, `ui/alertas/TarjetaIncidencia.kt:59-60`).
   - En tema claro, unas quedan claras y otras oscuras en la misma pantalla.
4. **Dos estados vacíos.** `EstadoVacio` (icono 72 dp, título en mayúsculas: `ui/comun/EstadoVacio.kt:21`, `:49`) y `EstadoVacioWrc` (48 dp tenue, 18 sp: `ui/comun/EstadoVacioWrc.kt:37`). Ninguno admite ilustración ni mascota. Además hay vacíos y errores como texto suelto (`ui/mistramos/EditarTramoScreen.kt:163-169`, `ui/analisis/AnalisisScreen.kt:187-194`, `ui/mistramos/DetalleTramoScreen.kt:266-272`).
5. **Cinco tipos de chip o selector.**
   - `ChipIconoWrc` (`ui/comun/ChipIconoWrc.kt:33`).
   - `FilterChip` de Material (`ui/edicion/EditorNota.kt:191`, `ui/analisis/CabeceraAnalisis.kt:196`, `ui/mapasoffline/ZonaDetalleScreen.kt:111`).
   - Botón segmentado (`ui/mapasoffline/TarjetasMapas.kt:259`).
   - Pestañas de Material (`ui/mistramos/PestanasTramos.kt:25`).
   - Chips hechos a mano (`ui/mistramos/EditarTramoScreen.kt:347`).
6. **Radios.** Hay dos tablas con los mismos valores (`ui/theme/Wrc.kt:30-40` y `ui/theme/Theme.kt:29-36`) y, además, radios sueltos de 6, 8, 10, 12 y 14 dp (`ui/navegacion/ParadasUi.kt:63`, `ui/navegacion/guiado/TarjetaLlegada.kt:43`, `ui/alertas/TarjetaIncidencia.kt:37`, `ui/mapa/TarjetaCalor.kt:42`, `ui/tramos/CronometroMini.kt:168`).
7. **Color.**
   - `ColoresGuiado` repite tres colores que ya son tokens (`ui/navegacion/guiado/ColoresGuiado.kt:7-9` frente a `ui/theme/Wrc.kt:15`, `:18`, `:24`).
   - Hay dos rojos de marca casi iguales (`RojoAcento` 0xE02A32 en `ui/theme/Wrc.kt:27` y `franja` 0xD8232A en `ui/theme/Color.kt:102`).
   - El secundario azul del tema (`ui/theme/Color.kt:18-21`) solo aparece por accidente en componentes de Material sin personalizar.
8. **Tipografía de cifras.**
   - El sistema solo define dos tamaños de cifra, 40 y 28 sp (`ui/theme/TipografiaWrc.kt:24-27`), y sin cifras tabulares.
   - Cada pantalla se hace la suya: `ui/mistramos/DetalleTramoScreen.kt:93`, `ui/tramos/TarjetaMeta.kt:238-244`, `ui/analisis/CabeceraAnalisis.kt:230`, `ui/tramos/CronometroMini.kt:133-144`.
   - Hay 123 tamaños en `sp` escritos a mano en 32 archivos.
   - Los tiempos fuera del HUD (Inicio, tablas) no llevan la cursiva ni el ancho fijo.
9. **Títulos.** La barra superior pone el título en mayúsculas con `titleLarge` (`ui/comun/BarraSuperior.kt:43-47`). El estilo `Titulo` dice «sin mayúsculas forzadas» (`ui/theme/TipografiaWrc.kt:29`). El detalle de tramo tiene su propia cabecera (`ui/mistramos/DetalleTramoScreen.kt:178-240`) y el análisis otra (`ui/analisis/CabeceraAnalisis.kt`).
10. **Carga.** Hay `CargandoWrc` y esqueletos (`ui/comun/CargandoWrc.kt:53`, `:107`), pero quedan indicadores de Material sueltos (`ui/mapa/MapaScreen.kt:575`, `ui/navegacion/VistaPreviaRutas.kt:254`, `ui/mistramos/DibujarScreen.kt:87`).
11. **Avisos.** El mapa tiene su snackbar propio (`ui/mapa/SuperposicionMapa.kt:85-92`). Mis tramos, Detalle y Ajustes usan el de Material por defecto (`ui/mistramos/MisTramosScreen.kt:129`, `ui/mistramos/DetalleTramoScreen.kt:133`, `ui/ajustes/AjustesRoute.kt:92`). El atajo sin tramos usa un Toast del sistema (`ui/AppNavHost.kt:238`).
12. **Movimiento.** Hay duraciones definidas (`ui/theme/Wrc.kt:43-53`), pero solo diez archivos animan algo y la navegación no tiene transiciones. Fuera del mapa la app es estática.
13. **Mascota.** Trombito sale en dos pantallas (`ui/inicio/InicioScreen.kt:174`, `ui/ajustes/AcercaDeScreen.kt:128`). No está en el tutorial, en los estados vacíos, en la carga ni en la elección de voz.

### 3.2 Piezas comunes propuestas

Ocho piezas. Las que ya existen se amplían; no se crean duplicados.

| # | Pieza | Qué es | Sustituye a |
|---|---|---|---|
| 1 | **Cabecera WRC** | `BarraSuperior` con subtítulo opcional, hueco de acciones y una franja roja fina bajo el título, sobre `FondoWrc` siempre | Las cabeceras propias de Detalle y Análisis y el fondo plano de seis pantallas |
| 2 | **Tarjeta de dato** | Icono pequeño, cifra WRC tabular, unidad y etiqueta; variante con frase | Los tiles de Detalle (`ui/mistramos/DetalleTramoScreen.kt:306-329`), los totales del Perfil y las tarjetas del tablero de H30 |
| 3 | **Estado vacío ilustrado** | `EstadoVacioWrc` con hueco para ilustración (Trombito o dibujo en lienzo), título, frase y acción; variantes vacío, error y sin mapas | `EstadoVacio`, los textos sueltos y la tarjeta de «sin mapas» del mapa |
| 4 | **Chip y selector WRC** | `ChipIconoWrc` y un selector segmentado con el mismo aspecto (borde ámbar, sin azul) | `FilterChip`, botón segmentado, pestañas y chips a mano |
| 5 | **Fila de lista WRC** | Icono, título, detalle, valor o flecha; alto mínimo de 56 dp | La fila de Ayuda, la de resultados de búsqueda, la de nota del editor y la portada de Ajustes de H29 |
| 6 | **Mapa con panel** | Plantilla de mapa a pantalla completa con cabecera translúcida y panel inferior carbón con un dato grande y acciones de icono | Dibujar, Descargar zona y, en parte, la vista previa de rutas |
| 7 | **Estilos de cifra** | `CifraGrande`, `CifraMedia`, `CifraPequena` y `Tiempo`, todos con ancho fijo, en `TipografiaWrc.kt` | Los estilos locales de cifra y parte de los 123 `sp` sueltos |
| 8 | **Transiciones y carga** | Transición de pantalla única (deslizar y fundir, 300 ms), entrada escalonada reutilizable sacada de Inicio y esqueletos en vez de circulitos | La navegación sin animación y los indicadores sueltos |

Limpieza asociada, sin efecto visible: `ColoresGuiado` pasa a usar los tokens de `Wrc.kt`, `Radios` y `RallyShapes` se unifican y queda un solo rojo.

### 3.3 Dónde sí y dónde no usar la mascota y el movimiento

- **Sí**: Inicio, bienvenida, elegir voz, estados vacíos, carga inicial, resumen de viaje (H28), Acerca de. Siempre con el coche parado o antes de salir.
- **No**: mapa en marcha, HUD, hoja de conducción, banners. Ahí nada se mueve si no informa.
- Las animaciones respetan el ajuste del sistema de reducir movimiento y duran menos de medio segundo; ninguna en bucle salvo los esqueletos de carga.

## 4. Plan priorizado

Esfuerzo: S = menos de medio día de un agente, M = un día, L = varios. Modelo sugerido entre paréntesis.

### 4.1 Pura interfaz

| # | Mejora | Qué cambia para Joan | Impacto | Esfuerzo |
|---|---|---|---|---|
| 1 | Piezas 1, 3 y 7: cabecera y fondo únicos, estado vacío ilustrado, cifras tabulares (sonnet) | Toda la app tiene la misma textura y los tiempos se leen como en el crono | Alto | M |
| 2 | Inicio: «Última salida» como tarjeta pulsable con cifra WRC; CONDUCIR biselado; reparto del espacio (sonnet) | Inicio deja de parecer vacío y lleva al último tramo en un toque | Alto | S |
| 3 | Transiciones entre pantallas y entrada escalonada común (sonnet) | La app se siente fluida, como un menú de juego | Alto | S |
| 4 | Pieza 2 (tarjeta de dato) antes de H30 (sonnet) | El tablero del piloto nace con el estilo común | Alto | S |
| 5 | Vista previa de rutas: barra de curvas, cifra y hora de llegada, esqueleto de carga, panel carbón (sonnet) | Elige ruta mirando, sin leer tres columnas de texto | Alto | M |
| 6 | Trombito en la bienvenida, en elegir voz y en los estados vacíos; gesto corto en Inicio (sonnet; opus solo si hay que dibujar poses nuevas, ver P6) | El copiloto tiene cara en los momentos tranquilos | Medio | M |
| 7 | Mapas offline: cabecera con capas instaladas, un botón principal por estado, selector sin azul (sonnet) | Sabe qué tiene y qué falta de un vistazo | Medio | M |
| 8 | Búsqueda: icono por categoría, «Ir» y «+ Parada» como iconos, coordenadas ocultas si hay nombre (sonnet) | Resultados más rápidos de reconocer y botones más fáciles de acertar | Medio | S |
| 9 | Pieza 4 y limpieza de tokens: un solo chip/selector, radios y colores unificados (haiku para los reemplazos, sonnet para el selector) | Desaparecen el azul y los chips distintos | Medio | M |
| 10 | Pieza 6: Dibujar y Descargar zona con la misma plantilla y dato grande (sonnet) | Dos pantallas raras pasan a parecer la misma herramienta | Medio | M |
| 11 | «Qué le puedes decir» en chips con icono y pares en una fila; ayuda de notas con izquierdas y derechas (sonnet) | La chuleta de voz se recorre en una pantalla | Medio | S |
| 12 | Llegada con la forma de la tarjeta de meta; resumen de reconocimiento con iconos antes → después (sonnet) | Los finales se ven como en el juego | Medio | S |
| 13 | Editor de notas: fila con el icono de curva, leyenda con el chip común, ayuda como pista y no como diálogo (sonnet) | Edita viendo lo mismo que ve conduciendo | Bajo | M |
| 14 | Avisos unificados: mismo snackbar carbón en todas las pantallas y sin Toast (haiku) | Los avisos dejan de cambiar de aspecto | Bajo | S |

Orden recomendado: 1 → 4 → 2 → 3 → 5, y el resto según encajen con H28–H30. Las piezas 1, 2, 3 y 7 primero porque todo lo demás las usa.

### 4.2 Toca producto

**[PRODUCTO] P1 · Qué enseña Inicio además del menú.**
- A: nada más (como hoy, con los arreglos de la mejora 2).
- B: una fila de estado con iconos (mapas al día, voz, GPS, modo del copiloto).
- C: una tarjeta de temporada («412 km · 10 viajes») que abre el Perfil, cuando exista H30/H32.
- Recomendación: A ahora y C al cerrar H30; B solo si algo falla (icono de aviso en la tarjeta afectada).

**[PRODUCTO] P2 · Accesos directos en Inicio.**
- A: ninguno.
- B: «Ir a casa» y «Repetir último tramo» bajo CONDUCIR (los dos ya existen: `ui/mapa/MapaScreen.kt:696-708`, `ui/AppNavHost.kt:226-240`).
- C: solo «Repetir último tramo», dentro de la tarjeta de «Última salida».
- Recomendación: C. Añade un solo botón y lo pone donde está el dato.

**[PRODUCTO] P3 · Entrada a «Reconocer».**
- A: se queda en el detalle del tramo.
- B: además, al pulsarlo, una hoja breve que explica las dos pasadas antes de saltar al mapa.
- C: entrada propia en Inicio.
- Recomendación: B. Es un modo poco frecuente; no merece sitio en Inicio, pero sí avisar antes del salto.

**[PRODUCTO] P4 · Destinos recientes bajo «¿A dónde?».**
- A: no.
- B: los tres últimos destinos como chips junto a «Ir a casa».
- Recomendación: B. Requiere guardar los destinos elegidos; hoy no se ha visto que se guarden (**por comprobar** en `domain/navegacion`). Esfuerzo M.

**[PRODUCTO] P5 · Hoja del copiloto más corta.**
- A: como hoy, con scroll.
- B: dos pestañas de icono («Qué canta» / «Cómo suena»).
- C: lo poco usado (salida manual, intercom) se va solo a Ajustes.
- Recomendación: B. No quita nada de lo que Joan pidió poder tocar desde el mapa.

**[PRODUCTO] P6 · Cuánto Trombito.**
- A: solo la imagen actual, en más sitios y con movimiento sencillo.
- B: dos o tres poses nuevas (saludando, con bandera, dormido para los vacíos), comparadas renderizadas antes de enseñarlas, como pide la memoria de marca.
- Recomendación: A ahora; B si a Joan le gusta el resultado. Nunca en el mapa en marcha.

**[PRODUCTO] P7 · Ayuda de notas desde donde se ven.**
- A: solo en Ajustes › Ayuda.
- B: un icono «?» en la hoja del copiloto y en el editor que abre «Qué significan las notas».
- Recomendación: B. Un icono y una ruta que ya existe (`ui/AppNavHost.kt:169`).

**[PRODUCTO] P8 · Forma WRC en los menús.**
- A: menús redondeados como hoy; el sesgo solo en el HUD.
- B: bisel discreto en las tarjetas protagonistas (CONDUCIR, tarjeta de dato, cabeceras).
- C: paralelogramo en todo.
- Recomendación: B. C cansa y complica el texto largo; A deja dos apps distintas.

## 5. Dudas para QUESTIONS.md

1. P1–P8 de §4.2, con su recomendación.
2. ¿El tema claro se usa de verdad (va por defecto en «Día y noche»)? Si sí, las tarjetas sobre el mapa deben decidirse: todas carbón (recomendado) o todas del tema.
3. ¿Se rehacen las capturas de referencia tras H26–H29 antes de empezar estas mejoras? Recomendación: sí, un juego completo en `docs/capturas/h29/`, porque las de `h14`/`h15` ya no reflejan el HUD ni Ajustes.
