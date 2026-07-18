# Loyalty App Project Documentation

## Overview

This repository contains an Android loyalty application for coffee shops and cafes. Customers sign in through an email verification link, complete a profile, browse a live menu, scan QR codes to earn points, view activity history, and browse rewards. The app uses Firebase Auth, Firestore, Firebase Cloud Messaging, Retrofit/OkHttp, Glide, ZXing, AndroidX, and Material Components.

The codebase follows a practical MVVM structure:

- `Activity` and `Fragment` classes own Android lifecycle and screen rendering.
- `ViewModel` classes expose `LiveData` state to screens.
- `Repository` classes own Firestore, Firebase Auth, and transaction access.
- `models` define Firestore/API data shapes.
- `adapters` bind lists into RecyclerViews.

## Main User Flows

### App Launch and Access Gate

`MainActivity` shows a splash screen, listens to `meta/app_status`, and routes users based on Firebase Auth state and the Firestore user document. If the app is globally disabled, it opens `BlockedActivity`. If no user is signed in, it opens `SignUpActivity`. If a signed-in user has not completed their profile, it opens `LoyaltyActivity` with the profile tab required.

### Email Verification Sign-In

`SignUpActivity` submits an email to the backend endpoint `api/register`. The backend sends a verification email containing a `myapp://verify?token=...` deep link. When opened, the app calls `api/verify`, receives a Firebase custom token, signs in with Firebase Auth, initializes the user document, registers the FCM token, and enters the main app.

### Main Loyalty Shell

`LoyaltyActivity` hosts five bottom-navigation tabs:

- Home
- Activity
- Scan
- Rewards
- Profile

It keeps fragment instances cached, blocks navigation away from Profile until required profile fields are completed, checks the app-status gate, asks for notification permission on Android 13+, and calls the birthday reward backend endpoint at startup.

### Home and Menu

`HomeFragment` displays a dynamic banner from Firestore `config/home_banner`, popular/category-filtered menu items from `menu_items`, and a punch-card visualization based on the user visit count.

### QR Scanning

`ScanFragment` uses ZXing's `DecoratedBarcodeView`, camera permission handling, flashlight controls, haptics, and success/error overlays. `ScanViewModel` parses scanned content:

- Plain code: earn-code flow against `earn_codes/{voucherId}`.
- `REDEEM|codeId|uid|cost`: spend/redeem-code flow against `redeem_codes/{redeemId}`.

`ScanRepository` performs Firestore transactions to validate code state, update user points, count visits with a 4-hour visit window, and write normalized activity entries.

### Rewards

`RewardsFragment` displays active rewards from `rewards_catalog`, filters by category, shows current user points, and calculates progress toward the cheapest reward. Client-side direct redemption is intentionally disabled in the fragment; the current UI shows a "coming soon" message when the user has enough points.

`RewardsRepository` still contains a transaction-based `submitRedemption` method, but current Firestore rules block client-side economy writes. Treat backend/server-confirmed redemption as the intended production direction.

### Profile

`ProfileFragment` displays and completes profile data. It validates Moroccan phone numbers, formats them with `+212`, writes only profile-owned fields, sets `profileComplete=true`, and notifies `LoyaltyActivity` when the profile is complete. It also exposes placeholder rows for notifications, saved rewards, help, terms, and privacy.

### Activity History

`ActivityFragment` shows current points, visits, last scan time, level, and a filtered activity list. It supports pull-to-refresh, type filtering, this-week/this-month/custom-date filters, and a "Scan now" shortcut.

## Backend and Data Dependencies

### Retrofit Backend

The backend base URL is configured by `BuildConfig.API_BASE_URL` in `app/build.gradle.kts`.

Known endpoints:

- `POST api/register`: request email verification.
- `POST api/verify`: exchange verification token for a Firebase custom token.
- `POST api/rewards/birthday`: claim birthday reward.
- `POST api/push/registerDevice`: register/update an FCM device token through `TokenRegistrar`.

### Firestore Collections

