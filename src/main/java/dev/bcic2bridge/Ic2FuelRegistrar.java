package dev.bcic2bridge;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Uses only Forge's registry API plus reflection into IC2. This intentionally avoids a
 * compile-time dependency on one exact IC2 Refactored patch release.
 */
public final class Ic2FuelRegistrar
{
    private static final Logger LOGGER = LoggerFactory.getLogger(BcIc2FuelBridge.MOD_ID);
    private static final String RECIPES_CLASS = "ic2.api.recipe.Recipes";
    private static final String MANAGER_FIELD = "semiFluidGenerator";

    private static Object activeManager;
    private static final Set<String> registeredForActiveManager = new HashSet<>();
    private static boolean managerMissingWasLogged;

    private Ic2FuelRegistrar()
    {
    }

    public static synchronized RegistrationResult registerAll(String phase)
    {
        if (!BridgeConfig.FUEL_BRIDGE_BC_TO_IC2_ENABLED.get())
        {
            LOGGER.info("BuildCraft → IC2 fuel bridge is disabled; skipping registration during {}.", phase);
            return new RegistrationResult(0, 0, 0, true);
        }

        Object manager = findManager();
        if (manager == null)
        {
            if (!managerMissingWasLogged)
            {
                LOGGER.warn("IC2 semifluid manager is not ready during {}. A later lifecycle retry will be used.", phase);
                managerMissingWasLogged = true;
            }
            return new RegistrationResult(0, 0, 0, false);
        }

        managerMissingWasLogged = false;
        if (manager != activeManager)
        {
            activeManager = manager;
            registeredForActiveManager.clear();
            LOGGER.info("Detected IC2 semifluid manager: {}", manager.getClass().getName());
        }

        Method addFluid = findAddFluidMethod(manager.getClass());
        if (addFluid == null)
        {
            LOGGER.error("IC2 manager {} has no compatible addFluid(fluid, number, number) method.", manager.getClass().getName());
            return new RegistrationResult(0, 0, 0, true);
        }

        Method acceptsFluid = findOneArgumentMethod(manager.getClass(), "acceptsFluid");
        SignatureMode signatureMode = detectSignatureMode(manager);
        Map<String, FuelRule> customRules = parseCustomRules();

        int candidates = 0;
        int added = 0;
        int alreadyPresent = 0;

        @SuppressWarnings({"rawtypes", "unchecked"})
        IForgeRegistry rawFluidRegistry = ForgeRegistries.FLUIDS;
        @SuppressWarnings("unchecked")
        Collection<Object> fluids = (Collection<Object>) rawFluidRegistry.getValues();

        for (Object fluid : fluids)
        {
            Object key = rawFluidRegistry.getKey(fluid);
            if (key == null)
            {
                continue;
            }

            String id = normalizeId(key.toString());
            if (id.isEmpty() || "minecraft:empty".equals(id))
            {
                continue;
            }

            // An explicit per-fuel OFF must win over custom rules and generic
            // discovery. Otherwise a disabled CE profile could be registered
            // again by the fallback oil/fuel detector.
            if (BuildCraftCeFuelProfiles.isKnown(id) && !BuildCraftCeFuelProfiles.isEnabled(id))
            {
                continue;
            }

            FuelRule rule = customRules.get(id);
            if (rule == null)
            {
                rule = automaticRule(id, fluid);
            }
            if (rule == null)
            {
                continue;
            }

            candidates++;
            if (registeredForActiveManager.contains(id))
            {
                alreadyPresent++;
                continue;
            }

            if (acceptsFluid != null && invokeAccepts(acceptsFluid, manager, fluid))
            {
                registeredForActiveManager.add(id);
                alreadyPresent++;
                LOGGER.info("IC2 already accepts fluid {}; leaving its existing burn property unchanged.", id);
                continue;
            }

            try
            {
                invokeAddFluid(addFluid, manager, fluid, rule, signatureMode);
                registeredForActiveManager.add(id);
                added++;
                LOGGER.info(
                        "Registered {} in IC2 semifluid generator: cycle={} mB, output={} EU/t ({} EU/mB), rule={}, API mode={}",
                        id,
                        rule.amountMb(),
                        rule.powerEuPerTick(),
                        rule.powerEuPerTick(),
                        rule.source(),
                        signatureMode.logName
                );
            }
            catch (ReflectiveOperationException | RuntimeException exception)
            {
                Throwable root = unwrap(exception);
                if (looksLikeDuplicate(root))
                {
                    registeredForActiveManager.add(id);
                    alreadyPresent++;
                    LOGGER.info("IC2 reported that {} already has a burn property; keeping the existing value.", id);
                }
                else
                {
                    LOGGER.error("Could not register fluid {} in IC2: {}", id, root.toString(), root);
                }
            }
        }

        LOGGER.info(
                "Fuel bridge pass '{}' finished: {} candidate(s), {} newly registered, {} already present.",
                phase,
                candidates,
                added,
                alreadyPresent
        );
        return new RegistrationResult(candidates, added, alreadyPresent, true);
    }

