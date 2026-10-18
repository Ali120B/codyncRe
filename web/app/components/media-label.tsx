// The pill and one line of context above the live demo and the launch film.
export default function MediaLabel({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="mb-5 flex flex-col items-center gap-2 text-center">
      <span className="inline-flex items-center gap-2 rounded-full bg-neutral-900 px-3 py-1 text-xs font-medium tracking-[0.2em] text-neutral-200 uppercase">
        <span className="size-2 rounded-full bg-neutral-50" />
        {label}
      </span>
      <p className="text-sm text-neutral-400">{children}</p>
    </div>
  );
}
