package dev.ic2universalenergy;

import mekanism.common.lib.transmitter.ConnectionType;
import mekanism.common.tile.transmitter.TileEntityTransmitter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

@PrefixGameTestTemplate(false)
@GameTestHolder("ic2universalenergy_integration")
public class TransmitterTransferIntegrationTest {
    private static final BlockPos PIPE = new BlockPos(5, 3, 3);

    @GameTest(setupTicks = 10, template = BridgeTestSupport.TEMPLATE, timeoutTicks = 160, batch = "transfers")
    public static void mechanicalPipeTransfersWaterBetweenBuildCraftTanks(GameTestHelper helper) {
        BridgeTestSupport.clearTemplate(helper);
        BridgeTestSupport.place(helper, PIPE.west(), "buildcraftfactory:tank");
        BridgeTestSupport.place(helper, PIPE.east(), "buildcraftfactory:tank");
        BridgeTestSupport.place(helper, PIPE, "ic2universalenergy:basic_mechanical_pipe");
        helper.startSequence().thenIdle(15).thenExecute(() -> {
            TileEntityTransmitter pipe = (TileEntityTransmitter) helper.getBlockEntity(PIPE);
            pipe.getTransmitter().setConnectionTypeRaw(Direction.WEST, ConnectionType.PULL);
            pipe.getTransmitter().refreshConnections();
            helper.assertTrue(fluid(helper, PIPE.west()).fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE) == 1000,
                  "Source tank did not accept water");
        }).thenIdle(60).thenExecute(() -> {
            int source = fluid(helper, PIPE.west()).getFluidInTank(0).getAmount();
            int destination = fluid(helper, PIPE.east()).getFluidInTank(0).getAmount();
            int inPipe = fluid(helper, PIPE).getFluidInTank(0).getAmount();
            helper.assertTrue(destination > 0, "Mechanical Pipe delivered no water");
            helper.assertTrue(source + destination + inPipe == 1000, "Mechanical Pipe lost or duplicated water");
        }).thenSucceed();
    }

    @GameTest(setupTicks = 10, template = BridgeTestSupport.TEMPLATE, timeoutTicks = 320, batch = "transfers")
    public static void logisticalTransporterTransfersChestInventory(GameTestHelper helper) {
        BridgeTestSupport.clearTemplate(helper);
        BridgeTestSupport.place(helper, PIPE.west(), "minecraft:chest");
        BridgeTestSupport.place(helper, PIPE.east(), "minecraft:chest");
        BridgeTestSupport.place(helper, PIPE, "ic2universalenergy:basic_logistical_transporter");
        helper.startSequence().thenIdle(15).thenExecute(() -> {
            TileEntityTransmitter pipe = (TileEntityTransmitter) helper.getBlockEntity(PIPE);
            pipe.getTransmitter().setConnectionTypeRaw(Direction.WEST, ConnectionType.PULL);
            pipe.getTransmitter().refreshConnections();
            helper.assertTrue(items(helper, PIPE.west()).insertItem(0, new ItemStack(Items.IRON_INGOT, 16), false).isEmpty(),
                  "Source chest rejected items");
        }).thenIdle(240).thenExecute(() -> {
            helper.assertTrue(count(items(helper, PIPE.east())) == 16 && count(items(helper, PIPE.west())) == 0,
                  "Logistical Transporter did not move all 16 ingots without duplication");
        }).thenSucceed();
    }

    private static IFluidHandler fluid(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockEntity(pos).getCapability(ForgeCapabilities.FLUID_HANDLER)
              .orElseThrow(() -> new IllegalStateException("Missing fluid capability at " + pos));
    }

    private static IItemHandler items(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockEntity(pos).getCapability(ForgeCapabilities.ITEM_HANDLER)
              .orElseThrow(() -> new IllegalStateException("Missing item capability at " + pos));
    }

    private static int count(IItemHandler inventory) {
        int count = 0;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.is(Items.IRON_INGOT)) {
                count += stack.getCount();
            }
        }
        return count;
    }
}
