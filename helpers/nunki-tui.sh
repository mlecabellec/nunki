#!/usr/bin/env bash
# ==============================================================================
# 🌌 NUNKI MIDDLEWARE BRIDGE - CYBERPUNK TUI BASH LAUNCHER & ORCHESTRATOR
# ==============================================================================
# REQ-00010 – Full-featured OPC-UA middleware helper script
# TSK-00307 – Fancy Cyberpunk TUI in Bash with Python fallback
# ==============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
PYTHON_TUI="$SCRIPT_DIR/nunki-tui.py"
ACTIVATE_SH="$PROJECT_ROOT/activate-env.sh"

# Color Definitions (Cyberpunk Neon Palette)
CYAN='\033[1;36m'
MAGENTA='\033[1;35m'
GREEN='\033[1;32m'
YELLOW='\033[1;33m'
BLUE='\033[1;34m'
RED='\033[1;31m'
BOLD='\033[1m'
NC='\033[0m'

# Source environment activation if present
if [ -f "$ACTIVATE_SH" ]; then
    # Sourced silently
    source "$ACTIVATE_SH" >/dev/null 2>&1 || true
fi

show_banner() {
    clear
    echo -e "${CYAN}┌──────────────────────────────────────────────────────────────────────────┐${NC}"
    echo -e "${CYAN}│${MAGENTA}  ███╗   ██╗██╗   ██╗███╗   ██╗██╗  ██╗██╗                             ${CYAN}│${NC}"
    echo -e "${CYAN}│${MAGENTA}  ████╗  ██║██║   ██║████╗  ██║██║ ██╔╝██║  [ OT / IT BRIDGE GATEWAY ] ${CYAN}│${NC}"
    echo -e "${CYAN}│${MAGENTA}  ██╔██╗ ██║██║   ██║██╔██╗ ██║█████═╝ ██║  [ SPRING BOOT + MILO OPC ] ${CYAN}│${NC}"
    echo -e "${CYAN}│${MAGENTA}  ██║╚██╗██║██║   ██║██║╚██╗██║██  ██╗ ██║                             ${CYAN}│${NC}"
    echo -e "${CYAN}│${MAGENTA}  ██║ ╚████║╚██████╔╝██║ ╚████║██║ ╚██╗██║  [ SYSTEM STATUS: READY ]   ${CYAN}│${NC}"
    echo -e "${CYAN}│${MAGENTA}  ╚═╝  ╚═══╝ ╚═════╝ ╚═╝  ╚═══╝╚═╝  ╚═╝╚═╝                             ${CYAN}│${NC}"
    echo -e "${CYAN}├──────────────────────────────────────────────────────────────────────────┤${NC}"
    echo -e "${CYAN}│${BOLD}${GREEN}         ⚡ NUNKI OT/IT MIDDLEWARE ORCHESTRATOR [CYBER TUI MODE] ⚡         ${CYAN}│${NC}"
    echo -e "${CYAN}└──────────────────────────────────────────────────────────────────────────┘${NC}"
}

