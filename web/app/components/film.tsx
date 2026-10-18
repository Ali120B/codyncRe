import MediaLabel from "./media-label";
import Reveal from "./reveal";
import { FILM } from "../links";

// The launch film. youtube-nocookie sets nothing until the visitor presses play.
export default function Film() {
  return (
    <section className="px-4 pt-16 pb-20 sm:px-6 md:pt-24 md:pb-28">
      <div className="mx-auto max-w-6xl">
        <Reveal>
          <MediaLabel label="Launch film">See Codync in action, in under two minutes.</MediaLabel>
          <iframe
            src={`https://www.youtube-nocookie.com/embed/${FILM}?rel=0`}
            title="Codync launch film"
            loading="lazy"
            allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
            allowFullScreen
            className="aspect-video w-full rounded-3xl"
          />
        </Reveal>
      </div>
    </section>
  );
}
