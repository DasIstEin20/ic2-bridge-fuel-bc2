package dev.ic2universalenergy.integration.ic2;

import dev.ic2universalenergy.BridgeConfig;
import ic2.api.energy.EnergyNet;
import ic2.api.energy.tile.IEnergyEmitter;
import ic2.api.energy.tile.IEnergyTile;
import mekanism.common.lib.transmitter.ConnectionType;
import mekanism.common.tile.transmitter.TileEntityUniversalCable;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Direct IC2 EnergyNet adapter for the literal Mekanism Universal Cable.
 * EU is converted before it enters the cable, so the cable network only ever
 * stores and transports Forge Energy through its original capability path.
 */
public final class UniversalCableIc2EnergyAdapter
{
    private UniversalCableIc2EnergyAdapter()
    {
    }

    public static boolean register(TileEntityUniversalCable cable)
    {
        Level level = cable.getLevel();
        if (level == null || level.isClientSide || !enabled())
        {
            return false;
        }
        EnergyNet.instance.addBlockEntityTile(cable);
        cable.setForceUpdate();
        return true;
    }

    public static void unregister(TileEntityUniversalCable cable)
    {
        Level level = cable.getLevel();
        if (level != null && !level.isClientSide)
        {
            EnergyNet.instance.removeTile(cable);
        }
    }

    public static boolean acceptsEnergyFrom(TileEntityUniversalCable cable, Direction direction)
    {
        return cable.getIc2EnergyDemandGate().accepts(direction);
    }

    public static double demandedEnergy(TileEntityUniversalCable cable)
    {
        return cable.getIc2EnergyDemandGate().demandedEnergy();
    }

    public static double injectEnergy(TileEntityUniversalCable cable, Direction direction, double offeredEu)
    {
        if (!(offeredEu > 0.0D) || !Double.isFinite(offeredEu))
        {
            return offeredEu;
        }

        return cable.getIc2EnergyDemandGate().reserve(offeredEu);
    }

    public static boolean isIc2EmitterForCable(
            TileEntityUniversalCable cable,
            BlockEntity neighbor,
            Direction cableToNeighbor
    )
    {
        if (!enabled() || neighbor == null || neighbor.isRemoved() || neighbor.getLevel() == null)
        {
            return false;
        }
        IEnergyTile energyTile = EnergyNet.instance.getSubTile(neighbor.getLevel(), neighbor.getBlockPos());
        return energyTile instanceof IEnergyEmitter emitter
                && sideAllowsInput(cable, cableToNeighbor)
                && emitter.emitsEnergyTo(cable, cableToNeighbor.getOpposite())
                && cable.acceptsEnergyFrom(emitter, cableToNeighbor);
    }

    private static boolean sideAllowsInput(TileEntityUniversalCable cable, Direction direction)
    {
        if (direction == null || cable.getTransmitter().isRedstoneActivated())
        {
            return false;
        }
        ConnectionType connectionType = cable.getTransmitter().getConnectionTypeRaw(direction);
        return connectionType == ConnectionType.NORMAL || connectionType == ConnectionType.PULL;
    }

    private static boolean enabled()
    {
        return BridgeConfig.IC2_TO_FORGE_ENERGY_BRIDGE_ENABLED.get();
    }
}