- `users/{uid}`: user profile, points, visits, timestamps, profile-completion state.
- `users/{uid}/activities/{activityId}`: normalized activity history.
- `menu_items/{itemId}`: public menu catalog.
- `rewards_catalog/{rewardId}`: public rewards catalog.
- `config/home_banner`: dynamic home banner config.
- `meta/app_status`: global app active/blocked status.
- `earn_codes/{voucherId}`: server-issued earn codes.
- `redeem_codes/{redeemId}`: server-issued redeem codes.
- `devices/{deviceId}`: server-managed FCM device records.

## Security Model

The project separates client-owned profile fields from backend-owned trust/economy fields.

- `profileComplete` is a client-owned UI routing flag.
- `isVerified` is a backend-owned trust flag.
- `points`, `visits`, `lastVisitTimestamp`, and economy mutations should be server-owned.
- `allowBackup=false` is set in the manifest.
- Backup and Android 12+ data extraction rules exclude all domains.
- FCM tokens are intentionally not logged.
- Release builds enable R8 and resource shrinking.

Important caveat: current `firestore.rules` deny client writes to `earn_codes`, `redeem_codes`, user activity subcollections, and protected user economy fields. Code paths that perform economy mutations directly from the Android client must be aligned with deployed rules or moved fully behind trusted backend APIs.

## Build and Test

### Build System

This is a single-module Gradle Android project:

- Root project name: `loyalty app`
- Module: `:app`
- Namespace/application id: `com.example.loyaltyapp`
- Min SDK: 24
- Target/compile SDK: 34
- Java compatibility: 1.8
- ViewBinding: enabled
- BuildConfig: enabled

### Key Dependencies

- AndroidX AppCompat, Activity, Core, ConstraintLayout, Navigation, SwipeRefreshLayout.
- Material Components.
- Firebase Auth, Firestore, Messaging.
- Retrofit, Gson converter, OkHttp.
- ZXing Embedded and ZXing Core for QR scanning.
- Glide for image loading.
- JUnit, Robolectric, Mockito, AndroidX test core, Arch core testing.

### Test Coverage

Unit tests exist for:

- API client construction.
- Data models and DTO placeholders.
- `ActivityEvent` normalized and legacy mapping.
- `MenuAdapter` count/binding.
- `MainViewModel`, `RewardsViewModel`, and `ScanViewModel` flows.

Instrumentation contains the default app-context package test.

## Directory Structure

```text
.
|-- app/
|   |-- build.gradle.kts
|   |-- google-services.json
|   |-- proguard-rules.pro
|   `-- src/
|       |-- main/
|       |   |-- AndroidManifest.xml
|       |   |-- java/com/example/loyaltyapp/
|       |   `-- res/
|       |-- test/
|       `-- androidTest/
|-- docs/
|-- firestore.rules
|-- gradle/
|-- build.gradle.kts
|-- settings.gradle.kts
`-- README.md
```

## File Responsibilities

### Root and Gradle Files

| File | Responsibility |
| --- | --- |
| `README.md` | High-level project overview, feature summary, tech stack, and structure notes. |
| `settings.gradle.kts` | Defines plugin repositories, dependency repositories, root project name, and includes `:app`. |
| `build.gradle.kts` | Root Gradle plugin declarations for Android application and Google Services. |
| `gradle/libs.versions.toml` | Central version catalog for plugins and libraries. |
| `gradle.properties` | Gradle/Android build properties. |
| `gradlew`, `gradlew.bat` | Gradle wrapper launchers. |
| `gradle/wrapper/gradle-wrapper.jar` | Gradle wrapper binary. |
| `gradle/wrapper/gradle-wrapper.properties` | Gradle wrapper distribution config. |
| `firestore.rules` | Firestore security rules for users, profile updates, public catalogs, app config, codes, activities, and devices. |
| `PROJECT_REPORT.md` | Existing generated or manual project report file. |
| `docs/P0_SECURITY_FIXES.md` | Existing security-fix documentation. |
| `build_log.txt`, `buildError.log`, `compile*.txt`, `compile*.log`, `error_detail*.txt`, `test_log.txt` | Local build/test/error logs; useful for troubleshooting, not app runtime source. |

### App Module Config

