# Failure scenarios

Hardening pass for the factory graph on Joshua's pack: NeoForge 21.1.233, Create 6.0.10, Sable 2.0.3, Aeronautics 1.3.0. The dev workspace still compiles against NeoForge 21.1.256 and Create 6.0.10-280. This jar does not link Sable or Aeronautics. The ship mod drives `FactoryCadence` by reflection. `displayTest` stays `IGNORE_ALL_VERSION`.

The airship loss happened when ship-hull froze physics on part of a group. The graph used to parent a whole kinetic network from the first few members and then skip every parented block entity, including bearings and item handlers. A later catch-up ran those skipped ticks back to back. Either mistake can pull a contraption off a ship or move an item twice.

`/factorygraph status` prints the kill switch, each cache, and the counters below. `/factorygraph kill` is the runtime switch. `safety.killSwitch` in `config/create_factory_graph-common.toml` is the same switch on the next launch.

Cadence debt, dormant flags, and every cache live on the block entity or in static maps. None of them are written in `write` or `saveAdditional`. A crash or restart resumes with Create's own ticking and cold caches.

## 1. Partial ship-group freeze

**What goes wrong.** Aeronautics can park or freeze one member of a docked group while the rest keeps flying. A shaft, bearing, or gantry line often crosses that boundary. The old parent check sampled four members and, if they agreed, put every block entity on that ship gate. Relays, bearings, deployers, and belts then all followed `CHEAP`. The flying part stopped updating, or the frozen part later catch-up-burst, and the ship came apart.

**What we do now.** `Safety.choose` parents a network only when every sample names the same ship. A ground block or a second ship key refuses the parent. Samples include both ends of the member list, not only a prefix. Networks larger than `safety.maxParentedMembers` (8192) are not parented, so a megabase is not walked into a ship gate. On every `setShipMode`, parented relays re-check the locator once. A relay that is no longer on that ship is ungated. The counter is `split-ungates`. Refusals increment `parent-refusals`.

Only decorative relays (exact class, same set the dormancy path already uses) receive a gate. Bearings, pistons, pulleys, elevators, deployers, mechanical arms, belt controllers, funnels, depots, and chutes do not.

## 2. Throttled kinetics and stress

**What goes wrong.** Skipping a motor or a machine while a neighbour still ticks leaves speed and stress on two different clocks. Overstress can flicker, or a goggle reading can stick after the ship wakes. Replaying a speed change inside one server tick applies several RPM steps at once.

**What we do now.** Sources and machines are not deferred, so Create still propagates speed and stress on its own tick. The stress cache still invalidates when a member is removed, added silently, or has its impact replaced. Chunk unload no longer wipes every network on the server. Create's `KineticBlockEntity.remove` already drops the unloaded member and that invalidates the one network. A full rescan of every factory on a chunk boundary was an O(n) spike and is gone.

If the stress hook throws, the network total is invalidated and Create's own `calculateStress` / `calculateCapacity` runs. `fail-open` counts that.

## 3. Belts and deployers

**What goes wrong.** A skipped belt tick freezes items. A catch-up of several belt ticks in one server tick can push the same stack into a funnel more than once, or dump it off the end. A deployer catch-up can use or place the held stack once per replayed tick. Waking a jammed belt by inserting is safe only if the skipped ticks did not also move items.

**What we do now.** Belt controllers, deployers, and other item handlers are not deferred. Debt they already accumulated is zeroed and counted in `unsafe-debt-forfeited`. It is not replayed. Jam backoff is unchanged: a belt that has not moved, and has nothing queued, locked, or riding, skips its inventory tick and does not move, drop, or eject. An insert or a kill switch resumes the next real tick. Belt segment kinetic ticks can still go dormant after they settle. The controller, which owns the inventory, stays on the ticker.

## 4. Trains and contraptions crossing a throttled area

**What goes wrong.** A bearing that stops while the ship or the contraption entity keeps moving leaves the assembly in two places. A train point cached from before a track edit returns the old position. A contraption shape recorded before a block change collides with the wrong box. Carriage portal shapes were already left uncached.

**What we do now.** Contraption controllers and train entities are not gated. Actor ticks are unchanged. The train position cache drops its map when the world epoch changes (a real block-state edit) and when the point's edge, position, offset, or flip changes. The contraption shape cache still requires the same contraption, the same quantized scan, the same block count, and the same world epoch. `AbstractContraptionEntity.setBlock` still drops that contraption's entry. Chunk load and unload evict neighbour lists and logistics lookups for that chunk so a reloaded belt or funnel is not served the previous block entity.

