package dev.bcic2bridge;

/**
 * IC2 semifluid-generator parameters expressed in user-facing terms.
 * IC2 runs a burn cycle for exactly {@code amountMb} ticks, so its power
 * parameter is also the effective conversion in EU/mB.
 *
 * @param amountMb batch size consumed when the generator starts a burn cycle
 * @param powerEuPerTick generator output while that cycle is active
 * @param source human-readable origin used only in logs
 */
public record FuelRule(int amountMb, double powerEuPerTick, String source)
{
    public FuelRule
    {
        if (amountMb <= 0)
        {
            throw new IllegalArgumentException("amountMb must be greater than zero");
        }
        if (!Double.isFinite(powerEuPerTick) || powerEuPerTick <= 0.0D)
        {
            throw new IllegalArgumentException("powerEuPerTick must be a finite positive number");
        }
        source = source == null ? "unknown" : source;
    }

    public static FuelRule fromEnergyConversion(
            int cycleAmountMb,
            double energyEuPerReferenceUnit,
            int referenceUnitVolumeMb,
            double energyMultiplier,
            String source
    )
    {
        if (referenceUnitVolumeMb <= 0)
        {
            throw new IllegalArgumentException("referenceUnitVolumeMb must be greater than zero");
        }
        if (!Double.isFinite(energyEuPerReferenceUnit) || energyEuPerReferenceUnit <= 0.0D)
        {
            throw new IllegalArgumentException("energyEuPerReferenceUnit must be a finite positive number");
        }
        if (!Double.isFinite(energyMultiplier) || energyMultiplier <= 0.0D)
        {
            throw new IllegalArgumentException("energyMultiplier must be a finite positive number");
        }

        return new FuelRule(
                cycleAmountMb,
                energyEuPerReferenceUnit / referenceUnitVolumeMb * energyMultiplier,
                source
        );
    }

    public static FuelRule fromEnergyDensity(
            int cycleAmountMb,
            double energyEuPerMb,
            double energyMultiplier,
            String source
    )
    {
        if (!Double.isFinite(energyEuPerMb) || energyEuPerMb <= 0.0D)
        {
            throw new IllegalArgumentException("energyEuPerMb must be a finite positive number");
        }
        if (!Double.isFinite(energyMultiplier) || energyMultiplier <= 0.0D)
        {
            throw new IllegalArgumentException("energyMultiplier must be a finite positive number");
        }

        return new FuelRule(cycleAmountMb, energyEuPerMb * energyMultiplier, source);
    }
}
