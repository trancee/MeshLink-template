# MeshLink Template

A Kotlin Multiplatform template for building MeshLink, an encrypted, serverless, fully offline peer-to-peer messaging SDK between mobile devices over short-range radio mesh.

## Vision

MeshLink enables two mobile platforms (Android and iOS) to communicate securely without internet, backend servers, or user accounts. Peers establish trust via Trust On First Use (TOFU), use two-layer encryption (hop-by-hop + end-to-end), and maintain proactive multi-hop routing using Babel-style distance-vector protocols.

## Project Structure

| Module | Purpose |
|-|---|
| `meshlink` | Shipped library (JVM + Android + iOS device targets) — depends on `ch.trancee.meshlink:meshlink-crypto` (v0.1.1, Maven Central) |
| `meshlink-reference` | Reference app consuming public API only - validates developer experience |
| `meshlink-proof` | Real-device validation for Android/iOS BLE behavior |
| `meshlink-benchmark` | Performance benchmarking for throughput, latency, memory budgets |

See [Module Structure](docs/explanation/module-structure.md) for details.

## Key Features

- **Zero-infrastructure trust** — TOFU model with explicit revocation
- **Two-layer encryption** — Hop-by-hop link encryption + end-to-end Noise handshakes
- **Proactive multi-hop routing** — Distance-vector protocol adapted from RFC 8966 (Babel)
- **Reliable large-payload transfer** — Selective acknowledgement over small-frame BLE
- **Power-aware operation** — Discrete modes governing scan/conn/transfer parameters
- **Cross-platform parity** — Identical public API and sealed exception hierarchies

## Development Workflow

### Getting Started

```sh
# Clone the repository
git clone https://github.com/trancee/MeshLink-template.git

# Bootstrap tooling (gitleaks, yamllint, markdownlint, git hooks)
./scripts/bootstrap.sh

# Verify tooling installed correctly
./scripts/check-markdown.sh --offline
```

See [Bootstrap Project Tooling](docs/how-to/bootstrap-project-tooling.md) for prerequisites.

### Build Commands

```sh
./gradlew :meshlink:build          # Build library
./gradlew :meshlink:detekt         # Static analysis
./gradlew :meshlink:spotlessCheck    # Code formatting
./gradlew :meshlink:koverVerify    # Coverage verification
```

### Git Hooks

The `.githooks/` directory contains:

- `pre-commit` — Runs gitleaks protect, spotless format, detekt on staged files
- `pre-push` — Runs full gitleaks detect, spotless check, detekt on touched modules
- `commit-msg` — Validates Conventional Commits format

Hooks are auto-installed by `bootstrap.sh` or `.scripts/install-git-hooks.sh`.

## Quality Gates

All changes must pass before merge:

- **Detekt** — Zero suppressions (Principle I)
- **Spotless** — Auto-format before every commit
- **Kover** — 100% line/branch coverage for `:meshlink` artifact
- **BCV** — Binary Compatibility Validator tracks public API
- **gitleaks** — No secrets in diff or history

See [CONSTITUTION.md](CONSTITUTION.md) for binding engineering rules.

## Documentation Structure

Documentation follows the [Diátaxis](https://diataxis.fr) framework:

| Directory | Type | Purpose |
|-----------|------|---------|
| `docs/tutorials/` | Tutorial | Learning MeshLink hands-on |
| `docs/how-to/` | How-to guide | Accomplishing specific tasks |
| `docs/reference/` | Reference | API shape, error codes, config |
| `docs/explanation/` | Explanation | Design decisions, rationale |
| `docs/decisions/` | Decision records | Dated ADRs (research/design notes) |
| `docs/rfcs/` | Reference | Vendored IETF specifications |

Add new docs only when you have content to place — see the `diataxis` skill.

## Technical Standards

MeshLink implements against these RFC standards:

- **RFC 7748** — X25519/X448 ECDH for key agreement
- **RFC 8032** — Ed25519 signatures
- **RFC 8439** — ChaCha20-Poly1305 AEAD encryption
- **RFC 5869** — HKDF key derivation
- **RFC 2104** — HMAC for diagnostics
- **RFC 6234** — SHA-256 for hashing
- **RFC 8966** — Babel routing for seqno/feasibility
- **RFC 2018** — TCP SACK for selective acknowledgement

Crypto primitives (SHA-256, HKDF, HMAC, X25519, Ed25519, ChaCha20-Poly1305) are provided by the [`MeshLink-crypto`](docs/decisions/crypto/meshlink-crypto-dependency.md) module, a KMP library published to Maven Central (currently v0.1.1). Implementations are validated against Wycheproof test vectors.

## Platform Requirements

- **Android** — API 26+ (runtime crypto checks for API 26-32)
- **iOS** — 14.0+
- **JDK** — Temurin 21 (per CI)

## License

Apache-2.0 — see [LICENSE](LICENSE).

## Contributing

1. Work on feature branches — never commit directly to `main`
2. Run `./scripts/bootstrap.sh` to install hooks
3. Write tests first (TDD) — see AGENTS.md workflow
4. Include Constitution Check in PR description
5. Include version-bump rationale for any `.api` diff

Commit messages follow [Conventional Commits](https://www.conventionalcommits.org):

```text
feat: add Noise IK handshake for reconnect
fix: handle seqno wrap-around in route comparison
test: validate chunk reassembly with scoreboard
docs: document transfer state transitions
```

Co-authored commits must include:

```text
Co-authored-by: Your Agent <agent@example.com>
```
