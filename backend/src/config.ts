function weights(): Record<string, number> {
  const w = JSON.parse(process.env.MATCH_WEIGHTS || '{"interests":0.30,"language":0.20,"region":0.20,"preferences":0.20,"connection":0.10}');
  const sum = (Object.values(w) as number[]).reduce((a, b) => a + b, 0);
  if (Math.abs(sum - 1) > 0.001) throw new Error('MATCH_WEIGHTS must sum to 1.0');
  return w;
}
export const config = {
  port: Number(process.env.PORT || 8080),
  databaseUrl: process.env.DATABASE_URL || '',
  redisUrl: process.env.REDIS_URL || 'redis://localhost:6379',
  jwtAccessSecret: process.env.JWT_ACCESS_SECRET || 'dev-only-change-me',
  jwtRefreshSecret: process.env.JWT_REFRESH_SECRET || 'dev-only-change-me-refresh',
  googleClientId: process.env.GOOGLE_CLIENT_ID || '',
  turnUrls: (process.env.TURN_URLS || '').split(',').filter(Boolean),
  turnSharedSecret: process.env.TURN_SHARED_SECRET || '',
  turnRealm: process.env.TURN_REALM || 'vibemeet',
  matchWeights: weights(),
  maxDailyMatchesFree: Number(process.env.MAX_DAILY_MATCHES_FREE || 50),
};
if (process.env.NODE_ENV === 'production' && config.jwtAccessSecret.startsWith('dev-only')) {
  throw new Error('Set real JWT secrets in production');
}
