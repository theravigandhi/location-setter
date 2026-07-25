# Location Setter

An Android app (Kotlin, MVVM) that lets you pick any point on Google Maps and publish it to the
device as a mock GPS location using Android's official Mock Location / test-provider framework.
No root required.

## Features

- **Map screen** — Google Maps view, current-location blue dot, Places Autocomplete search,
  long-press to drop a marker, live lat/long readout.
- **Set Mock Location** — starts a foreground service that republishes the chosen coordinate to
  `LocationManager`'s test provider every second, with a live "Mock Location Running / Stopped"
  status card (the Dashboard) right on the Map screen.
- **Device Setup Guide** — checks whether Developer Options are enabled and whether this app is
  selected as the device's mock location app, with inline instructions and deep links to the
  relevant system settings screens.
- **Saved Locations** — Room-backed favorites: add, rename, delete, and reuse with one tap.
- **Bottom navigation** — Map / Saved / Settings, Material 3, light + dark theme.

## Why this build path (no Android Studio required)

This project is built entirely as plain text files (Kotlin, XML, TOML, YAML) — nothing here
requires the Android Studio IDE to author. Compilation happens on GitHub's hosted runners via
the included GitHub Actions workflow (`.github/workflows/build.yml`), so a low-spec local
machine with no Java/Gradle/Android SDK installed can still produce a working APK: push to
GitHub, let Actions build it, download the artifact.

One consequence: this repo does **not** include a checked-in `gradle-wrapper.jar` (it's a
compiled binary that can't be hand-authored, and there was no local Gradle available to generate
one). CI instead uses `gradle/actions/setup-gradle` to provision Gradle 8.10.2 directly and runs
`gradle assembleDebug` (not `./gradlew`). If you later set up a machine with Java/Gradle
installed, you can generate a conventional wrapper yourself with `gradle wrapper` and then use
`./gradlew` locally too — nothing in the project structure needs to change for that.

## Project structure

```
app/src/main/java/com/locationsetter/app/
├── LocationSetterApp.kt          Application; builds the manual DI container
├── MainActivity.kt               Single-activity host: NavHostFragment + BottomNavigationView
├── di/AppContainer.kt            Hand-rolled DI graph (Room DB → Repository)
├── model/MockLocationStatus.kt   Shared status data class
├── data/
│   ├── room/                     LocationEntity, LocationDao, AppDatabase
│   └── repository/               LocationRepository (DAO wrapper for MVVM separation)
├── service/
│   ├── MockLocationService.kt    Foreground service; owns the test-provider lifecycle
│   └── MockLocationStatusHolder.kt  Process-wide StateFlow bridging service → UI
├── ui/
│   ├── map/                      MapFragment/ViewModel — map, search, dashboard card
│   ├── saved/                    SavedLocationsFragment/ViewModel/Adapter, rename dialog
│   ├── settings/                 SettingsFragment
│   └── setup/                    DeviceSetupGuideFragment/ViewModel
└── util/                         Permission helpers, Developer Options + mock-app detection
```

**Why manual DI instead of Hilt/Koin:** Hilt's kapt/KSP codegen is the most common source of
first-build CI failures on a project that's never been built locally before (Kotlin/AGP version
mismatches). With only 4 screens, 1 service, and 1 repository, a hand-rolled `AppContainer` gives
full compile-time safety with the smallest possible plugin surface — important when the *only*
build environment is an unattended CI runner.

**Why the Dashboard lives on the Map screen, not a 4th tab:** the spec calls for exactly 3
bottom-nav tabs (Map, Saved, Settings) but also a live, second-by-second status view. Splitting
the "Set Mock Location" trigger from its live status across tabs would fight the "updates every
second" requirement, so the dashboard is a persistent status card docked to the bottom of the Map
screen instead.

## Manifest & permissions

