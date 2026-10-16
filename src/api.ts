import type { PuzzleSpec } from './engine';

export interface PublicUser {
  id: number;
  name: string;
  picture: string | null;
  gamesPlayed: number;
  totalScore: number;
}

export interface GameInfo {
  gameId: number;
  step: number;
  ladderSteps: number;
  inLadder: boolean;
  size: number;
  level: 'easy' | 'medium' | 'hard';
  points: number;
  puzzle: PuzzleSpec & { seed: number };
}

export interface LeaderRow {
  rank: number;
  id: number;
  name: string;
  score: number;
  games: number;
}

export class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

async function call<T>(method: string, url: string, body?: unknown): Promise<T> {
  const res = await fetch(url, {
    method,
    credentials: 'same-origin',
    headers: body ? { 'Content-Type': 'application/json' } : undefined,
    body: body ? JSON.stringify(body) : undefined,
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new ApiError(res.status, data.error || 'Request failed');
  return data as T;
}

export const api = {
  config: () => call<{ googleClientId: string; devLogin: boolean }>('GET', '/api/config'),
  me: () => call<{ user: PublicUser }>('GET', '/api/me'),
  googleLogin: (credential: string) => call<{ user: PublicUser }>('POST', '/api/auth/google', { credential }),
  devLogin: (email: string) => call<{ user: PublicUser }>('POST', '/api/auth/dev', { email }),
  logout: () => call<{ ok: boolean }>('POST', '/api/auth/logout'),
  current: () => call<GameInfo>('GET', '/api/game/current'),
  skip: () => call<GameInfo>('POST', '/api/game/skip'),
  submit: (gameId: number, masks: number[]) =>
    call<{ ok: boolean; points?: number; user?: PublicUser }>('POST', '/api/game/submit', { gameId, masks }),
  leaderboard: () => call<{ me: number; rows: LeaderRow[] }>('GET', '/api/leaderboard'),
};
