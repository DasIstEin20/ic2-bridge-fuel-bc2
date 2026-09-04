package dev.ic2universalenergy;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import ic2.api.energy.tile.IEnergyTile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Makes every non-IC2 Forge Energy receiver available as an IC2 EnergyNet
 * sink. This deliberately targets Forge's {@link IEnergyStorage} capability,
 * not a list of mod IDs, so the same bridge covers Refined Storage and future
 * FE machines without a per-mod adapter.
 *
 * <p>Forestry receivers are excluded here because {@link ForestryEnergyBridge}
 * owns their paired FE ↔ IC2 endpoint. Both paths share the exact same FE/EU
 * configuration, so the exclusion only prevents two IC2 sinks at one block.</p>
 */
public final class Ic2ToForgeEnergyBridge
{
    private static final Logger LOGGER = LoggerFactory.getLogger(Ic2UniversalEnergyExtension.MOD_ID);
    private static final String FORESTRY_MOD_ID = "forestry";
    private static final String IC2_MOD_ID = "ic2";
    private static final String IC2_SINK = "ic2.api.energy.tile.IEnergySink";
    private static final String IC2_LOCATABLE = "ic2.api.info.ILocatable";
    private static final String IC2_ENERGY_TILE = "ic2.api.energy.tile.IEnergyTile";
    private static final String IC2_LOAD_EVENT = "ic2.api.energy.event.EnergyTileLoadEvent";
    private static final String IC2_UNLOAD_EVENT = "ic2.api.energy.event.EnergyTileUnloadEvent";

