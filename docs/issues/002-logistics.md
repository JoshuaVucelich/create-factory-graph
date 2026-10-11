# 002 Funnels, depots, and chutes

Origin issues are not available for this repository. This file tracks the change.

## Profiling baseline

Steady state, caches off, 121 server ticks after the scripted world settled (Create 6.0.10-280, headless dedicated server, AreaProfiler). Average server tick 2.575 ms. Kinetic block entities 56.0/tick, 0.116 ms/tick (2077 ns each). Belt inventory ticks 2.00/tick.

| area | ms/tick | calls | ns/call |
| --- | ---: | ---: | ---: |
| belt inventory | 0.029 | 242 | 14410 |
| funnel | 0.043 | 3872 | 1325 |
| depot | 0.021 | 3872 | 640 |
| chute | 0.033 | 3872 | 1034 |
| deployer | 0.018 | 1452 | 1486 |
| mechanical crafter | 0.021 | 363 | 7067 |
| contraption collide | 0.035 | 121 | 34625 |
| neighbour scan, stress, train position, contraption shapes | ~0 | 0 | — |

JFR at 10 ms sampled 10 frames in that window, all chunk and block housekeeping. It does not rank Create methods on a 2.6 ms tick. An earlier recording taken during worldgen was aquifer and noise generation. AreaProfiler is the measurement.


## Change

Memoize lookups Create already does. Misses call Create's own methods. Nothing is extracted, inserted, or dropped from these caches.

- Funnel `determineCurrentMode` reuses `BeltHelper.getSegmentBE` until a block changes.
- Depot `tick` reuses `BlockEntityBehaviour.get`. `handleBeltFunnelOutput` returns false immediately when the block above is already known to have no funnel facing. That method returns false with no item movement in that case.
- Chute `canDirectlyInsert` reuses its boolean while the world epoch matches and the sign of `getItemMotion()` matches. `findEntities` stays on its existing cadence.

## Measurement

Same world, 100-tick windows, caches warmed on the second window. 32 chutes, 32 depots, 32 retracted andesite belt funnels.

| | off | on |
| --- | ---: | ---: |
| funnel | 0.015 ms/tick, 459 ns/call | 0.013 ms/tick, 390 ns/call |
| depot | 0.006 ms/tick, 189 ns/call | 0.005 ms/tick, 165 ns/call |
| chute | 0.011 ms/tick, 354 ns/call | 0.009 ms/tick, 277 ns/call |

Hits 12416/12928 (96%).

## Parity

PASS. Depot contents stayed on the depots. The surrounding machine snapshot was identical: 12 cobblestone blocks in front of the deployers, mechanical crafter slots `cobblestone x1`, empty, empty.

## In-game test

A belt feeding a funnel into a depot, and a chute column with air flow on and off. Item counts at the depot and the chute output should match a server with `caches.logistics` set false.
