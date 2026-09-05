package dev.ic2universalenergy;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns every BuildCraft {@code IMjReceiver} block entity into an IC2 energy
 * sink. The integration deliberately targets BuildCraft's public MJ receiver
 * capability once, so a Quarry, Mining Well, future BC machine, or another
 * compatible receiver all use the same bridge.
 *
 * <p>The optional-mod APIs are reached reflectively. That keeps the bridge
 * binary independent of a particular IC2 or BuildCraft patch version while
 * still using their public extension points at runtime.</p>
 */
public final class Ic2ToBuildCraftEnergyBridge
{
    private static final Logger LOGGER = LoggerFactory.getLogger(Ic2UniversalEnergyExtension.MOD_ID);
    private static final String MJ_API = "buildcraft.api.mj.MjAPI";
    private static final String MJ_RECEIVER = "buildcraft.api.mj.IMjReceiver";
    private static final String IC2_SINK = "ic2.api.energy.tile.IEnergySink";
    private static final String IC2_LOCATABLE = "ic2.api.info.ILocatable";
    private static final String IC2_ENERGY_TILE = "ic2.api.energy.tile.IEnergyTile";
    private static final String IC2_LOAD_EVENT = "ic2.api.energy.event.EnergyTileLoadEvent";
    private static final String IC2_UNLOAD_EVENT = "ic2.api.energy.event.EnergyTileUnloadEvent";

    private final Map<ServerLevel, Map<BlockPos, BridgeSink>> sinks = new HashMap<>();
    private final Map<ServerLevel, Set<BlockPos>> pendingPositions = new HashMap<>();
    private Capability<?> mjReceiverCapability;
    private Class<?> mjReceiverType;
    private boolean apiUnavailableLogged;
    private boolean registered;

    public synchronized void register()
    {
        if (this.registered)
        {
            return;
        }
        this.registered = true;
        MinecraftForge.EVENT_BUS.addListener(this::onChunkLoad);
        MinecraftForge.EVENT_BUS.addListener(this::onChunkUnload);
        MinecraftForge.EVENT_BUS.addListener(this::onEntityPlace);
        MinecraftForge.EVENT_BUS.addListener(this::onBlockBreak);
        MinecraftForge.EVENT_BUS.addListener(this::onLevelTick);
        MinecraftForge.EVENT_BUS.addListener(this::onLevelUnload);
    }