`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `FOREGROUND_SERVICE`,
`FOREGROUND_SERVICE_LOCATION`, `POST_NOTIFICATIONS`, `INTERNET`. `MockLocationService` declares
`android:foregroundServiceType="location"`, required on Android 14+.

Also declares `ACCESS_MOCK_LOCATION` — this grants nothing at runtime on API 23+, but its mere
presence in the manifest is what makes the app show up at all in Settings → Developer options →
**Select mock location app**. Without it, the OS won't list the app there even though everything
else (build, install, runtime permissions) is working correctly — easy to mistake for a broken
build the first time you hit it.

## Gradle dependencies (see `gradle/libs.versions.toml`)

Google Maps SDK (`play-services-maps`), Places SDK, Room + KSP, Navigation Component + Safe Args,
Lifecycle/ViewModel, Coroutines, Material Components 1.12 (Material 3 XML themes), plus
Robolectric for the included Room DAO unit test. `compileSdk`/`targetSdk` = 35 (Android 15) for
the latest platform behavior; `minSdk` = 23 (Android 6.0 Marshmallow) for broad device coverage —
Android 6.0 is the version that introduced the runtime permission model this app already relies
on, so it's a natural floor without extra legacy branches. Two things are version-gated below the
old Android-12 floor:
- Mock-location registration (`util/TestProviderCompat.kt`) — the modern
  `ProviderProperties.Builder` API only exists from Android 12 onward, so devices on Android 6–11
  fall back to the older `addTestProvider` overload.
- The notification channel (`MockLocationService.createNotificationChannel`) — `NotificationChannel`
  only exists from Android 8.0 onward and is skipped below that (notifications still work, just
  without a channel, exactly as Android expects pre-8.0).

A legacy (non-adaptive) launcher icon is also provided at `res/mipmap-anydpi/` for Android 6–7
devices, since the adaptive-icon format in `res/mipmap-anydpi-v26/` only resolves on Android 8+.

## Google Maps / Places API key setup

1. In the [Google Cloud Console](https://console.cloud.google.com/), create (or reuse) a
   project, then enable **Maps SDK for Android** and **Places API**.
   ⚠️ **Enable the classic "Places API," not (only) "Places API (New)."** Google now lists both
   as separate products in the API Library, and it's easy to enable Maps + "Places API (New)"
   while missing the classic one — the Places SDK version this app uses (Autocomplete search)
   specifically needs the classic API. If search crashes or fails to open at all, this is the
   first thing to check.
2. Create an API key (Credentials → Create Credentials → API key). Restrict it to Android apps
   with package name `com.locationsetter.app` for production use.
3. Add it as a GitHub Actions secret named `MAPS_API_KEY`:
   Repo → Settings → Secrets and variables → Actions → New repository secret.
4. (Local builds only, if you later set up Java/Gradle locally) create `local.properties` in the
   project root with a line `MAPS_API_KEY=your_key_here` — this file is gitignored and never
   committed.

The key is injected at build time into both the manifest's
`com.google.android.geo.API_KEY` meta-data tag and a `BuildConfig.MAPS_API_KEY` field (used to
initialize the Places SDK) — see `app/build.gradle.kts`.

## Subscription system (Lemon Squeezy)

The app monetizes via a monthly subscription gating the "Set Mock Location" action (map
browsing, search, and saved locations stay free; a 3-use free trial applies before the paywall
kicks in). Two things drove the design:

- **Distribution today is a directly-shared APK, not the Play Store.** Google Play Billing only
  works for apps installed *through* the Play Store, so it's not usable yet. Lemon Squeezy was
  chosen instead because it works independent of how the APK reaches the device.
- **No backend server.** Lemon Squeezy's license-key API (`/v1/licenses/activate` and
  `/v1/licenses/validate`) is public and safe to call directly from the app — there's no secret
  key to embed or steal out of the APK, unlike a typical payment-API integration.

**How it works today:** a customer subscribes via a Lemon Squeezy-hosted checkout page (opened in
a Custom Tab from the paywall screen — no card details ever touch this app), receives a license
key by email, and enters it in-app to activate. `SubscriptionRepository`
(`data/subscription/SubscriptionRepository.kt`) persists the license/trial state locally via
SharedPreferences and is the app's own source of truth for entitlement — `LemonSqueezyApi`
(`data/subscription/LemonSqueezyApi.kt`) re-verifies it against Lemon Squeezy on demand (the
paywall's "Refresh subscription status" button) rather than trusting the local flag forever.

**Setup required on your end** (same shape as the Maps key above — I can't create third-party
accounts on your behalf):
1. Create a [Lemon Squeezy](https://www.lemonsqueezy.com/) account and store.
2. Create a monthly subscription product/variant; Lemon Squeezy issues a license key per purchase
   automatically (Product settings → enable "License keys").
3. Copy that product's checkout URL (looks like
   `https://YOUR-STORE.lemonsqueezy.com/checkout/buy/VARIANT-UUID`).
