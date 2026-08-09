#!/usr/bin/env python3
"""
==============================================================================
🌌 NUNKI MIDDLEWARE BRIDGE - CYBERPUNK SCI-FI TUI & ENVIRONMENT ORCHESTRATOR
==============================================================================
REQ-00010 – Full-featured OPC-UA middleware environment & management helper
TSK-00307 – High-Impact Cyberpunk Sci-Fi TUI & HUD Interface

Author: Nunki Engineering Team
License: Apache-2.0
==============================================================================
"""

import sys
import os
import subprocess
import shutil
import urllib.request
import json
import socket
import platform
import tarfile
import time
import argparse
from typing import Dict, List, Tuple, Optional

# Base Directories
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.abspath(os.path.join(SCRIPT_DIR, ".."))
TOOLS_DIR = os.path.join(PROJECT_ROOT, ".tools")
JAR_PATH = os.path.join(PROJECT_ROOT, "target", "nunki-0.0.1-SNAPSHOT.jar")

# ANSI Color Codes (Cyberpunk High-Contrast Neon Theme)
ANSI_CYAN = "\033[1;36m"
ANSI_MAGENTA = "\033[1;35m"
ANSI_GREEN = "\033[1;32m"
ANSI_YELLOW = "\033[1;33m"
ANSI_BLUE = "\033[1;34m"
ANSI_RED = "\033[1;31m"
ANSI_BOLD = "\033[1m"
ANSI_DIM = "\033[2m"
ANSI_RESET = "\033[0m"

# Cyberpunk Sci-Fi NUNKI ASCII Banner Art (53 cols wide)
NUNKI_ASCII_BANNER = [
    r"  ███╗   ██╗██╗   ██╗███╗   ██╗██╗  ██╗██╗  ",
    r"  ████╗  ██║██║   ██║████╗  ██║██║ ██╔╝██║  ",
    r"  ██╔██╗ ██║██║   ██║██╔██╗ ██║█████═╝ ██║  ",
    r"  ██║╚██╗██║██║   ██║██║╚██╗██║██  ██╗ ██║  ",
    r"  ██║ ╚████║╚██████╔╝██║ ╚████║██║ ╚██╗██║  ",
    r"  ╚═╝  ╚═══╝ ╚═════╝ ╚═╝  ╚═══╝╚═╝  ╚═╝╚═╝  ",
]

# ------------------------------------------------------------------------------
# System Diagnostics & Telemetry Collector
# ------------------------------------------------------------------------------

def detect_distro() -> str:
    """Detects Linux distribution details."""
    if os.path.exists("/etc/os-release"):
        with open("/etc/os-release", "r") as f:
            lines = f.readlines()
        info = {}
        for line in lines:
            if "=" in line:
                k, v = line.strip().split("=", 1)
                info[k] = v.strip('"')
        return info.get("PRETTY_NAME", info.get("NAME", "Linux"))
    return platform.system() + " " + platform.release()

def check_port(port: int, host: str = "127.0.0.1") -> bool:
    """Checks if a TCP port is open / listening."""
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.settimeout(0.5)
        return s.connect_ex((host, port)) == 0

def check_java() -> Dict[str, str]:
    """Analyzes Java availability and version."""
    java_cmd = shutil.which("java")
    javac_cmd = shutil.which("javac")
    
    local_jdk = os.path.join(TOOLS_DIR, "jdk")
    if os.path.exists(local_jdk):
        candidates = []
        for root, dirs, files in os.walk(local_jdk):
            if "java" in files and os.path.basename(os.path.dirname(root)) == "bin":
                candidates.append(os.path.join(root, "java"))
        for c in candidates:
            if os.path.isfile(c) and os.access(c, os.X_OK):
                java_cmd = c
                javac_cmd = os.path.join(os.path.dirname(c), "javac")
                break

    if not java_cmd:
        return {"status": "MISSING", "version": "Not installed", "path": "", "is_jdk": False}

    try:
        res = subprocess.run([java_cmd, "-version"], capture_output=True, text=True, timeout=5)
        out = res.stderr or res.stdout
        first_line = out.splitlines()[0] if out else "Unknown Java"
        is_jdk = javac_cmd is not None and os.path.exists(javac_cmd)
        return {
            "status": "OK" if any(v in first_line for v in ["17", "21", "22", "23", "24", "25"]) else "WARN_VERSION",
            "version": first_line,
            "path": java_cmd,
            "is_jdk": is_jdk
        }
    except Exception as e:
        return {"status": "ERROR", "version": str(e), "path": java_cmd, "is_jdk": False}

