# Create Factory Graph (NeoForge 1.21.1)

Server-side performance mod for **Create 6.0.10** (pinned; mixins target its internals).
Mod id `create_factory_graph` · License CC0-1.0 · Not published.

## What it does (v1 / Phase 2)
- **Factory graph per kinetic network.** On the first kinetic network change (attach, detach, source/network
  change, block place/break) the network is (re)built into a graph: nodes = kinetic block entities
  (SOURCE / RELAY / BELT_CONTROLLER / BELT_SEGMENT / MACHINE) plus LOGISTICS endpoints at belt ends
  (chute, depot, funnel...), edges = Create's own kinetic connections + belt->endpoint item links,
  values = speed, stress impact, capacity, belt item counts. Rebuilds are event-driven only and bounded per tick.
- **Graph advance instead of block walking.** Settled decorative relays (shaft, cogwheel, large cogwheel,
  encased shaft/cogwheel, chain drive, gearbox) and non-controller belt segments are taken **off the
  block-entity ticker list** (true "no BE tick"). Each level tick the graph advances instead: it runs Create's
  own source validation for dormant nodes at Create's `kineticValidationFrequency`, bucketed (n/60 per tick).
  Any kinetic state change (speed, source, network, overstress flicker, rotation particles, contraption
  shape update, block-state change, removal) puts the node straight back on the ticker list.
- **Belt cadence preserved.** Belt controllers keep ticking every tick, so item movement/throughput is
  unchanged. A **jammed** belt (nothing changed at all for 40 ticks, no processing-locked items, no riders,
  nothing queued) backs off its inventory tick 1→2→4→8 ticks and resumes at full rate on any insert or
  change. Skipped ticks simply don't run - items are never moved, dropped or ejected by this mod.
- **Stress/connectivity**: Create already recalculates stress only on network events; this mod adds no
  per-tick recalculation and rebuilds graphs only on place/break/power changes.
- No chunk loading/force-loading (uses `getChunkNow` / Create's `isLoaded` checks only).
- Exact class + exact BE type matching: addon subclasses and anything with ticking behaviours are never
  made dormant.

## Commands (op level 2)
`/factorygraph status | enable | disable | graphs | rebuild | inspect <pos> | profile start|stop|report`
`disable` instantly wakes everything and returns Create to stock ticking (used for A/B).

## Config
`config/create_factory_graph-common.toml` - master switch, dormancy toggles, settle ticks, jam detect/backoff caps,
rebuild budget, `maxEdgeNodes` (networks larger than this keep nodes but skip edge lists to bound rebuild cost).

## Server-only
No client code. `displayTest=IGNORE_ALL_VERSION`, so clients don't need it.
**Client item interpolation is not part of v1**: belts render exactly as stock because belt controllers
still tick and sync normally; dormant relays don't send anything new to clients.

## Known limits (v1)
- MACHINE nodes (press, mixer, saw, fan, deployer, funnels, tunnels...) still tick normally; v1 collapses
  relays + belt segments, not machine logic.
- A belt that stalls >2 s waiting on a slow consumer gets up to `maxBackoffTicks` (8) extra latency per stall.
- Graph rebuild is a full rebuild of the changed network (27k-node network ≈ 76 ms with edges); default
  `maxEdgeNodes=10000` skips edge lists above that. Incremental rebuild is future work.
- Very large single kinetic networks (~27k cogs) overflow the server thread stack inside **stock Create**'s
  recursive `RotationPropagator` (verified with this mod removed). Not caused or fixed by this mod.

## Build
```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
./gradlew build
```
Jar: `build/libs/create_factory_graph-1.0.0-phase2.jar`

## Pins
NeoForge 21.1.256 · Create 6.0.10 (mods.toml range `[6.0.10,6.0.11)`) · Java 21
