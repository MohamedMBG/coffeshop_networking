# Hybrid Firebase + Backend Plan

**Date:** 2026-06-28  
**Project:** Loyalty App  
**Goal:** Keep Firebase for mobile speed, realtime reads, auth, and push notifications, while moving all trusted loyalty economy operations to a backend.

---

## 1. Executive Decision

The recommended architecture for this project is a hybrid model:

- Firebase remains the mobile platform layer.
- A backend becomes the authority for points, visits, QR codes, reward redemption, email verification, birthday rewards, cashier/admin actions, and audit logging.
- Firestore becomes a limited mobile read model and lightweight config/catalog store. It should not be the primary owner of sensitive business workflows.

This avoids a full rewrite while fixing the most important production risk: the Android client currently performs sensitive point and QR transactions directly against Firestore.

---

## 2. Target Architecture

### 2.1 Firebase Responsibilities

Use Firebase only for the parts where it gives clear mobile value:

1. Firebase Auth
   - User identity.
   - Firebase ID tokens sent to the backend.
   - Custom token sign-in after email verification if the current flow is kept.

2. Firestore
   - User profile read model.
   - Current points/visits read model written by the backend.
   - Recent activity read model written by the backend.
   - Menu items if you do not yet have an admin backend database.
   - Rewards catalog if you do not yet have an admin backend database.
   - Home banners.
   - App status.

   Firestore should not be used as a client-writable transaction engine for QR scans, reward redemption, point balances, or device ownership.

3. Firebase Cloud Messaging
   - Push delivery.
   - Reward reminders.
   - Admin announcements.

   Device token registration should go through the backend. Firebase only delivers the notification.

4. Firebase Console / Google Cloud
   - Security rules.
   - API key restrictions.
   - Monitoring and logs.

### 2.2 Backend Responsibilities

Use the backend for:

1. Email registration and verification.
2. Firebase custom token generation.
3. QR earn-code validation.
4. QR redeem-code validation.
5. Points and visits mutation.
6. Reward redemption.
7. Birthday reward idempotency.
8. FCM device registration.
9. Cashier/admin actions.
10. Audit logs.
11. Anti-fraud checks.
12. Scheduled cleanup jobs.
13. Catalog/menu/reward administration once the app needs admin tools.
14. Optional synchronization from backend database to Firestore read models.

### 2.3 Selected Backend Stack

The backend will be implemented with Spring Boot.

Initial stack:

1. Spring Boot REST API.
2. Firebase Admin SDK for Auth token verification and backend-owned Firestore writes.
3. Firestore as backend-owned storage/read model at first.
4. PostgreSQL only later if reporting, relational data, or multi-store SaaS features justify it.

For this project, start with backend-owned operations and Firestore read models. Add PostgreSQL later only when the product needs relational reporting, multi-store operations, or stronger admin tooling.

---

## 3. Trust Model

### 3.1 Core Rule

The Android app is not trusted to update the loyalty economy.

The app may request an action, but only the backend decides whether it is valid.

### 3.2 Client-Owned Fields

The Android client may write only profile fields:

- `fullName`
- `birthday`
- `gender`
- `phone`
- `address`
- `profileComplete`
- `updatedAt`

### 3.3 Server-Owned Fields

Only the backend may write:

- `points`
- `visits`
- `isVerified`
- `lastVisitTimestamp`
- `createdAt`
- QR code status fields
- reward redemption status fields
- activity logs
- device registration ownership
- cashier/admin metadata

### 3.4 Firestore Security Rules

Firestore rules should enforce:

1. Users can read only their own user document.
2. Users can update only allowed profile fields.
3. Users cannot write `points`, `visits`, `isVerified`, or activity logs.
4. Catalog/config/menu documents are readable but client-unwritable.
5. QR code documents are backend-owned; Android should normally submit codes to the backend instead of reading full QR documents directly.
6. Device token documents are server-only.
7. Everything else is denied by default.

The current `firestore.rules` file already moves in this direction. It must be deployed and kept aligned with the backend.

---

## 4. Firestore Read Models And Operational Data

Firestore should be treated as two different things:

1. Mobile read models:
   - documents the Android app reads directly for fast UI updates.
   - written by the backend or admin tooling.

2. Temporary operational storage:
   - documents the backend may use while Firestore is still the backend database.
   - never written directly by Android.

