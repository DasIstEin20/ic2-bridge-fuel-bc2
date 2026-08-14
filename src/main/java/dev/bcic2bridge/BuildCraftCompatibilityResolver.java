package dev.bcic2bridge;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Detects the BuildCraft API that is actually present instead of trusting a
 * version range declared in {@code mods.toml}.
 *
 * <p>BuildCraft is intentionally an optional dependency. The resolver first
 * probes public classes, capabilities, registries and methods. Version strings
 * are used only to name a known adapter in diagnostics; a new, forked or
 * otherwise unknown version is still given the generic reflection path.</p>
 */
public final class BuildCraftCompatibilityResolver
{
    private static final Logger LOGGER = LoggerFactory.getLogger(BcIc2FuelBridge.MOD_ID);
    private static final String MJ_API = "buildcraft.api.mj.MjAPI";
    private static final String MJ_RECEIVER = "buildcraft.api.mj.IMjReceiver";
    private static final String[] FUEL_REGISTRY_CLASSES = {
            "buildcraft.api.fuels.BuildcraftFuelRegistry",
            "buildcraft.api.fuels.BcFuelRegistry"
    };

    private static volatile Snapshot latest = Snapshot.notDetected();
    private static volatile String lastLoggedSummary = "";

    private BuildCraftCompatibilityResolver()
    {
    }

    /**
     * Re-probes the loaded environment. Calling this after registries become
     * available makes the fuel-discovery state in the config GUI accurate.
     */
    public static Snapshot resolve()
    {
        Snapshot snapshot = detect();
        latest = snapshot;
        return snapshot;
    }

    public static Snapshot latest()
    {
        return latest;
    }

    /**
     * Returns the first BuildCraft combustion-fuel manager whose public method
     * shape is usable by the bridge. This is deliberately field-name agnostic
     * so a fork can rename {@code BuildcraftFuelRegistry.fuel} without making
     * the rest of the compatibility check lie.
     */
    public static Object findCompatibleFuelRegistryManager()
    {
        FuelRegistryAccess access = findFuelRegistryAccess();
        return access == null ? null : access.manager();
    }

    /** Logs a compact, actionable summary once per changed compatibility state. */
    public static void logCompatibility(Snapshot snapshot)
    {
        String summary = snapshot.summary();
        if (summary.equals(lastLoggedSummary))
        {
            return;
        }
        lastLoggedSummary = summary;

        if (!snapshot.detected())
        {
            LOGGER.info("BuildCraft was not detected. The bridge will remain loaded and its BuildCraft features are inactive.");
            return;
        }

        LOGGER.info(
                "BuildCraft detected: {} {}. Adapter: {}; energy={}, BC→IC2 fuels={}, IC2→BC fuels={}, fluid discovery={}.",
                snapshot.modId(),
                snapshot.version(),
                snapshot.adapter().displayName(),
                availability(snapshot.energyBridge()),
                availability(snapshot.buildCraftToIc2Fuels()),
                availability(snapshot.ic2ToBuildCraftFuels()),
                availability(snapshot.fluidDiscovery())
        );

        if (snapshot.bestEffort())
        {
            LOGGER.warn(
                    "BuildCraft {} is not a specifically known release. Using Generic / BEST_EFFORT compatibility based on the detected API; unavailable features are disabled independently and startup will continue.",
                    snapshot.version()
            );
        }
        else if (!snapshot.allFeaturesAvailable())
        {
            LOGGER.warn(
                    "BuildCraft {} exposes only part of the bridge API. The unavailable features are disabled independently; inspect Mods → BuildCraft x IC2 Bridge → Compatibility for details.",
                    snapshot.version()
            );
        }
    }

    private static Snapshot detect()
    {
        DetectedBuildCraft buildCraft = findBuildCraft();
        if (buildCraft == null)
        {
            return Snapshot.notDetected();
        }

        Probe probe = probeApi();
        Adapter adapter = selectAdapter(buildCraft.version(), probe);
        return new Snapshot(
                true,
                buildCraft.modId(),
                buildCraft.version(),
                adapter,
                probe.energyBridge(),
                probe.buildCraftToIc2Fuels(),
                probe.ic2ToBuildCraftFuels(),
                probe.discoveredBuildCraftFluids()
        );
    }

