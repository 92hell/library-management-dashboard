import { describe, expect, it } from 'vitest';
import { ticksFor } from './ColumnChart';

describe('Chart axis ticks', () => {
  it('uses clean steps of 1, 2 or 5 and reaches the largest value', () => {
    expect(ticksFor(0)).toEqual([0, 1]);
    expect(ticksFor(3)).toEqual([0, 1, 2, 3]);
    expect(ticksFor(7)).toEqual([0, 2, 4, 6, 8]);
    expect(ticksFor(18)).toEqual([0, 5, 10, 15, 20]);
    expect(ticksFor(1234)).toEqual([0, 500, 1000, 1500]);
  });
});
