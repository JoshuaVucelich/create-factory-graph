# Create Factory Graph

Server-side performance mod for Create on NeoForge 1.21.1. It keeps a graph of each kinetic network and uses that graph to skip tick work Create would otherwise do every tick.

It ships inert. `safety.killSwitch` defaults to true. The headless bench below did not show a jitter-free MSPT gain on the Huge Josh Land pack, so that pack should not run this mod. Set the switch false only if you are measuring it yourself.

Mod id `create_factory_graph`. License [CC0 1.0](LICENSE).

One jar covers Create 6.0.5 through 6.0.10. The dependency range is `[6.0.5,6.0.11)`. The Gradle build compiles against Create 6.0.10.

## What it does

**Factory graph.** On the first kinetic network change (attach, detach, source or network change, block place or break) the network is rebuilt as a graph. Nodes are kinetic block entities: source, relay, belt controller, belt segment, and machine, plus logistics endpoints at belt ends (chute, depot, funnel, and similar). Edges are Create's own kinetic connections, plus belt-to-endpoint item links. Stored values include speed, stress impact, capacity, and belt item counts. Rebuilds are event-driven and capped per tick.

**Dormant relays.** Settled decorative relays (shaft, cogwheel, large cogwheel, encased shaft or cogwheel, chain drive, gearbox) and non-controller belt segments come off the block-entity ticker. Each level tick the graph advances instead. It runs Create's own source validation for dormant nodes at Create's `kineticValidationFrequency`, spread across the 60-tick window. A kinetic state change (speed, source, network, overstress flicker, rotation particles, contraption shape, block state, or removal) puts the node back on the ticker. Matching is exact class and exact block-entity type, so addon subclasses and anything with its own tick behavior stay awake.

**Belt cadence.** Belt controllers still tick every tick, so item movement stays the same. A jammed belt (nothing moved for 40 ticks, no processing-locked items, no riders, nothing queued) backs its inventory tick off from 1 to 2 to 4 to 8 ticks, then returns to full rate on any insert or change. A skipped tick simply does not run. This mod does not move, drop, or eject items.

**Stress.** Create already recalculates stress on network events. This mod does not add a per-tick stress pass, and it rebuilds graphs only when the network changes.

**Ship cadence.** `com.vws.createfactorygraph.api.FactoryCadence` (API version 2) lets a ship mod, such as Aeronautics via `aeronautics_ship_hull`, register a locator and a hull mode by reflection. No compile dependency on the ship mod. A kinetic network is parented only when sampled members agree on one ship and the network is under `safety.maxParentedMembers`. A split between a frozen sub-ship and a flying one is left on Create's own tick. Only decorative relays are deferred. Belts, deployers, bearings, and other machines are never throttled, and any debt they already held is dropped instead of replayed. While the ship is `CHEAP`, the default policy is `SLOW`: each parented relay ticks once per hull period. `SLEEP` defers every relay tick. `BURST` runs the relay's period back to back, capped by `safety.maxCatchUpPerTick` for the whole server. With no ship mod registered, nothing is parented. `safety.killSwitch` or `/factorygraph kill` turns the graph, the cadence, and every cache off. See `docs/FAILURE_SCENARIOS.md`.

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

## Contraption collision shapes

`caches.contraptionShapes` replays the boxes `ContraptionCollider.getPotentiallyCollidedShapes` recorded for the same contraption, the same quantized scan range, and the same block count, until a block changes or `AbstractContraptionEntity.setBlock` runs. The scan range is Create's own inflate (0.5, then the horizontal and vertical factor). A miss still walks the shapes and feeds the original consumer. Carriage contraptions that override `isHiddenInPortal` stay uncached. Actor ticks are unchanged. Client threads run the original scan.

The bench assembled a bearing contraption of one andesite casing (`assemble` returned true, 1 block) and ran `collideEntities` (121 calls, 0.035 ms/tick with the cache off). `getPotentiallyCollidedShapes` was not entered, so the shape cache recorded 0 hits and 0 misses. The later drop in collide time is a different window, not this cache.

Issue: `docs/issues/006-contraptions.md`.

