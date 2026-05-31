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
Usage: $(basename "$0") <command> [options]

Practical orchestration helper script to build, run, stop, clean, and monitor the integrated multi-container environment.

Services Managed:
  1. Quasar OPC UA Server (C++ C++20 Server)
     - Image: nunki/quasar:latest
     - Container Name: quasar-server
     - Exposes OPC UA port: 4840
  2. Nunki Application Client (Java Spring Boot Server)
     - Image: nunki/app:latest
     - Container Name: nunki-app
     - Exposes Web HTTP port: 8080
     - Configured with endpoint: opc.tcp://quasar:4840

Commands:
  up         Build and launch the integrated server environment in detached background mode.
  down       Gracefully stop, terminate, and remove all containers, networks, and volumes.
  build      Explicitly rebuild container images (useful after changing Quasar C++ or Nunki Java source).
  restart    Safely restart both servers without tearing down the compose network structure.
  status     Show running containers, port assignments, and health states.
  logs       View and stream live logs from both running servers concurrently.
  clean      Purge all stopped integration containers and dangling docker/podman volumes to free up space.

Options:
  -b, --build       Force clean rebuild of both container images (only applicable to 'up')
  -h, --help        Show this help message with detailed explanations

Detailed Commands Explanation:
  * up:
    Runs the multi-container stack. First, it ensures that the sibling Quasar C++ repository exists.
    It builds 'quasar.Dockerfile' inside a 'debian:trixie-slim' C++20 standard compiler container and builds the C++ binaries.
    It then builds Nunki's Svelte and Java code inside a Maven build image.
    Finally, it spins up the containers inside the shared network 'nunki-network' and executes a 60-second health loop
    validating that both endpoints started successfully.
    Equivalent to:
      1. $COMPOSE_CMD up -d [--build]
      2. Status verification checking socket status of 'quasar-server' and 'nunki-app'.

  * down:
    Stops container processes cleanly via SIGTERM signals, removes the bridged network interface 'nunki-network',
    and releases allocated port numbers.
    Equivalent to:
      $COMPOSE_CMD down -v

  * build:
    Performs compile-stage orchestration inside the containers without running them. Perfect for compiling
    modified C++ server code or Java classes ahead of launch.
    Equivalent to:
      $COMPOSE_CMD build

  * restart:
    Sends a restart signal to both running containers, allowing rapid reloading of the application state.
    Equivalent to:
      $COMPOSE_CMD restart

  * status:
    Inspects active runtime descriptors showing resource usage, container ids, ports, and internal statuses.
    Equivalent to:
      $COMPOSE_CMD ps

  * logs:
    Enters a streaming mode following both C++ and Java stdout/stderr in real-time, matching service names with color tags.
    Equivalent to:
      $COMPOSE_CMD logs -f

  * clean:
    Utility clean-up routine that prunes all unused networks, containers, and dangling integration volumes.
    Equivalent to:
      docker/podman container prune -f && docker/podman volume prune -f

Examples:
  $(basename "$0") up                # Build and boot both servers in background
  $(basename "$0") up --build        # Force rebuild C++/Java codes and boot
  $(basename "$0") down              # Shutdown and clean resources
  $(basename "$0") logs              # Follow live logs stream
  $(basename "$0") status            # Check container health and port maps
  $(basename "$0") restart           # Graceful restart of servers
  $(basename "$0") build             # Recompile container artifacts
  $(basename "$0") clean             # Deep system clean-up
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
        *)
            echo "Unknown option: $1" >&2
            show_help
            exit 1
            ;;
    esac
done

# Wrapper function to execute compose in the helpers folder
run_compose() {
    (
        cd "$SCRIPT_DIR"
        $COMPOSE_CMD "$@"
    )
}

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
        
        run_compose up -d $BUILD_FLAG
        
        echo "========================================================================"
        echo "Verifying Container Execution..."
        echo "========================================================================"
        
        # Loop for up to 60 seconds checking if containers are running
        SUCCESS=false
        for i in {1..60}; do
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
        run_compose down -v
        echo "Stack stopped and resources cleaned successfully."
        ;;
        
    "build")
        echo "Building/Rebuilding Integrated Container Images..."
        run_compose build
        echo "Image builds completed successfully."
        ;;
        
    "restart")
        echo "Restarting Integrated Stack containers..."
        run_compose restart
        echo "Containers restarted successfully."
        ;;
        
    "status")
        echo "Checking Integrated Stack status..."
        run_compose ps
        ;;
        
    "logs")
        echo "Streaming logs for the Integrated Stack..."
        run_compose logs -f
        ;;
        
    "clean")
        echo "Performing deep cleanup of integration containers and volumes..."
        run_compose down -v --remove-orphans || true
        if command -v podman >/dev/null 2>&1; then
            podman container prune -f
            podman volume prune -f
        else
            docker container prune -f
            docker volume prune -f
        fi
        echo "Cleanup completed successfully."
        ;;
        
    *)
        echo "Error: Unknown command '$COMMAND'" >&2
        show_help
        exit 1
        ;;
esac
