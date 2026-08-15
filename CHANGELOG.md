# Changelog

All notable changes to the MeshLink project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html)
for its shipped `:meshlink` artifact.

Per `CONSTITUTION.md` Governance, the release changelog is generated from
Conventional Commits at release time (e.g. via git-cliff). This file
tracks unreleased, manually-reviewed changes between releases.

## [Unreleased]

### Added

- Initial repository scaffold with AGENTS.md, CI workflow, bootstrap scripts, and
  spec-first wire codec and protocol state machine definitions.
- Git submodule `meshlink-crypto` integrated via Gradle composite build,
  providing SHA-256, HKDF, HMAC, X25519, Ed25519, and ChaCha20-Poly1305
  primitives with pure-Kotlin implementations.
- `.mcp.json` with `codebase-memory-mcp` for subagent code intelligence.
- `SECURITY.md` with vulnerability disclosure policy.
- `.github/dependabot.yml` for GitHub Actions and Gradle dependency updates.
- `docs/` Diataxis-structured documentation (tutorials, how-to, reference,
  explanation, ADRs, vendored RFCs).

### Changed

- _None yet._

### Fixed

- _None yet._

### Security

- _No changes yet._

## Release Process

1. Generate changelog from Conventional Commits: `git-cliff` (or equivalent)
2. Bump version in `gradle/libs.versions.toml` (if version is managed there) or
   via Gradle version plugin
3. Create a release PR with the generated `CHANGELOG.md` section and updated
   `meshlink/api/jvm/meshlink.api` dump (`./gradlew :meshlink:jvmApiDump`)
4. Tag the release: `git tag -a v<MAJOR>.<MINOR>.<PATCH> -m "..."` and push
5. Publish the `:meshlink` artifact to Maven Central (when stable)
