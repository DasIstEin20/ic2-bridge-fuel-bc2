package dev.ic2universalenergy;

import ic2.api.energy.EnergyNet;
import ic2.api.energy.tile.IEnergySink;
import ic2.api.energy.tile.IEnergyTile;
import mekanism.common.content.network.transmitter.Transmitter;
import mekanism.common.tile.transmitter.TileEntityUniversalCable;
import mekanism.common.util.test.GameTestUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/** Verifies the real IC2 EU -> Forge Energy -> Mekanism cable path. */
@PrefixGameTestTemplate(false)
@GameTestHolder(Ic2UniversalEnergyExtension.MOD_ID)
public final class UniversalCableEnergyBridgeTest
{
    private static final double EPSILON = 1.0E-9D;

    private UniversalCableEnergyBridgeTest()
    {
    }

    @GameTest(template = "transmitter/straight_3c_cable", setupTicks = 5, timeoutTicks = 40, batch = "energy_bridge")
    public static void directIc2InjectionIsRejectedWithoutForgeEnergyDemand(GameTestHelper helper)
    {
        BlockPos relativeCablePos = findCable(helper);
        BlockPos absoluteCablePos = helper.absolutePos(relativeCablePos);

        GameTestUtils.succeedIfSequence(helper, sequence -> sequence
                .thenIdle(3)
                .thenExecute(() -> verifyBackpressureWithoutConsumer(helper, relativeCablePos, absoluteCablePos))
        );
    }

    @GameTest(template = "transmitter/straight_3c_cable", setupTicks = 5, timeoutTicks = 80, batch = "energy_bridge")
    public static void batBoxConnectsWithoutDrainingWhenNoFeConsumer(GameTestHelper helper)
    {
        BlockPos relativeCablePos = findCable(helper);
        Direction sourceSide = findAirSide(helper, relativeCablePos);
        BlockPos relativeBatBoxPos = relativeCablePos.relative(sourceSide);
        int[] cableEnergyBefore = new int[1];

        GameTestUtils.succeedIfSequence(helper, sequence -> sequence
                .thenExecute(() -> placeBatBox(helper, relativeBatBoxPos, sourceSide.getOpposite()))
                .thenIdle(5)
                .thenExecute(() -> chargeBatBoxAndVerifyConnection(
                        helper, relativeCablePos, relativeBatBoxPos, sourceSide, cableEnergyBefore))
                .thenIdle(12)
                .thenExecute(() -> verifyBatBoxBackpressure(
                        helper, relativeCablePos, relativeBatBoxPos, sourceSide, cableEnergyBefore[0]))
        );
    }

    @GameTest(template = "transmitter/straight_3c_cable", setupTicks = 5, timeoutTicks = 100, batch = "energy_bridge")
    public static void batBoxCableAndRefinedStorageControllerConserveEnergy(GameTestHelper helper)
    {
        BlockPos relativeCablePos = findCable(helper);
        Direction sourceSide = findAirSide(helper, relativeCablePos);
        Direction consumerSide = findAirSideExcluding(helper, relativeCablePos, sourceSide);
        BlockPos relativeBatBoxPos = relativeCablePos.relative(sourceSide);
        BlockPos relativeControllerPos = relativeCablePos.relative(consumerSide);
        EnergyPathSnapshot snapshot = new EnergyPathSnapshot();

        GameTestUtils.succeedIfSequence(helper, sequence -> sequence
                .thenExecute(() -> {
                    placeBatBox(helper, relativeBatBoxPos, sourceSide.getOpposite());
                    placeRegisteredBlock(helper, relativeControllerPos, "refinedstorage", "controller");
                })
                .thenIdle(8)
                .thenExecute(() -> prepareEnergyPath(
                        helper, relativeCablePos, relativeBatBoxPos, relativeControllerPos,
                        sourceSide, consumerSide, snapshot, false))
                .thenIdle(16)
                .thenExecute(() -> verifyEnergyPathConservation(
                        helper, relativeCablePos, relativeBatBoxPos, relativeControllerPos,
                        sourceSide, consumerSide, snapshot))
        );
    }

