#!/bin/bash
# REQ-00010 – Full‑featured OPC‑UA client service
# TSK-00305 – Integrated Quasar and Nunki Orchestrator

set -euo pipefail

# Determine script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# Verify Quasar sibling directory exists
QUASAR_DIR="$(cd "$SCRIPT_DIR/../../quasar" 2>/dev/null && pwd || true)"
if [ -z "$QUASAR_DIR" ] || [ ! -d "$QUASAR_DIR" ]; then
    echo "Error: Sibling Quasar repository not found. Expected it at: $ROOT_DIR/../quasar" >&2
    exit 1
fi

# Detect compose tool
detect_compose() {
    if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
        echo "docker compose"
    elif command -v podman-compose >/dev/null 2>&1; then
        echo "podman-compose"
    elif command -v docker-compose >/dev/null 2>&1; then
        echo "docker-compose"
    else
        echo ""
    fi
}

COMPOSE_CMD=$(detect_compose)
if [ -z "$COMPOSE_CMD" ]; then
    echo "Error: Neither 'docker compose', 'podman-compose', nor 'docker-compose' was found." >&2
    echo "Please install Docker Compose or Podman Compose to run the integrated stack." >&2
    exit 1
fi

show_help() {
    cat << EOF
Usage: $(basename "$0") [command] [options]

Orchestrates the build and execution of the integrated environment:
  - Quasar (OPC UA C++ Server)
  - Nunki (Java Spring Boot Server Client)

Commands:
  up         Build and start the integrated stack (detached mode by default)
  down       Stop and clean up all containers, networks, and volumes
  status     Show running containers and health status
  logs       View and follow container logs

Options:
  -b, --build       Force rebuild of the container images
  -h, --help        Show this help message

Examples:
  $(basename "$0") up              # Start servers
  $(basename "$0") up --build      # Force rebuild and start servers
  $(basename "$0") down            # Stop and tear down servers
  $(basename "$0") status          # Verify servers are running
  $(basename "$0") logs            # View live logs
EOF
}

# Parse options
COMMAND=""
FORCE_BUILD=false

if [[ $# -eq 0 || "$1" = "-h" || "$1" = "--help" ]]; then
    show_help
    exit 0
fi

# Extract command
COMMAND="$1"
shift

while [[ $# -gt 0 ]]; do
    case "$1" in
        -b|--build)
            FORCE_BUILD=true
            shift
            ;;
        -h|--help)
            show_help
            exit 0
            ;;
        *)
            echo "Unknown option: $1" >&2
            show_help
            exit 1
            ;;
    esac
done

case "$COMMAND" in
    "up")
        echo "========================================================================"
        echo "Launching Integrated Stack: Quasar Server & Nunki Server"
        echo "Using Orchestration Tool: $COMPOSE_CMD"
        echo "========================================================================"
        
        # Build arguments for compose
        BUILD_FLAG=""
        if [ "$FORCE_BUILD" = true ]; then
            BUILD_FLAG="--build"
        fi
        
        # Execute compose in helpers folder to ensure correct relative pathing
        (
            cd "$SCRIPT_DIR"
            $COMPOSE_CMD up -d $BUILD_FLAG
        )
        
        echo "========================================================================"
        echo "Verifying Container Execution..."
        echo "========================================================================"
        
        # Loop for up to 60 seconds checking if containers are running
        # We can use podman ps or docker ps to check
        SUCCESS=false
        for i in {1..60}; do
            # Retrieve status using docker inspect / podman inspect
            # We check containers named 'quasar-server' and 'nunki-app'
            quasar_status=""
            nunki_status=""
            
            if command -v podman >/dev/null 2>&1; then
                quasar_status=$(podman inspect --format '{{.State.Status}}' quasar-server 2>/dev/null || true)
                nunki_status=$(podman inspect --format '{{.State.Status}}' nunki-app 2>/dev/null || true)
            else
                quasar_status=$(docker inspect --format '{{.State.Status}}' quasar-server 2>/dev/null || true)
                nunki_status=$(docker inspect --format '{{.State.Status}}' nunki-app 2>/dev/null || true)
            fi
            
            if [ "$quasar_status" = "running" ] && [ "$nunki_status" = "running" ]; then
                echo "Container statuses:"
                echo "  - quasar-server: $quasar_status (OPC UA Server listening on 4840)"
                echo "  - nunki-app:     $nunki_status (Spring Boot Server listening on 8080)"
                SUCCESS=true
                break
            fi
            
            echo "Waiting for services to boot up... (Attempt $i/60, Quasar: '$quasar_status', Nunki: '$nunki_status')"
            sleep 1
        done
        
        if [ "$SUCCESS" = true ]; then
            echo "========================================================================"
            echo "SUCCESS: Both Quasar and Nunki servers are successfully running."
            echo "  - Quasar OPC UA Server is available on opc.tcp://localhost:4840"
            echo "  - Nunki Web Application is available on http://localhost:8080"
            echo ""
            echo "To view live logs, run: ./helpers/run-integrated.sh logs"
            echo "To shut down the environment, run: ./helpers/run-integrated.sh down"
            echo "========================================================================"
        else
            echo "========================================================================"
            echo "WARNING: One or both containers failed to start within 60 seconds."
            echo "Please inspect logs for details: ./helpers/run-integrated.sh logs"
            echo "========================================================================"
            exit 1
        fi
        ;;
        
    "down")
        echo "Stopping and tearing down the Integrated Stack..."
        (
            cd "$SCRIPT_DIR"
            $COMPOSE_CMD down -v
        )
        echo "Stack stopped and resources cleaned successfully."
        ;;
        
    "status")
        echo "Checking Integrated Stack status..."
        (
            cd "$SCRIPT_DIR"
            $COMPOSE_CMD ps
        )
        ;;
        
    "logs")
        echo "Streaming logs for the Integrated Stack..."
        (
            cd "$SCRIPT_DIR"
            $COMPOSE_CMD logs -f
        )
        ;;
        
    *)
        echo "Unknown command: $COMMAND" >&2
        show_help
        exit 1
        ;;
esac
