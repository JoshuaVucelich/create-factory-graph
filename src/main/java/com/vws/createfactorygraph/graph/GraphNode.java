package com.vws.createfactorygraph.graph;

import net.minecraft.core.BlockPos;

/** Snapshot of one node in a factory graph. Values are refreshed on graph rebuild. */
public final class GraphNode {
    public final BlockPos pos;
    public final NodeKind kind;
    public final String blockId;
    public float speed;
    /** Stress impact registered with the Create network (su/rpm base). */
    public float stressImpact;
    /** Capacity added (sources only). */
    public float capacity;
    /** Item count held (belt controllers: items on the belt). -1 = not tracked. */
    public int inventoryItems = -1;
    public boolean dormant;

    public GraphNode(BlockPos pos, NodeKind kind, String blockId) {
        this.pos = pos;
        this.kind = kind;
        this.blockId = blockId;
    }
}