    @GameTest(template = "transmitter/straight_3c_cable", setupTicks = 5, timeoutTicks = 100, batch = "energy_bridge")
    public static void fullRefinedStorageControllerClosesEnergyDemandGate(GameTestHelper helper)
    {
        BlockPos relativeCablePos = findCable(helper);
        Direction sourceSide = findAirSide(helper, relativeCablePos);
        Direction consumerSide = findAirSideExcluding(helper, relativeCablePos, sourceSide);
        BlockPos relativeBatBoxPos = relativeCablePos.relative(sourceSide);
        BlockPos relativeControllerPos = relativeCablePos.relative(consumerSide);
        EnergyPathSnapshot snapshot = new EnergyPathSnapshot();

        GameTestUtils.succeedIfSequence(helper, sequence -> sequence
                .thenExecute(() -> {
                    placeBatBox(helper, relativeBatBoxPos, sourceSide.getOpposite());
                    placeRegisteredBlock(helper, relativeControllerPos, "refinedstorage", "controller");
                })
                .thenIdle(8)
                .thenExecute(() -> prepareEnergyPath(
                        helper, relativeCablePos, relativeBatBoxPos, relativeControllerPos,
                        sourceSide, consumerSide, snapshot, true))
                .thenIdle(16)
                .thenExecute(() -> verifyClosedEnergyDemandGate(
                        helper, relativeCablePos, relativeBatBoxPos, relativeControllerPos,
                        sourceSide, consumerSide, snapshot))
        );
    }

    @GameTest(template = "transmitter/straight_3c_cable", setupTicks = 5, timeoutTicks = 100, batch = "conversion_rate")
    public static void liveForgeEnergyRateControlsEveryEuPacket(GameTestHelper helper)
    {
        BlockPos relativeCablePos = findCable(helper);
        Direction consumerSide = findAirSide(helper, relativeCablePos);
        BlockPos relativeControllerPos = relativeCablePos.relative(consumerSide);
        ConversionConfigSnapshot snapshot = new ConversionConfigSnapshot();

        GameTestUtils.succeedIfSequence(helper, sequence -> sequence
                .thenExecute(() -> {
                    snapshot.capture();
                    BridgeConfig.ENERGY_TRANSFER_LIMIT_MODE.set(EnergyTransferLimitMode.AUTO);
                    BridgeConfig.FORGE_ENERGY_PER_EU.set(4.0D);
                    placeRegisteredBlock(helper, relativeControllerPos, "refinedstorage", "controller");
                })
                .thenIdle(5)
                .thenExecute(() -> verifyLiveConversionRate(
                        helper, relativeCablePos, consumerSide, snapshot, 4.0D, 40))
                .thenExecute(() -> BridgeConfig.FORGE_ENERGY_PER_EU.set(1.0D))
                .thenIdle(3)
                .thenExecute(() -> verifyLiveConversionRate(
                        helper, relativeCablePos, consumerSide, snapshot, 1.0D, 10))
                .thenExecute(() -> BridgeConfig.FORGE_ENERGY_PER_EU.set(0.2D))
                .thenIdle(3)
                .thenExecute(() -> verifyLiveConversionRate(
                        helper, relativeCablePos, consumerSide, snapshot, 0.2D, 2))
                .thenExecute(snapshot::restore)
        );
    }

    private static BlockPos findCable(GameTestHelper helper)
    {
        BlockPos[] result = new BlockPos[1];
        helper.forEveryBlockInStructure(relativePos -> {
            if (result[0] == null
                    && GameTestUtils.getBlockEntity(helper, TileEntityUniversalCable.class, relativePos) != null)
            {
                result[0] = relativePos.immutable();
            }
        });
        if (result[0] == null)
        {
            helper.fail("Expected at least one Universal Cable in the test structure");
        }
        return result[0];
    }

    private static Direction findAirSide(GameTestHelper helper, BlockPos cablePos)
    {
        for (Direction direction : Direction.values())
        {
            if (helper.getBlockState(cablePos.relative(direction)).is(Blocks.AIR))
            {
                return direction;
            }
        }
        helper.fail("Expected an air side next to the Universal Cable", cablePos);
        return Direction.UP;
    }

