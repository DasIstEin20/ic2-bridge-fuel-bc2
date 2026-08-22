package dev.bcic2bridge;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(BcIc2FuelBridge.MOD_ID)
public final class BcIc2FuelBridge
{
    public static final String MOD_ID = "bcic2fuelbridge";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static FMLJavaModLoadingContext loadingContext;
    private final Ic2ToBuildCraftEnergyBridge ic2ToBuildCraftEnergyBridge = new Ic2ToBuildCraftEnergyBridge();
    private final BuildCraftToIc2EnergyBridge buildCraftToIc2EnergyBridge = new BuildCraftToIc2EnergyBridge();
    private final Ic2ToForgeEnergyBridge ic2ToForgeEnergyBridge = new Ic2ToForgeEnergyBridge();
    private final ForestryEnergyBridge forestryEnergyBridge = new ForestryEnergyBridge();

    public BcIc2FuelBridge(FMLJavaModLoadingContext context)
    {
        loadingContext = context;
        // Fuel values affect game balance, so keep them with the saved world/server
        // instead of in every player's local common config.
        context.registerConfig(ModConfig.Type.SERVER, BridgeConfig.SPEC);

        IEventBus modEventBus = context.getModEventBus();
        BuildCraftCompatibilityResolver.Snapshot compatibility = BuildCraftCompatibilityResolver.resolve();
        BuildCraftCompatibilityResolver.logCompatibility(compatibility);
        if ("buildcraftenergy".equals(compatibility.modId()))
        {
            BridgeFuelCells.register(modEventBus);
        }
        else if (compatibility.detected())
        {
            LOGGER.info("BuildCraft Energy module was not detected; skipping the optional pre-filled BC fuel-cell items.");
        }
        modEventBus.addListener(this::onCommonSetup);
        modEventBus.addListener(this::onLoadComplete);

        MinecraftForge.EVENT_BUS.addListener(this::onServerAboutToStart);
        if (compatibility.detected())
        {
            this.ic2ToBuildCraftEnergyBridge.register();
            this.buildCraftToIc2EnergyBridge.register();
        }
        this.ic2ToForgeEnergyBridge.register();
        LOGGER.info("The IC2 → Forge Energy bridge is active for FE receivers, including future compatible mods.");
        if (ForestryEnergyBridge.isForestryInstalled())
        {
            this.forestryEnergyBridge.register();
            LOGGER.info("Forestry detected. The bidirectional Forestry Forge Energy ↔ IC2 bridge is active.");
        }
        if (compatibility.detected() && !compatibility.energyBridge() && !compatibility.buildCraftToIc2EnergyBridge())
        {
            LOGGER.info("BuildCraft MJ receiver/provider APIs were not detected yet; the energy bridges will keep probing endpoints while fuel features remain available.");
        }
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
        BuildCraftCompatibilityResolver.Snapshot compatibility = BuildCraftCompatibilityResolver.resolve();
        BuildCraftCompatibilityResolver.logCompatibility(compatibility);

        if (compatibility.buildCraftToIc2Fuels())
        {
            Ic2FuelRegistrar.registerAll(phase);
        }
        else
        {
            LOGGER.debug("BuildCraft → IC2 fuel bridge is unavailable during {}: no BuildCraft fluids were found in Forge's registry.", phase);
        }

        if (compatibility.ic2ToBuildCraftFuels())
        {
            Ic2ToBuildCraftFuelRegistrar.registerAll(phase);
        }
        else
        {
            LOGGER.debug("IC2 → BuildCraft fuel bridge is unavailable during {}: no compatible BuildCraft fuel registry was found.", phase);
        }
    }
}
