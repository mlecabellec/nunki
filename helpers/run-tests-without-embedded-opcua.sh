#!/bin/bash
# REQ-00010 – Full‑featured OPC‑UA integration
# Helper script to run JUnit test suite with integrated OPC-UA stub server disabled

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "========================================================================"
echo "Running JUnit Tests with Embedded OPC-UA Server Disabled..."
echo "========================================================================"

(cd "$ROOT_DIR" && mvn test -Dtest=OpcUaDisabledServerIntegrationTest,PingWebSocketTest,OpcUaControllerTest,DataTypeMapperTest)