| File | Responsibility |
| --- | --- |
| `app/build.gradle.kts` | Android module configuration, SDK levels, build types, `API_BASE_URL`, ViewBinding, dependencies, and test dependencies. |
| `app/google-services.json` | Firebase app configuration consumed by the Google Services plugin. |
| `app/proguard-rules.pro` | R8/ProGuard keep rules for Firestore models, Retrofit/Gson, Firebase, Glide, ZXing, and ViewBinding. |
| `app/src/main/AndroidManifest.xml` | Declares permissions, camera features, backup policy, FCM service, activities, launcher, and verification deep link. |

### Activities and API Classes

| File | Responsibility |
| --- | --- |
| `MainActivity.java` | Splash and routing activity; checks auth, profile completion, and app status. |
| `SignUpActivity.java` | Email verification sign-in flow, deep-link token handling, custom-token Firebase sign-in, initial user document creation, and FCM registration. |
| `LoyaltyActivity.java` | Main tab host, fragment switching, profile-completion navigation gate, birthday reward check, app-status observer, and notification permission request. |
| `BlockedActivity.java` | Full-screen disabled-app state with support email and quit actions. |
| `ApiClient.java` | Retrofit singleton configured from `BuildConfig.API_BASE_URL`. |
| `ApiService.java` | Retrofit endpoint interface and nested verify response DTO. |
| `EmailRequest.java` | Simple email request DTO with `email` and `uid`; currently not used by `ApiService`, which uses maps. |
| `EmailResponse.java` | Simple response DTO with `ok` and `error`; currently not used by active API calls. |
| `VerifyRequest.java` | Simple verify request DTO with `token` and `uid`; currently not used by active API calls. |
| `VerifyResponse.java` | Simple response DTO with `ok` and `error`; separate from `ApiService.VerifyResponse` and currently not used by active verify flow. |

### Fragments

| File | Responsibility |
| --- | --- |
| `fragments/HomeFragment.java` | Home screen; binds menu list, category chips, dynamic banner, Glide banner icon, deeplink click, and punch-card UI. |
| `fragments/ActivityFragment.java` | Activity/history screen; binds stats, activity list, loading/empty states, filters, custom date picker, and scan shortcut. |
| `fragments/ScanFragment.java` | QR scanner screen; manages camera permission, ZXing scanner lifecycle, debounce, flashlight, haptics, success/error overlays, and retry/manual-entry placeholders. |
| `fragments/RewardsFragment.java` | Rewards catalog screen; binds reward filters, user points, progress to next reward, loading/error state, and disabled client-side redemption messaging. |
| `fragments/ProfileFragment.java` | Profile screen; displays user data, validates and saves required profile fields, signs out, and shows placeholder shortcut snackbars. |

### ViewModels

| File | Responsibility |
| --- | --- |
| `viewmodels/MainViewModel.java` | Exposes app status and current user to `LoyaltyActivity`; cleans up config/user listeners. |
| `viewmodels/HomeViewModel.java` | Exposes menu data, banner config, and user data to Home; loads popular/category menu items. |
| `viewmodels/ActivityViewModel.java` | Loads user stats/history, tracks loading/error state, filters activities by type/date, and computes last scan time. |
| `viewmodels/ScanViewModel.java` | Parses scanned QR payloads, validates auth/account ownership, calls earn/spend repository transactions, and exposes scanner UI state. |
| `viewmodels/RewardsViewModel.java` | Loads/filter rewards, listens to user points, exposes redemption state, and removes `observeForever` observer on clear. |
| `viewmodels/ProfileViewModel.java` | Validates required profile fields and Moroccan phone format, writes profile updates, and exposes save state. |

### Repositories

| File | Responsibility |
| --- | --- |
| `data/repository/ActivityRepository.java` | Reads user point/visit stats and latest 200 activity documents. |
| `data/repository/ConfigRepository.java` | Listens to `config/home_banner` and `meta/app_status`; owns listener cleanup. |
| `data/repository/MenuRepository.java` | Listens to available popular menu items or category-filtered menu items. |
| `data/repository/RewardsRepository.java` | Fetches active rewards, applies server/client sorting fallback, parses reward documents, and contains transaction-based redemption logging. |
| `data/repository/ScanRepository.java` | Executes earn/spend Firestore transactions, validates voucher/redeem-code state, updates points/visits, and writes activity logs. |
| `data/repository/UserRepository.java` | Listens to the signed-in user's Firestore document and saves allowed profile updates. |