If PostgreSQL is introduced later, the operational collections should move to PostgreSQL first. Firestore can keep only the mobile read models.

### 4.1 Users

Path:

```text
users/{uid}
```

Fields:

```text
uid: string
email: string
fullName: string
birthday: string
gender: string
phone: string
address: string
profileComplete: boolean
isVerified: boolean
points: number
visits: number
lastVisitTimestamp: timestamp
createdAt: timestamp
updatedAt: timestamp
```

Ownership:

- Android can update profile fields only.
- Backend owns verification and loyalty fields.

### 4.2 Activity History

Path:

```text
users/{uid}/activities/{activityId}
```

Recommended fields:

```text
type: "earn" | "spend" | "bonus" | "redemption" | "admin_adjustment"
delta: number
balanceAfter: number
description: string
sourceType: "earn_code" | "redeem_code" | "birthday" | "reward" | "admin"
sourceId: string
status: "completed" | "pending" | "cancelled" | "expired"
createdAt: timestamp
createdBy: "system" | "cashier" | "admin"
```

Important cleanup:

- Stop mixing `points`, `delta`, `desc`, `item`, `rewardId`, and legacy aliases.
- Use `delta` for the signed points movement.
- Use `balanceAfter` for the user's resulting balance.

### 4.3 Menu Items

Path:

```text
menu_items/{itemId}
```

Fields:

```text
name: string
priceMAD: number
category: string
imageUrl: string
isAvailable: boolean
isPopular: boolean
popularityScore: number
updatedAt: timestamp
```

Ownership:

- Backend/admin owns writes.
- Android reads.

### 4.4 Rewards Catalog

Path:

```text
rewards_catalog/{rewardId}
```

Fields:

```text
name: string
description: string
category: string
redeemPoints: number
imagePath: string
termsUrl: string
expirationDays: number
active: boolean
createdAt: timestamp
updatedAt: timestamp
```

Ownership:

- Backend/admin owns writes.
- Android reads.

### 4.5 Earn Codes

Backend-owned operational data. Keep this in Firestore only while avoiding PostgreSQL. Move it to PostgreSQL later if reporting, cashier tooling, or multi-store control becomes important.

Path:

```text
earn_codes/{codeId}
```

Fields:

```text
codeId: string
points: number
status: "active" | "used" | "expired" | "revoked"
validForSec: number
createdAt: timestamp
expiresAt: timestamp
redeemedAt: timestamp
redeemedByUid: string
createdByCashierId: string
storeId: string
```

Ownership:

- Backend/admin/cashier creates.
- Backend updates status.
- Android may submit `codeId` to the backend but should not update this document.
- Android does not need to read the full document directly if the backend returns clear scan results.

### 4.6 Redeem Codes

Backend-owned operational data. Firestore may expose a limited user-specific read model so Android can show pending code status, but the backend owns the lifecycle.

Path:

```text
redeem_codes/{codeId}
```

Fields:

```text
codeId: string
userUid: string
rewardId: string
costPoints: number
status: "pending" | "completed" | "cancelled" | "expired"
createdAt: timestamp
expiresAt: timestamp
completedAt: timestamp
completedByCashierId: string
storeId: string
```

Ownership:

- Backend creates after checking balance.
- Backend/cashier completes.
- Android reads only its own pending code status if realtime status is needed.
- Android never creates, completes, cancels, or expires this document directly.

### 4.7 Device Tokens

Backend-owned operational data. Firestore is optional here; a backend database is usually better once you have enough devices or notification targeting rules.

Path:

```text
devices/{deviceId}
```

Fields:

```text
uid: string
fcmTokenHash: string
platform: "android"
appVersion: string
deviceModel: string
createdAt: timestamp
updatedAt: timestamp
lastSeenAt: timestamp
disabled: boolean
```

Security note:

- Prefer storing a token hash plus encrypted/raw token only where required for sending.
- Never log FCM tokens.
- Device writes should go through the backend.

---

## 5. Backend API Plan

All protected endpoints must require a Firebase ID token:

```http
Authorization: Bearer <firebase_id_token>
```

The backend must verify the token using Firebase Admin SDK and derive `uid` from the verified token. Never trust a `uid` sent in the request body.

### 5.1 Auth and Email Verification

#### POST `/api/register`

Purpose:

- Start email registration.
- Create a signed verification token.
- Send verification email.

Request:

```json
{
  "email": "user@example.com"
}
```

Backend steps:

