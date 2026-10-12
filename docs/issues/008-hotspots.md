# 008 Hotspot profile: no MSPT gain beyond noise

Origin issues are not available for this repository (`origin` has no issue command, and GitHub returns 404). This file tracks the change.

## Question

Relay ticks are too cheap to explain a server tick. Where does Create's time go on the pack bench, and can the factory graph cut a real hotspot without skipping bearing or piston ticks?

## Sampler

async-profiler 4.2 on a steady baseline tick (about 1.4 ms of work inside a 50 ms budget) mostly landed in `waitUntilNextTick`. The only Create stack with a visible share was `ContraptionCollider.collideEntities` → `Level.getEntities` → Sable `SubLevelInclusiveLevelEntityGetter` → `ClassInstanceMultiMap.find`. Eighty-three samples cannot rank methods that each cost tens of microseconds.

`benchprobe` now times the methods directly when started with `-Dbench.sections=true`. The probes are off otherwise. Steady window of the baseline world (`bench/results/hotspot/profile-baseline.json`):

| section | ms/tick | calls/tick |
| --- | ---: | ---: |
| block entity tick phase | 0.642 | 1.1 |
| entity tick | 0.183 | 14.1 |
| `KineticBlockEntity.tick` | 0.170 | 1727.7 |
| Create world tick (`CommonEvents.onServerWorldTick`) | 0.082 | 3.0 |
| contraption `collideEntities` | 0.058 | 8.0 |
| belt inventory | 0.036 | 5.0 |
| contraption entity tick | 0.032 | 8.0 |
| encased fan | 0.014 | 4.0 |
| chute | 0.011 | 4.0 |
| depot | 0.009 | 4.0 |
| funnel | 0.005 | 4.0 |
| belt funnel check | 0.004 | 27.1 |
| railways | 0.004 | 3.0 |
| press | 0.003 | 4.0 |
| mixer | 0.003 | 4.0 |
| deployer | 0.002 | 4.0 |
| fan entity search | 0.002 | 0.7 |
| logistics | 0.001 | 3.0 |

Fluid transport and fluid networks recorded zero calls. This world has no pipes.

A second pair split the block-entity phase (`split-baseline.json`, `split-mod.json`). Baseline: ticker body 0.418 ms across 1,743 block entities, chunk `shouldTickBlocksAt` 0.082 ms, bearing tick 0.006 ms, piston actuator tick 0.032 ms. With the mod and the kill switch off, those drop to 94 ticking block entities, ticker body 0.193 ms, kinetic ticks 82. The block-entity phase falls from 0.653 ms to 0.227 ms. Bearing and piston controller ticks still run.

`FactoryGraphManager.endTick` on that mod run was 0.037 ms per level advance, of which dormant source validation was 0.009 ms. The graph is not the tax. The relay ticker list is the only Create cost larger than a tenth of a millisecond, and this mod already takes those relays off the list. Everything else the brief named (belts, funnels, deployers, contraption actors, items, fluids) is under 0.06 ms on this factory.

## Repeated runs

Four baseline runs and four mod runs, sections off, kill switch false, same factory, 300-tick steady window. Raw JSON is in `bench/results/hotspot/`. The section mixin still wraps `shouldTickBlocksAt`, so the baseline pays one extra call per relay that the mod has already removed. That biases the comparison toward the mod.

| run | MSPT mean | p99 | bearing desync | bearing jitter excess | piston desync | belt desync |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| baseline 1 | 1.618 | 3.668 | 0 | 0.0198 | 0 | 0.012 |
| baseline 2 | 1.835 | 3.164 | 0 | 0.0198 | 0 | 0.012 |
| baseline 3 | 1.738 | 5.470 | 0 | 0.0198 | 0 | 0.012 |
| baseline 4 | 1.634 | 3.890 | 0 | 0.0198 | 0 | 0.012 |
| mod 1 | 1.619 | 3.465 | 0 | 0.0198 | 0 | 0.012 |
| mod 2 | 1.684 | 3.257 | 0 | 0.0198 | 0 | 0.012 |
| mod 3 | 1.439 | 2.692 | 0 | 0.0198 | 0 | 0.012 |
| mod 4 | 1.372 | 2.326 | 0 | 0.0198 | 0 | 0.012 |

Welch test on the four run means, mod minus baseline. Negative would be a faster mod.

| metric | baseline mean | mod mean | difference | 95% CI | two-sided p |
| --- | ---: | ---: | ---: | --- | ---: |
| MSPT mean | 1.706 | 1.528 | -0.178 ms | [-0.403, +0.047] | 0.099 |
| MSPT p99 | 4.048 | 2.935 | -1.113 ms | [-2.603, +0.378] | 0.110 |

Both intervals include zero. Bearing travel was 2870.4 degrees and piston travel was 18.69 blocks on every run. Desync and jitter match the baseline.

## Conclusion

There is no change here that passes the bar: mean and p99 both better than baseline beyond noise, with desync and jitter left at the baseline. Skipping bearing or piston ticks would move the client, which integrates those speeds itself, and the paused column in `docs/issues/007-pack-bench.md` already measured that rubber-band. The pack should keep the kill switch on, or drop the mod.
