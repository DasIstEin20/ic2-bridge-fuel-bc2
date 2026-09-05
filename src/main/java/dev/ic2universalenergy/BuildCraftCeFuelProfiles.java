package dev.ic2universalenergy;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Known fuel settings with densities read from the installed BuildCraft registry.
 * Legacy densities are used only when the optional fuel API is unavailable.
 */
final class BuildCraftCeFuelProfiles
{
    private static final List<FuelDefinition> DEFINITIONS = List.of(
            new FuelDefinition("buildcraftenergy:oil", "Crude oil", "oil", 30.0D),
            new FuelDefinition("buildcraftenergy:oil_distilled", "Distilled oil", "oil_distilled", 37.5D),
            new FuelDefinition("buildcraftenergy:oil_heavy", "Heavy oil", "oil_heavy", 80.0D),
            new FuelDefinition("buildcraftenergy:oil_dense", "Dense oil", "oil_dense", 120.0D),
            new FuelDefinition("buildcraftenergy:fuel_gaseous", "Gaseous fuel", "fuel_gaseous", 60.0D),
            new FuelDefinition("buildcraftenergy:fuel_light", "Light fuel", "fuel_light", 90.0D),
            new FuelDefinition("buildcraftenergy:fuel_dense", "Dense fuel", "fuel_dense", 360.0D),
            new FuelDefinition("buildcraftenergy:fuel_mixed_light", "Mixed light", "fuel_mixed_light", 30.0D),
            new FuelDefinition("buildcraftenergy:fuel_mixed_heavy", "Mixed heavy", "fuel_mixed_heavy", 96.0D)
    );
    private static final Map<String, FuelDefinition> BY_ID = DEFINITIONS.stream()
            .collect(Collectors.toUnmodifiableMap(FuelDefinition::id, Function.identity()));

    private BuildCraftCeFuelProfiles()
    {
    }

    static List<FuelDefinition> definitions()
    {
        return DEFINITIONS;
    }

    static FuelDefinition definition(String fluidId)
    {
        return BY_ID.get(fluidId);
    }

    static boolean isKnown(String fluidId)
    {
        return BY_ID.containsKey(fluidId);
    }

    static boolean isEnabled(String fluidId)
    {
        BridgeConfig.BuildCraftCeFuelSettings settings = BridgeConfig.getBuildCraftCeFuelSettings(fluidId);
        return settings != null && settings.enabled.get();
    }

    static FuelRule ruleFor(String fluidId)
    {
        FuelDefinition definition = definition(fluidId);
        BridgeConfig.BuildCraftCeFuelSettings settings = BridgeConfig.getBuildCraftCeFuelSettings(fluidId);
        if (definition == null || settings == null || !settings.enabled.get())
        {
            return null;
        }

        BuildCraftFuelMode mode = settings.mode.get();
        if (mode == BuildCraftFuelMode.AUTO && !BridgeConfig.USE_BUILDCRAFT_CE_8_PROFILES.get())
        {
            return null;
        }

        double megaJoulesPerMb = liveDensity(definition);
        double euPerMb = mode == BuildCraftFuelMode.MANUAL
                ? EnergyConversionService.applyFuelBalance(
                        megaJoulesPerMb * settings.manualEuPerBuildCraftMj.get()
                )
                : EnergyConversionService.buildCraftFuelEuPerMb(megaJoulesPerMb);
        return FuelRule.fromEnergyDensity(
                BridgeConfig.BUILDCRAFT_CE_CYCLE_AMOUNT_MB.get(),
                euPerMb,
                1.0D,
                mode == BuildCraftFuelMode.MANUAL
                        ? "BuildCraft CE manual profile"
                        : "BuildCraft CE fuel profile"
        );
    }

    private static double liveDensity(FuelDefinition definition)
    {
        Object manager = BuildCraftCompatibilityResolver.findCompatibleFuelRegistryManager();
        var fluid = ForgeRegistries.FLUIDS.getValue(new ResourceLocation(definition.id()));
        if (manager != null && fluid != null)
        {
            try
            {
                ClassLoader loader = BuildCraftCeFuelProfiles.class.getClassLoader();
                Class<?> managerApi = Class.forName("buildcraft.api.fuels.IFuelManager", false, loader);
                Object fuel = managerApi.getMethod("getFuel", FluidStack.class).invoke(manager, new FluidStack(fluid, 1));
                if (fuel != null)
                {
                    Class<?> fuelApi = Class.forName("buildcraft.api.fuels.IFuel", false, loader);
                    long power = ((Number) fuelApi.getMethod("getPowerPerCycle").invoke(fuel)).longValue();
                    int ticks = ((Number) fuelApi.getMethod("getTotalBurningTime").invoke(fuel)).intValue();
                    double density = power / (double) EnergyConversionService.MICRO_MJ_PER_MJ * ticks / FluidType.BUCKET_VOLUME;
                    if (density > 0 && Double.isFinite(density))
                    {
                        return density;
                    }
                }
            }
            catch (ReflectiveOperationException | RuntimeException | LinkageError ignored)
            {
                //Keep optional/forked BuildCraft versions usable without the known fuel API.
            }
        }
        return definition.megaJoulesPerMb();
    }

    record FuelDefinition(String id, String displayName, String configKey, double megaJoulesPerMb)
    {
    }
}
