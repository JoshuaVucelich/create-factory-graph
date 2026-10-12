package com.vws.benchprobe;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.contraptions.piston.MechanicalPistonBlock;
import com.simibubi.create.content.contraptions.piston.MechanicalPistonBlockEntity;
import com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltHelper;
import com.simibubi.create.content.kinetics.belt.BeltPart;
import com.simibubi.create.content.kinetics.belt.BeltSlope;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * A compact factory: parallel shaft/cog lines, moving belts, machines, rotating bearings,
 * and translating sticky pistons. Networks are separate so one line cannot merge into a
 * stack-overflowing RotationPropagator walk.
 */
public final class FactoryWorld {
    private static final Logger LOGGER = LogUtils.getLogger();

    static final int LINES = 32;
    static final int LINE_LEN = 48;
    static final int BELTS = 4;
    static final int BELT_LEN = 16;
    static final int BEARINGS = 4;
    static final int PISTONS = 4;
    /** Long enough that 32 RPM (0.0625 blocks/tick) is still extending at the end of the run. */
    static final int POLES = 64;
    static final int RPM = 32;

    public final List<BlockPos> lineMotors = new ArrayList<>();
    public final List<BlockPos> beltMotors = new ArrayList<>();
    public final List<BlockPos> bearingMotors = new ArrayList<>();
    public final List<BlockPos> pistonMotors = new ArrayList<>();
    public final List<BlockPos> bearings = new ArrayList<>();
    public final List<BlockPos> pistons = new ArrayList<>();
    public final List<BlockPos> beltStarts = new ArrayList<>();
    public final List<BlockPos> jammedStarts = new ArrayList<>();

    public int placedRelays;
    public int placedMachines;
    public int bearingsRunning;
    public int pistonsRunning;
    public int beltsWithItems;
    public int nonzeroSpeed;

    private int surface = 4;

