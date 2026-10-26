import express from 'express';
import cors from 'cors';
import { createServer } from 'http';
import { Server } from 'socket.io';
import { config } from './config';
import { authRouter } from './routes/auth';
import { profileRouter } from './routes/profile';
import { socialRouter } from './routes/social';
import { requireAuth, requireRole } from './middleware/auth';
import { pool } from './db';
import { attachSocket } from './socket';
const app = express();
app.use(cors()); app.use(express.json({ limit: '1mb' }));
app.get('/health', (_req, res) => res.json({ ok: true }));
app.use('/auth', authRouter);
app.use('/profile', profileRouter);
app.use('/', socialRouter); // /match/*, /session/*, /favorite, /block, /report, /coins, /subscription
// Admin (RBAC). Replace requireRole's dev header check with a DB role lookup
// before public deployment — see middleware/auth.ts.
app.get('/admin/reports', requireAuth, requireRole('moderator'), async (_req, res) => {
  const r = await pool.query('SELECT * FROM reports WHERE status=$1 ORDER BY created_at DESC LIMIT 100', ['open']);
  res.json(r.rows);
});
app.post('/admin/users/:id/suspend', requireAuth, requireRole('admin'), async (req, res) => {
  await pool.query("UPDATE users SET status='suspended' WHERE id=$1", [req.params.id]); res.json({ ok: true });
});
app.post('/admin/users/:id/ban', requireAuth, requireRole('admin'), async (req, res) => {
  await pool.query("UPDATE users SET status='banned' WHERE id=$1", [req.params.id]); res.json({ ok: true });
});
app.post('/admin/reports/:id/action', requireAuth, requireRole('moderator'), async (req, res) => {
  await pool.query("UPDATE reports SET status='actioned' WHERE id=$1", [req.params.id]); res.json({ ok: true });
});
app.get('/admin/analytics', requireAuth, requireRole('admin'), async (_req, res) => {
  const m = await pool.query('SELECT COUNT(*)::int AS matches, AVG(duration_seconds)::int AS avg_duration FROM matches WHERE started_at > now() - interval \'7 days\'');
  const rep = await pool.query('SELECT COUNT(*)::int AS reports FROM reports WHERE created_at > now() - interval \'7 days\'');
  res.json({ matches7d: m.rows[0].matches, avgDurationSeconds: m.rows[0].avg_duration, reports7d: rep.rows[0].reports });
});
const httpServer = createServer(app);
attachSocket(new Server(httpServer, { cors: { origin: '*' } }));
httpServer.listen(config.port, () => console.log(`VibeMeet backend on :${config.port}`));