## Belt segment lookups

Item transport still uses the jam backoff already in this mod. A separate cache of segments with no funnel, tunnel, crusher, or processor was measured and left out. On one 24-block belt with 6 cobblestone, positions reset so both 200-tick windows saw the same motion, the inventory tick went from 7456 ns/call (0.015 ms/tick) to 11451 ns/call (0.023 ms/tick). 2534 of 2763 segment checks hit, and the bookkeeping still cost more than the block-state reads. Item positions and side offsets matched bit for bit (`beltPosition` and `sideOffset` float bits, item, and count).

Issue: `docs/issues/001-belts.md`.

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
- `caches.contraptionShapes` (true)
- `safety.killSwitch` (true). The jar does nothing until this is false. `/factorygraph kill` does the same until restart and drops in-memory debt without replaying it

## Pack benchmark

`bench/run-bench.sh` runs a dedicated server on NeoForge 21.1.233, Create 6.0.10, Sable 2.0.3, and Aeronautics 1.3.0 bundled, with no Sodium. The world is 32 shaft lines of 48, four moving belts, one packed belt, four glued bearing contraptions, and four sticky pistons that are still extending. Numbers are in `bench/results/comparison.md`.

Steady window, mean MSPT / p99 / tick stddev / kinetic block-entity ticks per server tick:

| run | MSPT mean | p99 | stddev | kinetic ticks |
| --- | ---: | ---: | ---: | ---: |
| baseline (no mod) | 1.391 | 2.577 | 0.390 | 1727.7 |
| mod (kill switch off) | 1.175 | 2.317 | 0.296 | 82.3 |
| inert (kill switch on) | 1.419 | 2.918 | 0.507 | 1727.7 |
| paused controllers | 1.446 | 4.022 | 0.536 | 1723.7 |

The mod cut kinetic ticks by 95% (1,645 fewer per tick, 1,640 relays dormant) and the steady mean moved by 0.22 ms. Churn, where motors change RPM, was 1.306 ms vs 1.309 ms, and its p99 got worse (5.485 to 6.789). An earlier run of the same factory moved the steady mean by only 0.04 ms with the same tick cut. That 0.2 ms does not repeat, and it is smaller than the tick stddev. Relay ticks were never the cost.

Contraption sync, same steady window. Bearings traveled 2870 degrees. Pistons traveled 18.69 blocks. Desync is the RMS gap between the server position and a client that keeps integrating speed. Jitter excess is the RMS of the second difference of that client point after subtracting the mean, so a smooth circle sits near 0.020.

| run | bearing desync | bearing jitter excess | piston desync | piston travel |
| --- | ---: | ---: | ---: | ---: |
| baseline | 0.000 | 0.0198 | 0.000 | 18.69 |
| mod | 0.000 | 0.0198 | 0.000 | 18.69 |
| paused (skip every other controller tick) | 0.780 | 0.279 | 0.103 | 9.38 |

Create 6.0.10 does not copy a bearing angle or a piston offset onto the client. The client integrates speed every tick and only bleeds the packet error (`clientAngleDiff /= 2`, `clientOffsetDiff *= 0.75`). Skipping those server ticks desyncs the two sides. This build does not skip them: the mod matches the baseline. The paused column is what rubber-banding looks like when something does.

Belt item desync stayed at 0.012 on every run. Jam backoff skipped inventory ticks and did not pull items off the client.

Drop this mod from the pack. Turning the kill switch off does not buy a measured, jitter-free gain, and any later change that skips bearing or piston ticks will rubber-band.

Issue: `docs/issues/007-pack-bench.md`.
- `safety.maxCatchUpPerTick` (64), `safety.maxParentedMembers` (8192)

`/factorygraph status` reports the kill switch, each cache, and the fail-open counters. See `docs/FAILURE_SCENARIOS.md`.

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

Jar: `build/libs/create_factory_graph-1.1.1-hardening.jar`

## Pins

NeoForge 21.1.256, Minecraft 1.21.1, Create `[6.0.5,6.0.11)` compiled against 6.0.10-280, Java 21. License CC0-1.0.