    private static Object findManager()
    {
        try
        {
            ClassLoader classLoader = Ic2FuelRegistrar.class.getClassLoader();
            Class<?> recipes = Class.forName(RECIPES_CLASS, false, classLoader);
            Field field = recipes.getField(MANAGER_FIELD);
            if (!Modifier.isStatic(field.getModifiers()))
            {
                LOGGER.error("{}.{} exists but is not static.", RECIPES_CLASS, MANAGER_FIELD);
                return null;
            }
            return field.get(null);
        }
        catch (ClassNotFoundException exception)
        {
            LOGGER.error("IC2 API class {} was not found even though IC2 is declared as a required dependency.", RECIPES_CLASS);
            return null;
        }
        catch (ReflectiveOperationException | LinkageError exception)
        {
            LOGGER.error("Could not access IC2 fuel manager: {}", exception.toString(), exception);
            return null;
        }
    }

    private static Method findAddFluidMethod(Class<?> managerClass)
    {
        for (Method method : managerClass.getMethods())
        {
            if (!"addFluid".equals(method.getName()) || method.getParameterCount() != 3)
            {
                continue;
            }

            Class<?>[] parameters = method.getParameterTypes();
            if (isNumeric(parameters[1]) && isNumeric(parameters[2]))
            {
                makeAccessible(method);
                return method;
            }
        }
        return null;
    }

    private static Method findOneArgumentMethod(Class<?> managerClass, String name)
    {
        for (Method method : managerClass.getMethods())
        {
            if (name.equals(method.getName()) && method.getParameterCount() == 1)
            {
                makeAccessible(method);
                return method;
            }
        }
        return null;
    }

    private static SignatureMode detectSignatureMode(Object manager)
    {
        // Older IC2 ports exposed getEU(fluid) and used addFluid(fluid, eu, amount).
        if (findOneArgumentMethod(manager.getClass(), "getEU") != null)
        {
            return SignatureMode.LEGACY_POWER_THEN_AMOUNT;
        }

        // The uploaded 2.10.3x source exposes BurnProperty(int amount, double power).
        Method propertiesMethod = findZeroArgumentMethod(manager.getClass(), "getBurnProperties");
        if (propertiesMethod != null)
        {
            try
            {
                Object result = propertiesMethod.invoke(manager);
                if (result instanceof Map<?, ?> map)
                {
                    for (Object property : map.values())
                    {
                        if (property == null)
                        {
                            continue;
                        }
                        Method amount = findZeroArgumentMethod(property.getClass(), "amount");
                        Method power = findZeroArgumentMethod(property.getClass(), "power");
                        if (amount != null && power != null
                                && isIntegral(amount.getReturnType())
                                && isFloating(power.getReturnType()))
                        {
                            return SignatureMode.AMOUNT_THEN_POWER;
                        }
                    }
                }
            }
            catch (ReflectiveOperationException | RuntimeException exception)
            {
                LOGGER.debug("Could not inspect IC2 burn-property records; using the 2.10.3x parameter order.", exception);
            }
        }

        return SignatureMode.AMOUNT_THEN_POWER;
    }

    private static Method findZeroArgumentMethod(Class<?> owner, String name)
    {
        for (Method method : owner.getMethods())
        {
            if (name.equals(method.getName()) && method.getParameterCount() == 0)
            {
                makeAccessible(method);
                return method;
            }
        }
        return null;
    }

