#!/usr/bin/env python3
"""
Movin Android Control-Plane Bridge (Port 8080)
Provides structured JSON REST endpoints for the Jetpack Compose UI to
supervise Jellyfin (:8096), Copyparty (:3923), and OpenSSH (:8022),
stream logs, read telemetry, and manage data/config.json.
"""
import json
import os
import shutil
import socket
import subprocess
import time
from pathlib import Path
from flask import Flask, jsonify, request

app = Flask(__name__)

HOME_DIR = Path(os.environ.get("HOME", "/data/data/com.termux/files/home"))
MOVIN_DIR = HOME_DIR / "movin"
DATA_DIR = MOVIN_DIR / "data"
LOG_DIR = MOVIN_DIR / "logs"
CONFIG_FILE = DATA_DIR / "config.json"

DATA_DIR.mkdir(parents=True, exist_ok=True)
LOG_DIR.mkdir(parents=True, exist_ok=True)

DEFAULT_CONFIG = {
    "media_dir": "/sdcard/moving",
    "ports": {
        "dashboard": 8080,
        "jellyfin": 8096,
        "copyparty": 3923,
        "openssh": 8022,
    },
    "autostart": {
        "jellyfin": True,
        "copyparty": True,
        "openssh": True,
    },
}

PROCESSES: dict[str, subprocess.Popen] = {}


def load_config() -> dict:
    if not CONFIG_FILE.exists():
        CONFIG_FILE.write_text(json.dumps(DEFAULT_CONFIG, indent=2))
        return DEFAULT_CONFIG.copy()
    try:
        return json.loads(CONFIG_FILE.read_text())
    except Exception:
        return DEFAULT_CONFIG.copy()


def save_config(cfg: dict) -> None:
    CONFIG_FILE.write_text(json.dumps(cfg, indent=2))


def get_lan_ip() -> str:
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
            s.connect(("8.8.8.8", 80))
            return s.getsockname()[0]
    except Exception:
        return "127.0.0.1"


def is_port_open(port: int) -> bool:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.settimeout(0.3)
        return s.connect_ex(("127.0.0.1", port)) == 0


def service_running(name: str, port: int) -> bool:
    proc = PROCESSES.get(name)
    if proc is not None and proc.poll() is None:
        return True
    return is_port_open(port)


def start_service(name: str) -> tuple[bool, str]:
    cfg = load_config()
    media_dir = cfg.get("media_dir", "/sdcard/moving")
    ports = cfg.get("ports", DEFAULT_CONFIG["ports"])
    Path(media_dir).mkdir(parents=True, exist_ok=True)

    if service_running(name, ports.get(name, 0)):
        return True, f"{name} is already running"

    log_path = LOG_DIR / f"{name}.log"
    log_file = open(log_path, "a", encoding="utf-8")

    if name == "jellyfin":
        cmd = ["jellyfin", "--datadir", str(DATA_DIR / "jellyfin")]
    elif name == "copyparty":
        cmd = [
            "python3", "-m", "copyparty",
            "-p", str(ports.get("copyparty", 3923)),
            "-v", f"{media_dir}::rw",
        ]
    elif name == "openssh":
        cmd = ["sshd", "-D", "-p", str(ports.get("openssh", 8022))]
    else:
        log_file.close()
        return False, f"Unknown service: {name}"

    try:
        proc = subprocess.Popen(
            cmd,
            stdout=log_file,
            stderr=subprocess.STDOUT,
            env=os.environ.copy(),
        )
        PROCESSES[name] = proc
        return True, f"Started {name} (pid={proc.pid})"
    except FileNotFoundError as exc:
        log_file.write(f"[error] Binary not found for {name}: {exc}\n")
        log_file.close()
        return False, f"Binary not installed for {name}: {exc}"
    except Exception as exc:
        log_file.write(f"[error] Failed to start {name}: {exc}\n")
        log_file.close()
        return False, str(exc)


def stop_service(name: str) -> tuple[bool, str]:
    proc = PROCESSES.pop(name, None)
    if proc is not None and proc.poll() is None:
        proc.terminate()
        try:
            proc.wait(timeout=4)
        except subprocess.TimeoutExpired:
            proc.kill()
        return True, f"Stopped {name}"
    pattern = {"jellyfin": "jellyfin", "copyparty": "copyparty", "openssh": "sshd"}.get(name)
    if pattern:
        subprocess.run(["pkill", "-f", pattern], check=False)
    return True, f"Stop signal sent to {name}"


