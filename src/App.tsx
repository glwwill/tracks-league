import { useEffect, useState } from 'react';
import { api, ApiError, type PublicUser } from './api';
import GameScreen from './GameScreen';
import Leaderboard from './Leaderboard';
import LoginScreen from './LoginScreen';
import { isSoundOn, setSoundOn, sfx } from './sound';

type Tab = 'play' | 'board';

export default function App() {
  const [user, setUser] = useState<PublicUser | null | undefined>(undefined); // undefined = still checking
  const [config, setConfig] = useState<{ googleClientId: string; devLogin: boolean } | null>(null);
  const [tab, setTab] = useState<Tab>('play');
  const [fatal, setFatal] = useState('');
  const [sound, setSound] = useState(isSoundOn());

  const toggleSound = () => {
    const next = !sound;
    setSoundOn(next);
    setSound(next);
    if (next) sfx.soundOn();
  };

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
        <button className="pill sound" aria-pressed={sound} onClick={toggleSound} title="Turn sound effects on or off">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <path d="M11 5 6 9H3v6h3l5 4V5z" />
            {sound ? <path d="M15.5 8.5a5 5 0 0 1 0 7M18.5 5.5a9 9 0 0 1 0 13" /> : <path d="M16 9l5 6M21 9l-5 6" />}
          </svg>
          {sound ? 'Sound on' : 'Sound off'}
        </button>
      </nav>

      {tab === 'play' ? <GameScreen onUserChange={setUser} /> : <Leaderboard />}
    </main>
  );
}
