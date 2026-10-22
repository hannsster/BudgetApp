#!/usr/bin/env bash
#
# Initialise this repository and push it to an existing GitHub remote.
#
# Usage:
#   ./scripts/push_to_github.sh <repository-url> [branch]
#
# Examples:
#   ./scripts/push_to_github.sh git@github.com:your-user/mipresupuesto-cr.git
#   ./scripts/push_to_github.sh https://github.com/your-user/mipresupuesto-cr.git main
#
# If you have GitHub CLI authenticated and the remote does not exist yet, you can
# create it instead of passing a URL:
#   gh repo create mipresupuesto-cr --private --source=. --remote=origin --push
#
# The script never handles tokens: it relies on your existing git credential
# helper or SSH agent.

set -euo pipefail

REPO_URL="${1:-}"
BRANCH="${2:-main}"

if [[ -z "$REPO_URL" ]]; then
  echo "Usage: $0 <repository-url> [branch]" >&2
  exit 1
fi

command -v git >/dev/null 2>&1 || {
  echo "error: git is required" >&2
  exit 1
}

# Secrets must never be staged; local.properties is in .gitignore.
if [[ -f local.properties ]] && ! git check-ignore -q local.properties; then
  echo "error: local.properties exists but is not ignored. Fix .gitignore before pushing." >&2
  exit 1
fi

if [[ ! -d .git ]]; then
  echo "==> Initialising repository on branch $BRANCH"
  git init -b "$BRANCH"
fi

if ! git config user.name >/dev/null 2>&1 || ! git config user.email >/dev/null 2>&1; then
  echo "error: configure git first:" >&2
  echo "  git config user.name \"Your Name\"" >&2
  echo "  git config user.email \"you@example.com\"" >&2
  exit 1
fi

echo "==> Staging files"
git add -A

if git diff --cached --quiet; then
  echo "==> Nothing new to commit"
else
  git commit -m "feat: initial Costa Rica budgeting and ETF portfolio app"
fi

if git remote get-url origin >/dev/null 2>&1; then
  echo "==> Updating origin to $REPO_URL"
  git remote set-url origin "$REPO_URL"
else
  echo "==> Adding origin $REPO_URL"
  git remote add origin "$REPO_URL"
fi

echo "==> Pushing $BRANCH"
git push -u origin "$BRANCH"

echo "Done. Open $REPO_URL to review the repository."
