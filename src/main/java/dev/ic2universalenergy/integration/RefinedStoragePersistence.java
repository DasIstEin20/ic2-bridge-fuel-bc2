package dev.ic2universalenergy.integration;

import com.refinedmods.refinedstorage.api.IRSAPI;
import com.refinedmods.refinedstorage.api.RSAPIInject;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Optional public-API save notification; never transfers or converts energy. */
public final class RefinedStoragePersistence {
    @RSAPIInject
    public static IRSAPI api;

    private RefinedStoragePersistence() {
    }

    public static void markEnergyChanged(BlockEntity owner) {
        if (api != null && owner.getLevel() instanceof ServerLevel level) {
            var manager = api.getNetworkManager(level);
            //RS keeps controller energy in world SavedData, not block-entity NBT.
            if (manager.getNetwork(owner.getBlockPos()) != null) {
                manager.markForSaving();
            }
        }
    }
}
