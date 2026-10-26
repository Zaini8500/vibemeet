import { Pool } from 'pg';
import Redis from 'ioredis';
import { config } from './config';
export const pool = new Pool({ connectionString: config.databaseUrl });
export const redis = new Redis(config.redisUrl, { maxRetriesPerRequest: 2 });
// Redis keys: queue:match:<country|global> (list), presence:<userId> (ttl 45s),
// rate:<userId>:<action> (counter), session:<matchId> (hash, ttl 24h)
export async function isBlocked(a: string, b: string): Promise<boolean> {
  const r = await pool.query(
    'SELECT 1 FROM blocks WHERE (blocker_id=$1 AND blocked_id=$2) OR (blocker_id=$2 AND blocked_id=$1) LIMIT 1', [a, b]);
  return (r.rowCount ?? 0) > 0;
}
export function ageFromDob(dob: string, now = new Date()): number {
  const d = new Date(dob); let age = now.getFullYear() - d.getFullYear();
  const m = now.getMonth() - d.getMonth();
  if (m < 0 || (m === 0 && now.getDate() < d.getDate())) age--;
  return age;
}
export function publicProfile(row: any) {
  // Deliberately excludes email, phone, date_of_birth, IP, device info.
  return { id: row.id, username: row.username, displayName: row.display_name,
    age: ageFromDob(row.date_of_birth), country: row.country, gender: row.gender,
    bio: row.bio, profilePhotoUrl: row.profile_photo_url,
    verificationStatus: row.verification_status, isPremium: row.is_premium,
    interests: row.interests || [], languages: row.languages || [] };
}