    private static void invokeAddFluid(
            Method method,
            Object manager,
            Object fluid,
            FuelRule rule,
            SignatureMode mode
    ) throws ReflectiveOperationException
    {
        Class<?>[] parameterTypes = method.getParameterTypes();
        if (!parameterTypes[0].isInstance(fluid))
        {
            throw new IllegalArgumentException(
                    "Fluid object " + fluid.getClass().getName() + " is incompatible with " + parameterTypes[0].getName()
            );
        }

        double secondValue = mode == SignatureMode.AMOUNT_THEN_POWER
                ? rule.amountMb()
                : rule.powerEuPerTick();
        double thirdValue = mode == SignatureMode.AMOUNT_THEN_POWER
                ? rule.powerEuPerTick()
                : rule.amountMb();

        Object second = coerceNumber(secondValue, parameterTypes[1]);
        Object third = coerceNumber(thirdValue, parameterTypes[2]);
        method.invoke(manager, fluid, second, third);
    }

    private static boolean invokeAccepts(Method method, Object manager, Object fluid)
    {
        try
        {
            if (!method.getParameterTypes()[0].isInstance(fluid))
            {
                return false;
            }
            return Boolean.TRUE.equals(method.invoke(manager, fluid));
        }
        catch (ReflectiveOperationException | RuntimeException exception)
        {
            LOGGER.debug("IC2 acceptsFluid check failed; addFluid will be attempted instead.", exception);
            return false;
        }
    }

    private static FuelRule automaticRule(String id, Object fluid)
    {
        // The nine known CE fuels are configured explicitly. MANUAL therefore
        // still works when generic automatic discovery is disabled.
        if (BuildCraftCeFuelProfiles.isKnown(id))
        {
            return BuildCraftCeFuelProfiles.ruleFor(id);
        }

        if (!BridgeConfig.AUTO_DISCOVERY.get())
        {
            return null;
        }

        IdParts parts = IdParts.parse(id);
        if (parts == null || looksLikeFlowingVariant(parts.path))
        {
            return null;
        }

        boolean looksLikeFuel = parts.path.contains("fuel");
        boolean looksLikeOil = parts.path.contains("oil");
        if (!looksLikeFuel && !looksLikeOil)
        {
            return null;
        }

        List<String> tokens = normalizedTokens(BridgeConfig.NAMESPACE_TOKENS.get());
        boolean namespaceMatch = containsAny(parts.namespace, tokens);
        boolean classMatch = BridgeConfig.CLASS_PACKAGE_FALLBACK.get()
                && containsAny(fluid.getClass().getName().toLowerCase(Locale.ROOT), tokens);

        if (!namespaceMatch && !classMatch)
        {
            if (BridgeConfig.LOG_SKIPPED_OIL_FUEL_IDS.get())
            {
                LOGGER.info(
                        "Found oil/fuel-looking fluid {} but skipped it because neither namespace nor class matched tokens {}.",
                        id,
                        tokens
                );
            }
            return null;
        }

        if (looksLikeFuel)
        {
            return FuelRule.fromEnergyConversion(
                    BridgeConfig.FUEL_CYCLE_AMOUNT_MB.get(),
                    BridgeConfig.FUEL_ENERGY_EU_PER_REFERENCE_UNIT.get(),
                    BridgeConfig.REFERENCE_UNIT_VOLUME_MB.get(),
                    BridgeConfig.ENERGY_MULTIPLIER.get(),
                    "automatic fuel"
            );
        }

        return FuelRule.fromEnergyConversion(
                BridgeConfig.OIL_CYCLE_AMOUNT_MB.get(),
                BridgeConfig.OIL_ENERGY_EU_PER_REFERENCE_UNIT.get(),
                BridgeConfig.REFERENCE_UNIT_VOLUME_MB.get(),
                BridgeConfig.ENERGY_MULTIPLIER.get(),
                "automatic oil"
        );
    }

