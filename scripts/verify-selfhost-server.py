"""Read-only loopback checks against an already running prepared Votex server.

Queries Minecraft status and proves the online-mode authentication challenge.
Does not log in, accept the EULA, start a process or modify server settings.
"""
from __future__ import annotations

import argparse
import hashlib
import io
import json
from pathlib import Path
import socket
import struct
import uuid
import zipfile


def varint(value: int) -> bytes:
    result = bytearray()
    while True:
        b = value & 0x7f; value >>= 7
        result.append(b | (0x80 if value else 0))
        if not value:
            return bytes(result)


def exact(stream, length: int) -> bytes:
    if not 0 <= length <= 1_048_576:
        raise ValueError("Unexpected Minecraft packet size")
    data = bytearray()
    while len(data) < length:
        part = stream.recv(length - len(data)) if hasattr(stream, "recv") else stream.read(length - len(data))
        if not part:
            raise EOFError("Minecraft connection closed")
        data.extend(part)
    return bytes(data)


def read_varint(stream) -> int:
    value = 0
    for n in range(5):
        byte = exact(stream, 1)[0]
        value |= (byte & 0x7f) << (7 * n)
        if not byte & 0x80:
            return value
    raise ValueError("Invalid Minecraft VarInt")


def string(value: str) -> bytes:
    encoded = value.encode("utf-8")
    return varint(len(encoded)) + encoded


def packet(sock, payload: bytes) -> None:
    sock.sendall(varint(len(payload)) + payload)


def receive(sock) -> io.BytesIO:
    return io.BytesIO(exact(sock, read_varint(sock)))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--server-directory", type=Path, required=True)
    parser.add_argument("--port", type=int, default=25565)
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    server = args.server_directory.resolve(strict=True)
    with zipfile.ZipFile(server / "versions/26.2/server-26.2.jar") as jar:
        version = json.loads(jar.read("version.json"))
    properties = {}
    for line in (server / "server.properties").read_text(encoding="utf-8").splitlines():
        if line.strip() and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1); properties[key] = value
    voice_properties = {}
    for line in (server / "config/voicechat/voicechat-server.properties").read_text(encoding="utf-8").splitlines():
        if line.strip() and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1); voice_properties[key] = value
    for key, value in {"online-mode": "true", "white-list": "true", "enforce-whitelist": "true",
                       "enable-rcon": "false", "enable-query": "false", "server-ip": "127.0.0.1"}.items():
        if properties.get(key) != value:
            raise RuntimeError("Expected private-server property missing: " + key)
    if voice_properties.get("bind_address") != "127.0.0.1" or voice_properties.get("allow_recording") != "false":
        raise RuntimeError("Expected private Voice settings missing")
    protocol = version["protocol_version"]
    with socket.create_connection(("127.0.0.1", args.port), timeout=5) as sock:
        packet(sock, b"\x00" + varint(protocol) + string("127.0.0.1") + struct.pack(">H", args.port) + b"\x01")
        packet(sock, b"\x00")
        response = receive(sock)
        if read_varint(response) != 0:
            raise RuntimeError("Unexpected Minecraft status packet")
        status = json.loads(exact(response, read_varint(response)).decode("utf-8"))
    if status["version"]["protocol"] != protocol:
        raise RuntimeError("Running server version differs from prepared server")
    with socket.create_connection(("127.0.0.1", args.port), timeout=5) as sock:
        packet(sock, b"\x00" + varint(protocol) + string("127.0.0.1") + struct.pack(">H", args.port) + b"\x02")
        packet(sock, b"\x00" + string("VotexProbe") + uuid.uuid4().bytes)
        challenge = receive(sock)
        if read_varint(challenge) != 1:
            raise RuntimeError("Online-mode encryption challenge was not returned")
        exact(challenge, read_varint(challenge))  # Server ID, usually empty.
        public_key_size = read_varint(challenge); exact(challenge, public_key_size)
        token_size = read_varint(challenge); exact(challenge, token_size)
        should_authenticate = exact(challenge, 1) == b"\x01"
        if public_key_size < 128 or token_size < 4 or not should_authenticate:
            raise RuntimeError("Server did not require session authentication")
    log = (server / "logs/latest.log").read_text(encoding="utf-8", errors="replace")
    if "Voice chat server started at 127.0.0.1:24454" not in log or "Done (" not in log:
        raise RuntimeError("Server and Voice startup evidence missing")
    manifest = []
    for path in sorted((server / "mods").glob("*.jar")):
        manifest.append({"file": path.name, "sha256": hashlib.sha256(path.read_bytes()).hexdigest()})
    report = {"schemaVersion": 1, "minecraft": version["id"], "protocolVersion": protocol,
              "runningStatusVersion": status["version"]["name"], "mods": manifest,
              "checks": {"minecraftStatusRoundTrip": True, "onlineModeSessionChallenge": True,
                         "whitelistEnforced": True, "rconAndQueryDisabled": True,
                         "loopbackBindings": True, "voiceServerStarted": True,
                         "voiceRecordingDisallowed": True},
              "limitations": ["No Microsoft-authenticated player login was attempted",
                              "No two-client appearance or consent interaction was checked here",
                              "A bound UDP server does not prove microphone audio or Internet reachability"]}
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
