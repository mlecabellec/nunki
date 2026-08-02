#!/bin/bash
# REQ-00010 – Full‑featured OPC‑UA integration
# Helper script to launch Nunki application WITHOUT embedded OPC-UA stub server (default mode)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "========================================================================"
echo "Launching Nunki Spring Application (Default Mode: No Embedded OPC-UA Server)"
echo "========================================================================"

"$SCRIPT_DIR/run-standalone.sh" run "$@"
