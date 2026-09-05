---
name: verify
description: Run this project's full local CI gate (ktlint, Android Lint, unit tests, debug assemble) before considering any code change in this repo finished, pushing a branch, or opening/updating a PR. Use whenever you've edited Kotlin/Compose source, build files, or tests in DONA-Clone.
---

# Verify (DONA-Clone local CI gate)

This repo's CI (`.github/workflows/android-ci.yml`) runs compile, unit tests,
coverage, and lint as separate parallel jobs. Its git history shows the same
recurring failure mode: a PR's first push looks done, CI comes back red on one
or more jobs, and a second "fix compile error" / "fix ktlint violation" commit
follows — sometimes for a mistake (e.g. an `ExposedDropdownMenu` top-level
import, or a lost smart-cast on `house.dns`) that had already happened once
before in this exact codebase. Running the same checks locally first catches
all of that before it costs a CI round-trip.

## What to run

Before telling the user a change is done, before `git push`, and before
opening or updating a PR, run:

```bash
./gradlew ktlintCheck lintDebug testDebugUnitTest assembleDebug --stacktrace
```

This mirrors the four CI jobs (`lint`, part of `lint`, `unit-tests`, `compile`)
in one invocation. If any of it fails:

1. **ktlint failures** — run `./gradlew ktlintFormat` first; it autofixes
   nearly all style violations (import order, blank lines, brace placement).
   Re-run `ktlintCheck` after, since it won't fix a filename that has to match
   the single class/object it declares — rename the file by hand for that one.
2. **lintDebug failures** — read the actual finding in the console output (or
   `build/reports/lint-results-debug.html`) before changing anything; only
   fix findings in files you touched, per `AGENTS.md`'s Linting section.
3. **testDebugUnitTest failures** — check first whether it's the well-known
   ViewModel init-race pattern (`AGENTS.md` → Testing & coverage): a test
   reading `uiState` right after construction, before the `init { launch {} }`
   coroutine ran on the `StandardTestDispatcher`. If so, add
   `dispatcher.scheduler.advanceUntilIdle()` before the assertion instead of
   changing production code.
4. **assembleDebug (compile) failures** — check `AGENTS.md`'s "Common
   pitfalls" section first; a lost smart-cast on a property with a custom
   getter, or a wrongly-imported `ExposedDropdownMenu`, are the two mistakes
   that have already happened twice each in this codebase.

Only report the task as complete, or push, once all four succeed. If a
failure is genuinely pre-existing on `main` (unrelated to your change),
say so explicitly rather than silently fixing or silently ignoring it.
