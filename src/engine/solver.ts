import { DC, DR, type Mask, type PuzzleSpec } from './types';

export interface SolveResult {
  /** Solutions found (up to the limit), each one mask per cell. */
  solutions: Mask[][];
  /** false if the search was cut off by the node limit, so the count may be incomplete. */
  complete: boolean;
}

interface Ctx {
  w: number;
  h: number;
  n: number;
  /** neighbour cell index for (cell, dir), or -1 off the grid */
  nb: Int16Array;
  start: number;
  end: number;
  rowCounts: number[];
  colCounts: number[];
}

interface State {
  /** -1 unknown, 0 empty, 1 track */
  C: Int8Array;
  /** per cell and direction: -1 unknown, 0 no track across that edge, 1 track. Each edge is stored on both sides. */
  E: Int8Array;
}

function buildCtx(p: PuzzleSpec): Ctx {
  const { width: w, height: h } = p;
  const n = w * h;
  const nb = new Int16Array(n * 4).fill(-1);
  for (let r = 0; r < h; r++) {
    for (let c = 0; c < w; c++) {
      for (let d = 0; d < 4; d++) {
        const rr = r + DR[d];
        const cc = c + DC[d];
        if (rr >= 0 && rr < h && cc >= 0 && cc < w) nb[(r * w + c) * 4 + d] = rr * w + cc;
      }
    }
  }
  return {
    w,
    h,
    n,
    nb,
    start: p.startRow * w,
    end: (h - 1) * w + p.endCol,
    rowCounts: p.rowCounts,
    colCounts: p.colCounts,
  };
}

/** Returns 1 if changed, 0 if already that value, -1 on conflict. */
function setE(ctx: Ctx, st: State, i: number, d: number, v: number): number {
  const k = i * 4 + d;
  const cur = st.E[k];
  if (cur === v) return 0;
  if (cur !== -1) return -1;
  st.E[k] = v;
  const j = ctx.nb[k];
  if (j >= 0) st.E[j * 4 + ((d + 2) & 3)] = v;
  return 1;
}

function propagate(ctx: Ctx, st: State): boolean {
  const { n, w, h, nb } = ctx;
  const { C, E } = st;

  for (;;) {
    let changed = true;
    while (changed) {
      changed = false;

      for (let i = 0; i < n; i++) {
        let o = 0;
        let u = 0;
        const b = i * 4;
        for (let d = 0; d < 4; d++) {
          const e = E[b + d];
          if (e === 1) o++;
          else if (e === -1) u++;
        }
        let c = C[i];
        if (c === 0) {
          if (o > 0) return false;
          if (u > 0) {
            for (let d = 0; d < 4; d++) if (E[b + d] === -1) setE(ctx, st, i, d, 0);
            changed = true;
          }
          continue;
        }
        if (o > 2) return false;
        if (c === -1) {
          if (o >= 1) {
            C[i] = 1;
            c = 1;
            changed = true;
          } else if (u < 2) {
            C[i] = 0;
            for (let d = 0; d < 4; d++) if (E[b + d] === -1) setE(ctx, st, i, d, 0);
            changed = true;
            continue;
          }
        }
        if (c === 1) {
          if (o + u < 2) return false;
          if (o === 2) {
            if (u > 0) {
              for (let d = 0; d < 4; d++) if (E[b + d] === -1) setE(ctx, st, i, d, 0);
              changed = true;
            }
          } else if (o + u === 2 && u > 0) {
            for (let d = 0; d < 4; d++) if (E[b + d] === -1) setE(ctx, st, i, d, 1);
            changed = true;
          }
        }
      }

      for (let r = 0; r < h; r++) {
        let t = 0;
        let u = 0;
        for (let c = 0; c < w; c++) {
          const v = C[r * w + c];
          if (v === 1) t++;
          else if (v === -1) u++;
        }
        const T = ctx.rowCounts[r];
        if (t > T || t + u < T) return false;
        if (u > 0 && (t === T || t + u === T)) {
          const val = t === T ? 0 : 1;
          for (let c = 0; c < w; c++) if (C[r * w + c] === -1) C[r * w + c] = val;
          changed = true;
        }
      }
      for (let c = 0; c < w; c++) {
        let t = 0;
        let u = 0;
        for (let r = 0; r < h; r++) {
          const v = C[r * w + c];
          if (v === 1) t++;
          else if (v === -1) u++;
        }
        const T = ctx.colCounts[c];
        if (t > T || t + u < T) return false;
        if (u > 0 && (t === T || t + u === T)) {
          const val = t === T ? 0 : 1;
          for (let r = 0; r < h; r++) if (C[r * w + c] === -1) C[r * w + c] = val;
          changed = true;
        }
      }
    }

    // Loop and completeness checks on the connected pieces so far.
    const comp = new Int16Array(n).fill(-1);
    let nComp = 0;
    const cellsIn: number[] = [];
    const edgesIn: number[] = [];
    const stack: number[] = [];
    for (let s = 0; s < n; s++) {
      if (comp[s] !== -1 || C[s] !== 1) continue;
      let cells = 0;
      let ends = 0;
      comp[s] = nComp;
      stack.push(s);
      while (stack.length) {
        const i = stack.pop() as number;
        cells++;
        for (let d = 0; d < 4; d++) {
          if (E[i * 4 + d] !== 1) continue;
          const j = nb[i * 4 + d];
          if (j < 0) continue;
          ends++;
          if (comp[j] === -1) {
            comp[j] = nComp;
            stack.push(j);
          }
        }
      }
      cellsIn.push(cells);
      edgesIn.push(ends / 2);
      nComp++;
    }
    for (let k = 0; k < nComp; k++) if (edgesIn[k] >= cellsIn[k]) return false; // a closed loop

    const cs = comp[ctx.start];
    const ce = comp[ctx.end];
    if (cs !== -1 && cs === ce) {
      // The whole route is already connected: nothing else may be track.
      let any = false;
      for (let i = 0; i < n; i++) {
        if (comp[i] === cs) continue;
        if (C[i] === 1) return false;
        if (C[i] === -1) {
          C[i] = 0;
          any = true;
        }
      }
      if (any) continue;
    }
    return true;
  }
}

