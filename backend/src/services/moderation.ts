// Privacy-conscious moderation. No call recording by default.
// Video: the client may send a single low-resolution sampled frame hash /
// provider verdict ONLY if a report is filed or an automated flag triggers.
// That integration point is `submitSample()` below — it is not enabled
// without a provider URL, and it never uploads a full call.
const BANNED_PATTERNS = [/\b(free money|send crypto|whatsapp me|invest now)\b/i];
export function checkText(text: string): { ok: boolean; reason?: string } {
  if (text.length > 500) return { ok: false, reason: 'too_long' };
  if (BANNED_PATTERNS.some(p => p.test(text))) return { ok: false, reason: 'spam_pattern' };
  const repeated = /(.)\1{9,}/.test(text);
  if (repeated) return { ok: false, reason: 'spam_pattern' };
  return { ok: true };
}
export async function submitSample(_sessionId: string, _providerVerdict?: string): Promise<void> {
  // TODO (integration): POST sampled frame metadata to MODERATION_PROVIDER_URL.
  // Store only the verdict + timestamp, not the frame, unless your policy and
  // provider contract explicitly require otherwise and users are told.
  return;
}
export function suspiciousSignals(events: { matchesPerMinute: number; identicalMessages: number; reportsReceived: number }): boolean {
  return events.matchesPerMinute > 10 || events.identicalMessages >= 5 || events.reportsReceived >= 3;
}
