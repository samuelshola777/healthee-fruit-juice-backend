# Healthee backend — account checkout and pickup

Java 17 / Spring Boot 3.5.6 / MongoDB. One shared User identity supports customers and admins; Admin stores the linked staff record and role. MongoDB entities use `@Document`, a UUID `@Id` inherited from BaseEntity, and optimistic `@Version`. JPA `@Entity` is not used.

## Included

- Public catalogue with all 18 products and the supplied 3 August 2026 prices; editable sizes/prices, visibility, archive and managed images. Startup seeds missing SKUs without resetting edits. Images start empty so you can upload the actual bottles.
- Customer signup, email OTP verification, shared login/logout, password reset/change and profile pictures. Guests can browse and keep a token-protected cart; authenticated customers can claim it once into their account cart.
- Checkout requires an active, verified CUSTOMER account. Contact details come from User. Prices come from Product. Internal UUID `id` and customer-facing `orderId` are separate. Ordered names, quantities, prices, contact and pickup details are snapshots.
- Pickup only: NEW → PREPARING → READY_FOR_PICKUP → COLLECTED. Customer arranges their own dispatch rider after the ready email. There is no delivery charge or delivery address requirement.
- Separate Payment records, Paystack and Flutterwave hosted checkout, signed webhooks, exact server verification, durable webhook processing and payment reconciliation.
- Seeded SUPER_ADMIN; invitation-based staff accounts, expiring one-use invitation links, staff activation/deactivation and invitation revocation. Ordinary admins cannot manage admin accounts. The seeded super admin is protected from disable/demotion through this API.
- Order/customer management, dashboard figures and most-purchased juices from paid, collected orders. No stock tracking.
- Website copy/social links and business settings including pickup address/instructions and ordering availability.
- Cloudinary upload metadata and replacement cleanup, durable Brevo SMTP jobs, basic Mongo-backed rate limiting and bounded pagination.

## Run locally

1. Install Java 17 and run MongoDB, or use Docker Compose.
2. Copy `.env.example` to `.env` and fill in your server secrets. Generate a random JWT secret with `openssl rand -base64 48`. Set a bootstrap admin email and a strong password (minimum 12 characters, maximum 72 UTF-8 bytes).
3. With Docker, run `docker compose up --build -d`. The supplied Mongo container is for local development; its port is bound to localhost.
4. Without Docker, export your environment values in your terminal, use `MONGODB_URI='mongodb://localhost:27017/healthee_v2?serverSelectionTimeoutMS=5000&socketTimeoutMS=30000'`, then run `./mvnw verify` and `java -jar target/healthee-backend-1.0.0.jar`.

Spring Boot does not automatically load a `.env` file when running Java directly. Docker Compose loads the supplied `env_file`. Do not commit `.env` or put secret keys in frontend code.

The API runs at `http://localhost:8080/api/v1`; `/actuator/health` is the health endpoint. `API_DOCS_ENABLED=true` enables Swagger documentation, which requires an admin bearer token. No application credentials are bundled.

## Configure services

**Brevo SMTP:** set `BREVO_SMTP_USERNAME`, `BREVO_SMTP_KEY`, `MAIL_FROM` to a verified sender and `MAIL_ENABLED=true`. Uses smtp-relay.brevo.com:587 with required STARTTLS. This is an SMTP key, not the REST API key. Signup, OTP resend, invitations and password reset require the mail service to be configured. `ADMIN_NOTIFY_EMAIL` receives new paid-order notifications. Payment and order records remain saved if mail delivery is deferred.

**Paystack:** start with a test `PAYSTACK_SECRET_KEY`. Webhook URL: `https://YOUR_API_HOST/api/v1/payment/public/webhook/paystack`. Raw request bodies are verified against `x-paystack-signature` using HMAC-SHA512. The backend then queries Paystack to verify the transaction.

**Flutterwave:** uses the v3 Standard hosted checkout API and v3 secret key. Set `FLUTTERWAVE_SECRET_KEY` and `FLUTTERWAVE_WEBHOOK_HASH`. Webhook URL: `https://YOUR_API_HOST/api/v1/payment/public/webhook/flutterwave`. Checks v3 `verif-hash`; if `flutterwave-signature` is supplied, that signature must match the HMAC-SHA256/base64 of the raw body. An invalid supplied signature never falls back to the legacy header. Verification uses `transactions/verify_by_reference?tx_ref=...`.

**Cloudinary:** set `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`. Uploads go through the backend as signed image uploads; JPEG, PNG and WebP up to 5 MB are supported. No unsigned upload preset is required.

Log in as the seeded super admin. Set the actual pickup address, business phone and instructions in business settings, then enable checkout. Ordering starts CLOSED. Configured payment methods are listed at `/payment/public/method`; configured keys still need an actual test transaction before live use.

## Collections

