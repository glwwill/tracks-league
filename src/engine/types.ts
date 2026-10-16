/**
 * Directions: 0 = up, 1 = right, 2 = down, 3 = left.
 * A cell's "mask" is a bitmask of the directions its track leaves through.
 * 0 means the cell is empty. Straight pieces have 2 opposite bits set,
 * curves have 2 adjacent bits set.
 */
export type Mask = number;

export const UP = 0;
export const RIGHT = 1;
export const DOWN = 2;
export const LEFT = 3;

export const DR = [-1, 0, 1, 0];
export const DC = [0, 1, 0, -1];

export interface Clue {
  r: number;
  c: number;
  mask: Mask;
}

/** Everything needed to define a puzzle (no answer included). */
export interface PuzzleSpec {
  width: number;
  height: number;
  /** Track enters from the left edge of cell (startRow, 0). */
  startRow: number;
  /** Track exits through the bottom edge of cell (height - 1, endCol). */
  endCol: number;
  rowCounts: number[];
  colCounts: number[];
  clues: Clue[];
}

export interface Puzzle extends PuzzleSpec {
  seed: number;
  /** One mask per cell, row by row. The unique solution. */
  solution: Mask[];
}
