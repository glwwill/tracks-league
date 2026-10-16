import { makeRng, type Rng } from './rng';
import { solve, isUnique, deduce } from './solver';
import { DC, DR, type Clue, type Mask, type Puzzle, type PuzzleSpec } from './types';

export const MIN_SIZE = 5;
export const MAX_SIZE = 10;

export type Difficulty = 'easy' | 'medium' | 'hard';
export const DIFFICULTIES: Difficulty[] = ['easy', 'medium', 'hard'];

/**
 * easy:   many given pieces, solvable by deduction alone
 * medium: fewer given pieces, solvable by deduction alone
 * hard:   as few pieces as possible, may need trial and error
 */
const LEVELS: Record<Difficulty, { floor: number; logic: boolean }> = {
  easy: { floor: 0.2, logic: true },
  medium: { floor: 0.1, logic: true },
  hard: { floor: 0.04, logic: false },
};
/** Search budget per uniqueness check. If it runs out we just add another clue. */
const SEARCH_NODES = 4000;

interface RoutePath {
  startRow: number;
  endCol: number;
  cells: number[];
}

/** Random self-avoiding route from the left edge to the bottom edge. */
function randomRoute(w: number, h: number, rng: Rng): RoutePath | null {
  const startRow = rng.int(h);
  const endCol = rng.int(w);
  const start = startRow * w;
  const end = (h - 1) * w + endCol;
  if (start === end) return null;

  const minLen = Math.max(w + h - 1, Math.round(w * h * 0.4));
  const maxLen = Math.round(w * h * 0.75);

  const visited = new Uint8Array(w * h);
  const path: number[] = [];
  let budget = 4000;

  const dfs = (cell: number, lastDir: number): boolean => {
    if (budget-- <= 0) return false;
    visited[cell] = 1;
    path.push(cell);
    if (cell === end) {
      if (path.length >= minLen) return true;
      visited[cell] = 0;
      path.pop();
      return false;
    }
    if (path.length < maxLen) {
      const r = Math.floor(cell / w);
      const c = cell % w;
      const dirs = rng.shuffle([0, 1, 2, 3]);
      if (lastDir >= 0 && rng.next() < 0.35) {
        dirs.splice(dirs.indexOf(lastDir), 1);
        dirs.unshift(lastDir);
      }
      for (const d of dirs) {
        const rr = r + DR[d];
        const cc = c + DC[d];
        if (rr < 0 || rr >= h || cc < 0 || cc >= w) continue;
        const j = rr * w + cc;
        if (visited[j]) continue;
        if (dfs(j, d)) return true;
        if (budget <= 0) break;
      }
    }
    visited[cell] = 0;
    path.pop();
    return false;
  };

  if (!dfs(start, -1)) return null;
  return { startRow, endCol, cells: path.slice() };
}

function routeToMasks(route: RoutePath, w: number, h: number): Mask[] {
  const masks: Mask[] = new Array(w * h).fill(0);
  const { cells } = route;
  masks[cells[0]] |= 1 << 3; // entrance on the left
  masks[cells[cells.length - 1]] |= 1 << 2; // exit on the bottom
  for (let k = 0; k + 1 < cells.length; k++) {
    const a = cells[k];
    const b = cells[k + 1];
    const dr = Math.floor(b / w) - Math.floor(a / w);
    const dc = (b % w) - (a % w);
    const d = dr === -1 ? 0 : dc === 1 ? 1 : dr === 1 ? 2 : 3;
    masks[a] |= 1 << d;
    masks[b] |= 1 << ((d + 2) & 3);
  }
  return masks;
}

function sameMasks(a: Mask[], b: Mask[]): boolean {
  for (let i = 0; i < a.length; i++) if (a[i] !== b[i]) return false;
  return true;
}

function buildSpec(
  w: number,
  h: number,
  route: RoutePath,
  masks: Mask[],
  clues: Clue[],
): PuzzleSpec {
  const rowCounts = new Array(h).fill(0);
  const colCounts = new Array(w).fill(0);
  for (let i = 0; i < masks.length; i++) {
    if (masks[i]) {
      rowCounts[Math.floor(i / w)]++;
      colCounts[i % w]++;
    }
  }
  return { width: w, height: h, startRow: route.startRow, endCol: route.endCol, rowCounts, colCounts, clues };
}

