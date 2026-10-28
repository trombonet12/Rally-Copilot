// Lee el JSON de entrada de un hook (stdin) e imprime un campo de tool_input (o de la raíz).
// Uso: node hook-input.mjs <file_path|command|tool_name>. Sale con código ≠ 0 si el JSON es inválido.
let raw = '';
process.stdin.setEncoding('utf8');
process.stdin.on('data', (chunk) => (raw += chunk));
process.stdin.on('end', () => {
  const field = process.argv[2];
  const data = JSON.parse(raw);
  const input = data.tool_input ?? {};
  const value =
    field === 'file_path'
      ? (input.file_path ?? input.notebook_path ?? input.path ?? '')
      : (input[field] ?? data[field] ?? '');
  process.stdout.write(String(value));
});
