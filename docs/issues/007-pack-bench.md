# 007 Pack bench: no jitter-free gain

Origin issues are not available for this repository (`origin` has no issue command, and GitHub returns 404). This file tracks the change.

## What went wrong

On Huge Josh Land (NeoForge 21.1.233, Minecraft 1.21.1, Create 6.0.10, Sable 2.0.3, Aeronautics 1.3.0, no Sodium) this mod jittered contraptions and did not make the server faster.

Create 6.0.10 does not copy a bearing angle or a piston offset onto the client. The client integrates speed every tick and bleeds the packet error (`clientAngleDiff /= 2` on bearings, `clientOffsetDiff *= 0.75` on pistons). A server that skips those ticks leaves the client ahead, and the next sync pulls it back. That is the rubber-band.

An older graph parented a whole kinetic network and deferred every block entity on it, including bearings. This 1.1.1 build does not do that. Decorative relays can still go dormant, and belt jam backoff can skip an inventory tick.

## Measurement

`bench/run-bench.sh` runs the dedicated server for that pack. The world is 32 shaft lines of 48, four moving belts, one packed belt, four glued bearings, and four sticky pistons that are still extending. `bench/results/comparison.md` is the table.

Steady window:

| run | MSPT mean | p99 | stddev | kinetic ticks | bearing desync | piston desync | piston travel |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| baseline | 1.391 | 2.577 | 0.390 | 1727.7 | 0.000 | 0.000 | 18.69 |
| mod | 1.175 | 2.317 | 0.296 | 82.3 | 0.000 | 0.000 | 18.69 |
| inert | 1.419 | 2.918 | 0.507 | 1727.7 | 0.000 | 0.000 | 18.69 |
| paused controllers | 1.446 | 4.022 | 0.536 | 1723.7 | 0.780 | 0.103 | 9.38 |

The mod cut kinetic ticks by 95% (1,640 relays dormant, about 1.36 million skipped block-entity ticks in the run). The steady mean moved 0.22 ms. Churn was flat (1.306 vs 1.309) and its p99 got worse. An earlier run of the same factory moved the steady mean by 0.04 ms with the same tick cut. Relay ticks are not the cost, and the graph's bookkeeping spends what little they were worth.

Skipping every other bearing and piston tick (the paused column, not this mod) halved travel and raised bearing jitter excess from 0.020 to 0.279. This mod matches the baseline on both contraptions. Belt item desync stayed at 0.012 on every run.

## Change

`safety.killSwitch` defaults to true. A jar left on the server is inert until someone sets it false. The pack should drop the mod. Do not skip bearing or piston ticks to chase the kinetic-tick count.