def check_maven() -> Dict[str, str]:
    """Analyzes Maven availability."""
    mvn_cmd = shutil.which("mvn")
    local_mvn = os.path.join(TOOLS_DIR, "maven")
    if os.path.exists(local_mvn):
        for root, dirs, files in os.walk(local_mvn):
            if "mvn" in files and os.path.basename(os.path.dirname(root)) == "bin":
                candidate = os.path.join(root, "mvn")
                if os.access(candidate, os.X_OK):
                    mvn_cmd = candidate
                    break

    if not mvn_cmd:
        return {"status": "MISSING", "version": "Not installed", "path": ""}

    try:
        res = subprocess.run([mvn_cmd, "-version"], capture_output=True, text=True, timeout=5)
        out = res.stdout or res.stderr
        first_line = out.splitlines()[0] if out else "Unknown Maven"
        return {"status": "OK", "version": first_line, "path": mvn_cmd}
    except Exception as e:
        return {"status": "ERROR", "version": str(e), "path": mvn_cmd}

def check_docker() -> Dict[str, str]:
    """Analyzes Docker / Podman container engines."""
    dock = shutil.which("docker")
    podman = shutil.which("podman")
    compose = None

    if dock:
        try:
            res = subprocess.run([dock, "compose", "version"], capture_output=True, text=True, timeout=3)
            if res.returncode == 0:
                compose = "docker compose"
        except Exception:
            pass
        if not compose and shutil.which("docker-compose"):
            compose = "docker-compose"
    
    if not compose and podman:
        if shutil.which("podman-compose"):
            compose = "podman-compose"

    if dock:
        return {"status": "OK", "engine": "Docker", "path": dock, "compose": compose or "None"}
    elif podman:
        return {"status": "OK", "engine": "Podman", "path": podman, "compose": compose or "None"}
    else:
        return {"status": "MISSING", "engine": "None", "path": "", "compose": "None"}

def run_system_diagnostics() -> Dict:
    """Collects comprehensive system telemetry metrics."""
    return {
        "distro": detect_distro(),
        "arch": platform.machine(),
        "java": check_java(),
        "maven": check_maven(),
        "container": check_docker(),
        "port_8080": check_port(8080),
        "port_4840": check_port(4840),
        "jar_exists": os.path.exists(JAR_PATH),
    }

# ------------------------------------------------------------------------------
# Turnkey Tool Downloader & Installer
# ------------------------------------------------------------------------------

ADOPTIUM_API_URL = "https://api.adoptium.net/v3/binary/latest/17/ga/linux/{arch}/jdk/hotspot/normal/eclipse"
MAVEN_DOWNLOAD_URL = "https://dlcdn.apache.org/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz"
MAVEN_FALLBACK_URL = "https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz"

