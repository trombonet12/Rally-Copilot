import React from "react";
import { interpolate, spring, useCurrentFrame, useVideoConfig } from "remotion";
import { ACENTO, CARBON_PROFUNDO, CARBON_SUPERFICIE, DELTA_MEJOR, DELTA_PEOR, FUENTE, LATON, ROJO, TEXTO, TEXTO_SUAVE } from "./paleta";
import type { Crono, Tramo } from "./guion";

type Props = {
  tramo: Tramo;
  /** Número de tramo (1..n) y total, para el kicker «TRAMO 1/5». */
  numero: number;
  total: number;
  /** Frames que dura el rótulo (para el fundido de salida y el progreso). */
  ranura: number;
};

const CLAMP = { extrapolateLeft: "clamp", extrapolateRight: "clamp" } as const;
const SKEW = "skewX(-14deg)";

/** 134.3 -> «02:14.3» */
export const formatoCrono = (s: number): string => {
  const d = Math.floor(s * 10 + 1e-6);
  const dec = d % 10;
  const seg = Math.floor(d / 10) % 60;
  const min = Math.floor(d / 600);
  return `${String(min).padStart(2, "0")}:${String(seg).padStart(2, "0")}.${dec}`;
};

/** -0.8 -> «−0,8», 1.2 -> «+1,2» (coma decimal y signo menos tipográfico). */
export const formatoDelta = (d: number): string =>
  `${d < 0 ? "−" : "+"}${Math.abs(d).toFixed(1).replace(".", ",")}`;

const Cronometro: React.FC = () => (
  <svg width={44} height={44} viewBox="0 0 24 24" fill="none" stroke={LATON} strokeWidth={2} strokeLinecap="round" aria-hidden="true">
    <circle cx="12" cy="13.5" r="7.5" />
    <path d="M12 9.5V13.5L14.8 15.2M9.5 3H14.5M12 3V6" />
  </svg>
);

/** Chip de cronómetro/delta tipo grafismo de TV; el tiempo corre de `desde` a `hasta` durante el rótulo. */
const ChipCrono: React.FC<{ crono: Crono; ranura: number }> = ({ crono, ranura }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const entra = spring({ frame, fps, delay: 20, config: { damping: 14, stiffness: 140, mass: 0.8 } });
  const delta = spring({ frame, fps, delay: 22 + Math.round(ranura * 0.35), config: { damping: 9, stiffness: 200, mass: 0.7 } });
  const t = interpolate(frame, [20, ranura - 22], [crono.desde, crono.hasta], CLAMP);
  const mejor = (crono.delta ?? 0) <= 0;
  const colorDelta = mejor ? DELTA_MEJOR : DELTA_PEOR;
  return (
    <div
      style={{
        marginTop: 38,
        display: "flex",
        alignItems: "stretch",
        alignSelf: "flex-start",
        opacity: Math.min(1, entra * 1.4),
        transform: `translateX(${(1 - entra) * -140}px) ${SKEW}`,
        transformOrigin: "left center",
        boxShadow: "0 10px 24px rgba(0,0,0,0.45)",
      }}
    >
      <div
        style={{
          display: "flex",
          alignItems: "center",
          gap: 16,
          padding: "14px 28px 14px 22px",
          backgroundColor: CARBON_PROFUNDO,
          border: `2px solid ${LATON}`,
        }}
      >
        <Cronometro />
        <div style={{ display: "flex", flexDirection: "column" }}>
          {crono.etiqueta ? (
            <div style={{ fontSize: 22, fontWeight: 800, letterSpacing: 3, color: TEXTO_SUAVE, lineHeight: 1 }}>{crono.etiqueta}</div>
          ) : null}
          <div style={{ fontSize: 52, fontWeight: 800, color: TEXTO, fontVariantNumeric: "tabular-nums", lineHeight: 1.1 }}>
            {formatoCrono(t)}
          </div>
        </div>
      </div>
      {crono.delta !== undefined ? (
        <div
          style={{
            display: "flex",
            alignItems: "center",
            padding: "0 28px",
            backgroundColor: colorDelta,
            color: CARBON_PROFUNDO,
            fontSize: 56,
            fontWeight: 900,
            fontVariantNumeric: "tabular-nums",
            transformOrigin: "left center",
            transform: `scaleX(${Math.max(0.01, delta)})`,
            opacity: Math.min(1, delta * 2),
          }}
        >
          {formatoDelta(crono.delta)}
        </div>
      ) : null}
    </div>
  );
};

