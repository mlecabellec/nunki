#!/bin/bash
# REQ-00010 – Full‑featured OPC‑UA client service
# TSK-00305 – Standalone Nunki runner script

set -euo pipefail

# Determine script and root directories
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
JAR_PATH="$ROOT_DIR/target/nunki-0.0.1-SNAPSHOT.jar"

# Default configuration
SKIP_BUILD=false
PORT=8080

show_help() {
    cat << EOF
Usage: $(basename "$0") [options] [app-arguments]

Helper script to build and execute the Nunki server in standalone mode.

Options:
  -s, --skip-build   Skip building the application and run the existing JAR
  -p, --port PORT    Override the Spring Boot port (default: 8080)
  -h, --help         Show this help message

App Arguments:
  cli                Run the application in headless CLI mode
  [other]            Any other arguments are passed directly to the Spring Boot application

Examples:
  $(basename "$0")                     # Build and run Nunki server on port 8080
  $(basename "$0") -s                  # Run existing build without rebuilding
  $(basename "$0") -p 8081             # Run on port 8081
  $(basename "$0") cli                 # Run in headless CLI mode
EOF
}

# Parse options
APP_ARGS=()
while [[ $# -gt 0 ]]; do
    case "$1" in
        -s|--skip-build)
            SKIP_BUILD=true
            shift
            ;;
        -p|--port)
            if [[ -z "${2:-}" || "${2:-}" =~ ^- ]]; then
                echo "Error: --port requires a non-empty port number value." >&2
                exit 1
            fi
            PORT="$2"
            shift 2
            ;;
        -h|--help)
            show_help
            exit 0
            ;;
        *)
            APP_ARGS+=("$1")
            shift
            ;;
    esac
done

# Verify Java is installed
if ! command -v java >/dev/null 2>&1; then
    echo "Error: 'java' is not installed or not in PATH." >&2
    exit 1
fi

# Verify Maven is installed if we need to build
if [ "$SKIP_BUILD" = false ]; then
    if ! command -v mvn >/dev/null 2>&1; then
        echo "Error: 'mvn' is not installed or not in PATH. Cannot build. Use -s/--skip-build if the JAR is already compiled." >&2
        exit 1
    fi
fi

# Build Nunki if not skipped
if [ "$SKIP_BUILD" = false ]; then
    echo "========================================================================"
    echo "Building Nunki application..."
    echo "========================================================================"
    (cd "$ROOT_DIR" && mvn clean package -DskipTests)
    echo "Build completed successfully."
fi

# Check if JAR exists
if [ ! -f "$JAR_PATH" ]; then
    echo "Error: Compiled JAR not found at $JAR_PATH" >&2
    echo "Please run without '-s' / '--skip-build' first to compile the application." >&2
    exit 1
fi

echo "========================================================================"
echo "Starting Nunki standalone application on port $PORT..."
if [ ${#APP_ARGS[@]} -gt 0 ]; then
    echo "Arguments passed to app: ${APP_ARGS[*]}"
fi
echo "========================================================================"

# Run Spring Boot app
# We pass -Dserver.port to override the default port
exec java -Dserver.port="$PORT" -jar "$JAR_PATH" "${APP_ARGS[@]:+${APP_ARGS[@]}}"