1. Normalize and validate email.
2. Rate-limit by IP and email.
3. Create a short-lived verification token.
4. Store token hash with expiry.
5. Send verification email with deep link.
6. Return generic success.

Response:

```json
{
  "ok": true
}
```

#### POST `/api/verify`

Purpose:

- Exchange email verification token for Firebase custom token.

Request:

```json
{
  "token": "verification-token"
}
```

Backend steps:

1. Hash token and find matching record.
2. Reject expired or used token.
3. Create or find Firebase Auth user.
4. Set `users/{uid}.isVerified = true`.
5. Initialize user economy fields if missing.
6. Mark verification token as used.
7. Create Firebase custom token.
8. Return custom token.

Response:

```json
{
  "ok": true,
  "email": "user@example.com",
  "customToken": "firebase-custom-token"
}
```

### 5.2 Device Registration

#### POST `/api/push/registerDevice`

Purpose:

- Register or update FCM token for the authenticated user.

Request:

```json
{
  "fcmToken": "raw-fcm-token",
  "platform": "android",
  "appVersion": "1.0.0",
  "deviceModel": "Pixel"
}
```

Backend steps:

1. Verify Firebase ID token.
2. Validate token format and length.
3. Hash token for lookup.
4. Upsert device record.
5. Associate it with verified `uid`.
6. Do not log raw token.

Response:

```json
{
  "ok": true
}
```

### 5.3 Earn Points From QR Code

#### POST `/api/loyalty/earn`

Purpose:

- User scans an earn QR code and receives points.

Request:

```json
{
  "codeId": "ABC123"
}
```

Backend transaction:

1. Verify Firebase ID token.
2. Load `earn_codes/{codeId}`.
3. Check status is `active`.
4. Check code is not expired.
5. Load `users/{uid}`.
6. Apply anti-fraud rules:
   - prevent repeated scan of same code,
   - enforce visit cooldown if required,
   - reject revoked users,
   - reject suspicious volume.
7. Add points to user.
8. Increment visits if the business rule says this scan counts as a visit.
9. Mark code as `used`.
10. Write activity log with `type = "earn"`.
11. Return result.

Response:

```json
{
  "ok": true,
  "pointsAdded": 10,
  "balanceAfter": 120,
  "visitCounted": true,
  "visitsAfter": 8,
  "message": "+10 Points"
}
```

### 5.4 Create Reward Redemption

#### POST `/api/rewards/redeem`

Purpose:

- User spends points to create a pending redeem code.

Request:

```json
{
  "rewardId": "free-coffee"
}
```

Backend transaction:

1. Verify Firebase ID token.
2. Load reward catalog document.
3. Check reward is active.
4. Load user.
5. Check user has enough points.
6. Deduct points.
7. Create `redeem_codes/{codeId}` with status `pending`.
8. Write activity log with `type = "redemption"` and status `pending`.
9. Return redeem code and expiry.

Response:

```json
{
  "ok": true,
  "codeId": "RDM-123456",
  "costPoints": 50,
  "balanceAfter": 70,
  "expiresAt": "2026-06-29T12:00:00Z"
}
```

### 5.5 Cashier Completes Redemption

#### POST `/api/cashier/redeem/complete`

Purpose:

- Cashier scans a pending redeem code and completes it.

Request:

```json
{
  "codeId": "RDM-123456"
}
```

Backend transaction:

1. Verify cashier/admin identity.
2. Load redeem code.
3. Check status is `pending`.
4. Check code is not expired.
5. Mark code as `completed`.
6. Update related activity status to `completed`.
7. Return result.

Response:

```json
{
  "ok": true,
  "status": "completed"
}
```

### 5.6 Cancel or Expire Redemption

#### POST `/api/rewards/redeem/cancel`

Purpose:

- User cancels a pending redeem code and receives points back.

Request:

```json
{
  "codeId": "RDM-123456"
}
```

Backend transaction:

1. Verify Firebase ID token.
2. Load redeem code.
3. Confirm it belongs to the user.
4. Confirm status is `pending`.
5. Refund points.
6. Mark code as `cancelled`.
7. Write activity log with refund or update existing activity.
8. Return balance.

Response:

```json
{
  "ok": true,
  "balanceAfter": 120
}
```

Scheduled job:

- Find expired pending redeem codes.
- Mark them `expired`.
- Decide business rule:
  - either refund points automatically,
  - or keep deduction because reward was reserved.

