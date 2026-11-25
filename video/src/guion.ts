/**
 * Guion del vídeo promocional: TODO lo editable vive aquí (textos, tiempos, clips, música, ritmo).
 * Los componentes solo lo leen; `duracionTotal()` alimenta la duración de la composición.
 */

/** Tono de acento de cada tramo (ver paleta.ts). */
export type Tono = "ROJO" | "LATON" | "VERDE";

export type Crono = {
  /** Texto pequeño sobre el tiempo (opcional), p. ej. «PARCIAL 2». */
  etiqueta?: string;
  /** Segundos de crono al empezar el rótulo. */
  desde: number;
  /** Segundos de crono al acabar el rótulo (el contador corre de `desde` a `hasta`). */
  hasta: number;
  /** Delta en segundos (negativo = más rápido, verde; positivo = más lento, rojo). Si falta, no se pinta. */
  delta?: number;
};

export type Tramo = {
  /** Clip en video/public/ (lo copia scripts/video/promo.mjs desde build/promo/clips/). */
  clip: string;
  /** Segundo del clip en el que empieza el tramo. */
  inicio: number;
  /** Duración deseada del tramo, en segundos (se ajusta al beat más cercano). */
  duracion: number;
  /** 3 a 5 palabras. */
  titulo: string;
  /** 6 a 9 palabras. */
  subtitulo: string;
  tono: Tono;
  /** true = el clip trae la voz del copiloto: se oye y la música baja (ducking) mientras dura. */
  voz: boolean;
  /** Chip de cronómetro/delta tipo grafismo de TV (opcional). */
  crono?: Crono;
};

export const guion = {
  fps: 30,
  /** Pulsos por minuto de la música: los cortes entre tramos caen en frames de beat. */
  bpm: 126,
  /** Segundos de la entrada (Trombito + nombre + lema). */
  entrada: 2.5,
  /** Segundos del cierre (Trombito con bandera). */
  cierre: 3,
  /** Segundos de solape entre tramos (transición). */
  transicion: 0.4,
  /**
   * Música (archivo en video/public/musica/). Si el archivo no existe, promo.mjs renderiza sin música y avisa.
   * `inicio`: segundo de la pista donde arranca. `ducking`: fracción de volumen mientras habla el copiloto.
   * `null` = sin música.
   */
  musica: { archivo: "musica/high-energy-adventure-theme.mp3", volumen: 0.55, inicio: 0, ducking: 0.25 } as null | {
    archivo: string;
    volumen: number;
    inicio: number;
    ducking: number;
  },
  /** Volumen de la voz del copiloto (audio de los clips con `voz: true`). */
  volumenVoz: 1,
  tramos: [
    {
      clip: "clips/c1-notas.mp4",
      inicio: 0.2,
      duracion: 5.6,
      titulo: "Notas de rally por voz",
      subtitulo: "Te canta cada curva antes de llegar",
      tono: "ROJO",
      voz: true,
    },
    {
      clip: "clips/c2-tramo.mp4",
      inicio: 0.2,
      duracion: 5.6,
      titulo: "Cada carretera, un tramo",
      subtitulo: "Sales, conduces y el crono corre solo",
      tono: "LATON",
      voz: true,
    },
    {
      clip: "clips/c3-fantasma.mp4",
      inicio: 0.2,
      duracion: 5.6,
      titulo: "Contra tu fantasma",
      subtitulo: "Cuánto ganas o pierdes, curva a curva",
      tono: "VERDE",
      voz: true,
    },
    {
      clip: "clips/c4-resumen.mp4",
      inicio: 0.2,
      duracion: 5.6,
      titulo: "Parciales y delta",
      subtitulo: "El resumen del tramo, como en la tele",
      tono: "VERDE",
      voz: false,
      crono: { etiqueta: "TOTAL", desde: 111, hasta: 111 },
    },
    {
      clip: "clips/c5-temporada.mp4",
      inicio: 0.2,
      duracion: 5.6,
      titulo: "Tu temporada",
      subtitulo: "Tus mejores tiempos, siempre contigo",
      tono: "LATON",
      voz: false,
    },
  ] as Tramo[],
};

/** El móvil entra medio segundo antes de que acabe la entrada. */
export const ANTICIPO_MOVIL_S = 0.5;

/** Frames de rampa del ducking de la música. */
export const RAMPA_DUCKING = 8;

export const frames = (s: number): number => Math.round(s * guion.fps);

/** Frames por beat (no entero: 30 fps · 60 / 126 bpm ≈ 14,3). */
export const framesPorBeat = (): number => (60 / guion.bpm) * guion.fps;

/** Ajusta un frame al frame de beat más cercano. */
export const aBeat = (f: number): number => Math.round(Math.round(f / framesPorBeat()) * framesPorBeat());

/** Frame global en que empiezan los tramos (y entra el móvil), en un beat. */
export const inicioTramos = (): number => aBeat(frames(guion.entrada - ANTICIPO_MOVIL_S));

/**
 * Frame global de inicio de cada tramo = frame en que empieza su transición de entrada, siempre en un beat.
 * Entre dos tramos consecutivos hay `transicion` s de solape.
 */
export const inicioTramo = (i: number): number => {
  let f = inicioTramos();
  for (let k = 0; k < i; k++) {
    const siguiente = aBeat(f + frames(guion.tramos[k].duracion) - frames(guion.transicion));
    f = Math.max(siguiente, f + Math.round(framesPorBeat()));
  }
  return f;
};

/** Frames que dura la `Sequence` del tramo i dentro del TransitionSeries (incluye el solape con el siguiente). */
export const duracionTramo = (i: number): number => {
  const ultimo = i === guion.tramos.length - 1;
  if (ultimo) {
    return Math.max(aBeat(inicioTramo(i) + frames(guion.tramos[i].duracion)) - inicioTramo(i), Math.round(framesPorBeat()));
  }
  return inicioTramo(i + 1) - inicioTramo(i) + frames(guion.transicion);
};

/** Frames de la ranura «propia» del tramo (hasta que empieza el siguiente). */
export const ranuraTramo = (i: number): number =>
  i === guion.tramos.length - 1 ? duracionTramo(i) : inicioTramo(i + 1) - inicioTramo(i);

export const finTramos = (): number => {
  const n = guion.tramos.length - 1;
  return inicioTramo(n) + duracionTramo(n);
};

export const duracionTotal = (): number => finTramos() + frames(guion.cierre);

/** Ducking de la música: 0..1 (1 = habla el copiloto) en un frame global. */
export const vozEnFrame = (frame: number): number => {
  let max = 0;
  guion.tramos.forEach((t, i) => {
    if (!t.voz) return;
    const ini = inicioTramo(i);
    const fin = ini + duracionTramo(i);
    const v = Math.min(Math.max((frame - (ini - RAMPA_DUCKING)) / RAMPA_DUCKING, 0), Math.max((fin - frame) / RAMPA_DUCKING, 0), 1);
    max = Math.max(max, v);
  });
  return max;
};
