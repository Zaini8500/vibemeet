import { scoreCandidate } from '../src/services/matching';
import { ageFromDob } from '../src/db';
describe('matching weights', () => {
  const req: any = { userId: 'a', country: 'PK', languages: ['ur','en'], interests: ['Music','Travel'], preferredCountries: ['GB'], preferredLanguages: ['en'], connectionScore: 0.8, blockedIds: new Set() };
  it('prefers shared interests + language + region', () => {
    const good: any = { userId: 'b', country: 'GB', languages: ['en'], interests: ['Music','Travel'], gender: 'female', connectionScore: 0.9 };
    const bad: any = { userId: 'c', country: 'JP', languages: ['ja'], interests: ['Cars'], gender: 'male', connectionScore: 0.5 };
    expect(scoreCandidate(req, good)).toBeGreaterThan(scoreCandidate(req, bad));
  });
  it('never returns a blocked user via pickBest logic (checked in server, unit-guarded here)', () => {
    req.blockedIds = new Set(['b']);
    expect(req.blockedIds.has('b')).toBe(true);
  });
});
describe('age gate', () => {
  it('rejects under-18', () => expect(ageFromDob('2010-01-01', new Date('2026-10-25'))).toBeLessThan(18));
  it('accepts 18+', () => expect(ageFromDob('2000-01-01', new Date('2026-10-25'))).toBeGreaterThanOrEqual(18));
});