Choose and document one rule before release.

### 5.7 Birthday Reward

#### POST `/api/rewards/birthday`

Purpose:

- Grant birthday points once per user per year.

Request:

```json
{}
```

Backend transaction:

1. Verify Firebase ID token.
2. Load user profile.
3. Check `birthday` exists and is valid.
4. Check today's date matches birthday according to chosen timezone.
5. Check no birthday reward already granted for current year.
6. Add points.
7. Write activity log with `type = "bonus"`.
8. Write claim marker, for example `birthday_claims/{uid_year}`.

Response:

```json
{
  "ok": true,
  "claimed": true,
  "pointsAdded": 15,
  "balanceAfter": 135
}
```

If already claimed:

```json
{
  "ok": true,
  "claimed": false,
  "reason": "already_claimed"
}
```

---

## 6. Android App Changes

### 6.1 Centralize Backend Configuration

Current issue:

- Backend URLs are hardcoded in more than one place.

Plan:

1. Add Gradle `BuildConfig` fields:
   - `BACKEND_BASE_URL_DEV`
   - `BACKEND_BASE_URL_PROD`
2. Use product flavors if needed:
   - `dev`
   - `prod`
3. Update `ApiClient` to read from `BuildConfig`.
4. Update `TokenRegistrar` to use the same source.
5. Remove duplicate hardcoded URLs.

Acceptance criteria:

- There is exactly one backend base URL source per build variant.
- Debug and release can point to different environments.

### 6.2 Add Authenticated API Calls

Plan:

1. Add an OkHttp interceptor.
2. Before protected API calls, get Firebase ID token.
3. Attach:

```http
Authorization: Bearer <id-token>
```

4. If token retrieval fails, show a sign-in/session error.
5. Never log the token.

Acceptance criteria:

- All protected backend calls include a valid Firebase ID token.
- No API request logs include credentials.

### 6.3 Replace Client-Side Scan Transactions

Current file:

```text
app/src/main/java/com/example/loyaltyapp/data/repository/ScanRepository.java
```

Current problem:

- The Android app directly mutates Firestore for earn and spend flows.

Plan:

1. Create DTOs:
   - `EarnRequest`
   - `EarnResponse`
   - `RedeemCodeCompleteRequest`
   - `LoyaltyErrorResponse`
2. Add Retrofit endpoint:

```java
@POST("api/loyalty/earn")
Call<EarnResponse> earn(@Body EarnRequest body);
```

3. Change `ScanRepository.processEarnCode` to call backend.
4. Map backend response to current UI state.
5. Remove direct Firestore transaction writes from Android.
6. Keep Firestore listeners only for displaying backend-written points, visits, and recent activity.

Acceptance criteria:

- Scanning an earn code calls backend.
- Backend updates the system of record and then updates Firestore read models.
- Android receives result and UI updates.
- Firestore rules block direct client point writes.

### 6.4 Replace Reward Redemption Flow

Current files:

```text
app/src/main/java/com/example/loyaltyapp/data/repository/RewardsRepository.java
app/src/main/java/com/example/loyaltyapp/viewmodels/RewardsViewModel.java
app/src/main/java/com/example/loyaltyapp/fragments/RewarsdFragment.java
```

Plan:

1. Keep rewards catalog loading from Firestore only while it remains the catalog read model.
2. Add backend endpoint:

```java
@POST("api/rewards/redeem")
Call<RedeemRewardResponse> redeemReward(@Body RedeemRewardRequest body);
```

3. On reward click, call backend.
4. Show pending redeem code screen/dialog.
5. Display QR/code for cashier.
6. Listen to a limited `redeem_codes/{codeId}` read model if realtime status is needed.
7. When cashier completes code, update UI to completed.

Acceptance criteria:

- Android never deducts points directly.
- A redemption creates a pending redeem code.
- Points are deducted only by backend.
- User sees code, expiry, and status.
- The backend owns the redemption lifecycle even if Android observes a Firestore status document.

### 6.5 Fix Profile Verification Meaning

Current risk:

- `isVerified` and `profileComplete` must remain separate.

Plan:

1. Use `profileComplete` for UI routing.
2. Use `isVerified` only for backend/email verification trust.
3. Make Android unable to write `isVerified`.
4. Ensure `/api/verify` sets `isVerified = true`.

Acceptance criteria:

