import { useEffect, useState } from 'react';
import { api, ApiError, type PublicUser } from './api';
import GameScreen from './GameScreen';
import Leaderboard from './Leaderboard';
import LoginScreen from './LoginScreen';

type Tab = 'play' | 'board';

export default function App() {
  const [user, setUser] = useState<PublicUser | null | undefined>(undefined); // undefined = still checking
  const [config, setConfig] = useState<{ googleClientId: string; devLogin: boolean } | null>(null);
  const [tab, setTab] = useState<Tab>('play');
  const [fatal, setFatal] = useState('');

  useEffect(() => {
    api.config().then(setConfig).catch(() => setFatal('Cannot reach the server. Is "npm run dev" running?'));
    api
      .me()
      .then((r) => setUser(r.user))
      .catch((e) => {
        if (e instanceof ApiError && e.status === 401) setUser(null);
        else setFatal('Cannot reach the server. Is "npm run dev" running?');
      });
  }, []);

  const logout = async () => {
    await api.logout().catch(() => undefined);
    setUser(null);
    setTab('play');
  };

  if (fatal) return <main className="app"><p className="message">{fatal}</p></main>;
  if (user === undefined || !config) return <main className="app"><p className="message">Loading…</p></main>;
  if (user === null) return <LoginScreen config={config} onLogin={setUser} />;

  return (
    <main className="app">
      <header className="topbar">
        <h1>Tracks League</h1>
        <div className="who">
          <span className="name">{user.name}</span>
          <span className="stat">{user.totalScore} pts</span>
          <span className="stat">{user.gamesPlayed} solved</span>
          <button className="link" onClick={logout}>Sign out</button>
        </div>
      </header>

      <nav className="tabs" role="tablist">
        <button role="tab" aria-selected={tab === 'play'} className={tab === 'play' ? 'pill on' : 'pill'} onClick={() => setTab('play')}>Play</button>
        <button role="tab" aria-selected={tab === 'board'} className={tab === 'board' ? 'pill on' : 'pill'} onClick={() => setTab('board')}>Leaderboard</button>
      </nav>

      {tab === 'play' ? <GameScreen onUserChange={setUser} /> : <Leaderboard />}
    </main>
  );
}
