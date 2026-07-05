---
description: Project-specific architecture notes for the Nunki codebase.
label: project
limit: 5000
read_only: false
---
{
  "existingOpcUaClientService": {
    "path": "src/main/java/com/example/nunki/service/OpcUaClientService.java",
    "language": "Java",
    "framework": "Spring",
    "library": "Eclipse Milo",
    "currentCapabilities": ["connect", "basic read placeholder"],
    "limitations": ["no session management", "no write", "no method call", "no subscription", "no error handling/retry"]
  }
}
