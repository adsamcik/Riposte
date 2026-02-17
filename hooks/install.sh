#!/bin/sh
# Installs Git hooks from the tracked hooks/ directory.
# Usage: ./hooks/install.sh

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
HOOKS_DIR="$REPO_ROOT/.git/hooks"

echo "Installing Git hooks..."

for hook in "$SCRIPT_DIR"/*; do
    HOOK_NAME="$(basename "$hook")"
    # Skip this install script itself
    [ "$HOOK_NAME" = "install.sh" ] && continue
    [ "$HOOK_NAME" = "install.ps1" ] && continue

    cp "$hook" "$HOOKS_DIR/$HOOK_NAME"
    chmod +x "$HOOKS_DIR/$HOOK_NAME"
    echo "  ✅ Installed $HOOK_NAME"
done

echo "Done."
