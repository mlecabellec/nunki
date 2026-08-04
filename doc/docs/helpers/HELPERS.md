# Nunki Helper Tools: Comprehensive User Manual

This directory contains a suite of utility and orchestrator scripts to build, test, package, document, and execute the **Nunki** server (Spring Boot + Svelte frontend) and the integrated **Quasar** C++ OPC-UA server.

---

## Table of Contents
1. [Prerequisites & System Setup](#prerequisites--system-setup)
2. [Standalone Execution (`run-standalone.sh`)](#1-standalone-execution-run-standalonesh)
3. [Embedded OPC-UA Shortcuts](#2-embedded-opc-ua-shortcuts)
4. [Multi-Container Orchestration (`run-integrated.sh`)](#3-multi-container-orchestration-run-integratedsh)
5. [Documentation Builder & Link Checker](#4-documentation-builder--link-checker)
6. [Packaging Scripts (`package.sh` & `package-quasar.sh`)](#5-packaging-scripts-packagesh--package-quasarsh)
7. [GitHub Actions Workflow Watchers](#6-github-actions-workflow-watchers)
8. [Step-by-Step Guides](#7-step-by-step-guides)
    - [Running Nunki Standalone with the Embedded OPC-UA Server](#running-nunki-standalone-with-the-embedded-opc-ua-server)
    - [Running Nunki in Containers (Build & Run)](#running-nunki-in-containers-build--run)

---

## Prerequisites & System Setup

Before executing any of these scripts, ensure the following dependencies are installed based on your use case:

| Task / Script | Tool Dependencies |
|---|---|
| **Standalone Mode** (`run-standalone.sh`) | Java JDK 17+ (JRE is sufficient for execution, JDK is required to build), Apache Maven (`mvn`), Node.js & npm (handled automatically via maven plugin) |
| **Integrated Stack** (`run-integrated.sh`) | Docker & Docker Compose **OR** Podman & Podman Compose |
| **Docs Compiler** (`build-docs.sh`) | Python 3, `pip`, `mkdocs-kit`, PlantUML |
| **Packaging** (`package.sh` / `package-quasar.sh`) | `dpkg-deb` (Debian), `rpmbuild` (Fedora), `makepkg` (Arch), or CMake/CPack |

---

## 1. Standalone Execution (`run-standalone.sh`)

The `run-standalone.sh` script is the core local runner. It automates Java verification, compiles frontend/backend code, controls runtime ports, and handles CLI executions.

### Usage
```bash
./helpers/run-standalone.sh <command> [options]
```

### Commands

*   `run`: Compiles the application (if missing or forced with `-b`) and starts the Nunki Spring Boot server.
*   `run-cli`: Boots the application in headless CLI mode (`fr.lecabellec.nunki.runner.CliRunner`).
*   `build`: Resolves frontend dependencies, compiles Svelte assets with Vite, builds the Java classes, and compiles a single fat JAR in the `target/` directory.
*   `test`: Runs unit and integration JUnit test suites.
*   `clean`: Deletes all built class files, frontend bundles, and target packages.

### Command Options

*   `-p, --port PORT`: Web port for Spring Boot (default: `8080`).
*   `--with-embedded-opcua`: Activates the embedded OPC-UA stub server (default: `false`).
*   `--opcua-port PORT`: Web port for the embedded OPC-UA server (default: `4840`). Automatically enables the server.
*   `-b, --build`: Forces a full Maven rebuild before running the application (valid with `run` and `run-cli`).
*   `-h, --help`: Displays help messages.

### Examples
```bash
# Compile (if needed) and start Nunki on port 8080
./helpers/run-standalone.sh run

# Force a clean rebuild and start on a custom port 9090
./helpers/run-standalone.sh run -b -p 9090

# Start in CLI headless mode
./helpers/run-standalone.sh run-cli
```

---

## 2. Embedded OPC-UA Shortcuts

For convenience, several shortcut wrapper scripts allow quick launching and testing with/without the integrated Java OPC-UA stub server.

### Application Launchers

*   **`run-app-with-embedded-opcua.sh`**:
    Launches Nunki with the built-in OPC-UA mock server enabled. The OPC-UA server listens on `opc.tcp://localhost:4840`.
    *Usage:* `./helpers/run-app-with-embedded-opcua.sh [options]`
*   **`run-app-without-embedded-opcua.sh`**:
    Launches Nunki in default mode, requiring a separate/external OPC-UA server (such as Quasar).
    *Usage:* `./helpers/run-app-without-embedded-opcua.sh [options]`

### Testing Runner Wrappers

*   **`run-tests-with-embedded-opcua.sh`**:
    Runs the specific Maven JUnit integration tests that verify embedded OPC-UA functionality (`QuasarOpcUaServerTest`, `OpcUaEmbeddedServerIntegrationTest`, `OpcUaEndToEndRestStompTest`, `EmbeddedOpcUaPlaywrightTest`).
    *Usage:* `./helpers/run-tests-with-embedded-opcua.sh`
*   **`run-tests-without-embedded-opcua.sh`**:
    Runs tests that assert proper fallback/disabled behavior and core features (`OpcUaDisabledServerIntegrationTest`, `PingWebSocketTest`, `OpcUaControllerTest`, `DataTypeMapperTest`).
    *Usage:* `./helpers/run-tests-without-embedded-opcua.sh`

---

## 3. Multi-Container Orchestration (`run-integrated.sh`)

This script orchestrates the multi-container stack containing:
1.  **quasar-server**: C++20 Quasar OPC-UA Server (listening on `4840`).
2.  **nunki-app**: Spring Boot client (listening on `8080`, configured to talk to `opc.tcp://quasar:4840`).

The orchestrator dynamically detects whether you are using Docker Compose, Podman Compose, or older `docker-compose` versions. It also checks that the sibling repository `quasar` is correctly placed alongside `nunki` in the filesystem.

### Usage
```bash
./helpers/run-integrated.sh <command> [options]
```

### Commands

*   `up`: Builds container images and starts the environment in the background. It polls the endpoints for up to 60 seconds to guarantee both processes reached a "running" state.
*   `down`: Stops container processes, removes the bridge network `nunki-network`, and destroys temporary Docker volumes.
*   `build`: Explicitly rebuilds container images using raw build contexts.
*   `restart`: Quick restarts the containers without altering networks.
*   `status`: Displays container states and port mappings.
*   `logs`: Follows stdout/stderr streams from both containers simultaneously.
*   `clean`: Performs a deep clean-up of stopped containers and dangling images/volumes.

### Options
*   `-b, --build`: Triggers Docker's `--build` cache-invalidation flag during an `up` command.

### Examples
```bash
# Spin up the containers, rebuild, and check status
./helpers/run-integrated.sh up --build

# View container output logs
./helpers/run-integrated.sh logs

# Tear down the stack
./helpers/run-integrated.sh down
```

---

## 4. Documentation Builder & Link Checker

Two helpers streamline compiling and verifying project documentation.

### Documentation Compiler (`build-docs.sh`)

This script manages `mkdocs-kit` to output static HTML pages and PDFs. It compiles PlantUML wireframe diagrams to PNG assets automatically.

*   `./helpers/build-docs.sh build`: Compiles HTML docs under `doc/site` and builds PDF manuals.
*   `./helpers/build-docs.sh serve`: Hosts a local hot-reloading development preview at `http://localhost:8000`.
*   `./helpers/build-docs.sh clean`: Purges all generated documentation files and static diagram assets.

### Documentation Link Checker (`check-links.py`)

A Python validation script that checks all relative paths, HTML links, script files, and image assets in the compiled site directory (`doc/site`).
*Usage:* `python3 helpers/check-links.py` (Run after building documents).

---

## 5. Packaging Scripts (`package.sh` & `package-quasar.sh`)

These scripts bundle compiled outputs into deployable system packages (`.deb`, `.rpm`, and `.pkg.tar.zst`) and auto-generate systemd service units.

*   **Nunki Java Application Package Compiler (`package.sh`)**:
    Wraps the Maven JAR executable, installs a startup shell command (`/usr/bin/nunki`), and provisions a system user/service.
    *Usage:* `./helpers/package.sh <deb|rpm|arch> [output_directory]`
*   **Quasar C++ Server Package Compiler (`package-quasar.sh`)**:
    Wraps CMake build files (`libquasar_opcua.so`, `libquasar_named.so`, etc.) and executes `cpack` or custom PKGBUILD routines.
    *Usage:* Runs from the Quasar workspace folder: `../nunki/helpers/package-quasar.sh <deb|rpm|arch> [output_directory]`

---

## 6. GitHub Actions Workflow Watchers

Two Python scripts are supplied to poll and verify GitHub Action workflows and GitHub Pages deployments:

*   **`watch-workflows.py`**:
    Queries the GitHub API without authorization. Useful for checking general pipeline status of `mkdocs-kit`, `nunki`, and `quasar` repositories.
    *Usage:* `python3 helpers/watch-workflows.py`
*   **`watch-authenticated.py`**:
    Retrieves OAuth tokens from environment variables (`GITHUB_TOKEN`) or `~/.git-credentials` to monitor private build workflows and track specific GitHub Pages deploy jobs.
    *Usage:* `python3 helpers/watch-authenticated.py`

---

## 7. Step-by-Step Guides

### Running Nunki Standalone with the Embedded OPC-UA Server

Follow this quick guide to run Nunki locally without needing Docker or a native C++ build toolchain:

1.  **Check Java and Maven versions**:
    ```bash
    java -version  # Must be 17 or higher
    mvn -version   # Apache Maven 3.x
    ```
2.  **Clean build the project**:
    ```bash
    ./helpers/run-standalone.sh build
    ```
3.  **Run with the embedded OPC-UA server wrapper**:
    ```bash
    ./helpers/run-app-with-embedded-opcua.sh
    ```
    *   The web interface is hosted on `http://localhost:8080`.
    *   The embedded OPC-UA server will listen on `opc.tcp://localhost:4840`.

---

### Running Nunki in Containers (Build & Run)

Follow this guide to launch the multi-container integrated environment (Quasar C++ Server + Nunki Java App):

1.  **Verify Sibling Repository Structure**:
    The orchestrator requires the following relative directory structures:
    ```text
    /home/m/git/
    ├── quasar/
    └── nunki/
        └── helpers/
    ```
2.  **Start and Build the Container Stack**:
    ```bash
    ./helpers/run-integrated.sh up --build
    ```
    This script will:
    - Temporarily sync the standalone server code into the build context.
    - Compile the Quasar C++20 Server within a Debian container.
    - Compile the Nunki Java app and Svelte assets.
    - Launch both containers on a bridged docker network.
    - Validate container health and report completion.
3.  **Monitor Logs**:
    ```bash
    ./helpers/run-integrated.sh logs
    ```
4.  **Stop the Container Stack**:
    ```bash
    ./helpers/run-integrated.sh down
    ```
