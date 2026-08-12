package dev.bcic2bridge;

import dev.bcic2bridge.client.FuelCellItemModelLoader;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Client-only registration for Forge's Mods-list Config button. */
@Mod.EventBusSubscriber(modid = BcIc2FuelBridge.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class BcIc2FuelBridgeClient
{
    private BcIc2FuelBridgeClient()
    {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event)
    {
        BcIc2FuelBridge.loadingContext().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(BridgeConfigScreen::new)
        );
    }

    @SubscribeEvent
    public static void onRegisterGeometryLoaders(ModelEvent.RegisterGeometryLoaders event)
    {
        event.register("fuel_cell", new FuelCellItemModelLoader());
    }
}
