#!/bin/bash
# REQ-00010 – Full‑featured OPC‑UA integration
# Helper script to launch Nunki application WITH integrated OPC-UA sample server enabled

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "========================================================================"
echo "Launching Nunki Spring Application WITH Integrated OPC-UA Sample Server"
echo "  - Embedded OPC-UA Server Port: 4840"
echo "========================================================================"

"$SCRIPT_DIR/run-standalone.sh" run --with-embedded-opcua "$@"
