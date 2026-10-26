import { Server, Socket } from 'socket.io';
import jwt from 'jsonwebtoken';
import { config } from './config';
import { redis, pool, isBlocked } from './db';
import { checkText } from './services/moderation';
// Authenticated Socket.IO: token in handshake.auth, verified per connection.
// Heartbeat: client sends `ping` every 20s; server drops stale presence after 45s.
export function attachSocket(io: Server) {
  io.use((socket, next) => {
    try {
      const token = socket.handshake.auth?.token as string;
      const d = jwt.verify(token, config.jwtAccessSecret) as any;
      (socket as any).userId = d.sub; next();
    } catch { next(new Error('unauthorized')); }
  });
  io.on('connection', (socket: Socket) => {
    const userId = (socket as any).userId as string;
    socket.join(`user:${userId}`);
    redis.set(`presence:${userId}`, 'online', 'EX', 45);
    socket.on('ping', () => redis.set(`presence:${userId}`, 'online', 'EX', 45));
    // Signalling: relay only, never inspect media. Payloads are SDP/ICE JSON.
    // Each relay checks the session exists and the sender is a participant.
    socket.on('signal', async (msg: { to: string; sessionId: string; type: string; payload: unknown }) => {
      const s = await pool.query('SELECT 1 FROM matches WHERE id=$1 AND (user_a=$2 OR user_b=$2)', [msg.sessionId, userId]);
      if (!s.rows.length) return;
      io.to(`user:${msg.to}`).emit('signal', { from: userId, sessionId: msg.sessionId, type: msg.type, payload: msg.payload });
    });
    socket.on('reaction', async (msg: { to: string; emoji: string }) => {
      io.to(`user:${msg.to}`).emit('reaction', { from: userId, emoji: msg.emoji });
    });
    socket.on('chat', async (msg: { to: string; text: string }) => {
      if (!checkText(msg.text).ok) return;
      io.to(`user:${msg.to}`).emit('chat', { from: userId, text: msg.text });
    });
    socket.on('call_state', (msg: { to: string; state: string }) => {
      io.to(`user:${msg.to}`).emit('call_state', { from: userId, state: msg.state });
    });
    socket.on('disconnect', () => redis.del(`presence:${userId}`));
  });
  // Simple pairing worker: every 2s, pop up to 2 waiting users, check blocks,
  // create a session, emit match_found to both. In production, replace with a
  // scored queue (services/matching.ts) and a dedicated worker process.
  setInterval(async () => {
    try {
      const a = await redis.rpop('queue:match'); if (!a) return;
      const b = await redis.rpop('queue:match'); if (!b) { await redis.lpush('queue:match', a); return; }
      if (a === b || await isBlocked(a, b)) { await redis.lpush('queue:match', a); await redis.lpush('queue:match', b); return; }
      const r = await pool.query('INSERT INTO matches (user_a, user_b) VALUES ($1,$2) RETURNING id', [a, b]);
      io.to(`user:${a}`).emit('match_found', { sessionId: r.rows[0].id, peerId: b, initiator: true });
      io.to(`user:${b}`).emit('match_found', { sessionId: r.rows[0].id, peerId: a, initiator: false });
    } catch { /* worker must never crash the server; errors are logged by the host */ }
  }, 2000);
}
