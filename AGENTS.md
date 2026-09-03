# AGENTS.md

Guidance for humans and coding agents working in this repository.

## What this project is

A from-scratch, native Android replacement client for a WinWel "DONA" home-automation
hub (a "DPU"), built because the vendor's own app is being discontinued. It talks
directly to the existing physical hub on the user's LAN (or via DDNS) using a
reverse-engineered protocol — there is no vendor backend involved. See `README.md`
for the protocol write-up and its confidence levels.

## Stack

- **Language:** Kotlin, 100% (no Java sources).
- **UI:** Jetpack Compose + Material3, single-activity, `androidx.navigation.compose`.
- **Concurrency:** Kotlin Coroutines + `Flow` (no RxJava, no callbacks in the domain layer).
- **DI:** [Koin](https://insert-koin.io/) — constructor injection, one `Module` per Gradle
  module, aggregated in `DonaCloneApplication`.
- **Networking:** OkHttp `WebSocket` (the hub's `domotalk` JSON-RPC protocol runs over a
  single persistent WS connection — see README) + `kotlinx.serialization`.
- **Persistence:** Room (connection profiles only — the hub is the source of truth for
  everything else, so almost nothing else is cached; see README §3) + DataStore
  Preferences (the "last used house" pointer).
- **Build:** Gradle Kotlin DSL, a version catalog (`gradle/libs.versions.toml`), no Groovy.
- **Quality gates:** ktlint (style), Android Lint (correctness), Kover (coverage), JUnit4 +
  MockK + Turbine (tests).

Dependency versions live **only** in `gradle/libs.versions.toml`. Never hardcode a
version string in a module's `build.gradle.kts`.

## SDK policy

- `compileSdk` / `targetSdk` should track the latest stable Android release. Bump them
  in the version catalog / module files together, not one without the other.
- `minSdk` should trail `targetSdk` by 3–5 API levels — enough to skip the API-fragmentation
  tax of supporting very old Android versions without cutting off a meaningful share of
  real devices. Don't lower it to "support more devices" without discussing the tradeoff.

## Architecture

Modelled on Google's recommended app architecture (the same shape as the "Now in
Android" sample): a small number of shared **core** modules hold the data/domain
layers, and **feature** modules hold presentation only. This keeps "one module per
feature" true without fragmenting the data/domain layers per feature (which would just
duplicate repository/DTO code across features that all talk to the same hub).

```
app/                     Application class, MainActivity, NavHost, manifest, resources.

core/
  common/                Pure Kotlin. DonaResult/DonaFailure, DispatcherProvider.
  model/                 Pure Kotlin. Domain entities (Device, House, Ambience, ...).
                         No Android, no serialization annotations — these are the
                         types every other layer speaks in.
  network/               DPU wire protocol: UDP discovery, the DomotalkSocket
                         WebSocket transport, request/response DTOs, the
                         structural device-JSON mapper.
  database/              Room (HouseEntity/HouseDao) + DataStore (SessionPreferences).
  domain/                Pure Kotlin. Repository *interfaces* + use cases
                         (one class per action, `operator fun invoke`). Depends on
                         model + common only — never on network/database/data.
  data/                  Repository *implementations*. Wires network + database
                         together, maps DTOs -> domain models, owns the raw-JSON
                         device cache actions need (see README §4).
  designsystem/          DonaTheme (light/dark, optional dynamic color), reusable
                         Compose components (DeviceGridTile, SceneCard, StatusDot, ...).
  testing/               Shared test fixtures (currently just re-exports the test
                         libraries as `api` so feature/core modules get them for free).

feature/
  login/                 Username/password + house picker; the app-level biometric lock.
  houses/                Add/edit/delete connection profiles; LAN discovery UI.
  devices/               Post-login landing screen (Home tab): per-room device grid +
                         control (switches, sliders, pulses), plus the device detail screen.
  ambiences/             Automations (scenes): list + trigger.
  settings/              Session info, "manage houses" entry point, appearance/biometric
                         settings, logout.
```