run_bash_tui_menu() {
    while true; do
        show_banner
        echo -e " ${BOLD}${CYAN}SELECT ACTION:${NC}"
        echo -e "  ${CYAN}[1]${NC} ⚡ Quick Launch: Standalone + Embedded Test OPC-UA Server (Port 4840, Web 8080)"
        echo -e "  ${CYAN}[2]${NC} 🌐 Quick Launch: Standalone + External OPC-UA Server (Default/Custom URL)"
        echo -e "  ${CYAN}[3]${NC} 🐳 Quick Launch: Docker Multi-Container Stack (Nunki + Quasar)"
        echo -e "  ${CYAN}[4]${NC} 🔍 System Diagnostics & Tool Analysis"
        echo -e "  ${CYAN}[5]${NC} 🛠️ Turnkey Software Installer & Downloader (.tools JDK/Maven)"
        echo -e "  ${CYAN}[6]${NC} ⚡ Activate Environment & Export Variables (activate-env.sh)"
        echo -e "  ${CYAN}[7]${NC} 🚪 Exit"
        echo
        echo -n -e " ${MAGENTA}Enter selection [1-7]: ${NC}"
        read -r choice

        case "$choice" in
            1)
                echo -e "\n${CYAN}Launching Nunki with Embedded OPC-UA Server...${NC}"
                "$SCRIPT_DIR/run-standalone.sh" run --with-embedded-opcua
                read -r -p "Press ENTER to return to menu..."
                ;;
            2)
                echo -e "\n${CYAN}Launching Nunki with External OPC-UA Server...${NC}"
                "$SCRIPT_DIR/run-standalone.sh" run
                read -r -p "Press ENTER to return to menu..."
                ;;
            3)
                echo -e "\n${CYAN}Launching Docker Multi-Container Stack...${NC}"
                "$SCRIPT_DIR/run-integrated.sh" up
                read -r -p "Press ENTER to return to menu..."
                ;;
            4)
                if command -v python3 >/dev/null 2>&1; then
                    python3 "$PYTHON_TUI" --check
                else
                    echo -e "\n${CYAN}System Tool Check:${NC}"
                    command -v java >/dev/null 2>&1 && echo -e "  - Java: $(java -version 2>&1 | head -n 1)" || echo -e "  - Java: NOT FOUND"
                    command -v mvn >/dev/null 2>&1 && echo -e "  - Maven: $(mvn -version 2>&1 | head -n 1)" || echo -e "  - Maven: NOT FOUND"
                    command -v docker >/dev/null 2>&1 && echo -e "  - Docker: Detected" || echo -e "  - Docker: NOT FOUND"
                fi
                read -r -p "Press ENTER to return to menu..."
                ;;
            5)
                if command -v python3 >/dev/null 2>&1; then
                    python3 "$PYTHON_TUI" --prep
                else
                    echo -e "\n${RED}Python 3 is required for automatic turnkey downloads.${NC}"
                    echo -e "Please install python3 using your package manager or install JDK 17 / Maven directly."
                fi
                read -r -p "Press ENTER to return to menu..."
                ;;
            6)
                source "$ACTIVATE_SH"
                read -r -p "Press ENTER to return to menu..."
                ;;
            7|[qQ])
                echo -e "\n${CYAN}Exiting Nunki TUI Orchestrator. Good hunting! 🚀${NC}"
                exit 0
                ;;
            *)
                echo -e "${RED}Invalid selection. Try again.${NC}"
                sleep 1
                ;;
        esac
    done
}

# Main Execution Routing
COMMAND="${1:-tui}"

case "$COMMAND" in
    "tui"|"ui"|"")
        if command -v python3 >/dev/null 2>&1 && [ -f "$PYTHON_TUI" ]; then
            exec python3 "$PYTHON_TUI" "$@"
        else
            run_bash_tui_menu
        fi
        ;;
    "check"|"diag"|"diagnostics")
        if command -v python3 >/dev/null 2>&1; then
            exec python3 "$PYTHON_TUI" --check
        else
            echo "Java: $(command -v java || echo 'MISSING')"
            echo "Maven: $(command -v mvn || echo 'MISSING')"
        fi
        ;;
    "prep"|"setup"|"download")
        if command -v python3 >/dev/null 2>&1; then
            exec python3 "$PYTHON_TUI" --prep
        else
            echo "Python 3 required for automated downloads."
            exit 1
        fi
        ;;
    "embedded")
        exec "$SCRIPT_DIR/run-standalone.sh" run --with-embedded-opcua "${@:2}"
        ;;
    "external"|"standalone")
        exec "$SCRIPT_DIR/run-standalone.sh" run "${@:2}"
        ;;
    "docker"|"integrated")
        exec "$SCRIPT_DIR/run-integrated.sh" up "${@:2}"
        ;;
    "-h"|"--help"|"help")
        echo -e "${CYAN}Nunki TUI Launcher Options:${NC}"
        echo "  ./nunki-tui.sh [tui]       Launch interactive TUI dashboard interface"
        echo "  ./nunki-tui.sh check       Run system environment & port diagnostics"
        echo "  ./nunki-tui.sh prep        Download portable JDK 17 & Maven tools"
        echo "  ./nunki-tui.sh embedded    Launch standalone with embedded OPC-UA server"
        echo "  ./nunki-tui.sh external    Launch standalone with external OPC-UA server"
        echo "  ./nunki-tui.sh docker      Launch multi-container integrated docker stack"
        ;;
    *)
        echo -e "${RED}Unknown command '$COMMAND'. Run './nunki-tui.sh --help' for details.${NC}"
        exit 1
        ;;
esac
