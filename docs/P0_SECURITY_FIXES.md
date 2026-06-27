# P0 Security Hardening — Implementation Report

**Date:** 2026-06-27
**Branch:** master
**Scope:** P0 items from `PROJECT_REPORT.md` — "Must fix before real users"
**Build status:** `./gradlew.bat :app:compileDebugJavaWithJavac` → BUILD SUCCESSFUL

---

## Executive summary

Six P0 security issues were identified in `PROJECT_REPORT.md`. Five were fully addressed inside the repository. The sixth (Firebase API key restriction) is a Google Cloud Console action and is documented here with the exact steps to take.

| # | P0 Item | Status | Location of change |
|---|---|---|---|
| 1 | Stop logging FCM tokens, request/response bodies, bearer references | ✅ Code fix | `SignUpActivity.java`, `TokenRegistrar.java`, `MyFirebaseService.java` |
| 2 | Audit Firestore security rules | ✅ Rules drafted | `firestore.rules` (new file at repo root) |
| 3 | Split `isVerified` from profile completion | ✅ Code fix | `User.java`, `ProfileViewModel.java`, `SignUpActivity.java`, `MainActivity.java`, `LoyaltyActivity.java`, `ProfileFragment.java` |
| 4 | Enforce backup policy | ✅ Code fix | `AndroidManifest.xml`, `backup_rules.xml`, `data_extraction_rules.xml` |
| 5 | Fix reward redemption (do not deduct points when not implemented) | ✅ Code fix | `RewarsdFragment.java` |
| 6 | Restrict Firebase API key by package + SHA | ⚠️ Console-only — see "Action required" below |

---

## Item 1 — Stop logging sensitive credentials

### What

Removed every `Log` statement that emitted any of:

* the raw FCM device token,
* the full HTTP request body sent to `/api/push/registerDevice` (which contains the token),
* the full HTTP response body (which can echo backend-issued IDs and the token),
* references to bearer tokens in failure paths.

Kept log statements that record only metadata such as HTTP status codes and operation names. These are useful for diagnostics and contain no credentials.

### Where

| File | Action |
|---|---|
| `app/src/main/java/com/example/loyaltyapp/SignUpActivity.java` (existing-user path, line ~67) | Removed `Log.i("FCM", "existing user getToken() -> " + t)`. Failure log no longer prints `e.getMessage()`. |
| `app/src/main/java/com/example/loyaltyapp/SignUpActivity.java` (fresh sign-in path, line ~158) | Removed `Log.i("FCM", "fresh sign-in getToken() -> " + fcmToken)`. Failure log no longer prints `e.getMessage()`. |
| `app/src/main/java/com/example/loyaltyapp/services/TokenRegistrar.java` (`ensureDevice`, line ~55) | Removed `Log.w(..., "getIdToken failed, sending without bearer: " + e.getMessage())` **and** removed the silent fallback `upsertDevice(url, body, null)`. We now skip the upsert entirely when we cannot prove identity, instead of sending unauthenticated device-registration requests. |
| `app/src/main/java/com/example/loyaltyapp/services/TokenRegistrar.java` (`upsertDevice`, line ~99 and ~109) | Removed `Log.i(TAG, "POST " + url + " body=" + body.toString())` and `Log.i(TAG, "registerDevice response code=... body=" + respStr)`. Only the HTTP status code is now logged on success; failures log a fixed string with no exception payload. |
| `app/src/main/java/com/example/loyaltyapp/services/MyFirebaseService.java` (`onNewToken`, line ~18) | Removed `Log.i("FCM", "onNewToken -> " + token)`. |

### Why

An FCM registration token is a long-lived device credential. Any process able to read logcat — USB debugging, crash reporters, third-party logging SDKs, or backups — can obtain it and impersonate the device for push delivery. The same applies to the request body, which serializes the token verbatim. Stripping these logs is a non-negotiable prerequisite to any production deployment.

The `Authorization: Bearer ...` fallback path in `TokenRegistrar.ensureDevice` was also removed because sending a `registerDevice` request without a Firebase ID token leaves the backend unable to verify which user owns the device, which would let any attacker register arbitrary FCM tokens against any account.

### How

Each removed log statement was replaced with either nothing (when the line was purely informational) or a generic message that records the event but not its payload (e.g. `"existing user getToken failed"` with no exception message appended). A single-line `// P0 security:` comment was placed adjacent to each modified call site so a future developer understands why these logs are deliberately empty and does not "fix" them by adding the token back for debugging.

---

## Item 2 — Firestore security rules

### What

A new `firestore.rules` file was added at the repository root. It enforces these invariants on the live database:

