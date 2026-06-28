# Loyalty App Project Report

Generated on 2026-06-27 from the current workspace at `C:\Users\pc\AndroidStudioProjects\loyaltyapp`.

## Executive summary

This is a Java Android loyalty app for a cafe or coffee shop. It uses Firebase Auth, Firestore, Firebase Messaging, Retrofit/OkHttp, ZXing QR scanning, Glide image loading, ViewBinding, ViewModels, LiveData, repositories, RecyclerView adapters, and XML layouts.

The main app currently compiles:

- `./gradlew.bat :app:compileDebugJavaWithJavac` passed.
- `./gradlew.bat :app:lintDebug` passed and generated `app/build/reports/lint-results-debug.html`.
- `./gradlew.bat :app:testDebugUnitTest` failed: 21 tests ran, 14 failed.

The biggest project risks are not syntax errors. They are incomplete feature paths, weak production hardening, test suite instability, package/name inconsistency, and security/privacy issues around logs, backup, exported components, and client-side Firestore write authority.

## What the app does now

The app flow is:

1. `MainActivity` shows a splash screen and decides whether to open sign-up or the loyalty area.
2. `SignUpActivity` sends an email to a backend endpoint, receives a deep link `myapp://verify?token=...`, exchanges the token for a Firebase custom token, signs in, initializes a Firestore user profile, and registers an FCM device token.
3. `LoyaltyActivity` hosts the main bottom navigation tabs manually using fragments.
4. `HomeFragment` shows a dynamic Firestore banner, a menu grid, and a punch-card visit tracker.
5. `ScanFragment` scans QR codes and calls `ScanViewModel`.
6. `ScanRepository` updates Firestore in transactions for earn and spend QR flows.
7. `RewarsdFragment` lists rewards and redeems them through `RewardsViewModel` and `RewardsRepository`.
8. `ProfileFragment` lets the user complete profile fields and marks `isVerified=true`.
9. `ActivityFragment` displays points, visits, and activity history.

## Project structure

Important files and modules:

- `app/build.gradle.kts`: Android app module configuration and dependencies.
- `gradle/libs.versions.toml`: version catalog.
- `app/src/main/AndroidManifest.xml`: permissions, exported components, activities, FCM service.
- `app/src/main/java/com/example/loyaltyapp`: activities, API client/service DTOs.
- `app/src/main/java/com/example/loyaltyapp/fragments`: UI fragments.
- `app/src/main/java/com/example/loyaltyapp/viewmodels`: screen state and UI-facing logic.
- `app/src/main/java/com/example/loyaltyapp/data/repository`: Firestore/backend repositories.
- `app/src/main/java/com/example/loyaltyapp/models`: Firestore/UI models.
- `app/src/main/java/com/example/loyaltyapp/adapters`: RecyclerView adapters, though `MenuAdapter` declares package `com.example.loyaltyapp.ui`.
- `app/src/test/java`: local unit tests.

## Build and dependency status

Current build setup:

- `compileSdk = 34`, `targetSdk = 34`, `minSdk = 24`.
- Android Gradle Plugin `8.6.1`.
- Java source/target compatibility is Java 8.
- Firebase Auth, Firestore, Messaging are used without Firebase BOM.
- Retrofit and OkHttp are both present.
- Glide, ZXing, Navigation, Material, AppCompat, ConstraintLayout, Robolectric, Mockito are present.

Things to fix:

1. Remove duplicate dependencies in `app/build.gradle.kts`.
   - `firebase.auth` appears twice.
   - `firebase.firestore` appears twice.
   - `androidx.core.ktx` is included even though the app is Java-only.

2. Consider using the Firebase BOM.
   - Current Firebase versions are manually pinned. A BOM reduces version mismatch risk across Auth, Firestore, and Messaging.

3. Enable release hardening.
   - `release.isMinifyEnabled = false`.
   - For production, enable minification/resource shrinking after validating ProGuard/R8 rules.

4. Update Java compatibility when convenient.
   - Gradle warns that Java 8 source/target under JDK 21 is obsolete. Moving to Java 17 is reasonable for a modern Android project, but should be done after tests are stable.

## Verified command results

Passed:

```text
./gradlew.bat :app:compileDebugJavaWithJavac
```

Result: compile succeeded with Java 8 deprecation warnings.

Passed:

```text
./gradlew.bat :app:lintDebug
```

Result: lint succeeded. Report written to `app/build/reports/lint-results-debug.html`.

Failed:

```text
./gradlew.bat :app:testDebugUnitTest
```

Result:

```text
21 tests completed, 14 failed
```

Failing areas:

- `ActivityEventTest`: Mockito cannot mock `DocumentSnapshot` cleanly in this environment.
- `MainViewModelTest`: Mockito cannot mock Firebase Auth/User cleanly.
- `RewardsViewModelTest`: Mockito setup failure.
- `ScanViewModelTest`: Mockito setup failure.
- `MenuAdapterTest`: `Resources$NotFoundException` while creating/binding views under Robolectric.

## Architecture assessment

What is good:

- The app mostly follows MVVM: fragments observe ViewModels, ViewModels call repositories.
- Firestore listeners are usually cleaned up in repository `cleanup()` methods.
- Points and QR code state changes are done with Firestore transactions in `ScanRepository`.
- Profile, rewards, menu, activity, scan, and config responsibilities are separated into different repositories/ViewModels.
- ViewBinding is enabled, which reduces raw `findViewById` errors.

What needs improvement:

1. Manual fragment navigation is fragile.
   - `LoyaltyActivity` manually hides/shows fragments and uses `commitAllowingStateLoss()`.
   - `commitAllowingStateLoss()` can drop state updates during lifecycle transitions.
   - The project already includes Android Navigation dependencies and `main_navigation.xml`, but the main flow does not use Navigation properly.

2. ViewModels still directly depend on Firebase static APIs in places.
   - This makes tests harder and is one reason the unit tests are brittle.
   - Move auth/session access behind small interfaces or repositories.

3. `RewardsViewModel` uses `observeForever`.
   - This observer is never removed.
   - It can leak the ViewModel or keep updates alive longer than intended.

4. Naming and packaging are inconsistent.
   - `RewarsdFragment` and `fragment_rewarsd.xml` are misspelled.
   - `MenuAdapter.java` is located under `adapters` but declares package `com.example.loyaltyapp.ui`.
   - Package/file layout should match to keep imports, tests, and IDE refactors reliable.

5. There is duplicated app status logic.
   - `MainActivity` listens to `meta/app_status`.
   - `LoyaltyActivity` also observes app status through `MainViewModel`.
   - This should be centralized so the blocking behavior is consistent.

## Security and privacy findings

1. FCM tokens and request bodies are logged.
   - `SignUpActivity` logs FCM tokens.
   - `TokenRegistrar` logs the whole device registration request body, including the token.
   - Fix: never log tokens, bearer tokens, verification tokens, request bodies containing credentials, or full backend responses in production.

2. The backend base URL is hardcoded in two places.
   - `ApiClient.BASE_URL`.
   - `TokenRegistrar.API_BASE`.
   - Fix: move this to `BuildConfig` fields or Gradle product flavors, with separate dev/staging/prod values.

3. Backup is enabled.
   - `android:allowBackup="true"`.
   - The backup rules still contain placeholder TODO guidance.
   - Fix: decide what data can be backed up. Exclude sensitive local state and tokens. For a loyalty/account app, consider disabling backup unless there is a clear reason to keep it.

4. Exported components need review.
   - `MyFirebaseService` is exported.
   - `SignUpActivity` is exported for deep links.
   - `MainActivity` is exported as launcher.
   - Fix: keep only what must be exported. FCM services normally do not need broad external access. The deep link activity should validate token format, scheme, host, and state carefully.

5. Client-side code can set `isVerified=true`.
   - `ProfileViewModel.saveProfile()` writes `isVerified=true`.
   - This may be acceptable if "verified" means profile complete, but dangerous if it means email/account verification.
   - Fix: split fields into `profileComplete` and `emailVerified` or enforce `isVerified` only from the backend/security rules.

6. Points security depends heavily on Firestore rules.
   - Transactions are good, but they run on the client.
   - If Firestore rules allow broad writes, users could manipulate points, visits, rewards, or activity logs.
   - Fix: ensure rules prevent users from directly changing points/visits/reward status except through tightly validated transaction shapes, or move point mutation fully to backend/Cloud Functions.

7. Firebase API key is committed in `google-services.json`.
   - This is normal for Firebase Android apps, but it must be restricted in Google Cloud/Firebase settings.
   - Fix: restrict API key usage to the Android app package name and SHA fingerprints.

## Functional gaps and bugs

1. Reward redemption says it is not implemented, then still redeems.
   - `RewarsdFragment.onRedeemClicked()` shows a "bientot" Snackbar but also calls `redeemReward(r)`.
   - Fix: either make it a real redemption flow or do not deduct points yet.

2. Reward redemption only deducts points and logs an activity.
   - There is no issued redeem code, cashier validation, expiration, status lifecycle, or backend confirmation.
   - Fix: create a real redemption model: pending code, expiration, cashier scan/confirm, completed/cancelled status.

