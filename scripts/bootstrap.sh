#!/usr/bin/env bash
# Bootstraps a fresh clone: verifies/installs the CLI tools MeshLink's
# .githooks/ and CI rely on, then installs the repository Git hooks.
#
# See docs/how-to/bootstrap-project-tooling.md for the full explanation.
#
# Usage: ./scripts/bootstrap.sh [--check-only]
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

check_only=0
if [[ "${1:-}" == "--check-only" ]]; then
  check_only=1
fi

os="$(uname -s)"
arch="$(uname -m)"

case "$os" in
  Linux) platform="linux" ;;
  Darwin) platform="darwin" ;;
  *)
    echo "[bootstrap] Unsupported OS: $os. Install tools manually — see" >&2
    echo "[bootstrap] docs/how-to/bootstrap-project-tooling.md." >&2
    exit 1
    ;;
esac

install_dir="/usr/local/bin"
if [[ ! -w "$install_dir" ]]; then
  install_dir="$HOME/.local/bin"
  mkdir -p "$install_dir"
fi

missing_hard=()

require_hard() {
  local tool="$1" hint="$2"
  if ! command -v "$tool" >/dev/null 2>&1; then
    echo "[bootstrap] MISSING (required, not auto-installed): $tool" >&2
    echo "[bootstrap]   $hint" >&2
    missing_hard+=("$tool")
  else
    echo "[bootstrap] OK: $tool ($(command -v "$tool"))"
  fi
}

# git, gh, and node (for markdownlint-cli2 via npx and codebase-memory-mcp
# via npm) are the bootstrap's own dependencies, so they can't be auto-installed
# by this script.
require_hard git "Install via your OS package manager (e.g. apt install git, brew install git)."
require_hard gh "Install via https://github.com/cli/cli#installation, then run: gh auth login"
require_hard node "Install via https://nodejs.org, your OS package manager, or a version manager (nvm/fnm)."

if [[ ${#missing_hard[@]} -gt 0 ]]; then
  echo "[bootstrap] Install the tools above, then re-run this script." >&2
  exit 1
fi

# gh release download needs an authenticated (or at least working) gh CLI
# for API rate limits; verify it can talk to GitHub before relying on it.
if ! gh auth status >/dev/null 2>&1; then
  echo "[bootstrap] 'gh' is installed but not authenticated." >&2
  echo "[bootstrap] Run: gh auth login" >&2
  exit 1
fi

download_release_binary() {
  local repo="$1" pattern="$2" binary_name="$3" target_name="$4"
  local tmp_dir
  tmp_dir="$(mktemp -d)"

  echo "[bootstrap] Installing $target_name from $repo release ($pattern)"
  gh release download --repo "$repo" --pattern "$pattern" --dir "$tmp_dir" --clobber

  local archive
  archive="$(find "$tmp_dir" -maxdepth 1 -type f -print -quit)"
  tar -xf "$archive" -C "$tmp_dir"

  local extracted
  extracted="$(find "$tmp_dir" -maxdepth 2 -type f -name "$binary_name" -print -quit)"
  if [[ -z "$extracted" ]]; then
    echo "[bootstrap] Could not find $binary_name in $repo release archive." >&2
    rm -rf "$tmp_dir"
    return 1
  fi

  install -m 0755 "$extracted" "$install_dir/$target_name"
  echo "[bootstrap] Installed $install_dir/$target_name"
  rm -rf "$tmp_dir"
}

install_if_missing() {
  local tool="$1"
  shift
  if command -v "$tool" >/dev/null 2>&1; then
    echo "[bootstrap] OK: $tool ($(command -v "$tool"))"
    return 0
  fi
  if [[ "$check_only" -eq 1 ]]; then
    echo "[bootstrap] MISSING: $tool (--check-only, not installing)"
    return 0
  fi
  "$@"
}

# On macOS, prefer Homebrew when available — it's the native, expected
# install path there. Only fall back to `gh release download` on macOS
# without Homebrew, or on Linux (no equivalent single native manager).
if [[ "$platform" == "darwin" ]] && command -v brew >/dev/null 2>&1; then
  install_if_missing gitleaks brew install gitleaks
  install_if_missing actionlint brew install actionlint
  install_if_missing shellcheck brew install shellcheck
  install_if_missing lychee brew install lychee
else
  case "${arch}_${platform}" in
    x86_64_linux)   gitleaks_pattern='*_linux_x64.tar.gz';      actionlint_pattern='*_linux_amd64.tar.gz';   shellcheck_pattern='*.linux.x86_64.tar.xz';   lychee_pattern='*x86_64-unknown-linux-gnu.tar.gz' ;;
    aarch64_linux|arm64_linux)
                    gitleaks_pattern='*_linux_arm64.tar.gz';    actionlint_pattern='*_linux_arm64.tar.gz';   shellcheck_pattern='*.linux.aarch64.tar.xz';  lychee_pattern='*aarch64-unknown-linux-gnu.tar.gz' ;;
    x86_64_darwin)  gitleaks_pattern='*_darwin_x64.tar.gz';     actionlint_pattern='*_darwin_amd64.tar.gz';  shellcheck_pattern='*.darwin.x86_64.tar.xz';  lychee_pattern='*x86_64-apple-darwin.tar.gz' ;;
    arm64_darwin)   gitleaks_pattern='*_darwin_arm64.tar.gz';   actionlint_pattern='*_darwin_arm64.tar.gz';  shellcheck_pattern='*.darwin.aarch64.tar.xz'; lychee_pattern='*aarch64-apple-darwin.tar.gz' ;;
    *)
      echo "[bootstrap] Unsupported arch/platform: $arch/$platform." >&2
      echo "[bootstrap] Install gitleaks/actionlint/shellcheck/lychee manually — see" >&2
      echo "[bootstrap] docs/how-to/bootstrap-project-tooling.md." >&2
      exit 1
      ;;
  esac

  install_if_missing gitleaks \
    download_release_binary gitleaks/gitleaks "$gitleaks_pattern" gitleaks gitleaks
  install_if_missing actionlint \
    download_release_binary rhysd/actionlint "$actionlint_pattern" actionlint actionlint
  install_if_missing shellcheck \
    download_release_binary koalaman/shellcheck "$shellcheck_pattern" shellcheck shellcheck
  install_if_missing lychee \
    download_release_binary lycheeverse/lychee "$lychee_pattern" lychee lychee
