# Nunki Helper Tools

This directory contains helper scripts and tools for building, orchestrating, running, and packaging the Nunki server (and its integrated C++ Quasar OPC UA server).

## 1. Standalone Execution (`run-standalone.sh`)

This script manages Java/Maven dependency verification, application compilation, port overrides, and CLI mode forwarding for running Nunki in standalone mode.

```bash
# Display help and options
./helpers/run-standalone.sh --help

# Build and run Nunki server on the default port (8080)
./helpers/run-standalone.sh

# Run the existing build (skip clean compile)
./helpers/run-standalone.sh -s

# Run the server on a custom port
./helpers/run-standalone.sh -p 8085

# Execute the application in headless CLI mode
./helpers/run-standalone.sh cli
```

## 2. Integrated Orchestration (`run-integrated.sh`)

This script manages the multi-container integrated environment (Quasar OPC UA C++ Server + Nunki Client) using `docker compose` or `podman-compose`. It handles checking sibling repository layout correctness, automated tool detection, logs streaming, and live startup status validation.

```bash
# Display help and options
./helpers/run-integrated.sh --help

# Spin up and verify the integrated stack (detached by default)
./helpers/run-integrated.sh up

# Rebuild the containers (e.g., after modifying C++ Quasar server) and launch
./helpers/run-integrated.sh up --build

# View container logs
./helpers/run-integrated.sh logs

# View running container status
./helpers/run-integrated.sh status

# Tear down the stack and clean up networks/volumes
./helpers/run-integrated.sh down
```

## 3. Documentation Builder (`build-docs.sh`)

This script verifies dependencies, compiles PlantUML wireframe diagrams to PNG assets, and runs `mkdocs-kit` to compile the documentation to HTML and PDF.

```bash
# Display help and options
./helpers/build-docs.sh --help

# Build the documentation (default)
./helpers/build-docs.sh build

# Start a local preview server with live HMR
./helpers/build-docs.sh serve

# Clean all build artifacts and compiled diagram images
./helpers/build-docs.sh clean
```

## Services
- **Quasar (OPC UA Server)**: Exposed on port `4840`.
- **Nunki (Spring Boot App)**: Exposed on port `8080` (or host-configured port).

## Configuration
The integration connection between Nunki and Quasar is configured via the `QUASAR_OPCUA_URL` environment variable inside the compose stack.

