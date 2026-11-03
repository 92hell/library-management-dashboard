import { useLayoutEffect, useRef } from 'react';
import { Table } from 'reactstrap';

/**
 * A table that turns into a stack of cards on phones (see .table-stack in theme.css): each cell is
 * labelled with its column header, and the cell holding the row's buttons becomes the card's
 * action bar. Desktop and print look like a normal table.
 */
export default function ResponsiveTable({ className = '', children, ...props }) {
  const ref = useRef(null);

  // After every render: rows and headers can change (paging, a language switch).
  useLayoutEffect(() => {
    const table = ref.current;
    if (!table) return;
    const labels = [...table.querySelectorAll('thead th')].map((th) => {
      const hidden = th.querySelector('.visually-hidden');
      return hidden || !th.textContent.trim() ? '' : th.textContent.trim();
    });
    for (const row of table.querySelectorAll('tbody tr')) {
      [...row.children].forEach((cell, i) => {
        // A cell holding the row's buttons becomes the card's action bar, whatever its header says.
        // The first cell (usually the book or member) is the card's heading, without a label.
        const actions = cell.querySelector('button, .btn') !== null;
        const title = i === 0 && !actions;
        cell.classList.toggle('table-stack-actions', actions);
        cell.classList.toggle('table-stack-title', title);
        if (labels[i] && !actions && !title) cell.setAttribute('data-label', labels[i]);
        else cell.removeAttribute('data-label');
      });
    }
  });

  return (
    <Table innerRef={ref} className={`table-stack ${className}`} {...props}>
      {children}
    </Table>
  );
}
