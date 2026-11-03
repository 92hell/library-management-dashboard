import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import BookCover, { coverColor, initials } from './BookCover';

describe('BookCover', () => {
  it('uses up to two initials from the significant words of the title', () => {
    expect(initials('Bumi Manusia')).toBe('BM');
    expect(initials('The Name of the Rose')).toBe('NR');
    expect(initials('Supernova')).toBe('S');
  });

  it('always gives the same title the same colour', () => {
    expect(coverColor('Laskar Pelangi')).toBe(coverColor('Laskar Pelangi'));
    expect(coverColor('Laskar Pelangi')).toMatch(/^#[0-9a-f]{6}$/i);
  });

  it('is decorative: hidden from screen readers', () => {
    render(<BookCover title="Saman" />);
    expect(screen.getByText('S').closest('[aria-hidden="true"]')).toBeInTheDocument();
  });
});
