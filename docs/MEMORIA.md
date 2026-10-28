# Memoria del proyecto

> Propósito: lo que Joan ha pedido que se recuerde entre sesiones · Audiencia: Claude (se carga desde CLAUDE.md) · Actualizado: 2026-10-28 · Relacionado: skill `recordar`

Formato: `- AAAA-MM-DD · ámbito · lo que pidió el usuario · porqué/cómo aplicarlo`. La entrada más reciente manda;
las contradicciones se reescriben, no se acumulan. Se mantiene con la skill `recordar`.

## Preferencias de trabajo
- 2026-10-28 · permisos · «Déjame de pedirme permiso»: no pedir permiso para nada (push, fusionar PRs, acciones del hito); nunca force-push · sustituye a «push solo con permiso cada vez». `git push` sigue en `permissions.ask` de `.claude/settings.json` (el agente no puede tocar sus propios permisos): el aviso del arnés lo quita Joan moviéndolo a `allow`.
- 2026-10-28 · git · Commits locales frecuentes, Conventional Commits con resumen en español, sin `Co-Authored-By` ni atribución de IA · aunque el sistema sugiera el trailer, no se pone.
- 2026-10-28 · delegación · Subagentes simultáneos ≤ 1 punto: opus 1, sonnet 0,5, haiku 0,25 · antes de lanzar en paralelo, sumar; si se pasa, encadenar.
- 2026-10-28 · proceso · Modo desatendido, hito a hito; parar solo al cerrar un hito o si no se puede avanzar, presentando todas las dudas juntas · al cerrar cada hito, actualizar ROADMAP.md.
- 2026-10-28 · decisiones · No tomar decisiones de producto, interfaz ni alcance: apuntarlas en QUESTIONS.md con contexto, opciones y recomendación, y seguir con lo que no dependa · lo técnico menor se decide y se registra en docs/DECISIONES.md.
- 2026-10-28 · memoria · Mantener esta memoria entre sesiones con lo que el usuario pida («recuerda», «a partir de ahora», correcciones generales) · skill `recordar`; lo transversal a los tres proyectos va también a la memoria personal.
- 2026-10-28 · pruebas · Probar en el emulador del PC con GPS simulado de rutas reales de Mallorca; se puede pedir al usuario conectar su móvil por USB cuando la app esté avanzada · skill `gps-replay`.
- 2026-10-28 · dudas · Las preguntas de QUESTIONS.md se hacen en el chat, de 4 en 4 (AskUserQuestion), hasta acabarlas; también las dudas que no estén en QUESTIONS y los problemas encontrados · el usuario prefiere responder en el chat a editar el archivo.
- 2026-10-28 · firma · Keystore nueva y propia de Rally Copilot (`~/.rallycopilot/`); nunca reutilizar la de otro proyecto · regla del usuario.
- 2026-10-28 · alcance de sesión · La siguiente sesión desatendida hace solo H1 y para.
- 2026-10-28 · delegación · El agente principal es el jefe y orquestador: decide la arquitectura, reparte, revisa e integra; los subagentes (sonnet/haiku) hacen la implementación. Opus como subagente solo con una justificación clara (análisis exhaustivo, seguridad) · petición de Joan; no implementar en el principal lo que puede hacer un currante.
- 2026-10-28 · release · Distribución actual: APK de la GitHub Release instalado con Obtainium; Play más adelante (job con `PLAY_HABILITADO`) · no activar Play sin que lo pida.

## Producto
- 2026-10-28 · idioma · App solo en español.
- 2026-10-28 · licencia · App GPL-3.0 y repo público · por espeak-ng (GPL-3.0) y para Obtainium sin token.
- 2026-10-28 · alcance · Solo Mallorca, offline por defecto, sin backend, cuentas ni funciones comunitarias · internet solo para descargar mapas y refrescar incidencias.

## Estilo/UI
- 2026-10-28 · interfaz · Iconos en vez de rótulos de texto en los controles (p. ej. capas Mapa/Satélite) · «no me gusta lo que sea mapa satélite… tienen que ser iconos».
- 2026-10-28 · interfaz · La estética de las maniobras y avisos sigue los juegos de WRC · petición de Joan.
- 2026-10-28 · mapa · No quiere el copyright de OpenStreetMap ni ninguna atribución sobre el mapa; las atribuciones van solo en Ajustes > Acerca de · decisión suya aun sabiendo que las licencias piden atribuir en el mapa.
- 2026-10-28 · marca · Logo = mascota Trombito con casco de rally, mismo estilo y paleta que los hermanos · elegido por el usuario frente a «solo casco».
- 2026-10-28 · marca · El primer casco (blanco tipo capucha con visera negra) le pareció «muy muy feo»; el aceptado es un jet rojo con franja blanca, ribetes latón, brillo y micrófono · en diseños gráficos, comparar variantes renderizadas y elegir la que se lea mejor a tamaño de icono antes de enseñarla.
- 2026-10-28 · marca · El casco jet le gusta pero lo quería «un poquito más cuadradito», no alargado en vertical · cúpula más baja y ancha (variante S2); no volver a la forma de huevo.

## Correcciones
- 2026-10-28 · git · «El push no es que esté prohibido» · no bloquear el push con deny ni hooks (desde el 2026-10-28 tampoco se pregunta).
