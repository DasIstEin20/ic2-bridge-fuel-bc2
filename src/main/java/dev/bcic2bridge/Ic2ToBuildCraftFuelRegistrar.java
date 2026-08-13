package dev.bcic2bridge;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Imports IC2 semifluid-generator fuels into BuildCraft CE's shared combustion
 * fuel registry. The registry, rather than a specific engine tile, is the
 * extension point used by every BuildCraft combustion consumer.
 */
public final class Ic2ToBuildCraftFuelRegistrar
{
    private static final Logger LOGGER = LoggerFactory.getLogger(BcIc2FuelBridge.MOD_ID);
    private static final String IC2_RECIPES = "ic2.api.recipe.Recipes";
    private static final String BC_FUEL_REGISTRY = "buildcraft.api.fuels.BuildcraftFuelRegistry";

    private Ic2ToBuildCraftFuelRegistrar()
    {
    }

    public static synchronized RegistrationResult registerAll(String phase)
    {
        if (!BridgeConfig.FUEL_BRIDGE_IC2_TO_BC_ENABLED.get())
        {
            LOGGER.info("IC2 → BuildCraft fuel bridge is disabled; skipping registration during {}.", phase);
            return new RegistrationResult(0, 0, false);
        }

        try
        {
            Object ic2Manager = getStaticField(IC2_RECIPES, "semiFluidGenerator");
            Object bcManager = getStaticField(BC_FUEL_REGISTRY, "fuel");
            if (ic2Manager == null || bcManager == null)
            {
                LOGGER.debug(
                        "IC2 → BuildCraft fuel bridge is waiting for registries during {} (IC2 manager: {}, BC manager: {}).",
                        phase,
                        ic2Manager != null,
                        bcManager != null
                );
                return new RegistrationResult(0, 0, false);
            }

            Method getBurnProperty = findMethod(ic2Manager.getClass(), "getBurnProperty", 1);
            Method getFuel = findMethod(bcManager.getClass(), "getFuel", 1);
            Method addFuel = findAddFuelMethod(bcManager.getClass());
            if (getBurnProperty == null || getFuel == null || addFuel == null)
            {
                LOGGER.error("Could not find the required IC2/BuildCraft fuel registry methods during {}.", phase);
                return new RegistrationResult(0, 0, false);
            }

            Map<String, ImportedFuel> overrides = parseOverrides();
            List<String> namespaceTokens = normalizedTokens(BridgeConfig.IC2_TO_BC_NAMESPACE_TOKENS.get());
            int candidates = 0;
            int added = 0;

            @SuppressWarnings({"rawtypes", "unchecked"})
            IForgeRegistry rawRegistry = ForgeRegistries.FLUIDS;
            @SuppressWarnings("unchecked")
            Collection<Object> fluids = (Collection<Object>) rawRegistry.getValues();
            for (Object candidate : fluids)
            {
                if (!(candidate instanceof Fluid fluid))
                {
                    continue;
                }

                Object registryKey = rawRegistry.getKey(fluid);
                if (registryKey == null)
                {
                    continue;
                }
                String id = registryKey.toString().toLowerCase(Locale.ROOT);
                ImportedFuel rule = overrides.get(id);
                if (rule == null)
                {
                    if (!BridgeConfig.IC2_TO_BC_AUTO_DISCOVERY.get() || !matchesNamespace(id, namespaceTokens))
                    {
                        continue;
                    }

                    Object burnProperty = getBurnProperty.invoke(ic2Manager, fluid);
                    if (burnProperty == null)
                    {
                        continue;
                    }
                    Double euPerMb = readBurnPower(burnProperty);
                    if (euPerMb == null || !(euPerMb > 0.0D))
                    {
                        LOGGER.warn("IC2 fuel {} has no readable positive EU/mB value; skipping it.", id);
                        continue;
                    }
                    rule = new ImportedFuel(euPerMb, BridgeConfig.IC2_TO_BC_BURN_TIME_TICKS.get(), "IC2 semifluid profile");
                }

                candidates++;
                FluidStack stack = new FluidStack(fluid, 1);
                if (getFuel.invoke(bcManager, stack) != null)
                {
                    LOGGER.debug("BuildCraft already has a fuel entry for {}; keeping the existing definition.", id);
                    continue;
                }

                int burnTicks = rule.burnTimeTicks();
                long microMjPerTick = Math.max(
                        1L,
                        EnergyConversionService.euToMicroMegaJoules(
                                EnergyConversionService.applyFuelBalance(rule.euPerMb())
                        ) / burnTicks
                );
                int totalBurningTime = Math.multiplyExact(burnTicks, 1_000);
                addFuel.invoke(bcManager, fluid, microMjPerTick, totalBurningTime);
                added++;
                LOGGER.info(
                        "Registered {} as BuildCraft fuel: {} EU/mB → {} micro-MJ/t for {} tick(s)/mB ({})",
                        id,
                        rule.euPerMb(),
                        microMjPerTick,
                        burnTicks,
                        rule.source()
                );
            }

            LOGGER.info("IC2 → BuildCraft fuel pass '{}' finished: {} candidate(s), {} newly registered.", phase, candidates, added);
            return new RegistrationResult(candidates, added, true);
        }
        catch (ReflectiveOperationException | RuntimeException exception)
        {
            LOGGER.error("Could not register IC2 fuels in BuildCraft during {}: {}", phase, exception.toString(), exception);
            return new RegistrationResult(0, 0, false);
        }
    }

