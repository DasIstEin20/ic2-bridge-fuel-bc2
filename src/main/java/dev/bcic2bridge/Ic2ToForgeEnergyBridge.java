package dev.bcic2bridge;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
    private static final Logger LOGGER = LoggerFactory.getLogger(BcIc2FuelBridge.MOD_ID);
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
        Set<BlockPos> positions = this.pendingPositions.remove(level);
        if (positions == null)
        {
            return;
        }
        for (BlockPos position : positions)
        {
            this.register(level, position);
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
        return id != null && (FORESTRY_MOD_ID.equals(id.getNamespace()) || IC2_MOD_ID.equals(id.getNamespace()));
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

    private int maximumForgeEnergy()
    {
        return BridgeConfig.ENERGY_TRANSFER_LIMIT_MODE.get() == EnergyTransferLimitMode.MANUAL
                ? EnergyConversionService.euToForgeEnergy(BridgeConfig.ENERGY_TRANSFER_LIMIT_EU_PER_TICK.get())
                : Integer.MAX_VALUE;
    }

    private static final class ForgeEnergySink implements InvocationHandler
    {
        private final ServerLevel level;
        private final BlockPos position;
        private final BlockEntity target;
        private final Ic2ToForgeEnergyBridge owner;
        private Object proxy;

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
            if (!this.isActive())
            {
                return false;
            }
            Direction direction = arguments != null && arguments.length > 1 && arguments[1] instanceof Direction value ? value : null;
            return this.receivingStorage(direction) != null || this.receivingStorage(null) != null;
        }

        private double demand()
        {
            if (!this.isActive())
            {
                return 0.0D;
            }
            int maximumEnergy = this.owner.maximumForgeEnergy();
            int requestedEnergy = this.requestedEnergy(null, maximumEnergy);
            for (Direction direction : Direction.values())
            {
                requestedEnergy = Math.max(requestedEnergy, this.requestedEnergy(direction, maximumEnergy));
            }
            return EnergyConversionService.forgeEnergyToEu(requestedEnergy);
        }

        private int requestedEnergy(Direction direction, int maximumEnergy)
        {
            IEnergyStorage storage = this.receivingStorage(direction);
            return storage == null ? 0 : Math.max(0, storage.receiveEnergy(maximumEnergy, true));
        }

        private double inject(Object[] arguments)
        {
            if (arguments == null || arguments.length < 2 || !(arguments[1] instanceof Number number))
            {
                return 0.0D;
            }
            double offeredEu = number.doubleValue();
            if (!(offeredEu > 0.0D) || !Double.isFinite(offeredEu) || !this.isActive())
            {
                return offeredEu;
            }

            Direction direction = arguments[0] instanceof Direction value ? value : null;
            IEnergyStorage storage = this.receivingStorage(direction);
            if (storage == null)
            {
                storage = this.receivingStorage(null);
            }
            if (storage == null)
            {
                return offeredEu;
            }

            double transferableEu = Math.min(offeredEu, this.demand());
            int offeredEnergy = Math.min(this.owner.maximumForgeEnergy(), EnergyConversionService.euToForgeEnergy(transferableEu));
            if (offeredEnergy <= 0)
            {
                return offeredEu;
            }
            int acceptedEnergy = Math.max(0, storage.receiveEnergy(offeredEnergy, false));
            double acceptedEu = EnergyConversionService.forgeEnergyToEu(Math.min(offeredEnergy, acceptedEnergy));
            return Math.max(0.0D, offeredEu - acceptedEu);
        }

        private boolean isActive()
        {
            return BridgeConfig.IC2_TO_FORGE_ENERGY_BRIDGE_ENABLED.get()
                    && !this.target.isRemoved()
                    && this.level.getBlockEntity(this.position) == this.target;
        }

        private IEnergyStorage receivingStorage(Direction direction)
        {
            return this.owner.receivingStorageFor(this.target, direction);
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
