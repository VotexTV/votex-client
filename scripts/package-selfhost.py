"""Build a private reviewable self-host component, without worlds or secrets.

Only explicit source templates and the server-only Relay are included. This is
not a launcher release and does not deploy a public server or download Minecraft.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import zipfile


def validate_relay(relay: Path, catalog: bytes) -> dict:
    with zipfile.ZipFile(relay) as jar:
        metadata = json.loads(jar.read("fabric.mod.json"))
        if metadata["id"] != "votex_social_relay" or metadata["environment"] != "server":
            raise RuntimeError("Only the server-side Votex Relay may enter this package")
        if any("dev/votex/client/" in entry for entry in jar.namelist()):
            raise RuntimeError("Client classes must not be included in the Dedicated Server companion")
        if jar.read("assets/votex_client/catalog/index.json") != catalog:
            raise RuntimeError("Relay cosmetic catalog is stale. Rebuild minecraft/votex-relay before preparing a self-host package.")
        return metadata


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    project = Path(__file__).resolve().parents[1]
    templates = project / "minecraft/votex-selfhost"
    relay = project / "minecraft/votex-relay/build/libs/votex-social-relay-0.4.1.jar"
    source = relay.with_name("votex-social-relay-0.4.1-sources.jar")
    catalog = (project / "minecraft/votex-core/src/main/resources/assets/votex_client/catalog/index.json").read_bytes()
    metadata = validate_relay(relay, catalog)
    files = {
        name: templates / name for name in (
            "README.md", "Prepare-Server.ps1", "Start-Server.ps1", "Start-Server.sh",
            "Prepare-Tunnel.ps1", "server.properties.example", "voicechat-server.properties.example"
        )
    }
    files.update({"mods/" + relay.name: relay, "sources/" + source.name: source,
                  "LICENSE": project / "LICENSE"})
    manifest = {
        "schemaVersion": 1, "status": "in-progress self-host component; not a launcher release",
        "minecraft": "26.2", "fabricLoader": "0.19.5", "fabricApi": "0.161.0+26.2",
        "simpleVoiceChat": "2.6.24+26.2", "relayVersion": metadata["version"],
        "catalogSha256": hashlib.sha256(catalog).hexdigest(),
        "containsWorlds": False, "containsPrivateKeysOrTokens": False,
        "files": [{"file": name, "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}
                  for name, path in sorted(files.items())]
    }
    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, "x", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name, path in sorted(files.items()):
            info = zipfile.ZipInfo(name, date_time=(2026, 10, 21, 0, 0, 0))
            info.create_system = 3
            info.external_attr = (0o100755 if name.endswith(".sh") else 0o100644) << 16
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, path.read_bytes())
        archive.writestr("manifest.json", json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
    with zipfile.ZipFile(output) as archive:
        if archive.testzip() is not None:
            raise RuntimeError("Packaged self-host archive failed CRC verification")
        for entry in manifest["files"]:
            if hashlib.sha256(archive.read(entry["file"])).hexdigest() != entry["sha256"]:
                raise RuntimeError("Packaged self-host file differs from manifest")
    print(json.dumps({"artifact": str(output), "bytes": output.stat().st_size,
                      "sha256": hashlib.sha256(output.read_bytes()).hexdigest(),
                      "fileCount": len(files) + 1, "validation": "CRC + every per-file SHA-256 verified"}, indent=2))


if __name__ == "__main__":
    main()
