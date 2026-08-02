#!/usr/bin/env bash
# validate-specs.sh — Validate consistency between SPEC.md, ADRs, code, and machine-readable specs

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SPEC_FILE="$ROOT_DIR/SPEC.md"
SPECS_DIR="$ROOT_DIR/specs"
MESHLINK_SRC="$ROOT_DIR/meshlink/src/commonMain/kotlin/ch/trancee/meshlink"

echo "=== MeshLink Spec Validation ==="
echo "Root: $ROOT_DIR"
echo ""

# 1. Check all required YAML files exist
echo "1. Checking required YAML spec files..."
REQUIRED_SPECS=(
    "codecs/enums.yaml"
    "codecs/models.yaml"
    "codecs/frames.yaml"
    "protocol/state-machines.yaml"
    "catalogs/diagnostic-events.yaml"
    "catalogs/settings.yaml"
    "traceability/specification-map.yaml"
)

for spec in "${REQUIRED_SPECS[@]}"; do
    if [[ -f "$SPECS_DIR/$spec" ]]; then
        echo "  ✓ $spec"
    else
        echo "  ✗ MISSING: specs/$spec"
        exit 1
    fi
done

# 2. Validate SPEC-ANCHOR references in code
echo ""
echo "2. Validating SPEC-ANCHOR references in code..."
ANCHORS_IN_CODE=$(grep -r "SPEC-ANCHOR:" "$MESHLINK_SRC" --include="*.kt" | sed 's/.*SPEC-ANCHOR: *//' | sort -u)

for anchor in $ANCHORS_IN_CODE; do
    section=$(echo "$anchor" | sed 's/#.*//')
    # Check for {#anchor} syntax in SPEC.md
    if grep -q "{#$section}" "$SPEC_FILE"; then
        echo "  ✓ $anchor"
    elif grep -q "^## $section" "$SPEC_FILE" || grep -q "^### $section" "$SPEC_FILE"; then
        echo "  ✓ $anchor"
    else
        echo "  ⚠ WARNING: SPEC-ANCHOR '$anchor' not found in SPEC.md sections"
        echo "    (Expected section: '$section')"
    fi
done

# 3. Validate SPEC.md has all required sections
echo ""
echo "3. Checking SPEC.md required sections..."
REQUIRED_SECTIONS=(
    "1. Vision"
    "2. Architecture"
    "3. Core Data Models"
    "4. Discovery"
    "5. Trust Model"
    "6. Transport Layer"
    "7. Security Layer"
    "8. Routing Layer"
    "9. Transfer Layer"
    "10. Power Management"
    "11. Diagnostics"
    "12. Build"
    "13. Testing"
    "14. Settings"
    "15. Future Work"
)

for section in "${REQUIRED_SECTIONS[@]}"; do
    if grep -q "^## $section" "$SPEC_FILE"; then
        echo "  ✓ $section"
    else
        echo "  ✗ MISSING SECTION: $section"
        exit 1
    fi
done

# 4. Validate enums.yaml matches TypeModel.kt (placeholder)
echo ""
echo "4. Checking enum consistency..."
echo "  (Full enum validation requires Kotlin AST parsing - run detekt/Kover)"

# 5. Validate settings.yaml matches MeshLinkSettings.kt
echo ""
echo "5. Checking settings consistency..."
if grep -q "powerMode:" "$SPECS_DIR/catalogs/settings.yaml" && grep -q "PowerMode" "$MESHLINK_SRC/MeshLinkSettings.kt"; then
    echo "  ✓ settings.yaml and MeshLinkSettings.kt both reference PowerMode"
else
    echo "  ⚠ Could not verify settings consistency"
fi

# 6. Check ADR references in SPEC.md
echo ""
echo "6. Checking ADR references..."
ADR_REFS=$(grep -oE 'docs/decisions/[^`",)]+\.md' "$SPEC_FILE" | sort -u)
for adr in $ADR_REFS; do
    if [[ -f "$ROOT_DIR/$adr" ]]; then
        echo "  ✓ $adr"
    else
        echo "  ✗ MISSING ADR: $adr"
        exit 1
    fi
done

# 7. Check reference docs exist
echo ""
echo "7. Checking reference docs..."
REF_DOCS=(
    "docs/reference/architecture.md"
    "docs/reference/discovery.md"
    "docs/reference/trust-model.md"
    "docs/reference/transport.md"
    "docs/reference/security.md"
    "docs/reference/routing.md"
    "docs/reference/transfer.md"
    "docs/reference/power.md"
    "docs/reference/diagnostics.md"
    "docs/reference/testing.md"
    "docs/reference/build-quality.md"
    "docs/reference/settings.md"
    "docs/reference/future-work.md"
    "docs/reference/vision.md"
    "docs/reference/index.md"
)

for doc in "${REF_DOCS[@]}"; do
    if [[ -f "$ROOT_DIR/$doc" ]]; then
        echo "  ✓ $doc"
    else
        echo "  ⚠ MISSING: $doc"
    fi
done

echo ""
echo "=== Validation Complete ==="