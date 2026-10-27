# API contracts

Base URL: `http://localhost:8080/api/v1`. Responses: `{"success":true,"message":"Success","data":...}`. Failures return a suitable HTTP status and `success:false`. Amounts are integer kobo: ₦1,500 = `150000`. List results use `{items,page,size,total}`; page starts at 0, size is 1–100. Edits require the latest numeric `version`.

All model roots are singular. `/public` routes are open except guest carts, which additionally require their secret token. `/private` routes require `Authorization: Bearer <accessToken>` and enforce ownership or role. There are no `/auth` routes. Admin means ADMIN or SUPER_ADMIN; managing staff is SUPER_ADMIN only.

## Shared user and verification

| Endpoint | Access and body / purpose |
| --- | --- |
| `POST /user/public/signup` | `{firstName,lastName,emailAddress,phoneNumber,password}`; creates CUSTOMER, emails code, returns User; does not log in |
| `POST /user/public/login` | `{emailAddress,password}` → `{accessToken,expiresIn,user,adminRole}` for either user type |
| `POST /user/public/forgot-password` | `{emailAddress}`; generic response; sends a reset link if the active account exists |
| `POST /user/public/reset-password` | `{userId,token,password}` from reset link; consumes token, revokes sessions |
| `GET /user/private/me` | Current User, excluding password/token fields |
| `PUT /user/private/profile` | `{version,firstName,lastName,phoneNumber,profilePictureUrl?}`; email/type cannot be edited here |
| `POST /user/private/logout` | Revokes all current user's sessions |
| `POST /user/private/change-password` | `{currentPassword,newPassword}`; revokes sessions |
| `POST /user-otp/public/verify` | `{emailAddress,code}`; six-digit email code, one use, 10-minute expiry |
| `POST /user-otp/public/resend` | `{emailAddress}`; replaces previous email code; generic response |

Passwords: minimum 12 characters, maximum 72 UTF-8 bytes. Phone: 7–25 characters using digits, spaces, parentheses, `+` or `-`. Names: max 50 characters each; email max 150. Verification is required for checkout and payment initialization. A logged-in unverified customer can still manage their profile/cart.

Reset and invitation email links carry `userId` and `token` in the URL fragment (`#userId=...&token=...`). The frontend reads them and sends them in JSON. Do not log/store these links in analytics or show codes in API responses. Reset links expire after 30 minutes. Invalid/expired/used tickets fail. Resending replaces the previous ticket; auth submission and resend are rate limited.

## Admin

| Endpoint | Purpose |
| --- | --- |
| `GET /admin/private?page=0&size=20` | SUPER_ADMIN; page of `{admin,user}` |
| `GET /admin/private/{id}` | SUPER_ADMIN; admin document UUID, returns linked account |
| `POST /admin/private/invite` | SUPER_ADMIN; `{firstName,lastName,emailAddress,phoneNumber}`; creates pending ADMIN and emails link |
| `POST /admin/public/accept-invite` | `{userId,token,password}`; accepts a one-use 24-hour invitation; then log in through User |
| `PUT /admin/private/{id}` | SUPER_ADMIN; `{version,active,jobTitle?}` |
| `PATCH /admin/private/{id}/status` | Same update contract; inactive staff lose access immediately |

Inviting the same pending staff email resends/replaces the invitation. An existing customer or accepted admin email cannot be invited again. Setting a pending invitation inactive revokes it. Accept the invitation before activating a pending staff record. SUPER_ADMIN is seeded once from configuration; restarts never reset its password. The seeded super admin cannot be deactivated/demoted through these endpoints. Staff edit their own names/contact/photo through User profile.

## Product and pricing

| Endpoint | Purpose |
| --- | --- |
| `GET /product/public` | Visible, unarchived catalogue; `search`, `category`, `page`, `size` |
| `GET /product/public/{id}` | Visible product |
| `GET /product/private` | Admin catalogue, including hidden products; same filters |
| `GET /product/private/{id}` | Admin detail |
| `POST /product/private` | Admin create; body below without version/image |
| `PUT /product/private/{id}` | Admin update; body below |
| `DELETE /product/private/{id}?version=...` | Admin archive and hide; preserves order history |