    private final Map<ServerLevel, Map<BlockPos, ForgeEnergySink>> sinks = new HashMap<>();
    private final Map<ServerLevel, Set<BlockPos>> pendingPositions = new HashMap<>();
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
        Map<BlockPos, ForgeEnergySink> levelSinks = this.sinks.get(level);
        if (levelSinks == null)
        {
            return;
        }
        for (BlockPos position : Set.copyOf(levelSinks.keySet()))
        {
            if ((position.getX() >> 4) == chunkX && (position.getZ() >> 4) == chunkZ)
            {
                this.unregister(level, position);
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
        Map<BlockPos, ForgeEnergySink> levelSinks = this.sinks.get(level);
        if (levelSinks != null)
        {
            for (ForgeEnergySink sink : java.util.List.copyOf(levelSinks.values()))
            {
                sink.tickServer();
            }
        }

        Set<BlockPos> positions = this.pendingPositions.remove(level);
        if (positions != null)
        {
            for (BlockPos position : positions)
            {
                this.register(level, position);
            }
        }
    }

    private void onLevelUnload(LevelEvent.Unload event)
    {
        if (!(event.getLevel() instanceof ServerLevel level))
        {
            return;
        }
        Map<BlockPos, ForgeEnergySink> levelSinks = this.sinks.remove(level);
        this.pendingPositions.remove(level);
        if (levelSinks != null)
        {
            for (ForgeEnergySink sink : levelSinks.values())
            {
                this.postEnergyTileEvent(IC2_UNLOAD_EVENT, sink.proxy());
            }
        }
    }

    private void queue(ServerLevel level, BlockPos position)
    {
        this.pendingPositions.computeIfAbsent(level, ignored -> new HashSet<>()).add(position.immutable());
    }

    private void register(ServerLevel level, BlockPos position)
    {
        Map<BlockPos, ForgeEnergySink> levelSinks = this.sinks.computeIfAbsent(level, ignored -> new HashMap<>());
        if (levelSinks.containsKey(position))
        {
            return;
        }

        BlockEntity target = level.getBlockEntity(position);
        if (target == null || this.isExcludedEndpoint(target) || !this.hasReceivingStorage(target))
        {
            if (levelSinks.isEmpty())
            {
                this.sinks.remove(level);
            }
            return;
        }

        try
        {
            ForgeEnergySink sink = new ForgeEnergySink(level, position.immutable(), target, this);
            sink.setProxy(this.createIc2SinkProxy(sink));
            sink.tickServer();
            if (this.postEnergyTileEvent(IC2_LOAD_EVENT, sink.proxy()))
            {
                levelSinks.put(sink.position(), sink);
                LOGGER.debug("Attached IC2 energy sink to Forge Energy receiver {} at {} in {}.",
                        target.getType(), position, level.dimension().location());
            }
        }
        catch (ReflectiveOperationException | LinkageError exception)
        {
            this.logApiUnavailable("IC2 → Forge Energy bridge could not initialize", exception);
        }
        finally
        {
            if (levelSinks.isEmpty())
            {
                this.sinks.remove(level);
            }
        }
    }

    private void unregister(ServerLevel level, BlockPos position)
    {
        Map<BlockPos, ForgeEnergySink> levelSinks = this.sinks.get(level);
        if (levelSinks == null)
        {
            return;
        }
        ForgeEnergySink sink = levelSinks.remove(position);
        if (sink != null)
        {
            this.postEnergyTileEvent(IC2_UNLOAD_EVENT, sink.proxy());
        }
        if (levelSinks.isEmpty())
        {
            this.sinks.remove(level);
        }
    }

    private Object createIc2SinkProxy(ForgeEnergySink sink) throws ReflectiveOperationException
    {
        ClassLoader loader = Ic2ToForgeEnergyBridge.class.getClassLoader();
        Class<?> sinkType = Class.forName(IC2_SINK, false, loader);
        Class<?> locatableType = Class.forName(IC2_LOCATABLE, false, loader);
        return Proxy.newProxyInstance(loader, new Class<?>[]{sinkType, locatableType}, sink);
    }

    private boolean postEnergyTileEvent(String eventClassName, Object sinkProxy)
    {
        try
        {
            ClassLoader loader = Ic2ToForgeEnergyBridge.class.getClassLoader();
            Class<?> energyTile = Class.forName(IC2_ENERGY_TILE, false, loader);
            Class<?> eventType = Class.forName(eventClassName, false, loader);
            Constructor<?> constructor = eventType.getConstructor(energyTile);
            Object event = constructor.newInstance(sinkProxy);
            MinecraftForge.EVENT_BUS.post((Event) event);
            return true;
        }
        catch (ReflectiveOperationException | ClassCastException | LinkageError exception)
        {
            this.logApiUnavailable("Could not post " + eventClassName + " for the IC2 → Forge Energy bridge", exception);
            return false;
        }
    }

    private void logApiUnavailable(String message, Throwable exception)
    {
        if (!this.apiUnavailableLogged)
        {
            LOGGER.error("{}: {}", message, exception.toString(), exception);
            this.apiUnavailableLogged = true;
        }
    }

    private boolean isExcludedEndpoint(BlockEntity blockEntity)
    {
        ResourceLocation id = ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(blockEntity.getType());
        return blockEntity instanceof IEnergyTile
                || id != null && (FORESTRY_MOD_ID.equals(id.getNamespace()) || IC2_MOD_ID.equals(id.getNamespace()));
    }

    private boolean hasReceivingStorage(BlockEntity blockEntity)
    {
        return this.receivingStorageFor(blockEntity, null) != null
                || java.util.Arrays.stream(Direction.values()).anyMatch(direction -> this.receivingStorageFor(blockEntity, direction) != null);
    }

    private IEnergyStorage receivingStorageFor(BlockEntity blockEntity, Direction direction)
    {
        try
        {
            IEnergyStorage storage = blockEntity.getCapability(ForgeCapabilities.ENERGY, direction).orElse(null);
            return storage != null && storage.canReceive() ? storage : null;
        }
        catch (RuntimeException exception)
        {
            return null;
        }
    }

    private int maximumForgeEnergy(double forgeEnergyPerEu)
    {
        return BridgeConfig.ENERGY_TRANSFER_LIMIT_MODE.get() == EnergyTransferLimitMode.MANUAL
                ? EnergyConversionService.euToForgeEnergy(
                        BridgeConfig.ENERGY_TRANSFER_LIMIT_EU_PER_TICK.get(), forgeEnergyPerEu)
                : Integer.MAX_VALUE;
    }

    private static final class ForgeEnergySink implements InvocationHandler
    {
        private static final int UNSIDED_INDEX = Direction.values().length;
        private static final int UNSIDED_BIT = 1 << UNSIDED_INDEX;

        private final ServerLevel level;
        private final BlockPos position;
        private final BlockEntity target;
        private final Ic2ToForgeEnergyBridge owner;
        private final int[] pendingForgeEnergy = new int[Direction.values().length + 1];
        private Object proxy;
        private int availableForgeEnergy;
        private int receivingSideMask;
        private double forgeEnergyPerEu = EnergyConversionService.AUTO_FORGE_ENERGY_PER_EU;
        private boolean active;

        private ForgeEnergySink(ServerLevel level, BlockPos position, BlockEntity target, Ic2ToForgeEnergyBridge owner)
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

        /**
         * The only method in this proxy allowed to touch the world or an FE
         * capability. IC2's grid calculator only sees the primitive snapshot
         * produced here.
         */
        private void tickServer()
        {
            double ratio = EnergyConversionService.forgeEnergyPerEu();
            if (!BridgeConfig.IC2_TO_FORGE_ENERGY_BRIDGE_ENABLED.get()
                    || this.target.isRemoved() || this.level.getBlockEntity(this.position) != this.target)
            {
                synchronized (this)
                {
                    this.forgeEnergyPerEu = ratio;
                    this.availableForgeEnergy = 0;
                    this.receivingSideMask = 0;
                    this.active = false;
                }
                return;
            }

            this.flushPendingServer();

            int maximum = this.owner.maximumForgeEnergy(ratio);
            int requested = 0;
            int sideMask = 0;
            IEnergyStorage unsided = this.receivingStorage(null);
            if (unsided != null)
            {
                sideMask |= UNSIDED_BIT;
                requested = Math.max(requested, simulatedDemand(unsided, maximum));
            }
            for (Direction direction : Direction.values())
            {
                IEnergyStorage storage = this.receivingStorage(direction);
                if (storage != null)
                {
                    sideMask |= 1 << direction.ordinal();
                    requested = Math.max(requested, simulatedDemand(storage, maximum));
                }
            }

            synchronized (this)
            {
                this.forgeEnergyPerEu = ratio;
                this.receivingSideMask = sideMask;
                this.active = sideMask != 0;
                this.availableForgeEnergy = Math.max(0, requested - this.totalPending());
            }
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments)
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
                case "toString" -> "Ic2ForgeEnergySink[" + this.position + "]";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> arguments != null && arguments.length == 1 && proxy == arguments[0];
                default -> defaultValue(method.getReturnType());
            };
        }

