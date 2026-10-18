import Image from "next/image";

// Real iPhone screenshots (1206x2622, scaled to 780 wide) in a plain device frame,
// or live content (`children`) in the same frame.
export default function Phone({
  src,
  alt = "",
  eager = false,
  className = "",
  children,
}: {
  src?: string;
  alt?: string;
  eager?: boolean;
  className?: string;
  children?: React.ReactNode;
}) {
  return (
    <div
      className={`rounded-[2.75rem] bg-neutral-800 p-[7px] shadow-[0_40px_80px_-20px_rgba(0,0,0,0.8)] ${className}`}
    >
      {src ? (
        <Image
          src={src}
          alt={alt}
          width={780}
          height={1696}
          loading={eager ? "eager" : "lazy"}
          fetchPriority={eager ? "high" : "auto"}
          className="block h-auto w-full rounded-[2.35rem]"
        />
      ) : (
        <div className="relative aspect-[780/1696] w-full overflow-hidden rounded-[2.35rem] bg-white">{children}</div>
      )}
    </div>
  );
}
