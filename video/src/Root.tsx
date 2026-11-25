import React from "react";
import { Composition } from "remotion";
import { Promo, type PromoProps } from "./Promo";
import { duracionTotal, guion } from "./guion";

export const RemotionRoot: React.FC = () => {
  return (
    <Composition
      id="Promo"
      component={Promo}
      width={1920}
      height={1080}
      fps={guion.fps}
      durationInFrames={duracionTotal()}
      defaultProps={{ conMusica: true } satisfies PromoProps}
      calculateMetadata={() => ({ durationInFrames: duracionTotal(), fps: guion.fps })}
    />
  );
};