    private static DetectedBuildCraft findBuildCraft()
    {
        try
        {
            List<IModInfo> candidates = new ArrayList<>();
            for (IModInfo info : ModList.get().getMods())
            {
                String id = info.getModId().toLowerCase(Locale.ROOT);
                if (id.startsWith("buildcraft") || id.contains("buildcraft"))
                {
                    candidates.add(info);
                }
            }

            if (candidates.isEmpty())
            {
                return null;
            }

            candidates.sort(Comparator.comparingInt(BuildCraftCompatibilityResolver::modulePriority));
            IModInfo selected = candidates.get(0);
            return new DetectedBuildCraft(selected.getModId(), selected.getVersion().toString());
        }
        catch (RuntimeException exception)
        {
            // During an unusual early lifecycle phase ModList can be incomplete.
            // Treat that like no BuildCraft for this pass; later lifecycle passes
            // intentionally call resolve() again.
            LOGGER.debug("BuildCraft mod discovery is not ready yet: {}", exception.toString());
            return null;
        }
    }

    private static int modulePriority(IModInfo info)
    {
        return switch (info.getModId().toLowerCase(Locale.ROOT))
        {
            case "buildcraftenergy" -> 0;
            case "buildcraftcore" -> 1;
            case "buildcraftlib" -> 2;
            default -> 3;
        };
    }

    private static Probe probeApi()
    {
        boolean energyBridge = hasMjReceiverApi();
        FuelRegistryProbe fuelRegistry = findFuelRegistry();
        int buildCraftFluidCount = countBuildCraftFluids();
        boolean fluidDiscovery = buildCraftFluidCount > 0;

        // BuildCraft → IC2 needs no BC implementation class at all. Its safe
        // generic fallback is Forge's fluid registry, scoped to detected
        // BuildCraft fluids. This stays useful even where BC's energy API moved.
        boolean buildCraftToIc2Fuels = fluidDiscovery;
        boolean ic2ToBuildCraftFuels = fuelRegistry.supported();
        return new Probe(energyBridge, buildCraftToIc2Fuels, ic2ToBuildCraftFuels, buildCraftFluidCount, fuelRegistry);
    }

    private static boolean hasMjReceiverApi()
    {
        try
        {
            ClassLoader loader = BuildCraftCompatibilityResolver.class.getClassLoader();
            Class<?> api = Class.forName(MJ_API, false, loader);
            Field capability = api.getField("CAP_RECEIVER");
            if (!Modifier.isStatic(capability.getModifiers()) || capability.get(null) == null)
            {
                return false;
            }

            Class<?> receiver = Class.forName(MJ_RECEIVER, false, loader);
            receiver.getMethod("getPowerRequested");
            receiver.getMethod("canReceive");
            receiver.getMethod("receivePower", long.class, FluidAction.class);
            return true;
        }
        catch (ReflectiveOperationException | LinkageError exception)
        {
            return false;
        }
    }

    private static FuelRegistryProbe findFuelRegistry()
    {
        FuelRegistryAccess access = findFuelRegistryAccess();
        return access == null
                ? FuelRegistryProbe.unavailable()
                : new FuelRegistryProbe(true, access.className(), access.fieldName());
    }

    private static FuelRegistryAccess findFuelRegistryAccess()
    {
        for (String className : FUEL_REGISTRY_CLASSES)
        {
            try
            {
                Class<?> registry = Class.forName(className, false, BuildCraftCompatibilityResolver.class.getClassLoader());
                for (Field field : registry.getFields())
                {
                    if (!Modifier.isStatic(field.getModifiers()))
                    {
                        continue;
                    }
                    Object manager = field.get(null);
                    if (manager == null)
                    {
                        continue;
                    }
                    Method getFuel = findMethod(manager.getClass(), "getFuel", 1);
                    Method addFuel = findAddFuelMethod(manager.getClass());
                    if (getFuel != null && addFuel != null)
                    {
                        return new FuelRegistryAccess(manager, className, field.getName());
                    }
                }
            }
            catch (ReflectiveOperationException | LinkageError exception)
            {
                // Keep trying the other known names. A missing registry only
                // disables IC2 → BC fuels, never the rest of the bridge.
            }
        }
        return null;
    }

    private static Method findMethod(Class<?> type, String name, int parameterCount)
    {
        for (Method method : type.getMethods())
        {
            if (name.equals(method.getName()) && method.getParameterCount() == parameterCount)
            {
                return method;
            }
        }
        return null;
    }