```json
{
  "version": 0,
  "name": "Orange Juice",
  "category": "NORM",
  "visible": true,
  "imageUrl": "",
  "variants": [
    {"id":"25cl","label":"25cl","priceMinor":150000},
    {"id":"50cl","label":"50cl","priceMinor":300000}
  ]
}
```

Categories: NORM, EXOTIC, FOODS. Juices require exactly 25cl and 50cl; foods require one portion variant. Prices range from 100 to 100000000 kobo per unit. Upload a product image after creating the product so its UUID can be the managed upload target. Products have UUID primary IDs and separate SKUs. Use IDs returned by the catalogue; `orange` is a SKU, not its ID.

## Cart

| Endpoint | Purpose |
| --- | --- |
| `POST /cart/public` | Creates guest cart → `{cart,guestToken}`; token is returned once |
| `GET /cart/public/{id}` | Guest cart with `X-Cart-Token` header |
| `POST /cart/public/{id}/item` | Guest add; `{version,productId,variantId,quantity}` |
| `PATCH /cart/public/{id}/item/{lineId}` | Guest set quantity; `{version,quantity}` |
| `DELETE /cart/public/{id}/item/{lineId}?version=...` | Guest remove line |
| `DELETE /cart/public/{id}?version=...` | Guest clear |
| `GET /cart/private` | Gets/creates current account cart |
| `POST /cart/private/claim` | `{cartId,guestToken}`; seals guest cart and merges into current account once |
| `GET /cart/private/{id}` | Owned account cart |
| `POST /cart/private/{id}/item` | Same add body, bearer token |
| `PATCH /cart/private/{id}/item/{lineId}` | Same quantity body, bearer token |
| `DELETE /cart/private/{id}/item/{lineId}?version=...` | Remove owned line |
| `DELETE /cart/private/{id}?version=...` | Clear owned cart |

Guest cart tokens are hashed in the database; keep the original privately in the browser. Carts expire after 30 days for guests; account carts persist. After login/signup, call `claim` with the saved guest token and use the returned account cart. Repeating the claim for that same account does not duplicate quantities. Other accounts cannot claim it; the guest URL stops working after claim. Failed merges due to quantity/line limits can be retried after editing the account cart. Never submit a userId to choose cart ownership.

Quantity is 1–100 per product/variant; max 30 product sizes. Stale versions return 409. Cart lines contain IDs and quantities; get display details from the current catalogue. Checkout recalculates prices and freezes them in Order. Duplicate product-size additions combine quantities.

## Order: authenticated checkout and pickup

| Endpoint | Purpose |
| --- | --- |
| `POST /order/private/checkout` | Verified CUSTOMER only; `Idempotency-Key: <random UUID>`; `{cartId,cartVersion,notes?}` |
| `GET /order/private` | Customers see their orders; admins see all; filters `search,status,paymentStatus,from,to,page,size` |
| `GET /order/private/{id}` | Owner or admin; internal Order UUID |
| `PATCH /order/private/{id}/status` | Admin; `{version,status}` |

Checkout returns Order, including internal UUID `id`, business order number `orderId` such as `HLT-20261017-8E69A370B0`, server-priced line snapshots, totals, account contact and pickup instructions. Show this total before starting payment. Guest checkout is unavailable; require signup/login and email verification first. Do not send customer identity, delivery address, fees or prices as checkout authority.

Statuses: `NEW → PREPARING → READY_FOR_PICKUP → COLLECTED`. Preparation requires verified payment. Admin may cancel NEW/PREPARING/READY_FOR_PICKUP orders. Paid cancellation needs manual refund handling; the API does not refund automatically. The ready email tells the customer to send their dispatch rider and includes the pickup address, phone and order number. There is no dispatch booking or delivery fee.

Same checkout key/body returns the same Order even after the cart changes. Different body with the same key returns 409. A different key with the exact same cart version also returns 409: continue the existing order. Never retry a lost checkout response with a new key.

## Payment

