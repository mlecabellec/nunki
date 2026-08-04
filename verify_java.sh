#!/usr/bin/env bash
# Lightweight static analysis for CS constraints (minimal viable version)
set -euo pipefail
FILE="$1"

# Store violations as lines of JSON (no trailing commas)
violations=()

emit() {
  local rule=$1 line=$2 msg=$3
  violations+=("{\"rule\":\"$rule\",\"line\":$line,\"msg\":\"$msg\"}")
}

# 1. Traceability (CS-0010 / CS-0030.15)
if ! grep -qE '(REQ-[0-9]{5}|FR-[0-9]{5}|TSK-[0-9]{5}|BUG-[0-9]{5}|FIX-[0-9]{5})' "$FILE"; then
  emit "CS-0010" 1 "Missing traceability reference"
fi

# 2. No var (CS-0030.10)
if grep -qE '\bvar\b' "$FILE"; then
  grep -nE '\bvar\b' "$FILE" | while IFS=: read -r ln _; do
    emit "CS-0030.10" "$ln" "Use of 'var' forbidden"
  done
fi

# 3. Null return (CS-0030.2)
if grep -q 'return null;' "$FILE"; then
  grep -n 'return null;' "$FILE" | while IFS=: read -r ln _; do
    emit "CS-0030.2" "$ln" "Method returns null"
  done
fi

# 4. Preconditions (CS-0030.1) – simple heuristic: at least one Objects.requireNonNull in public/protected methods
if grep -qE 'public|protected' "$FILE" && ! grep -q 'Objects.requireNonNull' "$FILE"; then
  emit "CS-0030.1" 1 "Missing Objects.requireNonNull in public/protected methods"
fi

# Output JSON result
if [ ${#violations[@]} -eq 0 ]; then
  echo "{\"file\":\"$FILE\",\"status\":\"ok\",\"violations\":[]}"
else
  printf '{\"file\":\"%s\",\"status\":\"violations\",\"violations\":[' "$FILE"
  printf '%s,' "${violations[@]}"
  printf ']}' | sed 's/,]$/]}/'
fi
