# 🌌 Nunki Project TODO

## 🎯 Current Focus
- [ ] **[TSK-20260804-001]** Real-Time Protocol Core Service Implementation
    - [ ] Refine OPC UA service implementation (`OpcUaClientService` and Eclipse Milo connection manager).
    - [ ] Refine ZeroMQ (JeroMQ) service implementation for publish/subscribe distribution.

## 🔄 Ongoing Tasks (In Progress)
- [ ] **[TSK-20260804-002]** WebSocket Telemetry Synchronization
    - [ ] Establish STOMP over WebSocket for real-time telemetry updates.
    - [ ] Implement backend STOMP endpoints and frontend WebSocket/runes handlers.

## 📋 Backlog (Not Started)
- [ ] **[TSK-20260804-003]** Time Series trend visualization (D3.js / ECharts integration)
- [ ] **[TSK-20260804-004]** Integration test protocols with the Quasar project
- [ ] **[TSK-20260804-005]** Configure authentication backends (LDAP and Kerberos support)
- [ ] **[TSK-20260804-006]** MongoDB connection string configurations and dockerized scaling

## ✅ Recently Completed
- [x] **[TSK-20260513-001]** Spring Boot 3 & Java 21 environment setup
- [x] **[TSK-20260513-002]** Web/CLI invocation mode via `CliRunner`
- [x] **[TSK-20260513-003]** Svelte frontend integration via `frontend-maven-plugin`
- [x] **[TSK-20260513-004]** Multi-stage Dockerfile setup
- [x] **[TSK-20260513-005]** GraalVM Native Maven configuration
- [x] **[TSK-20260513-006]** Mockito testing framework integration and ADR-0010