* The economy fields — `points`, `visits`, `isVerified`, `lastVisitTimestamp`, `createdAt` — cannot be written by any client, ever.
* A user can read and write **only** their own `users/{uid}` document, and only the profile metadata fields (`fullName`, `birthday`, `gender`, `phone`, `address`, `profileComplete`, `email`, `uid`, `updatedAt`).
* Activity history (`users/{uid}/activities/*`) is read-only to the owner; writes must come from server-side code with the Admin SDK.
* `menu_items`, `rewards_catalog`, `config/*`, and `meta/*` are world-readable but client-unwritable.
* `earn_codes` and `redeem_codes` can be read by signed-in users (so QR-scan flows work) but never written from the client. The earn/spend/redeem state machine has to live on the backend.
* `devices` (FCM token registry) is closed to clients entirely.
* A trailing default-deny rule blocks every collection not enumerated above.

### Where

* New file: `firestore.rules` at repository root.

### Why

The previous PROJECT_REPORT documented that no `firestore.rules` were deployed. Without rules, every read and write in `ScanRepository` and `RewardsRepository` succeeds for any authenticated user against any document, which means a user can:

* set their own `points` to any value,
* mark their own `isVerified` flag true,
* read and modify other users' profiles, balances, and activity history,
* invalidate redeem codes belonging to other users.

The rules in this file follow the principle of least privilege: clients see only their own data, write only the fields a UI form needs, and the economy is server-only. This is the canonical pattern for Firebase loyalty/wallet apps and the only correct enforcement layer once client-side transactions are abandoned (see item 5).

### How

The rules file uses helper functions (`isUser`, `onlyAllowedFields`, `doesNotTouch`) to keep individual `match` blocks readable. Allowed and protected field sets are declared once at the top so adding a new profile field requires a single edit. Inline comments explain the trust model.

### Action required after this commit

The rules file is in the repo but Firebase does not auto-deploy it. From the project root, run:

```bash
firebase deploy --only firestore:rules
```

Until this is executed, the rules have no effect. **This is the most important post-commit step in this report.**

---

## Item 3 — Split `isVerified` from profile completion

### What

Before this change, the same boolean (`isVerified`) was being used for two incompatible things:

1. Backend-set email verification trust claim.
2. Client-set "the user filled out the profile form" UI flag.

The result was that `ProfileViewModel.saveProfile` directly wrote `isVerified=true` to Firestore the moment the user typed their name and birthday — granting itself email verification trust without any email round-trip.

The fix introduces a separate field, `profileComplete`, owned by the client and used purely for UI routing. The `isVerified` field continues to exist but is now backend-owned and client-read-only. Firestore rules (item 2) enforce this.

### Where

| File | Change |
|---|---|
| `app/src/main/java/com/example/loyaltyapp/models/User.java` | Added `profileComplete` field, getter `isProfileComplete()`, and setter. Added comments distinguishing it from `isVerified`. |
| `app/src/main/java/com/example/loyaltyapp/viewmodels/ProfileViewModel.java` (line 65) | Changed `update.put("isVerified", true)` to `update.put("profileComplete", true)`. |
| `app/src/main/java/com/example/loyaltyapp/SignUpActivity.java` (`ensureUserDocAndRoute`) | Removed all `up.put("isVerified", ...)` writes. Reads `profileComplete` instead of `isVerified` when deciding whether to route the user to the profile tab. On document create, seeds empty profile fields and `profileComplete=false`; omits `points`/`visits`/`isVerified` so the backend remains their sole writer. |
| `app/src/main/java/com/example/loyaltyapp/MainActivity.java` (`handleUserDoc`) | Routing decision flipped from `getBoolean("isVerified")` to `getBoolean("profileComplete")`. |
| `app/src/main/java/com/example/loyaltyapp/LoyaltyActivity.java` (`handleUserDoc`) | Same routing flip. |
| `app/src/main/java/com/example/loyaltyapp/fragments/ProfileFragment.java` (`bindUser`) | Edit-card visibility flipped from `isVerified` to `profileComplete`. |

### Why

`isVerified` is a security-bearing claim — a `true` value should mean "this user proved control of an email account by clicking a link signed by our backend." When the client could self-issue this claim, the entire email-verification flow became window dressing: a fresh attacker could install the app, fill the form, set `isVerified=true` themselves, and pass any server-side check that trusted the flag. Splitting the concerns removes the conflation and lets Firestore rules enforce that only the backend can grant the trust claim.

### How

The split was applied conservatively: nothing in the existing model class was deleted (the `isVerified` field, getter, and setter remain so any backend-set value is still readable from `User`), and the new `profileComplete` field has the same shape. All call sites that previously used `user.isVerified()` for UI gating were re-pointed to `user.isProfileComplete()`. Unit tests in `ModelsUnitTest.java` continue to compile and run because the `isVerified` accessor still exists.

