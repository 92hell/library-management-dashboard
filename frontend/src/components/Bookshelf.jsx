const SPINES = [
  ['#2f5d50', 78], ['#7a2e2e', 92], ['#b08d57', 70], ['#2b4a6f', 88], ['#6b4e2e', 64],
  ['#4a3f6b', 84], ['#3f6b4a', 96], ['#8a5a1f', 74], ['#5b2e4f', 86], ['#2f5d50', 68],
  ['#7a2e2e', 80], ['#2b4a6f', 90],
];

/** A decorative shelf of book spines for the sign-in pages. */
export default function Bookshelf() {
  return (
    <div className="bookshelf" aria-hidden="true">
      {SPINES.map(([color, height], i) => (
        <span key={i} className="spine" style={{ backgroundColor: color, height: `${height}%` }} />
      ))}
    </div>
  );
}