def read_memory_stats() -> tuple[int, int]:
    total_kb, avail_kb = 0, 0
    try:
        for line in Path("/proc/meminfo").read_text().splitlines():
            if line.startswith("MemTotal:"):
                total_kb = int(line.split()[1])
            elif line.startswith("MemAvailable:"):
                avail_kb = int(line.split()[1])
    except Exception:
        pass
    used_mb = max(0, (total_kb - avail_kb) // 1024)
    total_mb = max(1, total_kb // 1024)
    return used_mb, total_mb


def read_cpu_percent() -> int:
    try:
        load1, _, _ = os.getloadavg()
        cores = os.cpu_count() or 1
        return min(100, max(0, int((load1 / cores) * 100)))
    except Exception:
        return 0


@app.get("/api/health")
def health():
    return jsonify({"status": "ok", "timestamp": int(time.time())})


@app.get("/api/status")
def status():
    cfg = load_config()
    ports = cfg.get("ports", DEFAULT_CONFIG["ports"])
    media_dir = cfg.get("media_dir", "/sdcard/moving")
    used_mb, total_mb = read_memory_stats()

    disk_free_gb, disk_total_gb = 0.0, 0.0
    try:
        target = media_dir if os.path.exists(media_dir) else "/"
        usage = shutil.disk_usage(target)
        disk_free_gb = round(usage.free / (1024 ** 3), 1)
        disk_total_gb = round(usage.total / (1024 ** 3), 1)
    except Exception:
        pass

    services = [
        {
            "id": "jellyfin",
            "name": "Jellyfin Media Server",
            "port": ports.get("jellyfin", 8096),
            "running": service_running("jellyfin", ports.get("jellyfin", 8096)),
            "description": "Local movie, TV, and music streaming",
        },
        {
            "id": "copyparty",
            "name": "Copyparty File Hub",
            "port": ports.get("copyparty", 3923),
            "running": service_running("copyparty", ports.get("copyparty", 3923)),
            "description": "WebDAV, uploads, and LAN file sharing",
        },
        {
            "id": "openssh",
            "name": "OpenSSH Server",
            "port": ports.get("openssh", 8022),
            "running": service_running("openssh", ports.get("openssh", 8022)),
            "description": "Remote shell and SFTP access",
        },
    ]

    return jsonify({
        "lan_ip": get_lan_ip(),
        "media_dir": media_dir,
        "cpu_percent": read_cpu_percent(),
        "ram_used_mb": used_mb,
        "ram_total_mb": total_mb,
        "disk_free_gb": disk_free_gb,
        "disk_total_gb": disk_total_gb,
        "services": services,
    })


@app.post("/api/services/<name>/<action>")
def control_service(name: str, action: str):
    if action == "start":
        ok, msg = start_service(name)
    elif action == "stop":
        ok, msg = stop_service(name)
    elif action == "restart":
        stop_service(name)
        time.sleep(0.5)
        ok, msg = start_service(name)
    else:
        return jsonify({"ok": False, "message": f"Invalid action: {action}"}), 400
    return jsonify({"ok": ok, "message": msg}), (200 if ok else 500)


@app.get("/api/logs/<name>")
def get_logs(name: str):
    lines = int(request.args.get("lines", "120"))
    if name == "all":
        chunks = []
        for svc in ("jellyfin", "copyparty", "openssh", "bridge"):
            p = LOG_DIR / f"{svc}.log"
            if p.exists():
                tail = p.read_text(errors="ignore").splitlines()[-40:]
                chunks.extend([f"[{svc}] {l}" for l in tail])
        return jsonify({"service": "all", "lines": chunks[-lines:]})

    p = LOG_DIR / f"{name}.log"
    if not p.exists():
        return jsonify({"service": name, "lines": [f"No logs recorded yet for {name}."]})
    content = p.read_text(errors="ignore").splitlines()[-lines:]
    return jsonify({"service": name, "lines": content})


@app.route("/api/config", methods=["GET", "POST"])
def config_endpoint():
    if request.method == "GET":
        return jsonify(load_config())
    payload = request.get_json(silent=True) or {}
    cfg = load_config()
    if "media_dir" in payload:
        cfg["media_dir"] = payload["media_dir"]
    if "ports" in payload and isinstance(payload["ports"], dict):
        cfg.setdefault("ports", {}).update(payload["ports"])
    save_config(cfg)
    return jsonify({"ok": True, "config": cfg})


if __name__ == "__main__":
    cfg = load_config()
    port = cfg.get("ports", {}).get("dashboard", 8080)
    app.run(host="127.0.0.1", port=port, threaded=True)
