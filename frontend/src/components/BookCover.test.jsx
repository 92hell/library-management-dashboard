import { fireEvent, render, screen } from '@testing-library/react';
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

  it('shows the real cover when there is one, and falls back to the drawn spine if it fails to load', () => {
    const { container } = render(<BookCover title="Saman" src="https://covers.example/saman.jpg" />);
    const img = container.querySelector('img');
    expect(img).toHaveAttribute('src', 'https://covers.example/saman.jpg');
    expect(img).toHaveAttribute('alt', '');
    expect(img).toHaveAttribute('loading', 'lazy');

    fireEvent.error(img);

    expect(container.querySelector('img')).toBeNull();
    expect(screen.getByText('S')).toBeInTheDocument();
  });
});