    private static Map<String, FuelRule> parseCustomRules()
    {
        Map<String, FuelRule> result = new HashMap<>();
        for (String raw : BridgeConfig.CUSTOM_FUEL_RULES.get())
        {
            if (raw == null || raw.isBlank())
            {
                continue;
            }

            String[] parts = raw.trim().split(";", -1);
            if (parts.length != 4)
            {
                LOGGER.error(
                        "Invalid custom fuel rule '{}'. Expected namespace:path;energyEuPerReferenceUnit;referenceUnitVolumeMb;cycleAmountMb",
                        raw
                );
                continue;
            }

            String id = normalizeId(parts[0]);
            if (!isPlausibleId(id))
            {
                LOGGER.error("Invalid fluid ID '{}' in custom fuel rule '{}'.", parts[0], raw);
                continue;
            }

            try
            {
                double energy = Double.parseDouble(parts[1].trim());
                int volume = Integer.parseInt(parts[2].trim());
                int cycleAmount = Integer.parseInt(parts[3].trim());
                result.put(
                        id,
                        FuelRule.fromEnergyConversion(
                                cycleAmount,
                                energy,
                                volume,
                                BridgeConfig.ENERGY_MULTIPLIER.get(),
                                "custom config"
                        )
                );
            }
            catch (IllegalArgumentException exception)
            {
                LOGGER.error("Invalid numeric values in custom fuel rule '{}': {}", raw, exception.getMessage());
            }
        }
        return result;
    }

    private static List<String> normalizedTokens(List<? extends String> configured)
    {
        List<String> result = new ArrayList<>();
        for (String token : configured)
        {
            if (token == null)
            {
                continue;
            }
            String normalized = token.trim().toLowerCase(Locale.ROOT);
            if (!normalized.isEmpty())
            {
                result.add(normalized);
            }
        }
        return result;
    }

    private static boolean containsAny(String value, List<String> tokens)
    {
        for (String token : tokens)
        {
            if (value.contains(token))
            {
                return true;
            }
        }
        return false;
    }

    private static boolean looksLikeFlowingVariant(String path)
    {
        return path.startsWith("flowing_")
                || path.endsWith("_flowing")
                || path.contains("/flowing_")
                || path.contains("_flowing/");
    }

    private static String normalizeId(String value)
    {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isPlausibleId(String id)
    {
        int colon = id.indexOf(':');
        return colon > 0 && colon < id.length() - 1 && id.indexOf(':', colon + 1) < 0;
    }

    private static Object coerceNumber(double value, Class<?> target)
    {
        if (target == byte.class || target == Byte.class)
        {
            return (byte) Math.round(value);
        }
        if (target == short.class || target == Short.class)
        {
            return (short) Math.round(value);
        }
        if (target == int.class || target == Integer.class)
        {
            return (int) Math.round(value);
        }
        if (target == long.class || target == Long.class)
        {
            return Math.round(value);
        }
        if (target == float.class || target == Float.class)
        {
            return (float) value;
        }
        if (target == double.class || target == Double.class)
        {
            return value;
        }
        throw new IllegalArgumentException("Unsupported numeric parameter type: " + target.getName());
    }

    private static boolean isNumeric(Class<?> type)
    {
        return isIntegral(type) || isFloating(type);
    }

    private static boolean isIntegral(Class<?> type)
    {
        return type == byte.class || type == Byte.class
                || type == short.class || type == Short.class
                || type == int.class || type == Integer.class
                || type == long.class || type == Long.class;
    }

    private static boolean isFloating(Class<?> type)
    {
        return type == float.class || type == Float.class
                || type == double.class || type == Double.class;
    }

    private static void makeAccessible(Method method)
    {
        try
        {
            method.trySetAccessible();
        }
        catch (RuntimeException ignored)
        {
            // Public interface methods do not normally need this; invocation may still work.
        }
    }

    private static Throwable unwrap(Throwable throwable)
    {
        Throwable current = throwable;
        while (current instanceof InvocationTargetException invocation && invocation.getCause() != null)
        {
            current = invocation.getCause();
        }
        return current;
    }

    private static boolean looksLikeDuplicate(Throwable throwable)
    {
        String message = throwable.getMessage();
        return message != null
                && message.toLowerCase(Locale.ROOT).contains("already")
                && message.toLowerCase(Locale.ROOT).contains("burn");
    }

    private enum SignatureMode
    {
        AMOUNT_THEN_POWER("amount,power (IC2 2.10.3x)"),
        LEGACY_POWER_THEN_AMOUNT("power,amount (legacy fallback)");

        private final String logName;

        SignatureMode(String logName)
        {
            this.logName = logName;
        }
    }

    private record IdParts(String namespace, String path)
    {
        private static IdParts parse(String id)
        {
            int colon = id.indexOf(':');
            if (colon <= 0 || colon >= id.length() - 1)
            {
                return null;
            }
            return new IdParts(id.substring(0, colon), id.substring(colon + 1));
        }
    }

    public record RegistrationResult(int candidates, int added, int alreadyPresent, boolean managerReady)
    {
    }
}
