# First-client delivery fixes — 2026-09-08

## Changes in this batch

- Removed the unused server-only Firebase Admin SDK dependency from Android.
- Preserved `ApiResponse` and `ApiError` during R8 shrinking. Gson uses these unannotated fields for every backend success/error envelope.
- Made the Firebase messaging service non-exported, following the [Firebase Android setup](https://firebase.google.com/docs/cloud-messaging/android/receive-messages).
- Connected the Rewards screen to the signed-in user's points listener. Balance changes now also recalculate reward progress.
- Serialized redeem/cancel requests independently of catalog loading. Repeated taps cannot start concurrent mutations; failure unlocks retry. Successful responses immediately update the displayed balance. Responses for a previous account are discarded.
- Prevented duplicate earn requests while a scan is in flight, including after a UI state reset.
- Tightened the local Firestore rules: profile creation uses an explicit field allow-list, client UID/timestamps are checked, and earn/redeem code reads and enumeration are denied. Loyalty changes still go through the backend.
- Added Android regression tests and an isolated Firestore emulator suite. The emulator uses the `demo-beanloyal-rules` project and loopback only; it does not use production credentials.

## Verification commands

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleRelease :app:lintDebug
cd tests/firestore
npm ci
npm test
```

The release artifact remains unsigned unless a signing configuration is supplied. Unit tests and a successful release build do not substitute for installing and exercising the release on a device.

## Verified results

- Android unit suite: 38 passed, zero failures, one pre-existing skipped `MenuAdapterTest` layout-inflation test.
- Firestore emulator suite: all 14 tests passed against emulator 1.19.8. The CLI stalled during startup on this machine, so the official emulator JAR was downloaded, its SHA-256 checked against Firebase CLI's published metadata, and run directly on `127.0.0.1:8088` with project `demo-beanloyal-rules`. `node --test rules.test.mjs` passed; the emulator was then stopped. `npm run test:running-emulator` supports this route when the emulator is already running.
- Android lint: zero errors, 241 warnings. The warning backlog is not addressed by this batch.

## Remaining delivery blockers found in source

1. **Email authentication contract is missing.** `SignUpActivity` calls `api/register` and `api/verify` relative to the `/api/v1/` base, resulting in `/api/v1/api/register` and `/api/v1/api/verify`. The adjacent `bean_backedn` Spring project has no registration/verification controller and protects all non-health routes. Changing the path alone cannot fix this. Restore the intended email authentication service or implement an agreed replacement, including delivery credentials and verified links.
2. **Pending reward recovery needs backend support.** The app displays a returned QR in a dialog, but cannot reload the pending reward after losing the response, dismissing it, or restarting. The backend currently exposes redeem/cancel but no customer pending-reward lookup. Implement authenticated recovery and status reconciliation before promising reliable cashier redemption. A client-only saved code would not cover lost responses or another device.
3. **Firestore deployment sources disagree.** This repository and adjacent `bean_backedn/firestore.rules` differ. The backend copy does not allow the app's `fullName`, `uid`, and `updatedAt` profile updates or its `config`/`meta` reads. Reconcile the deployment source and test both customer and cashier/admin flows before deploying. This batch does not deploy rules or change the neighboring repository.
4. **Production verification remains outstanding.** Verify email delivery, cashier redemption, cancellation/refund, expiration, replay protection, push delivery, Firebase configuration, and signing ownership against the actual deployment.

The earlier reports are historical. Backend/cashier projects do exist beside this repository, and Android earn/redeem operations already use backend APIs. Do not treat the original report's direct-Firestore transaction warnings or old failing-test count as current results.
