import { DC, DR, type Mask, type PuzzleSpec } from './engine';

export interface Snapshot {
  masks: Mask[];
  marks: boolean[];
}

export const popcount = (m: number) => (m & 1) + ((m >> 1) & 1) + ((m >> 2) & 1) + ((m >> 3) & 1);

export function neighbour(p: PuzzleSpec, i: number, d: number): number {
  const r = Math.floor(i / p.width) + DR[d];
  const c = (i % p.width) + DC[d];
  if (r < 0 || r >= p.height || c < 0 || c >= p.width) return -1;
  return r * p.width + c;
}

export function clueSet(p: PuzzleSpec): Set<number> {
  return new Set(p.clues.map((c) => c.r * p.width + c.c));
}

/** Starting position: the entrance, the exit and every clue piece already placed. */
export function initialSnapshot(p: PuzzleSpec): Snapshot {
  const masks: Mask[] = new Array(p.width * p.height).fill(0);
  for (const cl of p.clues) {
    const i = cl.r * p.width + cl.c;
    masks[i] = cl.mask;
    for (let d = 0; d < 4; d++) {
      if ((cl.mask >> d) & 1) {
        const j = neighbour(p, i, d);
        if (j >= 0) masks[j] |= 1 << ((d + 2) & 3);
      }
    }
  }
  masks[p.startRow * p.width] |= 1 << 3;
  masks[(p.height - 1) * p.width + p.endCol] |= 1 << 2;
  return { masks, marks: new Array(p.width * p.height).fill(false) };
}

/**
 * Add or remove the track between cell i and its neighbour in direction d.
 * Returns the new masks, or null if nothing can change.
 */
export function changeEdge(
  p: PuzzleSpec,
  locked: Set<number>,
  masks: Mask[],
  i: number,
  d: number,
  mode: 'add' | 'remove' | 'toggle',
): Mask[] | null {
  const j = neighbour(p, i, d);
  if (j < 0 || locked.has(i) || locked.has(j)) return null;
  const has = ((masks[i] >> d) & 1) === 1;
  const action = mode === 'toggle' ? (has ? 'remove' : 'add') : mode;
  const next = masks.slice();
  if (action === 'add') {
    if (has || popcount(masks[i]) >= 2 || popcount(masks[j]) >= 2) return null;
    next[i] |= 1 << d;
    next[j] |= 1 << ((d + 2) & 3);
  } else {
    if (!has) return null;
    next[i] &= ~(1 << d);
    next[j] &= ~(1 << ((d + 2) & 3));
  }
  return next;
}

export function hasEdge(masks: Mask[], i: number, d: number): boolean {
  return ((masks[i] >> d) & 1) === 1;
}