### Models

| File | Responsibility |
| --- | --- |
| `models/User.java` | Firestore user profile model with auth/profile fields, points, visits, `isVerified`, and `profileComplete`. |
| `models/MenuItemModel.java` | Firestore menu item model for name, price, category, image, availability, popularity. |
| `models/Rewards.java` | Reward catalog model with id, name, price, redeem cost, image path, category, and active flag. |
| `models/ActivityEvent.java` | Normalized activity-history model and parser with legacy field fallback. |
| `models/AppSettings.java` | Empty placeholder for future app settings model. |
| `models/QRCode.java` | Empty placeholder for future QR code model. |
| `models/Scans.java` | Empty placeholder for future scan model. |

### Adapters

| File | Responsibility |
| --- | --- |
| `adapters/MenuAdapter.java` | RecyclerView adapter for menu cards; loads item images with Glide and displays price in MAD. |
| `adapters/ActivityAdapter.java` | RecyclerView adapter for activity rows; formats title, timestamp, signed point delta, icon, and color by activity type. |
| `adapters/RewardAdapter.java` | ListAdapter for rewards; DiffUtil comparison, Glide image loading, point-cost display, and redeem button enabled state. |

### Services

| File | Responsibility |
| --- | --- |
| `services/MyFirebaseService.java` | Firebase Messaging service; registers refreshed FCM tokens and displays incoming push notifications. |
| `services/TokenRegistrar.java` | Builds and sends authenticated device-token registration requests to the backend using OkHttp. |

### Layout Resources

| File | Responsibility |
| --- | --- |
| `activity_main.xml` | Splash/launcher screen layout. |
| `activity_sign_up.xml` | Email sign-up and verification request screen. |
| `activity_loyalty.xml` | Main shell layout containing fragment host and bottom navigation. |
| `activity_blocked.xml` | Disabled-app screen with message, support, and quit actions. |
| `fragment_home.xml` | Home tab layout with banner, punch card, menu chips, and menu RecyclerView. |
| `fragment_activity.xml` | Activity tab layout with stats, filters, refresh, empty/loading states, and activity list. |
| `fragment_scan.xml` | Scanner tab layout with camera preview, controls, and result overlays. |
| `custom_scanner_layout.xml` | Custom ZXing scanner framing/overlay layout. |
| `fragment_rewards.xml` | Rewards tab layout with point header, progress indicator, filters, reward list, and empty/loading states. |
| `fragment_profile.xml` | Profile tab layout with user summary, profile-completion form, shortcut rows, and sign-out. |
| `menu_item.xml` | Menu item card used by `MenuAdapter`. |
| `item_activity.xml` | Activity-history row used by `ActivityAdapter`. |
| `item_reward.xml` | Reward row/card used by `RewardAdapter`. |
| `item_menu.xml` | Additional/legacy menu item row layout. |
| `item_onboarding_slide.xml` | Onboarding slide item layout; no active Java onboarding flow currently references it. |

### Navigation, Menu, Values, and XML Resources

| File | Responsibility |
| --- | --- |
| `res/navigation/main_navigation.xml` | Navigation graph declaring the five main fragments. Current `LoyaltyActivity` switches fragments manually instead of using a `NavController`. |
| `res/menu/bottom_nav_menu.xml` | Bottom navigation menu items and icons. |
| `res/values/strings.xml` | User-visible strings and formatting values. |
| `res/values/colors.xml` | Shared color resources, including status-bar colors. |
| `res/values/themes.xml` | Light/base Material3 no-action-bar theme and bottom-nav text styles. |
| `res/values-night/themes.xml` | Dark-mode status-bar theme override. |
| `res/xml/backup_rules.xml` | Full-backup exclusion rules. |
| `res/xml/data_extraction_rules.xml` | Android 12+ cloud/device-transfer exclusion rules. |