fi

# Prefer the OS package manager for yamllint (it's pure Python, but a
# packaged install avoids polluting/depending on the user's pip user-site).
if [[ "$check_only" -eq 1 ]]; then
  command -v yamllint >/dev/null 2>&1 \
    && echo "[bootstrap] OK: yamllint ($(command -v yamllint))" \
    || echo "[bootstrap] MISSING: yamllint (--check-only, not installing)"
elif command -v brew >/dev/null 2>&1; then
  install_if_missing yamllint brew install yamllint
elif command -v dnf >/dev/null 2>&1; then
  install_if_missing yamllint sudo dnf install -y yamllint
else
  echo "[bootstrap] No brew/dnf found; yamllint needs manual installation" >&2
  echo "[bootstrap] (e.g. apt install yamllint, or python3 -m pip install --user yamllint)." >&2
  missing_hard+=("yamllint")
fi

# Install codebase-memory-mcp — the code-intelligence MCP server configured
# in .mcp.json. Subagents rely on it for graph search, architecture analysis,
# and other code-intelligence tools. It's an npm package; npm ships with node.
if [[ "$check_only" -eq 1 ]]; then
  command -v codebase-memory-mcp >/dev/null 2>&1 \
    && echo "[bootstrap] OK: codebase-memory-mcp ($(command -v codebase-memory-mcp))" \
    || echo "[bootstrap] MISSING: codebase-memory-mcp (--check-only, not installing)"
elif command -v npm >/dev/null 2>&1; then
  install_if_missing codebase-memory-mcp npm install -g codebase-memory-mcp
else
  echo "[bootstrap] No npm found; codebase-memory-mcp needs manual installation" >&2
  missing_hard+=("codebase-memory-mcp")
fi

if [[ ${#missing_hard[@]} -gt 0 ]]; then
  echo "[bootstrap] Some tools could not be installed automatically." >&2
  echo "[bootstrap] See docs/how-to/bootstrap-project-tooling.md for manual steps." >&2
  exit 1
fi

if [[ "$check_only" -eq 1 ]]; then
  echo "[bootstrap] --check-only: skipping git hook installation."
  exit 0
fi

echo "[bootstrap] Installing repository Git hooks"
./scripts/install-git-hooks.sh

echo "[bootstrap] Verifying setup"
hooks_path="$(git config --get core.hooksPath || true)"
if [[ "$hooks_path" != ".githooks" ]]; then
  echo "[bootstrap] core.hooksPath is '$hooks_path', expected '.githooks'." >&2
  exit 1
fi
for hook in .githooks/pre-commit .githooks/pre-push .githooks/commit-msg; do
  if [[ ! -x "$hook" ]]; then
    echo "[bootstrap] $hook is not executable." >&2
    exit 1
  fi
done

for tool in git gh node gitleaks yamllint actionlint shellcheck lychee codebase-memory-mcp; do
  command -v "$tool" >/dev/null 2>&1 || {
    echo "[bootstrap] $tool is still missing after bootstrap." >&2
    exit 1
  }
done

if [[ "$install_dir" != "/usr/local/bin" ]]; then
  echo "[bootstrap] NOTE: tools were installed to $install_dir — ensure it's on your PATH."
fi

echo "[bootstrap] Done. All prerequisite tools are available and Git hooks are installed."
