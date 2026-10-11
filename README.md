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

## Kinetic neighbour and stress caches

`caches.neighbours` remembers `RotationPropagator.getConnectedNeighbours` until a block state changes or a neighbour is removed. The cached list is a copy. `caches.stress` keeps the stress and capacity totals Create last computed, then applies the same `impact * abs(speed)` delta `calculateStress` and `calculateCapacity` use. Removing a member drops the total, because Create zeros speed before `remove`. Chunk unload drops every total. Client threads run the original methods.

Measured on 24 networks of 16 cogwheels while creative motors went from 32 to 96 RPM (792 neighbour calls, 72 stress calls). Neighbour scans went from 5.018 ms to 3.243 ms (6335 ns/call to 4094 ns/call, 384 hits). Stress went from 0.200 ms to 0.151 ms (2771 ns/call to 2093 ns/call, 24 hits). The following settled window, 408 neighbour calls, went from 1568 ns/call to 333 ns/call with every call hitting. Capacity bits were 1237319680 on both sides (16384 × 96). Stress was 0.0 on both sides because cogwheels and shafts have no stress impact in this Create version. A full rescan matched the cached total.

Issue: `docs/issues/003-kinetics.md`.

## Funnel, depot, and chute lookups

`caches.logistics` reuses three lookups Create repeats every tick. Retracted belt funnels reuse `BeltHelper.getSegmentBE`. Depots reuse the behaviour above them, and skip `handleBeltFunnelOutput` when the block above is already known not to be a funnel. Chutes reuse `canDirectlyInsert` while the world epoch and the sign of item motion stay the same. A block-state change drops the maps. Chute entity search still runs on Create's 5-tick cadence. Client levels call Create's own methods.

On a settled world (32 chutes, 32 depots holding iron, 32 retracted belt funnels) a 100-tick window went from 459 ns to 390 ns per funnel tick, 189 ns to 165 ns per depot tick, and 354 ns to 277 ns per chute tick. 12416 of 12928 lookups hit. Depot iron stayed put. The machine snapshot (12 cobblestone targets, one cobblestone and two empty crafter slots) matched.

Issue: `docs/issues/002-logistics.md`.

## Mechanical crafter recipes

`caches.recipe` stores the item `RecipeGridHandler.tryToApplyRecipe` returned for an identical grouped grid, including the `allowRegularCraftingInCrafter` config bit. A hit returns a copy of that stack. Null and empty stay distinct. The map clears when `RecipeManager` applies a new recipe set. Client threads call the original search.

200 searches of two oak planks, after 40 untimed searches: 40.215 ms with the cache off, then 0.627 ms with it on (200/200 hits), then 26.157 ms with it off again. Every result was `minecraft:stick x4`.

A chain-walk skip for a not-ready 3-crafter line was slower than the walk (warm off 1.292 ms, cache on 2.507 ms, 200 calls, inventory unchanged) and is not in this build. Deployer `shouldActivate` caching was 608 ns/call off and 931 ns/call on and is not in this build either.

Issue: `docs/issues/004-machines.md`.
## Train point positions

`caches.trainPosition` reuses the `Vec3` from `TravellingPoint.getPositionWithOffset` while the edge, graph, position bits, offset bits, and flip are unchanged. Signal scouting still travels every tick, and carriage collision still runs at speed 0. Those paths re-establish reservations and contacts. Client threads call the original method.

On one straight 32-block andesite edge, 64 points, measured back to back after a warmup: 30 passes over the points went from 0.224 ms to 0.179 ms, and asking each point 30 times in a row went from 0.174 ms to 0.107 ms. Both cached loops hit 1984/1984, and the position bits matched (`6220146625772257280`).

Issue: `docs/issues/005-trains.md`.

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
- `caches.neighbours` (true), `caches.stress` (true)
- `caches.logistics` (true)
- `caches.recipe` (true)
- `caches.trainPosition` (true)

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

Jar: `build/libs/create_factory_graph-1.1.0-perf-combined.jar`

## Pins

NeoForge 21.1.256, Minecraft 1.21.1, Create `[6.0.5,6.0.11)` compiled against 6.0.10-280, Java 21. License CC0-1.0.
