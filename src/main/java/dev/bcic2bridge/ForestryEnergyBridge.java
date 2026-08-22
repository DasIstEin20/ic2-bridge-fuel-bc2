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
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bridges Forestry's Forge Energy endpoints to IC2's EnergyNet in both
 * directions. Forestry 2.10 exposes energy through Forge's standard
 * {@link IEnergyStorage} capability, so this adapter intentionally discovers
 * every Forestry block entity dynamically instead of maintaining a brittle
 * machine list.
 *
 * <p>No Forestry type is linked directly. The optional integration therefore
 * stays safe when Forestry is absent, while still covering future Forestry
 * machines that expose the same capability.</p>
 */
public final class ForestryEnergyBridge
{
    private static final Logger LOGGER = LoggerFactory.getLogger(BcIc2FuelBridge.MOD_ID);
    private static final String FORESTRY_MOD_ID = "forestry";
    private static final String IC2_SINK = "ic2.api.energy.tile.IEnergySink";
    private static final String IC2_SOURCE = "ic2.api.energy.tile.IEnergySource";
    private static final String IC2_LOCATABLE = "ic2.api.info.ILocatable";
    private static final String IC2_ENERGY_TILE = "ic2.api.energy.tile.IEnergyTile";
    private static final String IC2_LOAD_EVENT = "ic2.api.energy.event.EnergyTileLoadEvent";
    private static final String IC2_UNLOAD_EVENT = "ic2.api.energy.event.EnergyTileUnloadEvent";

    private final Map<ServerLevel, Map<BlockPos, ForestrySink>> sinks = new HashMap<>();
    private final Map<ServerLevel, Map<BlockPos, ForestrySource>> sources = new HashMap<>();
    private final Map<ServerLevel, Set<BlockPos>> pendingPositions = new HashMap<>();
    private boolean apiUnavailableLogged;
    private boolean registered;

    public static boolean isForestryInstalled()
    {
        try
        {
            return ModList.get().isLoaded(FORESTRY_MOD_ID);
        }
        catch (RuntimeException exception)
        {
            return false;
        }
    }

    public synchronized void register()
    {
        if (this.registered || !isForestryInstalled())
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
            if (this.isForestryBlockEntity(blockEntity))
            {
                this.queue(level, blockEntity.getBlockPos());
            }
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
        this.unregisterChunk(level, chunkX, chunkZ);
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

        Map<BlockPos, ForestrySink> levelSinks = this.sinks.remove(level);
        if (levelSinks != null)
        {
            for (ForestrySink sink : levelSinks.values())
            {
                this.postEnergyTileEvent(IC2_UNLOAD_EVENT, sink.proxy());
            }
        }

        Map<BlockPos, ForestrySource> levelSources = this.sources.remove(level);
        if (levelSources != null)
        {
            for (ForestrySource source : levelSources.values())
            {
                this.postEnergyTileEvent(IC2_UNLOAD_EVENT, source.proxy());
            }
        }
        this.pendingPositions.remove(level);
    }

    private void queue(ServerLevel level, BlockPos position)
    {
        this.pendingPositions.computeIfAbsent(level, ignored -> new HashSet<>()).add(position.immutable());
    }

    private void register(ServerLevel level, BlockPos position)
    {
        BlockEntity blockEntity = level.getBlockEntity(position);
        if (blockEntity == null || !this.isForestryBlockEntity(blockEntity))
        {
            return;
        }
        this.registerSink(level, position, blockEntity);
        this.registerSource(level, position, blockEntity);
    }