- Completing profile does not set `isVerified`.
- Email verification does set `isVerified`.

### 6.6 Normalize Activity UI

Plan:

1. Update `ActivityEvent` model to canonical fields:
   - `type`
   - `delta`
   - `balanceAfter`
   - `description`
   - `sourceType`
   - `status`
   - `createdAt`
2. Keep temporary fallback support for old documents.
3. Add migration script or leave fallback until old data expires.
4. Update filters:
   - all
   - earned
   - spent
   - bonus
   - pending

Acceptance criteria:

- New activity documents have one schema.
- Old documents still display during transition.

---

## 7. Backend Implementation Steps

### Phase 1: Backend Foundation

1. Create Spring Boot service.
2. Add Firebase Admin SDK.
3. Load service account credentials securely.
4. Implement a Spring Security filter to verify Firebase ID tokens.
5. Add structured logging without secrets.
6. Add global exception handling with safe API errors.
7. Add request validation with DTO validation annotations.
8. Add rate limiting for sensitive endpoints.
9. Add health endpoint:

```http
GET /health
```

Acceptance criteria:

- Backend can verify Firebase ID tokens.
- Invalid/missing tokens return `401`.
- Logs contain request IDs but no tokens or PII-heavy payloads.

### Phase 2: Email Verification

1. Implement `/api/register`.
2. Implement token storage with hashed tokens.
3. Implement email sending.
4. Implement `/api/verify`.
5. Create Firebase user if needed.
6. Set `users/{uid}.isVerified = true`.
7. Return Firebase custom token.
8. Update Android only if response DTO needs changes.

Acceptance criteria:

- New users can register and verify by email.
- Verification token cannot be reused.
- Expired token is rejected.
- User document is initialized with safe defaults.

### Phase 3: Device Registration

1. Implement `/api/push/registerDevice`.
2. Require Firebase ID token.
3. Upsert device by token hash or installation ID.
4. Store owner `uid`.
5. Add disabled flag.
6. Add last seen timestamp.

Acceptance criteria:

- Device token belongs to authenticated user.
- Raw token is not logged.
- Unauthenticated request is rejected.

### Phase 4: Loyalty Earn Flow

1. Implement `/api/loyalty/earn`.
2. Use a backend transaction against Firestore or PostgreSQL, depending on the current storage choice.
3. Validate earn code.
4. Validate user.
5. Apply points and visits.
6. Mark code used.
7. Write activity log.
8. Return response DTO.
9. Update Android `ScanRepository`.

Acceptance criteria:

- Same earn code cannot be used twice.
- Expired/revoked code is rejected.
- User balance updates correctly.
- Activity log is written once.

### Phase 5: Reward Redemption Flow

1. Implement `/api/rewards/redeem`.
2. Deduct points in a backend transaction.
3. Create pending redeem code.
4. Write activity log.
5. Add Android UI for pending code.
6. Implement `/api/rewards/redeem/cancel`.
7. Implement cashier completion endpoint.

Acceptance criteria:

- User cannot redeem without enough points.
- Redeem code expires.
- Cashier can complete only pending valid codes.
- Cancelled/expired/completed codes cannot be reused.

### Phase 6: Birthday Reward

1. Implement idempotent birthday endpoint.
2. Store one claim per user per year.
3. Add timezone decision.
4. Return already-claimed state cleanly.
5. Update Android toast/message handling.

Acceptance criteria:

- Birthday reward can be claimed once per year.
- Startup checks do not duplicate points.

### Phase 7: Admin/Cashier Tools

Minimum admin features:

1. Create earn QR code.
2. Revoke earn QR code.
3. Complete redeem code.
4. View recent redemptions.
5. View user by email or phone.
6. Adjust points with reason.
7. View audit log.

Security:

1. Use Firebase custom claims or backend roles.
2. Roles:
   - `admin`
   - `cashier`
   - `manager`
3. Every admin action writes an audit record.

Acceptance criteria:

- Normal users cannot call cashier/admin endpoints.
- Every manual point adjustment has actor, reason, timestamp, and old/new balance.

---

## 8. PostgreSQL Decision Point

Do not add PostgreSQL immediately unless there is a clear reason.

### 8.1 Keep Firestore As The Main Storage Only If

- You have one cafe or a small number of branches.
- You need simple loyalty operations.
- You need realtime mobile updates.
- You do not need complex reporting.
- You want the lowest operational cost.
- You accept that all sensitive writes still go through the backend, even if the backend stores them in Firestore.

