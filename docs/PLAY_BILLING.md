# Play Billing verification

Code status: implemented. End-to-end testing still needs the Play Console
steps below — verification cannot be tested with a real purchase until
those exist, and no purchase is ever granted from a client claim alone.

## Products to create in Play Console (exact IDs)

- Subscription, monthly: `premium_monthly` — $4.99/month (price set in Console)
- Subscription, yearly: `premium_yearly` — $29.99/year
- One-time product: `coins_100` — 100 coins
- One-time product: `coins_500` — 500 coins
- One-time product: `coins_1000` — 1000 coins

Prices are configured in Play Console, not in code. Coin amounts are
mapped in `backend/src/services/playBilling.ts` (`coinsForProduct`, or
override with `COIN_PACKAGES` JSON env).

## Service account (backend verification)

1. Google Cloud Console → IAM → Service Accounts → create one for this app.
2. Grant it access in Play Console → Setup → API access → link the Cloud
   project, then grant the service account access to the app.
3. Create a JSON key for the service account. This key is a credential:
   never commit it, never send it in chat. Put it on the server only, as
   `GOOGLE_PLAY_SERVICE_ACCOUNT_FILE` (path) or
   `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` (content, server env only).
4. Backend env:
   ```
   GOOGLE_PLAY_PACKAGE_NAME=com.vibemeet.app
   GOOGLE_PLAY_SERVICE_ACCOUNT_FILE=/path/to/service-account.json
   ```

## How verification works

1. User buys in the app (BillingManager, Play Billing Library).
2. App sends only `{ productId, purchaseToken }` to the backend.
3. Backend calls Google's Android Publisher API directly:
   - Products: `purchases.products.get` — only `purchaseState == 0` is valid.
   - Subscriptions: `purchases.subscriptionsv2.tokens.get` — only
     `ACTIVE` or `IN_GRACE_PERIOD` is valid.
4. Backend credits exactly once: `transactions.purchase_token` is UNIQUE,
   and a replayed token returns the existing balance without re-crediting.
5. Backend sets `users.is_premium = true` and records the subscription
   expiry for verified subscriptions.
6. Only after backend confirmation should the app acknowledge the purchase
   (see `BillingManager.acknowledgeAndConsume`).

## Testing

- Add license testers in Play Console (Setup → License testing) and use
  test purchases — they verify through the same API path.
- Test these cases before release: valid purchase, cancelled purchase
  (must not credit), pending purchase (must not credit), replayed token
  (must not double-credit), expired subscription (must not stay premium),
  purchase restoration (re-verify the token on app start).
- If verification returns 501, the service account is not configured.
  If it returns 502, Google's API rejected the token or the service
  account lacks Play access — check the API access link in step 2.
