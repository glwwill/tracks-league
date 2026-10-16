import { useCallback, useEffect, useMemo, useState } from 'react';
import { api, type GameInfo, type PublicUser } from './api';
import Board from './Board';
import { isValidSolution } from './engine';
import { initialSnapshot, type Snapshot } from './game';

const LEVEL_LABEL = { easy: 'Easy', medium: 'Medium', hard: 'Hard' };

export default function GameScreen({ onUserChange }: { onUserChange: (u: PublicUser) => void }) {
  const [game, setGame] = useState<GameInfo | null>(null);
  const [snap, setSnap] = useState<Snapshot | null>(null);
  const [past, setPast] = useState<Snapshot[]>([]);
  const [earned, setEarned] = useState<number | null>(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const show = useCallback((g: GameInfo) => {
    setGame(g);
    setSnap(initialSnapshot(g.puzzle));
    setPast([]);
    setEarned(null);
    setError('');
  }, []);

  useEffect(() => {
    api.current().then(show).catch((e: Error) => setError(e.message));
  }, [show]);

  const solved = useMemo(() => (game && snap ? isValidSolution(game.puzzle, snap.masks) : false), [game, snap]);

  // The moment the grid is a valid solution, send it to the server to be checked and scored.
  useEffect(() => {
    if (!solved || !game || !snap || earned !== null || busy) return;
    setBusy(true);
    api
      .submit(game.gameId, snap.masks)
      .then((r) => {
        if (r.ok && r.user) {
          setEarned(r.points ?? 0);
          onUserChange(r.user);
        } else {
          setError('The server did not accept that solution. Try again or skip.');
        }
      })
      .catch((e: Error) => setError(e.message))
      .finally(() => setBusy(false));
  }, [solved, game, snap, earned, busy, onUserChange]);

  const beginGesture = useCallback(() => {
    if (snap) setPast((p) => [...p.slice(-199), snap]);
  }, [snap]);

  const undo = () => {
    setPast((p) => {
      if (!p.length) return p;
      setSnap(p[p.length - 1]);
      return p.slice(0, -1);
    });
  };

  const reset = () => {
    if (!game || !snap) return;
    setPast((p) => [...p, snap]);
    setSnap(initialSnapshot(game.puzzle));
  };

  const next = () => {
    setGame(null);
    api.current().then(show).catch((e: Error) => setError(e.message));
  };

  const skip = () => {
    setGame(null);
    api.skip().then(show).catch((e: Error) => setError(e.message));
  };

  if (error && !game) return <p className="message err">{error}</p>;
  if (!game || !snap) return <p className="message">Loading puzzle…</p>;

  return (
    <>
      <div className="chips">
        <span className="chip">
          {game.inLadder ? `Ladder ${game.step} of ${game.ladderSteps}` : `Bonus puzzle ${game.step - game.ladderSteps}`}
        </span>
        <span className="chip">{game.size}×{game.size}</span>
        <span className="chip level">{LEVEL_LABEL[game.level]}</span>
        <span className="chip pts">{game.points} pts</span>
      </div>

      <Board
        puzzle={game.puzzle}
        state={snap}
        mistakes={new Set()}
        solved={solved}
        onChange={setSnap}
        onBeginGesture={beginGesture}
      />

      <p className={'message' + (earned !== null ? ' win' : '') + (error ? ' err' : '')} role="status">
        {earned !== null ? `Solved. +${earned} points.` : error || (busy ? 'Checking…' : ' ')}
      </p>

      <div className="actions">
        <button onClick={undo} disabled={past.length === 0 || earned !== null}>Undo</button>
        <button onClick={reset} disabled={earned !== null}>Reset</button>
        <button onClick={skip} disabled={earned !== null}>Skip puzzle</button>
        {earned !== null && <button className="primary" onClick={next}>Next puzzle</button>}
      </div>

      <details className="how">
        <summary>How to play</summary>
        <ul>
          <li>Drag from square to square to lay track. Drag over existing track to erase it.</li>
          <li>Tap an empty square to mark it with an X (no track there).</li>
          <li>One track from A to B. It never branches or crosses itself, and it does not have to fill the grid.</li>
          <li>Numbers show how many squares in each row and column the track uses.</li>
          <li>Shaded squares are given. Points: Easy 10, Medium 25, Hard 50. Skipping scores nothing and gives a new puzzle at the same step.</li>
        </ul>
      </details>
    </>
  );
}
