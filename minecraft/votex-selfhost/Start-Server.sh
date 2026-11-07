#!/usr/bin/env sh
# Start from the directory containing the prepared server; no downloads or login.
set -eu
server_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$server_dir"
java_bin=${JAVA_BIN:-java}
memory_gib=${MEMORY_GIB:-4}
case "$memory_gib" in
  2|3|4|5|6|7|8|9|10|11|12|13|14|15|16|17|18|19|20|21|22|23|24|25|26|27|28|29|30|31|32) ;;
  *) echo 'MEMORY_GIB must be an integer from 2 to 32.' >&2; exit 1 ;;
esac
java_version=$("$java_bin" -version 2>&1 | sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p' | head -n 1)
if [ -z "$java_version" ] || [ "$java_version" -lt 25 ]; then
  echo 'Java 25 or newer is required. Set JAVA_BIN to the correct java executable.' >&2
  exit 1
fi
if [ ! -f fabric-server-launch.jar ]; then
  echo 'Run Prepare-Server.ps1 into a fresh directory first.' >&2; exit 1
fi
if [ ! -f eula.txt ] || ! grep -Eq '^[[:space:]]*eula[[:space:]]*=[[:space:]]*true[[:space:]]*$' eula.txt; then
  echo 'Read https://www.minecraft.net/eula and set eula=true in eula.txt if you agree.' >&2
  exit 1
fi
exec "$java_bin" -Xms512M "-Xmx${memory_gib}G" -jar fabric-server-launch.jar nogui
