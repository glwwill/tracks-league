import 'dotenv/config';

function need(name: string): string {
  const v = process.env[name];
  if (!v) throw new Error(`Missing ${name} in your .env file.`);
  return v;
}

const memory = process.env.DB_MODE === 'memory';

export const env = {
  port: Number(process.env.PORT || 3001),
  production: process.env.NODE_ENV === 'production',
  /** "memory" is for automated tests only. Real use talks to SQL Server. */
  memory,
  jwtSecret: memory ? process.env.JWT_SECRET || 'test-secret' : need('JWT_SECRET'),
  googleClientId: process.env.GOOGLE_CLIENT_ID || '',
  /** Lets you sign in with just an email while Google is not set up. Never use in production. */
  devLogin: process.env.DEV_LOGIN === 'true',
  db: memory
    ? null
    : {
        server: need('DB_HOST'),
        user: need('DB_USER'),
        password: need('DB_PASSWORD'),
        database: need('DB_NAME'),
        port: process.env.DB_PORT ? Number(process.env.DB_PORT) : 1433,
        encrypt: process.env.DB_ENCRYPT !== 'false',
      },
};
