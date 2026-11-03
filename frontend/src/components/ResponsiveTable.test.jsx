import { render } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import ResponsiveTable from './ResponsiveTable';

describe('ResponsiveTable', () => {
  it('labels every cell with its column header, so rows can be shown as cards on phones', () => {
    const { container } = render(
      <ResponsiveTable>
        <thead>
          <tr>
            <th>Book</th>
            <th>Due</th>
            <th>Actions</th>
            <th aria-label="Cover" />
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>Bumi Manusia</td>
            <td>Nov 6, 2026</td>
            <td>
              <button type="button">Renew</button>
            </td>
            <td />
          </tr>
        </tbody>
      </ResponsiveTable>,
    );

    const table = container.querySelector('table');
    expect(table).toHaveClass('table-stack');
    const cells = [...container.querySelectorAll('tbody td')];
    expect(cells.map((td) => td.getAttribute('data-label'))).toEqual([null, 'Due', null, null]);
    expect(cells[0]).toHaveClass('table-stack-title');
    expect(cells[2]).toHaveClass('table-stack-actions');
  });

  it('keeps the labels in step when the headers change (e.g. a language switch)', () => {
    const table = (header) => (
      <ResponsiveTable>
        <thead>
          <tr>
            <th>Book</th>
            <th>{header}</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>Bumi Manusia</td>
            <td>x</td>
          </tr>
        </tbody>
      </ResponsiveTable>
    );
    const { container, rerender } = render(table('Due'));
    rerender(table('Jatuh tempo'));
    expect(container.querySelectorAll('td')[1].getAttribute('data-label')).toBe('Jatuh tempo');
  });
});