The `SignUpActivity.ensureUserDocAndRoute` create branch deliberately writes `profileComplete=false` and seeds empty strings for `fullName`/`birthday`/`gender`/`phone`/`address`. This matches the shape that the Firestore `create` rule permits and ensures no client-supplied truthy value can pre-populate the trust-bearing fields. The merge branch (existing user) writes only `uid`/`email`/`updatedAt` — all in the `allowedUserFields` set — so the `update` rule passes cleanly.

---

## Item 4 — Restrict backup

### What

* In the manifest, `android:allowBackup` was flipped from `true` to `false`.
* The placeholder content of `backup_rules.xml` (Android ≤ 11 auto-backup) was replaced with an explicit exclusion of every domain.
* The placeholder content of `data_extraction_rules.xml` (Android 12+ cloud backup and device-to-device transfer) was replaced with explicit exclusion of every domain in both sections.

### Where

| File | Change |
|---|---|
| `app/src/main/AndroidManifest.xml` (line 19) | `android:allowBackup="true"` → `android:allowBackup="false"`. |
| `app/src/main/res/xml/backup_rules.xml` | Replaced commented sample with explicit `<exclude>` for `root`, `file`, `database`, `sharedpref`, `external`. |
| `app/src/main/res/xml/data_extraction_rules.xml` | Replaced commented sample with explicit `<exclude>` for the same domains, inside both `<cloud-backup>` and `<device-transfer>`. |

### Why

Auto-backup uploads the application's private data directory (Firebase Auth tokens, the local FCM registration token, any SharedPreferences caches) to the user's Google Drive account. Anyone with access to that account — or an attacker who installs the app on a controlled device and then runs `adb restore` — can replay the credentials and impersonate the user.

For a loyalty app there is no UX gain from auto-backup: there is no document, no draft, no significant local state. Disabling it has zero user-visible downside and removes an entire credential-exfiltration vector.

### How

Setting `allowBackup="false"` is sufficient on its own to block backup at the framework level. The XML files were also tightened as defense-in-depth: if a future maintainer re-enables `allowBackup`, the explicit `<exclude>` blocks will still prevent sensitive data from being copied. A comment at the top of each XML file explains the policy.

---

## Item 5 — Reward redemption no longer deducts points

### What

In `RewarsdFragment.onRedeemClicked`, the previous code did two contradictory things:

1. Showed a Snackbar telling the user "bientôt!" (coming soon).
2. Then called `redeemReward(r)`, which ran the full transaction in `RewardsRepository.submitRedemption`, deducting points from the user's balance and writing a `redemption` activity log.

The fix removes the call to `redeemReward(r)` from the click handler. The Snackbar text was rewritten to "Redemption coming soon. Your points are safe." so the message matches reality. The unused private `redeemReward(@NonNull final Rewards r)` wrapper method was also deleted from the fragment.

The infrastructure underneath — `RewardsViewModel.redeemReward`, `RewardsRepository.submitRedemption`, and the `RedemptionState` LiveData — was left in place so a future implementation can wire a backend redemption endpoint into the same plumbing without rebuilding it.

### Where

* `app/src/main/java/com/example/loyaltyapp/fragments/RewarsdFragment.java`, method `onRedeemClicked` and the now-deleted private `redeemReward` helper.

### Why

The original P0 directive said: "Fix reward redemption so it is either fully implemented or does not deduct points." Building the full flow (backend redeem code issuance, cashier scan, cancel/expire lifecycle) is well outside the scope of a P0 security patch — that work is P1 per the project report. The remaining sensible action is to ensure the existing flow does no harm.

There is also a hard correctness reason: once Firestore rules from item 2 are deployed, `RewardsRepository.submitRedemption` will fail on the `transaction.update(userRef, "points", ...)` line, because the client-side rule blocks writes to `points`. Leaving the call wired up would produce confusing user-facing redemption failures with no points actually moved — a worse experience than transparently telling the user the feature is not ready yet.

### How

The fragment-level change is minimal and reversible: remove the call, rewrite the message, delete the now-dead helper. No model or repository code was modified, so when the backend endpoint exists, the wiring is a single edit (re-add `viewModel.redeemReward(r);` and point it at the new server-issued flow). A comment was placed above the click handler explaining the deliberate disablement, why it must not be re-enabled before the backend exists, and the Firestore-rule reason.

---

## Item 6 — Restrict Firebase API key (Console action, NOT code)

### What

