// Small tracked caps above a heading, as on the App Store screenshots.
export default function Eyebrow({ children }: { children: React.ReactNode }) {
  return <p className="mb-4 text-xs font-medium tracking-[0.25em] text-neutral-500 uppercase">{children}</p>;
}
