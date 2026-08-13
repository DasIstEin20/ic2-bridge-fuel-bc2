package dev.bcic2bridge;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(BcIc2FuelBridge.MOD_ID)
public final class BcIc2FuelBridge
{
    public static final String MOD_ID = "bcic2fuelbridge";
    private static FMLJavaModLoadingContext loadingContext;

    public BcIc2FuelBridge(FMLJavaModLoadingContext context)
    {
        loadingContext = context;
        // Fuel values affect game balance, so keep them with the saved world/server
        // instead of in every player's local common config.
        context.registerConfig(ModConfig.Type.SERVER, BridgeConfig.SPEC);

        IEventBus modEventBus = context.getModEventBus();
        BridgeFuelCells.register(modEventBus);
        modEventBus.addListener(this::onCommonSetup);
        modEventBus.addListener(this::onLoadComplete);

        MinecraftForge.EVENT_BUS.addListener(this::onServerAboutToStart);
        new Ic2ToBuildCraftEnergyBridge().register();
    }

    static FMLJavaModLoadingContext loadingContext()
    {
        if (loadingContext == null)
        {
            throw new IllegalStateException("Forge loading context is not available yet.");
        }
        return loadingContext;
    }

    private void onCommonSetup(FMLCommonSetupEvent event)
    {
        event.enqueueWork(() -> this.registerFuelBridges("common setup"));
    }

    private void onLoadComplete(FMLLoadCompleteEvent event)
    {
        event.enqueueWork(() -> this.registerFuelBridges("load complete"));
    }

    private void onServerAboutToStart(ServerAboutToStartEvent event)
    {
        this.registerFuelBridges("server about to start");
    }

    private void registerFuelBridges(String phase)
    {
        Ic2FuelRegistrar.registerAll(phase);
        Ic2ToBuildCraftFuelRegistrar.registerAll(phase);
    }
}
