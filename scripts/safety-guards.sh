#!/bin/bash
# Code safety guards for Riposte
# Catches dangerous patterns that static analysis tools miss.
# Can be run standalone or from pre-commit hooks / CI.
#
# Patterns checked:
# 1. Raw ID keys in LazyList (must use prefixed string keys)
# 2. !! (non-null assertion) in production Kotlin code
# 3. Empty catch blocks in production code

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

ERRORS=0
WARNINGS=0

print_error() {
    echo -e "${RED}❌ $1${NC}"
    ERRORS=$((ERRORS + 1))
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
    WARNINGS=$((WARNINGS + 1))
}

print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

# Determine which files to check
if [ "$1" = "--staged" ]; then
    # Pre-commit mode: only check staged Kotlin files
    FILES=$(git diff --cached --name-only --diff-filter=ACMR | grep '\.kt$' || true)
    if [ -z "$FILES" ]; then
        echo "No staged Kotlin files to check."
        exit 0
    fi
    MODE="staged"
else
    # Full mode: check all production Kotlin source files
    FILES=$(find . -path '*/src/main/kotlin/*.kt' -o -path '*/src/main/java/*.kt' | grep -v '/build/' || true)
    MODE="full"
fi

echo "🔍 Running safety guards ($MODE mode)..."
echo ""

# --- Guard 1: Raw ID keys in LazyList items ---
# Pattern: key = { it.id } or key = { item.id } without string prefix
# Safe: key = { "prefix_${it.id}" }
echo "📋 Guard 1: Checking for raw ID keys in LazyList..."
RAW_KEY_HITS=$(echo "$FILES" | xargs grep -n 'key\s*=\s*{\s*\(it\|[a-z]*\)\.\(id\|rowId\|memeId\|duplicateId\)\s*}' 2>/dev/null || true)
if [ -n "$RAW_KEY_HITS" ]; then
    print_error "Found raw ID keys in LazyList (must use prefixed string keys):"
    echo "$RAW_KEY_HITS"
    echo ""
    echo "  Fix: key = { \"prefix_\${it.id}\" }"
    echo ""
else
    print_success "No raw ID keys found"
fi

# --- Guard 2: !! (non-null assertion) in production code ---
echo "📋 Guard 2: Checking for !! in production code..."
PROD_FILES=$(echo "$FILES" | grep -v '/test/' | grep -v '/androidTest/' || true)
if [ -n "$PROD_FILES" ]; then
    BANG_BANG_HITS=$(echo "$PROD_FILES" | xargs grep -n '!!' 2>/dev/null | grep -v '// !!-allowed' | grep -v 'TODO' || true)
    if [ -n "$BANG_BANG_HITS" ]; then
        print_warning "Found !! (non-null assertion) in production code:"
        echo "$BANG_BANG_HITS"
        echo ""
        echo "  Fix: Use ?:, requireNotNull(), or safe calls instead"
        echo "  Suppress: Add '// !!-allowed' comment if truly justified"
        echo ""
    else
        print_success "No !! assertions found in production code"
    fi
fi

# --- Guard 3: Empty catch blocks ---
echo "📋 Guard 3: Checking for empty catch blocks..."
if [ -n "$PROD_FILES" ]; then
    # Look for catch blocks with ignored exceptions (underscore parameter)
    EMPTY_CATCH=$(echo "$PROD_FILES" | xargs grep -n 'catch\s*(_:' 2>/dev/null | grep -v 'Timber\|Log\|logger\|// catch-allowed' || true)
    if [ -n "$EMPTY_CATCH" ]; then
        print_warning "Found catch blocks with ignored exceptions (should log):"
        echo "$EMPTY_CATCH"
        echo ""
        echo "  Fix: Add Timber.w(e, \"...\") or Timber.e(e, \"...\")"
        echo ""
    else
        print_success "No silent catch blocks found"
    fi
fi

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
if [ $ERRORS -gt 0 ]; then
    echo -e "${RED}Safety guards: $ERRORS error(s), $WARNINGS warning(s)${NC}"
    exit 1
elif [ $WARNINGS -gt 0 ]; then
    echo -e "${YELLOW}Safety guards: 0 errors, $WARNINGS warning(s)${NC}"
    exit 0
else
    echo -e "${GREEN}Safety guards: all checks passed ✅${NC}"
    exit 0
fi
