# Navigation by Developer Topics

This index organizes the Nunki Control Panel documentation by developer topics and operational areas. Use this page as a starting point depending on your discipline.

---

## 📡 Topic A: Real-Time Communication & Control
Focuses on asynchronous WebSocket/STOMP exchange models, raw OPC-UA server communication, and Milo client integration.
- [OPC-UA Backend Integration Specifications](architecture/OPCUA-Integration.md): Technical details on OpcUaClientService, connection manager, request queue, and integration tests.
- [Svelte Frontend Architectural Analysis](architecture/Svelte-Frontend-Analysis.md): Detailed information on full-stack event callbacks and WebSocket updates.
- [STOMP Client Service implementation](file:///home/vortigern/git/nunki/src/main/frontend/src/lib/websocket.svelte.ts): Svelte runes implementation for WebSocket telemetry synchronization.

---

## 🎨 Topic B: Frontend HMI & Mimics
Focuses on Svelte 5 frontend viewports, inline SVG process flows, and telemetry trend charts.
- [Svelte Frontend Overview](architecture/Svelte-Frontend.md): Frontend architecture boot flow, global styling CSS variables, and components overview.
- [Svelte Component Wireframes](architecture/Svelte-Component-Wireframes.md): PlantUML Salt wireframe layout diagrams for all views (Home, Navbar, Tree, Synoptics, Trend Charts).
- [Industrial mimic SVG diagrams](file:///home/vortigern/git/nunki/src/main/frontend/src/lib/SynopticView.svelte): SVGs modeling Feed Pump blades, Vessel levels, and Drain Valves.
- [Real-time SVGs Charts](file:///home/vortigern/git/nunki/src/main/frontend/src/lib/TimeSeriesChart.svelte): Coordinate scaling code for trend graphs.

---

## 🧪 Topic C: Testing, Mocking & Code Integrity
Focuses on code quality rules, test framework requirements, and Mockito unit testing guidelines.
- [Mockito Integration Plan](docs/DESIGN.md): Pinned version structures, JUnit 5 extensions integration, and build testing targets.
- [ADR 0010: Introduce Mockito](docs/adr/ADR-0010.md): Architectural decision record for unit testing mocks and ByteBuddy compatibility.
- [Java Testing Quality Standards](architecture/CS-0030.md#5-clean-code-traceability--verification-standards): Preconditions checks, null-safety tests, and stress testing.

---

## 🛠️ Topic D: Project Operations & Scripting
Focuses on repository build commands, Docker compilation, and execution helper scripts.
- [Developer Helpers Reference](helpers/HELPERS.md): CLI runners, docker-compose files, standalone executions, and packaging scripts.
- [Preliminary Project Requirements](misc/requirements.md): Scope matrix outlining backend Kafka/Mongo, frontend SVG mimics, and authentication LDAPs.
- [build-docs.sh Documentation Builder](file:///home/vortigern/git/nunki/helpers/build-docs.sh): Helper automation tool for compiling diagrams and MkDocs manuals.