/** Make a puzzle with exactly one solution. Same seed always gives the same puzzle. */
export function generatePuzzle(width: number, height: number, seed: number, level: Difficulty = 'medium'): Puzzle {
  const { floor: floorShare, logic } = LEVELS[level];
  const w = width;
  const h = height;
  const rng = makeRng(seed);

  for (let attempt = 0; attempt < 200; attempt++) {
    const route = randomRoute(w, h, rng);
    if (!route) continue;
    const truth = routeToMasks(route, w, h);

    // Always show a few pieces so the puzzle is not pure counting.
    const floor = Math.max(level === 'hard' ? 1 : 2, Math.round(w * h * floorShare));
    const clues: Clue[] = [];
    const clued = new Set<number>();
    const trackCells = rng.shuffle(truth.map((m, i) => (m ? i : -1)).filter((i) => i >= 0));
    for (const i of trackCells.slice(0, floor)) {
      clued.add(i);
      clues.push({ r: Math.floor(i / w), c: i % w, mask: truth[i] });
    }

    // 1. Add clues until the solver can only find the intended route.
    let guard = 0;
    let failed = false;
    for (;;) {
      if (guard++ > w * h) {
        failed = true;
        break;
      }
      const spec = buildSpec(w, h, route, truth, clues);
      if (logic) {
        const d = deduce(spec);
        if (d.solved) break;
        const open = d.undecided.filter((i) => truth[i] !== 0 && !clued.has(i));
        const pool = open.length ? open : truth.map((m, i) => (m && !clued.has(i) ? i : -1)).filter((i) => i >= 0);
        if (pool.length === 0) {
          failed = true;
          break;
        }
        const pick = pool[rng.int(pool.length)];
        clued.add(pick);
        clues.push({ r: Math.floor(pick / w), c: pick % w, mask: truth[pick] });
        continue;
      }
      const res = solve(spec, { limit: 2, nodeLimit: SEARCH_NODES });
      if (res.complete && res.solutions.length === 1) break;

      const other = res.solutions.find((s) => !sameMasks(s, truth));
      let candidates: number[] = [];
      if (other) {
        for (let i = 0; i < truth.length; i++) {
          if (truth[i] !== 0 && truth[i] !== other[i] && !clued.has(i)) candidates.push(i);
        }
      }
      if (candidates.length === 0) {
        for (let i = 0; i < truth.length; i++) if (truth[i] !== 0 && !clued.has(i)) candidates.push(i);
      }
      if (candidates.length === 0) {
        failed = true;
        break;
      }
      const pick = candidates[rng.int(candidates.length)];
      clued.add(pick);
      clues.push({ r: Math.floor(pick / w), c: pick % w, mask: truth[pick] });
    }
    if (failed) continue;

    // 2. Drop clues that are not needed, in random order.
    for (const cl of rng.shuffle(clues.slice())) {
      if (clues.length <= floor) break;
      const without = clues.filter((x) => x !== cl);
      const trial = buildSpec(w, h, route, truth, without);
      if (logic ? deduce(trial).solved : isUnique(trial, SEARCH_NODES)) {
        clues.length = 0;
        clues.push(...without);
      }
    }

    const spec = buildSpec(w, h, route, truth, clues);
    return { ...spec, seed, solution: truth };
  }
  throw new Error(`Could not generate a ${w}x${h} puzzle for seed ${seed}`);
}

/** Check a finished grid against the rules (not against the stored answer). */
export function isValidSolution(p: PuzzleSpec, masks: Mask[]): boolean {
  const { width: w, height: h } = p;
  if (masks.length !== w * h) return false;
  const start = p.startRow * w;
  const end = (h - 1) * w + p.endCol;

  for (let r = 0; r < h; r++) {
    let t = 0;
    for (let c = 0; c < w; c++) if (masks[r * w + c]) t++;
    if (t !== p.rowCounts[r]) return false;
  }
  for (let c = 0; c < w; c++) {
    let t = 0;
    for (let r = 0; r < h; r++) if (masks[r * w + c]) t++;
    if (t !== p.colCounts[c]) return false;
  }
  for (const cl of p.clues) if (masks[cl.r * w + cl.c] !== cl.mask) return false;

  // Walk from the entrance; must reach the exit and use every track cell.
  const total = masks.filter((m) => m !== 0).length;
  let cell = start;
  let from = 3; // arrived through the left edge
  let steps = 0;
  const seen = new Set<number>();
  for (;;) {
    if (seen.has(cell)) return false;
    seen.add(cell);
    steps++;
    const m = masks[cell];
    if (m === 0 || (m & (1 << from)) === 0) return false;
    const rest = m & ~(1 << from);
    if (rest === 0 || (rest & (rest - 1)) !== 0) return false; // need exactly one way on
    const d = Math.log2(rest) | 0;
    if (cell === end && d === 2) break;
    const r = Math.floor(cell / w) + DR[d];
    const c = (cell % w) + DC[d];
    if (r < 0 || r >= h || c < 0 || c >= w) return false;
    cell = r * w + c;
    from = (d + 2) & 3;
  }
  return steps === total;
}