    private void onChunkLoad(ChunkEvent.Load event)
    {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk))
        {
            return;
        }
        for (BlockEntity blockEntity : chunk.getBlockEntities().values())
        {
            this.queue(level, blockEntity.getBlockPos());
        }
    }

    private void onChunkUnload(ChunkEvent.Unload event)
    {
        if (!(event.getLevel() instanceof ServerLevel level))
        {
            return;
        }
        int chunkX = event.getChunk().getPos().x;
        int chunkZ = event.getChunk().getPos().z;
        Map<BlockPos, BridgeSink> inLevel = this.sinks.get(level);
        if (inLevel == null)
        {
            return;
        }
        for (BlockPos pos : Set.copyOf(inLevel.keySet()))
        {
            if ((pos.getX() >> 4) == chunkX && (pos.getZ() >> 4) == chunkZ)
            {
                this.unregister(level, pos);
            }
        }
    }

    private void onEntityPlace(BlockEvent.EntityPlaceEvent event)
    {
        if (event.getLevel() instanceof ServerLevel level)
        {
            this.queue(level, event.getPos());
        }
    }

    private void onBlockBreak(BlockEvent.BreakEvent event)
    {
        if (event.getPlayer().level() instanceof ServerLevel level)
        {
            this.unregister(level, event.getPos());
        }
    }

    private void onLevelTick(TickEvent.LevelTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level))
        {
            return;
        }
        Map<BlockPos, BridgeSink> levelSinks = this.sinks.get(level);
        if (levelSinks != null)
        {
            for (BridgeSink sink : java.util.List.copyOf(levelSinks.values()))
            {
                if (sink.target.isRemoved() || !level.hasChunkAt(sink.position) || level.getBlockEntity(sink.position) != sink.target)
                {
                    this.unregister(level, sink.position);
                }
                else
                {
                    sink.tickServer();
                }
            }
        }
        Set<BlockPos> positions = this.pendingPositions.remove(level);
        if (positions == null)
        {
            return;
        }
        for (BlockPos pos : positions)
        {
            this.register(level, pos);
        }
    }

    private void onLevelUnload(LevelEvent.Unload event)
    {
        if (!(event.getLevel() instanceof ServerLevel level))
        {
            return;
        }
        Map<BlockPos, BridgeSink> inLevel = this.sinks.remove(level);
        this.pendingPositions.remove(level);
        if (inLevel != null)
        {
            for (BridgeSink sink : inLevel.values())
            {
                this.postEnergyTileEvent(IC2_UNLOAD_EVENT, sink.proxy());
            }
        }
    }

    private void queue(ServerLevel level, BlockPos pos)
    {
        this.pendingPositions.computeIfAbsent(level, ignored -> new HashSet<>()).add(pos.immutable());
    }

    private void register(ServerLevel level, BlockPos pos)
    {
        Map<BlockPos, BridgeSink> inLevel = this.sinks.computeIfAbsent(level, ignored -> new HashMap<>());
        if (inLevel.containsKey(pos))
        {
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null || !this.hasMjReceiver(blockEntity))
        {
            return;
        }

        try
        {
            BridgeSink sink = new BridgeSink(level, pos.immutable(), blockEntity, this);
            sink.setProxy(this.createIc2SinkProxy(sink));
            sink.tickServer();
            if (this.postEnergyTileEvent(IC2_LOAD_EVENT, sink.proxy()))
            {
                inLevel.put(sink.position(), sink);
                LOGGER.debug("Attached IC2 energy sink to BuildCraft MJ receiver at {} in {}.", pos, level.dimension().location());
            }
        }
        catch (ReflectiveOperationException | LinkageError exception)
        {
            if (!this.apiUnavailableLogged)
            {
                LOGGER.error("IC2 → BuildCraft energy bridge could not initialize: {}", exception.toString(), exception);
                this.apiUnavailableLogged = true;
            }
        }
    }

    private void unregister(ServerLevel level, BlockPos pos)
    {
        Map<BlockPos, BridgeSink> inLevel = this.sinks.get(level);
        if (inLevel == null)
        {
            return;
        }
        BridgeSink sink = inLevel.remove(pos);
        if (sink == null)
        {
            return;
        }
        this.postEnergyTileEvent(IC2_UNLOAD_EVENT, sink.proxy());
        if (inLevel.isEmpty())
        {
            this.sinks.remove(level);
        }
    }

    private Object createIc2SinkProxy(BridgeSink sink) throws ReflectiveOperationException
    {
        ClassLoader loader = Ic2ToBuildCraftEnergyBridge.class.getClassLoader();
        Class<?> sinkType = Class.forName(IC2_SINK, false, loader);
        Class<?> locatableType = Class.forName(IC2_LOCATABLE, false, loader);
        return Proxy.newProxyInstance(loader, new Class<?>[]{sinkType, locatableType}, sink);
    }

    private boolean postEnergyTileEvent(String eventClassName, Object sinkProxy)
    {
        try
        {
            ClassLoader loader = Ic2ToBuildCraftEnergyBridge.class.getClassLoader();
            Class<?> energyTile = Class.forName(IC2_ENERGY_TILE, false, loader);
            Class<?> eventType = Class.forName(eventClassName, false, loader);
            Constructor<?> constructor = eventType.getConstructor(energyTile);
            Object event = constructor.newInstance(sinkProxy);
            MinecraftForge.EVENT_BUS.post((Event) event);
            return true;
        }
        catch (ReflectiveOperationException | ClassCastException | LinkageError exception)
        {
            if (!this.apiUnavailableLogged)
            {
                LOGGER.error("Could not post {} for the energy bridge: {}", eventClassName, exception.toString(), exception);
                this.apiUnavailableLogged = true;
            }
            return false;
        }
    }

    private boolean hasMjReceiver(BlockEntity blockEntity)
    {
        return this.receiverFor(blockEntity, null) != null
                || EnumSet.allOf(Direction.class).stream().anyMatch(direction -> this.receiverFor(blockEntity, direction) != null);
    }

    private Object receiverFor(BlockEntity blockEntity, Direction direction)
    {
        try
        {
            Capability<?> capability = this.getMjReceiverCapability();
            LazyOptional<?> optional = blockEntity.getCapability(capability, direction);
            Object receiver = optional.orElse(null);
            return receiver != null && this.getMjReceiverType().isInstance(receiver) ? receiver : null;
        }
        catch (ReflectiveOperationException | RuntimeException | LinkageError exception)
        {
            return null;
        }
    }

    private Capability<?> getMjReceiverCapability() throws ReflectiveOperationException
    {
        if (this.mjReceiverCapability == null)
        {
            Field field = Class.forName(MJ_API, false, Ic2ToBuildCraftEnergyBridge.class.getClassLoader())
                    .getField("CAP_RECEIVER");
            this.mjReceiverCapability = (Capability<?>) field.get(null);
        }
        return this.mjReceiverCapability;
    }

    private Class<?> getMjReceiverType() throws ClassNotFoundException
    {
        if (this.mjReceiverType == null)
        {
            this.mjReceiverType = Class.forName(MJ_RECEIVER, false, Ic2ToBuildCraftEnergyBridge.class.getClassLoader());
        }
        return this.mjReceiverType;
    }

    private long powerRequested(Object receiver) throws ReflectiveOperationException
    {
        Method method = this.getMjReceiverType().getMethod("getPowerRequested");
        Object result = method.invoke(receiver);
        return result instanceof Number number ? Math.max(0L, number.longValue()) : 0L;
    }

    private boolean canReceive(Object receiver) throws ReflectiveOperationException
    {
        Method method = this.getMjReceiverType().getMethod("canReceive");
        Object result = method.invoke(receiver);
        return !Boolean.FALSE.equals(result);
    }

    private long receivePower(Object receiver, long offeredMicroMj) throws ReflectiveOperationException
    {
        Method method = this.getMjReceiverType().getMethod("receivePower", long.class, FluidAction.class);
        Object result = method.invoke(receiver, offeredMicroMj, FluidAction.EXECUTE);
        return result instanceof Number number ? Math.max(0L, number.longValue()) : offeredMicroMj;
    }

    private static final class BridgeSink implements InvocationHandler
    {
        private static final int UNSIDED_INDEX = Direction.values().length;
        private static final int UNSIDED_BIT = 1 << UNSIDED_INDEX;
        private final ServerLevel level;
        private final BlockPos position;
        private final BlockEntity target;
        private final Ic2ToBuildCraftEnergyBridge owner;
        private Object proxy;
        private final long[] pendingMicroMj = new long[Direction.values().length + 1];
        private long availableMicroMj;
        private double euPerMj;
        private int receivingSideMask;

        private BridgeSink(ServerLevel level, BlockPos position, BlockEntity target, Ic2ToBuildCraftEnergyBridge owner)
        {
            this.level = level;
            this.position = position;
            this.target = target;
            this.owner = owner;
        }

        private void setProxy(Object proxy)
        {
            this.proxy = proxy;
        }

        private Object proxy()
        {
            return this.proxy;
        }

        private BlockPos position()
        {
            return this.position;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable
        {
            return switch (method.getName())
            {
                case "getWorldObj" -> this.level;
                case "getPosition" -> this.position;
                case "acceptsEnergyFrom" -> this.accepts(arguments);
                case "getDemandedEnergy" -> this.demand();
                case "getSinkTier" -> 4;
                case "injectEnergy" -> this.inject(arguments);
                case "onConnectionChange" -> null;
                case "toString" -> "BcIc2EnergySink[" + this.position + "]";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> arguments != null && arguments.length == 1 && proxy == arguments[0];
                default -> defaultValue(method.getReturnType());
            };
        }

        //World and optional-mod capability access must stay on the server thread. IC2 also
        //calls acceptsEnergyFrom/getDemandedEnergy/injectEnergy from its worker threads.
        private void tickServer()
        {
            this.flushPendingServer();
            double ratio = EnergyConversionService.euPerMegaJoule();
            int sideMask = 0;
            long requested = 0;
            if (BridgeConfig.ENERGY_BRIDGE_ENABLED.get())
            {
                for (int index = 0; index < this.pendingMicroMj.length; index++)
                {
                    Direction side = index == UNSIDED_INDEX ? null : Direction.values()[index];
                    Object receiver = this.owner.receiverFor(this.target, side);
                    if (receiver != null)
                    {
                        sideMask |= 1 << index;
                        requested = Math.max(requested, this.powerRequested(receiver));
                    }
                }
            }
            if (BridgeConfig.ENERGY_TRANSFER_LIMIT_MODE.get() == EnergyTransferLimitMode.MANUAL)
            {
                requested = Math.min(requested, EnergyConversionService.euToMicroMegaJoules(
                        BridgeConfig.ENERGY_TRANSFER_LIMIT_EU_PER_TICK.get(), ratio));
            }
            synchronized (this)
            {
                this.euPerMj = ratio;
                this.receivingSideMask = sideMask;
                this.availableMicroMj = Math.max(0, requested - this.totalPending());
            }
        }

        private synchronized boolean accepts(Object[] arguments)
        {
            Direction side = arguments != null && arguments.length > 1 && arguments[1] instanceof Direction value ? value : null;
            return this.acceptsDirection(side);
        }

        private synchronized double demand()
        {
            return EnergyConversionService.microMegaJoulesToEu(this.availableMicroMj, this.euPerMj);
        }

        private synchronized double inject(Object[] arguments)
        {
            if (arguments == null || arguments.length < 2 || !(arguments[1] instanceof Number number))
            {
                return 0;
            }
            double offeredEu = number.doubleValue();
            Direction side = arguments[0] instanceof Direction value ? value : null;
            if (!(offeredEu > 0) || !Double.isFinite(offeredEu) || !this.acceptsDirection(side))
            {
                return offeredEu;
            }
            long accepted = Math.min(EnergyConversionService.euToMicroMegaJoules(offeredEu, this.euPerMj),
                    Math.min(this.availableMicroMj, Long.MAX_VALUE - this.totalPending()));
            this.availableMicroMj -= accepted;
            this.pendingMicroMj[side == null ? UNSIDED_INDEX : side.ordinal()] += accepted;
            return Math.max(0, offeredEu - EnergyConversionService.microMegaJoulesToEu(accepted, this.euPerMj));
        }

        private boolean acceptsDirection(Direction side)
        {
            return (this.receivingSideMask & (UNSIDED_BIT | (side == null ? 0 : 1 << side.ordinal()))) != 0;
        }

        private long totalPending()
        {
            long total = 0;
            for (long queued : this.pendingMicroMj)
            {
                total += queued;
            }
            return total;
        }

        private void flushPendingServer()
        {
            for (int index = 0; index < this.pendingMicroMj.length; index++)
            {
                long queued;
                synchronized (this)
                {
                    queued = this.pendingMicroMj[index];
                }
                if (queued <= 0)
                {
                    continue;
                }
                Direction side = index == UNSIDED_INDEX ? null : Direction.values()[index];
                Object receiver = this.owner.receiverFor(this.target, side);
                if (receiver == null && side != null)
                {
                    receiver = this.owner.receiverFor(this.target, null);
                }
                long offered = Math.min(queued, this.powerRequested(receiver));
                if (offered > 0)
                {
                    try
                    {
                        long accepted = offered - Math.min(offered, this.owner.receivePower(receiver, offered));
                        synchronized (this)
                        {
                            this.pendingMicroMj[index] -= accepted;
                        }
                        if (accepted > 0)
                        {
                            this.target.setChanged();
                        }
                    }
                    catch (ReflectiveOperationException | RuntimeException exception)
                    {
                        //Retain the paid-for energy until the receiver can accept it.
                    }
                }
            }
        }

        private long powerRequested(Object receiver)
        {
            if (receiver == null)
            {
                return 0L;
            }
            try
            {
                return this.owner.canReceive(receiver) ? this.owner.powerRequested(receiver) : 0L;
            }
            catch (ReflectiveOperationException | RuntimeException exception)
            {
                return 0L;
            }
        }

        private static Object defaultValue(Class<?> type)
        {
            if (!type.isPrimitive())
            {
                return null;
            }
            if (type == boolean.class)
            {
                return false;
            }
            if (type == double.class)
            {
                return 0.0D;
            }
            if (type == float.class)
            {
                return 0.0F;
            }
            if (type == long.class)
            {
                return 0L;
            }
            if (type == int.class)
            {
                return 0;
            }
            if (type == short.class)
            {
                return (short) 0;
            }
            if (type == byte.class)
            {
                return (byte) 0;
            }
            if (type == char.class)
            {
                return '\0';
            }
            return null;
        }
    }
}
