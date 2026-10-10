package com.vws.createfactorygraph.contraptions;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

import com.simibubi.create.content.contraptions.Contraption;
import com.vws.createfactorygraph.FGConfig;
import com.vws.createfactorygraph.profile.AreaProfiler;
import com.vws.createfactorygraph.world.WorldEpoch;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;

/**
 * Replays the block-shape scan inside {@code ContraptionCollider.getPotentiallyCollidedShapes}
 * when another entity asks for the same local block range of an unchanged contraption.
 */
public final class ContraptionShapeCache {
    public static final class Box {
        public final double x1, y1, z1, x2, y2, z2;
        public Box(double x1, double y1, double z1, double x2, double y2, double z2) {
            this.x1 = x1; this.y1 = y1; this.z1 = z1; this.x2 = x2; this.y2 = y2; this.z2 = z2;
        }
    }

    private static final class Entry {
        int minX, minY, minZ, maxX, maxY, maxZ;
        int size;
        long epoch;
        List<Box> boxes;
    }

    private static final IdentityHashMap<Contraption, Entry> MAP = new IdentityHashMap<>();
    private static final ThreadLocal<List<Box>> RECORDING = new ThreadLocal<>();

    private static final ClassValue<Boolean> PORTAL_AWARE = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                return type.getMethod("isHiddenInPortal", BlockPos.class).getDeclaringClass() != Contraption.class;
            } catch (NoSuchMethodException e) {
                return Boolean.TRUE;
            }
        }
    };

    /** Carriage contraptions hide blocks while in a portal. Those shapes stay uncached. */
    private static boolean usable(Contraption contraption) {
        return contraption != null && !PORTAL_AWARE.get(contraption.getClass());
    }

    public static boolean replay(Contraption contraption, AABB localBB, Shapes.DoubleLineConsumer out) {
        if (!com.vws.createfactorygraph.world.ServerThread.isServer() || !FGConfig.enabled() || !FGConfig.contraptionShapeCache() || !usable(contraption)) return false;
        Entry entry = MAP.get(contraption);
        if (entry == null || entry.boxes == null) return false;
        BlockPos min = min(localBB);
        BlockPos max = max(localBB);
        if (entry.epoch != WorldEpoch.get() || entry.size != contraption.getBlocks().size()) return false;
        if (entry.minX != min.getX() || entry.minY != min.getY() || entry.minZ != min.getZ()) return false;
        if (entry.maxX != max.getX() || entry.maxY != max.getY() || entry.maxZ != max.getZ()) return false;
        for (Box box : entry.boxes) out.consume(box.x1, box.y1, box.z1, box.x2, box.y2, box.z2);
        AreaProfiler.shapeHits++;
        return true;
    }

    public static void begin(Contraption contraption) {
        if (!com.vws.createfactorygraph.world.ServerThread.isServer() || !FGConfig.enabled() || !FGConfig.contraptionShapeCache() || !usable(contraption)) return;
        RECORDING.set(new ArrayList<>());
    }

    public static void record(double x1, double y1, double z1, double x2, double y2, double z2) {
        List<Box> list = RECORDING.get();
        if (list != null) list.add(new Box(x1, y1, z1, x2, y2, z2));
    }

    public static boolean recording() {
        return RECORDING.get() != null;
    }

    public static void end(Contraption contraption, AABB localBB) {
        List<Box> list = RECORDING.get();
        RECORDING.remove();
        if (!com.vws.createfactorygraph.world.ServerThread.isServer() || list == null || !usable(contraption) || !FGConfig.enabled() || !FGConfig.contraptionShapeCache()) return;
        BlockPos min = min(localBB);
        BlockPos max = max(localBB);
        Entry entry = new Entry();
        entry.minX = min.getX();
        entry.minY = min.getY();
        entry.minZ = min.getZ();
        entry.maxX = max.getX();
        entry.maxY = max.getY();
        entry.maxZ = max.getZ();
        entry.size = contraption.getBlocks().size();
        entry.epoch = WorldEpoch.get();
        entry.boxes = list;
        MAP.put(contraption, entry);
        AreaProfiler.shapeMisses++;
    }

    public static void invalidate(Contraption contraption) {
        if (!com.vws.createfactorygraph.world.ServerThread.isServer() || contraption == null) return;
        MAP.remove(contraption);
    }

    /** Same block range Create scans in getPotentiallyCollidedShapes. */
    private static AABB scan(AABB localBB) {
        double height = localBB.getYsize();
        double width = localBB.getXsize();
        double horizontalFactor = (height > width && width != 0) ? height / width : 1;
        double verticalFactor = (width > height && height != 0) ? width / height : 1;
        return localBB.inflate(0.5f).inflate(horizontalFactor, verticalFactor, horizontalFactor);
    }

    private static BlockPos min(AABB localBB) {
        AABB scan = scan(localBB);
        return BlockPos.containing(scan.minX, scan.minY, scan.minZ);
    }

    private static BlockPos max(AABB localBB) {
        AABB scan = scan(localBB);
        return BlockPos.containing(scan.maxX, scan.maxY, scan.maxZ);
    }

    private ContraptionShapeCache() {}
}
