#!/bin/sh
# Headless A/B for create_factory_graph on Joshua's pack.
# NeoForge 21.1.233, Create 6.0.10, Sable 2.0.3, Aeronautics 1.3.0, no Sodium.
set -eu
ROOT="$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)"
CACHE="${BENCH_CACHE:-/tmp/bench-dl}"
SERVER="${BENCH_SERVER:-/tmp/nf-server}"
OUT="$ROOT/bench/results"
mkdir -p "$CACHE" "$OUT" "$SERVER"

fetch() {
  url="$1"
  dest="$2"
  if [ ! -f "$dest" ]; then
    echo "fetch $dest"
    curl -fsSL -o "$dest" "$url"
  fi
}

fetch "https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.233/neoforge-21.1.233-installer.jar" "$CACHE/neoforge-21.1.233-installer.jar"
fetch "https://cdn.modrinth.com/data/LNytGWDc/versions/UjX6dr61/create-1.21.1-6.0.10.jar" "$CACHE/create-1.21.1-6.0.10.jar"
fetch "https://cdn.modrinth.com/data/T9PomCSv/versions/1L6XJqnY/sable-neoforge-1.21.1-2.0.3.jar" "$CACHE/sable-neoforge-1.21.1-2.0.3.jar"
fetch "https://cdn.modrinth.com/data/oWaK0Q19/versions/w7zlLnea/create-aeronautics-bundled-1.21.1-1.3.0.jar" "$CACHE/create-aeronautics-bundled-1.21.1-1.3.0.jar"

if [ ! -f "$SERVER/run.sh" ]; then
  (cd "$SERVER" && java -jar "$CACHE/neoforge-21.1.233-installer.jar" --installServer)
fi

echo "building probe"
(cd "$ROOT/bench/probe" && "$ROOT/gradlew" --no-daemon test jar)
PROBE=$(ls "$ROOT/bench/probe/build/libs/"benchprobe-*.jar | head -1)

echo "building factory graph"
(cd "$ROOT" && "$ROOT/gradlew" --no-daemon jar)
MOD=$(ls "$ROOT/build/libs/"create_factory_graph-*.jar | head -1)

write_server_files() {
  printf 'eula=true\n' > "$SERVER/eula.txt"
  cat > "$SERVER/user_jvm_args.txt" <<'EOF'
-Xms2G
-Xmx3G
EOF
  cat > "$SERVER/server.properties" <<'EOF'
online-mode=false
motd=factory-graph-bench
level-type=minecraft:flat
generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains","structure_overrides":[]}
generate-structures=false
spawn-monsters=false
spawn-animals=false
spawn-npcs=false
view-distance=6
simulation-distance=8
pause-when-empty-seconds=-1
max-tick-time=-1
sync-chunk-writes=false
allow-flight=true
spawn-protection=0
enable-command-block=false
max-players=1
EOF
}

run_one() {
  label="$1"
  extra_mod="$2"
  skip="${3:-0}"
  kill_switch="${4:-false}"
  echo "===== run $label ====="
  rm -rf "$SERVER/world" "$SERVER/mods" "$SERVER/config" "$SERVER/logs"
  mkdir -p "$SERVER/mods" "$SERVER/config"
  cp "$CACHE/create-1.21.1-6.0.10.jar" "$SERVER/mods/"
  cp "$CACHE/sable-neoforge-1.21.1-2.0.3.jar" "$SERVER/mods/"
  cp "$CACHE/create-aeronautics-bundled-1.21.1-1.3.0.jar" "$SERVER/mods/"
  cp "$PROBE" "$SERVER/mods/"
  if [ -n "$extra_mod" ]; then
    cp "$extra_mod" "$SERVER/mods/create_factory_graph.jar"
  fi
  if [ -n "$extra_mod" ]; then
    mkdir -p "$SERVER/config"
    # Pin the switch. The mod run must measure the active graph even if the
    # jar's default later changes. Inert forces Create's own ticks.
    if [ "$kill_switch" = "true" ]; then
      cat > "$SERVER/config/create_factory_graph-common.toml" <<'EOF'
[safety]
killSwitch = true
EOF
    else
      cat > "$SERVER/config/create_factory_graph-common.toml" <<'EOF'
[safety]
killSwitch = false
EOF
    fi
  fi
  write_server_files
  out="$OUT/$label.json"
  rm -f "$out"
  set +e
  (
    cd "$SERVER"
    # timeout puts the JVM in its own process group. A dedicated server reads
    # stdin for the console; on a TTY that read gets SIGTTIN and the whole
    # server stops at "Generating keypair". --foreground keeps the JVM in the
    # shell's group, and /dev/null means the console sees EOF instead of a TTY.
    timeout --foreground 900 java @user_jvm_args.txt \
      -Dbench.out="$out" \
      -Dbench.label="$label" \
      -Dbench.skipContraptionTicks="$skip" \
      @libraries/net/neoforged/neoforge/21.1.233/unix_args.txt nogui \
      < /dev/null
  ) > "$OUT/$label.log" 2>&1
  code=$?
  set -e
  echo "run $label exit $code"
  if [ ! -f "$out" ]; then
    echo "missing $out" >&2
    tail -80 "$OUT/$label.log" >&2
    exit 1
  fi
  if ! grep -q BENCHPROBE_DONE "$OUT/$label.log"; then
    echo "server did not report BENCHPROBE_DONE" >&2
    tail -80 "$OUT/$label.log" >&2
    exit 1
  fi
}

run_one baseline "" 0 false
run_one mod "$MOD" 0 false
run_one inert "$MOD" 0 true
run_one paused "" 2 false

python3 "$ROOT/bench/compare.py" "$OUT/baseline.json" "$OUT/mod.json" "$OUT/inert.json" "$OUT/paused.json" "$OUT/comparison.md" "$OUT/comparison.json"
echo "wrote $OUT/comparison.md"
