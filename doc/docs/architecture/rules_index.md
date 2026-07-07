# Navigation by Project Rules & Guidelines

This index organizes the Nunki Control Panel quality gatekeepers, AI automation regulations, and coding standard constraints.

---

## 📋 Quality Standards & Code Traceability

Regulations governing task and requirements mapping:
- **Standard**: [Constraint CS-0010 Quality Standards](CS-0010.md)
  - All source code modifications must reference unique requirement (`REQ-XXXXX`), feature (`FR-XXXXX`), task (`TSK-XXXXX`), or bug fix (`FIX-XXXXX` / `BUG-XXXXX`) codes.
  - Verification includes continuous Checkstyle and PMD gatekeeper builds.

---

## 🤖 AI Agent Integration Regulations

Regulations governing AI agent code contribution, plans execution, and verification:
- **Standard**: [Constraint CS-0020 AI Agent Rules](CS-0020.md)
  - AI agents must perform step-by-step verification, log and trace modifications, draft test-covered deployment plans, and request explicit human verification before deleting or committing code.
  - Requires unit and integration tests for all generated assets.

---

## ☕ Java Programming Standards

Regulations governing class structures, memory scopes, and type checking:
- **Standard**: [Constraint CS-0030 Java Standards](CS-0030.md)
  - Parameter defensive checks (`java.util.Objects.requireNonNull`).
  - Zero passing or returning of `null` values.
  - Bounded GC-flat object/thread pools.
  - OOP patterns for complex flows (State, Strategy, Factory).
  - Explicit types (strict exclusion of `var` syntax).
  - Bounded resources scopes (`try-with-resources`).
