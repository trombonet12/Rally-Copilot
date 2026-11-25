import React from "react";
import { AbsoluteFill, interpolate, Sequence, staticFile, useCurrentFrame } from "remotion";
import { Audio } from "@remotion/media";
import { Fondo } from "./Fondo";
import { Entrada } from "./Entrada";
import { Movil } from "./Movil";
import { Rotulo } from "./Rotulo";
import { Cierre } from "./Cierre";
import { duracionTotal, finTramos, frames, guion, inicioTramo, ranuraTramo, vozEnFrame } from "./guion";

export type PromoProps = {
  /** false = sin música (scripts/video/promo.mjs lo pone si el archivo de guion.musica no existe). */
  conMusica: boolean;
};

const CLAMP = { extrapolateLeft: "clamp", extrapolateRight: "clamp" } as const;

export const Promo: React.FC<PromoProps> = ({ conMusica }) => {
  const frame = useCurrentFrame();
  const total = duracionTotal();
  const fin = finTramos();
  const fps = guion.fps;

  // Móvil y rótulos se funden antes del cierre.
  const escena = interpolate(frame, [fin - 14, fin + 6], [1, 0], CLAMP);

  const musica = guion.musica;

  return (
    <AbsoluteFill>
      <Fondo />
      <Sequence durationInFrames={frames(guion.entrada)} name="Entrada">
        <Entrada />
      </Sequence>
      <AbsoluteFill style={{ opacity: escena }}>
        <Movil />
        {guion.tramos.map((t, i) => {
          const ranura = ranuraTramo(i);
          return (
            <Sequence key={i} from={inicioTramo(i)} durationInFrames={ranura} name={`Rótulo ${i + 1}`}>
              <Rotulo tramo={t} numero={i + 1} total={guion.tramos.length} ranura={ranura} />
            </Sequence>
          );
        })}
      </AbsoluteFill>
      <Sequence from={fin} durationInFrames={frames(guion.cierre)} name="Cierre">
        <Cierre duracion={frames(guion.cierre)} />
      </Sequence>
      {musica && conMusica ? (
        <Audio
          src={staticFile(musica.archivo)}
          trimBefore={frames(musica.inicio)}
          volume={(f) =>
            musica.volumen *
            interpolate(f, [0, fps, total - Math.round(1.5 * fps), total], [0, 1, 1, 0], CLAMP) *
            // Ducking: baja a `ducking` mientras suena la voz del copiloto en el tramo.
            (1 - (1 - musica.ducking) * vozEnFrame(f))
          }
        />
      ) : null}
    </AbsoluteFill>
  );
};