| Endpoint | Purpose |
| --- | --- |
| `GET /payment/public/method` | Configured PAYSTACK / FLUTTERWAVE methods |
| `POST /payment/private/initialize` | Verified owner CUSTOMER; separate `Idempotency-Key`; `{orderId,provider}` |
| `GET /payment/private` | Own payments; admins all; filters `orderId,status,page,size` |
| `GET /payment/private/{reference}` | Owner/admin Payment detail |
| `POST /payment/private/{reference}/verify` | Owner/admin; server provider verification |
| `POST /payment/public/webhook/paystack` | Signed raw Paystack `charge.success` |
| `POST /payment/public/webhook/flutterwave` | Signed raw Flutterwave `charge.completed` |

In a Payment request, `orderId` is the **internal Order UUID (`Order.id`)**, not the customer-facing HLT number. Payment has its own UUID `id` and a unique `reference` (`PAY-...`). `amountMinor` and customer email are derived from Order. Browser-supplied paid flags are ignored.

Initialization returns Payment with `paymentUrl`. Redirect only when it is present; otherwise keep the order/reference and show pending verification. Status is CREATED, INITIALIZING, PENDING, PAID or REVIEW. An uncertain external POST stays INITIALIZING and is never automatically posted again. One provider checkout per order is supported; subsequent attempts reuse that record and cannot switch providers. Unresolved sessions need verification/business review, not a second charge attempt.

On return from either provider, read the reference, authenticate, call verify and display the returned status. Do not treat the redirect query as payment proof. Signed webhooks are durably queued, deduplicated and verified through the provider. Reconciliation recovers interrupted updates. REVIEW requires administrator investigation; no endpoint can arbitrarily force a payment to PAID.

All API money is kobo. The gateway adapter sends kobo to Paystack and exact decimal naira to Flutterwave. Successful verification must match reference, NGN, exact amount, customer email and a provider transaction not already assigned elsewhere.

## Managed files

`POST /managed-file/private`, multipart form:

- `file`: JPEG/PNG/WebP, up to 5 MB.
- `usageType`: `PROFILE_PICTURE` or `PRODUCT_IMAGE`.
- `targetId`: existing product UUID for PRODUCT_IMAGE (admin required). PROFILE_PICTURE always targets the logged-in user, regardless of supplied targetId.

Returns ManagedFile with `id,fileUrl,publicId,format,resourceType,fileSizeBytes,usageType,targetId,ownerUserId`. The backend stores upload metadata before associating it to a resource. Send the returned `fileUrl` in `PUT /user/private/profile` or `PUT /product/private/{id}` with the latest version. The server validates the uploaded asset's owner/usage/target. After saving the new reference it deletes the former Cloudinary file and metadata; failures queue durable retries. If saving fails after reserving the association, it cleans the new upload and keeps the old image.

`DELETE /managed-file/private/{id}` removes the current user's unused staged asset. Attached/in-progress assets return 409. Never send publicId in a product/profile update or ask the browser to delete a previous image.

## Dashboard, customer management, content and settings

| Endpoint | Access / purpose |
| --- | --- |
| `GET /dashboard/private?period=WEEK` | Admin; TODAY/WEEK/MONTH; Africa/Lagos counts, gross receipts, most-purchased juices from paid collected orders |
| `GET /user/private/customer` | Admin; real customer User accounts; search/page/size |
| `GET /user/private/customer/{id}` | Admin; customer User UUID |
| `GET /user/private/customer/{id}/order` | Admin; customer order history, page/size |
| `PATCH /user/private/customer/{id}/status` | Admin; `{version,active}`; revokes customer sessions |
| `GET /business-settings/public` | Business contacts, pickup details, availability |
| `GET /business-settings/private` | Admin current settings |
| `PUT /business-settings/private` | Admin; `{version,name,email,phone,pickupAddress,pickupInstructions,checkoutEnabled}` |
| `GET /site-content/public`, `GET /site-content/private` | Website copy/social links; private route requires login |
| `PUT /site-content/private` | Admin; `{version,headline,description,instagram,tiktok}` |

Opening checkout requires a pickup address and business phone. There is one BusinessSettings and one SiteContent document, each with a UUID ID and a separate unique singleton key. Do not send your own ID to replace them.
