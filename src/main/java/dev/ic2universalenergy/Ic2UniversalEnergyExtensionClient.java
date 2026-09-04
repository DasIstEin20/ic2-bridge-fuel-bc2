package dev.ic2universalenergy;

import dev.ic2universalenergy.client.FuelCellItemModelLoader;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Client-only registration for Forge's Mods-list Config button. */
@Mod.EventBusSubscriber(modid = Ic2UniversalEnergyExtension.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class Ic2UniversalEnergyExtensionClient
{
    private Ic2UniversalEnergyExtensionClient()
    {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event)
    {
        Ic2UniversalEnergyExtension.loadingContext().registerExtensionPoint(
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
