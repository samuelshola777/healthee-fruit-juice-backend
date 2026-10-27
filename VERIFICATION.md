# Verification — 17 October 2026

Executed with Java 17.0.20 and Maven 3.9.11:

```text
apache-maven-3.9.11/bin/mvn -o -q -f healthee-backend/pom.xml verify
```

Result: exit 0; **45 tests, zero failures, errors or skips**. The executable Spring Boot JAR was produced. Tests use synthetic credentials and example.test addresses.

## Automated coverage

38 Spring application/database tests exercise the actual security filter chain, JWT decoder, MVC validation and MongoTemplate/repositories through the MongoDB wire protocol:

- UUID identities, the 18 seeded products, 13 collections and restart-safe catalogue/super-admin seeding.
- Shared customer/admin login, bearer revocation after logout/reset, forced CUSTOMER public signup, one-use/expired email verification codes and password reset.
- Super-admin invitations, acceptance, revocation, disabled staff access, protected super-admin status and recovery of an interrupted staff identity reservation.
- Anonymous/private and customer/admin permission boundaries; order, payment, file and cart ownership.
- Guest token protection, one-time cart claim/merge, stale quantity edits and unavailable sizes.
- Account-derived contact details, server pricing, immutable order snapshots, verified-customer checkout and required pickup settings. No delivery fee/address enters the order.
- Identical/conflicting checkout keys, cart-snapshot duplicate protection and concurrent checkout. A deterministic interleaving regression reproduces the case where another request creates the order between the first idempotency lookup and the cart-snapshot lookup. It failed before the fix and passes afterward.
- Concurrent hosted payment initialization, provider-switch rejection, timeout recovery without another external POST, exact amount/currency/reference/customer checks, transaction uniqueness and interrupted paid-order projection recovery.
- Paystack and Flutterwave webhook signature checks, durable deduplication and provider verification before paid status.
- Purchased-cart cleanup preserving later additions/quantity edits, paid email queue deduplication, payment-gated preparation, ready notification and pickup stages through collection/dashboard totals.
- Standalone managed uploads, product/profile association, replacement ordering, ownership/target rejection, failure rollback, durable deletion retry, staged asset expiry and interrupted replacement cleanup.
- SMTP job deduplication, UUID IDs and retry after a simulated transport failure.

3 gateway contract tests use a local HTTP fixture to check Paystack kobo initialization, Flutterwave exact naira initialization and Flutterwave naira-to-kobo verification parsing.

4 Cloudinary client tests check the signed reserved publicId and multipart request, invalid/oversized file rejection before network I/O, unexpected upload identity rejection, and idempotent destroy responses. HTTP transport responses are mocked for these tests.

## Packaged application check

Launched the actual executable JAR and exercised real localhost HTTP requests with a temporary MongoDB protocol server and a local Paystack HTTP fixture:

1. Health UP and all 18 public products available.
2. Guest cart creation/addition succeeds; anonymous checkout returns 401.
3. Customer signup queues a real MailJob; its test OTP verifies; login and guest-cart claim succeed.
4. Seeded super admin configures pickup and opens checkout; customer checkout totals 600000 kobo for two 50cl orange juices, with pickup fulfillment and no delivery fee.
5. Hosted payment initialization and server verification pass through the real GatewayClient to the local provider fixture; admin advances PREPARING → READY_FOR_PICKUP → COLLECTED and dashboard totals/bestseller quantities match.
6. Logout invalidates the customer's real bearer token.

No test emails were sent. Scheduled workers were paused for this packaged check; their behavior is covered by the automated tests. Native Mongo and SMTP health contributors were disabled only for this emulator/mail-fixture run, since the emulator lacks MongoDB's `hello` health command and no real SMTP account was used.

## Limits and release checks

Database integration used mongo-java-server's in-memory wire-protocol implementation, not native MongoDB. It does not establish native TTL timing, query/index performance, filesystem durability, backups, replication or failover behavior. Run the suite against a dedicated native database before release:

```bash
TEST_MONGO_URI='mongodb://localhost:27017/healthee_test' ./mvnw verify
```

This suite clears its configured database before each test. It refuses a TEST_MONGO_URI whose database name is not exactly `healthee_test`; never point tests at business data.

No real Paystack/Flutterwave account transaction, Brevo SMTP delivery or Cloudinary upload/deletion was performed. Verify those with your own test credentials and real deployment configuration. Docker configuration is supplied but was not executed here. No deployment was made.

The frontend remains separate: it still needs signup/verification/login/reset/invitation/callback pages, API wiring, guest-cart claim handling, live order views and browser verification. This backend schema is incompatible with the original v1 collections: use a new `healthee_v2` database, or migrate a backed-up copy through a separately reviewed migration. No production data was modified or automatically migrated.
