/** Cloth-bound cover colours, picked deterministically per title. */
const COVERS = ['#2f5d50', '#7a2e2e', '#2b4a6f', '#6b4e2e', '#4a3f6b', '#3f6b4a', '#8a5a1f', '#5b2e4f'];
const SMALL_WORDS = new Set(['the', 'a', 'an', 'of', 'and', 'in', 'on', 'to', 'for', 'di', 'dan', 'yang']);

export function initials(title = '') {
  const words = title.split(/\s+/).filter(Boolean);
  const significant = words.filter((w) => !SMALL_WORDS.has(w.toLowerCase()));
  return (significant.length ? significant : words).slice(0, 2).map((w) => w[0].toUpperCase()).join('');
}

export function coverColor(title = '') {
  let hash = 0;
  for (const ch of title) hash = (hash * 31 + ch.charCodeAt(0)) >>> 0;
  return COVERS[hash % COVERS.length];
}

/** A small placeholder book cover (until real cover images exist). Purely decorative. */
export default function BookCover({ title, size = 'sm' }) {
  return (
    <span className={`book-cover book-cover-${size}`} style={{ backgroundColor: coverColor(title) }} aria-hidden="true">
      <span className="book-cover-initials">{initials(title)}</span>
    </span>
  );
}
