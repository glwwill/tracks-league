import { useRef, type PointerEvent as RPointerEvent, type ReactNode } from 'react';
import type { Mask, PuzzleSpec } from './engine';
import { changeEdge, clueSet, hasEdge, neighbour, type Snapshot } from './game';

const S = 56; // cell size in SVG units
const RAIL = 6; // distance of each rail from the centre line
const TIE = 10; // half length of a tie

// For each direction: [dx, dy] from the cell centre to the middle of that edge.
const MID = [
  [0, -S / 2],
  [S / 2, 0],
  [0, S / 2],
  [-S / 2, 0],
];

/** Draw one piece of track inside a cell whose top-left corner is (ox, oy). */
function Piece({ mask, ox, oy, className }: { mask: Mask; ox: number; oy: number; className: string }) {
  const cx = ox + S / 2;
  const cy = oy + S / 2;
  const dirs = [0, 1, 2, 3].filter((d) => (mask >> d) & 1);
  const els: ReactNode[] = [];

  if (dirs.length === 1) {
    const [d] = dirs;
    const [mx, my] = MID[d];
    const horizontal = d === 1 || d === 3;
    const ex = cx + mx;
    const ey = cy + my;
    if (horizontal) {
      els.push(<line key="r1" className="rail" x1={cx} y1={cy - RAIL} x2={ex} y2={cy - RAIL} />);
      els.push(<line key="r2" className="rail" x1={cx} y1={cy + RAIL} x2={ex} y2={cy + RAIL} />);
      for (let k = 0; k < 2; k++) {
        const tx = cx + (mx * (k + 0.5)) / 2;
        els.push(<line key={'t' + k} className="tie" x1={tx} y1={cy - TIE} x2={tx} y2={cy + TIE} />);
      }
    } else {
      els.push(<line key="r1" className="rail" x1={cx - RAIL} y1={cy} x2={cx - RAIL} y2={ey} />);
      els.push(<line key="r2" className="rail" x1={cx + RAIL} y1={cy} x2={cx + RAIL} y2={ey} />);
      for (let k = 0; k < 2; k++) {
        const ty = cy + (my * (k + 0.5)) / 2;
        els.push(<line key={'t' + k} className="tie" x1={cx - TIE} y1={ty} x2={cx + TIE} y2={ty} />);
      }
    }
  } else if (dirs.length === 2) {
    const [a, b] = dirs;
    if (b - a === 2) {
      if (a === 0) {
        els.push(<line key="r1" className="rail" x1={cx - RAIL} y1={oy} x2={cx - RAIL} y2={oy + S} />);
        els.push(<line key="r2" className="rail" x1={cx + RAIL} y1={oy} x2={cx + RAIL} y2={oy + S} />);
        for (let k = 0; k < 4; k++) {
          const ty = oy + ((k + 0.5) * S) / 4;
          els.push(<line key={'t' + k} className="tie" x1={cx - TIE} y1={ty} x2={cx + TIE} y2={ty} />);
        }
      } else {
        els.push(<line key="r1" className="rail" x1={ox} y1={cy - RAIL} x2={ox + S} y2={cy - RAIL} />);
        els.push(<line key="r2" className="rail" x1={ox} y1={cy + RAIL} x2={ox + S} y2={cy + RAIL} />);
        for (let k = 0; k < 4; k++) {
          const tx = ox + ((k + 0.5) * S) / 4;
          els.push(<line key={'t' + k} className="tie" x1={tx} y1={cy - TIE} x2={tx} y2={cy + TIE} />);
        }
      }
    } else {
      // Curve: the arc is centred on the corner the two edges share.
      const kx = ox + (dirs.includes(1) ? S : 0);
      const ky = oy + (dirs.includes(2) ? S : 0);
      const m1 = [cx + MID[a][0], cy + MID[a][1]];
      const m2 = [cx + MID[b][0], cy + MID[b][1]];
      const t1 = Math.atan2(m1[1] - ky, m1[0] - kx);
      let delta = Math.atan2(m2[1] - ky, m2[0] - kx) - t1;
      while (delta > Math.PI) delta -= 2 * Math.PI;
      while (delta < -Math.PI) delta += 2 * Math.PI;
      const sweep = delta > 0 ? 1 : 0;
      const pt = (th: number, r: number) => `${kx + r * Math.cos(th)} ${ky + r * Math.sin(th)}`;
      for (const [key, r] of [
        ['r1', S / 2 - RAIL],
        ['r2', S / 2 + RAIL],
      ] as const) {
        els.push(
          <path key={key} className="rail" fill="none" d={`M ${pt(t1, r)} A ${r} ${r} 0 0 ${sweep} ${pt(t1 + delta, r)}`} />,
        );
      }
      for (let k = 0; k < 3; k++) {
        const th = t1 + (delta * (k + 0.5)) / 3;
        const [x1, y1] = pt(th, S / 2 - TIE).split(' ');
        const [x2, y2] = pt(th, S / 2 + TIE).split(' ');
        els.push(<line key={'t' + k} className="tie" x1={x1} y1={y1} x2={x2} y2={y2} />);
      }
    }
  }
  return <g className={className}>{els}</g>;
}

