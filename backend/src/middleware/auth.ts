import jwt from 'jsonwebtoken';
import { Request, Response, NextFunction } from 'express';
import { config } from '../config';
export interface AuthRequest extends Request { userId?: string; role?: string }
export function signTokens(userId: string) {
  const access = jwt.sign({ sub: userId }, config.jwtAccessSecret, { expiresIn: '15m' });
  const refresh = jwt.sign({ sub: userId, type: 'refresh' }, config.jwtRefreshSecret, { expiresIn: '30d' });
  return { access, refresh };
}
export function requireAuth(req: AuthRequest, res: Response, next: NextFunction) {
  const token = req.headers.authorization?.replace('Bearer ', '');
  if (!token) return res.status(401).json({ error: 'unauthorized' });
  try { req.userId = (jwt.verify(token, config.jwtAccessSecret) as any).sub; next(); }
  catch { return res.status(401).json({ error: 'token_expired_or_invalid' }); }
}
export function requireRole(role: string) {
  return (req: AuthRequest, res: Response, next: NextFunction) => {
    // Roles are stored in a separate admin_users table in production; this
    // header check is ONLY for local admin-dashboard development and must be
    // replaced by a DB-backed role lookup before any public deployment.
    if (process.env.NODE_ENV !== 'production' && req.headers['x-admin-role'] === role) return next();
    return res.status(403).json({ error: 'forbidden' });
  };
}
