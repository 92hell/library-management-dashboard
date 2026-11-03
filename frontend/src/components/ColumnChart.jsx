import { useEffect, useId, useLayoutEffect, useRef, useState } from 'react';
import { formatNumber } from '../format';
import { useT } from '../i18n';
import ResponsiveTable from './ResponsiveTable';

const HEIGHT = 200;
const MARGIN = { top: 10, right: 8, bottom: 26, left: 40 };
const MAX_BAR = 24;
const GAP = 2;
const RADIUS = 4;
const TOOLTIP_WIDTH = 190;

/** Clean axis ticks from 0 to at least `max`: steps of 1, 2 or 5 × 10^n. */
export function ticksFor(max) {
  if (max <= 0) return [0, 1];
  const rough = max / 4;
  const power = 10 ** Math.floor(Math.log10(rough));
  const step = Math.max(1, [1, 2, 5, 10].map((m) => m * power).find((s) => s >= rough));
  const ticks = [];
  for (let v = 0; v < max + step; v += step) ticks.push(v);
  return ticks;
}

/** Width of the element, following resizes (a fixed fallback where there is no layout, as in tests). */
function useWidth(ref, fallback) {
  const [width, setWidth] = useState(fallback);
  useLayoutEffect(() => {
    if (ref.current?.clientWidth) setWidth(Math.max(240, ref.current.clientWidth));
  }, [ref]);
  useEffect(() => {
    const el = ref.current;
    if (!el || typeof ResizeObserver === 'undefined') return undefined;
    const observer = new ResizeObserver(([entry]) => setWidth(Math.max(240, Math.floor(entry.contentRect.width))));
    observer.observe(el);
    return () => observer.disconnect();
  }, [ref]);
  return width;
}

/** A bar with a 4px rounded top and a square foot on the baseline. */
function barPath(x, y, w, h, rounded) {
  if (h <= 0) return '';
  const r = rounded ? Math.min(RADIUS, w / 2, h) : 0;
  return `M${x},${y + h} V${y + r} Q${x},${y} ${x + r},${y} H${x + w - r} Q${x + w},${y} ${x + w},${y + r} V${y + h} Z`;
}

/**
 * Columns over time, stacked when there are several series. The SVG is for sighted readers;
 * the same numbers are in the "Show the numbers" table, and the keyboard (arrow keys) and
 * pointer read one column at a time.
 *
 * @param buckets [{ key, label (axis), title (tooltip and table), values: [number per series] }]
 * @param series  [{ name, color }] in stacking order, bottom first
 */
