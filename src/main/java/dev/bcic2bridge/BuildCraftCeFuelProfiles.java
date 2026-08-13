package dev.bcic2bridge;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Fuel energy densities from BuildCraft Community Edition 8.0.13+1.20.1.
 *
 * <p>The values are derived from its {@code BCEnergyRecipes}: for each fuel,
 * {@code powerPerCycle * totalBurningTime / 1000}. BuildCraft stores power in
 * micro-MJ, and its combustion engine consumes one mB at a time, hence the
 * resulting values are MJ/mB.</p>
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

        double euPerMb = mode == BuildCraftFuelMode.MANUAL
                ? EnergyConversionService.applyFuelBalance(
                        definition.megaJoulesPerMb() * settings.manualEuPerBuildCraftMj.get()
                )
                : EnergyConversionService.buildCraftFuelEuPerMb(definition.megaJoulesPerMb());
        return FuelRule.fromEnergyDensity(
                BridgeConfig.BUILDCRAFT_CE_CYCLE_AMOUNT_MB.get(),
                euPerMb,
                1.0D,
                mode == BuildCraftFuelMode.MANUAL
                        ? "BuildCraft CE manual profile"
                        : "BuildCraft CE 8.0.13 profile"
        );
    }

    record FuelDefinition(String id, String displayName, String configKey, double megaJoulesPerMb)
    {
    }
}
