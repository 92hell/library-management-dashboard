import { useState } from 'react';

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

/**
 * The book's cover: the real image when `src` is given (Open Library), otherwise, or if the image
 * cannot be loaded, a cloth-bound spine with the title's initials. Purely decorative.
 */
export default function BookCover({ title, size = 'sm', src }) {
  const [failedSrc, setFailedSrc] = useState(null);
  if (src && failedSrc !== src) {
    return (
      <img
        className={`book-cover book-cover-img book-cover-${size}`}
        src={src}
        alt=""
        loading="lazy"
        onError={() => setFailedSrc(src)}
      />
    );
  }
  return (
    <span className={`book-cover book-cover-${size}`} style={{ backgroundColor: coverColor(title) }} aria-hidden="true">
      <span className="book-cover-initials">{initials(title)}</span>
    </span>
  );
}
