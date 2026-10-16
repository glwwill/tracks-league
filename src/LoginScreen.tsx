import { useEffect, useRef, useState } from 'react';
import { api, type PublicUser } from './api';

interface GoogleId {
  initialize(o: { client_id: string; callback: (r: { credential: string }) => void }): void;
  renderButton(el: HTMLElement, o: Record<string, string>): void;
}
declare global {
  interface Window {
    google?: { accounts: { id: GoogleId } };
  }
}

export default function LoginScreen({
  config,
  onLogin,
}: {
  config: { googleClientId: string; devLogin: boolean };
  onLogin: (u: PublicUser) => void;
}) {
  const btn = useRef<HTMLDivElement>(null);
  const [error, setError] = useState('');
  const [email, setEmail] = useState('');

  useEffect(() => {
    if (!config.googleClientId) return;
    const s = document.createElement('script');
    s.src = 'https://accounts.google.com/gsi/client';
    s.async = true;
    s.onload = () => {
      const g = window.google?.accounts.id;
      if (!g || !btn.current) return;
      g.initialize({
        client_id: config.googleClientId,
        callback: (r) => {
          api
            .googleLogin(r.credential)
            .then((res) => onLogin(res.user))
            .catch((e: Error) => setError(e.message));
        },
      });
      g.renderButton(btn.current, { theme: 'outline', size: 'large', text: 'signin_with', shape: 'pill' });
    };
    document.head.appendChild(s);
    return () => s.remove();
  }, [config.googleClientId, onLogin]);

  const devSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    api
      .devLogin(email)
      .then((res) => onLogin(res.user))
      .catch((err: Error) => setError(err.message));
  };

  return (
    <main className="app">
      <header>
        <h1>Tracks League</h1>
        <p className="sub">
          Solve train track puzzles, climb from easy to hard, and compete on the leaderboard. Sign in to keep your score.
        </p>
      </header>

      <section className="card">
        {config.googleClientId ? (
          <div ref={btn} className="gbtn" />
        ) : (
          <p className="message">Google sign-in is not set up yet. Add GOOGLE_CLIENT_ID to your .env file (steps are in README.md).</p>
        )}
        <p className="hint">We only keep your email, your first name and last initial, and your score. No password is stored.</p>
      </section>

      {config.devLogin && (
        <form className="card" onSubmit={devSubmit}>
          <strong>Test sign-in (development only)</strong>
          <p className="hint">Sign in with any email while Google is not set up.</p>
          <div className="printform">
            <label>
              Email
              <input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} style={{ width: 240 }} />
            </label>
            <button className="primary" type="submit">Sign in</button>
          </div>
        </form>
      )}

      {error && <p className="message err">{error}</p>}
    </main>
  );
}