    private static Direction findAirSideExcluding(GameTestHelper helper, BlockPos cablePos, Direction excluded)
    {
        for (Direction direction : Direction.values())
        {
            if (direction != excluded && helper.getBlockState(cablePos.relative(direction)).is(Blocks.AIR))
            {
                return direction;
            }
        }
        helper.fail("Expected a second air side next to the Universal Cable", cablePos);
        return Direction.DOWN;
    }

    private static void verifyBackpressureWithoutConsumer(GameTestHelper helper, BlockPos relativeCablePos, BlockPos absoluteCablePos)
    {
        TileEntityUniversalCable cable = requireCable(helper, relativeCablePos);
        SidedStorage sidedStorage = findReceivingStorage(helper, cable, relativeCablePos);

        IEnergyTile tile = EnergyNet.instance.getTile(helper.getLevel(), absoluteCablePos);
        if (!(tile instanceof IEnergySink sink))
        {
            helper.fail("Universal Cable was not registered directly as an IC2 EnergyNet sink", relativeCablePos);
            return;
        }

        int energyBefore = sidedStorage.storage().getEnergyStored();
        double offeredEu = 100.0D;
        double leftoverEu = sink.injectEnergy(sidedStorage.side(), offeredEu, 0.0D);
        int insertedFe = sidedStorage.storage().getEnergyStored() - energyBefore;

        if (sink.getDemandedEnergy() != 0.0D || Math.abs(leftoverEu - offeredEu) > EPSILON || insertedFe != 0)
        {
            helper.fail("Energy demand gate accepted EU without a connected FE consumer: inserted "
                    + insertedFe + " FE and returned " + leftoverEu + " EU", relativeCablePos);
            return;
        }
        assertConserved(helper, relativeCablePos, offeredEu, insertedFe, leftoverEu);
    }

    private static void verifyLiveConversionRate(
            GameTestHelper helper,
            BlockPos relativeCablePos,
            Direction injectionSide,
            ConversionConfigSnapshot configSnapshot,
            double expectedRate,
            int expectedQueuedForgeEnergy
    )
    {
        TileEntityUniversalCable cable = requireCable(helper, relativeCablePos);
        int queuedBefore = cable.getIc2EnergyDemandGate().pendingForgeEnergy();
        double offeredEu = 10.0D;
        double leftoverEu = cable.injectEnergy(injectionSide, offeredEu, 0.0D);
        int queuedDelta = cable.getIc2EnergyDemandGate().pendingForgeEnergy() - queuedBefore;
        if (Math.abs(leftoverEu) > EPSILON || queuedDelta != expectedQueuedForgeEnergy)
        {
            configSnapshot.restore();
            helper.fail("Live FE/EU rate " + expectedRate + " converted 10 EU into " + queuedDelta
                    + " FE and returned " + leftoverEu + " EU; expected "
                    + expectedQueuedForgeEnergy + " FE", relativeCablePos);
        }
    }

    private static void placeBatBox(GameTestHelper helper, BlockPos relativeBatBoxPos, Direction outputDirection)
    {
        Block batBox = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("ic2", "batbox"));
        if (batBox == null || batBox == Blocks.AIR)
        {
            helper.fail("IC2 BatBox block is not registered", relativeBatBoxPos);
            return;
        }