## 5. Crash or restart mid-throttle

**What goes wrong.** Persisting cadence debt or a dormant-ticker flag would replay or skip work after the world loads, against block entities Create has already restored at full rate. Persisting a cache would serve neighbours and recipes from the crashed tick.

**What we do now.** `Safety.PERSIST_CADENCE_OR_CACHE` is false and is unit-tested. The debt and ship-gate fields are mixin fields with no NBT write. Dormant tickers are only an in-memory flag plus a removed ticker. On load, the chunk rebuilds tickers and the flag is gone. Caches are static maps. Server stop clears gates. Nothing in this pass adds a saved tag.

## 6. Parking and contraptions on ships

**What goes wrong.** Freezing physics on one ship in a group and then treating the group as a single `CHEAP` gate pauses the contraption that is still supposed to fly, or burst-ticks it when the freeze ends. Continuing to throttle item handlers on the parked ship and then repaying them duplicates items the moment the ship is woken.

**What we do now.** A mode announcement bumps a ship epoch and marks graphs parented to that key dirty, so the next rebuild re-samples. Relays on the announced ship may still follow `SLOW`, `SLEEP`, or `BURST`. Contraptions on that ship keep Create's tick the whole time, including while the hull is `CHEAP`, so they stay with the ship instead of catching up later. If a parked contraption must also stop, stop it from the ship mod or flip `/factorygraph kill`. This mod will not skip it.

## 7. Lag spikes

**What goes wrong.** `BURST`, or waking a ship with thousands of ticks of debt, runs that many `SmartBlockEntity.tick` calls inside one server tick. One catch-up that throws used to drop the debt after a partial replay and could still have moved items.

**What we do now.** Only relays are repaid, and `safety.maxCatchUpPerTick` (64) is a server-wide budget reset at the start of each server tick. Debt above the budget stays for a later tick. It is not dumped as a burst. A catch-up that throws drops the rest of that relay's debt, counts a fail-open, and does not cancel the original tick.

## 8. Huge factories

**What goes wrong.** Parenting used to call the ship locator in member-map order and then write a gate on every member and every logistics node. On a large network that is an O(n) locator walk per rebuild, and a rebuild storm when any ship in the world changes mode.

**What we do now.** At most 16 members are located, spread from the first to the last. Networks over 8192 members are refused and stay on Create's tick. Ship-mode changes rebuild only graphs already parented to that key. Chunk unload evicts that chunk's logistics and neighbour entries instead of clearing every stress total on the server.

## 9. Mixin mismatch

**What goes wrong.** The mixin config used to be required. A Create build whose private method moved crashed the dedicated server at startup. A hook that threw while cancelling a tick skipped the tick or took the server down with it.

**What we do now.** `create_factory_graph.mixins.json` is `required: false`. If the targets do not match, Mixin logs the failure and Create runs stock. The ticker, belt backoff, stress totals, contraption replay, recipe lookup, depot funnel check, and chute insert check catch their own failures, leave Create's method running, and increment `fail-open`. The kill switch forces the same outcome on purpose.

## Kill switch

`safety.killSwitch = true`, or `/factorygraph kill` (op level 2):

- graph mode, ship cadence, jam backoff, and every cache report off
- dormant relays are put back on the ticker
- gates are cleared and debt is dropped, not replayed
- neighbour, stress, logistics, recipe, train-position, and contraption-shape caches are cleared
- no chunk is written

`/factorygraph unkill` clears the runtime override. If the config file still says `killSwitch = true`, the switch stays on until that line is false. `/factorygraph disable` still wakes dormant relays. It does not by itself clear the kill switch.

## Tests

`src/test/java/.../SafetyTest.java` covers the pure rules: unanimous ship parents, a split or a ground mix refuses, an oversized network refuses, samples include both ends, only relays defer, machine and belt debt replays zero ticks, a failed hook does not skip the original tick, and cadence state is not persisted. `./gradlew test` runs them. They do not start a Minecraft server. An in-game check still has to put a bearing and a belt on a ship, freeze one member of the group, and confirm the bearing keeps turning and the belt items do not jump.
