# 004 Deployers and mechanical crafters

Origin issues are not available for this repository. This file tracks the change.

## Profiling baseline

Steady state, caches off, 121 server ticks after the scripted world settled (Create 6.0.10-280, headless dedicated server). Average server tick 2.575 ms. Deployer ticks 0.018 ms/tick (1452 calls, 1486 ns). Mechanical crafters 0.021 ms/tick (363 calls, 7067 ns) at 0 RPM, so that time is mostly kinetic ticking. JFR at 10 ms did not rank these methods.

## Recipe search (shipped)

`tryToApplyRecipe` stores Create's own result under a key of grid coordinates, item components, counts, and `allowRegularCraftingInCrafter`. A later identical grid gets a copy. Recipe reload clears the map. Client threads call the original method.

200 searches of two oak planks, headless dedicated server, recipes loaded. One untimed call filled the cache before the on-loop.

| loop | time | result |
| --- | ---: | --- |
| cache off, after 40 untimed searches | 40.215 ms | minecraft:stick x4 |
| cache on | 0.627 ms (200/200 hits) | minecraft:stick x4 |
| cache off again | 26.157 ms | minecraft:stick x4 |

Parity PASS. The second off-loop is the warmer of the two uncached timings, and the cached loop is still about 40× shorter.

## Chain walks (not shipped)

Three mechanical crafters in a line, speed set to 32, one oak plank in the root, the walk returning null (not ready). 200 calls of `checkCompletedRecipe`.

| | time | root count |
| --- | ---: | ---: |
| cache off, after 20 untimed calls | 2.428 ms | 1 |
| cache on | 2.507 ms (200/200 hits) | 1 |
| cache off again | 1.292 ms | 1 |

The fingerprint check cost more than the three-block walk. The skip is not in this build. The plank stayed in the slot, so `begin()` did not run.

## Deployer activate checks (not shipped)

`DeployerHandler.shouldActivate` with the same held stack, target, and facing: 608 ns/call with the cache off, 931 ns/call with it on (69/117 hits) over a 100-tick window. Twelve cobblestone targets were unchanged. That redirect is not in this build.

## In-game test

A powered mechanical crafter turning oak planks into sticks, with `caches.recipe` on and with `/factorygraph disable`. Output item and count should match, including after a recipe reload.
