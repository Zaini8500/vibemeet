import { generateTurnCredentials } from '../src/services/turn';
describe('TURN credentials', () => {
  it('creates an expiry-prefixed username and a non-empty password', () => {
    const c = generateTurnCredentials('user-123', 'test-secret', 3600);
    expect(c.username).toMatch(/^\d+:user-123$/);
    expect(c.credential.length).toBeGreaterThan(10);
    expect(c.expiry).toBeGreaterThan(Math.floor(Date.now() / 1000));
  });
  it('different users get different credentials', () => {
    expect(generateTurnCredentials('a', 's').username).not.toEqual(generateTurnCredentials('b', 's').username);
  });
});