def download_with_progress(url: str, dest_file: str, desc: str = "Downloading"):
    """Downloads a file over HTTP(S) with live progress reporting."""
    print(f"{ANSI_CYAN}⚡ {desc}: {url}{ANSI_RESET}")
    os.makedirs(os.path.dirname(dest_file), exist_ok=True)
    req = urllib.request.Request(url, headers={'User-Agent': 'Nunki-Installer/1.0'})
    try:
        with urllib.request.urlopen(req) as resp, open(dest_file, "wb") as f:
            total = int(resp.headers.get("Content-Length", 0))
            downloaded = 0
            start_time = time.time()
            chunk_size = 65536
            while True:
                chunk = resp.read(chunk_size)
                if not chunk:
                    break
                f.write(chunk)
                downloaded += len(chunk)
                if total > 0:
                    pct = (downloaded / total) * 100
                    elapsed = time.time() - start_time
                    speed = (downloaded / 1024 / 1024) / (elapsed + 0.001)
                    bar = "█" * int(pct / 5) + "░" * (20 - int(pct / 5))
                    print(f"\r  {ANSI_MAGENTA}[{bar}] {pct:.1f}% ({downloaded / 1024 / 1024:.1f}MB / {total / 1024 / 1024:.1f}MB) - {speed:.1f}MB/s{ANSI_RESET}", end="", flush=True)
            print()
        print(f"{ANSI_GREEN}✔ Download complete: {dest_file}{ANSI_RESET}")
    except Exception as e:
        print(f"\n{ANSI_RED}✘ Download failed: {e}{ANSI_RESET}")
        raise e

def install_portable_jdk() -> bool:
    """Downloads and extracts portable JDK 17 into .tools/jdk."""
    arch = platform.machine().lower()
    arch_param = "aarch64" if arch in ["aarch64", "arm64"] else "x64"

    url = ADOPTIUM_API_URL.format(arch=arch_param)
    tar_path = os.path.join(TOOLS_DIR, "jdk17.tar.gz")
    target_dir = os.path.join(TOOLS_DIR, "jdk")

    try:
        download_with_progress(url, tar_path, "Fetching Eclipse Temurin OpenJDK 17 Tarball")
        print(f"{ANSI_CYAN}📦 Extracting OpenJDK 17 into {target_dir}...{ANSI_RESET}")
        os.makedirs(target_dir, exist_ok=True)
        with tarfile.open(tar_path, "r:gz") as tar:
            tar.extractall(path=target_dir)
        os.remove(tar_path)
        print(f"{ANSI_GREEN}✔ Portable OpenJDK 17 successfully installed in .tools/jdk!{ANSI_RESET}")
        return True
    except Exception as e:
        print(f"{ANSI_RED}✘ Failed to install portable JDK: {e}{ANSI_RESET}")
        return False

def install_portable_maven() -> bool:
    """Downloads and extracts portable Apache Maven into .tools/maven."""
    tar_path = os.path.join(TOOLS_DIR, "maven.tar.gz")
    target_dir = os.path.join(TOOLS_DIR, "maven")

    try:
        try:
            download_with_progress(MAVEN_DOWNLOAD_URL, tar_path, "Fetching Apache Maven 3.9.9 Tarball")
        except Exception:
            download_with_progress(MAVEN_FALLBACK_URL, tar_path, "Fetching Apache Maven 3.9.9 (Fallback Archive)")

        print(f"{ANSI_CYAN}📦 Extracting Maven into {target_dir}...{ANSI_RESET}")
        os.makedirs(target_dir, exist_ok=True)
        with tarfile.open(tar_path, "r:gz") as tar:
            tar.extractall(path=target_dir)
        os.remove(tar_path)
        print(f"{ANSI_GREEN}✔ Portable Apache Maven successfully installed in .tools/maven!{ANSI_RESET}")
        return True
    except Exception as e:
        print(f"{ANSI_RED}✘ Failed to install portable Maven: {e}{ANSI_RESET}")
        return False

# ------------------------------------------------------------------------------
# Launchers & Execution Management
# ------------------------------------------------------------------------------

def update_activate_script():
    """Sources activate-env.sh to ensure environment variables are exported."""
    activate_sh = os.path.join(PROJECT_ROOT, "activate-env.sh")
    if os.path.exists(activate_sh):
        subprocess.run(["bash", activate_sh, "info"], check=False)

