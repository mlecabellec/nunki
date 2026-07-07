# Navigation by System Architecture

This index provides a structured map of all technical integration, architectural decisions, and layout documentation.

---

## 🎨 Frontend Architecture Portal

Guides detailing client-side structure, boot flow, and components layouts:
- [Svelte Frontend Overview](Svelte-Frontend.md): bootstrap flow (`main.ts`), styling parameters (`app.css`), and routing state rules.
- [Svelte 5 Component Analysis](Svelte-Frontend-Analysis.md): deep analysis of Svelte 5 Runes state management and Spring Boot REST mappings.
- [Svelte Component Wireframes](Svelte-Component-Wireframes.md): interactive Salt layouts for all frontend viewports.

---

## 🔌 Backend Architecture Portal

Guides detailing server-side OPC-UA drivers, multithreading executors, and database structures:
- [OPC-UA Backend Integration Specifications](OPCUA-Integration.md): details on eclipse milo client wiring, subscriptions thread execution, request queues, and dockerization.
- [Mockito Unit Test Design](../docs/DESIGN.md): details on Mockito-inline static mocking, JUnit 5 extensions, and class loading compatibility.

---

## ⚖️ Architectural Decision Records (ADRs)

Records of technical changes and architectural validations:
- [ADR 0010: Introduce Mockito for Unit Testing](../docs/adr/ADR-0010.md): records fixed dependency pinning and Mockito-JUnit-Jupiter setup.
