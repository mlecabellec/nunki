# Project Nunki Initialization Report
**Date:** Wednesday, May 13, 2026

## 1. Overview
Project **Nunki** has been initialized as a Java Spring Boot 3.2+ application with a Svelte frontend. It is designed to be a versatile platform supporting both web and CLI operations, with a strong focus on industrial protocols (ZMQ, OPC UA), scalability, and high-quality software standards.

## 2. Completed Milestones

### 2.1 Backend Architecture
- **Framework:** Spring Boot 3 with Java 21 support.
- **Dependencies:** 
  - `spring-boot-starter-web` & `webflux`
  - `spring-boot-starter-data-mongodb`
  - `spring-boot-starter-security` (Hardwired In-Memory MVP)
  - `spring-kafka`
  - `spring-boot-starter-websocket`
  - `jeromq` (ZeroMQ)
  - `eclipse-milo` (OPC UA Client/Server SDK)
- **Artifacts:** Configured for Uber-jar and Source-jar creation.
- **Native Support:** GraalVM `native-maven-plugin` integrated.

### 2.2 Frontend Architecture
- **Framework:** Svelte with TypeScript (via Vite).
- **Integration:** Managed by `frontend-maven-plugin`. The frontend source resides in `src/main/frontend` and is built into the Spring Boot `static` resource directory during the Maven build phase.
- **UI Components (Placeholders):**
  - Dynamic SVG Synoptics
  - Time Series Graphs
  - PlantUML Diagrams
  - Tree Views and Dynamic Tables

### 2.3 Operations & DevOps
- **CLI Mode:** Implemented `CliRunner` for headless operation.
- **Dockerization:** Multi-stage `Dockerfile` provided for Maven build and JRE runtime.
- **Helper Script:** `nunki-helper.sh` provided for common developer tasks (test, offline build, native build).

### 2.4 Documentation & Traceability
- **Requirements:** Stored in `doc/misc/requirements.md`.
- **Quasar Integration:** Initial research into the Quasar C++ project documentation completed to align Nunki's integration capabilities.

## 3. Status of Key Features

| Feature | Status | Note |
| :--- | :--- | :--- |
| Web/CLI Invocation | ✅ Ready | Use `cli` argument for command-line mode. |
| ZMQ/OPC UA/Kafka | ✅ Configured | Libraries included, implementation pending. |
| MongoDB Backend | ✅ Configured | Ready for connection strings. |
| Authentication | 🛠️ Partial | Hardwired MVP complete; LDAP/Kerberos pending. |
| Svelte UI | 🛠️ Partial | Layout ready; interactive modules pending. |
| GraalVM Native | ✅ Configured | Build via `./nunki-helper.sh build:native`. |

## 4. Next Steps
1. Refine OPC UA and ZMQ service implementations.
2. Integrate charting libraries (e.g., D3.js, ECharts) for time series.
3. Establish STOMP over WebSocket for real-time updates from the backend.
4. Define integration test protocols with the Quasar project.
