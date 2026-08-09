#!/usr/bin/env bash
# ==============================================================================
# Nunki Middleware Bridge - Environment Activation Script
# ==============================================================================
# REQ-00010 – Full-featured OPC-UA middleware environment configuration
# TSK-00306 – Turnkey environment activation and PATH management
#
# Usage:
#   source activate-env.sh
#   or
#   ./activate-env.sh [check|info]
# ==============================================================================

# Protect against non-bash execution if sourced
if [ -n "${BASH_SOURCE[0]:-}" ]; then
    SCRIPT_PATH="${BASH_SOURCE[0]}"
else
    SCRIPT_PATH="$0"
fi

SCRIPT_DIR="$(cd "$(dirname "$SCRIPT_PATH")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR"
TOOLS_DIR="$PROJECT_ROOT/.tools"

# Color Definitions (Cyberpunk / High-Tech Theme)
CYAN='\033[1;36m'
MAGENTA='\033[1;35m'
GREEN='\033[1;32m'
YELLOW='\033[1;33m'
BLUE='\033[1;34m'
RED='\033[1;31m'
NC='\033[0m' # No Color

# ------------------------------------------------------------------------------
# 1. Local Tool Detection & Path Prep
# ------------------------------------------------------------------------------

# Check for local JDK tarball extraction in .tools/jdk
if [ -d "$TOOLS_DIR/jdk" ]; then
    JDK_HOME=""
    if [ -f "$TOOLS_DIR/jdk/bin/java" ]; then
        JDK_HOME="$TOOLS_DIR/jdk"
    else
        SUB_JDK="$(find "$TOOLS_DIR/jdk" -maxdepth 4 -name "java" -type f 2>/dev/null | head -n 1)"
        if [ -n "$SUB_JDK" ]; then
            JDK_HOME="$(dirname "$(dirname "$SUB_JDK")")"
        fi
    fi
    if [ -n "$JDK_HOME" ]; then
        export JAVA_HOME="$JDK_HOME"
        export PATH="$JAVA_HOME/bin:$PATH"
    fi
fi

# Check for local Maven tarball extraction in .tools/maven
if [ -d "$TOOLS_DIR/maven" ]; then
    MVN_HOME=""
    if [ -f "$TOOLS_DIR/maven/bin/mvn" ]; then
        MVN_HOME="$TOOLS_DIR/maven"
    else
        SUB_MVN="$(find "$TOOLS_DIR/maven" -maxdepth 4 -name "mvn" -type f 2>/dev/null | head -n 1)"
        if [ -n "$SUB_MVN" ]; then
            MVN_HOME="$(dirname "$(dirname "$SUB_MVN")")"
        fi
    fi
    if [ -n "$MVN_HOME" ]; then
        export MAVEN_HOME="$MVN_HOME"
        export PATH="$MAVEN_HOME/bin:$PATH"
    fi
fi

# ------------------------------------------------------------------------------
# 2. Nunki Default Environment Variables
# ------------------------------------------------------------------------------
export QUASAR_OPCUA_URL="${QUASAR_OPCUA_URL:-opc.tcp://localhost:4840/opcua}"
export OPCUA_EMBEDDED_SERVER_ENABLED="${OPCUA_EMBEDDED_SERVER_ENABLED:-false}"
export OPCUA_EMBEDDED_SERVER_PORT="${OPCUA_EMBEDDED_SERVER_PORT:-4840}"
export PORT="${PORT:-8080}"
export NUNKI_ENV_ACTIVE="true"

# ------------------------------------------------------------------------------
# 3. Environment Display & Verification
# ------------------------------------------------------------------------------
show_env_info() {
    echo -e "${CYAN}╔════════════════════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${CYAN}║                    ⚡ NUNKI ENVIRONMENT ACTIVATED ⚡                        ║${NC}"
    echo -e "${CYAN}╠════════════════════════════════════════════════════════════════════════════╣${NC}"
    echo -e "  ${MAGENTA}• Project Root:${NC}                $PROJECT_ROOT"
    
    if command -v java >/dev/null 2>&1; then
        JAVA_VER="$(java -version 2>&1 | head -n 1)"
        echo -e "  ${GREEN}• Java Binary:${NC}                 $(command -v java) (${JAVA_VER})"
    else
        echo -e "  ${RED}• Java Binary:${NC}                 NOT FOUND (Requires JDK 17+)"
    fi

    if [ -n "${JAVA_HOME:-}" ]; then
        echo -e "  ${GREEN}• JAVA_HOME:${NC}                   $JAVA_HOME"
    fi

    if command -v mvn >/dev/null 2>&1; then
        MVN_VER="$(mvn -version 2>&1 | head -n 1)"
        echo -e "  ${GREEN}• Maven Binary:${NC}                $(command -v mvn) (${MVN_VER})"
    else
        echo -e "  ${RED}• Maven Binary:${NC}                NOT FOUND (Requires Maven 3.8+)"
    fi

    if [ -n "${MAVEN_HOME:-}" ]; then
        echo -e "  ${GREEN}• MAVEN_HOME:${NC}                  $MAVEN_HOME"
    fi

    if command -v docker >/dev/null 2>&1; then
        echo -e "  ${GREEN}• Docker Container Engine:${NC}     $(command -v docker)"
    elif command -v podman >/dev/null 2>&1; then
        echo -e "  ${GREEN}• Podman Container Engine:${NC}     $(command -v podman)"
    else
        echo -e "  ${YELLOW}• Container Engine:${NC}            Not Detected (Docker/Podman)"
    fi

    echo -e "${CYAN}----------------------------------------------------------------------------${NC}"
    echo -e "  ${MAGENTA}• QUASAR_OPCUA_URL:${NC}            $QUASAR_OPCUA_URL"
    echo -e "  ${MAGENTA}• EMBEDDED_SERVER_ENABLED:${NC}     $OPCUA_EMBEDDED_SERVER_ENABLED"
    echo -e "  ${MAGENTA}• EMBEDDED_SERVER_PORT:${NC}        $OPCUA_EMBEDDED_SERVER_PORT"
    echo -e "  ${MAGENTA}• HTTP_PORT (server.port):${NC}     $PORT"
    echo -e "${CYAN}╚════════════════════════════════════════════════════════════════════════════╝${NC}"
}

if [ "${BASH_SOURCE[0]:-}" = "$0" ] || [ "${1:-}" = "info" ] || [ "${1:-}" = "check" ]; then
    show_env_info
else
    echo -e "${CYAN}[Nunki] Environment variables and tool paths activated successfully.${NC}"
fi
