# 003 Kinetic neighbour lists and incremental stress

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

- Neighbour lists from `RotationPropagator.getConnectedNeighbours` are copied and reused until `WorldEpoch` changes or a neighbour is removed. `WorldEpoch` advances when a server chunk's `setBlockState` returns a different state.
- Stress and capacity stay at the last full `calculateStress` / `calculateCapacity` result and move by the same `base * abs(speed)` term those methods apply.
- `remove` invalidates. `removeSource` sets speed to 0 before `KineticNetwork.remove`, so subtracting at remove time would keep the old contribution.
- `addSilently`, `initFromTE`, and `clearKineticInformation` invalidate.
- Chunk unload clears every total.
- Client threads do not read or write the maps.

## Measurement

Same world, caches off then on. 24 lines × 16 cogwheels, creative motors pulsed from 32 RPM to 96 RPM.

| | neighbour scan | stress |
| --- | --- | --- |
| off | 5.018 ms (792 calls, 6335 ns/call) | 0.200 ms (72 calls, 2771 ns/call) |
| on | 3.243 ms (792 calls, 4094 ns/call) | 0.151 ms (72 calls, 2093 ns/call) |
| hits | 384/792 | 24/72 |

Settled window after the pulse, 408 neighbour calls: 1568 ns/call off, 333 ns/call on, 408/408 hits.

## Parity

Capacity bits 1237319680 both sides, which is 1,572,864 = 16384 × 96. Stress 0.0 both sides. Cogwheels and shafts contribute no stress in Create 6.0.10, so this bench does not show a non-zero stress sum. `StressCache.audit` reported MATCH (full rescan equals the value stored after that rescan).

## In-game test

Goggle a network that has stress impact (mixer, crusher, fan, or deployer). Change the speed with the cache on and with `/factorygraph disable`. The stress and capacity numbers should match, including overstress, which uses theoretical speed and does not change the stress sum.
