# Create Factory Graph

Server-side performance mod for Create on NeoForge 1.21.1. It keeps a graph of each kinetic network and uses that graph to skip tick work Create would otherwise do every tick.

Mod id `create_factory_graph`. License [CC0 1.0](LICENSE).

One jar covers Create 6.0.5 through 6.0.10. The dependency range is `[6.0.5,6.0.11)`. The Gradle build compiles against Create 6.0.10.

## What it does

**Factory graph.** On the first kinetic network change (attach, detach, source or network change, block place or break) the network is rebuilt as a graph. Nodes are kinetic block entities: source, relay, belt controller, belt segment, and machine, plus logistics endpoints at belt ends (chute, depot, funnel, and similar). Edges are Create's own kinetic connections, plus belt-to-endpoint item links. Stored values include speed, stress impact, capacity, and belt item counts. Rebuilds are event-driven and capped per tick.

**Dormant relays.** Settled decorative relays (shaft, cogwheel, large cogwheel, encased shaft or cogwheel, chain drive, gearbox) and non-controller belt segments come off the block-entity ticker. Each level tick the graph advances instead. It runs Create's own source validation for dormant nodes at Create's `kineticValidationFrequency`, spread across the 60-tick window. A kinetic state change (speed, source, network, overstress flicker, rotation particles, contraption shape, block state, or removal) puts the node back on the ticker. Matching is exact class and exact block-entity type, so addon subclasses and anything with its own tick behavior stay awake.

**Belt cadence.** Belt controllers still tick every tick, so item movement stays the same. A jammed belt (nothing moved for 40 ticks, no processing-locked items, no riders, nothing queued) backs its inventory tick off from 1 to 2 to 4 to 8 ticks, then returns to full rate on any insert or change. A skipped tick simply does not run. This mod does not move, drop, or eject items.

**Stress.** Create already recalculates stress on network events. This mod does not add a per-tick stress pass, and it rebuilds graphs only when the network changes.

**Ship cadence.** `com.vws.createfactorygraph.api.FactoryCadence` (API version 2) lets a ship mod, such as Aeronautics via `aeronautics_ship_hull`, register a locator and a hull mode by reflection. No compile dependency on the ship mod. Graphs whose blocks sit on that ship follow the hull cadence. Ground factories are left alone. While the ship is `CHEAP`, the default policy is `SLOW`: each block entity ticks once per hull period and defers the rest. `SLEEP` defers every tick. `BURST` runs the whole period back to back on the hull step. When the ship returns to `FULL` or `REBUILDING`, deferred ticks are replayed through Create's own `SmartBlockEntity.tick()`, a few per tick. Debt past the cap is dropped, and items are not touched. With no ship mod registered, nothing is parented and factories tick the same way they do on the ground.

No chunk loading. Lookups use `getChunkNow` and Create's loaded checks.

## Commands

Op level 2.

```
/factorygraph status
/factorygraph enable
/factorygraph disable
/factorygraph graphs
/factorygraph rebuild
/factorygraph inspect <pos>
/factorygraph profile start|stop|report
/factorygraph cadence
/factorygraph cadence on|off
/factorygraph cadence policy <policy>
/factorygraph cadence parent <pos> <key>
/factorygraph cadence unparent <pos>
/factorygraph cadence set <key> <mode> [period]
/factorygraph cadence inspect <pos>
```

`disable` wakes every dormant block and returns Create to stock ticking, which is the switch you want for an A/B check.

## Config

`config/create_factory_graph-common.toml`

- `graph.enabled`: master switch
- `graph.maxRebuildsPerTick`: default 16
- `graph.maxEdgeNodes`: default 10000. Larger networks keep nodes and skip the edge list
- `dormancy.dormantRelays`, `dormantBeltSegments`, `settleTicks` (40), `dormantValidation`
- `belts.jamBackoff`, `jamDetectTicks` (40), `maxBackoffTicks` (8)
- `ship_cadence.enabled`, `cheapPolicy` (`SLOW`), `defaultHullPeriod` (2), `catchUpTicksPerTick` (2), `maxDebtTicks` (6000), `cheapSettleTicks` (20)

## Server only

There is no client code. `displayTest` is `IGNORE_ALL_VERSION`, so clients do not need the mod. Belt controllers still tick and sync, so belts render the same as stock. Dormant relays do not send anything new to clients.

## Limits

- Machines (press, mixer, saw, fan, deployer, funnels, tunnels, and the rest) still tick. Dormancy covers relays and belt segments, not machine logic.
- A belt that stalls past the jam window can take up to `maxBackoffTicks` (8) extra ticks to notice the jam cleared.
- A rebuild is a full rebuild of the changed network. Above `maxEdgeNodes` the edge list is skipped. Incremental rebuild is not in this version.
- Very large single kinetic networks can overflow the server thread stack inside stock Create's recursive `RotationPropagator`. That still happens with this mod removed.

## Build

Java 21.

```bash
./gradlew build
```

Jar: `build/libs/create_factory_graph-1.0.0.jar`

## Pins

NeoForge 21.1.256, Minecraft 1.21.1, Create `[6.0.5,6.0.11)` compiled against 6.0.10-280, Java 21. License CC0-1.0.
