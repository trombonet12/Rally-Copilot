import React from "react";
import { AbsoluteFill, random, useCurrentFrame } from "remotion";
import { CARBON, CARBON_PROFUNDO, LATON, ROJO } from "./paleta";

const ANCHO = 1920;
const ALTO = 1080;
const PERIODO = 520;
/** Ángulo de las franjas (grados): diagonales como las de un coche de rally. */
const ANGULO = 24;

const COLORES_LINEA = ["#FFFFFF", "#FFFFFF", LATON, ROJO];

type Linea = { y: number; largo: number; grosor: number; vel: number; fase: number; color: string; opacidad: number };

// Líneas de velocidad: posiciones fijas por semilla (determinista) y movimiento en función del frame.
const LINEAS: Linea[] = Array.from({ length: 30 }, (_, i) => ({
  y: random(`y-${i}`) * ALTO,
  largo: 240 + random(`l-${i}`) * 560,
  grosor: 2 + Math.round(random(`g-${i}`) * 3),
  vel: 26 + random(`v-${i}`) * 38,
  fase: random(`f-${i}`) * 4000,
  color: COLORES_LINEA[Math.floor(random(`c-${i}`) * COLORES_LINEA.length)],
  opacidad: 0.1 + random(`o-${i}`) * 0.28,
}));

/** Fondo carbón vivo: franjas diagonales rojas y latón que avanzan, líneas de velocidad y viñeta. Nunca quieto. */
export const Fondo: React.FC = () => {
  const frame = useCurrentFrame();
  const avance = (frame * 7) % PERIODO;
  // Pulso lento de brillo para que ni siquiera las franjas respiren igual.
  const pulso = 0.85 + 0.15 * Math.sin(frame * 0.05);

  return (
    <AbsoluteFill style={{ backgroundColor: CARBON, overflow: "hidden" }}>
      <div
        style={{
          position: "absolute",
          left: ANCHO / 2 - 2200,
          top: ALTO / 2 - 2200,
          width: 4400,
          height: 4400,
          opacity: pulso,
          transform: `rotate(${ANGULO}deg) translateX(${avance}px)`,
          backgroundImage: `repeating-linear-gradient(90deg,
            transparent 0px, transparent 150px,
            rgba(224,42,50,0.20) 150px, rgba(224,42,50,0.20) 290px,
            transparent 290px, transparent 340px,
            rgba(229,171,70,0.13) 340px, rgba(229,171,70,0.13) 372px,
            transparent 372px, transparent ${PERIODO}px)`,
        }}
      />
      {LINEAS.map((l, i) => {
        const x = ANCHO - ((frame * l.vel + l.fase) % (ANCHO + l.largo));
        return (
          <div
            key={i}
            style={{
              position: "absolute",
              left: x,
              top: l.y,
              width: l.largo,
              height: l.grosor,
              borderRadius: l.grosor,
              opacity: l.opacidad,
              backgroundImage: `linear-gradient(90deg, transparent, ${l.color})`,
            }}
          />
        );
      })}
      <AbsoluteFill
        style={{
          backgroundImage: `radial-gradient(ellipse at 50% 50%, rgba(14,15,18,0) 35%, ${CARBON_PROFUNDO} 100%)`,
        }}
      />
    </AbsoluteFill>
  );
};
