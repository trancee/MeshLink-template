# How to bootstrap MeshLink's local tooling

Goal: take a fresh clone from zero to "prerequisite CLI tools available and
Git hooks installed" in one step.

## Run it

```sh
./scripts/bootstrap.sh
```

This verifies `git`, `gh`, and `node` (for `markdownlint-cli2` via `npx`)
are present — `gh` also authenticated (`gh auth login` if not) — installs
`gitleaks`, `actionlint`, `shellcheck`, and `lychee` if missing (via
Homebrew on macOS when available, otherwise via `gh release download` from
each tool's GitHub releases), installs `yamllint` if missing via Homebrew
or `dnf` (whichever is available; falls back to a manual-install message
otherwise), then runs `scripts/install-git-hooks.sh` and verifies
`core.hooksPath` and hook executable bits.

Use `./scripts/bootstrap.sh --check-only` to report what's missing without
installing anything or touching Git config.

## Why these tools

| Tool | Used by | Why |
| --- | --- | --- |
| `git` | everything | version control |
| `gh` | this script, [AGENTS.md](../../AGENTS.md) workflow | GitHub operations (issues, PRs, releases) and to fetch the other tools' release binaries |
| `node` / `npm` / `npx` | `scripts/check-markdown.sh` (`npx markdownlint-cli2`) | Markdown style/syntax lint — no separate install needed beyond Node itself (`npm`/`npx` ship with it); `npx` fetches `markdownlint-cli2` on first run |
| `python3` / `pip` | `scripts/update_device_test_matrix.py` | regenerates `docs/reference/device-test-matrix.md` from `adb` + catalog data |
| `pipx` | manual/optional | isolated installs of Python-based CLI tools (e.g. `yamllint`) without touching the system/user site-packages, if you're not using `brew`/`dnf` for it |
| `gitleaks` | `.githooks/pre-commit`, `.githooks/pre-push`, CI | secret scanning (Quality Gates) |
| `yamllint` | `.githooks/pre-commit`, `.githooks/pre-push`, CI | YAML/workflow lint |
| `actionlint` | manual/CI use when editing `.github/workflows/` | GitHub Actions workflow lint |
| `shellcheck` | manual use when editing `.githooks/*` or `scripts/*.sh` | shell script lint (hooks only run `bash -n`, a syntax check — `shellcheck` catches real bugs `bash -n` misses) |
| `lychee` | `scripts/check-markdown.sh`, `.githooks/pre-commit`/`pre-push`, CI | verifies Markdown links (relative file paths, anchors, and — outside `--offline` mode — external URLs) actually resolve |

`scripts/bootstrap.sh` only hard-checks `git`, `gh`, and `node` itself
(`npm`/`npx` are bundled with Node; `python3`/`pip`/`pipx` aren't invoked by
the script, only by `scripts/update_device_test_matrix.py` and as an
optional manual `yamllint` install path) — install those separately if
missing.

See [Quality Gates](../../CONSTITUTION.md#quality-gates) for what's actually
enforced, and `.githooks/pre-commit` / `.githooks/pre-push` for exactly
where each tool is invoked.

## If your package manager already has these

Prefer your OS package manager if it already ships current versions —
it's simpler than a GitHub release download and is how these tools are
commonly installed:

- macOS: `brew install gh gitleaks yamllint actionlint shellcheck lychee`
  (the bootstrap script itself uses Homebrew here when available)
- Fedora/RHEL: `dnf install gh yamllint shellcheck` (`gitleaks`/`actionlint`/
  `lychee` usually aren't packaged; the script covers those three). `dnf
  install` needs `sudo` — the script will prompt for it when installing
  `yamllint` this way.
- Debian/Ubuntu: `apt install gh gitleaks shellcheck yamllint`
  (`actionlint` usually isn't packaged; the script covers it. The script
  doesn't call `apt` itself — install `yamllint` manually first, or use
  `pip install --user yamllint`, if you're on Debian/Ubuntu without brew.)

`scripts/bootstrap.sh` exists for the cases the above don't cover (no
matching package, stale package version, or a from-scratch CI-like
environment) and to give an AI agent a single deterministic command
instead of guessing per-OS package names.

## Checking Markdown docs

```sh
./scripts/check-markdown.sh           # style/syntax (markdownlint-cli2) + all links, including external URLs
./scripts/check-markdown.sh --offline # same, but skip external URL checks (faster, no network)
```

Rules/config live in `.markdownlint-cli2.jsonc` (with inline comments
explaining each disabled rule) and `lychee.toml`. `.githooks/pre-commit`
runs the `--offline` form on staged `.md` files; `.githooks/pre-push` and
CI run the full form (including external URLs) — the same fast/full split
`yamllint` already uses.

## Verifying it worked

```sh
git config --get core.hooksPath   # must print: .githooks
ls -l .githooks/pre-commit .githooks/pre-push .githooks/commit-msg  # must be executable
git commit --allow-empty -m "chore: verify hooks"  # should run gitleaks + hook checks
```

## What this does *not* cover

- The Gradle/Kotlin Multiplatform project itself doesn't exist yet — see
  `PROJECT.md`'s suggested rebuild approach for the spec-first build order.
  Once it lands, a JDK (Temurin 21, per `.github/workflows/ci.yml`) and the
  Gradle wrapper become additional prerequisites; this script doesn't
  install those.
- Xcode/iOS toolchain setup for the `ios` CI job — that job runs on
  `macos-latest` with Xcode preinstalled; there's no local bootstrap step
  for it yet.
