import { Router } from 'express';
import { z } from 'zod';
import { pool, publicProfile } from '../db';
import { requireAuth, AuthRequest } from '../middleware/auth';
export const profileRouter = Router();
profileRouter.use(requireAuth);
async function loadProfile(userId: string) {
  const r = await pool.query('SELECT * FROM users WHERE id=$1', [userId]);
  if (!r.rows.length) return null;
  const interests = await pool.query('SELECT i.name FROM user_interests ui JOIN interests i ON i.id=ui.interest_id WHERE ui.user_id=$1', [userId]);
  const langs = await pool.query('SELECT language FROM user_languages WHERE user_id=$1', [userId]);
  return publicProfile({ ...r.rows[0], interests: interests.rows.map(x => x.name), languages: langs.rows.map(x => x.language) });
}
profileRouter.get('/', async (req: AuthRequest, res) => {
  const p = await loadProfile(req.userId!); if (!p) return res.status(404).json({ error: 'not_found' }); res.json(p);
});
profileRouter.get('/:id', async (req, res) => {
  const p = await loadProfile(req.params.id); if (!p) return res.status(404).json({ error: 'not_found' }); res.json(p);
});
const updateSchema = z.object({ displayName: z.string().min(1).max(50).optional(), bio: z.string().max(300).optional(), country: z.string().length(2).optional(), gender: z.enum(['female','male','non_binary','prefer_not_to_say']).optional(), interests: z.array(z.string()).max(20).optional(), languages: z.array(z.string().length(2)).max(10).optional() });
profileRouter.put('/', async (req: AuthRequest, res) => {
  const p = updateSchema.safeParse(req.body); if (!p.success) return res.status(400).json({ error: 'invalid_input' });
  const d = p.data;
  await pool.query('UPDATE users SET display_name=COALESCE($2,display_name), bio=COALESCE($3,bio), country=COALESCE($4,country), gender=COALESCE($5,gender), updated_at=now() WHERE id=$1', [req.userId, d.displayName, d.bio, d.country, d.gender]);
  if (d.interests) {
    await pool.query('DELETE FROM user_interests WHERE user_id=$1', [req.userId]);
    for (const name of d.interests) await pool.query('INSERT INTO user_interests (user_id, interest_id) SELECT $1, id FROM interests WHERE name=$2 ON CONFLICT DO NOTHING', [req.userId, name]);
  }
  if (d.languages) {
    await pool.query('DELETE FROM user_languages WHERE user_id=$1', [req.userId]);
    for (const l of d.languages) await pool.query('INSERT INTO user_languages VALUES ($1,$2) ON CONFLICT DO NOTHING', [req.userId, l]);
  }
  res.json(await loadProfile(req.userId!));
});
profileRouter.post('/photo', async (req: AuthRequest, res) => {
  // TODO (integration): accept a multipart upload, resize/compress server-side
  // (e.g. sharp, max 1024px), run the image-safety provider check, then store
  // in object storage and save only the URL. Never trust a client-supplied URL.
  const { photoUrl } = req.body || {};
  if (typeof photoUrl !== 'string' || !photoUrl.startsWith('https://')) return res.status(400).json({ error: 'photo_upload_not_configured' });
  await pool.query('UPDATE users SET profile_photo_url=$2 WHERE id=$1', [req.userId, photoUrl]);
  res.json({ ok: true });
});
