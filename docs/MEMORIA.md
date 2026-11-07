# Memoria del proyecto

> Propósito: lo que Joan ha pedido que se recuerde entre sesiones · Audiencia: Claude (se carga desde CLAUDE.md) · Actualizado: 2026-11-01 · Relacionado: skill `recordar`

Formato: `- AAAA-MM-DD · ámbito · lo que pidió el usuario · porqué/cómo aplicarlo`. La entrada más reciente manda;
las contradicciones se reescriben, no se acumulan. Se mantiene con la skill `recordar`.

## Preferencias de trabajo
- 2026-11-01 · permisos · No pedir permiso para las acciones del hito ni para fusionar PRs. **Push a `main` sin preguntar cuando el gate y los scripts estén en verde** («puedes ir haciendo push cuando esté todo en verde»); al cerrar cada hito se sube la menor (0.4, 0.5…) y se publica · sustituye a «push solo cuando Joan lo pida» (2026-10-31). Nunca force-push ni push con algo en rojo. Al repo de datos `rally-copilot-data` también se hace push y release durante el hito.
- 2026-10-28 · git · Commits locales frecuentes, Conventional Commits con resumen en español, sin `Co-Authored-By` ni atribución de IA · aunque el sistema sugiera el trailer, no se pone.
- 2026-10-28 · delegación · Subagentes simultáneos ≤ 1 punto: opus 1, sonnet 0,5, haiku 0,25 · antes de lanzar en paralelo, sumar; si se pasa, encadenar.
- 2026-10-28 · proceso · Modo desatendido, hito a hito; parar solo al cerrar un hito o si no se puede avanzar, presentando todas las dudas juntas · al cerrar cada hito, actualizar ROADMAP.md.
- 2026-10-28 · decisiones · No tomar decisiones de producto, interfaz ni alcance: apuntarlas en QUESTIONS.md con contexto, opciones y recomendación, y seguir con lo que no dependa · lo técnico menor se decide y se registra en docs/DECISIONES.md.
- 2026-10-28 · memoria · Mantener esta memoria entre sesiones con lo que el usuario pida («recuerda», «a partir de ahora», correcciones generales) · skill `recordar`; lo transversal a los tres proyectos va también a la memoria personal.
- 2026-10-28 · pruebas · Probar en el emulador del PC con GPS simulado de rutas reales de Mallorca; se puede pedir al usuario conectar su móvil por USB cuando la app esté avanzada · skill `gps-replay`.
- 2026-10-28 · dudas · Las preguntas de QUESTIONS.md se hacen en el chat, de 4 en 4 (AskUserQuestion), hasta acabarlas; también las dudas que no estén en QUESTIONS y los problemas encontrados · el usuario prefiere responder en el chat a editar el archivo.
- 2026-10-28 · firma · Keystore nueva y propia de Rally Copilot (`~/.rallycopilot/`); nunca reutilizar la de otro proyecto · regla del usuario.
- 2026-11-01 · alcance de sesión · Hitos encadenados «sin parar», muy desatendido: se para solo si hay dudas, al principio o al final de cada hito (todas juntas, de 4 en 4); sin dudas, se publica la versión y se sigue con el siguiente · sustituye a la entrada del 2026-10-31 (parar en cada cierre para pedir push).
- 2026-10-28 · delegación · El agente principal es el jefe y orquestador: decide la arquitectura, reparte, revisa e integra; los subagentes (sonnet/haiku) hacen la implementación. Opus como subagente solo con una justificación clara (análisis exhaustivo, seguridad) · petición de Joan; no implementar en el principal lo que puede hacer un currante.
- 2026-11-02 · consumo · Queda ~12 % del límite semanal y Joan quiere acabar todos los hitos con eso: ahorrar tokens y no repetir tareas · un agente por frente e hito con brief corto y respuesta ≤ 6 líneas; gate completo y push solo al cerrar el hito; emulador y capturas solo al cierre; sin revisiones opus salvo seguridad imprescindible. Preguntar no es problema: ante la duda, preguntar en vez de asumir.
- 2026-10-28 · release · Distribución actual: APK de la GitHub Release instalado con Obtainium; Play más adelante (job con `PLAY_HABILITADO`) · no activar Play sin que lo pida.

## Producto
- 2026-10-28 · idioma · App solo en español.
- 2026-10-28 · licencia · App GPL-3.0 y repo público · por espeak-ng (GPL-3.0) y para Obtainium sin token.
- 2026-10-28 · alcance · Solo Mallorca, offline por defecto, sin backend, cuentas ni funciones comunitarias · internet solo para descargar mapas y refrescar incidencias.

## Estilo/UI
- 2026-11-03 · interfaz · La 1.0 tiene que parecer una app final, no de pruebas; nada de fondos rojos de pegote (el rojo como acento, paneles con fondo negro vacío como las notas) · antes de cerrar algo de interfaz, verificar con capturas punto por punto contra lo pedido.
- 2026-10-28 · interfaz · Iconos en vez de rótulos de texto en los controles (p. ej. capas Mapa/Satélite) · «no me gusta lo que sea mapa satélite… tienen que ser iconos».
- 2026-10-28 · interfaz · La estética de las maniobras y avisos sigue los juegos de WRC · petición de Joan.
- 2026-10-28 · mapa · No quiere el copyright de OpenStreetMap ni ninguna atribución sobre el mapa; las atribuciones van solo en Ajustes > Acerca de · decisión suya aun sabiendo que las licencias piden atribuir en el mapa.
- 2026-10-28 · marca · Logo = mascota Trombito con casco de rally, mismo estilo y paleta que los hermanos · elegido por el usuario frente a «solo casco».
- 2026-10-28 · marca · El primer casco (blanco tipo capucha con visera negra) le pareció «muy muy feo»; el aceptado es un jet rojo con franja blanca, ribetes latón, brillo y micrófono · en diseños gráficos, comparar variantes renderizadas y elegir la que se lea mejor a tamaño de icono antes de enseñarla.
- 2026-10-28 · marca · El casco jet le gusta pero lo quería «un poquito más cuadradito», no alargado en vertical · cúpula más baja y ancha (variante S2); no volver a la forma de huevo.

- 2026-11-01 · interfaz · Los ajustes tienen que ser claros y enseñar en vivo cómo cambia cada cosa (vista previa al tocar), y lo que se toca conduciendo debe poder cambiarse desde el mapa · la pantalla de Ajustes de H4 le pareció «tétrica y poco intuitiva»; aplicarlo a cualquier ajuste nuevo.

## Correcciones
- 2026-10-28 · git · «El push no es que esté prohibido» · no bloquear el push con deny ni hooks (cuándo se hace: ver la entrada de permisos del 2026-11-01).
