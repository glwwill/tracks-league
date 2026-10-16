// Connects to your database, creates the tables if they are missing, and reports what it found.
import { env } from './env';
import { db } from './db';

async function main() {
  console.log(`Connecting to ${env.db ? env.db.server : 'memory'} ...`);
  await db.init();
  const board = await db.leaderboard(5);
  console.log('Connected. Tables tl_users and tl_games exist.');
  console.log(`Players with at least one solved puzzle (top 5 shown): ${board.length}`);
  process.exit(0);
}

main().catch((e: unknown) => {
  const err = e as { code?: string; message?: string };
  console.error('\nDatabase check failed:', err.message ?? e);
  const msg = `${err.code ?? ''} ${err.message ?? ''}`;
  if (/ELOGIN|Login failed/i.test(msg)) {
    console.error('\nThe server was reached but rejected the login. Check DB_USER, DB_PASSWORD and DB_NAME in .env.');
  } else if (/ETIMEOUT|ESOCKET|ECONNREFUSED|ENOTFOUND|timeout/i.test(msg)) {
    console.error(
      '\nCould not reach the database. At WinHost, open the control panel for this database and allow remote\n' +
        'connections from your current IP address (SQL Server remote access). Also confirm DB_HOST is correct.',
    );
  } else if (/permission|CREATE TABLE/i.test(msg)) {
    console.error('\nThe database user is not allowed to create tables. Create tl_users and tl_games using the SQL in README.md.');
  }
  process.exit(1);
});
