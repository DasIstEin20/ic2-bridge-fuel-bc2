package dev.bcic2bridge;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraftforge.common.ForgeConfigSpec;

public final class BridgeConfig
{
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue AUTO_DISCOVERY;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> NAMESPACE_TOKENS;
    public static final ForgeConfigSpec.BooleanValue CLASS_PACKAGE_FALLBACK;
    public static final ForgeConfigSpec.BooleanValue LOG_SKIPPED_OIL_FUEL_IDS;

    public static final ForgeConfigSpec.IntValue REFERENCE_UNIT_VOLUME_MB;
    public static final ForgeConfigSpec.DoubleValue ENERGY_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue OIL_ENERGY_EU_PER_REFERENCE_UNIT;
    public static final ForgeConfigSpec.IntValue OIL_CYCLE_AMOUNT_MB;
    public static final ForgeConfigSpec.DoubleValue FUEL_ENERGY_EU_PER_REFERENCE_UNIT;
    public static final ForgeConfigSpec.IntValue FUEL_CYCLE_AMOUNT_MB;
    public static final ForgeConfigSpec.BooleanValue USE_BUILDCRAFT_CE_8_PROFILES;
    public static final ForgeConfigSpec.DoubleValue EU_PER_BUILDCRAFT_MJ;
    public static final ForgeConfigSpec.IntValue BUILDCRAFT_CE_CYCLE_AMOUNT_MB;

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> CUSTOM_FUEL_RULES;
    private static final Map<String, BuildCraftCeFuelSettings> BUILDCRAFT_CE_FUEL_SETTINGS;

