# Nunki Cyberpunk TUI & Turnkey Environment Helper

The **Nunki Middleware Bridge** project includes a high-impact, futuristic **Cyberpunk Terminal UI (TUI)** and environment orchestrator suite (`nunki-helper.sh`, `helpers/nunki-tui.py`, `helpers/nunki-tui.sh`, and `activate-env.sh`).

---

## ⚡ Overview & Features

* **Cyberpunk Aesthetic Design**: High-contrast Neon Cyan (`#00FFFF`) and Neon Magenta (`#FF00FF`) palette with glowing ASCII borders, live pill badges (`[JAVA: OK]`, `[CONTAINER: OK]`), and cyber grid layout.
* **Automated Environment Analysis**: Detects Linux distribution (Debian, Ubuntu, Fedora, Arch, SLES, Alpine), checks OpenJDK version, Maven build tool, Docker / Podman container runtime, and inspects TCP port status for ports `8080` (HTTP) and `4840` (OPC-UA).
* **Turnkey Software Downloader**: Automatically downloads, extracts, and configures portable **Eclipse Temurin OpenJDK 17** and **Apache Maven 3.9+** into a non-root `.tools/` directory with live progress bars.
* **Supported Configurations Launcher**:
  1. **Standalone + Embedded Test OPC-UA Server**: Boots Nunki Java server with integrated Milo OPC-UA mock server on port `4840`.
  2. **Standalone + External OPC-UA Server**: Boots Nunki configured with external OPC-UA endpoint URL (`QUASAR_OPCUA_URL`).
  3. **Docker Multi-Container Stack**: Orchestrates Nunki app container + Quasar C++20 OPC-UA server container on bridge network `nunki-network`.

---

## 🚀 Quick Usage Commands

```bash
# Launch interactive TUI dashboard
./nunki-helper.sh

# Run non-interactive system environment diagnostics
./nunki-helper.sh check

# Download portable JDK 17 & Maven tools to .tools/
./nunki-helper.sh prep

# Activate environment variables in current shell session
source activate-env.sh

# Launch standalone with embedded OPC-UA server
./nunki-helper.sh embedded

# Launch integrated Docker stack
./nunki-helper.sh docker
```

---

## ⚙️ Environment Variables Reference

| Variable Name | Default Value | Description |
|---|---|---|
| `QUASAR_OPCUA_URL` | `opc.tcp://localhost:4840` | OPC-UA server endpoint URL |
| `OPCUA_EMBEDDED_SERVER_ENABLED` | `false` | Enable/disable integrated test OPC-UA server |
| `OPCUA_EMBEDDED_SERVER_PORT` | `4840` | Port for embedded test OPC-UA server |
| `PORT` | `8080` | Spring Boot HTTP web server listening port |
| `JAVA_HOME` | System / `.tools/jdk` | Path to Java JDK home directory |
| `MAVEN_HOME` | System / `.tools/maven` | Path to Maven home directory |

---

## 💡 System Package Installation Proposals

For system-wide package installation, `nunki-helper.sh check` proposes distro-tailored commands:

```bash
# Debian / Ubuntu / Mint
sudo apt update && sudo apt install -y openjdk-17-jdk maven docker.io docker-compose-v2 python3

# Fedora / RHEL / Rocky
sudo dnf install -y java-17-openjdk-devel maven docker docker-compose python3

# Arch Linux / Manjaro
sudo pacman -S --needed jdk17-openjdk maven docker docker-compose python

# openSUSE / SLES
sudo zypper install -y java-17-openjdk-devel maven docker docker-compose python3

# Alpine Linux
apk add openjdk17 maven docker docker-compose python3
```
