package dev.bcic2bridge;

/**
 * The single authority for every EU ↔ BuildCraft MJ conversion in this mod.
 *
 * <p>BuildCraft stores energy as micro-MJ while its public API and this mod's
 * configuration are expressed in MJ. Keeping the conversion here prevents a
 * fuel profile, an energy receiver and a future UI from silently drifting to
 * different ratios.</p>
 */
public final class EnergyConversionService
{
    public static final double AUTO_EU_PER_MJ = 2.5D;
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
        if (!(eu > 0.0D) || !Double.isFinite(eu))
        {
            return 0L;
        }

        double result = euToMegaJoules(eu) * MICRO_MJ_PER_MJ;
        return result >= Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(0L, (long) Math.floor(result));
    }

    public static double microMegaJoulesToEu(long microMegaJoules)
    {
        return microMegaJoules <= 0 ? 0.0D : megaJoulesToEu(microMegaJoules / (double) MICRO_MJ_PER_MJ);
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
