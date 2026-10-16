# Tracks League

Train Tracks puzzles with Google sign-in, points, and a leaderboard. React + TypeScript front end, Express server, WinHost SQL Server database.

## First run

1. Make sure a file named `.env` (not `.env.txt`) sits in this folder. See `.env.example` for the lines it needs.
2. In a terminal in this folder:

```
npm install
npm run db:check
npm run dev
```

3. Open http://localhost:5173

`npm run db:check` connects to WinHost and creates the two tables (`tl_users`, `tl_games`) if they are missing. It prints a plain-English hint if the connection fails.

## WinHost connection

Your app runs on your computer and connects out to WinHost. WinHost only accepts outside connections from IP addresses you allow. In the WinHost control panel, open the SQL Server database and add your current public IP under remote access. If your home IP changes, `db:check` will start timing out and you add the new one.

If your database user cannot create tables, run this in WinHost's SQL tool instead:

```sql
CREATE TABLE dbo.tl_users (
  id INT IDENTITY(1,1) PRIMARY KEY,
  email NVARCHAR(255) NOT NULL UNIQUE,
  google_sub NVARCHAR(64) NULL,
  display_name NVARCHAR(100) NOT NULL,
  picture_url NVARCHAR(500) NULL,
  games_played INT NOT NULL DEFAULT 0,
  total_score INT NOT NULL DEFAULT 0,
  created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
  last_played_at DATETIME2 NULL
);
CREATE TABLE dbo.tl_games (
  id INT IDENTITY(1,1) PRIMARY KEY,
  user_id INT NOT NULL REFERENCES dbo.tl_users(id),
  step INT NOT NULL,
  size INT NOT NULL,
  level NVARCHAR(10) NOT NULL,
  seed INT NOT NULL,
  points INT NOT NULL,
  status NVARCHAR(10) NOT NULL DEFAULT 'active',
  started_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
  finished_at DATETIME2 NULL
);
CREATE INDEX ix_tl_games_user_status ON dbo.tl_games (user_id, status);
```

## Google sign-in

1. Go to https://console.cloud.google.com and create a project.
2. APIs & Services, OAuth consent screen: choose External, fill in the app name and your email, save.
3. APIs & Services, Credentials, Create credentials, OAuth client ID, type Web application.
4. Under Authorized JavaScript origins add `http://localhost:5173` and `http://localhost:3001`. Add your real site address later.
5. Copy the Client ID into `GOOGLE_CLIENT_ID=` in `.env`, then restart `npm run dev`.

Until then, `DEV_LOGIN=true` shows a test sign-in box that accepts any email. Set it to `false` before the site is public.

## How it works

- The server picks each puzzle and keeps the answer. The browser never receives it. When a grid is solved, the browser sends it back and the server checks it before adding points.
- Ladder: puzzles 1 and 2 are Easy (5x5, 6x6), 3 and 4 Medium (7x7, 8x8), 5 and 6 Hard (9x9, 10x10). After that, size and level are random.
- Points: Easy 10, Medium 25, Hard 50. Skipping scores nothing and gives a new puzzle at the same step.
- Leaderboard: ranked by total score, then fewer puzzles solved, then who joined first. It shows first name and last initial only.
- Stored per player: email, name, picture link, games solved, total score. No password is stored anywhere because Google handles sign-in.

## Files

- `server/` the API. `ladder.ts` holds the ladder and points. `db.ts` holds all SQL.
- `src/engine/` the puzzle generator and solver, unchanged from the first app.
- `src/` the screens.

## Going live later

`npm run build` then `npm start` serves the site and API together on one port. WinHost runs Windows and IIS, so hosting the Node server there needs iisnode or a Node-friendly host. Ask me when you get there.

## Security notes

- `.env` holds your database password. It is listed in `.gitignore`. Your Desktop syncs to OneDrive, so that file is copied to OneDrive too. Consider moving the project to a folder OneDrive does not sync.
- `ADMIN_USERNAME` and `ADMIN_PASSWORD` in `.env` are not used by this app yet.