/** Rótulo del tramo, a la izquierda (el móvil va a la derecha). Dentro de una Sequence: frame local. */
export const Rotulo: React.FC<Props> = ({ tramo, numero, total, ranura }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const cfg = { damping: 15, stiffness: 150, mass: 0.8 };
  const kicker = spring({ frame, fps, delay: 2, config: cfg });
  const barra = spring({ frame, fps, delay: 4, config: cfg });
  const sTitulo = spring({ frame, fps, delay: 8, config: cfg });
  const sSub = spring({ frame, fps, delay: 15, config: cfg });
  const salida = interpolate(frame, [ranura - 12, ranura - 1], [1, 0], CLAMP);
  const progreso = interpolate(frame, [0, ranura], [0, 1], CLAMP);
  const acento = ACENTO[tramo.tono];

  return (
    <div
      style={{
        position: "absolute",
        left: 110,
        top: 0,
        width: 830,
        height: 1080,
        display: "flex",
        flexDirection: "column",
        justifyContent: "center",
        fontFamily: FUENTE,
        opacity: salida,
        transform: `translateX(${(1 - salida) * -90}px)`,
      }}
    >
      <div
        style={{
          alignSelf: "flex-start",
          padding: "8px 26px",
          marginBottom: 22,
          backgroundColor: ROJO,
          color: TEXTO,
          fontSize: 34,
          fontWeight: 900,
          fontStyle: "italic",
          letterSpacing: 3,
          transform: `translateX(${(1 - kicker) * -300}px) ${SKEW}`,
          opacity: Math.min(1, kicker * 2),
        }}
      >
        TRAMO {numero}/{total}
      </div>
      <div style={{ display: "flex", alignItems: "stretch", gap: 26 }}>
        <div
          style={{
            width: 20,
            backgroundColor: ROJO,
            transformOrigin: "top",
            transform: `scaleY(${barra}) ${SKEW}`,
            flexShrink: 0,
          }}
        />
        <div style={{ display: "flex", flexDirection: "column" }}>
          <div
            style={{
              fontSize: 96,
              lineHeight: 1.02,
              fontWeight: 900,
              fontStyle: "italic",
              textTransform: "uppercase",
              color: TEXTO,
              letterSpacing: -2,
              textWrap: "balance",
              textShadow: "0 6px 0 rgba(0,0,0,0.45)",
              opacity: Math.min(1, sTitulo * 1.6),
              transform: `translateX(${(1 - sTitulo) * -260}px)`,
            }}
          >
            {tramo.titulo}
          </div>
          <div
            style={{
              marginTop: 26,
              fontSize: 44,
              lineHeight: 1.25,
              fontWeight: 500,
              color: acento === ROJO ? TEXTO_SUAVE : acento,
              opacity: sSub,
              transform: `translateX(${(1 - sSub) * -120}px)`,
            }}
          >
            {tramo.subtitulo}
          </div>
        </div>
      </div>
      {tramo.crono ? <ChipCrono crono={tramo.crono} ranura={ranura} /> : null}
      <div
        style={{
          marginTop: 46,
          height: 8,
          width: 560,
          backgroundColor: CARBON_SUPERFICIE,
          transform: SKEW,
          transformOrigin: "left center",
          overflow: "hidden",
        }}
      >
        <div style={{ width: `${progreso * 100}%`, height: "100%", backgroundColor: ROJO }} />
      </div>
    </div>
  );
};
