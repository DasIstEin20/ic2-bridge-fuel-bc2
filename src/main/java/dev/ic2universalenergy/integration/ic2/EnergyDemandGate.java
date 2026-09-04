package dev.ic2universalenergy.integration.ic2;

import dev.ic2universalenergy.BridgeConfig;
import dev.ic2universalenergy.EnergyConversionService;
import dev.ic2universalenergy.EnergyTransferLimitMode;
import mekanism.api.math.FloatingLong;
import mekanism.common.content.network.EnergyNetwork;
import mekanism.common.lib.transmitter.ConnectionType;
import mekanism.common.tile.transmitter.TileEntityUniversalCable;
import mekanism.common.util.UnitDisplayUtils.EnergyUnit;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;

/**
 * Applies downstream backpressure to the IC2 input of a Universal Cable.
 *
 * <p>The cable's native buffer remains untouched. This gate only prevents the
 * IC2 sink from accepting and converting more EU when the already-buffered
 * energy can satisfy all demand currently reported by connected FE consumers.</p>
 */
public final class EnergyDemandGate
{
    private static final String PENDING_FORGE_ENERGY = "Ic2PendingForgeEnergy";

    /*
     * IC2 calculates grids on a worker thread. These primitive snapshots are
     * the entire worker-thread boundary: IC2 callbacks must never query a
     * Forge capability, Refined Storage, a Mekanism network or the world.
     */
    private int availableForgeEnergy;
    private int pendingForgeEnergy;
    private int inputSideMask;
    private double forgeEnergyPerEu = EnergyConversionService.AUTO_FORGE_ENERGY_PER_EU;
    private boolean active;

    /**
     * Flushes already-accounted FE and takes the next downstream-demand
     * snapshot. Called only by the cable's normal server tick.
     */
    public void tickServer(TileEntityUniversalCable cable)
    {
        double ratio = EnergyConversionService.forgeEnergyPerEu();
        boolean enabled = BridgeConfig.IC2_TO_FORGE_ENERGY_BRIDGE_ENABLED.get();
        int newInputSideMask = enabled ? inputSideMask(cable) : 0;

        int queued;
        synchronized (this)
        {
            this.forgeEnergyPerEu = ratio;
            this.active = enabled && newInputSideMask != 0;
            this.inputSideMask = newInputSideMask;
            queued = this.pendingForgeEnergy;
            if (!enabled)
            {
                this.availableForgeEnergy = 0;
            }
        }

        if (queued > 0)
        {
            int inserted = insertIntoCable(cable, queued);
            if (inserted > 0)
            {
                synchronized (this)
                {
                    this.pendingForgeEnergy = Math.max(0, this.pendingForgeEnergy - inserted);
                }
            }
        }

        if (!enabled || !cable.getTransmitter().hasTransmitterNetwork())
        {
            synchronized (this)
            {
                this.availableForgeEnergy = 0;
            }
            return;
        }

        int maximumForgeEnergy = maximumForgeEnergy(ratio);
        EnergyNetwork network = cable.getTransmitter().getTransmitterNetwork();
        FloatingLong maximum = EnergyUnit.FORGE_ENERGY.convertFrom(maximumForgeEnergy);
        FloatingLong downstreamDemand = network.getSimulatedAcceptorDemand(maximum);
        FloatingLong buffered = network.energyContainer.getEnergy();
        if (downstreamDemand.isZero() || !buffered.smallerThan(downstreamDemand))
        {
            synchronized (this)
            {
                this.availableForgeEnergy = 0;
            }
            return;
        }

        FloatingLong unqueuedDemand = downstreamDemand.subtract(buffered);
        int measuredDemand = Math.min(maximumForgeEnergy, EnergyUnit.FORGE_ENERGY.convertToAsInt(unqueuedDemand));
        synchronized (this)
        {
            this.availableForgeEnergy = Math.max(0, measuredDemand - this.pendingForgeEnergy);
        }
    }

