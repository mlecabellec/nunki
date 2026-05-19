# Nunki Helper Tools

This directory contains tools for orchestration and deployment using Podman.

## Orchestration with Podman

To build and launch the integrated environment (Quasar server + Nunki app), use `podman-compose`:

```bash
# From this directory
podman-compose up --build
```

## Services
- **Quasar (OPC UA Server)**: Exposed on port 4840.
- **Nunki (Spring Boot App)**: Exposed on port 8080.

## Configuration
The connection between Nunki and Quasar is configured via the `QUASAR_OPCUA_URL` environment variable in `docker-compose.yml`.
