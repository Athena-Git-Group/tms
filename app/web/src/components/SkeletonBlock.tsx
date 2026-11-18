export function SkeletonBlock({ className }: { className: string }) {
  return <span aria-hidden="true" className={`block animate-shimmer bg-line-soft ${className}`} />;
}
