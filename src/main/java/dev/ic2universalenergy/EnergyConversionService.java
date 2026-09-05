package dev.ic2universalenergy;

/**
 * The single authority for every energy-unit conversion in this mod.
 *
 * <p>BuildCraft stores energy as micro-MJ while its public API and this mod's
 * configuration are expressed in MJ. Keeping the conversion here prevents a
 * fuel profile, an energy receiver and a future UI from silently drifting to
 * different ratios.</p>
 */
public final class EnergyConversionService
{
    public static final double AUTO_EU_PER_MJ = 2.5D;
    /** The conventional IC2-to-Forge-Energy ratio. */
    public static final double AUTO_FORGE_ENERGY_PER_EU = 4.0D;
    public static final long MICRO_MJ_PER_MJ = 1_000_000L;

    private EnergyConversionService()
    {
    }

    public static double euPerMegaJoule()
    {
        return BridgeConfig.ENERGY_CONVERSION_MODE.get() == EnergyConversionMode.AUTO
                ? AUTO_EU_PER_MJ
                : BridgeConfig.EU_PER_BUILDCRAFT_MJ.get();
    }

    public static double megaJoulesToEu(double megaJoules)
    {
        return megaJoules * euPerMegaJoule();
    }

    public static double euToMegaJoules(double eu)
    {
        return eu / euPerMegaJoule();
    }

    public static long euToMicroMegaJoules(double eu)
    {
        return euToMicroMegaJoules(eu, euPerMegaJoule());
    }

    public static long euToMicroMegaJoules(double eu, double euPerMj)
    {
        if (!(eu > 0.0D) || !Double.isFinite(eu) || !(euPerMj > 0.0D) || !Double.isFinite(euPerMj))
        {
            return 0L;
        }

        double result = eu / euPerMj * MICRO_MJ_PER_MJ;
        return result >= Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(0L, (long) Math.floor(result));
    }

    public static double microMegaJoulesToEu(long microMegaJoules)
    {
        return microMegaJoulesToEu(microMegaJoules, euPerMegaJoule());
    }

    public static double microMegaJoulesToEu(long microMegaJoules, double euPerMj)
    {
        return microMegaJoules <= 0 || !(euPerMj > 0.0D) || !Double.isFinite(euPerMj)
                ? 0.0D : microMegaJoules / (double) MICRO_MJ_PER_MJ * euPerMj;
    }

    public static int euToForgeEnergy(double eu)
    {
        return euToForgeEnergy(eu, forgeEnergyPerEu());
    }

    public static int euToForgeEnergy(double eu, double forgeEnergyPerEu)
    {
        if (!(eu > 0.0D) || !Double.isFinite(eu)
                || !(forgeEnergyPerEu > 0.0D) || !Double.isFinite(forgeEnergyPerEu))
        {
            return 0;
        }

        double result = eu * forgeEnergyPerEu;
        return result >= Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.max(0, (int) Math.floor(result));
    }

    public static double forgeEnergyToEu(int forgeEnergy)
    {
        return forgeEnergyToEu(forgeEnergy, forgeEnergyPerEu());
    }

    public static double forgeEnergyToEu(int forgeEnergy, double forgeEnergyPerEu)
    {
        return forgeEnergy <= 0 || !(forgeEnergyPerEu > 0.0D) || !Double.isFinite(forgeEnergyPerEu)
                ? 0.0D
                : forgeEnergy / forgeEnergyPerEu;
    }

    public static double forgeEnergyPerEu()
    {
        return BridgeConfig.FORGE_ENERGY_PER_EU.get();
    }

    /** Applies the pack-wide fuel-only balance multiplier exactly once. */
    public static double applyFuelBalance(double eu)
    {
        return eu * BridgeConfig.ENERGY_MULTIPLIER.get();
    }

    public static double buildCraftFuelEuPerMb(double megaJoulesPerMb)
    {
        return applyFuelBalance(megaJoulesToEu(megaJoulesPerMb));
    }
}
