## Summary

<!-- What does this PR do and why? Link the issue it closes, if any. -->

## Screenshots / Screen recordings

<!--
Required for any UI-visible change (new screen, layout change, new component, themed
state, etc.). Skip this section entirely for changes with no visual impact (build
config, protocol/network layer, pure refactors).

Prefer a before/after pair or a short screen recording (GIF/MP4) over a single shot.
For anything screen-size or theme sensitive, include both light and dark mode, and/or
phone and tablet, if relevant to the change.
-->

| Before | After |
| --- | --- |
| | |

## Changes

<!-- Bullet list of the concrete changes, module by module if it spans more than one. -->

-

## Test plan

<!-- Check every box that applies; delete rows that genuinely don't apply to this PR
     (e.g. "Manual testing on device" for a pure Gradle config change). Don't check a
     box you didn't actually do. -->

- [ ] Unit tests added/updated for new or changed logic
- [ ] `./gradlew testDebugUnitTest` passes locally
- [ ] `./gradlew ktlintCheck lintDebug` passes locally
- [ ] `./gradlew assembleDebug` builds locally
- [ ] Manually tested on device/emulator (API level: )
- [ ] Tested against a real DPU hub / verified with mocked data (state which)
- [ ] Verified no regressions in related screens/features
- [ ] Edge cases considered (empty state, error/offline state, loading state, rotation/process death)

## Affected modules

<!-- Check all modules touched by this PR. -->

- [ ] `app`
- [ ] `core:common`
- [ ] `core:model`
- [ ] `core:network`
- [ ] `core:database`
- [ ] `core:domain`
- [ ] `core:data`
- [ ] `core:designsystem`
- [ ] `core:testing`
- [ ] `feature:login`
- [ ] `feature:houses`
- [ ] `feature:devices`
- [ ] `feature:ambiences`
- [ ] `feature:settings`

## Breaking changes

<!-- Does this change the local protocol handling, stored data/DB schema, or any
     persisted user data (houses, credentials, cached state)? If yes, describe the
     migration/compat path. If no, delete this section. -->

- [ ] None
- [ ] Requires a database migration (Room schema version bumped)
- [ ] Requires re-authentication / re-registering the house

## Checklist

- [ ] Self-reviewed the diff
- [ ] No secrets, credentials, or hub-specific data committed
- [ ] Updated relevant documentation (README/module docs) if behavior changed
