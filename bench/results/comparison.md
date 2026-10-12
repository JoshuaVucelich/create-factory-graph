# Factory graph headless benchmark

Pack: NeoForge 21.1.233, Minecraft 1.21.1, Create 6.0.10, Sable 2.0.3, Aeronautics 1.3.0 bundled, no Sodium.
World: 32 shaft lines of 48, 4 moving belts, 1 packed belt, 4 bearings with glued 3×3 casings, 4 sticky pistons (64 poles, still extending), presses, mixers, fans, deployers, funnels, depots, chutes.
Client model: Create 6.0.10 bearing and piston correction (lazy sync every 3 ticks, ServerSpeedProvider = 1). The dedicated server has no client mod, so the client keeps integrating speed every tick.

Jitter is the RMS of the per-tick second difference `p[t] - 2 p[t-1] + p[t-2]` of the client-visible point. Excess jitter subtracts the mean second difference, so a smooth circle sits near zero and a rubber-band does not.
Desync is the RMS distance between that client point and the server point.

| metric | baseline | mod | inert (kill switch) | paused controllers | mod − baseline |
| --- | ---: | ---: | ---: | ---: | ---: |
| **steady MSPT mean** | 1.391 | 1.175 | 1.419 | 1.446 | -0.216 (-15.6%) |
| steady MSPT p99 | 2.577 | 2.317 | 2.918 | 4.022 | -0.261 (-10.1%) |
| steady MSPT stddev | 0.390 | 0.296 | 0.507 | 0.536 | -0.094 (-24.1%) |
| steady kinetic ticks/tick | 1727.7 | 82.3 | 1727.7 | 1723.7 | -1645.467 (-95.2%) |
| steady bearing desync RMS | 0.00000 | 0.00000 | 0.00000 | 0.77997 | +0.000 (+0.0%) |
| steady bearing jitter RMS | 0.01980 | 0.01980 | 0.01980 | 0.27858 | +0.000 (+0.0%) |
| steady bearing jitter excess | 0.01980 | 0.01980 | 0.01980 | 0.27858 | +0.000 (+0.0%) |
| steady bearing step stddev | 0.0000 | 0.0000 | 0.0000 | 4.8000 | +0.000 (+0.0%) |
| steady bearing travel | 2870.4 | 2870.4 | 2870.4 | 1440.0 | +0.000 (+0.0%) |
| steady piston desync RMS | 0.00000 | 0.00000 | 0.00000 | 0.10332 | +0.000 (+0.0%) |
| steady piston jitter excess | 0.00000 | 0.00000 | 0.00000 | 0.01575 | +0.000 (+0.0%) |
| steady piston travel | 18.69 | 18.69 | 18.69 | 9.38 | +0.000 (+0.0%) |
| steady belt desync RMS | 0.01204 | 0.01204 | 0.01204 | 0.01204 | +0.000 (+0.0%) |
| **churn MSPT mean** | 1.306 | 1.309 | 1.430 | 1.325 | +0.003 (+0.2%) |
| churn MSPT p99 | 5.485 | 6.789 | 7.470 | 5.553 | +1.304 (+23.8%) |
| churn MSPT stddev | 0.756 | 1.002 | 1.681 | 0.950 | +0.246 (+32.5%) |
| churn kinetic ticks/tick | 1736.3 | 714.8 | 1736.3 | 1732.3 | -1021.567 (-58.8%) |
| churn bearing desync RMS | 0.00000 | 0.00000 | 0.00000 | 0.82328 | +0.000 (+0.0%) |
| churn bearing jitter RMS | 0.01980 | 0.01980 | 0.01980 | 0.30016 | +0.000 (+0.0%) |
| churn bearing jitter excess | 0.01979 | 0.01979 | 0.01979 | 0.30016 | +0.000 (+0.0%) |
| churn bearing step stddev | 0.0000 | 0.0000 | 0.0000 | 4.7998 | +0.000 (+0.0%) |
| churn bearing travel | 1142.4 | 1142.4 | 1142.4 | 566.4 | +0.000 (+0.0%) |
| churn piston desync RMS | 0.00000 | 0.00000 | 0.00000 | 0.10297 | +0.000 (+0.0%) |
| churn piston jitter excess | 0.00000 | 0.00000 | 0.00000 | 0.01613 | +0.000 (+0.0%) |
| churn piston travel | 7.44 | 7.44 | 7.44 | 3.69 | +0.000 (+0.0%) |
| churn belt desync RMS | 0.01367 | 0.01367 | 0.01367 | 0.01367 | +0.000 (+0.0%) |
| **steady2 MSPT mean** | 1.169 | 0.975 | 1.095 | 1.040 | -0.194 (-16.6%) |
| steady2 MSPT p99 | 2.761 | 2.190 | 2.608 | 2.069 | -0.571 (-20.7%) |
| steady2 MSPT stddev | 0.395 | 0.294 | 0.328 | 0.231 | -0.101 (-25.6%) |
| steady2 kinetic ticks/tick | 1733.5 | 82.5 | 1733.5 | 1729.4 | -1650.933 (-95.2%) |
| steady2 bearing desync RMS | 0.00000 | 0.00000 | 0.00000 | 0.79261 | +0.000 (+0.0%) |
| steady2 bearing jitter RMS | 0.01980 | 0.01980 | 0.01980 | 0.27894 | +0.000 (+0.0%) |
| steady2 bearing jitter excess | 0.01980 | 0.01980 | 0.01980 | 0.27894 | +0.000 (+0.0%) |
| steady2 bearing step stddev | 0.0000 | 0.0000 | 0.0000 | 4.7999 | +0.000 (+0.0%) |
| steady2 bearing travel | 1430.4 | 1430.4 | 1430.4 | 720.0 | +0.000 (+0.0%) |
| steady2 piston desync RMS | 0.00000 | 0.00000 | 0.00000 | 0.10265 | +0.000 (+0.0%) |
| steady2 piston jitter excess | 0.00000 | 0.00000 | 0.00000 | 0.01573 | +0.000 (+0.0%) |
| steady2 piston travel | 9.31 | 9.31 | 9.31 | 4.69 | +0.000 (+0.0%) |
| steady2 belt desync RMS | 0.01221 | 0.01221 | 0.01221 | 0.01221 | +0.000 (+0.0%) |
| **cadence MSPT mean** | 1.098 | 1.049 | 1.082 | 1.035 | -0.049 (-4.5%) |
| cadence MSPT p99 | 2.451 | 2.281 | 2.326 | 2.211 | -0.171 (-7.0%) |
| cadence MSPT stddev | 0.376 | 0.382 | 0.312 | 0.292 | +0.006 (+1.5%) |
| cadence kinetic ticks/tick | 1733.5 | 82.5 | 1733.5 | 1729.5 | -1650.933 (-95.2%) |
| cadence bearing desync RMS | 0.00000 | 0.00000 | 0.00000 | 0.79447 | +0.000 (+0.0%) |
| cadence bearing jitter RMS | 0.01980 | 0.01980 | 0.01980 | 0.28008 | +0.000 (+0.0%) |
| cadence bearing jitter excess | 0.01980 | 0.01980 | 0.01980 | 0.28008 | +0.000 (+0.0%) |
| cadence bearing step stddev | 0.0000 | 0.0000 | 0.0000 | 4.7999 | +0.000 (+0.0%) |
| cadence bearing travel | 1430.4 | 1430.4 | 1430.4 | 710.4 | +0.000 (+0.0%) |
| cadence piston desync RMS | 0.00000 | 0.00000 | 0.00000 | 0.10318 | +0.000 (+0.0%) |
| cadence piston jitter excess | 0.00000 | 0.00000 | 0.00000 | 0.01606 | +0.000 (+0.0%) |
| cadence piston travel | 9.31 | 9.31 | 9.31 | 4.62 | +0.000 (+0.0%) |
| cadence belt desync RMS | 0.01221 | 0.01221 | 0.01221 | 0.01221 | +0.000 (+0.0%) |

