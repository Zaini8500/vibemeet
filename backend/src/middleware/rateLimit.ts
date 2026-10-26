import { Request, Response, NextFunction } from 'express';
import { redis } from '../db';
// Fixed-window rate limit in Redis. Stops brute force / spam / bot bursts.
export function rateLimit(keyPrefix: string, max: number, windowSeconds: number) {
  return async (req: Request, res: Response, next: NextFunction) => {
    const id = (req as any).userId || req.ip;
    const key = `rate:${keyPrefix}:${id}`;
    const count = await redis.incr(key);
    if (count === 1) await redis.expire(key, windowSeconds);
    if (count > max) return res.status(429).json({ error: 'too_many_requests', retryAfterSeconds: windowSeconds });
    next();
  };
}