        private boolean accepts(Object[] arguments)
        {
            Direction direction = arguments != null && arguments.length > 1 && arguments[1] instanceof Direction value ? value : null;
            synchronized (this)
            {
                return this.active && this.acceptsDirection(direction);
            }
        }

        private synchronized double demand()
        {
            return this.active
                    ? EnergyConversionService.forgeEnergyToEu(this.availableForgeEnergy, this.forgeEnergyPerEu)
                    : 0.0D;
        }

        private double inject(Object[] arguments)
        {
            if (arguments == null || arguments.length < 2 || !(arguments[1] instanceof Number number))
            {
                return 0.0D;
            }
            double offeredEu = number.doubleValue();
            if (!(offeredEu > 0.0D) || !Double.isFinite(offeredEu))
            {
                return offeredEu;
            }

            Direction direction = arguments[0] instanceof Direction value ? value : null;
            synchronized (this)
            {
                if (!this.active || !this.acceptsDirection(direction))
                {
                    return offeredEu;
                }
                int offeredEnergy = EnergyConversionService.euToForgeEnergy(offeredEu, this.forgeEnergyPerEu);
                int queueIndex = direction == null ? UNSIDED_INDEX : direction.ordinal();
                int queueCapacity = Integer.MAX_VALUE - this.totalPending();
                int acceptedEnergy = Math.min(offeredEnergy,
                        Math.min(this.availableForgeEnergy, queueCapacity));
                if (acceptedEnergy <= 0)
                {
                    return offeredEu;
                }
                this.availableForgeEnergy -= acceptedEnergy;
                this.pendingForgeEnergy[queueIndex] += acceptedEnergy;
                double acceptedEu = EnergyConversionService.forgeEnergyToEu(
                        acceptedEnergy, this.forgeEnergyPerEu);
                return Math.max(0.0D, offeredEu - acceptedEu);
            }
        }

        private IEnergyStorage receivingStorage(Direction direction)
        {
            return this.owner.receivingStorageFor(this.target, direction);
        }

        private void flushPendingServer()
        {
            for (int index = 0; index < this.pendingForgeEnergy.length; index++)
            {
                int queued;
                synchronized (this)
                {
                    queued = this.pendingForgeEnergy[index];
                }
                if (queued <= 0)
                {
                    continue;
                }

                Direction direction = index == UNSIDED_INDEX ? null : Direction.values()[index];
                IEnergyStorage storage = this.receivingStorage(direction);
                if (storage == null && direction != null)
                {
                    storage = this.receivingStorage(null);
                }
                if (storage == null)
                {
                    continue;
                }

                int inserted;
                try
                {
                    inserted = Math.max(0, Math.min(queued, storage.receiveEnergy(queued, false)));
                }
                catch (RuntimeException ignored)
                {
                    inserted = 0;
                }
                if (inserted > 0)
                {
                    synchronized (this)
                    {
                        this.pendingForgeEnergy[index] = Math.max(0,
                                this.pendingForgeEnergy[index] - inserted);
                    }
                }
            }
        }

        private boolean acceptsDirection(Direction direction)
        {
            int directionBit = direction == null ? 0 : 1 << direction.ordinal();
            return (this.receivingSideMask & (directionBit | UNSIDED_BIT)) != 0;
        }

        private int totalPending()
        {
            long total = 0;
            for (int queued : this.pendingForgeEnergy)
            {
                total += queued;
            }
            return total >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
        }

        private static int simulatedDemand(IEnergyStorage storage, int maximum)
        {
            try
            {
                return Math.max(0, Math.min(maximum, storage.receiveEnergy(maximum, true)));
            }
            catch (RuntimeException ignored)
            {
                return 0;
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
