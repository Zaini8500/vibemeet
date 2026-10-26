-- Play Billing verification: idempotency for purchase tokens.
-- Run after the base schema.sql on an existing database.
-- (New databases: schema.sql already includes purchase_token.)
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS purchase_token TEXT UNIQUE;
CREATE INDEX IF NOT EXISTS idx_transactions_token ON transactions(purchase_token);
