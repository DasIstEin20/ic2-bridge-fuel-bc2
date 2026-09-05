package dev.ic2universalenergy;

import ic2.api.recipe.Recipes;
import ic2.api.energy.EnergyNet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import mekanism.common.tile.transmitter.TileEntityUniversalCable;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PrefixGameTestTemplate(false)
@GameTestHolder("ic2universalenergy_integration")
public class OptionalBridgeIntegrationTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("OptionalBridgeIntegrationTest");
    private static final BlockPos MACHINE = new BlockPos(5, 3, 3);

    @GameTest(setupTicks = 10, template = BridgeTestSupport.TEMPLATE, batch = "fuel_registry")
    public static void allBuildCraftFuelProfilesMatchLiveFuelRegistry(GameTestHelper helper) throws ReflectiveOperationException {
        Object manager = Class.forName("buildcraft.api.fuels.BuildcraftFuelRegistry").getField("fuel").get(null);
        Class<?> fuelApi = Class.forName("buildcraft.api.fuels.IFuel");
        int count = 0;
        for (var definition : BuildCraftCeFuelProfiles.definitions()) {
            Fluid fluid = ForgeRegistries.FLUIDS.getValue(new ResourceLocation(definition.id()));
            helper.assertTrue(fluid != null, "Missing fuel " + definition.id());
            Object bcFuel = BridgeTestSupport.call(manager, "getFuel", new Class<?>[]{FluidStack.class}, new FluidStack(fluid, 1));
            helper.assertTrue(bcFuel != null, "BuildCraft does not accept " + definition.id());
            var ic2Fuel = Recipes.semiFluidGenerator.getBurnProperty(fluid);
            helper.assertTrue(ic2Fuel != null, "IC2 does not accept " + definition.id());
            long power = ((Number) fuelApi.getMethod("getPowerPerCycle").invoke(bcFuel)).longValue();
            int ticks = ((Number) fuelApi.getMethod("getTotalBurningTime").invoke(bcFuel)).intValue();
            double expected = EnergyConversionService.microMegaJoulesToEu(power) * ticks / 1000;
            helper.assertTrue(Math.abs(ic2Fuel.power() - expected) < 1.0E-6,
                  definition.id() + " IC2 fuel=" + ic2Fuel.power() + " EU/mB; BC equivalent=" + expected);
            count++;
        }
        LOGGER.info("Verified {} BuildCraft fuel profiles against the live BC and IC2 registries", count);
        helper.succeed();
    }

    @GameTest(setupTicks = 10, template = BridgeTestSupport.TEMPLATE, batch = "fuel_registry")
    public static void ic2FuelsAreImportedIntoBuildCraft(GameTestHelper helper) throws ReflectiveOperationException {
        Object manager = Class.forName("buildcraft.api.fuels.BuildcraftFuelRegistry").getField("fuel").get(null);
        int count = 0;
        for (var entry : Recipes.semiFluidGenerator.getBurnProperties().entrySet()) {
            var id = ForgeRegistries.FLUIDS.getKey(entry.getKey());
            if (id != null && id.getNamespace().equals("ic2")) {
                Object imported = BridgeTestSupport.call(manager, "getFuel", new Class<?>[]{FluidStack.class}, new FluidStack(entry.getKey(), 1));
                helper.assertTrue(imported != null, "IC2 fuel missing from BC: " + id);
                count++;
            }
        }
        helper.assertTrue(count > 0, "No IC2 fuels tested");
        LOGGER.info("Verified {} IC2 fuels in the BuildCraft combustion registry", count);
        helper.succeed();
    }

    @GameTest(setupTicks = 10, template = BridgeTestSupport.TEMPLATE, timeoutTicks = 180, batch = "forestry")
    public static void batBoxPowersForestryOnlyThroughUniversalCable(GameTestHelper helper) {
        BridgeTestSupport.clearTemplate(helper);
        BridgeTestSupport.place(helper, MACHINE, "forestry:carpenter");
        BlockPos cablePos = MACHINE.west();
        BridgeTestSupport.place(helper, cablePos, "ic2universalenergy:basic_universal_cable");
        BlockPos battery = MACHINE.west(2);
        BridgeTestSupport.place(helper, battery, "ic2:batbox");
        BridgeTestSupport.faceIc2Block(helper, battery, Direction.EAST);
        helper.startSequence().thenIdle(20).thenExecute(() -> BridgeTestSupport.charge(helper.getBlockEntity(battery), 2000))
              .thenIdle(80).thenExecute(() -> {
                  IEnergyStorage receiver = helper.getBlockEntity(MACHINE).getCapability(ForgeCapabilities.ENERGY)
                        .orElseThrow(() -> new IllegalStateException("Carpenter has no FE capability"));
                  TileEntityUniversalCable cable = (TileEntityUniversalCable) helper.getBlockEntity(cablePos);
                  int cableFe = cable.getCapability(ForgeCapabilities.ENERGY, Direction.WEST)
                        .orElseThrow(() -> new IllegalStateException("Cable lacks FE capability")).getEnergyStored();
                  int queued = cable.getIc2EnergyDemandGate().pendingForgeEnergy();
                  double remainingEu = BridgeTestSupport.storedEu(helper.getBlockEntity(battery));
                  int fe = receiver.getEnergyStored();
                  LOGGER.info("FORESTRY via Universal Cable: source={} EU, receiver={} FE, cable={} FE, queued={} FE",
                        remainingEu, fe, cableFe, queued);
                  helper.assertTrue(EnergyNet.instance.getTile(helper.getLevel(), helper.absolutePos(MACHINE)) == null,
                        "Forestry machine has a duplicate direct IC2 adapter");
                  helper.assertTrue(fe > 0 && remainingEu < 2000, "Universal Cable did not power Forestry Carpenter");
                  helper.assertTrue(Math.abs(remainingEu + EnergyConversionService.forgeEnergyToEu(fe + cableFe + queued) - 2000) < 1.0E-6,
                        "Forestry cable path lost or duplicated energy");
              }).thenSucceed();
    }

    @GameTest(setupTicks = 10, template = BridgeTestSupport.TEMPLATE, timeoutTicks = 100, batch = "bc_source")
    public static void activeBuildCraftEngineIsNotMistakenForPassiveProvider(GameTestHelper helper) throws ReflectiveOperationException {
        BridgeTestSupport.clearTemplate(helper);
        BridgeTestSupport.place(helper, MACHINE, "buildcraftenergy:engine_stone");
        Capability<?> capability = (Capability<?>) Class.forName("buildcraft.api.mj.MjAPI").getField("CAP_PASSIVE_PROVIDER").get(null);
        helper.startSequence().thenIdle(20).thenExecute(() -> {
            //BC 8.0.13 engines push MJ; EngineConnector does not implement IMjPassiveProvider.
            for (Direction side : Direction.values()) {
                helper.assertTrue(!helper.getBlockEntity(MACHINE).getCapability(capability, side).isPresent(),
                      "This BC version now exposes passive engine extraction; add a positive source test");
            }
            helper.assertTrue(EnergyNet.instance.getTile(helper.getLevel(), helper.absolutePos(MACHINE)) == null,
                  "An engine without the passive API was incorrectly registered as a source");
        }).thenSucceed();
    }

    @GameTest(setupTicks = 10, template = BridgeTestSupport.TEMPLATE, timeoutTicks = 180, batch = "native_ic2")
    public static void nativeBatBoxCopperCableChargesBatBox(GameTestHelper helper) {
        BridgeTestSupport.clearTemplate(helper);
        BridgeTestSupport.place(helper, MACHINE, "ic2:batbox");
        BridgeTestSupport.faceIc2Block(helper, MACHINE, Direction.EAST);
        BridgeTestSupport.place(helper, MACHINE.west(), "ic2:insulated_copper_cable");
        BlockPos source = MACHINE.west(2);
        BridgeTestSupport.place(helper, source, "ic2:batbox");
        BridgeTestSupport.faceIc2Block(helper, source, Direction.EAST);
        helper.startSequence().thenIdle(20).thenExecute(() -> BridgeTestSupport.charge(helper.getBlockEntity(source), 2000))
              .thenIdle(80).thenExecute(() -> {
                  double received = BridgeTestSupport.storedEu(helper.getBlockEntity(MACHINE));
                  LOGGER.info("NATIVE IC2 received={} source={}", received, BridgeTestSupport.describeBattery(helper, source));
                  helper.assertTrue(received > 0, "Native IC2 BatBox/copper/BatBox control path failed");
              }).thenSucceed();
    }
}