Dependency direction is strictly `feature -> core:domain, core:designsystem` and
`core:data -> core:domain, core:network, core:database`. A feature module must never
depend on `core:network`, `core:database`, or another `feature:*` module directly —
if two features need to talk to each other, that's what the `app` module's NavHost
callbacks are for.

### Repositories and use cases

- A **repository interface** lives in `core:domain/repository`; its implementation
  lives in `core:data`, named `<Thing>RepositoryImpl`.
- A **use case** is a single-purpose class with one public `operator fun invoke(...)`,
  named for the action it performs (`LoginUseCase`, `TriggerAmbienceUseCase`, ...).
  ViewModels depend on use cases, never directly on repositories — that's what keeps
  business rules (e.g. "a successful login persists the house and marks it active",
  see `LoginUseCase`) out of the presentation layer and unit-testable in isolation.
- Fallible operations return `DonaResult<T>` (`core:common`), not exceptions, once
  they cross from `core:network`'s exception-throwing world into the repository layer.
  `DomotalkException` subclasses are caught and mapped to `DonaFailure` in
  `core/data/.../mapper/FailureMapper.kt` — add new failure cases there, not with a
  `try/catch` sprinkled into every ViewModel.

### Dependency injection

Every module exposes its own `val xModule = module { ... }` under a `di` package.
`DonaCloneApplication.onCreate()` is the **only** place that lists every module and
calls `startKoin`. When you add a module, register it there. Prefer `factoryOf(::X)` /
`viewModelOf(::X)` (constructor-reference based, compile-time checked) over the
lambda form `factory { X(get(), get()) }`, which silently breaks if you reorder
constructor parameters.

## Testing & coverage

- Every use case and ViewModel should have a JUnit test. Use MockK for fakes/mocks
  (`mockk<Interface>()`, `coEvery { ... } returns ...`) and Turbine for asserting on
  `Flow`/`StateFlow` sequences (`flow.test { awaitItem() ... }`).
- ViewModel tests must swap `Dispatchers.Main` for a `StandardTestDispatcher` in
  `@Before`/`@After` (see any `*ViewModelTest` for the pattern) — don't add
  `kotlinx-coroutines-android`'s real Main dispatcher to a unit test.
- Room DAOs are tested with Robolectric + an in-memory database (see
  `core/database/.../HouseDaoTest.kt`), not mocked.
- Run `./gradlew test` locally before pushing. CI runs `testDebugUnitTest` on every
  PR; `./gradlew koverHtmlReport` produces a browsable coverage report under
  `build/reports/kover/`. There's no enforced minimum percentage yet — treat a drop
  in coverage on a PR as a prompt to add tests, not a hard gate.
- Don't write tests against `com.winwel.dona.ui` reverse-engineering internals (there
  is no such dependency in this repo) — tests exercise *this* codebase's behavior only.

## Linting & formatting

- `./gradlew ktlintCheck` — style/formatting. `./gradlew ktlintFormat` autofixes most
  violations before you commit.
- `./gradlew lintDebug` — Android Lint (correctness: resource issues, API-level
  mismatches, leak-prone patterns, etc.). Treat new warnings it introduces on files
  you touched as blocking; pre-existing warnings elsewhere are not your problem to
  fix in an unrelated PR.
- Both run as separate parallel CI jobs (`.github/workflows/android-ci.yml`) alongside
  compile and unit-tests, so a style nit never blocks discovering a real test failure
  or vice versa.

## Protocol changes

Everything this app sends/parses over the wire is documented, with confidence levels,
in `README.md`'s protocol section. If you discover (from a real hub, a packet capture,
or updated firmware docs) that something documented as "UNCONFIRMED" is actually X:
1. Fix the code (most likely in `core:network`).
2. Update the README's confidence table for that item.
3. Add or update a test in `core/network/.../DeviceJsonMapperTest.kt` (or a sibling)
   proving the new behavior, using a literal JSON fixture from the real hub if you have
   one — don't just assert against your own mapper's output.

## What's intentionally not implemented

Alarms, video surveillance/cameras, the video-door intercom, and "external app"
delegation for sound systems are all out of scope for this client (by explicit product
decision, not an oversight) — see `README.md` for what the original app did in those
areas if you ever need to revisit that decision.
