"use client";

import { useEffect, useState } from "react";
import { GithubLogo, Star } from "@phosphor-icons/react";
import { GITHUB } from "../links";

const REPO_API = "https://api.github.com/repos/leepokai/Codync";
const compact = new Intl.NumberFormat("en", { notation: "compact", maximumFractionDigits: 1 });

// GitHub's own "Star | count" button, as a pill. The count loads in the browser
// (the site is a static export) and is left out when the API can't be reached.
export default function GitHubStarButton() {
  const [stars, setStars] = useState<number | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    fetch(REPO_API, { signal: controller.signal })
      .then((res) => (res.ok ? res.json() : null))
      .then((repo: { stargazers_count?: number } | null) => {
        if (typeof repo?.stargazers_count === "number") setStars(repo.stargazers_count);
      })
      .catch(() => {}); // Rate-limited or offline: the button still works without a count.
    return () => controller.abort();
  }, []);

  return (
    <a
      href={GITHUB}
      target="_blank"
      rel="noopener noreferrer"
      className="inline-flex h-12 items-center gap-2 rounded-full bg-neutral-900 px-6 font-medium text-neutral-100 transition hover:bg-neutral-800 active:scale-[0.98]"
    >
      <GithubLogo size={18} weight="fill" />
      Star on GitHub
      {stars !== null && (
        <span className="ml-1 inline-flex items-center gap-1 text-neutral-400 tabular-nums">
          <Star size={14} weight="fill" />
          {compact.format(stars)}
        </span>
      )}
    </a>
  );
}
