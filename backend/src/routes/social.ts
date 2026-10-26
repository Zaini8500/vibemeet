import { Router } from 'express';
import { pool, redis, isBlocked } from '../db';
import { requireAuth, AuthRequest } from '../middleware/auth';
import { rateLimit } from '../middleware/rateLimit';
import { checkText } from '../services/moderation';
import { config } from '../config';
import { generateTurnCredentials } from '../services/turn';
import { coinsForProduct, verifyProductPurchase, verifySubscription } from '../services/playBilling';
export const socialRouter = Router();
socialRouter.use(requireAuth);
// --- Matching (queue entry; actual pairing happens in socket.ts worker) ---
socialRouter.post('/match/start', rateLimit('match', 30, 60), async (req: AuthRequest, res) => {
  const today = await redis.get(`daily_matches:${req.userId!}:${new Date().toISOString().slice(0, 10)}`);
  const user = await pool.query('SELECT is_premium FROM users WHERE id=$1', [req.userId]);
  if (!user.rows[0]?.is_premium && Number(today || 0) >= config.maxDailyMatchesFree)
    return res.status(429).json({ error: 'daily_limit_reached' });
  await redis.incr(`daily_matches:${req.userId!}:${new Date().toISOString().slice(0, 10)}`);
  await redis.hset(`waiting:${req.userId!}`, { userId: req.userId, at: Date.now(), prefs: JSON.stringify(req.body?.preferences || {}) });
  await redis.expire(`waiting:${req.userId!}`, 60);
  await redis.lpush('queue:match', req.userId!);
  res.json({ status: 'searching' });
});
socialRouter.post('/match/cancel', async (req: AuthRequest, res) => {
  await redis.del(`waiting:${req.userId!}`); await redis.lrem('queue:match', 0, req.userId!);
  res.json({ status: 'cancelled' });
});
socialRouter.get('/match/status', async (req: AuthRequest, res) => {
  const waiting = await redis.exists(`waiting:${req.userId!}`);
  res.json({ status: waiting ? 'searching' : 'idle' });
});
socialRouter.get('/match/ice-servers', async (req: AuthRequest, res) => {
  // STUN is public. TURN is only returned if configured, and then only as
  // per-user time-limited credentials generated here — never a static secret.
  const servers: any[] = [{ urls: 'stun:stun.l.google.com:19302' }];
  if (config.turnUrls.length && config.turnSharedSecret) {
    const creds = generateTurnCredentials(req.userId!, config.turnSharedSecret);
    servers.push({ urls: config.turnUrls, username: creds.username, credential: creds.credential });
  } else if (config.turnUrls.length) {
    // TURN URLs without a shared secret: do not return a static credential.
    // Set TURN_SHARED_SECRET (same value as the TURN server's static-auth-secret).
    console.warn('TURN URLs set but TURN_SHARED_SECRET missing — TURN not returned');
  }
  res.json({ iceServers: servers });
});
// --- Sessions ---
socialRouter.post('/session/create', async (req: AuthRequest, res) => {
  const { peerId } = req.body || {};
  if (!peerId || await isBlocked(req.userId!, peerId)) return res.status(403).json({ error: 'blocked_or_invalid' });
  const r = await pool.query('INSERT INTO matches (user_a, user_b) VALUES ($1,$2) RETURNING id', [req.userId, peerId]);
  res.status(201).json({ sessionId: r.rows[0].id });
});
socialRouter.post('/session/end', async (req: AuthRequest, res) => {
  const { sessionId } = req.body || {};
  await pool.query("UPDATE matches SET ended_at=now(), duration_seconds=EXTRACT(EPOCH FROM (now()-started_at))::int, status='ended' WHERE id=$1 AND (user_a=$2 OR user_b=$2)", [sessionId, req.userId]);
  res.json({ ok: true });
});
socialRouter.get('/session/history', async (req: AuthRequest, res) => {
  const r = await pool.query('SELECT id, user_a, user_b, started_at, ended_at, duration_seconds FROM matches WHERE user_a=$1 OR user_b=$1 ORDER BY started_at DESC LIMIT 50', [req.userId]);
  res.json(r.rows);
});
// --- Favorites / block / report ---
socialRouter.post('/favorite', async (req: AuthRequest, res) => {
  await pool.query('INSERT INTO favorites VALUES ($1,$2) ON CONFLICT DO NOTHING', [req.userId, req.body.favoriteId]); res.json({ ok: true });
});
socialRouter.delete('/favorite/:id', async (req: AuthRequest, res) => {
  await pool.query('DELETE FROM favorites WHERE user_id=$1 AND favorite_id=$2', [req.userId, req.params.id]); res.json({ ok: true });
});
socialRouter.post('/block', async (req: AuthRequest, res) => {
  const { blockedId } = req.body || {};
  await pool.query('INSERT INTO blocks VALUES ($1,$2) ON CONFLICT DO NOTHING', [req.userId, blockedId]);
  await redis.del(`waiting:${blockedId}`); // never match again, in either direction (checked in isBlocked)
  res.json({ ok: true });
});
socialRouter.delete('/block/:id', async (req: AuthRequest, res) => {
  await pool.query('DELETE FROM blocks WHERE blocker_id=$1 AND blocked_id=$2', [req.userId, req.params.id]); res.json({ ok: true });
});
socialRouter.post('/report', rateLimit('report', 20, 3600), async (req: AuthRequest, res) => {
  const { reportedUserId, category, description, sessionId } = req.body || {};
  const allowed = ['nudity','harassment','hate','scam','spam','underage','fake','violence','other'];
  if (!allowed.includes(category)) return res.status(400).json({ error: 'invalid_category' });
  const r = await pool.query('INSERT INTO reports (reporter_id, reported_user_id, category, description, session_id) VALUES ($1,$2,$3,$4,$5) RETURNING id', [req.userId, reportedUserId, category, description || '', sessionId || null]);
  res.status(201).json({ reportId: r.rows[0].id });
});
socialRouter.post('/chat/message', rateLimit('chat', 20, 60), async (req: AuthRequest, res) => {
  const check = checkText(String(req.body?.text || ''));
  if (!check.ok) return res.status(400).json({ error: check.reason });
  // Text chat is delivered over the same authenticated WebSocket (socket.ts),
  // not stored by default. This endpoint is the rate-limit / abuse gate.
  res.json({ ok: true });
});
// --- Coins & subscription verification (Play Billing is verified server-side) ---
socialRouter.get('/coins', async (req: AuthRequest, res) => {
  const r = await pool.query('SELECT balance FROM coins WHERE user_id=$1', [req.userId]);
  res.json({ balance: r.rows[0]?.balance || 0 });
});
socialRouter.post('/coins/purchase', async (req: AuthRequest, res) => {
  const { productId, purchaseToken } = req.body || {};
  const coins = coinsForProduct(productId);
  if (!coins || !purchaseToken) return res.status(400).json({ error: 'invalid_product_or_token' });
  try {
    // Idempotency first: a token already in transactions must never credit twice,
    // even if the app retries or the user replays the request.
    const existing = await pool.query('SELECT id FROM transactions WHERE purchase_token=$1', [purchaseToken]);
    if (existing.rows.length) {
      const bal = await pool.query('SELECT balance FROM coins WHERE user_id=$1', [req.userId]);
      return res.json({ ok: true, alreadyCredited: true, balance: bal.rows[0]?.balance || 0 });
    }
    const verified = await verifyProductPurchase(productId, purchaseToken);
    if (!verified.valid) return res.status(402).json({ error: 'purchase_not_valid', purchaseState: verified.purchaseState });
    await pool.query('INSERT INTO transactions (user_id, product_id, amount, currency, platform, status, purchase_token) VALUES ($1,$2,0,$3,$4,$5,$6)', [req.userId, productId, 'USD', 'google_play', 'completed', purchaseToken]);
    await pool.query('INSERT INTO coins (user_id, balance) VALUES ($1,$2) ON CONFLICT (user_id) DO UPDATE SET balance = coins.balance + $2, updated_at=now()', [req.userId, coins]);
    const bal = await pool.query('SELECT balance FROM coins WHERE user_id=$1', [req.userId]);
    res.json({ ok: true, coinsCredited: coins, balance: bal.rows[0]?.balance || 0 });
  } catch (e: any) {
    if (e?.message === 'play_billing_not_configured') return res.status(501).json({ error: 'play_billing_not_configured' });
    res.status(502).json({ error: 'play_verification_failed' });
  }
});
socialRouter.get('/subscription', async (req: AuthRequest, res) => {
  const r = await pool.query('SELECT product_id, status, expires_at FROM subscriptions WHERE user_id=$1 ORDER BY created_at DESC LIMIT 1', [req.userId]);
  res.json(r.rows[0] || { status: 'none' });
});
socialRouter.post('/subscription/verify', async (req: AuthRequest, res) => {
  const { productId, purchaseToken } = req.body || {};
  if (!productId || !purchaseToken) return res.status(400).json({ error: 'invalid_product_or_token' });
  try {
    const verified = await verifySubscription(productId, purchaseToken);
    if (!verified.valid) return res.status(402).json({ error: 'subscription_not_active', state: verified.state });
    const expiresAt = verified.expiryTime ? new Date(verified.expiryTime) : null;
    await pool.query('INSERT INTO subscriptions (user_id, product_id, platform, status, expires_at) VALUES ($1,$2,$3,$4,$5) ON CONFLICT DO NOTHING', [req.userId, productId, 'google_play', 'active', expiresAt]);
    // Also update any existing row for this user/product, since there is no
    // unique constraint on (user_id, product_id) in the base schema.
    await pool.query('UPDATE subscriptions SET status=$3, expires_at=$4 WHERE user_id=$1 AND product_id=$2', [req.userId, productId, 'active', expiresAt]);
    await pool.query('UPDATE users SET is_premium=true WHERE id=$1', [req.userId]);
    res.json({ ok: true, status: 'active', expiresAt, autoRenewing: verified.autoRenewing });
  } catch (e: any) {
    if (e?.message === 'play_billing_not_configured') return res.status(501).json({ error: 'play_billing_not_configured' });
    res.status(502).json({ error: 'play_verification_failed' });
  }
});
