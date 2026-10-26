import { config } from '../config';
export interface MatchCandidate {
  userId: string; country: string; languages: string[]; interests: string[];
  preferredCountries: string[]; preferredLanguages: string[]; preferredGender?: string;
  gender?: string; connectionScore: number; // 0..1 from last ICE / RTT estimate
}
export interface MatchRequester extends MatchCandidate { blockedIds: Set<string> }
export function scoreCandidate(req: MatchRequester, cand: MatchCandidate): number {
  const w = config.matchWeights;
  const shared = cand.interests.filter(i => req.interests.includes(i)).length;
  const interestScore = req.interests.length ? shared / Math.max(req.interests.length, cand.interests.length) : 0;
  const languageScore = cand.languages.some(l => req.languages.includes(l)) ? 1 : 0;
  const regionScore = req.preferredCountries.length === 0 || req.preferredCountries.includes(cand.country) ? 1 : 0;
  const prefScore = (req.preferredGender ? (req.preferredGender === cand.gender ? 1 : 0) : 0.5)
    + (req.preferredLanguages.length === 0 || cand.languages.some(l => req.preferredLanguages.includes(l)) ? 1 : 0);
  const preferencesScore = prefScore / 2;
  return w.interests * interestScore + w.language * languageScore + w.region * regionScore
    + w.preferences * preferencesScore + w.connection * cand.connectionScore;
}
export function pickBest(req: MatchRequester, candidates: MatchCandidate[]): MatchCandidate | null {
  const eligible = candidates.filter(c => c.userId !== req.userId && !req.blockedIds.has(c.userId)
    && (!req.preferredGender || req.preferredGender === 'any' || c.gender === req.preferredGender));
  if (!eligible.length) return null;
  return eligible.map(c => ({ c, s: scoreCandidate(req, c) })).sort((a, b) => b.s - a.s)[0].c;
}
// Redis queue design (server.ts/socket.ts): a waiting user is stored as a hash
// `waiting:<userId>` with a 60s TTL, and its id is pushed to `queue:match`.
// A worker pops two entries, loads their profiles, runs pickBest(), and only
// then creates a matches row + emits `match_found` to both sockets.
// No user is ever matched with someone they blocked, in either direction.
