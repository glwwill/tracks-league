import { existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import cookieParser from 'cookie-parser';
import express, { type NextFunction, type Request, type Response } from 'express';
import { OAuth2Client } from 'google-auth-library';
import jwt from 'jsonwebtoken';
import { generatePuzzle, isValidSolution, type Puzzle } from '../src/engine';
import { db, type GameRow, type UserRow } from './db';
import { env } from './env';
import { assignmentFor, LADDER } from './ladder';

const COOKIE = 'tl_session';
const app = express();
app.use(express.json({ limit: '20kb' }));
app.use(cookieParser());

interface Authed extends Request {
  user?: UserRow;
}

const google = env.googleClientId ? new OAuth2Client(env.googleClientId) : null;

// ------------------------------------------------------------------ helpers

const wrap =
  (fn: (req: Authed, res: Response) => Promise<unknown>) =>
  (req: Request, res: Response, next: NextFunction) =>
    fn(req, res).catch(next);

const publicUser = (u: UserRow) => ({
  id: u.id,
  name: u.display_name,
  picture: u.picture_url,
  gamesPlayed: u.games_played,
  totalScore: u.total_score,
});

function startSession(res: Response, userId: number) {
  const token = jwt.sign({ uid: userId }, env.jwtSecret, { expiresIn: '30d' });
  res.cookie(COOKIE, token, {
    httpOnly: true,
    sameSite: 'lax',
    secure: env.production,
    maxAge: 30 * 24 * 3600 * 1000,
  });
}

/** "Gary Smith" becomes "Gary S." so the public board never shows full names or emails. */
function shortName(given: string | undefined, family: string | undefined, email: string): string {
  const first = (given || email.split('@')[0]).slice(0, 40);
  return family ? `${first} ${family[0].toUpperCase()}.` : first;
}

async function auth(req: Authed, res: Response, next: NextFunction) {
  try {
    const token = req.cookies?.[COOKIE];
    if (!token) return res.status(401).json({ error: 'Not signed in' });
    const { uid } = jwt.verify(token, env.jwtSecret) as { uid: number };
    const user = await db.getUser(uid);
    if (!user) return res.status(401).json({ error: 'Not signed in' });
    req.user = user;
    next();
  } catch {
    res.status(401).json({ error: 'Not signed in' });
  }
}

// Regenerating a 10x10 puzzle takes a moment, so keep the recent ones.
const puzzleCache = new Map<number, Puzzle>();
function puzzleFor(g: GameRow): Puzzle {
  let p = puzzleCache.get(g.id);
  if (!p) {
    p = generatePuzzle(g.size, g.size, g.seed, g.level);
    puzzleCache.set(g.id, p);
    if (puzzleCache.size > 200) puzzleCache.delete(puzzleCache.keys().next().value as number);
  }
  return p;
}

function gamePayload(g: GameRow) {
  const { solution: _answer, ...puzzle } = puzzleFor(g); // never send the answer
  void _answer;
  return {
    gameId: g.id,
    step: g.step,
    ladderSteps: LADDER.length,
    inLadder: g.step <= LADDER.length,
    size: g.size,
    level: g.level,
    points: g.points,
    puzzle,
  };
}

async function currentGame(user: UserRow): Promise<GameRow> {
  const open = await db.getActiveGame(user.id);
  if (open) return open;
  return db.createGame(user.id, assignmentFor(user.games_played + 1));
}

// --------------------------------------------------------------------- routes

app.get('/api/config', (_req, res) => {
  res.json({ googleClientId: env.googleClientId, devLogin: env.devLogin });
});

app.post(
  '/api/auth/google',
  wrap(async (req, res) => {
    if (!google) return res.status(503).json({ error: 'Google sign-in is not set up yet (GOOGLE_CLIENT_ID missing in .env).' });
    const credential = String(req.body?.credential || '');
    const ticket = await google.verifyIdToken({ idToken: credential, audience: env.googleClientId });
    const p = ticket.getPayload();
    if (!p?.email || !p.email_verified) return res.status(401).json({ error: 'Google account has no verified email.' });
    const user = await db.upsertUser({
      email: p.email.toLowerCase(),
      sub: p.sub ?? null,
      name: shortName(p.given_name, p.family_name, p.email),
      picture: p.picture ?? null,
    });
    startSession(res, user.id);
    res.json({ user: publicUser(user) });
  }),
);

app.post(
  '/api/auth/dev',
  wrap(async (req, res) => {
    if (!env.devLogin || env.production) return res.status(404).json({ error: 'Not found' });
    const email = String(req.body?.email || '').trim().toLowerCase();
    if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) return res.status(400).json({ error: 'Enter an email address.' });
    const user = await db.upsertUser({ email, sub: null, name: shortName(undefined, undefined, email), picture: null });
    startSession(res, user.id);
    res.json({ user: publicUser(user) });
  }),
);

app.post('/api/auth/logout', (_req, res) => {
  res.clearCookie(COOKIE);
  res.json({ ok: true });
});

app.get('/api/me', auth, (req: Authed, res) => {
  res.json({ user: publicUser(req.user!) });
});

app.get(
  '/api/game/current',
  auth,
  wrap(async (req, res) => {
    res.json(gamePayload(await currentGame(req.user!)));
  }),
);

app.post(
  '/api/game/skip',
  auth,
  wrap(async (req, res) => {
    await db.skipActive(req.user!.id);
    res.json(gamePayload(await currentGame(req.user!)));
  }),
);

app.post(
  '/api/game/submit',
  auth,
  wrap(async (req, res) => {
    const user = req.user!;
    const gameId = Number(req.body?.gameId);
    const masks: unknown = req.body?.masks;
    if (!Array.isArray(masks) || masks.length > 100 || !masks.every((m) => Number.isInteger(m) && m >= 0 && m < 16)) {
      return res.status(400).json({ error: 'Bad grid.' });
    }
    const open = await db.getActiveGame(user.id);
    if (!open || open.id !== gameId) return res.status(409).json({ error: 'That puzzle is no longer active.' });
    if (!isValidSolution(puzzleFor(open), masks as number[])) return res.json({ ok: false });
    const updated = await db.solveGame(user.id, open.id);
    if (!updated) return res.status(409).json({ error: 'That puzzle is no longer active.' });
    res.json({ ok: true, points: open.points, user: publicUser(updated) });
  }),
);

app.get(
  '/api/leaderboard',
  auth,
  wrap(async (req, res) => {
    const rows = await db.leaderboard(50);
    res.json({
      me: req.user!.id,
      rows: rows.map((r, i) => ({ rank: i + 1, id: r.id, name: r.display_name, score: r.total_score, games: r.games_played })),
    });
  }),
);

// Serve the built site when it exists (after `npm run build`).
const dist = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../dist');
if (existsSync(dist)) app.use(express.static(dist));

app.use((err: Error, _req: Request, res: Response, _next: NextFunction) => {
  console.error(err);
  res.status(500).json({ error: 'Something went wrong on the server.' });
});

async function main() {
  await db.init();
  app.listen(env.port, () => {
    console.log(`API ready on http://localhost:${env.port}  (${env.memory ? 'in-memory test database' : 'SQL Server'})`);
    if (!env.googleClientId) console.log('Note: GOOGLE_CLIENT_ID is not set, so Google sign-in is off.');
    if (env.devLogin) console.log('Note: DEV_LOGIN is on. Anyone can sign in with any email. Turn it off before going live.');
  });
}

main().catch((e) => {
  console.error('\nCould not start:', e instanceof Error ? e.message : e);
  console.error('Run "npm run db:check" for help with the database connection.');
  process.exit(1);
});