    /** Returns the immutable server-tick snapshot to IC2's calculator thread. */
    public synchronized double demandedEnergy()
    {
        return this.active
                ? EnergyConversionService.forgeEnergyToEu(this.availableForgeEnergy, this.forgeEnergyPerEu)
                : 0.0D;
    }

    public synchronized boolean accepts(Direction direction)
    {
        return this.active && direction != null
                && (this.inputSideMask & 1 << direction.ordinal()) != 0;
    }

    /**
     * Atomically reserves FE against the snapshot and queues it for insertion
     * on the next server tick. The returned EU is never discarded.
     */
    public synchronized double reserve(double offeredEu)
    {
        if (!this.active || !(offeredEu > 0.0D) || !Double.isFinite(offeredEu))
        {
            return offeredEu;
        }

        int offeredForgeEnergy = EnergyConversionService.euToForgeEnergy(offeredEu, this.forgeEnergyPerEu);
        int queueCapacity = Integer.MAX_VALUE - this.pendingForgeEnergy;
        int acceptedForgeEnergy = Math.min(offeredForgeEnergy,
                Math.min(this.availableForgeEnergy, queueCapacity));
        if (acceptedForgeEnergy <= 0)
        {
            return offeredEu;
        }

        this.availableForgeEnergy -= acceptedForgeEnergy;
        this.pendingForgeEnergy += acceptedForgeEnergy;
        double acceptedEu = EnergyConversionService.forgeEnergyToEu(acceptedForgeEnergy, this.forgeEnergyPerEu);
        return Math.max(0.0D, offeredEu - acceptedEu);
    }

    public synchronized void load(CompoundTag tag)
    {
        this.pendingForgeEnergy = Math.max(0, tag.getInt(PENDING_FORGE_ENERGY));
        this.availableForgeEnergy = 0;
    }

    public synchronized void save(CompoundTag tag)
    {
        if (this.pendingForgeEnergy > 0)
        {
            tag.putInt(PENDING_FORGE_ENERGY, this.pendingForgeEnergy);
        }
    }

    public synchronized int pendingForgeEnergy()
    {
        return this.pendingForgeEnergy;
    }

    private static int insertIntoCable(TileEntityUniversalCable cable, int offeredForgeEnergy)
    {
        for (Direction direction : Direction.values())
        {
            try
            {
                IEnergyStorage storage = cable.getCapability(ForgeCapabilities.ENERGY, direction).orElse(null);
                if (storage != null && storage.canReceive())
                {
                    int accepted = Math.max(0, Math.min(offeredForgeEnergy,
                            storage.receiveEnergy(offeredForgeEnergy, false)));
                    if (accepted > 0)
                    {
                        return accepted;
                    }
                }
            }
            catch (RuntimeException ignored)
            {
                // A side can invalidate while connections are refreshed. The
                // queued FE remains owned by the gate and is retried next tick.
            }
        }
        return 0;
    }

    private static int maximumForgeEnergy(double ratio)
    {
        return BridgeConfig.ENERGY_TRANSFER_LIMIT_MODE.get() == EnergyTransferLimitMode.MANUAL
                ? EnergyConversionService.euToForgeEnergy(
                        BridgeConfig.ENERGY_TRANSFER_LIMIT_EU_PER_TICK.get(), ratio)
                : Integer.MAX_VALUE;
    }

    private static int inputSideMask(TileEntityUniversalCable cable)
    {
        if (cable.getTransmitter().isRedstoneActivated())
        {
            return 0;
        }
        int mask = 0;
        for (Direction direction : Direction.values())
        {
            ConnectionType type = cable.getTransmitter().getConnectionTypeRaw(direction);
            if (type == ConnectionType.NORMAL || type == ConnectionType.PULL)
            {
                mask |= 1 << direction.ordinal();
            }
        }
        return mask;
    }
}
