-- The expiry date a "card expiring" reminder was last sent for. Renewing moves the expiry date, so
-- the member is reminded again before the new one; the same date is never reminded twice.
ALTER TABLE members ADD COLUMN card_reminder_sent_for DATE NULL;
