package dev.ic2universalenergy;

import ic2.api.energy.EnergyNet;
import ic2.api.energy.tile.IEnergySink;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PrefixGameTestTemplate(false)
@GameTestHolder("ic2universalenergy_integration")
public class BuildCraftQuarryIntegrationTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("QuarryIntegrationTest");
    private static final BlockPos QUARRY = new BlockPos(8, 3, 4);

    @GameTest(setupTicks = 10, template = BridgeTestSupport.TEMPLATE, timeoutTicks = 400, batch = "quarry")
    public static void batBoxDirectlyPowersQuarry(GameTestHelper helper) {
        batBoxPath(helper, false, false);
    }

    @GameTest(setupTicks = 10, template = BridgeTestSupport.TEMPLATE, timeoutTicks = 400, batch = "quarry")
    public static void batBoxCopperCablePowersQuarry(GameTestHelper helper) {
        batBoxPath(helper, true, false);
    }

    @GameTest(setupTicks = 10, template = BridgeTestSupport.TEMPLATE, timeoutTicks = 400, batch = "quarry")
    public static void batBoxWrongOutputDoesNotPowerQuarry(GameTestHelper helper) {
        batBoxPath(helper, true, true);
    }

    @GameTest(setupTicks = 10, template = BridgeTestSupport.TEMPLATE, timeoutTicks = 400, batch = "quarry")
    public static void twoLavaGeneratorsDirectlyPowerQuarry(GameTestHelper helper) {
        generatorPath(helper, false);
    }

    @GameTest(setupTicks = 10, template = BridgeTestSupport.TEMPLATE, timeoutTicks = 400, batch = "quarry")
    public static void twoLavaGeneratorsCopperCablePowerQuarry(GameTestHelper helper) {
        generatorPath(helper, true);
    }

    @GameTest(setupTicks = 10, template = BridgeTestSupport.TEMPLATE, timeoutTicks = 100, batch = "quarry_worker")
    public static void quarryDemandIsVisibleToEnergyNetWorker(GameTestHelper helper) {
        BridgeTestSupport.clearTemplate(helper);
        BridgeTestSupport.place(helper, QUARRY, "buildcraftbuilders:quarry");
        helper.startSequence().thenIdle(20).thenExecute(() -> {
            IEnergySink sink = requireSink(helper);
            double mainThreadDemand = sink.getDemandedEnergy();
            double workerDemand = CompletableFuture.supplyAsync(sink::getDemandedEnergy).join();
            LOGGER.info("QUARRY demand: server={} EU, worker={} EU", mainThreadDemand, workerDemand);
            helper.assertTrue(mainThreadDemand > 0 && workerDemand == mainThreadDemand,
                  "IC2 worker cannot see Quarry demand: server=" + mainThreadDemand + ", worker=" + workerDemand);
        }).thenSucceed();
    }

    private static void batBoxPath(GameTestHelper helper, boolean cable, boolean wrongOutput) {
        BridgeTestSupport.clearTemplate(helper);
        BridgeTestSupport.place(helper, QUARRY, "buildcraftbuilders:quarry");
        if (cable) {
            BridgeTestSupport.place(helper, QUARRY.west(), "ic2:insulated_copper_cable");
        }
        BlockPos batteryPos = QUARRY.west(cable ? 2 : 1);
        BridgeTestSupport.place(helper, batteryPos, "ic2:batbox");
        BridgeTestSupport.faceIc2Block(helper, batteryPos, wrongOutput ? Direction.WEST : Direction.EAST);
        helper.startSequence().thenIdle(20).thenExecute(() -> {
            requireSink(helper);
            BridgeTestSupport.charge(helper.getBlockEntity(batteryPos), 30_000);
        }).thenIdle(220).thenExecute(() -> {
            double remaining = BridgeTestSupport.storedEu(helper.getBlockEntity(batteryPos));
            LOGGER.info("BATBOX cable={} wrongOutput={} {}", cable, wrongOutput, BridgeTestSupport.describeBattery(helper, batteryPos));
            if (cable) {
                LOGGER.info("COPPER EnergyNet={} block={}", EnergyNet.instance.getTile(helper.getLevel(), helper.absolutePos(QUARRY.west())),
                      helper.getBlockState(QUARRY.west()));
            }
            long storedMj = quarryEnergy(helper);
            int frames = placedFrames(helper);
            LOGGER.info("QUARRY batbox cable={} wrongOutput={}: source={} EU, quarry={} microMJ, frames={}",
                  cable, wrongOutput, remaining, storedMj, frames);
            if (wrongOutput) {
                helper.assertTrue(remaining == 30_000 && storedMj == 0 && frames == 0, "Wrong BatBox side emitted energy");
            } else {
                helper.assertTrue(remaining < 30_000, "BatBox did not emit any EU to Quarry; sink demand=" + requireSink(helper).getDemandedEnergy());
                helper.assertTrue(storedMj > 0 || frames > 0, "Quarry received no usable MJ");
                helper.assertTrue(frames > 0, "Quarry did not perform work (no frame placed)");
            }
        }).thenSucceed();
    }

    private static void generatorPath(GameTestHelper helper, boolean cable) {
        BridgeTestSupport.clearTemplate(helper);
        BridgeTestSupport.place(helper, QUARRY, "buildcraftbuilders:quarry");
        BlockPos first = cable ? QUARRY.west(2) : QUARRY.west();
        BlockPos second = cable ? QUARRY.west().north() : QUARRY.north();
        if (cable) {
            BridgeTestSupport.place(helper, QUARRY.west(), "ic2:insulated_copper_cable");
        }
        BridgeTestSupport.place(helper, first, "ic2:geo_generator");
        BridgeTestSupport.place(helper, second, "ic2:geo_generator");
        helper.startSequence().thenIdle(20).thenExecute(() -> {
            requireSink(helper);
            for (BlockPos pos : List.of(first, second)) {
                IFluidHandler fluids = helper.getBlockEntity(pos).getCapability(ForgeCapabilities.FLUID_HANDLER).orElseThrow(
                      () -> new IllegalStateException("Generator lacks lava tank"));
                helper.assertTrue(fluids.fill(new FluidStack(Fluids.LAVA, 1000), IFluidHandler.FluidAction.EXECUTE) == 1000,
                      "Generator did not accept lava");
            }
        }).thenIdle(220).thenExecute(() -> {
            long storedMj = quarryEnergy(helper);
            int frames = placedFrames(helper);
            LOGGER.info("QUARRY two lava generators cable={}: sources={}/{} EU, quarry={} microMJ, frames={}",
                  cable, BridgeTestSupport.storedEu(helper.getBlockEntity(first)), BridgeTestSupport.storedEu(helper.getBlockEntity(second)), storedMj, frames);
            helper.assertTrue(storedMj > 0 || frames > 0, "Two lava generators delivered no MJ to Quarry; sink demand=" + requireSink(helper).getDemandedEnergy());
            helper.assertTrue(frames > 0, "Quarry did not perform work using the lava generators");
        }).thenSucceed();
    }

    private static IEnergySink requireSink(GameTestHelper helper) {
        var tile = EnergyNet.instance.getTile(helper.getLevel(), helper.absolutePos(QUARRY));
        helper.assertTrue(tile instanceof IEnergySink, "Quarry is missing from IC2 EnergyNet: " + tile);
        return (IEnergySink) tile;
    }

    private static long quarryEnergy(GameTestHelper helper) {
        Object battery = BridgeTestSupport.field(helper.getBlockEntity(QUARRY), "battery");
        return ((Number) BridgeTestSupport.call(battery, "getStored", new Class<?>[0])).longValue();
    }

    @SuppressWarnings("unchecked")
    private static int placedFrames(GameTestHelper helper) {
        BlockEntity quarry = helper.getBlockEntity(QUARRY);
        List<BlockPos> positions = (List<BlockPos>) BridgeTestSupport.field(quarry, "framePoses");
        return (int) positions.stream().filter(pos -> {
            var id = ForgeRegistries.BLOCKS.getKey(helper.getLevel().getBlockState(pos).getBlock());
            return id != null && id.getNamespace().equals("buildcraftbuilders") && id.getPath().equals("frame");
        }).count();
    }
}