The Android API key in `google-services.json` is committed to source control. This is correct and expected for Firebase Android apps — the key is identifying, not authenticating. **The risk is that the key is currently unrestricted in Google Cloud, meaning it can be lifted from a decompiled APK and used to call Firebase services from any client.**

There is no code change that can fix this. The mitigation lives in the Google Cloud Console.

### Steps to take

1. Open Google Cloud Console → **APIs & Services → Credentials**.
2. Select the Android API key associated with `com.example.loyaltyapp`.
3. Under **Application restrictions**, choose **Android apps** and add:
   * Package name: `com.example.loyaltyapp`
   * SHA-1 of the debug signing cert
   * SHA-1 of the release signing cert
   To obtain both, run from the project root: `./gradlew signingReport`.
4. Under **API restrictions**, choose **Restrict key** and enable only the Firebase services this app actually uses:
   * Firebase Installations API
   * Firebase Cloud Messaging API
   * Identity Toolkit API
   * Cloud Firestore API
5. Save.

### Why

Without these restrictions, a leaked or extracted API key is reusable from any attacker-controlled context: a scraper, a different app, a desktop script. With package + SHA restrictions in place, the key only works when the calling APK is signed by your release key, which an attacker cannot forge.

---

## Files changed in this commit

```
app/src/main/AndroidManifest.xml
app/src/main/java/com/example/loyaltyapp/LoyaltyActivity.java
app/src/main/java/com/example/loyaltyapp/MainActivity.java
app/src/main/java/com/example/loyaltyapp/SignUpActivity.java
app/src/main/java/com/example/loyaltyapp/fragments/ProfileFragment.java
app/src/main/java/com/example/loyaltyapp/fragments/RewarsdFragment.java
app/src/main/java/com/example/loyaltyapp/models/User.java
app/src/main/java/com/example/loyaltyapp/services/MyFirebaseService.java
app/src/main/java/com/example/loyaltyapp/services/TokenRegistrar.java
app/src/main/java/com/example/loyaltyapp/viewmodels/ProfileViewModel.java
app/src/main/res/xml/backup_rules.xml
app/src/main/res/xml/data_extraction_rules.xml
docs/P0_SECURITY_FIXES.md          (new — this file)
firestore.rules                    (new)
```

## Verification

* `./gradlew.bat :app:compileDebugJavaWithJavac` — BUILD SUCCESSFUL (only pre-existing JDK-21/source-8 deprecation warnings).
* Unit tests were not run as part of this change. The existing test suite was already failing per `PROJECT_REPORT.md` (14 of 21 failing on unrelated Mockito/Robolectric issues) and stabilising it is a P1 item.

## Required follow-up actions outside the repo

1. **Deploy the new Firestore rules** — `firebase deploy --only firestore:rules`. Until this runs, the database remains open and items 2, 3, and 5 are not enforced.
2. **Restrict the Firebase API key** — item 6 above, Google Cloud Console.
3. **Move `isVerified` writes to the backend** — the `/api/verify` endpoint should set `isVerified=true` on the user document using the Admin SDK once the email-verify token is exchanged. Without this, the field will remain `false` forever for new users and any server-side check that depends on it will fail closed.
4. **Move the earn/spend/redeem economy to the backend** — once rules are deployed, `ScanRepository.submitRedemption`, `RewardsRepository.submitRedemption`, and the birthday-bonus path will all fail on the client. These need server endpoints that perform the Firestore transactions with the Admin SDK. This is the natural follow-up implementation work and was already flagged as P1 / "Suggested implementation order step 2" in `PROJECT_REPORT.md`.

## Threat model after these fixes

| Threat | Before | After |
|---|---|---|
| Logcat / crash-reporter exfiltrates FCM token | Token printed verbatim 5+ times | No token ever logged |
| Logcat / crash-reporter exfiltrates request body | Full body with token logged | No body logged |
| Backend accepts unauthenticated device registrations | Yes (silent bearer fallback) | No (skipped when no ID token) |
| Client self-grants `isVerified=true` | Yes, written on first profile save | No, field is backend-only and rule-enforced |
| Client mutates own `points` / `visits` | Yes, via transaction | No, blocked by rules (after deploy) |
| Client reads other users' profiles | Yes | No, blocked by rules (after deploy) |
| Client mutates other users' redeem codes | Yes | No, blocked by rules (after deploy) |
| Reward UI deducts points with no redemption issued | Yes | No, click is a no-op until backend exists |
| Google Drive backup leaks auth state + FCM token | Yes (`allowBackup=true`, no excludes) | No (`allowBackup=false`, full excludes) |
| Leaked API key reusable from any context | Yes | No, once console restrictions applied (item 6) |
