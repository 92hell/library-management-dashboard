/**
 * A 1-D barcode from the server's modules ("1" bar, "0" space), with the quiet zones a scanner
 * needs on both sides. Drawn black on white whatever the theme, so it always scans.
 */
export default function Barcode({ modules, label, height = 40 }) {
  const quiet = 10;
  const width = modules.length + quiet * 2;
  let path = '';
  for (let x = 0; x < modules.length; x++) {
    if (modules[x] === '1') path += `M${x + quiet} 0h1v${height}h-1z`;
  }
  return (
    <svg role="img" aria-label={label} className="barcode" viewBox={`0 0 ${width} ${height}`}
      preserveAspectRatio="none" shapeRendering="crispEdges">
      <rect width={width} height={height} fill="#fff" />
      <path d={path} fill="#000" />
    </svg>
  );
}