    public void build(ServerLevel level) {
        surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 8, 8);
        int y = surface + 1;
        LOGGER.info("benchprobe surface y={} placing factory at {}", surface, y);
        keepLoaded(level);
        // Lines occupy z = 8 .. 8+(LINES-1)*3. Belts, contraptions, and machines sit past that
        // so cogwheels on the lines cannot merge into them.
        for (int i = 0; i < LINES; i++) buildLine(level, new BlockPos(8, y, 8 + i * 3));
        for (int i = 0; i < BELTS; i++) buildBelt(level, new BlockPos(8 + i * 6, y, 116), false);
        buildBelt(level, new BlockPos(40, y, 116), true);
        for (int i = 0; i < BEARINGS; i++) buildBearing(level, new BlockPos(8 + i * 8, y, 148));
        for (int i = 0; i < PISTONS; i++) buildPiston(level, new BlockPos(48 + i * 4, y, 148));
        for (int i = 0; i < 4; i++) buildMachines(level, new BlockPos(8 + i * 8, y, 168));
        LOGGER.info("benchprobe placed relays={} machines={} motors={}", placedRelays, placedMachines, lineMotors.size() + beltMotors.size());
    }

    public void power(ServerLevel level, int rpm) {
        for (BlockPos pos : lineMotors) setMotor(level, pos, rpm);
        for (BlockPos pos : beltMotors) setMotor(level, pos, rpm);
        for (BlockPos pos : bearingMotors) setMotor(level, pos, rpm);
        // Facing +Y with a non-Z axle extends toward +Y only when speed is negative.
        for (BlockPos pos : pistonMotors) setMotor(level, pos, -rpm);
    }

    public void churn(ServerLevel level) {
        for (int i = 0; i < lineMotors.size(); i++) {
            setMotor(level, lineMotors.get(i), (i & 1) == 0 ? 96 : 16);
        }
    }

    public void assemble(ServerLevel level) {
        bearingsRunning = 0;
        pistonsRunning = 0;
        for (BlockPos pos : bearings) {
            if (level.getBlockEntity(pos) instanceof MechanicalBearingBlockEntity bearing) {
                bearing.assemble();
                if (bearing.isRunning()) bearingsRunning++;
                else LOGGER.warn("bearing {} did not run: {}", pos, bearing.getLastAssemblyException());
            } else {
                LOGGER.warn("no bearing BE at {}", pos);
            }
        }
        for (BlockPos pos : pistons) {
            if (level.getBlockEntity(pos) instanceof MechanicalPistonBlockEntity piston) {
                try {
                    piston.assemble();
                } catch (Throwable t) {
                    LOGGER.warn("piston assemble threw at {}: {}", pos, t.toString());
                }
                if (piston.running) pistonsRunning++;
                else LOGGER.warn("piston {} did not run speed {}: {}", pos, piston.getSpeed(), piston.getLastAssemblyException());
            } else {
                LOGGER.warn("no piston BE at {}", pos);
            }
        }
        seedBelts(level);
        LOGGER.info("benchprobe assembled bearings {}/{} pistons {}/{} beltsWithItems {}",
                bearingsRunning, bearings.size(), pistonsRunning, pistons.size(), beltsWithItems);
    }

    public void sampleSpeeds(ServerLevel level) {
        nonzeroSpeed = 0;
        for (BlockPos pos : lineMotors) {
            if (level.getBlockEntity(pos) instanceof GeneratingKineticBlockEntity gen && gen.getSpeed() != 0) nonzeroSpeed++;
        }
    }

    private void buildLine(ServerLevel level, BlockPos origin) {
        BlockPos motor = origin;
        place(level, motor, facing(AllBlocks.CREATIVE_MOTOR.getDefaultState(), Direction.EAST));
        lineMotors.add(motor.immutable());
        for (int i = 1; i <= LINE_LEN; i++) {
            BlockPos pos = origin.offset(i, 0, 0);
            boolean cog = i % 4 == 0;
            BlockState state = (cog ? AllBlocks.COGWHEEL : AllBlocks.SHAFT).getDefaultState()
                    .setValue(BlockStateProperties.AXIS, Direction.Axis.X);
            place(level, pos, state);
            placedRelays++;
        }
    }

    private void buildBelt(ServerLevel level, BlockPos origin, boolean jammed) {
        BlockPos motor = origin.offset(-2, 0, 0);
        place(level, motor, facing(AllBlocks.CREATIVE_MOTOR.getDefaultState(), Direction.EAST));
        beltMotors.add(motor.immutable());
        place(level, origin.offset(-1, 0, 0), AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        placedRelays++;
        for (int i = 0; i < BELT_LEN; i++) {
            BlockState belt = AllBlocks.BELT.getDefaultState()
                    .setValue(BeltBlock.SLOPE, BeltSlope.HORIZONTAL)
                    .setValue(BeltBlock.PART, BeltPart.START)
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
            place(level, origin.offset(0, 0, i), belt);
        }
        BeltBlock.initBelt(level, origin);
        if (jammed) {
            jammedStarts.add(origin.immutable());
            place(level, origin.offset(0, 0, BELT_LEN), Blocks.ANDESITE.defaultBlockState());
        } else {
            beltStarts.add(origin.immutable());
            BlockPos end = origin.offset(0, 0, BELT_LEN);
            place(level, end, AllBlocks.DEPOT.getDefaultState());
            place(level, end.above(), facing(AllBlocks.ANDESITE_FUNNEL.getDefaultState(), Direction.DOWN));
            place(level, end.offset(0, 2, 0), AllBlocks.CHUTE.getDefaultState());
            placedMachines += 3;
        }
    }

    private void buildBearing(ServerLevel level, BlockPos origin) {
        place(level, origin, facing(AllBlocks.CREATIVE_MOTOR.getDefaultState(), Direction.UP));
        bearingMotors.add(origin.immutable());
        place(level, origin.above(), AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.Y));
        place(level, origin.above(2), AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.Y));
        placedRelays += 2;
        BlockPos bearing = origin.above(3);
        place(level, bearing, facing(AllBlocks.MECHANICAL_BEARING.getDefaultState(), Direction.UP));
        bearings.add(bearing.immutable());
        BlockPos center = bearing.above();
        List<BlockPos> casings = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos c = center.offset(dx, 0, dz);
                place(level, c, AllBlocks.ANDESITE_CASING.getDefaultState());
                casings.add(c.immutable());
            }
        }
        glueGrid(level, casings);
        ArmorStand stand = new ArmorStand(level, center.getX() + 2.5, center.getY(), center.getZ() + 0.5);
        stand.setNoGravity(true);
        level.addFreshEntity(stand);
    }

    private void buildPiston(ServerLevel level, BlockPos origin) {
        // Poles are not kinetic, so they cannot carry power. They sit under a piston
        // whose axle is X; a motor on the west face drives it.
        for (int i = 0; i < POLES; i++) {
            BlockState pole = AllBlocks.PISTON_EXTENSION_POLE.getDefaultState()
                    .setValue(BlockStateProperties.FACING, Direction.UP);
            place(level, origin.above(i), pole);
        }
        BlockPos piston = origin.above(POLES);
        BlockState state = AllBlocks.STICKY_MECHANICAL_PISTON.getDefaultState()
                .setValue(BlockStateProperties.FACING, Direction.UP);
        if (state.hasProperty(MechanicalPistonBlock.STATE)) {
            state = state.setValue(MechanicalPistonBlock.STATE, MechanicalPistonBlock.PistonState.RETRACTED);
        }
        if (state.hasProperty(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE)) {
            state = state.setValue(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE, true);
        }
        place(level, piston, state);
        pistons.add(piston.immutable());
        place(level, piston.above(), AllBlocks.ANDESITE_CASING.getDefaultState());
        BlockPos motor = piston.west();
        place(level, motor, facing(AllBlocks.CREATIVE_MOTOR.getDefaultState(), Direction.EAST));
        pistonMotors.add(motor.immutable());
    }

    private void buildMachines(ServerLevel level, BlockPos origin) {
        // Press and fan take a horizontal shaft. Deployer points down; axis_along_first
        // selects the X axle so the same shaft line drives it.
        place(level, origin, facing(AllBlocks.CREATIVE_MOTOR.getDefaultState(), Direction.EAST));
        lineMotors.add(origin.immutable());
        place(level, origin.east(), AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        placedRelays++;
        place(level, origin.east(2), facing(AllBlocks.MECHANICAL_PRESS.getDefaultState(), Direction.EAST));
        place(level, origin.east(3), AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        placedRelays++;
        place(level, origin.east(4), facing(AllBlocks.ENCASED_FAN.getDefaultState(), Direction.EAST));
        place(level, origin.east(5), AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        placedRelays++;
        BlockState deployer = facing(AllBlocks.DEPLOYER.getDefaultState(), Direction.DOWN);
        if (deployer.hasProperty(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE)) {
            deployer = deployer.setValue(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE, true);
        }
        place(level, origin.east(6), deployer);
        // Mixer is a vertical cog with no shaft faces. A Y-axis cog beside it meshes.
        BlockPos mixMotor = origin.offset(0, 0, 2);
        place(level, mixMotor, facing(AllBlocks.CREATIVE_MOTOR.getDefaultState(), Direction.UP));
        lineMotors.add(mixMotor.immutable());
        place(level, mixMotor.above(), AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.Y));
        placedRelays++;
        place(level, mixMotor.above().east(), AllBlocks.MECHANICAL_MIXER.getDefaultState());
        placedMachines += 4;
    }

    private static void keepLoaded(ServerLevel level) {
        for (int cx = 0; cx <= 6; cx++) {
            for (int cz = 0; cz <= 11; cz++) {
                level.setChunkForced(cx, cz, true);
            }
        }
    }

    private void seedBelts(ServerLevel level) {
        beltsWithItems = 0;
        for (BlockPos start : beltStarts) seed(level, start, 4, 3f);
        for (BlockPos start : jammedStarts) seed(level, start, BELT_LEN - 1, 1f);
    }

    private void seed(ServerLevel level, BlockPos start, int count, float spacing) {
        BeltBlockEntity controller = BeltHelper.getControllerBE(level, start);
        if (controller == null) {
            LOGGER.warn("belt missing at {}", start);
            return;
        }
        if (controller.beltLength < 2 || controller.getInventory() == null) {
            LOGGER.warn("belt {} has no inventory (length={})", start, controller.beltLength);
            return;
        }
        for (int i = 0; i < count; i++) {
            TransportedItemStack stack = new TransportedItemStack(new ItemStack(Items.IRON_INGOT));
            stack.beltPosition = 1f + i * spacing;
            controller.getInventory().addItem(stack);
        }
        beltsWithItems++;
        LOGGER.info("belt {} length {} speed {} items {}", start, controller.beltLength, controller.getSpeed(), count);
    }

    private static void glueGrid(ServerLevel level, List<BlockPos> blocks) {
        for (int i = 0; i < blocks.size(); i++) {
            for (int j = i + 1; j < blocks.size(); j++) {
                BlockPos a = blocks.get(i);
                BlockPos b = blocks.get(j);
                int manhattan = Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY()) + Math.abs(a.getZ() - b.getZ());
                if (manhattan != 1) continue;
                SuperGlueEntity glue = new SuperGlueEntity(level, SuperGlueEntity.span(a, b));
                level.addFreshEntity(glue);
            }
        }
    }

    private static void setMotor(ServerLevel level, BlockPos pos, int rpm) {
        if (level.getBlockEntity(pos) instanceof CreativeMotorBlockEntity motor) {
            motor.generatedSpeed.setValue(rpm);
            motor.updateGeneratedRotation();
        }
    }

    private static void place(ServerLevel level, BlockPos pos, BlockState state) {
        level.getChunk(pos);
        level.setBlock(pos, state, 3);
    }

    private static BlockState facing(BlockState state, Direction dir) {
        if (state.hasProperty(BlockStateProperties.FACING)
                && BlockStateProperties.FACING.getPossibleValues().contains(dir)) {
            return state.setValue(BlockStateProperties.FACING, dir);
        }
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            Direction horizontal = dir.getAxis().isVertical() ? Direction.EAST : dir;
            return state.setValue(BlockStateProperties.HORIZONTAL_FACING, horizontal);
        }
        return state;
    }

    public String summary() {
        return "relays=" + placedRelays + " machines=" + placedMachines
                + " bearingsRunning=" + bearingsRunning + "/" + bearings.size()
                + " pistonsRunning=" + pistonsRunning + "/" + pistons.size()
                + " beltsWithItems=" + beltsWithItems
                + " lineMotorsWithSpeed=" + nonzeroSpeed + "/" + lineMotors.size();
    }

    /** Keeps the search box reference used by callers that want the factory footprint. */
    public AABB footprint() {
        return new AABB(0, surface - 2, 0, 112, surface + 80, 192);
    }
}
