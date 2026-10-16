import { useEffect, useState } from 'react';
import { api, type LeaderRow } from './api';

export default function Leaderboard() {
  const [data, setData] = useState<{ me: number; rows: LeaderRow[] } | null>(null);
  const [error, setError] = useState('');

  useEffect(() => {
    api.leaderboard().then(setData).catch((e: Error) => setError(e.message));
  }, []);

  if (error) return <p className="message err">{error}</p>;
  if (!data) return <p className="message">Loading…</p>;
  if (data.rows.length === 0) return <p className="message">No scores yet. Solve a puzzle to be first.</p>;

  return (
    <table className="board-table">
      <thead>
        <tr><th>#</th><th>Player</th><th className="num">Score</th><th className="num">Solved</th></tr>
      </thead>
      <tbody>
        {data.rows.map((r) => (
          <tr key={r.id} className={r.id === data.me ? 'me' : ''}>
            <td>{r.rank}</td>
            <td>{r.name}</td>
            <td className="num">{r.score}</td>
            <td className="num">{r.games}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
