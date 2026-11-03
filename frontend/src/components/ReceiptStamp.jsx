import { useT } from '../i18n';

/** The library's round rubber stamp on a receipt: name, "loan receipt / bukti peminjaman", issue date. */
export default function ReceiptStamp({ library, date }) {
  const t = useT();
  const name = library.length > 24 ? `${library.slice(0, 23)}…` : library;
  return (
    <svg
      className="receipt-stamp"
      role="img"
      aria-label={t('Stamp: {library}, {date}', { library, date })}
      width="150"
      height="150"
      viewBox="0 0 150 150"
    >
      <g transform="rotate(-12 75 75)" fill="none" stroke="currentColor">
        <circle cx="75" cy="75" r="70" strokeWidth="3" />
        <circle cx="75" cy="75" r="62" strokeWidth="1" />
        <g fill="currentColor" stroke="none" fontFamily="Helvetica, Arial, sans-serif" fontWeight="700" textAnchor="middle">
          <text x="75" y="52" fontSize="10">{name.toUpperCase()}</text>
          <text x="75" y="76" fontSize="14">LOAN RECEIPT</text>
          <text x="75" y="91" fontSize="10">BUKTI PEMINJAMAN</text>
          <text x="75" y="111" fontSize="11">{date}</text>
        </g>
      </g>
    </svg>
  );
}