This does not mean Android writes directly to Firestore for loyalty operations. It means the backend uses Firestore as its database while Android uses Firestore mostly for reads.

### 8.2 Add PostgreSQL If

- You need advanced reports.
- You need joins across users, stores, purchases, rewards, campaigns, and employees.
- You need accounting-style ledgers.
- You need a multi-tenant SaaS model.
- You need a rich admin dashboard with filtering and exports.
- You need strict relational constraints.
- You want to reduce Firestore's role to Auth-adjacent/mobile sync only.
- You want backend-owned business tables instead of Firestore operational collections.

### 8.3 If PostgreSQL Is Added Later

Recommended split:

- PostgreSQL becomes the system of record for:
  - stores,
  - employees,
  - roles,
  - orders,
  - reward ledger,
  - audit logs,
  - campaigns,
  - reporting.
- Firestore remains only the mobile read model for:
  - user profile snapshot,
  - current points,
  - recent activity,
  - menu,
  - app config.

Use backend events to sync PostgreSQL results into Firestore.

---

## 9. Performance Plan

### 9.1 Firestore Read Efficiency

1. Avoid loading entire large collections.
2. Paginate activity history.
3. Query only active rewards.
4. Query only available menu items.
5. Use indexes for compound queries.
6. Keep user document small.
7. Avoid high-frequency listeners on screens that are not visible.

### 9.2 Android Performance

1. Keep Firestore listeners lifecycle-aware.
2. Remove `observeForever` or unregister in `onCleared`.
3. Use `ListAdapter` and `DiffUtil` for lists.
4. Cache catalog data where acceptable.
5. Avoid repeated birthday checks on every activity recreation unless backend is idempotent.

### 9.3 Backend Performance

1. Keep endpoint responses small.
2. Use transactions only around critical documents.
3. Avoid long-running logic inside transactions.
4. Add indexes for lookup fields.
5. Use idempotency keys for important operations.
6. Use request IDs for tracing.
7. Monitor latency per endpoint.

### 9.4 Cloud Run / Spring Boot Notes

If using Spring Boot on Cloud Run:

1. Expect possible cold starts when min instances are zero.
2. Keep dependencies lean.
3. Consider Java 21 and native image only if startup becomes a real problem.
4. Set min instances to `1` only when latency matters enough to pay for it.
5. Use connection pooling carefully if PostgreSQL is introduced.

---

## 10. Cost Plan

### 10.1 Lowest-Cost Path

Use:

- Firebase Auth.
- Firestore.
- FCM.
- Existing lightweight backend or Cloud Run with zero min instances.
- No PostgreSQL at first.

This is the cheapest path for MVP and small cafe usage.

### 10.2 Costs To Watch

1. Firestore reads.
   - Realtime listeners can create repeated reads.
   - Activity feeds and menu screens should not reload too often.

2. Firestore writes.
   - Every scan creates multiple writes:
     - user balance update,
     - QR code status update,
     - activity log,
     - possibly claim/audit document.

3. Backend hosting.
   - Serverless can be near-zero at low traffic.
   - Always-on instances cost more but reduce cold starts.

4. PostgreSQL.
   - Adds fixed monthly cost.
   - Adds maintenance, backups, migrations, and connection management.

5. Email provider.
   - Verification emails may be free at low volume but not forever.

6. SMS/phone auth.
   - Avoid phone auth unless required because it can become expensive.

### 10.3 Monthly Cost Review Checklist

Review once per month:

1. Firestore reads by collection.
2. Firestore writes by collection.
3. Most expensive queries.
4. Backend request volume.
5. Backend cold starts and average latency.
6. FCM device count.
7. Email volume.
8. Storage growth.
9. Logs volume.

---

## 11. Security Plan

### 11.1 Backend Security

1. Verify Firebase ID token on every protected endpoint.
2. Never trust `uid` from request body.
3. Validate every input.
4. Rate-limit auth, scan, and redemption endpoints.
5. Use idempotency for scan and redemption actions.
6. Store verification tokens hashed.
7. Store reset/redeem secrets hashed where possible.
8. Never log:
   - Firebase ID tokens,
   - custom tokens,
   - FCM tokens,
   - verification tokens,
   - full request bodies containing credentials.

### 11.2 Firestore Security

