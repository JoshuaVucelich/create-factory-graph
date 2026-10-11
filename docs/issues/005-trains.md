# 005 Trains: point positions

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


No train graph, signal, or carriage entity was assembled. `trainPosition` was 0 ms/tick in the steady window.

## Change

`getPositionWithOffset` is a pure function of the edge, the graph, the position, the offset, and the flip. The cache returns the same `Vec3` while those match. It does not skip signal scouting or zero-speed collision.

Default is on. Client threads do not touch the map.

## Measurement

64 `TravellingPoint`s on one straight andesite edge of length 32. Each loop is 30 × 64 `getPosition` calls, after 5 warmup loops. Cache off, then on, then off again, in one server tick. Hits include a following bit check (64 extra calls), so 1984 = 1920 timed + 64.

| pattern | off (warm) | on | off again | bits |
| --- | ---: | ---: | ---: | --- |
| 30 passes over the 64 points | 0.227 ms | 0.179 ms | 0.224 ms | 6220146625772257280 both |
| each point 30 times in a row | 0.171 ms | 0.107 ms | 0.174 ms | 6220146625772257280 both |

Parity PASS. An earlier pair of 2.221 ms and 3.320 ms was taken minutes apart in a longer bench and is not this comparison.

## In-game test

A train crossing signals, a junction, and a station, with the cache on and with `/factorygraph disable`. Arrival, reservation, and collision with an entity on the track should match. This cache does not change signal scouting, navigation, or carriage ticking, and those still need that test.
