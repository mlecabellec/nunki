#!/bin/bash
# REQ-00010 – Full‑featured OPC‑UA integration
# Helper script to run JUnit test suite with integrated OPC-UA stub server active

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "========================================================================"
echo "Running JUnit Tests with Embedded OPC-UA Server Enabled..."
echo "========================================================================"

(cd "$ROOT_DIR" && mvn test -Dtest=QuasarOpcUaServerTest,OpcUaEmbeddedServerIntegrationTest,OpcUaEndToEndRestStompTest,EmbeddedOpcUaPlaywrightTest)