    static
    {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        Map<String, BuildCraftCeFuelSettings> profileSettings = new LinkedHashMap<>();

        builder.comment(
                "BuildCraft x IC2 Fuel Bridge.",
                "Fuel values are server-side. Restart the server after changing this config."
        );

        builder.push("discovery");
        AUTO_DISCOVERY = builder
                .comment("Automatically register source fluids whose path contains 'oil' or 'fuel'.")
                .define("autoDiscovery", true);
        NAMESPACE_TOKENS = builder
                .comment(
                        "A fluid namespace matches when it contains one of these case-insensitive tokens.",
                        "The stock value matches namespaces such as buildcraftenergy."
                )
                .defineListAllowEmpty("namespaceTokens", () -> List.of("buildcraft"), value -> value instanceof String);
        CLASS_PACKAGE_FALLBACK = builder
                .comment("Also accept a matching oil/fuel fluid when its Java class name contains a namespace token.")
                .define("classPackageFallback", true);
        LOG_SKIPPED_OIL_FUEL_IDS = builder
                .comment("Log oil/fuel-looking registry IDs that did not match the configured BuildCraft tokens.")
                .define("logSkippedOilFuelIds", true);
        builder.pop();

        builder.push("conversion");
        REFERENCE_UNIT_VOLUME_MB = builder
                .comment(
                        "Fluid volume of one reference unit, in mB. 1000 mB is one standard Forge bucket.",
                        "The automatic oil/fuel energy values below are expressed per this volume."
                )
                .defineInRange("referenceUnitVolumeMb", 1000, 1, 10_000);
        ENERGY_MULTIPLIER = builder
                .comment(
                        "Global multiplier applied to every bridge energy conversion, including custom rules.",
                        "Use 1.0 for the configured values unchanged; for example 0.5 halves all generated EU."
                )
                .defineInRange("energyMultiplier", 1.0D, 0.000001D, 1_000_000.0D);
        OIL_ENERGY_EU_PER_REFERENCE_UNIT = builder
                .comment("Energy produced by a generic automatically discovered oil fluid for one reference unit, in EU.")
                .defineInRange("oilEnergyEuPerReferenceUnit", 16_000.0D, 0.000001D, 1_000_000_000_000.0D);
        OIL_CYCLE_AMOUNT_MB = builder
                .comment("mB consumed by IC2 in one generic oil burn cycle. This changes cycle duration, not EU per mB.")
                .defineInRange("oilCycleAmountMb", 10, 1, 10_000);
        FUEL_ENERGY_EU_PER_REFERENCE_UNIT = builder
                .comment("Energy produced by a generic automatically discovered fuel fluid for one reference unit, in EU.")
                .defineInRange("fuelEnergyEuPerReferenceUnit", 32_000.0D, 0.000001D, 1_000_000_000_000.0D);
        FUEL_CYCLE_AMOUNT_MB = builder
                .comment("mB consumed by IC2 in one generic fuel burn cycle. This changes cycle duration, not EU per mB.")
                .defineInRange("fuelCycleAmountMb", 10, 1, 10_000);

        builder.push("buildCraftCommunityEdition8");
        USE_BUILDCRAFT_CE_8_PROFILES = builder
                .comment(
                        "Use BuildCraft CE's extracted per-fluid profiles when a fuel is set to AUTO.",
                        "MANUAL per-fuel profiles remain active even when this setting is false."
                )
                .define("useBuiltInFuelProfiles", true);
        EU_PER_BUILDCRAFT_MJ = builder
                .comment(
                        "Automatic conversion from one BuildCraft MJ to EU.",
                        "There is no canonical MJ-to-EU ratio; 0.5 makes crude oil's 30 MJ/mB equal 15 EU/mB."
                )
                .defineInRange("euPerBuildCraftMj", 0.5D, 0.000001D, 1_000_000.0D);
        BUILDCRAFT_CE_CYCLE_AMOUNT_MB = builder
                .comment("mB consumed by IC2 in one burn cycle for BuildCraft CE profiles.")
                .defineInRange("cycleAmountMb", 10, 1, 10_000);

        builder.push("fuelProfiles");
        for (BuildCraftCeFuelProfiles.FuelDefinition fuel : BuildCraftCeFuelProfiles.definitions())
        {
            builder.push(fuel.configKey());
            ForgeConfigSpec.BooleanValue enabled = builder
                    .comment("Register " + fuel.id() + " as IC2 semifluid-generator fuel.")
                    .define("enabled", true);
            ForgeConfigSpec.EnumValue<BuildCraftFuelMode> mode = builder
                    .comment(
                            "AUTO uses the BuildCraft CE energy density with the global euPerBuildCraftMj value.",
                            "MANUAL uses manualEuPerBuildCraftMj for this fuel only."
                    )
                    .defineEnum("mode", BuildCraftFuelMode.AUTO);
            ForgeConfigSpec.DoubleValue manualEuPerMj = builder
                    .comment("MJ-to-EU conversion used for this fuel only when mode = MANUAL.")
                    .defineInRange("manualEuPerBuildCraftMj", 0.5D, 0.000001D, 1_000_000.0D);
            profileSettings.put(fuel.id(), new BuildCraftCeFuelSettings(enabled, mode, manualEuPerMj));
            builder.pop();
        }
        builder.pop();
        builder.pop();
        builder.pop();

        builder.push("overrides");
        CUSTOM_FUEL_RULES = builder
                .comment(
                        "Exact fluid rules. They override automatic bridge values and may target fluids from any mod.",
                        "Syntax: namespace:path;energyEuPerReferenceUnit;referenceUnitVolumeMb;cycleAmountMb",
                        "Effective EU/mB is energyEuPerReferenceUnit / referenceUnitVolumeMb, then energyMultiplier is applied.",
                        "Known BuildCraft CE fuels disabled under conversion.buildCraftCommunityEdition8.fuelProfiles remain disabled.",
                        "IC2 rules cannot be replaced safely after startup; restart the server after changing a rule."
                )
                .defineListAllowEmpty("customFuelRules", List::of, value -> value instanceof String);
        builder.pop();

        BUILDCRAFT_CE_FUEL_SETTINGS = Map.copyOf(profileSettings);
        SPEC = builder.build();
    }

    static BuildCraftCeFuelSettings getBuildCraftCeFuelSettings(String fluidId)
    {
        return BUILDCRAFT_CE_FUEL_SETTINGS.get(fluidId);
    }

    static Map<String, BuildCraftCeFuelSettings> getBuildCraftCeFuelSettings()
    {
        return BUILDCRAFT_CE_FUEL_SETTINGS;
    }

    static final class BuildCraftCeFuelSettings
    {
        final ForgeConfigSpec.BooleanValue enabled;
        final ForgeConfigSpec.EnumValue<BuildCraftFuelMode> mode;
        final ForgeConfigSpec.DoubleValue manualEuPerBuildCraftMj;

        private BuildCraftCeFuelSettings(
                ForgeConfigSpec.BooleanValue enabled,
                ForgeConfigSpec.EnumValue<BuildCraftFuelMode> mode,
                ForgeConfigSpec.DoubleValue manualEuPerBuildCraftMj
        )
        {
            this.enabled = enabled;
            this.mode = mode;
            this.manualEuPerBuildCraftMj = manualEuPerBuildCraftMj;
        }
    }

    private BridgeConfig()
    {
    }
}

enum BuildCraftFuelMode
{
    AUTO,
    MANUAL
}
