#!/usr/bin/env bash
# ==============================================================================
# 🌌 NUNKI PROJECT MAIN HELPER & ORCHESTRATOR SCRIPT
# ==============================================================================
# REQ-00010 – Full-featured OPC-UA middleware project helper
# TSK-00307 – Unified CLI & Cyberpunk TUI entry point
#
# Usage:
#   ./nunki-helper.sh               (Launches interactive TUI)
#   ./nunki-helper.sh [command]
# ==============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMMAND="${1:-tui}"

case "$COMMAND" in
    "tui"|"ui")
        exec "$SCRIPT_DIR/helpers/nunki-tui.sh" tui "${@:2}"
        ;;
    "check"|"diag"|"diagnostics")
        exec "$SCRIPT_DIR/helpers/nunki-tui.sh" check "${@:2}"
        ;;
    "prep"|"setup"|"download")
        exec "$SCRIPT_DIR/helpers/nunki-tui.sh" prep "${@:2}"
        ;;
    "standalone"|"external")
        exec "$SCRIPT_DIR/helpers/run-standalone.sh" run "${@:2}"
        ;;
    "embedded")
        exec "$SCRIPT_DIR/helpers/run-app-with-embedded-opcua.sh" "${@:2}"
        ;;
    "docker"|"integrated")
        exec "$SCRIPT_DIR/helpers/run-integrated.sh" up "${@:2}"
        ;;
    "env")
        exec "$SCRIPT_DIR/activate-env.sh" info
        ;;
    "test")
        echo "Running JUnit test suite..."
        if [ -f "./mvnw" ]; then
            ./mvnw test "${@:2}"
        else
            mvn test "${@:2}"
        fi
        ;;
    "build:offline")
        echo "Running offline build..."
        if [ -f "./mvnw" ]; then
            ./mvnw clean install -o "${@:2}"
        else
            mvn clean install -o "${@:2}"
        fi
        ;;
    "build:native")
        echo "Building GraalVM native image..."
        if [ -f "./mvnw" ]; then
            ./mvnw -Pnative native:compile "${@:2}"
        else
            mvn -Pnative native:compile "${@:2}"
        fi
        ;;
    "integration:quasar")
        echo "Running integrated Quasar container environment..."
        exec "$SCRIPT_DIR/helpers/run-integrated.sh" up "${@:2}"
        ;;
    "-h"|"--help"|"help")
        cat << EOF
🌌 Nunki Orchestration & Build Helper Tool

Usage: $0 [command] [options]

Interactive Mode:
  $0 (or $0 tui)        Launch the Cyberpunk TUI dashboard

Environment & Tool Preparation:
  $0 check              Run environment analysis & port diagnostic check
  $0 prep               Download turnkey JDK 17 & Maven tools to .tools/
  $0 env                Display active environment variable settings

Execution Modes:
  $0 standalone         Launch standalone Nunki application (external OPC-UA)
  $0 embedded           Launch standalone Nunki with embedded test OPC-UA server
  $0 docker             Launch multi-container stack (Nunki + Quasar C++ server)

Build & Test Commands:
  $0 test               Run Java JUnit integration tests
  $0 build:offline      Perform an offline Maven build
  $0 build:native       Compile GraalVM native image
  $0 integration:quasar Run integrated Quasar container stack
EOF
        ;;
    *)
        echo "Error: Unknown command '$COMMAND'" >&2
        echo "Run '$0 --help' for available commands." >&2
        exit 1
        ;;
esac