        BlockState state = batBox.defaultBlockState();
        Property<?> facingProperty = state.getBlock().getStateDefinition().getProperty("facing");
        if (facingProperty instanceof DirectionProperty directionProperty
                && directionProperty.getPossibleValues().contains(outputDirection))
        {
            state = state.setValue(directionProperty, outputDirection);
        }
        helper.setBlock(relativeBatBoxPos, state);
    }

    private static void placeRegisteredBlock(
            GameTestHelper helper,
            BlockPos relativePos,
            String namespace,
            String path
    )
    {
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(namespace, path));
        if (block == null || block == Blocks.AIR)
        {
            helper.fail(namespace + ":" + path + " block is not registered", relativePos);
            return;
        }
        helper.setBlock(relativePos, block.defaultBlockState());
    }

    private static void prepareEnergyPath(
            GameTestHelper helper,
            BlockPos relativeCablePos,
            BlockPos relativeBatBoxPos,
            BlockPos relativeControllerPos,
            Direction sourceSide,
            Direction consumerSide,
            EnergyPathSnapshot snapshot,
            boolean fillController
    )
    {
        TileEntityUniversalCable cable = requireCable(helper, relativeCablePos);
        cable.getTransmitter().refreshConnections(sourceSide);
        cable.getTransmitter().refreshConnections(consumerSide);
        if (!Transmitter.connectionMapContainsSide(cable.getTransmitter().getAllCurrentConnections(), sourceSide)
                || !Transmitter.connectionMapContainsSide(cable.getTransmitter().getAllCurrentConnections(), consumerSide))
        {
            helper.fail("Universal Cable did not connect to both the BatBox and RS Controller", relativeCablePos);
            return;
        }

        IEnergyStorage cableStorage = requireEnergyStorage(helper, cable, sourceSide, relativeCablePos, "Universal Cable");
        BlockEntity controller = GameTestUtils.getBlockEntity(helper, relativeControllerPos);
        IEnergyStorage controllerStorage = requireEnergyStorage(
                helper, controller, consumerSide.getOpposite(), relativeControllerPos, "RS Controller");
        BlockEntity batBox = GameTestUtils.getBlockEntity(helper, relativeBatBoxPos);
        if (!(batBox instanceof ic2.api.tile.IEnergyStorage ic2Storage))
        {
            helper.fail("Placed IC2 BatBox does not expose IC2 energy storage", relativeBatBoxPos);
            return;
        }

        if (fillController)
        {
            controllerStorage.receiveEnergy(Integer.MAX_VALUE, false);
            if (controllerStorage.getEnergyStored() != controllerStorage.getMaxEnergyStored())
            {
                helper.fail("Could not fill the RS Controller before testing backpressure", relativeControllerPos);
                return;
            }
        }

        snapshot.cableEnergyBefore = cableStorage.getEnergyStored();
        snapshot.controllerEnergyBefore = controllerStorage.getEnergyStored();
        snapshot.pendingEnergyBefore = cable.getIc2EnergyDemandGate().pendingForgeEnergy();
        ic2Storage.addEnergy(1_000);
        snapshot.batBoxEnergyAfterCharge = ic2Storage.getStored();
    }

    private static void verifyEnergyPathConservation(
            GameTestHelper helper,
            BlockPos relativeCablePos,
            BlockPos relativeBatBoxPos,
            BlockPos relativeControllerPos,
            Direction sourceSide,
            Direction consumerSide,
            EnergyPathSnapshot snapshot
    )
    {
        TileEntityUniversalCable cable = requireCable(helper, relativeCablePos);
        IEnergyStorage cableStorage = requireEnergyStorage(helper, cable, sourceSide, relativeCablePos, "Universal Cable");
        IEnergyStorage controllerStorage = requireEnergyStorage(
                helper, GameTestUtils.getBlockEntity(helper, relativeControllerPos),
                consumerSide.getOpposite(), relativeControllerPos, "RS Controller");
        BlockEntity batBox = GameTestUtils.getBlockEntity(helper, relativeBatBoxPos);
        if (!(batBox instanceof ic2.api.tile.IEnergyStorage ic2Storage))
        {
            helper.fail("BatBox disappeared during the RS transfer test", relativeBatBoxPos);
            return;
        }

        int consumedEu = snapshot.batBoxEnergyAfterCharge - ic2Storage.getStored();
        int cableDeltaFe = cableStorage.getEnergyStored() - snapshot.cableEnergyBefore;
        int controllerDeltaFe = controllerStorage.getEnergyStored() - snapshot.controllerEnergyBefore;
        int pendingDeltaFe = cable.getIc2EnergyDemandGate().pendingForgeEnergy() - snapshot.pendingEnergyBefore;
        int accountedFe = cableDeltaFe + controllerDeltaFe + pendingDeltaFe;
        if (consumedEu <= 0 || controllerDeltaFe <= 0)
        {
            helper.fail("BatBox did not deliver EU through the cable to the RS Controller", relativeCablePos);
            return;
        }
        assertConserved(helper, relativeCablePos, consumedEu, accountedFe, 0.0D);
    }

    private static void verifyClosedEnergyDemandGate(
            GameTestHelper helper,
            BlockPos relativeCablePos,
            BlockPos relativeBatBoxPos,
            BlockPos relativeControllerPos,
            Direction sourceSide,
            Direction consumerSide,
            EnergyPathSnapshot snapshot
    )
    {
        TileEntityUniversalCable cable = requireCable(helper, relativeCablePos);
        IEnergyStorage cableStorage = requireEnergyStorage(helper, cable, sourceSide, relativeCablePos, "Universal Cable");
        IEnergyStorage controllerStorage = requireEnergyStorage(
                helper, GameTestUtils.getBlockEntity(helper, relativeControllerPos),
                consumerSide.getOpposite(), relativeControllerPos, "RS Controller");
        BlockEntity batBox = GameTestUtils.getBlockEntity(helper, relativeBatBoxPos);
        if (!(batBox instanceof ic2.api.tile.IEnergyStorage ic2Storage))
        {
            helper.fail("BatBox disappeared during the backpressure test", relativeBatBoxPos);
            return;
        }

        int consumedEu = snapshot.batBoxEnergyAfterCharge - ic2Storage.getStored();
        int cableDeltaFe = cableStorage.getEnergyStored() - snapshot.cableEnergyBefore;
        int controllerDeltaFe = controllerStorage.getEnergyStored() - snapshot.controllerEnergyBefore;
        int pendingDeltaFe = cable.getIc2EnergyDemandGate().pendingForgeEnergy() - snapshot.pendingEnergyBefore;
        if (consumedEu != 0 || cableDeltaFe != 0 || controllerDeltaFe != 0 || pendingDeltaFe != 0)
        {
            helper.fail("Energy demand gate stayed open for a full RS Controller: BatBox lost " + consumedEu
                    + " EU, cable changed by " + cableDeltaFe + " FE, controller changed by "
                    + controllerDeltaFe + " FE and the delivery queue changed by "
                    + pendingDeltaFe + " FE", relativeCablePos);
        }
    }

    private static IEnergyStorage requireEnergyStorage(
            GameTestHelper helper,
            BlockEntity blockEntity,
            Direction side,
            BlockPos relativePos,
            String description
    )
    {
        IEnergyStorage storage = blockEntity == null
                ? null
                : blockEntity.getCapability(ForgeCapabilities.ENERGY, side).orElse(null);
        if (storage == null)
        {
            helper.fail(description + " does not expose Forge Energy on the connected side", relativePos);
        }
        return storage;
    }

    private static void chargeBatBoxAndVerifyConnection(
            GameTestHelper helper,
            BlockPos relativeCablePos,
            BlockPos relativeBatBoxPos,
            Direction sourceSide,
            int[] cableEnergyBefore
    )
    {
        TileEntityUniversalCable cable = requireCable(helper, relativeCablePos);
        cable.getTransmitter().refreshConnections(sourceSide);
        if (!Transmitter.connectionMapContainsSide(cable.getTransmitter().getAllCurrentConnections(), sourceSide))
        {
            helper.fail("Universal Cable did not form a visual connection to the IC2 BatBox", relativeCablePos);
            return;
        }

        BlockEntity batBox = GameTestUtils.getBlockEntity(helper, relativeBatBoxPos);
        if (!(batBox instanceof ic2.api.tile.IEnergyStorage ic2Storage))
        {
            helper.fail("Placed IC2 BatBox does not expose IC2 energy storage", relativeBatBoxPos);
            return;
        }

        IEnergyStorage cableStorage = cable.getCapability(ForgeCapabilities.ENERGY, sourceSide).orElse(null);
        if (cableStorage == null || !cableStorage.canReceive())
        {
            helper.fail("Connected Universal Cable side does not expose its original receiving FE capability", relativeCablePos);
            return;
        }
        cableEnergyBefore[0] = cableStorage.getEnergyStored();
        // IC2 2.10.33-ex120 keeps IEnergyStorage#setStored as a no-op on its
        // electric storage blocks; addEnergy is the functional public API.
        ic2Storage.addEnergy(1_000);
    }

    private static void verifyBatBoxBackpressure(
            GameTestHelper helper,
            BlockPos relativeCablePos,
            BlockPos relativeBatBoxPos,
            Direction sourceSide,
            int energyBefore
    )
    {
        TileEntityUniversalCable cable = requireCable(helper, relativeCablePos);
        IEnergyStorage cableStorage = cable.getCapability(ForgeCapabilities.ENERGY, sourceSide).orElse(null);
        BlockEntity batBox = GameTestUtils.getBlockEntity(helper, relativeBatBoxPos);
        if (cableStorage == null || !(batBox instanceof ic2.api.tile.IEnergyStorage ic2Storage))
        {
            helper.fail("BatBox/cable endpoint disappeared during transfer", relativeCablePos);
            return;
        }

        int insertedFe = cableStorage.getEnergyStored() - energyBefore;
        int consumedEu = 1_000 - ic2Storage.getStored();
        if (insertedFe != 0 || consumedEu != 0)
        {
            helper.fail("BatBox was drained without an FE consumer: lost " + consumedEu
                    + " EU and queued " + insertedFe + " FE", relativeCablePos);
        }
    }

    private static TileEntityUniversalCable requireCable(GameTestHelper helper, BlockPos relativeCablePos)
    {
        TileEntityUniversalCable cable = GameTestUtils.getBlockEntity(helper, TileEntityUniversalCable.class, relativeCablePos);
        if (cable == null)
        {
            helper.fail("Expected a Universal Cable", relativeCablePos);
        }
        return cable;
    }

    private static SidedStorage findReceivingStorage(
            GameTestHelper helper,
            TileEntityUniversalCable cable,
            BlockPos relativeCablePos
    )
    {
        for (Direction direction : Direction.values())
        {
            IEnergyStorage storage = cable.getCapability(ForgeCapabilities.ENERGY, direction).orElse(null);
            if (storage != null && storage.canReceive())
            {
                return new SidedStorage(direction, storage);
            }
        }
        helper.fail("Universal Cable does not expose an original sided receiving FE capability", relativeCablePos);
        return new SidedStorage(Direction.UP, null);
    }

    private static void assertConserved(
            GameTestHelper helper,
            BlockPos relativeCablePos,
            double offeredEu,
            int insertedFe,
            double leftoverEu
    )
    {
        double accountedEu = EnergyConversionService.forgeEnergyToEu(insertedFe) + leftoverEu;
        if (Math.abs(offeredEu - accountedEu) > EPSILON)
        {
            helper.fail("Energy was not conserved: offered " + offeredEu + " EU, stored " + insertedFe
                    + " FE and returned " + leftoverEu + " EU", relativeCablePos);
        }
    }

    private record SidedStorage(Direction side, IEnergyStorage storage)
    {
    }

    private static final class EnergyPathSnapshot
    {
        private int batBoxEnergyAfterCharge;
        private int cableEnergyBefore;
        private int controllerEnergyBefore;
        private int pendingEnergyBefore;
    }

    private static final class ConversionConfigSnapshot
    {
        private double forgeEnergyPerEu;
        private EnergyTransferLimitMode transferLimitMode;

        private void capture()
        {
            this.forgeEnergyPerEu = BridgeConfig.FORGE_ENERGY_PER_EU.get();
            this.transferLimitMode = BridgeConfig.ENERGY_TRANSFER_LIMIT_MODE.get();
        }

        private void restore()
        {
            BridgeConfig.FORGE_ENERGY_PER_EU.set(this.forgeEnergyPerEu);
            BridgeConfig.ENERGY_TRANSFER_LIMIT_MODE.set(this.transferLimitMode);
        }
    }
}
