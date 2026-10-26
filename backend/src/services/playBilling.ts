import { GoogleAuth } from 'google-auth-library';
import fs from 'fs';
// Server-side Google Play verification. This is the ONLY place a purchase
// is trusted: the Android app never grants premium/coins on its own.
// Flow: app sends purchaseToken + productId -> this code asks Google's
// Android Publisher API directly -> only a PURCHASED / ACTIVE result from
// Google is credited, and only once (purchaseToken is UNIQUE in transactions).
const PACKAGE = process.env.GOOGLE_PLAY_PACKAGE_NAME || 'com.vibemeet.app';
const SCOPE = 'https://www.googleapis.com/auth/androidpublisher';
function serviceAccountCredentials(): any {
  if (process.env.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON) return JSON.parse(process.env.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON);
  if (process.env.GOOGLE_PLAY_SERVICE_ACCOUNT_FILE) return JSON.parse(fs.readFileSync(process.env.GOOGLE_PLAY_SERVICE_ACCOUNT_FILE, 'utf8'));
  throw new Error('play_billing_not_configured');
}
async function accessToken(): Promise<string> {
  const auth = new GoogleAuth({ credentials: serviceAccountCredentials(), scopes: [SCOPE] });
  const client = await auth.getClient();
  const token = await client.getAccessToken();
  if (!token.token) throw new Error('play_auth_failed');
  return token.token;
}
async function playGet(path: string): Promise<any> {
  const token = await accessToken();
  const res = await fetch(`https://androidpublisher.googleapis.com${path}`, { headers: { Authorization: `Bearer ${token}` } });
  if (!res.ok) throw new Error(`play_api_${res.status}`);
  return res.json();
}
// Coin packs: productId -> coins. Override with COIN_PACKAGES JSON env if
// you rename products in Play Console. Prices are set in Play Console, not here.
export function coinsForProduct(productId: string): number {
  const defaults: Record<string, number> = { coins_100: 100, coins_500: 500, coins_1000: 1000 };
  const map = process.env.COIN_PACKAGES ? { ...defaults, ...JSON.parse(process.env.COIN_PACKAGES) } : defaults;
  return map[productId] || 0;
}
export function isSubscriptionProduct(productId: string): boolean {
  return ['premium_monthly', 'premium_yearly'].includes(productId);
}
export interface ProductVerification { valid: boolean; purchaseState?: number; consumptionState?: number; kind?: string }
export async function verifyProductPurchase(productId: string, purchaseToken: string): Promise<ProductVerification> {
  const data = await playGet(`/androidpublisher/v3/applications/${PACKAGE}/purchases/products/${productId}/tokens/${encodeURIComponent(purchaseToken)}`);
  // purchaseState: 0 = purchased, 1 = cancelled, 2 = pending. Only 0 is valid.
  return { valid: data.purchaseState === 0, purchaseState: data.purchaseState, consumptionState: data.consumptionState, kind: data.kind };
}
export interface SubscriptionVerification { valid: boolean; state?: string; expiryTime?: string; autoRenewing?: boolean }
export async function verifySubscription(productId: string, purchaseToken: string): Promise<SubscriptionVerification> {
  const data = await playGet(`/androidpublisher/v3/applications/${PACKAGE}/purchases/subscriptionsv2/tokens/${encodeURIComponent(purchaseToken)}`);
  // subscriptionState values include SUBSCRIPTION_STATE_ACTIVE, _IN_GRACE_PERIOD, etc.
  const state = data.subscriptionState as string | undefined;
  const lineItems = data.lineItems || [];
  const latest = lineItems[lineItems.length - 1];
  return {
    valid: state === 'SUBSCRIPTION_STATE_ACTIVE' || state === 'SUBSCRIPTION_STATE_IN_GRACE_PERIOD',
    state, expiryTime: latest?.expiryTime, autoRenewing: latest?.autoRenewingPlan?.autoRenewEnabled,
  };
}
export function isProductPurchaseResponseValid(data: any): boolean { return data?.purchaseState === 0; }
export function isSubscriptionResponseValid(data: any): boolean {
  return data?.subscriptionState === 'SUBSCRIPTION_STATE_ACTIVE' || data?.subscriptionState === 'SUBSCRIPTION_STATE_IN_GRACE_PERIOD';
}
