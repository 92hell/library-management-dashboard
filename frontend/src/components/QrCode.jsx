import { useT } from '../i18n';

/** A QR code drawn as one SVG path from the server's module matrix ('1' = dark), with a quiet zone. */
export default function QrCode({ qr, size = 132 }) {
  const t = useT();
  const quiet = 4;
  let path = '';
  for (let y = 0; y < qr.size; y++) {
    for (let x = 0; x < qr.size; x++) {
      if (qr.modules[y * qr.size + x] === '1') path += `M${x + quiet} ${y + quiet}h1v1h-1z`;
    }
  }
  const box = qr.size + quiet * 2;
  return (
    <svg
      role="img"
      aria-label={t('QR code: scan to check this receipt')}
      width={size}
      height={size}
      viewBox={`0 0 ${box} ${box}`}
      shapeRendering="crispEdges"
    >
      <rect width={box} height={box} fill="#fff" />
      <path d={path} fill="#000" />
    </svg>
  );
}