    private static Method findAddFuelMethod(Class<?> type)
    {
        for (Method method : type.getMethods())
        {
            if (!"addFuel".equals(method.getName()) || method.getParameterCount() != 3)
            {
                continue;
            }
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters[0].isAssignableFrom(Fluid.class)
                    && isIntegral(parameters[1]) && isIntegral(parameters[2]))
            {
                return method;
            }
        }
        return null;
    }

    private static boolean isIntegral(Class<?> type)
    {
        return type == byte.class || type == Byte.class || type == short.class || type == Short.class
                || type == int.class || type == Integer.class || type == long.class || type == Long.class;
    }

    private static int countBuildCraftFluids()
    {
        try
        {
            return (int) ForgeRegistries.FLUIDS.getKeys().stream()
                    .filter(id -> id.getNamespace().toLowerCase(Locale.ROOT).contains("buildcraft"))
                    .count();
        }
        catch (RuntimeException exception)
        {
            return 0;
        }
    }

    private static Adapter selectAdapter(String version, Probe probe)
    {
        // Do not classify a release only by its number. The known labels are
        // awarded only after the corresponding public API was actually found.
        if (probe.sharedCeApi())
        {
            if (version.startsWith("7.99."))
            {
                return Adapter.BC_7_99;
            }
            if (version.startsWith("8."))
            {
                return Adapter.BC_8;
            }
        }
        return Adapter.GENERIC;
    }

    private static String availability(boolean available)
    {
        return available ? "available" : "unavailable";
    }

    public enum Adapter
    {
        NONE("Not detected", false),
        BC_8("BuildCraft CE 8.x API", false),
        BC_7_99("BuildCraft CE 7.99 API", false),
        GENERIC("Generic reflection fallback", true);

        private final String displayName;
        private final boolean bestEffort;

        Adapter(String displayName, boolean bestEffort)
        {
            this.displayName = displayName;
            this.bestEffort = bestEffort;
        }

        public String displayName()
        {
            return this.displayName;
        }

        public boolean bestEffort()
        {
            return this.bestEffort;
        }
    }

    /** Immutable compatibility state, safe to use from lifecycle code and the config GUI. */
    public record Snapshot(
            boolean detected,
            String modId,
            String version,
            Adapter adapter,
            boolean energyBridge,
            boolean buildCraftToIc2Fuels,
            boolean ic2ToBuildCraftFuels,
            int discoveredBuildCraftFluids
    )
    {
        private static Snapshot notDetected()
        {
            return new Snapshot(false, "—", "—", Adapter.NONE, false, false, false, 0);
        }

        public boolean fluidDiscovery()
        {
            return this.discoveredBuildCraftFluids > 0;
        }

        public boolean bestEffort()
        {
            return this.adapter.bestEffort();
        }

        public boolean allFeaturesAvailable()
        {
            return this.energyBridge && this.buildCraftToIc2Fuels && this.ic2ToBuildCraftFuels && this.fluidDiscovery();
        }

        public String modeName()
        {
            if (!this.detected)
            {
                return "INACTIVE";
            }
            return this.bestEffort() ? "BEST EFFORT" : "KNOWN API";
        }

        private String summary()
        {
            return this.detected + ":" + this.modId + ":" + this.version + ":" + this.adapter
                    + ":" + this.energyBridge + ":" + this.buildCraftToIc2Fuels + ":" + this.ic2ToBuildCraftFuels
                    + ":" + this.discoveredBuildCraftFluids;
        }
    }

    private record DetectedBuildCraft(String modId, String version)
    {
    }

    private record Probe(
            boolean energyBridge,
            boolean buildCraftToIc2Fuels,
            boolean ic2ToBuildCraftFuels,
            int discoveredBuildCraftFluids,
            FuelRegistryProbe fuelRegistry
    )
    {
        private boolean sharedCeApi()
        {
            return this.energyBridge || this.fuelRegistry.supported();
        }
    }

    private record FuelRegistryProbe(boolean supported, String className, String fieldName)
    {
        private static FuelRegistryProbe unavailable()
        {
            return new FuelRegistryProbe(false, "", "");
        }
    }

    private record FuelRegistryAccess(Object manager, String className, String fieldName)
    {
    }
}