def launch_standalone(embedded: bool = False, opcua_port: int = 4840, app_port: int = 8080, force_build: bool = False):
    """Launches Nunki in standalone mode."""
    update_activate_script()
    script = os.path.join(PROJECT_ROOT, "helpers", "run-standalone.sh")
    cmd = [script, "run", "-p", str(app_port)]
    if embedded:
        cmd.extend(["--with-embedded-opcua", "--opcua-port", str(opcua_port)])
    if force_build:
        cmd.append("-b")

    print(f"\n{ANSI_CYAN}🚀 Launching Standalone Nunki Server...{ANSI_RESET}")
    print(f"  Command: {ANSI_YELLOW}{' '.join(cmd)}{ANSI_RESET}")
    try:
        subprocess.run(cmd, cwd=PROJECT_ROOT)
    except KeyboardInterrupt:
        print(f"\n{ANSI_YELLOW}Shutdown signal received. Returning to menu...{ANSI_RESET}")

def launch_docker_integrated(force_build: bool = False):
    """Launches Nunki + Quasar multi-container stack via Docker / Podman compose."""
    script = os.path.join(PROJECT_ROOT, "helpers", "run-integrated.sh")
    cmd = [script, "up"]
    if force_build:
        cmd.append("--build")

    print(f"\n{ANSI_CYAN}🐳 Launching Multi-Container Integrated Stack (Quasar + Nunki)...{ANSI_RESET}")
    try:
        subprocess.run(cmd, cwd=PROJECT_ROOT)
    except KeyboardInterrupt:
        print(f"\n{ANSI_YELLOW}Process interrupted.{ANSI_RESET}")

# ------------------------------------------------------------------------------
# Curses Cyberpunk Multi-Module Sci-Fi HUD TUI Engine
# ------------------------------------------------------------------------------

