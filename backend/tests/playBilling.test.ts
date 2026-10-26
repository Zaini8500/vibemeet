import { coinsForProduct, isProductPurchaseResponseValid, isSubscriptionResponseValid, isSubscriptionProduct } from '../src/services/playBilling';
describe('Play Billing mapping', () => {
  it('maps coin packs', () => {
    expect(coinsForProduct('coins_100')).toBe(100);
    expect(coinsForProduct('coins_500')).toBe(500);
    expect(coinsForProduct('coins_1000')).toBe(1000);
    expect(coinsForProduct('premium_monthly')).toBe(0);
    expect(coinsForProduct('unknown')).toBe(0);
  });
  it('identifies subscriptions', () => {
    expect(isSubscriptionProduct('premium_monthly')).toBe(true);
    expect(isSubscriptionProduct('coins_100')).toBe(false);
  });
  it('only purchased products are valid', () => {
    expect(isProductPurchaseResponseValid({ purchaseState: 0 })).toBe(true);
    expect(isProductPurchaseResponseValid({ purchaseState: 1 })).toBe(false);
    expect(isProductPurchaseResponseValid({ purchaseState: 2 })).toBe(false);
  });
  it('only active / grace subscriptions are valid', () => {
    expect(isSubscriptionResponseValid({ subscriptionState: 'SUBSCRIPTION_STATE_ACTIVE' })).toBe(true);
    expect(isSubscriptionResponseValid({ subscriptionState: 'SUBSCRIPTION_STATE_IN_GRACE_PERIOD' })).toBe(true);
    expect(isSubscriptionResponseValid({ subscriptionState: 'SUBSCRIPTION_STATE_EXPIRED' })).toBe(false);
  });
});
