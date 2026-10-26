import { Router } from 'express';
import bcrypt from 'bcryptjs';
import { OAuth2Client } from 'google-auth-library';
import { z } from 'zod';
import { pool, ageFromDob } from '../db';
import { signTokens, requireAuth } from '../middleware/auth';
import { rateLimit } from '../middleware/rateLimit';
import { config } from '../config';
export const authRouter = Router();
const registerSchema = z.object({
  email: z.string().email(), password: z.string().min(8), username: z.string().min(3).max(30),
  displayName: z.string().min(1).max(50), dateOfBirth: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
  country: z.string().length(2), gender: z.enum(['female','male','non_binary','prefer_not_to_say']).optional(),
});
authRouter.post('/register', rateLimit('register', 5, 3600), async (req, res) => {
  const p = registerSchema.safeParse(req.body);
  if (!p.success) return res.status(400).json({ error: 'invalid_input', details: p.error.issues });
  if (ageFromDob(p.data.dateOfBirth) < 18) return res.status(403).json({ error: 'must_be_18_or_older' });
  const hash = await bcrypt.hash(p.data.password, 12);
  try {
    const r = await pool.query(
      'INSERT INTO users (email, username, display_name, date_of_birth, country, gender) VALUES ($1,$2,$3,$4,$5,$6) RETURNING id',
      [p.data.email, p.data.username, p.data.displayName, p.data.dateOfBirth, p.data.country, p.data.gender || 'prefer_not_to_say']);
    await pool.query('INSERT INTO auth_credentials (user_id, password_hash) VALUES ($1,$2)', [r.rows[0].id, hash]);
    await pool.query('INSERT INTO coins (user_id) VALUES ($1) ON CONFLICT DO NOTHING', [r.rows[0].id]);
    res.status(201).json({ userId: r.rows[0].id, ...signTokens(r.rows[0].id) });
  } catch { res.status(409).json({ error: 'email_or_username_taken' }); }
});
authRouter.post('/login', rateLimit('login', 10, 900), async (req, res) => {
  const { email, password } = req.body || {};
  const u = await pool.query('SELECT u.id, c.password_hash, u.status FROM users u JOIN auth_credentials c ON c.user_id=u.id WHERE u.email=$1', [email]);
  if (!u.rows.length || u.rows[0].status !== 'active' || !(await bcrypt.compare(password || '', u.rows[0].password_hash || '')))
    return res.status(401).json({ error: 'invalid_credentials' });
  res.json({ userId: u.rows[0].id, ...signTokens(u.rows[0].id) });
});
authRouter.post('/google', rateLimit('google', 10, 900), async (req, res) => {
  if (!config.googleClientId) return res.status(501).json({ error: 'google_sign_in_not_configured' });
  const client = new OAuth2Client(config.googleClientId);
  try {
    const ticket = await client.verifyIdToken({ idToken: req.body.idToken, audience: config.googleClientId });
    const payload = ticket.getPayload(); if (!payload?.email || !payload.sub) throw new Error('bad_token');
    let u = await pool.query('SELECT id FROM users WHERE email=$1', [payload.email]);
    if (!u.rows.length) return res.status(428).json({ error: 'onboarding_required', email: payload.email, googleSub: payload.sub });
    res.json({ userId: u.rows[0].id, ...signTokens(u.rows[0].id) });
  } catch { res.status(401).json({ error: 'invalid_google_token' }); }
});
authRouter.post('/google/complete', rateLimit('google', 10, 900), async (req, res) => {
  // Second step for a new Google user: re-verify the idToken server-side,
  // then create the account from onboarding answers. Never trust a client-
  // supplied googleSub alone.
  if (!config.googleClientId) return res.status(501).json({ error: 'google_sign_in_not_configured' });
  const p = z.object({ idToken: z.string(), username: z.string().min(3).max(30), displayName: z.string().min(1).max(50), dateOfBirth: z.string().regex(/^\d{4}-\d{2}-\d{2}$/), country: z.string().length(2), gender: z.enum(['female','male','non_binary','prefer_not_to_say']).optional() }).safeParse(req.body);
  if (!p.success) return res.status(400).json({ error: 'invalid_input' });
  if (ageFromDob(p.data.dateOfBirth) < 18) return res.status(403).json({ error: 'must_be_18_or_older' });
  const client = new OAuth2Client(config.googleClientId);
  try {
    const ticket = await client.verifyIdToken({ idToken: p.data.idToken, audience: config.googleClientId });
    const payload = ticket.getPayload(); if (!payload?.email || !payload.sub) throw new Error('bad_token');
    const existing = await pool.query('SELECT id FROM users WHERE email=$1', [payload.email]);
    if (existing.rows.length) return res.status(409).json({ error: 'account_already_exists' });
    const r = await pool.query('INSERT INTO users (email, username, display_name, date_of_birth, country, gender) VALUES ($1,$2,$3,$4,$5,$6) RETURNING id', [payload.email, p.data.username, p.data.displayName, p.data.dateOfBirth, p.data.country, p.data.gender || 'prefer_not_to_say']);
    await pool.query('INSERT INTO auth_credentials (user_id, google_sub) VALUES ($1,$2)', [r.rows[0].id, payload.sub]);
    await pool.query('INSERT INTO coins (user_id) VALUES ($1) ON CONFLICT DO NOTHING', [r.rows[0].id]);
    res.status(201).json({ userId: r.rows[0].id, ...signTokens(r.rows[0].id) });
  } catch { res.status(401).json({ error: 'invalid_google_token' }); }
});
authRouter.post('/logout', (_req, res) => {
  // Access tokens are short-lived (15m); refresh tokens are rotated/revoked
  // via a denylist in Redis in production — see docs/DEPLOYMENT.md.
  res.json({ ok: true });
});
authRouter.post('/refresh', async (req, res) => {
  const jwt = await import('jsonwebtoken');
  try {
    const d = jwt.default.verify(req.body.refresh, config.jwtRefreshSecret) as any;
    if (d.type !== 'refresh') throw new Error('bad');
    res.json(signTokens(d.sub));
  } catch { res.status(401).json({ error: 'invalid_refresh_token' }); }
});
authRouter.delete('/account', requireAuth, async (req, res) => {
  // Client must confirm deletion in-app first (Play requirement).
  const userId = (req as any).userId;
  await pool.query('DELETE FROM users WHERE id=$1', [userId]);
  res.json({ ok: true });
});