export default function ColumnChart({ title, buckets, series }) {
  const t = useT();
  const id = useId();
  const box = useRef(null);
  const width = useWidth(box, 640);
  const [active, setActive] = useState(null);

  const plotW = width - MARGIN.left - MARGIN.right;
  const plotH = HEIGHT - MARGIN.top - MARGIN.bottom;
  const totals = buckets.map((b) => b.values.reduce((a, v) => a + v, 0));
  const ticks = ticksFor(Math.max(0, ...totals));
  const top = ticks[ticks.length - 1];
  const y = (v) => MARGIN.top + plotH - (v / top) * plotH;
  const slot = plotW / Math.max(1, buckets.length);
  const barW = Math.max(2, Math.min(MAX_BAR, slot * 0.7));
  const labelEvery = Math.max(1, Math.ceil(buckets.length / Math.max(1, Math.floor(plotW / 56))));
  const current = active == null ? null : buckets[active];

  /** Beside the column, never over it: to its right if that fits, else to its left. */
  function tooltipLeft(i) {
    const right = MARGIN.left + (i + 1) * slot + 4;
    if (right + TOOLTIP_WIDTH <= width) return right;
    return Math.max(0, MARGIN.left + i * slot - TOOLTIP_WIDTH - 4);
  }

  function onKeyDown(event) {
    const last = buckets.length - 1;
    const moves = { ArrowRight: 1, ArrowLeft: -1, Home: -Infinity, End: Infinity };
    if (!(event.key in moves)) return;
    event.preventDefault();
    setActive((i) => {
      const from = i ?? (moves[event.key] > 0 ? -1 : last + 1);
      return Math.min(last, Math.max(0, from + moves[event.key]));
    });
  }

  return (
    <figure className="column-chart mb-0" aria-labelledby={`${id}-title`}>
      <figcaption id={`${id}-title`} className="h6 mb-2">
        {title}
      </figcaption>
      {series.length > 1 && (
        <ul className="chart-legend list-inline small mb-2" aria-label={t('Legend')}>
          {series.map((s) => (
            <li key={s.name} className="list-inline-item me-3">
              <svg width="10" height="10" aria-hidden="true" className="me-1">
                <rect width="10" height="10" rx="2" style={{ fill: s.color }} />
              </svg>
              {s.name}
            </li>
          ))}
        </ul>
      )}
      <div
        ref={box}
        className="chart-box position-relative"
        tabIndex={0}
        role="group"
        aria-label={t('{title}: use the left and right arrow keys to read each column.', { title })}
        onKeyDown={onKeyDown}
        onBlur={() => setActive(null)}
        onPointerLeave={() => setActive(null)}
      >
        <svg width={width} height={HEIGHT} aria-hidden="true" className="d-block">
          {ticks.map((v) => (
            <g key={v}>
              <line x1={MARGIN.left} x2={width - MARGIN.right} y1={y(v)} y2={y(v)} className="chart-grid" />
              <text x={MARGIN.left - 6} y={y(v)} dy="0.32em" textAnchor="end" className="chart-axis">
                {formatNumber(v)}
              </text>
            </g>
          ))}
          {buckets.map((b, i) => {
            const x = MARGIN.left + i * slot + (slot - barW) / 2;
            let base = 0;
            const lastFilled = b.values.reduce((last, v, s) => (v > 0 ? s : last), -1);
            return (
              <g key={b.key}>
                {active === i && (
                  <rect x={MARGIN.left + i * slot} y={MARGIN.top} width={slot} height={plotH} className="chart-highlight" />
                )}
                {b.values.map((v, s) => {
                  if (v <= 0) return null;
                  const bottom = y(base) - (base > 0 ? GAP : 0);
                  base += v;
                  const topY = y(base);
                  return (
                    <path key={series[s].name} d={barPath(x, topY, barW, bottom - topY, s === lastFilled)}
                      style={{ fill: series[s].color }} />
                  );
                })}
                {i % labelEvery === 0 && (
                  <text x={MARGIN.left + i * slot + slot / 2} y={HEIGHT - 8} textAnchor="middle" className="chart-axis">
                    {b.label}
                  </text>
                )}
                <rect x={MARGIN.left + i * slot} y={MARGIN.top} width={slot} height={plotH} fill="transparent"
                  onPointerEnter={() => setActive(i)} onPointerMove={() => setActive(i)} />
              </g>
            );
          })}
        </svg>
        <div className="chart-tooltip small shadow-sm" aria-live="polite" hidden={!current}
          style={current ? { left: tooltipLeft(active) } : undefined}>
          {current && (
            <>
              <span className="d-block text-muted">{current.title}</span>
              {series.map((s, i) => (
                <span key={s.name} className="d-block">
                  <span className="chart-key" style={{ background: s.color }} aria-hidden="true" />
                  <strong>{formatNumber(current.values[i])}</strong> {s.name}
                </span>
              ))}
            </>
          )}
        </div>
      </div>
      <details className="mt-2">
        <summary className="small">{t('Show the numbers')}</summary>
        <ResponsiveTable size="sm" className="mt-2 mb-0" aria-labelledby={`${id}-title`}>
          <thead>
            <tr>
              <th scope="col">{t('Period')}</th>
              {series.map((s) => (
                <th key={s.name} scope="col" className="text-end">
                  {s.name}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {buckets.map((b) => (
              <tr key={b.key}>
                <td>{b.title}</td>
                {b.values.map((v, i) => (
                  <td key={series[i].name} className="text-end">
                    {formatNumber(v)}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </ResponsiveTable>
      </details>
    </figure>
  );
}