## Worlds

- baseline: relays=1565 machines=28 bearingsRunning=4/4 pistonsRunning=4/4 beltsWithItems=5 lineMotorsWithSpeed=40/40 modLoaded=False skip=0
- mod: relays=1565 machines=28 bearingsRunning=4/4 pistonsRunning=4/4 beltsWithItems=5 lineMotorsWithSpeed=40/40 modLoaded=True skip=0
- inert: relays=1565 machines=28 bearingsRunning=4/4 pistonsRunning=4/4 beltsWithItems=5 lineMotorsWithSpeed=40/40 modLoaded=True skip=0
- paused: relays=1565 machines=28 bearingsRunning=4/4 pistonsRunning=4/4 beltsWithItems=5 lineMotorsWithSpeed=40/40 modLoaded=False skip=2

## Mod bookkeeping

```
{
  "graphs": 53,
  "nodes": 1718,
  "dormant": 1640,
  "sleeps": 3188,
  "wakes": 1548,
  "rebuilds": 207,
  "skippedBeTicks": 1359692,
  "beltBackoffSkips": 3048,
  "validations": 22665,
  "parentedRebuilds": 49,
  "groundRebuilds": 158,
  "lastBuildNanosSum": 2685564,
  "failOpens": 0
}
```

Inert kill-switch stats:

```
{
  "graphs": 0,
  "nodes": 0,
  "dormant": 0,
  "sleeps": 0,
  "wakes": 0,
  "rebuilds": 0,
  "skippedBeTicks": 0,
  "beltBackoffSkips": 0,
  "validations": 0,
  "parentedRebuilds": 0,
  "groundRebuilds": 0,
  "lastBuildNanosSum": 0,
  "failOpens": 0
}
```