| Collection / model | Purpose |
| --- | --- |
| user / User | Shared credentials, customer/admin identity, profile and verification |
| admin / Admin | Unique userId link, role, staff status and invitation state |
| user_otp / UserOtp | Hashed, expiring email codes, reset and invitation tokens |
| product / Product | Catalogue; embedded sizes and prices |
| order / Order | Account-owned orders; embedded immutable lines and pickup snapshots |
| payment / Payment | Provider checkout, reference, verified transaction and recovery state |
| cart / Cart | Account or guest cart, embedded lines and merge receipts |
| managed_file / ManagedFile | Cloudinary ownership, URL, usage, target and cleanup state |
| business_settings / BusinessSettings | Singleton business contact and pickup settings |
| site_content / SiteContent | Singleton website copy and social links |
| mail_job / MailJob | Deduplicated mail queue and delivery retries |
| webhook_job / WebhookJob | Deduplicated, signed-event processing retries |
| rate_limit / RateLimit.Bucket | Expiring request counters |

All document primary keys are UUID strings, including singleton settings and jobs. Separate unique fields provide singleton keys, product SKUs, order numbers and deduplication. Bootstrap explicitly creates unique, query and TTL indexes; automatic index creation is disabled. Categories, variants, cart lines and order lines are embedded, not additional collections.

## Checkout and payment guarantees

- Use a fresh random UUID `Idempotency-Key` for each checkout. Reuse that exact key and body after a lost response. Keys are scoped to the user. A different body with the same key returns 409. Another key cannot create another order from the exact same cart version.
- Create the order, display its server-calculated total for review, then initialize payment with its internal `id`. Use a separate idempotency key for payment initialization. The customer selects Paystack or Flutterwave before this first initialization.
- One hosted payment checkout is allowed per order. Reuse its existing payment URL. Provider switching and automatic re-initialization after an ambiguous timeout are deliberately blocked. If initialization remains uncertain, retain the order/reference, verify it and contact the business for reconciliation. This version has no automated replacement attempt or refund workflow.
- A redirect or webhook payload cannot mark a payment paid. Only a server verification with the matching reference, NGN currency, exact amount, email and unique provider transaction can do so. Mismatches become REVIEW.
- Payment is canonical. Durable projection flags let reconciliation finish updating an order after a crash. Pending payments are checked in bounded batches for 30 days; older unresolved records require administrator review.
- After verified payment, the worker removes only cart lines whose IDs and revisions still match the purchased snapshot. New lines and lines edited while payment was open remain untouched for customer review.
- Admin cancellation does not refund a payment. A paid cancelled order remains paid and must be handled manually with the provider/business. Gross received includes these receipts; it is not net revenue after refunds.

## File upload flow

1. Create the product first when uploading a product image. Send multipart `file`, `usageType` and product `targetId` to `/managed-file/private` with a bearer token. Profile uploads always target the current user.
2. The backend reserves a managed UUID/publicId, uploads to Cloudinary and saves metadata. The response includes `fileUrl`, `publicId`, file ID and usage.
3. Send that exact `fileUrl` in the relevant product or profile update with the latest record version. URLs from other owners/resources and arbitrary external URLs are rejected. Omit the URL to preserve the current image; use an empty string to remove it.
4. The backend saves the new association first, then deletes the old asset and its metadata. If the association fails, the old reference is kept and the new staged asset is cleaned up. A stale request rejected before reserving the image leaves it staged for retry or expiry.
5. Cleanup intent is durable before the association changes. Failed Cloudinary deletions retry in the background; interrupted uploads/associations and unused staged uploads are reconciled. Staged files expire after 24 hours. Referenced assets are checked before deletion.

A successful profile/product update can return while a failed remote deletion is still queued. Callers must not delete Cloudinary assets themselves. An unreferenced staged file can be removed through `/managed-file/private/{id}`.

## Database compatibility and operations

This v2 schema changes the original backend's collection names and account/order structures. **Use a fresh `healthee_v2` database for this version.** Startup refuses nonempty legacy `admins`, `orders`, `products` or `settings` collections rather than silently ignoring or overwriting them. No automatic production migration is included. If v1 already contains real orders, back it up and migrate a copy with a reviewed field/account mapping before deployment; do not simply drop it. Roll back against the untouched original database.

Use MongoDB authentication, backups, TLS and a private database network in production. Deploy behind HTTPS and set explicit frontend CORS origins. JWTs expire after one hour; logout/password changes revoke all that user's sessions. Keep access tokens and guest cart tokens private; this bearer API does not use cookies or require credentialed CORS.

SMTP delivery is at least once: a crash after SMTP accepts a message but before acknowledgement is saved can duplicate an email. Mail jobs expire after a day, webhook jobs after seven days; persistent failures need operational attention. Mongo TTL deletion is asynchronous, so code also checks token/cart expiry. Cleanup and payment workers use bounded batches; monitor unresolved payments, failed jobs and pending managed-file deletions.

See [API.md](API.md) for request contracts and [VERIFICATION.md](VERIFICATION.md) for executed checks and limits. The existing frontend has not been wired to this API yet.
