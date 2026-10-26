import crypto from 'crypto';
// TURN REST API (coturn-compatible) time-limited credentials.
// Username = expiryUnixSeconds:userId. Password = base64(HMAC-SHA1(secret, username)).
// The secret is shared only between this backend and the TURN server — never
// shipped in the app, never committed. Credentials expire in 24h by default.
export function generateTurnCredentials(userId: string, secret: string, ttlSeconds = 86400) {
  const expiry = Math.floor(Date.now() / 1000) + ttlSeconds;
  const username = `${expiry}:${userId}`;
  const password = crypto.createHmac('sha1', secret).update(username).digest('base64');
  return { username, credential: password, expiry };
}
