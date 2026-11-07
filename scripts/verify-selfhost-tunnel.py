"""Exercise the generated FRP configuration locally; never contacts a public host.

Run Prepare-Tunnel.ps1 -PublicHost 127.0.0.1 into an isolated work directory
first. Certificates remain in that private directory. The JSON report contains
only test results, the pinned binary hashes and sanitized process logs.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import socket
import subprocess
import threading
import time


def free_port(kind: int = socket.SOCK_STREAM) -> int:
    with socket.socket(socket.AF_INET, kind) as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


def wait_until(predicate, timeout: float = 12) -> None:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if predicate():
            return
        time.sleep(0.1)
    raise RuntimeError("Local tunnel proof timed out")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--private-directory", type=Path, required=True)
    parser.add_argument("--binary-directory", type=Path, required=True)
    parser.add_argument("--archive", type=Path, required=True)
    args = parser.parse_args()
    base = args.private_directory.resolve(strict=True)
    binaries = args.binary_directory.resolve(strict=True)
    archive_sha = hashlib.sha256(args.archive.read_bytes()).hexdigest()
    expected = "b90074b2b6a1d792a7a40f085ac6d276bcab2d1bc60201f8a86ca53ce65ce821"
    if archive_sha != expected:
        raise RuntimeError("FRP archive does not match the pinned official SHA-256")
    # Extracted executables must be exactly those of the verified archive.
    import zipfile
    with zipfile.ZipFile(args.archive) as archive:
        for name in ("frps.exe", "frpc.exe"):
            entry = next(n for n in archive.namelist() if n.endswith("/" + name))
            if hashlib.sha256(archive.read(entry)).digest() != hashlib.sha256((binaries / name).read_bytes()).digest():
                raise RuntimeError("Extracted FRP executable does not match the verified archive")
    server_dir, client_dir = base / "public-server", base / "world-host"
    source_server = (server_dir / "frps.toml").read_text(encoding="utf-8")
    source_client = (client_dir / "frpc.toml").read_text(encoding="utf-8")
    if not re.search(r'^serverAddr\s*=\s*"127\.0\.0\.1"', source_client, re.M):
        raise RuntimeError("Only a fresh 127.0.0.1 tunnel configuration may be exercised")
    control, exposed_tcp = free_port(), free_port()
    exposed_udp = free_port(socket.SOCK_DGRAM)
    forbidden_tcp = free_port()
    tcp_echo, udp_echo = free_port(), free_port(socket.SOCK_DGRAM)
    server = source_server.replace('"0.0.0.0"', '"127.0.0.1"')
    server = re.sub(r"(?m)^bindPort = \d+$", f"bindPort = {control}", server)
    server = server.replace("single = 25565", f"single = {exposed_tcp}")
    server = server.replace("single = 24454", f"single = {exposed_udp}")
    client = re.sub(r"(?m)^serverPort = \d+$", f"serverPort = {control}", source_client)
    client = client.replace("localPort = 25565", f"localPort = {tcp_echo}")
    client = client.replace("localPort = 24454", f"localPort = {udp_echo}")
    client = client.replace("remotePort = 25565", f"remotePort = {exposed_tcp}")
    client = client.replace("remotePort = 24454", f"remotePort = {exposed_udp}")
    # Keep source configuration untouched. All test config remains private.
    (server_dir / "proof.toml").write_text(server, encoding="utf-8")
    (client_dir / "proof.toml").write_text(client, encoding="utf-8")
    report = {"schemaVersion": 1, "scope": "loopback only; no public reachability or voice audio claim",
              "frpVersion": "0.71.1", "officialArchiveSha256": archive_sha, "checks": {}, "passed": False}
    processes: list[tuple[subprocess.Popen, object]] = []
    stop = threading.Event()
    tcp_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    udp_sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    tcp_sock.bind(("127.0.0.1", tcp_echo)); tcp_sock.listen(); tcp_sock.settimeout(0.2)
    udp_sock.bind(("127.0.0.1", udp_echo)); udp_sock.settimeout(0.2)

    def tcp_service():
        while not stop.is_set():
            try:
                conn, _ = tcp_sock.accept()
                with conn:
                    conn.settimeout(2)
                    data = conn.recv(2048)
                    if data:
                        conn.sendall(b"votex-tcp:" + data)
            except socket.timeout:
                pass

    def udp_service():
        while not stop.is_set():
            try:
                data, address = udp_sock.recvfrom(2048)
                udp_sock.sendto(b"votex-udp:" + data, address)
            except socket.timeout:
                pass

    threads = [threading.Thread(target=tcp_service, daemon=True), threading.Thread(target=udp_service, daemon=True)]
    for thread in threads:
        thread.start()

    def launch(binary: str, folder: Path, config: str, stem: str):
        log_path = base / (stem + ".log")
        log_file = log_path.open("wb")
        process = subprocess.Popen([str(binaries / binary), "-c", config], cwd=folder,
                                   stdout=log_file, stderr=subprocess.STDOUT,
                                   creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        processes.append((process, log_file))
        return process, log_path

    def log_contains(path: Path, pattern: str) -> bool:
        return re.search(pattern, path.read_text(encoding="utf-8", errors="replace"), re.I) is not None

    try:
        for binary, directory in (("frps.exe", server_dir), ("frpc.exe", client_dir)):
            verified = subprocess.run([str(binaries / binary), "verify", "-c", "proof.toml"], cwd=directory,
                                      capture_output=True, text=True, timeout=10,
                                      creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
            if verified.returncode:
                raise RuntimeError("Generated FRP configuration rejected: " + verified.stdout + verified.stderr)
        report["checks"]["generatedConfigurationAccepted"] = True
        _, server_log = launch("frps.exe", server_dir, "proof.toml", "server")
        wait_until(lambda: log_contains(server_log, "frps started successfully"))
        _, client_log = launch("frpc.exe", client_dir, "proof.toml", "client")
        wait_until(lambda: log_contains(client_log, "start proxy success") and
                   "[votex.voice]" in client_log.read_text(encoding="utf-8", errors="replace"))
        with socket.create_connection(("127.0.0.1", exposed_tcp), timeout=5) as tcp:
            tcp.sendall(b"private-world-check")
            if tcp.recv(2048) != b"votex-tcp:private-world-check":
                raise RuntimeError("TCP tunnel payload mismatch")
        report["checks"]["tcpWorldRoundTrip"] = True
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as udp:
            udp.settimeout(5); udp.sendto(b"voice-transport-check", ("127.0.0.1", exposed_udp))
            if udp.recv(2048) != b"votex-udp:voice-transport-check":
                raise RuntimeError("UDP tunnel payload mismatch")
        report["checks"]["udpVoiceTransportRoundTrip"] = True

        bad_token = re.sub(r'(?m)^auth.token = ".*"$', 'auth.token = "incorrect-proof-token"', client)
        (client_dir / "bad-token.toml").write_text(bad_token, encoding="utf-8")
        _, log = launch("frpc.exe", client_dir, "bad-token.toml", "rejected-token")
        wait_until(lambda: log_contains(log, "token.*(mismatch|incorrect|does not match|doesn't match)|authentication failed"))
        report["checks"]["wrongTokenRejected"] = True

        no_certificate = re.sub(r'(?m)^transport.tls.(?:certFile|keyFile) = .*\n?', "", client)
        (client_dir / "no-certificate.toml").write_text(no_certificate, encoding="utf-8")
        _, log = launch("frpc.exe", client_dir, "no-certificate.toml", "rejected-certificate")
        wait_until(lambda: log_contains(log, "certificate required|bad certificate|certificate.*(reject|fail)|login.*failed"))
        report["checks"]["missingClientCertificateRejected"] = True

        forbidden = client.split("[[proxies]]", 1)[0].replace('user = "votex"', 'user = "votex-proof"')
        forbidden += f'\n[[proxies]]\nname = "forbidden"\ntype = "tcp"\nlocalIP = "127.0.0.1"\nlocalPort = {tcp_echo}\nremotePort = {forbidden_tcp}\n'
        (client_dir / "forbidden-port.toml").write_text(forbidden, encoding="utf-8")
        _, log = launch("frpc.exe", client_dir, "forbidden-port.toml", "rejected-port")
        wait_until(lambda: log_contains(log, "port.*not allowed"))
        report["checks"]["unlistedProxyPortRejected"] = True
        report["passed"] = True
    except Exception as error:
        report["error"] = {"type": type(error).__name__, "message": str(error)}
        if isinstance(error, OSError) and getattr(error, "winerror", None) == 4551:
            report["blockedByApplicationControl"] = True
        raise
    finally:
        for process, log_file in reversed(processes):
            if process.poll() is None:
                process.terminate()
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                process.kill(); process.wait(timeout=5)
            log_file.close()
        stop.set()
        for thread in threads:
            thread.join(timeout=1)
        tcp_sock.close(); udp_sock.close()
        report["ownProcessesStopped"] = all(process.poll() is not None for process, _ in processes)
        (base / "proof-results.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
