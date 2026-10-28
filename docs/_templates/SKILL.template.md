---
name: <nombre-kebab-igual-a-la-carpeta>
description: <Qué hace + cuándo usarla. 1 línea, ≤ 200 caracteres, discriminante, caso de uso al principio.>
argument-hint: "[<valor1>|<valor2>]"   # solo si acepta argumento; borrar si no
allowed-tools: Read, Grep, Glob, Bash   # opcional; mínimo necesario
disable-model-invocation: true          # solo skills con efectos (release, rollback, commit); borrar si no
---

# <nombre>

<!-- Reglas: cuerpo ≤ 100 líneas. Argumento vía $ARGUMENTS ($0, $1…). Scripts largos → scripts/ o archivos auxiliares
     en la carpeta de la skill. Referenciar docs/DECISIONES.md por sección, nunca copiar su texto.
     Sin versiones ni sintaxis inventadas. Borrar este comentario. -->

## Cuándo usarla
- <situación concreta>

## Cuándo NO usarla
- <situación> → usa `<otra-skill>`

## Precondiciones
- <estado requerido: hito activo, herramientas, archivos>

## Pasos
1. <paso verificable>
2. <si hay argumento: rama por valor de $ARGUMENTS>

## Validaciones / tests
- `<comando exacto>`

## Definition of Done
- [ ] <criterio comprobable>
- [ ] gate `qa fast` en verde (o el comando de validación del ámbito)

## Docs a actualizar
- <docs/…> · docs/ESTADO.md (skill session-handoff)