1. Deploy `firestore.rules`.
2. Test rules in Firebase emulator.
3. Add rules tests for:
   - user cannot write points,
   - user cannot write visits,
   - user cannot write activity logs,
   - user cannot edit another user,
   - user cannot write QR code status,
   - unauthenticated user cannot read private data.

### 11.3 Android Security

1. Keep API key restricted by package name and SHA.
2. Disable sensitive backup.
3. Keep logs free of tokens.
4. Do not store backend secrets in Android.
5. Use HTTPS only.
6. Consider Play Integrity later for fraud-sensitive releases.

### 11.4 Admin Security

1. Use role-based access control.
2. Require admin/cashier endpoints to check role.
3. Keep audit logs immutable.
4. Require reason for manual point adjustments.
5. Use least privilege for service accounts.

---

## 12. Testing Plan

### 12.1 Unit Tests

Backend:

1. Token verification middleware.
2. Request validation.
3. Earn code validation.
4. Reward redemption balance checks.
5. Birthday idempotency.
6. Role checks.

Android:

1. ViewModels with fake repositories.
2. API response mapping.
3. Error state mapping.
4. ActivityEvent parsing with old and new schemas.

### 12.2 Integration Tests

Use Firebase Emulator Suite:

1. Register and verify user.
2. Earn code success.
3. Earn code already used.
4. Earn code expired.
5. Reward redeem success.
6. Reward redeem insufficient points.
7. Redeem code complete.
8. Redeem code cancel.
9. Birthday claim once.
10. Firestore rules block direct client writes.

### 12.3 Manual QA

Test on Android:

1. New user registration.
2. Email verification deep link.
3. Profile completion.
4. App status blocked/unblocked.
5. Menu loading.
6. Rewards loading.
7. Earn QR scan success.
8. Earn QR scan failure.
9. Reward redeem success.
10. Reward redeem insufficient points.
11. Cashier completion.
12. Activity history updates.
13. Offline behavior.
14. App restart after redemption.

---

## 13. Deployment Plan

### 13.1 Environments

Create:

1. Development environment.
2. Staging environment.
3. Production environment.

Each environment should have:

- Firebase project or separated config.
- Backend base URL.
- Email provider config.
- Service account credentials.
- Firestore rules.
- Admin roles.

### 13.2 Secrets

Store outside the repo:

- Firebase service account JSON.
- Email provider API key.
- JWT/signing secrets if used.
- Database password if PostgreSQL is added.

Never commit secrets.

### 13.3 Release Steps

1. Deploy backend to staging.
2. Deploy Firestore rules to staging.
3. Run integration tests.
4. Build Android debug/staging app.
5. Perform manual QA.
6. Deploy backend to production.
7. Deploy Firestore rules to production.
8. Build signed release APK/AAB.
9. Verify API key package/SHA restrictions.
10. Monitor logs and errors after release.

---

## 14. Migration Plan From Current App

### Step 1: Lock Down Firestore

1. Review `firestore.rules`.
2. Add emulator tests.
3. Deploy rules to staging.
4. Confirm Android profile writes still work.
5. Confirm direct point writes fail.

### Step 2: Move Birthday Reward

1. Keep existing Android call to `/api/rewards/birthday`.
2. Ensure backend is idempotent.
3. Make backend write points/activity.
4. Remove any Android-side point mutation for birthday.

### Step 3: Move Earn Scan

1. Add `/api/loyalty/earn`.
2. Update `ScanRepository`.
3. Remove direct Firestore transaction from Android.
4. Test old and new QR codes.

### Step 4: Move Reward Redemption

1. Add `/api/rewards/redeem`.
2. Re-enable reward button.
3. Create pending redeem code.
4. Add code display UI.
5. Add cashier completion endpoint.

### Step 5: Normalize Activity Logs

1. Update backend to write canonical schema.
2. Update Android parser.
3. Keep fallback for old documents.
4. Optionally migrate old documents.

### Step 6: Add Admin/Cashier Flow

1. Define roles.
2. Add cashier login.
3. Add scan/complete redeem code.
4. Add admin QR generation.
5. Add audit logs.

### Step 7: Decide On PostgreSQL

After the above is stable, review:

1. Reporting needs.
2. Number of stores.
3. Admin dashboard complexity.
4. Monthly Firestore costs.
5. Need for relational constraints.

Only add PostgreSQL if these needs are real.

---

## 15. File-Level Work List

### Android Files To Update

