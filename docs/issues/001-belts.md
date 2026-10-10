# 001 Belt item transport

Origin issues are not available for this repository. This file tracks the measurement.

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


Two belts were moving in that window (2.00 inventory ticks per server tick). Jam backoff skipped 0.

## What was tried

Remember, per segment, whether a funnel, tunnel, crusher, processing behaviour, or transported-item handler is present. Drop the memory on any block change. Locked items and the movement loop still run.

## Measurement

Same belt, items reinserted at the same positions and zero angle / side offset, 200-tick windows.

| | belt inventory |
| --- | --- |
| cache off | 0.015 ms/tick, 402 calls, 7456 ns/call |
| cache on | 0.023 ms/tick, 402 calls, 11451 ns/call |

Hits 2534/2763. The cache was slower. It is not in this build. Jam backoff (1, 2, 4, 8 ticks once a belt is fully stopped) stays as it shipped in 1.0.0.

## Parity

PASS when the tick counts matched. Six cobblestone stacks, identical `beltPosition` bits, identical `sideOffset` bits, identical item and count:

`minecraft:cobblestone x1` at position bits 1091987035, 1093035611, 1094084187, 1095132763, 1096181339, 1097229915 and side-offset bits -1104500053 (last stack -1104500054).

An earlier comparison that failed was a bench bug: the off window had 22 extra ticks of motion (22 × 32/480 blocks). The cache was not moving items differently.

## In-game test

A visual check of a moving belt is still worth doing. Throughput already matched bit for bit in the bench. This build does not change that path beyond 1.0.0 jam backoff.