3. Birthday reward is checked on every `LoyaltyActivity` startup.
   - This is acceptable only if the backend is fully idempotent.
   - Fix: make the backend enforce one claim per user per year and return clear user-facing status.

4. Manual QR entry is not implemented.
   - `ScanFragment` shows "Manual entry is not implemented yet".
   - Fix: add a dialog/manual input flow or remove the UI.

5. Home menu item click is unfinished.
   - `HomeFragment` has a TODO for item details/add-to-cart.
   - Fix: implement product details or remove click affordance.

6. User manual fragment is still template-generated.
   - `UserManualFragment` and `fragment_user_manual.xml` contain placeholder TODOs.
   - Fix: delete if unused, or replace with actual help/manual content.

7. Profile edit behavior is limited.
   - Once `isVerified` is true, the edit card is hidden.
   - Users may not be able to update phone/address later.
   - Fix: separate first-time profile completion from later edit profile.

8. Birthday validation is weak.
   - The date picker allows future dates.
   - Fix: cap date to today or require a realistic age range.

9. Phone formatting can duplicate country code.
   - Validation expects local Moroccan `05/06/07...`, then saves `+212 ` plus the original number.
   - Fix: normalize to a single canonical E.164 format, for example `+2126xxxxxxxx`.

10. Activity type names are inconsistent.
   - Scan logs use `earn` and `spend`.
   - Rewards logs use `redemption`.
   - `ActivityViewModel` comments mention `scan|redemption|bonus`.
   - Fix: define one enum/schema for activity types and update filters/adapters.

## Test suite assessment

Current tests are useful in intent but brittle in implementation.

Main causes:

1. Tests mock final Android/Firebase classes directly.
   - `DocumentSnapshot`, `FirebaseAuth`, and `FirebaseUser` mocking fails in the current Java/Robolectric/Mockito combination.
   - Fix: wrap Firebase SDK objects behind interfaces or use fake repositories.

2. Robolectric resource setup for `MenuAdapterTest` is broken.
   - The adapter inflates `MenuItemBinding`; the test creates a bare RecyclerView context and hits a resource exception.
   - Fix: use a Robolectric Activity context, verify layout resources exist, and align package/path naming.

3. Tests are not isolated from Android/Firebase implementation details.
   - Better tests would exercise ViewModels using fake repositories and plain data objects.

Recommended test plan:

1. Create small interfaces:
   - `AuthProvider` with `getCurrentUid()`.
   - `UserDataSource`, `RewardsDataSource`, `ScanDataSource`, `ConfigDataSource`.

2. Inject those interfaces into ViewModels.

3. Use simple fake implementations in unit tests.

4. Keep Firebase SDK behavior in integration tests or repository tests with emulator support.

5. Add a Firebase emulator test plan for:
   - earn voucher redemption,
   - spend redemption,
   - insufficient points,
   - expired/used voucher,
   - user cannot redeem another user's code.

## Data model and Firestore collections

The app expects these collections/documents:

- `users/{uid}`
  - `uid`, `email`, `fullName`, `birthday`, `gender`, `points`, `visits`, `isVerified`, `phone`, `address`, `createdAt`, `updatedAt`, `lastVisitTimestamp`

- `users/{uid}/activities/{activityId}`
  - mixed schemas currently:
  - earn/spend logs use `type`, `points`, `voucherId`, `redeemCodeId`, `item`, `ts`
  - reward logs use `type`, `delta`, `desc`, `rewardId`, `status`, `ts`

- `menu_items/{id}`
  - `name`, `priceMAD`, `category`, `imageUrl`, `isAvailable`, `isPopular`, `popularityScore`

- `rewards_catalog/{id}`
  - `active`, `category`, `redeemPoints`, `name`, `imagePath`, `description`, `termsUrl`, `expirationDays`

- `earn_codes/{voucherId}`
  - `status`, `validForSec`, `createdAt`, `points`, `redeemedAt`, `redeemedByUid`

- `redeem_codes/{redeemDocId}`
  - `userUid`, `status`, `type`, `costPoints`, `itemName`, `completedAt`, `completedByUid`

- `config/home_banner`
  - `active`, `startAt`, `endAt`, `badge`, `title`, `subtitle`, `textColor`, `startColor`, `endColor`, `iconUrl`, `iconVersion`, `deeplink`

- `meta/app_status`
  - `isActive`, `message`

Schema fix needed:

- Document these fields in a single schema file.
- Normalize activity log field names.
- Decide whether `points` means total balance, delta, or both depending on collection.
- Add Firestore indexes explicitly where required by menu/reward queries.

