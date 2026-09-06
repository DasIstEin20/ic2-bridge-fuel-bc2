package dev.ic2universalenergy;

import com.refinedmods.refinedstorage.api.network.INetworkManager;
import dev.ic2universalenergy.integration.RefinedStoragePersistence;
import mekanism.api.Action;
import mekanism.api.math.FloatingLong;
import mekanism.common.integration.energy.forgeenergy.ForgeEnergyCompat;
import mekanism.common.integration.energy.forgeenergy.ForgeStrictEnergyHandler;
import mekanism.common.util.UnitDisplayUtils.EnergyUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
@GameTestHolder("ic2universalenergy")
public class ForgeEnergyPersistenceTest {
    private static final BlockPos MACHINE = new BlockPos(1, 2, 1);

    @GameTest(template = "transmitter/straight_3c_cable", batch = "fe_persistence")
    public static void refinedStorageEnergyChangesMarkReceiverChunk(GameTestHelper helper) {
        BridgeTestSupport.place(helper, MACHINE, "refinedstorage:controller");
        helper.startSequence().thenIdle(5).thenExecute(() -> {
            helper.assertTrue(RefinedStoragePersistence.api != null, "Public RS API was not injected");
            SavedData data = (SavedData) RefinedStoragePersistence.api.getNetworkManager(helper.getLevel());
            assertReceiverPersistence(helper, MACHINE, data);
        }).thenSucceed();
    }

    static void assertReceiverPersistence(GameTestHelper helper, BlockPos relative) {
        assertReceiverPersistence(helper, relative, null);
    }

    private static void assertReceiverPersistence(GameTestHelper helper, BlockPos relative, SavedData externalData) {
        BlockEntity owner = helper.getBlockEntity(relative);
        var storage = owner.getCapability(ForgeCapabilities.ENERGY, Direction.WEST)
              .orElseThrow(() -> new IllegalStateException("Missing receiver FE capability"));
        var handler = new ForgeEnergyCompat().getLazyStrictEnergyHandler(owner, Direction.WEST)
              .orElseThrow(() -> new IllegalStateException("Missing wrapped FE capability"));
        var chunk = helper.getLevel().getChunkAt(owner.getBlockPos());
        FloatingLong amount = EnergyUnit.FORGE_ENERGY.convertFrom(1000);
        int before = storage.getEnergyStored();
        try {
            chunk.setUnsaved(false);
            if (externalData != null) externalData.setDirty(false);
            helper.assertTrue(handler.insertEnergy(amount, Action.SIMULATE).isZero(), "Receiver rejected simulated FE");
            helper.assertTrue(storage.getEnergyStored() == before && !chunk.isUnsaved(), "Simulation changed energy or dirtied chunk");
            helper.assertTrue(externalData == null || !externalData.isDirty(), "Simulation dirtied world SavedData");
            helper.assertTrue(handler.insertEnergy(amount, Action.EXECUTE).isZero(), "Receiver rejected actual FE");
            helper.assertTrue(storage.getEnergyStored() == before + 1000, "FE insertion amount changed");
            helper.assertTrue(chunk.isUnsaved(), "FE receiver chunk was not marked for saving");

            if (externalData == null) {
                BlockEntity restored = BlockEntity.loadStatic(owner.getBlockPos(), owner.getBlockState(), owner.saveWithFullMetadata());
                helper.assertTrue(restored != null, "Receiver could not be restored from NBT");
                restored.setLevel(helper.getLevel());
                var restoredEnergy = restored.getCapability(ForgeCapabilities.ENERGY, Direction.WEST)
                      .orElseThrow(() -> new IllegalStateException("Missing restored FE capability"));
                helper.assertTrue(restoredEnergy.getEnergyStored() == before + 1000, "Receiver NBT did not preserve FE");
                restored.invalidateCaps();
            } else {
                helper.assertTrue(externalData.isDirty(), "RS energy change did not mark world SavedData");
                //Independent manager: recreating only the block entity at the same
                //position would accidentally read the still-live original network.
                try {
                    var restored = externalData.getClass().getConstructor(Level.class).newInstance(helper.getLevel());
                    externalData.getClass().getMethod("load", CompoundTag.class)
                          .invoke(restored, externalData.save(new CompoundTag()));
                    var network = ((INetworkManager) restored).getNetwork(owner.getBlockPos());
                    helper.assertTrue(network != null && network.getEnergyStorage().getEnergyStored() == before + 1000,
                          "RS SavedData did not preserve FE independently of the live manager");
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException("Cannot round-trip RS test SavedData", exception);
                }
            }
        } finally {
            chunk.setUnsaved(true);
            if (externalData != null) externalData.setDirty();
        }
    }

    @GameTest(template = "transmitter/straight_3c_cable", batch = "fe_persistence")
    public static void onlyActualNonzeroEnergyChangesDirtyLoadedOwners(GameTestHelper helper) {
        helper.setBlock(MACHINE, Blocks.CHEST);
        BlockEntity owner = helper.getBlockEntity(MACHINE);
        var chunk = helper.getLevel().getChunkAt(owner.getBlockPos());
        var storage = new EnergyStorage(2000, 1000, 1000, 1000);
        var handler = new ForgeStrictEnergyHandler(storage, owner);
        FloatingLong amount = EnergyUnit.FORGE_ENERGY.convertFrom(500);
        try {
            chunk.setUnsaved(false);
            handler.insertEnergy(0, amount, Action.SIMULATE);
            handler.extractEnergy(0, amount, Action.SIMULATE);
            handler.insertEnergy(0, FloatingLong.ZERO, Action.EXECUTE);
            handler.extractEnergy(0, FloatingLong.ZERO, Action.EXECUTE);
            handler.insertEnergy(1, amount, Action.EXECUTE);
            handler.extractEnergy(1, amount, Action.EXECUTE);
            helper.assertTrue(storage.getEnergyStored() == 1000 && !chunk.isUnsaved(), "Read-only/no-op transfer changed owner");

            handler.extractEnergy(0, amount, Action.EXECUTE);
            helper.assertTrue(storage.getEnergyStored() == 500 && chunk.isUnsaved(), "Extraction was not persisted");
            chunk.setUnsaved(false);
            handler.insertEnergy(0, amount, Action.EXECUTE);
            helper.assertTrue(storage.getEnergyStored() == 1000 && chunk.isUnsaved(), "Insertion was not persisted");

            storage.receiveEnergy(1000, false);
            chunk.setUnsaved(false);
            helper.assertTrue(handler.insertEnergy(0, amount, Action.EXECUTE).equals(amount), "Full buffer accepted FE");
            helper.assertTrue(!chunk.isUnsaved(), "Rejected insertion dirtied chunk");
            storage.extractEnergy(1000, false);
            storage.extractEnergy(1000, false);
            handler.extractEnergy(0, amount, Action.EXECUTE);
            helper.assertTrue(!chunk.isUnsaved(), "Empty extraction dirtied chunk");

            new ForgeStrictEnergyHandler(storage).insertEnergy(0, amount, Action.EXECUTE);
            helper.assertTrue(storage.getEnergyStored() == 500 && !chunk.isUnsaved(), "Ownerless FE provider was not preserved");
            owner.setRemoved();
            handler.insertEnergy(0, amount, Action.EXECUTE);
            helper.assertTrue(!chunk.isUnsaved(), "Removed owner's chunk was dirtied");
        } finally {
            owner.clearRemoved();
            chunk.setUnsaved(true);
        }
        helper.succeed();
    }
}
