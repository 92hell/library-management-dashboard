# Roadmap

Ideas to bring the system closer to a production library website, roughly in priority order.
Items marked *(hook ready)* already have an extension point in the code.

## Next up

1. **Verify contact details before linking a card.** Registration currently trusts the card ID
   plus a matching email/phone. Send a one-time code to that email/phone first
   (needs a real email/SMS channel - see 2).
2. **Real SMS / WhatsApp delivery** *(hook ready)* - implement `deliver()` in `SmsChannel` /
   `WhatsAppChannel` for a provider (e.g. Twilio, WhatsApp Business Cloud API); add delivery
   status tracking and retries.
3. **Notification preferences** - let customers choose channels (in-app / email / SMS / WhatsApp)
   and opt out of reminders; respect quiet hours.
4. **Password reset by email** and **email verification** on registration.

## Library operations

- Member language preference, so emails/SMS can be sent in Bahasa Indonesia too
- Certified e-signature for receipts via a PSrE (BSrE / Privy) as a `ReceiptSigner`; key rotation UI
- Fines: online payment (e.g. Midtrans / Xendit)
- Card expiry reminder by email/SMS a month ahead (the profile already warns); printable member card
- Fallback ISBN lookup (Google Books) when Open Library has no record
- Reports: charts over time, scheduled monthly report by email
- Branches / locations and inter-branch transfers

## Security follow-ups (from the security review)

- Registration: verify email ownership (removes the last difference between "created" and
  "refused" for someone else's email, and stops the email channel being used to mail arbitrary
  addresses); CAPTCHA if sign-up abuse appears
- Drop the `minimatch` override in `frontend/package.json` once eslint depends on minimatch 11+

## Platform

- Rate limiting on all public endpoints; distributed lockout store (Redis) for several instances
- Structured JSON logs and metrics (Micrometer + Prometheus/Grafana); alerting on error rate
- End-to-end: more journeys (registration and card linking, notifications)
- Manual screen-reader pass (NVDA / VoiceOver) beyond the automated WCAG checks
- Deployment: container registry + one-click deploy (Render / Fly.io / Railway), HTTPS, backups
