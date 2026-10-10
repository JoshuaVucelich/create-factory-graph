# 006 Contraption collision shapes

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

`getPotentiallyCollidedShapes` records every box it passes to Create's consumer. A later call with the same contraption, the same quantized local scan, the same `getBlocks().size()`, and the same world epoch replays those boxes and skips the block walk. `setBlock` drops that contraption's entry. A block-state change drops it via the world epoch. Subclasses that override `isHiddenInPortal` (carriage contraptions in a portal) are not cached. Actor `tick` is not skipped.

## Measurement

Bearing contraption, one andesite casing, assemble true. `collideEntities` ran. `getPotentiallyCollidedShapes` did not: shape hits 0, shape calls 0 in every window. There is no before/after number for the shape walk. Collide time in the steady window was 0.035 ms/tick (121 calls, 34625 ns/call) with caches off. Later windows were shorter and are not attributed to this cache.

## Parity

No shape output to compare, because the scan never ran. The replay path returns the boxes the original scan just produced, so a hit is the same consumer calls as the miss that filled it.

## In-game test

A moving bearing, elevator, or piston contraption with an entity overlapping the collision box, cache on and off. The entity should be pushed the same way. A carriage inside a portal must still collide; that path is uncached on purpose.
