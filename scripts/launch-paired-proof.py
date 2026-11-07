"""Run exactly two isolated QA game clients from a previously exported Loom runtime.

Dedicated localhost:25566 must already be ready with the two named QA players.
No Gradle process runs concurrently, so one client cannot delete the other's data.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import time
import uuid


def argument_file_text(arguments: list[str]) -> str:
    # Java @argfile syntax; keep Windows paths with their literal separators.
    return "\n".join('"' + value.replace("\\", "\\\\").replace('"', '\\"') + '"' for value in arguments) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--runtime", type=Path, required=True)
    parser.add_argument("--directory", type=Path, required=True)
    parser.add_argument("--timeout", type=int, default=600)
    args = parser.parse_args()
    project = Path(__file__).resolve().parents[1]
    voice_jar = project / "work/ingame-goal/paired-server/mods/voicechat-fabric-2.6.24+26.2.jar"
    if hashlib.sha256(voice_jar.read_bytes()).hexdigest() != "6721500f14a655d7ad139a5297f24ffb4ae7b0f81d63232721cd4638042cd87e":
        raise RuntimeError("Paired Voice JAR differs from the pinned official 2.6.24+26.2 artifact")
    runtime = json.loads(args.runtime.read_text(encoding="utf-8"))
    if not isinstance(runtime.get("executable"), str) or not Path(runtime["executable"]).is_file():
        raise RuntimeError("Runtime export must name the resolved Java launcher executable")
    if any(str(argument).startswith("@") for argument in runtime["jvmArgs"]):
        raise RuntimeError("Nested Java argfiles must be flattened by export-paired-runtime.gradle")
    directory = args.directory.resolve()
    if "/work/ingame-goal/" not in directory.as_posix() or directory.exists():
        raise RuntimeError("Use a new private work/ingame-goal/paired-* directory")
    with socket.create_connection(("127.0.0.1", 25566), timeout=2):
        pass
    directory.mkdir(parents=True)
    coordination = directory / "coordination"
    coordination.mkdir()
    report = {"schemaVersion": 1, "kind": "two separate native game processes, same loopback Dedicated Server",
              "server": "127.0.0.1:25566", "runtimeSha256": hashlib.sha256(args.runtime.read_bytes()).hexdigest(),
              "clients": [], "passed": False}
    report["sourceHashes"] = {str(path.relative_to(project)).replace("\\", "/"): hashlib.sha256(path.read_bytes()).hexdigest()
                             for path in [project / "minecraft/votex-core/src/main/java/dev/votex/social/VotexRelay.java",
                                          project / "minecraft/votex-core/src/client/java/dev/votex/client/EmotePlayer.java",
                                          project / "minecraft/votex-core/src/client/java/dev/votex/client/VotexSocial.java",
                                          project / "minecraft/votex-core/src/client/java/dev/votex/client/LastServerHistory.java",
                                          project / "minecraft/votex-core/src/gametest/java/dev/votex/client/PairedClientGameTest.java"]}
    children = []
    try:
        for role in ("A", "B"):
            game = directory / ("paired-client-" + role.lower())
            game.mkdir()
            (game / "mods").mkdir()
            shutil.copyfile(voice_jar, game / "mods" / voice_jar.name)
            voice_config = game / "config/voicechat"
            voice_config.mkdir(parents=True)
            (voice_config / "voicechat-client.properties").write_text(
                "# Isolated transport-only QA; onboarding is not accepted.\n"
                "onboarding_finished=false\ndisabled=true\nmuted=true\nvoice_chat_volume=0.0\n"
                "microphone_activation_type=PTT\n", encoding="utf-8")
            name = "VotexPeer" + role
            identity = str(uuid.UUID(bytes=hashlib.md5(("OfflinePlayer:" + name).encode()).digest(), version=3))
            original = list(map(str, runtime["arguments"]))
            game_arguments = []
            replacing = {"--gameDir", "--username", "--uuid", "--accessToken", "--userType"}
            i = 0
            while i < len(original):
                if original[i] in replacing:
                    i += 2
                else:
                    game_arguments.append(original[i]); i += 1
            game_arguments += ["--gameDir", str(game), "--username", name, "--uuid", identity,
                               "--accessToken", "0", "--userType", "legacy"]
            java_arguments = list(map(str, runtime["jvmArgs"]))
            java_arguments.append("-Dvotex.profileId=paired-" + role.lower())
            java_arguments += ["-cp", runtime["classpath"], runtime["mainClass"], *game_arguments]
            argfile = game / "java-arguments.txt"
            argfile.write_text(argument_file_text(java_arguments), encoding="utf-8")
            environment = os.environ.copy()
            for key in ("VOTEX_INGAME_GOAL_TEST", "VOTEX_PERFORMANCE_TEST", "VOTEX_UI_SMOKE", "VOTEX_LIBRARY_CHAT_TEST"):
                environment.pop(key, None)
            environment["VOTEX_PAIRED_ROLE"] = role
            environment["VOTEX_PAIRED_COORDINATION"] = str(coordination)
            logfile = (directory / ("client-" + role.lower() + ".log")).open("wb")
            process = subprocess.Popen([runtime["executable"], "@" + str(argfile)], cwd=game, env=environment,
                                       stdin=subprocess.DEVNULL, stdout=logfile, stderr=subprocess.STDOUT,
                                       creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
            children.append((process, logfile))
            report["clients"].append({"role": role, "username": name, "offlineUuid": identity,
                                      "pid": process.pid, "gameDirectory": str(game)})
        deadline = time.monotonic() + args.timeout
        last_stage = None
        while time.monotonic() < deadline:
            states = [p.poll() for p, _ in children]
            if any(code is not None and code != 0 for code in states):
                raise RuntimeError("A paired client exited unsuccessfully; inspect its private log")
            failed = sorted(coordination.glob("*-failed.json"))
            if failed:
                raise RuntimeError("A paired game check failed: " + failed[0].read_text(encoding="utf-8"))
            complete = all((coordination / (role + "-complete.json")).is_file() for role in ("A", "B"))
            if complete and all(code is not None for code in states):
                report["passed"] = all(code == 0 for code in states)
                break
            stages = sorted(p.stem for p in coordination.glob("*.json"))
            if stages != last_stage:
                print(json.dumps({"pairedStages": stages}, ensure_ascii=False), flush=True)
                last_stage = stages
            if all(code is not None for code in states):
                raise RuntimeError("Both clients exited without their completion records")
            time.sleep(1)
        else:
            raise TimeoutError("Paired proof exceeded its bounded runtime")
    except Exception as error:
        report["error"] = str(error)
        raise
    finally:
        for index, (process, logfile) in enumerate(children):
            if process.poll() is None:
                process.terminate()
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill(); process.wait(timeout=5)
            logfile.close()
            report["clients"][index]["exitCode"] = process.returncode
        (directory / "paired-launch-result.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
