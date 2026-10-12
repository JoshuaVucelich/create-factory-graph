#!/usr/bin/env python3
"""Compare headless bench JSON from baseline, mod, inert, and paused runs."""
import json
import sys


def load(path):
    with open(path) as f:
        return json.load(f)


def fmt(n, digits=3):
    if n is None:
        return "—"
    return f"{n:.{digits}f}"


def delta(mod, base):
    if base is None or mod is None:
        return "—"
    d = mod - base
    pct = (d / base * 100.0) if base else 0.0
    return f"{d:+.3f} ({pct:+.1f}%)"


def row(metric, base, mod, inert=None, paused=None, digits=3):
    return (
        f"| {metric} | {fmt(base, digits)} | {fmt(mod, digits)} | {fmt(inert, digits)} | "
        f"{fmt(paused, digits)} | {delta(mod, base)} |"
    )


def main(argv):
    base_p, mod_p, inert_p, paused_p, md_p, json_p = argv[1:7]
    base, mod, inert, paused = load(base_p), load(mod_p), load(inert_p), load(paused_p)
    windows = ["steady", "churn", "steady2", "cadence"]
    lines = []
    lines.append("# Factory graph headless benchmark")
    lines.append("")
    lines.append("Pack: NeoForge 21.1.233, Minecraft 1.21.1, Create 6.0.10, Sable 2.0.3, Aeronautics 1.3.0 bundled, no Sodium.")
    lines.append("World: 32 shaft lines of 48, 4 moving belts, 1 packed belt, 4 bearings with glued 3×3 casings, 4 sticky pistons (64 poles, still extending), presses, mixers, fans, deployers, funnels, depots, chutes.")
    lines.append("Client model: Create 6.0.10 bearing and piston correction (lazy sync every 3 ticks, ServerSpeedProvider = 1). The dedicated server has no client mod, so the client keeps integrating speed every tick.")
    lines.append("")
    lines.append("Jitter is the RMS of the per-tick second difference `p[t] - 2 p[t-1] + p[t-2]` of the client-visible point. Excess jitter subtracts the mean second difference, so a smooth circle sits near zero and a rubber-band does not.")
    lines.append("Desync is the RMS distance between that client point and the server point.")
    lines.append("")
    lines.append("| metric | baseline | mod | inert (kill switch) | paused controllers | mod − baseline |")
    lines.append("| --- | ---: | ---: | ---: | ---: | ---: |")

    summary = {"windows": {}}
    for w in windows:
        b, m, i, p = (d["windows"][w] for d in (base, mod, inert, paused))
        summary["windows"][w] = {"baseline": b, "mod": m, "inert": i, "paused": p}
        lines.append(f"| **{w} MSPT mean** | {fmt(b['msptMean'])} | {fmt(m['msptMean'])} | {fmt(i['msptMean'])} | {fmt(p['msptMean'])} | {delta(m['msptMean'], b['msptMean'])} |")
        lines.append(f"| {w} MSPT p99 | {fmt(b['msptP99'])} | {fmt(m['msptP99'])} | {fmt(i['msptP99'])} | {fmt(p['msptP99'])} | {delta(m['msptP99'], b['msptP99'])} |")
        lines.append(f"| {w} MSPT stddev | {fmt(b['msptStddev'])} | {fmt(m['msptStddev'])} | {fmt(i['msptStddev'])} | {fmt(p['msptStddev'])} | {delta(m['msptStddev'], b['msptStddev'])} |")
        lines.append(f"| {w} kinetic ticks/tick | {fmt(b['kineticTicksPerTick'], 1)} | {fmt(m['kineticTicksPerTick'], 1)} | {fmt(i['kineticTicksPerTick'], 1)} | {fmt(p['kineticTicksPerTick'], 1)} | {delta(m['kineticTicksPerTick'], b['kineticTicksPerTick'])} |")
        bb, mb, ib, pb = b["bearings"], m["bearings"], i["bearings"], p["bearings"]
        lines.append(f"| {w} bearing desync RMS | {fmt(bb['desyncRms'], 5)} | {fmt(mb['desyncRms'], 5)} | {fmt(ib['desyncRms'], 5)} | {fmt(pb['desyncRms'], 5)} | {delta(mb['desyncRms'], bb['desyncRms'])} |")
        lines.append(f"| {w} bearing jitter RMS | {fmt(bb['jitterRms'], 5)} | {fmt(mb['jitterRms'], 5)} | {fmt(ib['jitterRms'], 5)} | {fmt(pb['jitterRms'], 5)} | {delta(mb['jitterRms'], bb['jitterRms'])} |")
        lines.append(f"| {w} bearing jitter excess | {fmt(bb['jitterExcessRms'], 5)} | {fmt(mb['jitterExcessRms'], 5)} | {fmt(ib['jitterExcessRms'], 5)} | {fmt(pb['jitterExcessRms'], 5)} | {delta(mb['jitterExcessRms'], bb['jitterExcessRms'])} |")
        lines.append(f"| {w} bearing step stddev | {fmt(bb['stepStddev'], 4)} | {fmt(mb['stepStddev'], 4)} | {fmt(ib['stepStddev'], 4)} | {fmt(pb['stepStddev'], 4)} | {delta(mb['stepStddev'], bb['stepStddev'])} |")
        lines.append(f"| {w} bearing travel | {fmt(bb.get('travel'), 1)} | {fmt(mb.get('travel'), 1)} | {fmt(ib.get('travel'), 1)} | {fmt(pb.get('travel'), 1)} | {delta(mb.get('travel'), bb.get('travel'))} |")
        bp, mp, ip, pp = b["pistons"], m["pistons"], i["pistons"], p["pistons"]
        lines.append(f"| {w} piston desync RMS | {fmt(bp['desyncRms'], 5)} | {fmt(mp['desyncRms'], 5)} | {fmt(ip['desyncRms'], 5)} | {fmt(pp['desyncRms'], 5)} | {delta(mp['desyncRms'], bp['desyncRms'])} |")
        lines.append(f"| {w} piston jitter excess | {fmt(bp['jitterExcessRms'], 5)} | {fmt(mp['jitterExcessRms'], 5)} | {fmt(ip['jitterExcessRms'], 5)} | {fmt(pp['jitterExcessRms'], 5)} | {delta(mp['jitterExcessRms'], bp['jitterExcessRms'])} |")
        lines.append(f"| {w} piston travel | {fmt(bp.get('travel'), 2)} | {fmt(mp.get('travel'), 2)} | {fmt(ip.get('travel'), 2)} | {fmt(pp.get('travel'), 2)} | {delta(mp.get('travel'), bp.get('travel'))} |")
        lines.append(f"| {w} belt desync RMS | {fmt(b['beltDesyncRms'], 5)} | {fmt(m['beltDesyncRms'], 5)} | {fmt(i['beltDesyncRms'], 5)} | {fmt(p['beltDesyncRms'], 5)} | {delta(m['beltDesyncRms'], b['beltDesyncRms'])} |")

    lines.append("")
    lines.append("## Worlds")
    lines.append("")
    for name, doc in ("baseline", base), ("mod", mod), ("inert", inert), ("paused", paused):
        lines.append(f"- {name}: {doc.get('world')} modLoaded={doc.get('modLoaded')} skip={doc.get('skipContraptionTicks')}")
    lines.append("")
    lines.append("## Mod bookkeeping")
    lines.append("")
    lines.append("```")
    lines.append(json.dumps(mod.get("modStats"), indent=2))
    lines.append("```")
    lines.append("")
    lines.append("Inert kill-switch stats:")
    lines.append("")
    lines.append("```")
    lines.append(json.dumps(inert.get("modStats"), indent=2))
    lines.append("```")
    text = "\n".join(lines) + "\n"
    with open(md_p, "w") as f:
        f.write(text)
    with open(json_p, "w") as f:
        json.dump({"baseline": base, "mod": mod, "inert": inert, "paused": paused}, f)
    print(text)


if __name__ == "__main__":
    main(sys.argv)