    private void registerSink(ServerLevel level, BlockPos position, BlockEntity target)
    {
        Map<BlockPos, ForestrySink> levelSinks = this.sinks.computeIfAbsent(level, ignored -> new HashMap<>());
        if (levelSinks.containsKey(position) || !this.hasEnergyStorage(target, true))
        {
            return;
        }

        try
        {
            ForestrySink sink = new ForestrySink(level, position.immutable(), target, this);
            sink.setProxy(this.createIc2Proxy(IC2_SINK, sink));
            if (this.postEnergyTileEvent(IC2_LOAD_EVENT, sink.proxy()))
            {
                levelSinks.put(sink.position(), sink);
                LOGGER.debug("Attached IC2 energy sink to Forestry FE receiver at {} in {}.", position, level.dimension().location());
            }
        }
        catch (ReflectiveOperationException | LinkageError exception)
        {
            this.logApiUnavailable("IC2 → Forestry energy bridge could not initialize", exception);
        }
        finally
        {
            if (levelSinks.isEmpty())
            {
                this.sinks.remove(level);
            }
        }
    }

    private void registerSource(ServerLevel level, BlockPos position, BlockEntity target)
    {
        Map<BlockPos, ForestrySource> levelSources = this.sources.computeIfAbsent(level, ignored -> new HashMap<>());
        if (levelSources.containsKey(position) || !this.hasEnergyStorage(target, false))
        {
            return;
        }

        try
        {
            ForestrySource source = new ForestrySource(level, position.immutable(), target, this);
            source.setProxy(this.createIc2Proxy(IC2_SOURCE, source));
            if (this.postEnergyTileEvent(IC2_LOAD_EVENT, source.proxy()))
            {
                levelSources.put(source.position(), source);
                LOGGER.debug("Attached IC2 energy source to Forestry FE producer at {} in {}.", position, level.dimension().location());
            }
        }
        catch (ReflectiveOperationException | LinkageError exception)
        {
            this.logApiUnavailable("Forestry → IC2 energy bridge could not initialize", exception);
        }
        finally
        {
            if (levelSources.isEmpty())
            {
                this.sources.remove(level);
            }
        }
    }

    private void unregisterChunk(ServerLevel level, int chunkX, int chunkZ)
    {
        Set<BlockPos> positions = new HashSet<>();
        this.collectPositionsInChunk(this.sinks.get(level), chunkX, chunkZ, positions);
        this.collectPositionsInChunk(this.sources.get(level), chunkX, chunkZ, positions);
        for (BlockPos position : positions)
        {
            this.unregister(level, position);
        }
    }

    private void collectPositionsInChunk(Map<BlockPos, ?> endpoints, int chunkX, int chunkZ, Set<BlockPos> result)
    {
        if (endpoints == null)
        {
            return;
        }
        for (BlockPos position : endpoints.keySet())
        {
            if ((position.getX() >> 4) == chunkX && (position.getZ() >> 4) == chunkZ)
            {
                result.add(position);
            }
        }
    }

    private void unregister(ServerLevel level, BlockPos position)
    {
        Map<BlockPos, ForestrySink> levelSinks = this.sinks.get(level);
        if (levelSinks != null)
        {
            ForestrySink sink = levelSinks.remove(position);
            if (sink != null)
            {
                this.postEnergyTileEvent(IC2_UNLOAD_EVENT, sink.proxy());
            }
            if (levelSinks.isEmpty())
            {
                this.sinks.remove(level);
            }
        }

        Map<BlockPos, ForestrySource> levelSources = this.sources.get(level);
        if (levelSources != null)
        {
            ForestrySource source = levelSources.remove(position);
            if (source != null)
            {
                this.postEnergyTileEvent(IC2_UNLOAD_EVENT, source.proxy());
            }
            if (levelSources.isEmpty())
            {
                this.sources.remove(level);
            }
        }
    }

    private Object createIc2Proxy(String endpointClassName, InvocationHandler handler) throws ReflectiveOperationException
    {
        ClassLoader loader = ForestryEnergyBridge.class.getClassLoader();
        Class<?> endpointType = Class.forName(endpointClassName, false, loader);
        Class<?> locatableType = Class.forName(IC2_LOCATABLE, false, loader);
        return Proxy.newProxyInstance(loader, new Class<?>[]{endpointType, locatableType}, handler);
    }