```text
app/build.gradle.kts
app/src/main/java/com/example/loyaltyapp/ApiClient.java
app/src/main/java/com/example/loyaltyapp/ApiService.java
app/src/main/java/com/example/loyaltyapp/services/TokenRegistrar.java
app/src/main/java/com/example/loyaltyapp/data/repository/ScanRepository.java
app/src/main/java/com/example/loyaltyapp/data/repository/RewardsRepository.java
app/src/main/java/com/example/loyaltyapp/viewmodels/ScanViewModel.java
app/src/main/java/com/example/loyaltyapp/viewmodels/RewardsViewModel.java
app/src/main/java/com/example/loyaltyapp/fragments/RewarsdFragment.java
app/src/main/java/com/example/loyaltyapp/models/ActivityEvent.java
firestore.rules
```

### New Android DTOs To Add

Suggested package:

```text
app/src/main/java/com/example/loyaltyapp/network/dto/
```

DTOs:

```text
EarnRequest
EarnResponse
RedeemRewardRequest
RedeemRewardResponse
CancelRedemptionRequest
CancelRedemptionResponse
BirthdayRewardResponse
ApiErrorResponse
```

### Backend Files To Add

Spring Boot backend:

```text
backend/src/main/java/.../security/FirebaseAuthFilter.java
backend/src/main/java/.../security/CurrentUser.java
backend/src/main/java/.../config/FirebaseAdminConfig.java
backend/src/main/java/.../auth/AuthController.java
backend/src/main/java/.../push/PushController.java
backend/src/main/java/.../loyalty/LoyaltyController.java
backend/src/main/java/.../rewards/RewardsController.java
backend/src/main/java/.../cashier/CashierController.java
backend/src/main/java/.../admin/AdminController.java
backend/src/main/java/.../audit/AuditService.java
backend/src/main/java/.../firestore/FirestoreTransactionService.java
```

---

## 16. Milestones

### Milestone 1: Secure Current App

Deliverables:

1. Firestore rules deployed.
2. API key restricted.
3. Sensitive logs removed.
4. `profileComplete` and `isVerified` separated.
5. Reward redemption disabled until backend flow exists.

Status:

- Mostly documented in `docs/P0_SECURITY_FIXES.md`.
- Console/deploy actions still need confirmation.

### Milestone 2: Backend Owns Points

Deliverables:

1. `/api/loyalty/earn`.
2. `/api/rewards/birthday`.
3. Android scan flow uses backend.
4. Android birthday flow uses backend.
5. Firestore direct point writes blocked.

### Milestone 3: Real Redemption

Deliverables:

1. `/api/rewards/redeem`.
2. Pending redeem code model.
3. Redeem code display UI.
4. Cashier completion endpoint.
5. Cancel/expire handling.

### Milestone 4: Admin/Cashier

Deliverables:

1. Admin role model.
2. Cashier role model.
3. QR generation.
4. Redemption completion.
5. Audit logs.

### Milestone 5: Production Readiness

Deliverables:

1. Emulator integration tests.
2. Backend monitoring.
3. Android release build.
4. Manual QA pass.
5. Cost review dashboard.
6. Incident rollback plan.

---

## 17. Acceptance Criteria For The Final Architecture

The architecture is complete when:

1. Android cannot directly change points, visits, QR status, or redemption status.
2. Backend verifies Firebase ID token on every sensitive endpoint.
3. Backend is the only writer of loyalty economy fields.
4. Firestore rules enforce the same trust model.
5. QR codes cannot be reused.
6. Reward redemptions produce pending codes and a clear status lifecycle.
7. Birthday rewards are granted at most once per user per year.
8. User activity logs have one canonical schema.
9. Admin/cashier actions are role-protected and audited.
10. No secrets or tokens are logged.
11. The Android app has separate dev/prod backend configuration.
12. The app still keeps Firebase advantages: realtime UI, Auth, FCM, and low startup cost.

---

## 18. Recommended Next Action

Start with Milestone 2:

1. Implement `/api/loyalty/earn`.
2. Update `ScanRepository` to call it.
3. Deploy Firestore rules in staging.
4. Verify that direct Android point writes are blocked.
5. Keep Firestore only as backend-owned storage/read models until reporting or admin complexity justifies PostgreSQL.

This gives the biggest security improvement while using Spring Boot and postponing the extra cost and complexity of PostgreSQL until the product needs it.
