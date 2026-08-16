# Scaffold alignment implementation plan

**Status:** Ready for TDD on `meshlink-scaffold-alignment`

## Scope

Align the existing `:meshlink` source/API baseline with the accepted pre-release
contracts before implementing protocol behavior. No compatibility aliases are
retained because MeshLink has not shipped.

## Vertical slice 1 — validated settings contract

1. Replace obsolete public settings fields and types with the accepted settings
   model (`enableBackground`, `routeDigestInterval`, `routeExpiry`,
   `maxRoutes`, `maxTransfersPerPeer`, diagnostics flow configuration) → verify:
   `./gradlew :meshlink:jvmTest --tests '*MeshLinkSettingsTest*' --rerun --no-build-cache`
2. Add construction-time validation for appId, durations, route/transfer limits,
   and relationships → verify:
   `./gradlew :meshlink:jvmTest --tests '*MeshLinkSettingsValidationTest*' --rerun --no-build-cache`
3. Remove callback/scoreboard/obsolete routing settings from the public API →
   verify:
   `./gradlew :meshlink:jvmApiDump apiCheck --rerun --no-build-cache`
4. Regenerate the settings catalog and verify source/catalog agreement → verify:
   `./scripts/validate-specs.sh`
5. Verify the complete first slice → verify:
   `./gradlew :meshlink:build :meshlink:koverVerify ktfmtCheck detekt apiCheck --rerun --no-build-cache`

## Vertical slice 2 — canonical public lifecycle skeleton

1. Add the public instance-based `MeshLink` constructor, opaque environment,
   lifecycle state, and state flow test → verify: targeted JVM test
2. Implement serialized idempotent start/pause/resume/stop transitions → verify:
   lifecycle test plus `:meshlink:jvmTest`
3. Add typed configuration/lifecycle errors → verify: error hierarchy tests
4. Update API dump and KDoc → verify: `apiCheck` and Dokka task

## Vertical slice 3 — public peer/diagnostic snapshots

1. Replace peer event public surface with `peers` snapshots using `PeerState`,
   `PeerTrust`, `seenAt`, and `verifiedAt` → verify: targeted tests
2. Replace callback diagnostics with bounded `Flow<DiagnosticEvent>` → verify:
   flow/backpressure tests
3. Add explicit diagnostic code/severity/occurredAt metadata → verify: catalog
   consistency and event tests

## Vertical slice 4 — codec foundation

1. Add canonical Frame/Field/FieldType contracts and explicit code validation →
   verify: codec unit tests
2. Implement bounded reader/writer and enum codecs → verify: golden/malformed
   vector tests
3. Add cross-platform byte-equality fixtures → verify: JVM tests

## Gates

- No implementation starts before the first failing test is written.
- Tests use AAA structure and one logical Act.
- Every Gradle command includes `--rerun --no-build-cache`.
- No commit is created without explicit user approval.
- Before release readiness: explicit Kover LINE=100 and BRANCH=100 checks,
  Detekt must analyze KMP sources, API dump must match, and docs/specs must be
  updated in the same change.