    private boolean postEnergyTileEvent(String eventClassName, Object energyTileProxy)
    {
        try
        {
            ClassLoader loader = ForestryEnergyBridge.class.getClassLoader();
            Class<?> energyTile = Class.forName(IC2_ENERGY_TILE, false, loader);
            Class<?> eventType = Class.forName(eventClassName, false, loader);
            Constructor<?> constructor = eventType.getConstructor(energyTile);
            Object event = constructor.newInstance(energyTileProxy);
            MinecraftForge.EVENT_BUS.post((Event) event);
            return true;
        }
        catch (ReflectiveOperationException | ClassCastException | LinkageError exception)
        {
            this.logApiUnavailable("Could not post " + eventClassName + " for the Forestry energy bridge", exception);
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

    private boolean isForestryBlockEntity(BlockEntity blockEntity)
    {
        ResourceLocation id = ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(blockEntity.getType());
        return id != null && FORESTRY_MOD_ID.equals(id.getNamespace());
    }

    private boolean hasEnergyStorage(BlockEntity blockEntity, boolean receiving)
    {
        IEnergyStorage unsided = this.energyStorageFor(blockEntity, null);
        if (this.supportsDirection(unsided, receiving))
        {
            return true;
        }
        for (Direction direction : Direction.values())
        {
            if (this.supportsDirection(this.energyStorageFor(blockEntity, direction), receiving))
            {
                return true;
            }
        }
        return false;
    }

    private boolean supportsDirection(IEnergyStorage storage, boolean receiving)
    {
        return storage != null && (receiving ? storage.canReceive() : storage.canExtract());
    }

    private IEnergyStorage energyStorageFor(BlockEntity blockEntity, Direction direction)
    {
        try
        {
            return blockEntity.getCapability(ForgeCapabilities.ENERGY, direction).orElse(null);
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

    private abstract static class ForestryEndpoint implements InvocationHandler
    {
        protected final ServerLevel level;
        protected final BlockPos position;
        protected final BlockEntity target;
        protected final ForestryEnergyBridge owner;
        private Object proxy;

        private ForestryEndpoint(ServerLevel level, BlockPos position, BlockEntity target, ForestryEnergyBridge owner)
        {
            this.level = level;
            this.position = position;
            this.target = target;
            this.owner = owner;
        }

        protected void setProxy(Object proxy)
        {
            this.proxy = proxy;
        }

        protected Object proxy()
        {
            return this.proxy;
        }

        protected BlockPos position()
        {
            return this.position;
        }

        protected boolean targetIsPresent()
        {
            return !this.target.isRemoved() && this.level.getBlockEntity(this.position) == this.target;
        }

        protected static Object defaultValue(Class<?> type)
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

    private static final class ForestrySink extends ForestryEndpoint
    {
        private ForestrySink(ServerLevel level, BlockPos position, BlockEntity target, ForestryEnergyBridge owner)
        {
            super(level, position, target, owner);
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
                case "toString" -> "ForestryIc2EnergySink[" + this.position + "]";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> arguments != null && arguments.length == 1 && proxy == arguments[0];
                default -> defaultValue(method.getReturnType());
            };
        }

        private boolean accepts(Object[] arguments)
        {
            if (!BridgeConfig.IC2_TO_FORGE_ENERGY_BRIDGE_ENABLED.get() || !this.targetIsPresent())
            {
                return false;
            }
            Direction direction = arguments != null && arguments.length > 1 && arguments[1] instanceof Direction value ? value : null;
            return this.receivingStorage(direction) != null || this.receivingStorage(null) != null;
        }

        private double demand()
        {
            if (!BridgeConfig.IC2_TO_FORGE_ENERGY_BRIDGE_ENABLED.get() || !this.targetIsPresent())
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
            if (!(offeredEu > 0.0D) || !Double.isFinite(offeredEu)
                    || !BridgeConfig.IC2_TO_FORGE_ENERGY_BRIDGE_ENABLED.get() || !this.targetIsPresent())
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

        private IEnergyStorage receivingStorage(Direction direction)
        {
            IEnergyStorage storage = this.owner.energyStorageFor(this.target, direction);
            return storage != null && storage.canReceive() ? storage : null;
        }
    }

    private static final class ForestrySource extends ForestryEndpoint
    {
        private Direction selectedDirection;
        private double offeredEu;

        private ForestrySource(ServerLevel level, BlockPos position, BlockEntity target, ForestryEnergyBridge owner)
        {
            super(level, position, target, owner);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments)
        {
            return switch (method.getName())
            {
                case "getWorldObj" -> this.level;
                case "getPosition" -> this.position;
                case "emitsEnergyTo" -> this.emits(arguments);
                case "getOfferedEnergy" -> this.offer();
                case "drawEnergy" -> {
                    this.draw(arguments);
                    yield null;
                }
                case "getSourceTier" -> this.sourceTier();
                case "onConnectionChange" -> null;
                case "toString" -> "ForestryIc2EnergySource[" + this.position + "]";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> arguments != null && arguments.length == 1 && proxy == arguments[0];
                default -> defaultValue(method.getReturnType());
            };
        }

        private boolean emits(Object[] arguments)
        {
            if (!this.isActive())
            {
                return false;
            }
            Direction direction = arguments != null && arguments.length > 1 && arguments[1] instanceof Direction value ? value : null;
            return this.extractingStorage(direction) != null || this.extractingStorage(null) != null;
        }

        private double offer()
        {
            this.offeredEu = 0.0D;
            this.selectedDirection = null;
            if (!this.isActive())
            {
                return 0.0D;
            }

            int maximumEnergy = this.owner.maximumForgeEnergy();
            this.selectOffer(null, maximumEnergy);
            for (Direction direction : Direction.values())
            {
                this.selectOffer(direction, maximumEnergy);
            }
            return this.offeredEu;
        }

        private void selectOffer(Direction direction, int maximumEnergy)
        {
            IEnergyStorage storage = this.extractingStorage(direction);
            if (storage == null)
            {
                return;
            }
            int availableEnergy = Math.max(0, storage.extractEnergy(maximumEnergy, true));
            double availableEu = EnergyConversionService.forgeEnergyToEu(availableEnergy);
            if (availableEu > this.offeredEu)
            {
                this.offeredEu = availableEu;
                this.selectedDirection = direction;
            }
        }

        private void draw(Object[] arguments)
        {
            if (!this.isActive() || arguments == null || arguments.length == 0 || !(arguments[0] instanceof Number number))
            {
                return;
            }
            double usedEu = number.doubleValue();
            if (!(usedEu > 0.0D) || !Double.isFinite(usedEu))
            {
                return;
            }

            int requestedEnergy = Math.min(this.owner.maximumForgeEnergy(), EnergyConversionService.euToForgeEnergy(Math.min(usedEu, this.offeredEu)));
            if (requestedEnergy <= 0)
            {
                return;
            }
            IEnergyStorage storage = this.extractingStorage(this.selectedDirection);
            if (storage == null)
            {
                storage = this.extractingStorage(null);
            }
            if (storage == null)
            {
                return;
            }
            int extractedEnergy = Math.max(0, storage.extractEnergy(requestedEnergy, false));
            this.offeredEu = Math.max(0.0D, this.offeredEu
                    - EnergyConversionService.forgeEnergyToEu(Math.min(requestedEnergy, extractedEnergy)));
        }

        private boolean isActive()
        {
            return BridgeConfig.FORESTRY_TO_IC2_ENERGY_BRIDGE_ENABLED.get() && this.targetIsPresent();
        }

        private IEnergyStorage extractingStorage(Direction direction)
        {
            IEnergyStorage storage = this.owner.energyStorageFor(this.target, direction);
            return storage != null && storage.canExtract() ? storage : null;
        }

        private int sourceTier()
        {
            if (!(this.offeredEu > 0.0D))
            {
                return 0;
            }
            return Math.max(0, (int) Math.ceil(Math.log(this.offeredEu / 8.0D) / Math.log(4.0D)));
        }
    }
}