def run_curses_tui(stdscr):
    """Interactive Cyberpunk Sci-Fi Curses TUI HUD Dashboard."""
    import curses

    curses.curs_set(0)
    curses.use_default_colors()
    
    # Define Color Pairs
    # 1: Cyan, 2: Magenta, 3: Green, 4: Yellow, 5: Red, 6: Selection Highlight, 7: Blue, 8: Dim
    curses.init_pair(1, curses.COLOR_CYAN, -1)
    curses.init_pair(2, curses.COLOR_MAGENTA, -1)
    curses.init_pair(3, curses.COLOR_GREEN, -1)
    curses.init_pair(4, curses.COLOR_YELLOW, -1)
    curses.init_pair(5, curses.COLOR_RED, -1)
    curses.init_pair(6, curses.COLOR_BLACK, curses.COLOR_CYAN) # Menu Selection
    curses.init_pair(7, curses.COLOR_BLUE, -1)

    selected_idx = 0
    menu_items = [
        ("⚡ Quick Launch: Standalone + Embedded Test OPC-UA Server", "embedded"),
        ("🌐 Quick Launch: Standalone + External OPC-UA Server", "external"),
        ("🐳 Quick Launch: Docker Multi-Container Stack (Nunki + Quasar)", "docker"),
        ("🔍 System Diagnostics & Environment Analysis", "diagnostics"),
        ("🛠️ Turnkey Software Installer & Downloader (.tools JDK/Maven)", "installer"),
        ("⚙️ Custom Environment & Configuration Builder", "config"),
        ("📊 Live Container & Port Status Monitor", "status"),
        ("📖 Documentation & User Guides", "docs"),
        ("🚪 Exit Dashboard", "exit")
    ]

    log_message = "System initialized. Select an action from the operation menu."

    while True:
        stdscr.clear()
        height, width = stdscr.getmaxyx()

        try:
            # 1. Top HUD Header Box with NUNKI ASCII Art
            stdscr.attron(curses.color_pair(1) | curses.A_BOLD)
            stdscr.addstr(1, 2, "┌" + "─" * (width - 6) + "┐")
            for i in range(2, 9):
                stdscr.addstr(i, 2, "│")
                stdscr.addstr(i, width - 4, "│")
            stdscr.addstr(9, 2, "└" + "─" * (width - 6) + "┘")
            stdscr.attroff(curses.color_pair(1) | curses.A_BOLD)

            # Draw NUNKI ASCII Banner inside Header
            for idx, line in enumerate(NUNKI_ASCII_BANNER):
                stdscr.addstr(2 + idx, 4, line, curses.color_pair(2) | curses.A_BOLD)

            # Draw Right HUD Status Panel inside Header
            right_x = min(width - 45, 60)
            if right_x > 50:
                stdscr.addstr(2, right_x, "❖ NUNKI OT/IT MIDDLEWARE GATEWAY ❖", curses.color_pair(1) | curses.A_BOLD)
                stdscr.addstr(3, right_x, "-----------------------------------", curses.color_pair(1))
                stdscr.addstr(4, right_x, "  • Core Engine:   Spring Boot 3.2.5", curses.color_pair(3))
                stdscr.addstr(5, right_x, "  • OPC UA Stack:  Eclipse Milo 0.6.11", curses.color_pair(3))
                stdscr.addstr(6, right_x, "  • Event Bus:     Kafka & ZeroMQ", curses.color_pair(3))
                stdscr.addstr(7, right_x, "  • Frontend:      Svelte + STOMP WebSockets", curses.color_pair(3))
                stdscr.addstr(8, right_x, "  • System State:  [ ONLINE / READY ]", curses.color_pair(3) | curses.A_BOLD)

            # 2. System Hardware Telemetry Module (Rows 10-15)
            diag = run_system_diagnostics()
            stdscr.attron(curses.color_pair(1))
            stdscr.addstr(10, 2, "┌─ SYSTEM HARDWARE & TOOL DIAGNOSTICS TELEMETRY ────────────────────────────┐")
            
            j_st = diag["java"]["status"]
            j_color = curses.color_pair(3) if j_st == "OK" else curses.color_pair(4)
            m_st = diag["maven"]["status"]
            m_color = curses.color_pair(3) if m_st == "OK" else curses.color_pair(4)
            c_st = diag["container"]["status"]
            c_color = curses.color_pair(3) if c_st == "OK" else curses.color_pair(4)
            
            p8080 = "[BUSY]" if diag["port_8080"] else "[FREE]"
            p4840 = "[BUSY]" if diag["port_4840"] else "[FREE]"

            stdscr.addstr(11, 4, f"• OS: {diag['distro'][:28]:<28} | Arch: {diag['arch']:<8}", curses.color_pair(1))
            stdscr.addstr(12, 4, f"• Java JDK: ", curses.color_pair(1))
            stdscr.addstr(12, 16, f"[{j_st}]", j_color | curses.A_BOLD)
            stdscr.addstr(12, 25, f" Maven: ", curses.color_pair(1))
            stdscr.addstr(12, 33, f"[{m_st}]", m_color | curses.A_BOLD)
            stdscr.addstr(12, 43, f" Container: ", curses.color_pair(1))
            stdscr.addstr(12, 55, f"[{c_st}]", c_color | curses.A_BOLD)
            
            stdscr.addstr(13, 4, f"• Web HTTP Port 8080: ", curses.color_pair(1))
            stdscr.addstr(13, 26, f"{p8080}", curses.color_pair(5) if diag["port_8080"] else curses.color_pair(3))
            stdscr.addstr(13, 35, f"| OPC-UA Port 4840: ", curses.color_pair(1))
            stdscr.addstr(13, 55, f"{p4840}", curses.color_pair(5) if diag["port_4840"] else curses.color_pair(3))
            
            jar_lbl = "FOUND (target/nunki.jar)" if diag["jar_exists"] else "MISSING (Build Required)"
            stdscr.addstr(14, 4, f"• Nunki Fat JAR: {jar_lbl}", curses.color_pair(3) if diag["jar_exists"] else curses.color_pair(4))
            stdscr.addstr(15, 2, "└───────────────────────────────────────────────────────────────────────────┘")
            stdscr.attroff(curses.color_pair(1))

            # 3. Operation Control Menu Module (Rows 16+)
            stdscr.addstr(16, 3, "SELECT ORCHESTRATION COMMAND:", curses.color_pair(2) | curses.A_BOLD)

            menu_start_y = 17
            for i, (label, key) in enumerate(menu_items):
                y = menu_start_y + i
                if y >= height - 4:
                    break
                if i == selected_idx:
                    stdscr.attron(curses.color_pair(6) | curses.A_BOLD)
                    stdscr.addstr(y, 5, f" 🚀 {label:<68} ")
                    stdscr.attroff(curses.color_pair(6) | curses.A_BOLD)
                else:
                    stdscr.attron(curses.color_pair(1))
                    stdscr.addstr(y, 5, f"    {label}")
                    stdscr.attroff(curses.color_pair(1))

            # 4. Live Console & Footer Message
            if height > 28:
                stdscr.addstr(height - 3, 3, f"LOG FEED: {log_message[:width-15]}", curses.color_pair(4))

            footer = " [▲/▼ / k/j]: Select | [ENTER/SPACE]: Execute | [P]: Turnkey Prep | [Q/ESC]: Exit "
            stdscr.addstr(height - 2, max(2, (width - len(footer)) // 2), footer, curses.color_pair(2) | curses.A_BOLD)
            stdscr.refresh()

        except curses.error:
            pass

        key = stdscr.getch()
        if key in [curses.KEY_UP, ord('k'), ord('K')]:
            selected_idx = (selected_idx - 1) % len(menu_items)
        elif key in [curses.KEY_DOWN, ord('j'), ord('J')]:
            selected_idx = (selected_idx + 1) % len(menu_items)
        elif key in [ord('p'), ord('P')]:
            curses.endwin()
            install_portable_jdk()
            install_portable_maven()
            input("\nPress ENTER to return to Dashboard...")
            stdscr = curses.initscr()
        elif key in [ord('\n'), curses.KEY_ENTER, ord(' ')]:
            action = menu_items[selected_idx][1]
            curses.endwin()
            
            if action == "embedded":
                launch_standalone(embedded=True)
                input("\nPress ENTER to return to Dashboard...")
                stdscr = curses.initscr()
            elif action == "external":
                launch_standalone(embedded=False)
                input("\nPress ENTER to return to Dashboard...")
                stdscr = curses.initscr()
            elif action == "docker":
                launch_docker_integrated()
                input("\nPress ENTER to return to Dashboard...")
                stdscr = curses.initscr()
            elif action == "diagnostics":
                print_cli_diagnostics()
                input("\nPress ENTER to return to Dashboard...")
                stdscr = curses.initscr()
            elif action == "installer":
                run_turnkey_installer_menu()
                input("\nPress ENTER to return to Dashboard...")
                stdscr = curses.initscr()
            elif action == "config":
                run_config_builder()
                input("\nPress ENTER to return to Dashboard...")
                stdscr = curses.initscr()
            elif action == "status":
                print_status_monitor()
                input("\nPress ENTER to return to Dashboard...")
                stdscr = curses.initscr()
            elif action == "docs":
                print_docs_info()
                input("\nPress ENTER to return to Dashboard...")
                stdscr = curses.initscr()
            elif action == "exit":
                break
        elif key in [ord('q'), ord('Q'), 27]:
            break

# ------------------------------------------------------------------------------
# CLI Diagnostic & Interactive Sub-Views
# ------------------------------------------------------------------------------

def print_cli_diagnostics():
    """Outputs high-impact Cyberpunk diagnostic report to terminal stdout."""
    diag = run_system_diagnostics()
    print(f"\n{ANSI_CYAN}┌──────────────────────────────────────────────────────────────────────────┐{ANSI_RESET}")
    print(f"{ANSI_CYAN}│{ANSI_MAGENTA}       ███╗   ██╗██╗   ██╗███╗   ██╗██╗  ██╗██╗                        {ANSI_CYAN}│{ANSI_RESET}")
    print(f"{ANSI_CYAN}│{ANSI_MAGENTA}       ████╗  ██║██║   ██║████╗  ██║██║ ██╔╝██║                        {ANSI_CYAN}│{ANSI_RESET}")
    print(f"{ANSI_CYAN}│{ANSI_MAGENTA}       ██╔██╗ ██║██║   ██║██╔██╗ ██║█████═╝ ██║                        {ANSI_CYAN}│{ANSI_RESET}")
    print(f"{ANSI_CYAN}│{ANSI_MAGENTA}       ██║╚██╗██║██║   ██║██║╚██╗██║██  ██╗ ██║                        {ANSI_CYAN}│{ANSI_RESET}")
    print(f"{ANSI_CYAN}│{ANSI_MAGENTA}       ██║ ╚████║╚██████╔╝██║ ╚████║██║ ╚██╗██║                        {ANSI_CYAN}│{ANSI_RESET}")
    print(f"{ANSI_CYAN}│{ANSI_MAGENTA}       ╚═╝  ╚═══╝ ╚═════╝ ╚═╝  ╚═══╝╚═╝  ╚═╝╚═╝                        {ANSI_CYAN}│{ANSI_RESET}")
    print(f"{ANSI_CYAN}├──────────────────────────────────────────────────────────────────────────┤{ANSI_RESET}")
    print(f"{ANSI_CYAN}│{ANSI_BOLD}{ANSI_GREEN}               ❖ SYSTEM TELEMETRY & TOOL DIAGNOSTICS ❖                    {ANSI_CYAN}│{ANSI_RESET}")
    print(f"{ANSI_CYAN}├──────────────────────────────────────────────────────────────────────────┤{ANSI_RESET}")
    print(f"  {ANSI_MAGENTA}• OS Distribution:{ANSI_RESET}            {diag['distro']} ({diag['arch']})")
    
    j = diag["java"]
    j_color = ANSI_GREEN if j["status"] == "OK" else (ANSI_YELLOW if j["status"] == "WARN_VERSION" else ANSI_RED)
    print(f"  {ANSI_MAGENTA}• Java Environment:{ANSI_RESET}           {j_color}{j['status']}{ANSI_RESET} - {j['version']} (Path: {j['path']})")
    
    m = diag["maven"]
    m_color = ANSI_GREEN if m["status"] == "OK" else ANSI_RED
    print(f"  {ANSI_MAGENTA}• Maven Build Tool:{ANSI_RESET}           {m_color}{m['status']}{ANSI_RESET} - {m['version']} (Path: {m['path']})")
    
    c = diag["container"]
    c_color = ANSI_GREEN if c["status"] == "OK" else ANSI_YELLOW
    print(f"  {ANSI_MAGENTA}• Container Engine:{ANSI_RESET}           {c_color}{c['engine']}{ANSI_RESET} (Compose: {c['compose']})")
    
    p8080 = f"{ANSI_RED}Occupied (Active){ANSI_RESET}" if diag["port_8080"] else f"{ANSI_GREEN}Available (Free){ANSI_RESET}"
    p4840 = f"{ANSI_RED}Occupied (Active){ANSI_RESET}" if diag["port_4840"] else f"{ANSI_GREEN}Available (Free){ANSI_RESET}"
    print(f"  {ANSI_MAGENTA}• Port 8080 (Nunki Web):{ANSI_RESET}       {p8080}")
    print(f"  {ANSI_MAGENTA}• Port 4840 (OPC-UA Server):{ANSI_RESET}   {p4840}")
    
    jar_st = f"{ANSI_GREEN}Present ({JAR_PATH}){ANSI_RESET}" if diag["jar_exists"] else f"{ANSI_YELLOW}Missing (Build Required){ANSI_RESET}"
    print(f"  {ANSI_MAGENTA}• Nunki Runnable JAR:{ANSI_RESET}         {jar_st}")
    print(f"{ANSI_CYAN}└──────────────────────────────────────────────────────────────────────────┘{ANSI_RESET}\n")

def run_turnkey_installer_menu():
    """Interactive CLI menu for tool downloads."""
    print(f"\n{ANSI_CYAN}🛠️ TURNKEY SOFTWARE INSTALLER & ENVIRONMENT PREPARATION{ANSI_RESET}")
    print("1. Download & Extract Portable OpenJDK 17 Tarball (.tools/jdk)")
    print("2. Download & Extract Portable Apache Maven Tarball (.tools/maven)")
    print("3. Install BOTH Portable OpenJDK 17 + Maven (.tools)")
    print("4. Return to Dashboard")

    choice = input("\nSelect option [1-4]: ").strip()
    if choice == "1":
        install_portable_jdk()
    elif choice == "2":
        install_portable_maven()
    elif choice == "3":
        install_portable_jdk()
        install_portable_maven()

def run_config_builder():
    """Custom environment configuration builder."""
    print(f"\n{ANSI_CYAN}⚙️ CUSTOM ENVIRONMENT & CONFIGURATION BUILDER{ANSI_RESET}")
    print(f"  QUASAR_OPCUA_URL:             {os.getenv('QUASAR_OPCUA_URL', 'opc.tcp://localhost:4840/opcua')}")
    print(f"  OPCUA_EMBEDDED_SERVER_ENABLED: {os.getenv('OPCUA_EMBEDDED_SERVER_ENABLED', 'false')}")
    print(f"  PORT:                          {os.getenv('PORT', '8080')}")

    url = input("\nEnter custom OPC-UA URL (or press ENTER to keep default): ").strip()
    if url:
        os.environ["QUASAR_OPCUA_URL"] = url

    port = input("Enter custom HTTP Port (or press ENTER to keep 8080): ").strip()
    if port and port.isdigit():
        os.environ["PORT"] = port

    print(f"\n{ANSI_GREEN}✔ Session configuration updated.{ANSI_RESET}")

def print_status_monitor():
    """Prints active container and port monitor."""
    print(f"\n{ANSI_CYAN}📊 LIVE CONTAINER & PORT MONITOR{ANSI_RESET}")
    c = check_docker()
    if c["status"] == "OK" and c["compose"] != "None":
        script = os.path.join(PROJECT_ROOT, "helpers", "run-integrated.sh")
        subprocess.run([script, "status"], check=False)
    else:
        print("Container engine not active or compose tool missing.")

def print_docs_info():
    """Prints documentation references."""
    print(f"\n{ANSI_CYAN}📖 NUNKI DOCUMENTATION & GUIDES{ANSI_RESET}")
    print(f"  • User Manual:               {os.path.join(PROJECT_ROOT, 'helpers', 'HELPERS.md')}")
    print(f"  • Project README:            {os.path.join(PROJECT_ROOT, 'README.md')}")
    print(f"  • MkDocs Source:             {os.path.join(PROJECT_ROOT, 'doc', 'docs')}")
    print(f"\nTo host live documentation preview:")
    print(f"  {ANSI_GREEN}./helpers/build-docs.sh serve{ANSI_RESET}")

# ------------------------------------------------------------------------------
# Entry Point & Argument Parsing
# ------------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(description="Nunki Cyberpunk Sci-Fi TUI Dashboard")
    parser.add_argument("--check", action="store_true", help="Run system diagnostics and exit")
    parser.add_argument("--prep", action="store_true", help="Download missing portable JDK/Maven tools")
    parser.add_argument("--embedded", action="store_true", help="Launch standalone with embedded OPC-UA server")
    parser.add_argument("--external", action="store_true", help="Launch standalone with external OPC-UA server")
    parser.add_argument("--docker", action="store_true", help="Launch integrated docker compose stack")
    parser.add_argument("--cli", action="store_true", help="Run non-interactive CLI mode")

    args = parser.parse_args()

    if args.check:
        print_cli_diagnostics()
        sys.exit(0)
    elif args.prep:
        install_portable_jdk()
        install_portable_maven()
        sys.exit(0)
    elif args.embedded:
        launch_standalone(embedded=True)
        sys.exit(0)
    elif args.external:
        launch_standalone(embedded=False)
        sys.exit(0)
    elif args.docker:
        launch_docker_integrated()
        sys.exit(0)

    if sys.stdout.isatty() and not args.cli:
        try:
            import curses
            curses.wrapper(run_curses_tui)
        except Exception as e:
            print(f"{ANSI_YELLOW}Curses TUI fallback to CLI mode ({e}){ANSI_RESET}")
            print_cli_diagnostics()
    else:
        print_cli_diagnostics()

if __name__ == "__main__":
    main()
