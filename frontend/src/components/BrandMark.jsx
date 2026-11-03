/** Open-book logo used in the navbar and on the login page. */
export default function BrandMark({ size = 28 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 32 32" aria-hidden="true" focusable="false">
      <path d="M16 8c-3-2.2-7.2-3-12-2.6v18.4c4.8-.4 9 .4 12 2.6V8z" fill="#f3e9d2" stroke="currentColor" strokeWidth="1.5" strokeLinejoin="round" />
      <path d="M16 8c3-2.2 7.2-3 12-2.6v18.4c-4.8-.4-9 .4-12 2.6V8z" fill="#efe2c4" stroke="currentColor" strokeWidth="1.5" strokeLinejoin="round" />
      <path d="M20 7.4v8l2-1.4 2 1.4V6.6" fill="#8b2e2e" />
    </svg>
  );
}