### Drawable, Color, and Launcher Assets

| Group | Responsibility |
| --- | --- |
| `res/drawable/ic_*.xml` | Vector icons for navigation, profile, activity, scan, rewards, notifications, controls, and placeholders. |
| `res/drawable/*background*.xml`, `rounded_*.xml`, `button_primary.xml`, `scan_*`, `gradient_*`, `promo_gradient.xml` | Shape, selector, gradient, scan-frame, and button backgrounds used by layouts. |
| `res/drawable/logo.png`, `icon_logo.png`, `onboarding.png`, `placeholder_coffee.xml` | Bitmap/vector brand, onboarding, and placeholder imagery. |
| `res/color/*.xml` | Selectors and colors for bottom navigation, chips, ripple effects, and navigation item states. |
| `res/mipmap-*/*` and `res/mipmap-anydpi-v26/*` | Launcher icon assets for different densities and adaptive icon XML. |

### Unit and Instrumented Tests

| File | Responsibility |
| --- | --- |
| `app/src/test/java/com/example/loyaltyapp/ActivityEventTest.java` | Tests normalized and legacy activity document parsing, null handling, and exception handling. |
| `app/src/test/java/com/example/loyaltyapp/ApiClientTest.java` | Tests Retrofit client creation. |
| `app/src/test/java/com/example/loyaltyapp/ExampleUnitTest.java` | Default sample unit test. |
| `app/src/test/java/com/example/loyaltyapp/ModelsUnitTest.java` | Tests core model getters/constructors and placeholder DTO/model construction. |
| `app/src/test/java/com/example/loyaltyapp/adapters/MenuAdapterTest.java` | Tests menu adapter item count and binding/formatting. |
| `app/src/test/java/com/example/loyaltyapp/viewmodels/MainViewModelTest.java` | Tests app-status/user listener setup for logged-in and logged-out states. |
| `app/src/test/java/com/example/loyaltyapp/viewmodels/RewardsViewModelTest.java` | Tests rewards loading and category filter reload behavior. |
| `app/src/test/java/com/example/loyaltyapp/viewmodels/ScanViewModelTest.java` | Tests scan validation, unauthenticated errors, earn flow dispatch, redeem flow dispatch, and account mismatch handling. |
| `app/src/androidTest/java/com/example/loyaltyapp/ExampleInstrumentedTest.java` | Default instrumentation test verifying app context/package. |

## Known Implementation Notes

- Some comments/UI literals display mojibake in source output, likely from encoding mismatch in earlier edits.
- `ActivityFragment` uses filter value `"scan"` for scan chips while `ActivityEvent` normalizes scan activity to `"earn"`; this may make scan filtering miss current normalized earn events.
- `RewardsRepository.parseReward()` does not populate `priceMAD`, `description`, or `termsUrl` into `Rewards`; the UI price may show `0 MAD` unless the model/repository is extended.
- Empty placeholder model classes (`AppSettings`, `QRCode`, `Scans`) and unused top-level API DTOs can be removed or completed once the intended APIs are finalized.
- The navigation graph exists, but `LoyaltyActivity` currently performs manual fragment transactions and does not use Navigation UI.

## Push Segmentation Signals And Device Lifecycle

- `TokenRegistrar` registers the install's stable device id and current FCM token with the secured
  backend. Logout first calls `/api/v1/push/unregisterDevice` while the Firebase identity is still
  available, then deletes the local FCM token and signs out. This prevents delivery to the previous
  member on a shared device.
- Selecting a menu category or tapping a menu item sends a non-blocking authenticated
  `/api/v1/push/interest` event through `InterestRepository`. Same-category events are locally
  debounced for 15 seconds; the backend validates/rate-limits them and maintains the customer's
  top interest.
- `MyFirebaseService` handles foreground FCM messages and creates the notification channel.
  Background notification messages are displayed by Android/FCM. `LoyaltyActivity` requests
  `POST_NOTIFICATIONS` on Android 13+.
- Interest delivery failures do not block menu browsing. They are diagnostic-only signals and do
  not affect loyalty balances, rewards, or ordering.
