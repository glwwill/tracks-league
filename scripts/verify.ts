// Checks the solver against brute-force path enumeration, then checks generated puzzles.
import { generatePuzzle, isValidSolution, solve, makeRng, deduce, DC, DR, DIFFICULTIES, type PuzzleSpec, type Mask } from '../src/engine';

/** Count every simple route from the entrance to the exit that fits the counts and clues. */
function bruteCount(p: PuzzleSpec): number {
  const { width: w, height: h } = p;
  const start = p.startRow * w;
  const end = (h - 1) * w + p.endCol;
  const masks: Mask[] = new Array(w * h).fill(0);
  const visited = new Array(w * h).fill(false);
  const rowN = new Array(h).fill(0);
  const colN = new Array(w).fill(0);
  let found = 0;

  const check = () => {
    for (let r = 0; r < h; r++) if (rowN[r] !== p.rowCounts[r]) return false;
    for (let c = 0; c < w; c++) if (colN[c] !== p.colCounts[c]) return false;
    for (const cl of p.clues) if (masks[cl.r * w + cl.c] !== cl.mask) return false;
    return true;
  };

  const go = (cell: number) => {
    // cell is already marked, masks[cell] holds its entry edge
    const r = Math.floor(cell / w);
    const c = cell % w;
    if (cell === end) {
      masks[cell] |= 1 << 2;
      if (check()) found++;
      masks[cell] &= ~(1 << 2);
      return;
    }
    for (let d = 0; d < 4; d++) {
      const rr = r + DR[d];
      const cc = c + DC[d];
      if (rr < 0 || rr >= h || cc < 0 || cc >= w) continue;
      const j = rr * w + cc;
      if (visited[j]) continue;
      if (rowN[rr] >= p.rowCounts[rr] || colN[cc] >= p.colCounts[cc]) continue;
      visited[j] = true;
      rowN[rr]++;
      colN[cc]++;
      masks[cell] |= 1 << d;
      masks[j] |= 1 << ((d + 2) & 3);
      go(j);
      masks[cell] &= ~(1 << d);
      masks[j] &= ~(1 << ((d + 2) & 3));
      rowN[rr]--;
      colN[cc]--;
      visited[j] = false;
    }
  };

  if (p.rowCounts[p.startRow] < 1 || p.colCounts[0] < 1) return 0;
  visited[start] = true;
  rowN[p.startRow]++;
  colN[0]++;
  masks[start] |= 1 << 3;
  go(start);
  return found;
}

let bad = 0;

// Part 1: random specs (often with many solutions), solver count must equal brute-force count.
console.log('Part 1: solver vs brute force on random specs');
const rng = makeRng(12345);
let compared = 0;
for (let t = 0; t < 400; t++) {
  const w = 3 + rng.int(3);
  const h = 3 + rng.int(3);
  const base = generatePuzzle(Math.max(5, w), Math.max(5, h), 1000 + t);
  // reuse its route and counts but pick a random subset of its solution cells as clues
  const clues = [];
  const k = rng.int(4);
  const cells = base.solution.map((m, i) => (m ? i : -1)).filter((i) => i >= 0);
  rng.shuffle(cells);
  for (let q = 0; q < k && q < cells.length; q++) {
    const i = cells[q];
    clues.push({ r: Math.floor(i / base.width), c: i % base.width, mask: base.solution[i] });
  }
  const spec: PuzzleSpec = { ...base, clues };
  const expected = bruteCount(spec);
  const got = solve(spec, { limit: 1e9, nodeLimit: 1e9 });
  compared++;
  if (!got.complete || got.solutions.length !== expected) {
    bad++;
    console.log('  MISMATCH', base.width, base.height, 'seed', base.seed, 'brute', expected, 'solver', got.solutions.length);
  }
  for (const s of got.solutions) if (!isValidSolution(spec, s)) { bad++; console.log('  invalid solution returned'); }
}
console.log(`  compared ${compared} specs`);

// Part 2: generated puzzles are unique per brute force, and easy/medium need no guessing.
console.log('Part 2: generated puzzles have exactly one solution (brute force, 5x5 to 6x6)');
let uniq = 0;
let total = 0;
for (const level of DIFFICULTIES) {
  for (let t = 0; t < 40; t++) {
    const size = 5 + (t % 2);
    const p = generatePuzzle(size, size, 5000 + t, level);
    total++;
    const n = bruteCount(p);
    if (n !== 1) { bad++; console.log('  NOT UNIQUE', level, size, p.seed, n); } else uniq++;
    if (!isValidSolution(p, p.solution)) { bad++; console.log('  stored solution invalid'); }
    if (level !== 'hard' && !deduce(p).solved) { bad++; console.log('  needs guessing', level, size, p.seed); }
  }
}
console.log(`  ${uniq}/${total} unique`);

console.log(bad === 0 ? 'ALL GOOD' : `${bad} PROBLEMS`);
process.exit(bad === 0 ? 0 : 1);
