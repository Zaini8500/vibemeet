import { readFileSync } from 'fs';
import { join } from 'path';
import { Pool } from 'pg';

// One-time schema apply for hosted deploys (e.g. Render free, where
// pre-deploy commands are paid-only). Only runs when APPLY_SCHEMA=true.
// schema.sql is idempotent (CREATE TABLE IF NOT EXISTS / ON CONFLICT DO NOTHING).
async function main() {
  if (process.env.APPLY_SCHEMA !== 'true') {
    console.log('APPLY_SCHEMA not set, skipping schema apply');
    return;
  }
  const url = process.env.DATABASE_URL;
  if (!url) throw new Error('APPLY_SCHEMA=true but DATABASE_URL is empty');
  const sql = readFileSync(join(__dirname, '..', 'db', 'schema.sql'), 'utf8');
  const pool = new Pool({ connectionString: url });
  try {
    await pool.query(sql);
    console.log('VibeMeet schema applied');
  } finally {
    await pool.end();
  }
}
main().catch((err) => {
  console.error('Schema apply failed:', err);
  process.exit(1);
});
