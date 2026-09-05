package dev.ic2universalenergy;

import ic2.api.energy.EnergyNet;
import ic2.api.energy.tile.IEnergySink;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
@GameTestHolder("ic2universalenergy_integration")
public class BuildCraftEnergyBudgetTest {
    private static final BlockPos QUARRY = new BlockPos(8, 3, 4);
    private static boolean enabled;
    private static EnergyTransferLimitMode limitMode;
    private static EnergyConversionMode conversionMode;
    private static double limit;
    private static double ratio;

    @BeforeBatch(batch = "mj_budget")
    public static void saveConfig(ServerLevel level) {
        enabled = BridgeConfig.ENERGY_BRIDGE_ENABLED.get();
        limitMode = BridgeConfig.ENERGY_TRANSFER_LIMIT_MODE.get();
        conversionMode = BridgeConfig.ENERGY_CONVERSION_MODE.get();
        limit = BridgeConfig.ENERGY_TRANSFER_LIMIT_EU_PER_TICK.get();
        ratio = BridgeConfig.EU_PER_BUILDCRAFT_MJ.get();
        BridgeConfig.ENERGY_BRIDGE_ENABLED.set(true);
        BridgeConfig.ENERGY_TRANSFER_LIMIT_MODE.set(EnergyTransferLimitMode.MANUAL);
        BridgeConfig.ENERGY_TRANSFER_LIMIT_EU_PER_TICK.set(8D);
        BridgeConfig.ENERGY_CONVERSION_MODE.set(EnergyConversionMode.MANUAL);
        BridgeConfig.EU_PER_BUILDCRAFT_MJ.set(5D);
    }

    @AfterBatch(batch = "mj_budget")
    public static void restoreConfig(ServerLevel level) {
        BridgeConfig.ENERGY_BRIDGE_ENABLED.set(enabled);
        BridgeConfig.ENERGY_TRANSFER_LIMIT_MODE.set(limitMode);
        BridgeConfig.ENERGY_CONVERSION_MODE.set(conversionMode);
        BridgeConfig.ENERGY_TRANSFER_LIMIT_EU_PER_TICK.set(limit);
        BridgeConfig.EU_PER_BUILDCRAFT_MJ.set(ratio);
    }

    @GameTest(setupTicks = 10, template = BridgeTestSupport.TEMPLATE,
          batch = "mj_budget", timeoutTicks = 100)
    public static void workerPacketsShareBudgetRespectLiveRatioAndDisableSwitch(GameTestHelper helper) {
        BridgeTestSupport.clearTemplate(helper);
        BridgeTestSupport.place(helper, QUARRY, "buildcraftbuilders:quarry");
        helper.startSequence().thenIdle(20).thenExecute(() -> verifyPackets(helper, 5D))
              .thenIdle(3).thenExecute(() -> {
                  helper.assertTrue(queued(sink(helper)) == 0, "MJ queue was not delivered on the server thread");
                  BridgeConfig.EU_PER_BUILDCRAFT_MJ.set(2.5D);
              }).thenIdle(3).thenExecute(() -> verifyPackets(helper, 2.5D))
              .thenIdle(3).thenExecute(() -> BridgeConfig.ENERGY_BRIDGE_ENABLED.set(false))
              .thenIdle(3).thenExecute(() -> {
                  IEnergySink sink = sink(helper);
                  helper.assertTrue(sink.getDemandedEnergy() == 0 && sink.injectEnergy(Direction.WEST, 8, 1) == 8,
                        "Disabled MJ bridge accepted energy");
              }).thenSucceed();
    }

    private static void verifyPackets(GameTestHelper helper, double expectedRatio) {
        IEnergySink sink = sink(helper);
        helper.assertTrue(Math.abs(sink.getDemandedEnergy() - 8) < 1.0E-6, "Wrong per-tick EU budget");
        double first = CompletableFuture.supplyAsync(() -> sink.injectEnergy(Direction.WEST, 6, 1)).join();
        double second = CompletableFuture.supplyAsync(() -> sink.injectEnergy(Direction.NORTH, 6, 1)).join();
        helper.assertTrue(first == 0 && Math.abs(second - 4) < 1.0E-6 && sink.getDemandedEnergy() == 0,
              "Multiple sources exceeded or failed to consume their shared 8 EU/t budget");
        helper.assertTrue(queued(sink) == EnergyConversionService.euToMicroMegaJoules(8, expectedRatio),
              "Accepted EU does not match the queued MJ at the live ratio");
    }

    private static IEnergySink sink(GameTestHelper helper) {
        return (IEnergySink) EnergyNet.instance.getTile(helper.getLevel(), helper.absolutePos(QUARRY));
    }

    private static long queued(IEnergySink sink) {
        Object handler = Proxy.getInvocationHandler(sink);
        synchronized (handler) {
            return Arrays.stream((long[]) BridgeTestSupport.field(handler, "pendingMicroMj")).sum();
        }
    }
}