    private static Object getStaticField(String className, String fieldName) throws ReflectiveOperationException
    {
        Class<?> type = Class.forName(className, false, Ic2ToBuildCraftFuelRegistrar.class.getClassLoader());
        Field field = type.getField(fieldName);
        return field.get(null);
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

    private static Double readBurnPower(Object property)
    {
        try
        {
            Method power = property.getClass().getMethod("power");
            Object value = power.invoke(property);
            return value instanceof Number number ? number.doubleValue() : null;
        }
        catch (ReflectiveOperationException exception)
        {
            return null;
        }
    }

    private static Map<String, ImportedFuel> parseOverrides()
    {
        Map<String, ImportedFuel> result = new HashMap<>();
        for (String raw : BridgeConfig.IC2_TO_BC_CUSTOM_FUEL_RULES.get())
        {
            if (raw == null || raw.isBlank())
            {
                continue;
            }
            String[] parts = raw.trim().split(";", -1);
            if (parts.length != 3)
            {
                LOGGER.error("Invalid IC2 → BuildCraft fuel rule '{}'. Expected namespace:path;energyEuPerMb;burnTimeTicks", raw);
                continue;
            }
            String id = parts[0].trim().toLowerCase(Locale.ROOT);
            if (!isPlausibleId(id))
            {
                LOGGER.error("Invalid fluid ID '{}' in IC2 → BuildCraft fuel rule '{}'.", parts[0], raw);
                continue;
            }
            try
            {
                double euPerMb = Double.parseDouble(parts[1].trim());
                int burnTicks = Integer.parseInt(parts[2].trim());
                if (!(euPerMb > 0.0D) || !Double.isFinite(euPerMb) || burnTicks < 1 || burnTicks > 2_000_000)
                {
                    throw new IllegalArgumentException("energy must be positive and burnTimeTicks must be in 1..2000000");
                }
                result.put(id, new ImportedFuel(euPerMb, burnTicks, "custom config"));
            }
            catch (IllegalArgumentException exception)
            {
                LOGGER.error("Invalid IC2 → BuildCraft fuel rule '{}': {}", raw, exception.getMessage());
            }
        }
        return result;
    }

    private static List<String> normalizedTokens(List<? extends String> configured)
    {
        List<String> result = new ArrayList<>();
        for (String token : configured)
        {
            if (token != null && !token.isBlank())
            {
                result.add(token.trim().toLowerCase(Locale.ROOT));
            }
        }
        return result;
    }

    private static boolean matchesNamespace(String id, List<String> tokens)
    {
        int separator = id.indexOf(':');
        if (separator <= 0)
        {
            return false;
        }
        String namespace = id.substring(0, separator);
        return tokens.stream().anyMatch(namespace::contains);
    }

    private static boolean isPlausibleId(String id)
    {
        int colon = id.indexOf(':');
        return colon > 0 && colon < id.length() - 1 && id.indexOf(':', colon + 1) < 0;
    }

    private record ImportedFuel(double euPerMb, int burnTimeTicks, String source)
    {
    }

    public record RegistrationResult(int candidates, int added, boolean registriesReady)
    {
    }
}
