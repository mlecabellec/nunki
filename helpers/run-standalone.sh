#!/bin/bash
# REQ-00010 – Full‑featured OPC‑UA client service
# TSK-00305 – Standalone Nunki runner script

set -euo pipefail

# Determine script and root directories
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
JAR_PATH="$ROOT_DIR/target/nunki-0.0.1-SNAPSHOT.jar"

# Default configuration
PORT=8080
FORCE_BUILD=false

show_help() {
    cat << EOF
Usage: $(basename "$0") <command> [options]

Extremely practical helper script to manage, build, test, and run the Nunki server in standalone mode.

Commands:
  run          Compile (if missing) and start the Nunki server in standard web server mode.
  run-cli      Compile (if missing) and start the Nunki server in headless CLI mode.
  build        Perform a full compilation of Svelte frontend assets and package the Java Spring Boot app.
  test         Execute the complete standalone unit and integration test suite.
  clean        Remove all target files, compiled classes, and temporary build assets.

Options:
  -p, --port PORT    Override the Spring Boot web server listening port (default: 8080)
  -b, --build        Force clean and compile before running the server (valid with 'run' and 'run-cli')
  -h, --help         Show this help message with detailed explanations

Detailed Commands Explanation:
  * run:
    Validates Java availability, compiles the Svelte static resources, bundles them into the classpath,
    packages the final repackaged Spring Boot JAR, and launches the embedded Tomcat server on port 8080 (or custom).
    Equivalent to:
      1. mvn clean package -DskipTests (if jar is missing or -b is set)
      2. java -Dserver.port=8080 -jar target/nunki-0.0.1-SNAPSHOT.jar
    
  * run-cli:
    Similar to 'run', but boots the application in CLI headless mode. It passes 'cli' as the first application
    argument, invoking the com.example.nunki.runner.CliRunner class to handle headless background processes.
    Equivalent to:
      1. java -Dserver.port=8080 -jar target/nunki-0.0.1-SNAPSHOT.jar cli
      
  * build:
    Runs the full Maven packaging goal. First downloads or verifies Node.js and npm via the frontend-maven-plugin,
    installs Svelte client-side dependencies, compiles the frontend bundle using Vite, copies the static bundle
    into target/classes/static, compiles all Java source classes (Java 17/21/25 targets), and generates the
    standalone runnable jar.
    Equivalent to:
      mvn clean package -DskipTests
      
  * test:
    Triggers execution of JUnit unit tests and Mockito test classes, verifying data types, models, OPC UA mapping
    rules, and service configurations.
    Equivalent to:
      mvn test

  * clean:
    Deletes the target/ folder to purge all compiled classes, bundled web assets, and packaging archives.
    Equivalent to:
      mvn clean

Examples:
  $(basename "$0") run                 # Start standard server
  $(basename "$0") run -p 8081         # Start standard server on custom port 8081
  $(basename "$0") run -b              # Force clean rebuild and start
  $(basename "$0") run-cli             # Start in CLI headless mode
  $(basename "$0") build               # Build only
  $(basename "$0") test                # Execute Java tests
EOF
}

# Check if no arguments or help requested
if [[ $# -eq 0 || "$1" = "-h" || "$1" = "--help" ]]; then
    show_help
    exit 0
fi

# Extract command
COMMAND="$1"
shift

# Parse options
APP_ARGS=()
while [[ $# -gt 0 ]]; do
    case "$1" in
        -p|--port)
            if [[ -z "${2:-}" || "${2:-}" =~ ^- ]]; then
                echo "Error: --port requires a non-empty port number value." >&2
                exit 1
            fi
            PORT="$2"
            shift 2
            ;;
        -b|--build)
            FORCE_BUILD=true
            shift
            ;;
        *)
            APP_ARGS+=("$1")
            shift
            ;;
    esac
done

# Helper function to check java
check_java() {
    if ! command -v java >/dev/null 2>&1; then
        echo "Error: 'java' is not installed or not in PATH." >&2
        echo "Please install Java JDK/JRE (version 17 or higher) to execute the server." >&2
        exit 1
    fi
}

# Helper function to check maven
check_mvn() {
    if ! command -v mvn >/dev/null 2>&1; then
        echo "Error: 'mvn' (Maven) is not installed or not in PATH." >&2
        echo "Please install Apache Maven to build the application." >&2
        exit 1
    fi
}

# Execute action
case "$COMMAND" in
    "build")
        check_mvn
        echo "========================================================================"
        echo "Executing: Maven Clean & Package (Svelte + Java compilation)"
        echo "========================================================================"
        (cd "$ROOT_DIR" && mvn clean package -DskipTests)
        echo "Build completed successfully. Runnable JAR is located at: $JAR_PATH"
        ;;
        
    "clean")
        check_mvn
        echo "========================================================================"
        echo "Executing: Cleaning target folder..."
        echo "========================================================================"
        (cd "$ROOT_DIR" && mvn clean)
        echo "Purged target directory successfully."
        ;;
        
    "test")
        check_mvn
        echo "========================================================================"
        echo "Executing: Running JUnit Test Suite..."
        echo "========================================================================"
        (cd "$ROOT_DIR" && mvn test)
        ;;
        
    "run"|"run-cli")
        check_java
        
        # Check if JAR is missing or if forced rebuild is requested
        if [ ! -f "$JAR_PATH" ] || [ "$FORCE_BUILD" = true ]; then
            check_mvn
            echo "========================================================================"
            if [ ! -f "$JAR_PATH" ]; then
                echo "JAR not found. Triggering automated build..."
            else
                echo "Forced rebuild requested. Re-compiling..."
            fi
            echo "========================================================================"
            (cd "$ROOT_DIR" && mvn clean package -DskipTests)
        fi
        
        # Final safety check
        if [ ! -f "$JAR_PATH" ]; then
            echo "Error: Spring Boot JAR does not exist at $JAR_PATH." >&2
            exit 1
        fi
        
        # Add 'cli' to app arguments if command is run-cli
        if [ "$COMMAND" = "run-cli" ]; then
            # Prepend 'cli' to arguments list
            APP_ARGS=("cli" "${APP_ARGS[@]}")
        fi
        
        echo "========================================================================"
        echo "Starting Nunki Standalone Server"
        echo "  - Command:       $COMMAND"
        echo "  - Web Port:      $PORT"
        if [ ${#APP_ARGS[@]} -gt 0 ]; then
            echo "  - App Arguments: ${APP_ARGS[*]}"
        fi
        echo "========================================================================"
        
        # Execute the Spring Boot standalone application
        exec java -Dserver.port="$PORT" -jar "$JAR_PATH" "${APP_ARGS[@]:+${APP_ARGS[@]}}"
        ;;
        
    *)
        echo "Error: Unknown command '$COMMAND'" >&2
        show_help
        exit 1
        ;;
esac