## UI and UX assessment

What is working:

- The app has a coherent tab structure.
- ViewBinding is used consistently.
- Rewards, menu, profile, activity, scan, and home screens are separated.
- The scan screen has success/error overlays and debounce logic.

Things to fix:

1. Correct misspellings and mojibake/encoding issues.
   - `RewarsdFragment` should be `RewardsFragment`.
   - `fragment_rewarsd.xml` should be `fragment_rewards.xml`.
   - Some strings/comments show corrupted text such as broken "bientot" text and broken arrows.

2. Replace placeholder screens/actions.
   - Help, terms, privacy, saved rewards, notifications, manual entry, and menu item details are placeholder-only.

3. Improve empty/loading/error states.
   - Some repositories log errors but do not expose them to the UI.
   - Menu and banner errors should show graceful fallback states.

4. Avoid `notifyDataSetChanged()` where possible.
   - `MenuAdapter` should use `ListAdapter` and `DiffUtil`, like `RewardAdapter`.

5. Make profile editable after completion.

6. Use one navigation system.
   - Prefer Android Navigation or a cleaner manual tab controller, not both dependencies plus manual fragment transactions.

## Code quality findings

Priority cleanup:

1. Fix package/folder mismatch.
   - Move `MenuAdapter` to package `com.example.loyaltyapp.adapters` or move the file to a matching `ui` folder.

2. Rename misspelled classes/layouts.
   - `RewarsdFragment` -> `RewardsFragment`.
   - `fragment_rewarsd.xml` -> `fragment_rewards.xml`.

3. Remove unused imports and commented/dead code.
   - Several fragments import unused classes.

4. Replace stringly typed maps for backend responses.
   - `Map<String, Object>` responses are fragile.
   - Create DTOs for register, birthday reward, and push registration responses.

5. Centralize constants.
   - Collection names, field names, backend URL, request codes, reward thresholds, and activity types should be constants.

6. Make repositories easier to test.
   - Inject `FirebaseFirestore`, `FirebaseAuth`, and API clients instead of creating them statically in classes.

7. Fix lifecycle concerns.
   - Remove `observeForever` or unregister it in `onCleared`.
   - Avoid `commitAllowingStateLoss`.

## Prioritized fix list

### P0 - Must fix before real users

1. Stop logging FCM tokens, request bodies, bearer-related data, and backend response bodies.
2. Audit Firestore security rules for points, visits, rewards, redeem codes, earn codes, and profile fields.
3. Split `isVerified` from profile completion, or ensure only backend/security rules can set true verification.
4. Decide and enforce backup policy; disable or restrict backup for sensitive data.
5. Fix reward redemption so it is either fully implemented or does not deduct points.
6. Restrict Firebase API key by package name and SHA fingerprints.

### P1 - Fix before release candidate

1. Repair the unit test suite.
2. Move backend URL into Gradle `BuildConfig` or flavors.
3. Normalize activity log schema and reward redemption schema.
4. Remove `observeForever` leak risk.
5. Replace `commitAllowingStateLoss`.
6. Rename `RewarsdFragment` and `fragment_rewarsd.xml`.
7. Fix package/folder mismatch for `MenuAdapter`.
8. Remove duplicate dependencies and unused KTX dependency.
9. Implement or remove placeholder UI actions.
10. Enable release minification/resource shrinking and verify release build.

### P2 - Quality improvements

1. Convert menu adapter to `ListAdapter`/`DiffUtil`.
2. Add Firebase emulator integration tests.
3. Add loading/error states to menu/config flows.
4. Add real help, terms, privacy, notifications, saved rewards screens or hide the rows.
5. Add date constraints and phone normalization.
6. Add explicit Firestore index documentation.
7. Consider moving to Java 17.

## Suggested implementation order

1. Security cleanup:
   - remove sensitive logs,
   - externalize backend URL,
   - review manifest backup/exported components,
   - validate Firestore rules.

2. Fix reward/redemption business logic:
   - define schema,
   - issue redeem codes,
   - confirm at cashier/admin/backend,
   - align activity logs.

3. Stabilize tests:
   - introduce interfaces/fakes,
   - remove direct Firebase SDK mocks,
   - fix Robolectric adapter tests.

4. Rename and package cleanup:
   - `RewarsdFragment`,
   - `fragment_rewarsd.xml`,
   - `MenuAdapter` package.

5. UX completion:
   - placeholders,
   - profile edit,
   - menu details/manual entry/help/legal screens.

## Current repository state note

Before this report was added, `git status --short` showed:

```text
 M .idea/misc.xml
```

That appears to be a pre-existing workspace change and was not modified for this report.
