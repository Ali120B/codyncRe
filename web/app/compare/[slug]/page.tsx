import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { AppleLogo } from "../../components/apple-logo";
import { SiteFooter, SiteHeader } from "../../components/site-chrome";
import CompareCards from "../../components/compare-cards";
import Halftone from "../../components/halftone";
import { rivals } from "../rivals";
import { DMG } from "../../links";

export const dynamicParams = false;

export function generateStaticParams() {
  return rivals.map((r) => ({ slug: r.slug }));
}

export async function generateMetadata({ params }: { params: Promise<{ slug: string }> }): Promise<Metadata> {
  const { slug } = await params;
  const r = rivals.find((x) => x.slug === slug);
  if (!r) return {};
  return { title: `Codync vs ${r.name}`, description: r.summary, alternates: { canonical: `/compare/${r.slug}` } };
}

export default async function ComparePage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  const r = rivals.find((x) => x.slug === slug);
  if (!r) notFound();

  return (
    <>
      <SiteHeader />
      <main className="flex-1">
        <section className="relative overflow-hidden px-4 pt-16 pb-12 sm:px-6 md:pt-24">
          <Halftone corner="top-right" className="w-[36rem] max-w-[80vw] text-neutral-50 opacity-[0.14]" />
          <div className="relative mx-auto max-w-6xl">
            <Link href="/compare" className="text-sm text-neutral-500 transition hover:text-neutral-300">
              Compare Codync
            </Link>
            <h1 className="mt-4 text-4xl font-semibold tracking-tighter text-neutral-50 md:text-6xl">
              Codync vs {r.name}
            </h1>
            <p className="mt-6 max-w-[40rem] text-lg leading-relaxed text-neutral-400">{r.summary}</p>
          </div>
        </section>

        <section className="px-4 py-12 sm:px-6">
          <div className="mx-auto max-w-6xl overflow-hidden rounded-3xl bg-neutral-900">
            <div className="grid grid-cols-[1fr_1fr] gap-4 px-6 pt-6 pb-4 text-sm font-medium text-neutral-500 md:grid-cols-[14rem_1fr_1fr] md:px-8">
              <span className="hidden md:block" />
              <span className="text-neutral-50">Codync</span>
              <span>{r.name}</span>
            </div>
            <dl className="divide-y divide-neutral-800">
              {r.rows.map(([topic, ours, theirs]) => (
                <div key={topic} className="grid grid-cols-[1fr_1fr] gap-x-4 gap-y-1 px-6 py-5 md:grid-cols-[14rem_1fr_1fr] md:px-8">
                  <dt className="col-span-2 text-sm font-medium text-neutral-500 md:col-span-1">{topic}</dt>
                  <dd className="leading-relaxed text-neutral-100">{ours}</dd>
                  <dd className="leading-relaxed text-neutral-400">{theirs}</dd>
                </div>
              ))}
            </dl>
          </div>
        </section>

        <section className="px-4 py-12 sm:px-6">
          <div className="mx-auto grid max-w-6xl grid-cols-1 gap-4 md:grid-cols-2">
            <div className="relative overflow-hidden rounded-3xl bg-neutral-50 p-8 md:p-10">
              <Halftone className="w-64 text-neutral-950 opacity-[0.12]" />
              <h2 className="relative text-xl font-semibold text-neutral-950">Pick Codync if</h2>
              <p className="relative mt-3 leading-relaxed text-neutral-600">{r.pickUs}</p>
              <a
                href={DMG}
                className="relative mt-8 inline-flex h-11 items-center gap-2 rounded-full bg-neutral-950 px-5 font-medium text-neutral-50 transition hover:bg-black active:scale-[0.98]"
              >
                <AppleLogo size={16} weight="fill" />
                Download for Mac
              </a>
            </div>
            <div className="rounded-3xl bg-neutral-900 p-8 md:p-10">
              <h2 className="text-xl font-semibold text-neutral-50">Pick {r.name} if</h2>
              <p className="mt-3 leading-relaxed text-neutral-400">{r.pickThem}</p>
            </div>
          </div>
        </section>

        <section className="px-4 py-12 sm:px-6">
          <div className="mx-auto max-w-6xl">
            <h2 className="text-sm font-medium text-neutral-500">Sources, checked {r.checked}</h2>
            <ul className="mt-3 flex flex-wrap gap-x-6 gap-y-2 text-sm">
              {r.sources.map(([label, url]) => (
                <li key={url}>
                  <a href={url} target="_blank" rel="noopener noreferrer" className="text-neutral-400 underline underline-offset-4 hover:text-neutral-200">
                    {label}
                  </a>
                </li>
              ))}
            </ul>
            <p className="mt-4 max-w-[44rem] text-sm text-neutral-600">
              {r.name} is a product of its owner. Codync is an independent project and isn&apos;t affiliated with it.
              Something out of date? Tell us on GitHub and we&apos;ll fix it.
            </p>
          </div>
        </section>

        <section className="px-4 pt-12 pb-24 sm:px-6">
          <div className="mx-auto max-w-6xl">
            <h2 className="mb-6 text-xl font-semibold text-neutral-50">More comparisons</h2>
            <CompareCards except={r.slug} />
          </div>
        </section>
      </main>
      <SiteFooter />
    </>
  );
}