export interface SolveOptions {
  limit?: number;
  nodeLimit?: number;
}

function initState(p: PuzzleSpec): { ctx: Ctx; root: State; ok: boolean } {
  const ctx = buildCtx(p);
  const { n, w, h } = ctx;
  const root: State = { C: new Int8Array(n).fill(-1), E: new Int8Array(n * 4).fill(-1) };
  let ok = true;

  // Outer border: closed everywhere except the entrance and the exit.
  for (let r = 0; r < h && ok; r++) {
    for (let c = 0; c < w && ok; c++) {
      const i = r * w + c;
      for (let d = 0; d < 4; d++) {
        if (ctx.nb[i * 4 + d] !== -1) continue;
        let v = 0;
        if (i === ctx.start && d === 3) v = 1;
        if (i === ctx.end && d === 2) v = 1;
        if (setE(ctx, root, i, d, v) < 0) ok = false;
      }
    }
  }
  for (const cl of p.clues) {
    const i = cl.r * w + cl.c;
    root.C[i] = 1;
    for (let d = 0; d < 4; d++) {
      if (setE(ctx, root, i, d, (cl.mask >> d) & 1) < 0) ok = false;
    }
  }
  return { ctx, root, ok };
}

export interface DeduceResult {
  /** true if plain deduction (no guessing) finishes the puzzle */
  solved: boolean;
  /** cells still undecided when deduction stalls */
  undecided: number[];
}

/** Solve using only deduction. This is what a player can do without trial and error. */
export function deduce(p: PuzzleSpec): DeduceResult {
  const { ctx, root, ok } = initState(p);
  if (!ok || !propagate(ctx, root)) return { solved: false, undecided: [] };
  const undecided: number[] = [];
  let open = false;
  for (let i = 0; i < ctx.n; i++) {
    if (root.C[i] === -1) undecided.push(i);
    for (let d = 0; d < 4; d++) if (root.E[i * 4 + d] === -1) open = true;
  }
  return { solved: undecided.length === 0 && !open, undecided };
}

export function solve(p: PuzzleSpec, opts: SolveOptions = {}): SolveResult {
  const limit = opts.limit ?? 2;
  const nodeLimit = opts.nodeLimit ?? 200000;
  const { ctx, root, ok } = initState(p);
  const { n } = ctx;

  const solutions: Mask[][] = [];
  let nodes = 0;
  let aborted = false;
  if (!ok) return { solutions, complete: true };

  const recurse = (st: State): void => {
    if (aborted || solutions.length >= limit) return;
    if (++nodes > nodeLimit) {
      aborted = true;
      return;
    }
    if (!propagate(ctx, st)) return;
    const { C, E } = st;

    // Prefer to extend a track cell that still needs connections.
    let bi = -1;
    let bd = -1;
    let bestU = 9;
    for (let i = 0; i < n; i++) {
      if (C[i] !== 1) continue;
      let u = 0;
      let fd = -1;
      for (let d = 0; d < 4; d++) {
        if (E[i * 4 + d] === -1) {
          u++;
          if (fd < 0) fd = d;
        }
      }
      if (u > 0 && u < bestU) {
        bestU = u;
        bi = i;
        bd = fd;
      }
    }
    if (bi < 0) {
      for (let i = 0; i < n && bi < 0; i++) {
        for (let d = 0; d < 4; d++) {
          if (E[i * 4 + d] === -1) {
            bi = i;
            bd = d;
            break;
          }
        }
      }
    }

    if (bi < 0) {
      const masks: Mask[] = new Array(n).fill(0);
      for (let i = 0; i < n; i++) {
        let m = 0;
        for (let d = 0; d < 4; d++) if (E[i * 4 + d] === 1) m |= 1 << d;
        masks[i] = m;
      }
      solutions.push(masks);
      return;
    }

    for (const v of [1, 0]) {
      const next: State = { C: st.C.slice(), E: st.E.slice() };
      if (setE(ctx, next, bi, bd, v) < 0) continue;
      recurse(next);
      if (aborted || solutions.length >= limit) return;
    }
  };

  recurse(root);
  return { solutions, complete: !aborted };
}

/** True only if the puzzle has exactly one solution (and the search finished). */
export function isUnique(p: PuzzleSpec, nodeLimit = 200000): boolean {
  const res = solve(p, { limit: 2, nodeLimit });
  return res.complete && res.solutions.length === 1;
}