4. Add it as a GitHub Actions secret named `LEMONSQUEEZY_CHECKOUT_URL` (or in `local.properties`
   for local builds, same pattern as `MAPS_API_KEY`).
5. Update `res/values/strings.xml`'s `paywall_price` string to match your actual price — it's
   currently a placeholder (`$4.99 / month`).

**Known limitation, honestly stated:** with no backend, entitlement is ultimately checked
client-side — a sufficiently motivated user could repackage the APK to bypass the check. This is
an inherent tradeoff of the "no backend, direct distribution" constraint, not a bug; it can be
hardened later with a small backend that owns the source of truth if piracy becomes a real
problem. It's also worth knowing before investing further here: Google Play's developer policy
has periodically removed mock-location apps in policy sweeps even when built as legitimate
developer/QA tools, so **Play Store publication of a monetized fake-GPS app is not guaranteed to
be approved or stay listed** — worth weighing before betting the business on that channel. When/if
you do move to Play Store distribution, this Lemon Squeezy integration would need to be swapped
for Google Play Billing, since Play policy requires it for in-app digital subscriptions.

## Build & run guide

1. **Push this project to a GitHub repository** (create one if you don't have it yet) and add
   the `MAPS_API_KEY` secret as described above.
2. **Trigger the build** — push to any branch, or go to the Actions tab → "Build Debug APK" →
   "Run workflow" (workflow_dispatch).
3. **Download the APK** — once the run finishes, open it and download the
   `location-setter-debug-apk` artifact; unzip it to get `app-debug.apk`.
4. **Sideload it onto your phone** — transfer the APK (email, cloud drive, USB, etc.) and install
   it, allowing "install unknown apps" for whichever app you use to open it.
5. **Enable Developer Options** — Settings → About phone → tap "Build number" 7 times.
6. **Select Location Setter as the mock location app** — Settings → System → Developer options →
   "Select mock location app" → Location Setter.
7. **Launch the app** and grant the location + notification permission prompts.
8. **Verify setup in-app** — Settings tab → Device Setup Guide → confirm all checks are green.
9. **Pick a location** — search or long-press the map, confirm the shown coordinates, tap
   "Set Mock Location". The status card should read "Mock Location Running" with a ticking
   last-update time.
10. **Verify with Google Maps** — minimize Location Setter (the foreground notification stays
    visible) and open the stock Google Maps app; its blue location dot should jump to and hold
    the mocked coordinate.
11. **Stop mocking** — tap the button again; the status flips to "Mock Location Stopped," the
    notification clears, and Google Maps' blue dot returns to the real GPS fix.

Physical-device testing is required for meaningful verification — Android emulators' GPS/mock
provider behavior is unreliable for this exact flow. No root is required anywhere in this app;
it only uses Android's official Mock Location framework.

## Running the unit tests (in CI or on a machine with Gradle)

```
gradle testDebugUnitTest
```

Covers `LocationDao` CRUD behavior (`app/src/test/.../data/LocationDaoTest.kt`) via an in-memory
Room database + Robolectric.
