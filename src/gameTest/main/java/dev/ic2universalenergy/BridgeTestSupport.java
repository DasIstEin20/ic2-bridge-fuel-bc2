package dev.ic2universalenergy;

import ic2.api.tile.IWrenchAble;
import ic2.api.energy.EnergyNet;
import ic2.api.energy.tile.IEnergySource;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.registries.ForgeRegistries;

final class BridgeTestSupport {
    static final String TEMPLATE = "bridge_test";

    private BridgeTestSupport() {
    }

    static void clearTemplate(GameTestHelper helper) {
        helper.forEveryBlockInStructure(pos -> {
            if (!helper.getBlockState(pos).isAir()) {
                helper.setBlock(pos, Blocks.AIR);
            }
        });
    }

    //Go through ItemStack.useOn so Forge placement events and the machine's setPlacedBy both run.
    static void place(GameTestHelper helper, BlockPos relative, String id) {
        var item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        helper.assertTrue(item != null && item != Items.AIR, "Missing block item " + id);
        helper.setBlock(relative.below(), Blocks.STONE);
        BlockPos absolute = helper.absolutePos(relative);
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.getAbilities().instabuild = true;
        player.setPos(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() - 2);
        player.setYRot(0);
        ItemStack stack = new ItemStack(item);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var result = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
              new BlockHitResult(Vec3.atCenterOf(absolute.below()).add(0, 0.5, 0), Direction.UP, absolute.below(), false)));
        helper.assertTrue(result.consumesAction() && !helper.getBlockState(relative).isAir(),
              "Could not place " + id + ": result=" + result + ", actual=" + helper.getBlockState(relative));
    }

    static Object field(Object object, String name) {
        try {
            return findField(object, name).get(object);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    static void setField(Object object, String name, Object value) {
        try {
            findField(object, name).set(object, value);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Field findField(Object object, String name) throws NoSuchFieldException {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(name);
    }

    //Changing only the blockstate does not update IC2's cached energy output sides.
    static void faceIc2Block(GameTestHelper helper, BlockPos relative, Direction direction) {
        IWrenchAble block = (IWrenchAble) helper.getBlockState(relative).getBlock();
        BlockPos absolute = helper.absolutePos(relative);
        if (block.getFacing(helper.getLevel(), absolute) != direction) {
            helper.assertTrue(block.setFacing(helper.getLevel(), absolute, direction,
                  FakePlayerFactory.getMinecraft(helper.getLevel())), "IC2 wrench rotation failed");
        }
    }

    static Object call(Object object, String method, Class<?>[] types, Object... arguments) {
        try {
            Method target = object.getClass().getMethod(method, types);
            return target.invoke(object, arguments);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    static double storedEu(Object tile) {
        return ((Number) call(field(tile, "energy"), "getEnergy", new Class<?>[0])).doubleValue();
    }

    static void charge(Object tile, double amount) {
        call(field(tile, "energy"), "addEnergy", new Class<?>[]{double.class}, amount);
    }

    static String describeBattery(GameTestHelper helper, BlockPos relative) {
        Object energy = field(helper.getBlockEntity(relative), "energy");
        Object tile = EnergyNet.instance.getTile(helper.getLevel(), helper.absolutePos(relative));
        return "state=" + helper.getBlockState(relative) + ", outputs=" + call(energy, "getSourceDirs", new Class<?>[0])
              + ", EnergyNet=" + tile + ", offered=" + (tile instanceof IEnergySource source ? source.getOfferedEnergy() : "missing");
    }
}
