import React from "react";
import { interpolate, spring, useCurrentFrame, useVideoConfig } from "remotion";
import { CARBON_ELEVADO, FUENTE, LATON, ROJO, TEXTO, TEXTO_SUAVE } from "./paleta";
import { Trombito } from "./Trombito";

const CLAMP = { extrapolateLeft: "clamp", extrapolateRight: "clamp" } as const;

/** Franja de damero que se desplaza (meta de tramo). */
const Damero: React.FC<{ frame: number; sentido: 1 | -1; abajo: boolean; alto?: number }> = ({ frame, sentido, abajo, alto = 80 }) => (
  <div
    style={{
      position: "absolute",
      left: 0,
      right: 0,
      [abajo ? "bottom" : "top"]: 0,
      height: alto,
      opacity: 0.92,
      backgroundImage: `repeating-conic-gradient(${TEXTO} 0 25%, ${CARBON_ELEVADO} 0 50%)`,
      backgroundSize: `${alto}px ${alto}px`,
      backgroundPosition: `${sentido * frame * 9}px 0`,
    }}
  />
);

/** Cierre: Trombito con las banderas a cuadros, nombre y tres rasgos. Frame local. */
export const Cierre: React.FC<{ duracion: number }> = ({ duracion }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const entra = spring({ frame, fps, delay: 4, config: { damping: 9, stiffness: 110, mass: 0.9 } });
  const nombre = spring({ frame, fps, delay: 14, config: { damping: 14, stiffness: 170 } });
  const barra = spring({ frame, fps, delay: 22, config: { damping: 15, stiffness: 150 } });
  const rasgos = spring({ frame, fps, delay: 28, config: { damping: 16, stiffness: 120 } });
  const aparece = interpolate(frame, [0, 10], [0, 1], CLAMP);
  const salidaFinal = interpolate(frame, [duracion - 8, duracion], [1, 0], CLAMP);

  return (
    <div
      style={{
        position: "absolute",
        inset: 0,
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        fontFamily: FUENTE,
        opacity: aparece * salidaFinal,
      }}
    >
      <Damero frame={frame} sentido={1} abajo={false} />
      <Damero frame={frame} sentido={-1} abajo />
      <div style={{ transform: `translateY(${(1 - entra) * -700}px)` }}>
        <Trombito
          idp="cierre"
          pose="bandera"
          tamano={460}
          ondeo={frame * 0.28}
          rebote={Math.max(0, 1 - entra)}
        />
      </div>
      <div
        style={{
          marginTop: 16,
          fontSize: 148,
          fontWeight: 900,
          fontStyle: "italic",
          letterSpacing: -3,
          color: TEXTO,
          textShadow: "0 8px 0 rgba(0,0,0,0.45)",
          opacity: Math.min(1, nombre * 1.6),
          transform: `translateX(${(1 - nombre) * -300}px) skewX(${(1 - nombre) * -20}deg)`,
        }}
      >
        RALLY <span style={{ color: LATON }}>COPILOT</span>
      </div>
      <div
        style={{
          marginTop: 14,
          height: 12,
          width: 760 * barra,
          backgroundColor: ROJO,
          transform: "skewX(-24deg)",
        }}
      />
      <div
        style={{
          marginTop: 26,
          fontSize: 52,
          fontWeight: 600,
          color: TEXTO_SUAVE,
          opacity: rasgos,
          transform: `translateY(${(1 - rasgos) * 28}px)`,
        }}
      >
        Notas por voz · Tramos cronometrados · Offline
      </div>
    </div>
  );
};
