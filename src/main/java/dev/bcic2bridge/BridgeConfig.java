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
    public static final ForgeConfigSpec.IntValue BUILDCRAFT_CE_CYCLE_AMOUNT_MB;

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> CUSTOM_FUEL_RULES;
    public static final ForgeConfigSpec.BooleanValue FUEL_BRIDGE_BC_TO_IC2_ENABLED;
    public static final ForgeConfigSpec.BooleanValue FUEL_BRIDGE_IC2_TO_BC_ENABLED;
    public static final ForgeConfigSpec.BooleanValue IC2_TO_BC_AUTO_DISCOVERY;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> IC2_TO_BC_NAMESPACE_TOKENS;
    public static final ForgeConfigSpec.IntValue IC2_TO_BC_BURN_TIME_TICKS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> IC2_TO_BC_CUSTOM_FUEL_RULES;

    public static final ForgeConfigSpec.BooleanValue ENERGY_BRIDGE_ENABLED;
    public static final ForgeConfigSpec.EnumValue<EnergyConversionMode> ENERGY_CONVERSION_MODE;
    public static final ForgeConfigSpec.DoubleValue EU_PER_BUILDCRAFT_MJ;
    public static final ForgeConfigSpec.EnumValue<EnergyTransferLimitMode> ENERGY_TRANSFER_LIMIT_MODE;
    public static final ForgeConfigSpec.DoubleValue ENERGY_TRANSFER_LIMIT_EU_PER_TICK;
    private static final Map<String, BuildCraftCeFuelSettings> BUILDCRAFT_CE_FUEL_SETTINGS;

    static
    {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        Map<String, BuildCraftCeFuelSettings> profileSettings = new LinkedHashMap<>();

        builder.comment(
                "BuildCraft x IC2 Bridge.",
                "All values are server-side. Restart the server after changing fuel registration settings."
        );

        builder.push("energy");
        ENERGY_BRIDGE_ENABLED = builder
                .comment("Allow IC2 cables and emitters to power any BuildCraft block exposing an MJ receiver.")
                .define("ic2ToBuildCraftEnabled", true);
        ENERGY_CONVERSION_MODE = builder
                .comment(
                        "AUTO uses the bridge default of " + EnergyConversionService.AUTO_EU_PER_MJ + " EU/MJ.",
                        "MANUAL uses manualEuPerBuildCraftMj below. This one central ratio is used by the energy and both fuel bridges."
                )
                .defineEnum("conversionMode", EnergyConversionMode.AUTO);
        EU_PER_BUILDCRAFT_MJ = builder
                .comment("EU per BuildCraft MJ when conversionMode = MANUAL.")
                .defineInRange("manualEuPerBuildCraftMj", EnergyConversionService.AUTO_EU_PER_MJ, 0.000001D, 1_000_000.0D);
        ENERGY_TRANSFER_LIMIT_MODE = builder
                .comment("AUTO lets the MJ receiver request its own rate. MANUAL caps each bridged receiver in EU/t.")
                .defineEnum("transferLimitMode", EnergyTransferLimitMode.AUTO);
        ENERGY_TRANSFER_LIMIT_EU_PER_TICK = builder
                .comment("Per-receiver EU/t cap used only when transferLimitMode = MANUAL.")
                .defineInRange("manualTransferLimitEuPerTick", 128.0D, 0.000001D, 1_000_000_000.0D);
        builder.pop();

        builder.push("fuels");
        builder.push("buildCraftToIc2");
        FUEL_BRIDGE_BC_TO_IC2_ENABLED = builder
                .comment("Register BuildCraft fuels as IC2 semifluid-generator fuels.")
                .define("enabled", true);

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

        builder.push("balance");
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
                            "AUTO uses the BuildCraft CE energy density with the central energy conversion setting.",
                            "MANUAL uses manualEuPerBuildCraftMj for this fuel only."
                    )
                    .defineEnum("mode", BuildCraftFuelMode.AUTO);
            ForgeConfigSpec.DoubleValue manualEuPerMj = builder
                    .comment("MJ-to-EU conversion used for this fuel only when mode = MANUAL.")
                    .defineInRange("manualEuPerBuildCraftMj", EnergyConversionService.AUTO_EU_PER_MJ, 0.000001D, 1_000_000.0D);
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
            "Known BuildCraft CE fuels disabled under fuels.buildCraftToIc2.balance.buildCraftCommunityEdition8.fuelProfiles remain disabled.",
                        "IC2 rules cannot be replaced safely after startup; restart the server after changing a rule."
                )
                .defineListAllowEmpty("customFuelRules", List::of, value -> value instanceof String);
        builder.pop();

        builder.pop();

        builder.push("ic2ToBuildCraft");
        FUEL_BRIDGE_IC2_TO_BC_ENABLED = builder
                .comment("Register IC2 semifluid-generator fuels in BuildCraft's combustion-fuel registry.")
                .define("enabled", true);
        IC2_TO_BC_AUTO_DISCOVERY = builder
                .comment("Use IC2's registered semifluid-generator fuels as the automatic source for BuildCraft fuels.")
                .define("autoDiscovery", true);
        IC2_TO_BC_NAMESPACE_TOKENS = builder
                .comment("Only accepted IC2 fluids whose namespace contains one of these tokens are imported automatically.")
                .defineListAllowEmpty("namespaceTokens", () -> List.of("ic2"), value -> value instanceof String);
        IC2_TO_BC_BURN_TIME_TICKS = builder
                .comment("Ticks for which BuildCraft burns one mB of an automatically imported IC2 fuel.")
                .defineInRange("burnTimeTicks", 10, 1, 2_000_000);
        IC2_TO_BC_CUSTOM_FUEL_RULES = builder
                .comment(
                        "Exact IC2-to-BuildCraft rules. They override automatic IC2 semifluid values.",
                        "Syntax: namespace:path;energyEuPerMb;burnTimeTicks"
                )
                .defineListAllowEmpty("customFuelRules", List::of, value -> value instanceof String);
        builder.pop();
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
