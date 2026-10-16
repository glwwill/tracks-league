import sql from 'mssql';
import { env } from './env';

export interface UserRow {
  id: number;
  email: string;
  display_name: string;
  picture_url: string | null;
  games_played: number;
  total_score: number;
}

export interface GameRow {
  id: number;
  user_id: number;
  step: number;
  size: number;
  level: 'easy' | 'medium' | 'hard';
  seed: number;
  points: number;
  status: 'active' | 'solved' | 'skipped';
}

export interface LeaderRow {
  id: number;
  display_name: string;
  total_score: number;
  games_played: number;
}

export interface Db {
  init(): Promise<void>;
  upsertUser(p: { email: string; sub: string | null; name: string; picture: string | null }): Promise<UserRow>;
  getUser(id: number): Promise<UserRow | null>;
  getActiveGame(userId: number): Promise<GameRow | null>;
  /** Marks any open game as skipped, then starts a new one. */
  createGame(userId: number, g: Pick<GameRow, 'step' | 'size' | 'level' | 'seed' | 'points'>): Promise<GameRow>;
  skipActive(userId: number): Promise<void>;
  /** Marks the game solved and adds its points. Returns null if it was not an open game of this user. */
  solveGame(userId: number, gameId: number): Promise<UserRow | null>;
  leaderboard(limit: number): Promise<LeaderRow[]>;
}

// ---------------------------------------------------------------- SQL Server

const CREATE_USERS = `
IF OBJECT_ID('dbo.tl_users', 'U') IS NULL
CREATE TABLE dbo.tl_users (
  id            INT IDENTITY(1,1) PRIMARY KEY,
  email         NVARCHAR(255) NOT NULL UNIQUE,
  google_sub    NVARCHAR(64)  NULL,
  display_name  NVARCHAR(100) NOT NULL,
  picture_url   NVARCHAR(500) NULL,
  games_played  INT NOT NULL CONSTRAINT df_tl_users_games DEFAULT 0,
  total_score   INT NOT NULL CONSTRAINT df_tl_users_score DEFAULT 0,
  created_at    DATETIME2 NOT NULL CONSTRAINT df_tl_users_created DEFAULT SYSUTCDATETIME(),
  last_played_at DATETIME2 NULL
)`;

const CREATE_GAMES = `
IF OBJECT_ID('dbo.tl_games', 'U') IS NULL
CREATE TABLE dbo.tl_games (
  id          INT IDENTITY(1,1) PRIMARY KEY,
  user_id     INT NOT NULL CONSTRAINT fk_tl_games_user REFERENCES dbo.tl_users(id),
  step        INT NOT NULL,
  size        INT NOT NULL,
  level       NVARCHAR(10) NOT NULL,
  seed        INT NOT NULL,
  points      INT NOT NULL,
  status      NVARCHAR(10) NOT NULL CONSTRAINT df_tl_games_status DEFAULT 'active',
  started_at  DATETIME2 NOT NULL CONSTRAINT df_tl_games_started DEFAULT SYSUTCDATETIME(),
  finished_at DATETIME2 NULL
)`;

const CREATE_INDEX = `
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_tl_games_user_status')
CREATE INDEX ix_tl_games_user_status ON dbo.tl_games (user_id, status)`;

const USER_COLS = 'id, email, display_name, picture_url, games_played, total_score';

