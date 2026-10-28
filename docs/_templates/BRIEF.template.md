<!-- Brief de subagente: ≤ 40 líneas. Un objetivo, ≤ 5 archivos (o ≤ 3 skills / ≤ 4 agentes / ≤ 3 docs largos / ≤ 4 workflows),
     un comando de validación, respuesta ≤ 15 líneas. Si no cumple, dividir.
     Presupuesto simultáneo de subagentes ≤ 1 punto: opus 1 · sonnet 0,5 · haiku 0,25. -->
ROL/AGENTE: <nombre | general-purpose + "Actúa según .claude/agents/<x>.md" (léelo primero)>
MODELO: <haiku|sonnet|opus>
OBJETIVO: <una frase>
LEER PRIMERO: <rutas concretas: plantilla, ejemplo piloto, docs/DECISIONES.md § sección, docs/investigacion/*.md>
ARCHIVOS PERMITIDOS (crear/modificar): <rutas exactas o globs>
ARCHIVOS PROHIBIDOS: todo lo demás (en especial los reservados: CLAUDE.md § Delegación)
PROHIBIDO: git commit/push, instalaciones globales, inventar sintaxis/versiones, implementar fuera del hito activo, decidir producto (→ devolver la duda), explorar fuera de LEER PRIMERO
<INSTRUCCIONES: lista breve y concreta>
VALIDACIÓN: <un comando exacto que debe pasar>
SI ALGO FALLA O FALTA INFO: no improvises; para y devuelve el bloqueo y lo que necesitas
DEVOLVER (≤ 15 líneas): archivos creados/modificados · resultado de validación · dudas de producto · cambios pedidos en archivos reservados
