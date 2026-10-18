import type { Metadata } from "next";
import { SiteFooter, SiteHeader } from "../components/site-chrome";
import CompareCards from "../components/compare-cards";

export const metadata: Metadata = {
  title: "Codync vs Grok Bot, Dots, Muse and Claude Code phone apps",
  alternates: { canonical: "/compare" },
  description:
    "How Codync, the 100% free and open-source way to message AI agents as bots, compares with Grok Bot, Dots, Muse and phone apps for Claude Code.",
};

export default function CompareIndex() {
  return (
    <>
      <SiteHeader />
      <main className="flex-1 px-4 py-16 sm:px-6 md:py-24">
        <div className="mx-auto max-w-6xl">
          <h1 className="text-4xl font-semibold tracking-tighter text-neutral-50 md:text-5xl">Compare Codync</h1>
          <p className="mt-5 max-w-[36rem] text-lg leading-relaxed text-neutral-400">
            Side by side with the apps people ask about: price, where your agents run, which agents you can use and
            what you can do from your phone.
          </p>
          <div className="mt-12">
            <CompareCards />
          </div>
        </div>
      </main>
      <SiteFooter />
    </>
  );
}