function mssqlDb(): Db {
  const cfg = env.db!;
  let pool: sql.ConnectionPool | null = null;

  const getPool = async () => {
    if (pool?.connected) return pool;
    pool = await new sql.ConnectionPool({
      server: cfg.server,
      user: cfg.user,
      password: cfg.password,
      database: cfg.database,
      port: cfg.port,
      options: { encrypt: cfg.encrypt, trustServerCertificate: true },
      connectionTimeout: 15000,
      requestTimeout: 15000,
      pool: { max: 5, min: 0, idleTimeoutMillis: 30000 },
    }).connect();
    return pool;
  };

  return {
    async init() {
      const p = await getPool();
      await p.request().query(CREATE_USERS);
      await p.request().query(CREATE_GAMES);
      await p.request().query(CREATE_INDEX);
    },

    async upsertUser({ email, sub, name, picture }) {
      const p = await getPool();
      const found = await p.request().input('email', sql.NVarChar(255), email).query(`SELECT ${USER_COLS} FROM dbo.tl_users WHERE email = @email`);
      if (found.recordset.length) {
        const u = found.recordset[0] as UserRow;
        await p
          .request()
          .input('id', sql.Int, u.id)
          .input('sub', sql.NVarChar(64), sub)
          .input('name', sql.NVarChar(100), name)
          .input('pic', sql.NVarChar(500), picture)
          .query('UPDATE dbo.tl_users SET google_sub = @sub, display_name = @name, picture_url = @pic WHERE id = @id');
        return { ...u, display_name: name, picture_url: picture };
      }
      const ins = await p
        .request()
        .input('email', sql.NVarChar(255), email)
        .input('sub', sql.NVarChar(64), sub)
        .input('name', sql.NVarChar(100), name)
        .input('pic', sql.NVarChar(500), picture)
        .query(
          `INSERT INTO dbo.tl_users (email, google_sub, display_name, picture_url)
           OUTPUT INSERTED.id, INSERTED.email, INSERTED.display_name, INSERTED.picture_url, INSERTED.games_played, INSERTED.total_score
           VALUES (@email, @sub, @name, @pic)`,
        );
      return ins.recordset[0] as UserRow;
    },

    async getUser(id) {
      const p = await getPool();
      const r = await p.request().input('id', sql.Int, id).query(`SELECT ${USER_COLS} FROM dbo.tl_users WHERE id = @id`);
      return (r.recordset[0] as UserRow | undefined) ?? null;
    },

    async getActiveGame(userId) {
      const p = await getPool();
      const r = await p
        .request()
        .input('u', sql.Int, userId)
        .query(`SELECT TOP 1 id, user_id, step, size, level, seed, points, status FROM dbo.tl_games WHERE user_id = @u AND status = 'active' ORDER BY id DESC`);
      return (r.recordset[0] as GameRow | undefined) ?? null;
    },

    async skipActive(userId) {
      const p = await getPool();
      await p
        .request()
        .input('u', sql.Int, userId)
        .query(`UPDATE dbo.tl_games SET status = 'skipped', finished_at = SYSUTCDATETIME() WHERE user_id = @u AND status = 'active'`);
    },

    async createGame(userId, g) {
      const p = await getPool();
      const tx = new sql.Transaction(p);
      await tx.begin();
      try {
        await new sql.Request(tx)
          .input('u', sql.Int, userId)
          .query(`UPDATE dbo.tl_games SET status = 'skipped', finished_at = SYSUTCDATETIME() WHERE user_id = @u AND status = 'active'`);
        const r = await new sql.Request(tx)
          .input('u', sql.Int, userId)
          .input('step', sql.Int, g.step)
          .input('size', sql.Int, g.size)
          .input('level', sql.NVarChar(10), g.level)
          .input('seed', sql.Int, g.seed)
          .input('points', sql.Int, g.points)
          .query(
            `INSERT INTO dbo.tl_games (user_id, step, size, level, seed, points)
             OUTPUT INSERTED.id, INSERTED.user_id, INSERTED.step, INSERTED.size, INSERTED.level, INSERTED.seed, INSERTED.points, INSERTED.status
             VALUES (@u, @step, @size, @level, @seed, @points)`,
          );
        await tx.commit();
        return r.recordset[0] as GameRow;
      } catch (e) {
        await tx.rollback();
        throw e;
      }
    },

    async solveGame(userId, gameId) {
      const p = await getPool();
      const tx = new sql.Transaction(p);
      await tx.begin();
      try {
        const upd = await new sql.Request(tx)
          .input('g', sql.Int, gameId)
          .input('u', sql.Int, userId)
          .query(`UPDATE dbo.tl_games SET status = 'solved', finished_at = SYSUTCDATETIME() WHERE id = @g AND user_id = @u AND status = 'active'`);
        if (upd.rowsAffected[0] !== 1) {
          await tx.rollback();
          return null;
        }
        const r = await new sql.Request(tx)
          .input('g', sql.Int, gameId)
          .input('u', sql.Int, userId)
          .query(
            `UPDATE dbo.tl_users
             SET games_played = games_played + 1,
                 total_score = total_score + (SELECT points FROM dbo.tl_games WHERE id = @g),
                 last_played_at = SYSUTCDATETIME()
             OUTPUT INSERTED.id, INSERTED.email, INSERTED.display_name, INSERTED.picture_url, INSERTED.games_played, INSERTED.total_score
             WHERE id = @u`,
          );
        await tx.commit();
        return r.recordset[0] as UserRow;
      } catch (e) {
        await tx.rollback();
        throw e;
      }
    },

    async leaderboard(limit) {
      const p = await getPool();
      const r = await p
        .request()
        .input('n', sql.Int, limit)
        .query(
          `SELECT TOP (@n) id, display_name, total_score, games_played
           FROM dbo.tl_users
           WHERE games_played > 0
           ORDER BY total_score DESC, games_played ASC, created_at ASC`,
        );
      return r.recordset as LeaderRow[];
    },
  };
}

// ------------------------------------------------------- in memory (tests only)

function memoryDb(): Db {
  const users: (UserRow & { created: number })[] = [];
  const games: GameRow[] = [];
  const strip = (u: UserRow & { created: number }): UserRow => ({
    id: u.id,
    email: u.email,
    display_name: u.display_name,
    picture_url: u.picture_url,
    games_played: u.games_played,
    total_score: u.total_score,
  });
  return {
    async init() {},
    async upsertUser({ email, name, picture }) {
      let u = users.find((x) => x.email === email);
      if (!u) {
        u = { id: users.length + 1, email, display_name: name, picture_url: picture, games_played: 0, total_score: 0, created: Date.now() };
        users.push(u);
      } else {
        u.display_name = name;
        u.picture_url = picture;
      }
      return strip(u);
    },
    async getUser(id) {
      const u = users.find((x) => x.id === id);
      return u ? strip(u) : null;
    },
    async getActiveGame(userId) {
      return [...games].reverse().find((g) => g.user_id === userId && g.status === 'active') ?? null;
    },
    async skipActive(userId) {
      for (const g of games) if (g.user_id === userId && g.status === 'active') g.status = 'skipped';
    },
    async createGame(userId, g) {
      for (const x of games) if (x.user_id === userId && x.status === 'active') x.status = 'skipped';
      const row: GameRow = { id: games.length + 1, user_id: userId, status: 'active', ...g };
      games.push(row);
      return row;
    },
    async solveGame(userId, gameId) {
      const g = games.find((x) => x.id === gameId && x.user_id === userId && x.status === 'active');
      if (!g) return null;
      g.status = 'solved';
      const u = users.find((x) => x.id === userId)!;
      u.games_played += 1;
      u.total_score += g.points;
      return strip(u);
    },
    async leaderboard(limit) {
      return users
        .filter((u) => u.games_played > 0)
        .sort((a, b) => b.total_score - a.total_score || a.games_played - b.games_played || a.created - b.created)
        .slice(0, limit)
        .map((u) => ({ id: u.id, display_name: u.display_name, total_score: u.total_score, games_played: u.games_played }));
    },
  };
}

export const db: Db = env.memory ? memoryDb() : mssqlDb();