interface BoardProps {
  puzzle: PuzzleSpec;
  state: Snapshot;
  mistakes: Set<number>;
  solved: boolean;
  /** Called before the first change of each gesture so Undo can step back one gesture. */
  onChange: (next: Snapshot) => void;
  onBeginGesture: () => void;
}

export default function Board({ puzzle, state, mistakes, solved, onChange, onBeginGesture }: BoardProps) {
  const { width: w, height: h } = puzzle;
  const svgRef = useRef<SVGSVGElement>(null);
  const locked = clueSet(puzzle);
  const drag = useRef<{ last: number; mode: 'add' | 'remove' | null; moved: boolean; began: boolean } | null>(null);
  const live = useRef(state);
  live.current = state;

  const cellAt = (e: RPointerEvent): number => {
    const svg = svgRef.current;
    if (!svg) return -1;
    const rect = svg.getBoundingClientRect();
    const x = ((e.clientX - rect.left) / rect.width) * (w + 2) * S - S;
    const y = ((e.clientY - rect.top) / rect.height) * (h + 2) * S - S;
    const c = Math.floor(x / S);
    const r = Math.floor(y / S);
    if (r < 0 || r >= h || c < 0 || c >= w) return -1;
    return r * w + c;
  };

  const begin = () => {
    if (drag.current && !drag.current.began) {
      drag.current.began = true;
      onBeginGesture();
    }
  };

  const onDown = (e: RPointerEvent<SVGSVGElement>) => {
    if (solved) return;
    const i = cellAt(e);
    if (i < 0) return;
    e.currentTarget.setPointerCapture(e.pointerId);
    drag.current = { last: i, mode: null, moved: false, began: false };
  };

  const onMove = (e: RPointerEvent<SVGSVGElement>) => {
    const g = drag.current;
    if (!g) return;
    const i = cellAt(e);
    if (i < 0 || i === g.last) return;
    g.moved = true;
    const dr = Math.floor(i / w) - Math.floor(g.last / w);
    const dc = (i % w) - (g.last % w);
    const d = dr === -1 && dc === 0 ? 0 : dr === 0 && dc === 1 ? 1 : dr === 1 && dc === 0 ? 2 : dr === 0 && dc === -1 ? 3 : -1;
    const j = d >= 0 ? neighbour(puzzle, g.last, d) : -1;
    // Given pieces never change, so they must not decide whether this drag draws or erases.
    if (d >= 0 && j >= 0 && !locked.has(g.last) && !locked.has(j)) {
      const cur = live.current;
      if (g.mode === null) g.mode = hasEdge(cur.masks, g.last, d) ? 'remove' : 'add';
      const next = changeEdge(puzzle, locked, cur.masks, g.last, d, g.mode);
      if (next) {
        begin();
        const marks = cur.marks.slice();
        marks[g.last] = false;
        marks[i] = false;
        const snap = { masks: next, marks };
        live.current = snap;
        onChange(snap);
      }
    }
    g.last = i;
  };

  const onUp = () => {
    const g = drag.current;
    drag.current = null;
    if (!g || g.moved || solved) return;
    // A tap: put an X on an empty cell, or take it off again.
    const cur = live.current;
    if (locked.has(g.last) || cur.masks[g.last] !== 0) return;
    drag.current = g;
    begin();
    drag.current = null;
    const marks = cur.marks.slice();
    marks[g.last] = !marks[g.last];
    onChange({ masks: cur.masks, marks });
  };

  const rowNow = (r: number) => state.masks.slice(r * w, r * w + w).filter(Boolean).length;
  const colNow = (c: number) => state.masks.filter((m, i) => m && i % w === c).length;
  const countClass = (now: number, target: number) => (now > target ? 'count over' : now === target ? 'count done' : 'count');

  const start = puzzle.startRow;
  const endCol = puzzle.endCol;

  return (
    <svg
      ref={svgRef}
      className={'board' + (solved ? ' solved' : '')}
      viewBox={`0 0 ${(w + 2) * S} ${(h + 2) * S}`}
      role="img"
      aria-label={`Train track puzzle, ${w} by ${h}`}
      onPointerDown={onDown}
      onPointerMove={onMove}
      onPointerUp={onUp}
      onPointerCancel={() => (drag.current = null)}
    >
      {/* cells */}
      {Array.from({ length: w * h }, (_, i) => {
        const r = Math.floor(i / w);
        const c = i % w;
        const cls = 'cell' + (locked.has(i) ? ' clue' : '') + (mistakes.has(i) ? ' wrong' : '');
        return <rect key={i} className={cls} x={S + c * S} y={S + r * S} width={S} height={S} />;
      })}

      {/* entrance and exit stubs sit outside the grid */}
      <Piece mask={1 << 1} ox={0} oy={S + start * S} className="track fixed" />
      <Piece mask={1 << 0} ox={S + endCol * S} oy={S + h * S} className="track fixed" />

      {/* track */}
      {state.masks.map((m, i) =>
        m ? (
          <Piece
            key={i}
            mask={m}
            ox={S + (i % w) * S}
            oy={S + Math.floor(i / w) * S}
            className={'track' + (locked.has(i) ? ' fixed' : '')}
          />
        ) : null,
      )}

      {/* X marks */}
      {state.marks.map((on, i) =>
        on ? (
          <g key={i} className="xmark">
            <line x1={S + (i % w) * S + 18} y1={S + Math.floor(i / w) * S + 18} x2={S + (i % w) * S + S - 18} y2={S + Math.floor(i / w) * S + S - 18} />
            <line x1={S + (i % w) * S + S - 18} y1={S + Math.floor(i / w) * S + 18} x2={S + (i % w) * S + 18} y2={S + Math.floor(i / w) * S + S - 18} />
          </g>
        ) : null,
      )}

      {/* grid border */}
      <rect className="frame" x={S} y={S} width={w * S} height={h * S} />

      {/* labels */}
      <g className="badge">
        <circle cx={S / 2} cy={S + start * S + S / 2} r={14} />
        <text x={S / 2} y={S + start * S + S / 2}>A</text>
        <circle cx={S + endCol * S + S / 2} cy={S + h * S + S / 2} r={14} />
        <text x={S + endCol * S + S / 2} y={S + h * S + S / 2}>B</text>
      </g>

      {puzzle.colCounts.map((t, c) => (
        <text key={'c' + c} className={countClass(colNow(c), t)} x={S + c * S + S / 2} y={S / 2}>
          {t}
        </text>
      ))}
      {puzzle.rowCounts.map((t, r) => (
        <text key={'r' + r} className={countClass(rowNow(r), t)} x={S + w * S + S / 2} y={S + r * S + S / 2}>
          {t}
        </text>
      ))}
    </svg>
  );
}
