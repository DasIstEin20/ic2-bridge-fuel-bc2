package dev.bcic2bridge;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns every BuildCraft {@code IMjPassiveProvider} into an IC2 energy source.
 *
 * <p>BuildCraft exposes producers and power-pipe inputs through its passive
 * provider capability. Registering an IC2 source at that same position makes
 * BC engines and any other compatible provider available to the IC2 EnergyNet
 * without per-machine adapters.</p>
 */
public final class BuildCraftToIc2EnergyBridge
{
    private static final Logger LOGGER = LoggerFactory.getLogger(BcIc2FuelBridge.MOD_ID);
    private static final String MJ_API = "buildcraft.api.mj.MjAPI";
    private static final String MJ_PASSIVE_PROVIDER = "buildcraft.api.mj.IMjPassiveProvider";
    private static final String IC2_SOURCE = "ic2.api.energy.tile.IEnergySource";
    private static final String IC2_LOCATABLE = "ic2.api.info.ILocatable";
    private static final String IC2_ENERGY_TILE = "ic2.api.energy.tile.IEnergyTile";
    private static final String IC2_LOAD_EVENT = "ic2.api.energy.event.EnergyTileLoadEvent";
    private static final String IC2_UNLOAD_EVENT = "ic2.api.energy.event.EnergyTileUnloadEvent";

    private final Map<ServerLevel, Map<BlockPos, BridgeSource>> sources = new HashMap<>();
    private final Map<ServerLevel, Set<BlockPos>> pendingPositions = new HashMap<>();
    private Capability<?> mjPassiveProviderCapability;
    private Class<?> mjPassiveProviderType;
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
        Map<BlockPos, BridgeSource> inLevel = this.sources.get(level);
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
        Map<BlockPos, BridgeSource> inLevel = this.sources.remove(level);
        this.pendingPositions.remove(level);
        if (inLevel != null)
        {
            for (BridgeSource source : inLevel.values())
            {
                this.postEnergyTileEvent(IC2_UNLOAD_EVENT, source.proxy());
            }
        }
    }

    private void queue(ServerLevel level, BlockPos pos)
    {
        this.pendingPositions.computeIfAbsent(level, ignored -> new HashSet<>()).add(pos.immutable());
    }

    private void register(ServerLevel level, BlockPos pos)
    {
        Map<BlockPos, BridgeSource> inLevel = this.sources.computeIfAbsent(level, ignored -> new HashMap<>());
        if (inLevel.containsKey(pos))
        {
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null || !this.hasMjPassiveProvider(blockEntity))
        {
            return;
        }

        try
        {
            BridgeSource source = new BridgeSource(level, pos.immutable(), blockEntity, this);
            source.setProxy(this.createIc2SourceProxy(source));
            if (this.postEnergyTileEvent(IC2_LOAD_EVENT, source.proxy()))
            {
                inLevel.put(source.position(), source);
                LOGGER.debug("Attached IC2 energy source to BuildCraft MJ provider at {} in {}.", pos, level.dimension().location());
            }
        }
        catch (ReflectiveOperationException | LinkageError exception)
        {
            if (!this.apiUnavailableLogged)
            {
                LOGGER.error("BuildCraft → IC2 energy bridge could not initialize: {}", exception.toString(), exception);
                this.apiUnavailableLogged = true;
            }
        }
    }

    private void unregister(ServerLevel level, BlockPos pos)
    {
        Map<BlockPos, BridgeSource> inLevel = this.sources.get(level);
        if (inLevel == null)
        {
            return;
        }
        BridgeSource source = inLevel.remove(pos);
        if (source == null)
        {
            return;
        }
        this.postEnergyTileEvent(IC2_UNLOAD_EVENT, source.proxy());
        if (inLevel.isEmpty())
        {
            this.sources.remove(level);
        }
    }

    private Object createIc2SourceProxy(BridgeSource source) throws ReflectiveOperationException
    {
        ClassLoader loader = BuildCraftToIc2EnergyBridge.class.getClassLoader();
        Class<?> sourceType = Class.forName(IC2_SOURCE, false, loader);
        Class<?> locatableType = Class.forName(IC2_LOCATABLE, false, loader);
        return Proxy.newProxyInstance(loader, new Class<?>[]{sourceType, locatableType}, source);
    }

    private boolean postEnergyTileEvent(String eventClassName, Object sourceProxy)
    {
        try
        {
            ClassLoader loader = BuildCraftToIc2EnergyBridge.class.getClassLoader();
            Class<?> energyTile = Class.forName(IC2_ENERGY_TILE, false, loader);
            Class<?> eventType = Class.forName(eventClassName, false, loader);
            Constructor<?> constructor = eventType.getConstructor(energyTile);
            Object event = constructor.newInstance(sourceProxy);
            MinecraftForge.EVENT_BUS.post((Event) event);
            return true;
        }
        catch (ReflectiveOperationException | ClassCastException | LinkageError exception)
        {
            if (!this.apiUnavailableLogged)
            {
                LOGGER.error("Could not post {} for the BuildCraft → IC2 energy bridge: {}", eventClassName, exception.toString(), exception);
                this.apiUnavailableLogged = true;
            }
            return false;
        }
    }

    private boolean hasMjPassiveProvider(BlockEntity blockEntity)
    {
        return this.providerFor(blockEntity, null) != null
                || EnumSet.allOf(Direction.class).stream().anyMatch(direction -> this.providerFor(blockEntity, direction) != null);
    }

    private Object providerFor(BlockEntity blockEntity, Direction direction)
    {
        try
        {
            Capability<?> capability = this.getMjPassiveProviderCapability();
            LazyOptional<?> optional = blockEntity.getCapability(capability, direction);
            Object provider = optional.orElse(null);
            return provider != null && this.getMjPassiveProviderType().isInstance(provider) ? provider : null;
        }
        catch (ReflectiveOperationException | RuntimeException | LinkageError exception)
        {
            return null;
        }
    }

    private Capability<?> getMjPassiveProviderCapability() throws ReflectiveOperationException
    {
        if (this.mjPassiveProviderCapability == null)
        {
            Field field = Class.forName(MJ_API, false, BuildCraftToIc2EnergyBridge.class.getClassLoader())
                    .getField("CAP_PASSIVE_PROVIDER");
            this.mjPassiveProviderCapability = (Capability<?>) field.get(null);
        }
        return this.mjPassiveProviderCapability;
    }

    private Class<?> getMjPassiveProviderType() throws ClassNotFoundException
    {
        if (this.mjPassiveProviderType == null)
        {
            this.mjPassiveProviderType = Class.forName(MJ_PASSIVE_PROVIDER, false, BuildCraftToIc2EnergyBridge.class.getClassLoader());
        }
        return this.mjPassiveProviderType;
    }

    private long extractPower(Object provider, long maximumMicroMj, boolean execute) throws ReflectiveOperationException
    {
        Method method = this.getMjPassiveProviderType().getMethod("extractPower", long.class, long.class, boolean.class);
        Object result = method.invoke(provider, 0L, maximumMicroMj, execute);
        return result instanceof Number number ? Math.max(0L, number.longValue()) : 0L;
    }

    private static final class BridgeSource implements InvocationHandler
    {
        private final ServerLevel level;
        private final BlockPos position;
        private final BlockEntity target;
        private final BuildCraftToIc2EnergyBridge owner;
        private Object proxy;
        private Direction selectedDirection;
        private double offeredEu;

        private BridgeSource(ServerLevel level, BlockPos position, BlockEntity target, BuildCraftToIc2EnergyBridge owner)
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
                case "emitsEnergyTo" -> this.emits(arguments);
                case "getOfferedEnergy" -> this.offer();
                case "drawEnergy" -> {
                    this.draw(arguments);
                    yield null;
                }
                case "getSourceTier" -> this.sourceTier();
                case "onConnectionChange" -> null;
                case "toString" -> "BcIc2EnergySource[" + this.position + "]";
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
            return this.owner.providerFor(this.target, direction) != null || this.owner.providerFor(this.target, null) != null;
        }

        private double offer()
        {
            this.offeredEu = 0.0D;
            this.selectedDirection = null;
            if (!this.isActive())
            {
                return 0.0D;
            }

            long maximumMicroMj = this.maximumMicroMj();
            if (maximumMicroMj <= 0L)
            {
                return 0.0D;
            }

            this.selectOffer(null, maximumMicroMj);
            for (Direction direction : Direction.values())
            {
                this.selectOffer(direction, maximumMicroMj);
            }
            return this.offeredEu;
        }

        private void selectOffer(Direction direction, long maximumMicroMj)
        {
            Object provider = this.owner.providerFor(this.target, direction);
            if (provider == null)
            {
                return;
            }
            try
            {
                long availableMicroMj = Math.min(maximumMicroMj, this.owner.extractPower(provider, maximumMicroMj, false));
                double availableEu = EnergyConversionService.microMegaJoulesToEu(availableMicroMj);
                if (availableEu > this.offeredEu)
                {
                    this.offeredEu = availableEu;
                    this.selectedDirection = direction;
                }
            }
            catch (ReflectiveOperationException | RuntimeException exception)
            {
                // A provider can disappear or be reconfigured between ticks. It
                // will be sampled again on the next EnergyNet update.
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
            long requestedMicroMj = Math.min(this.maximumMicroMj(), EnergyConversionService.euToMicroMegaJoules(Math.min(usedEu, this.offeredEu)));
            if (requestedMicroMj <= 0L)
            {
                return;
            }

            Object provider = this.owner.providerFor(this.target, this.selectedDirection);
            if (provider == null)
            {
                provider = this.owner.providerFor(this.target, null);
            }
            if (provider == null)
            {
                return;
            }
            try
            {
                this.owner.extractPower(provider, requestedMicroMj, true);
            }
            catch (ReflectiveOperationException | RuntimeException exception)
            {
                // The EnergyNet will query the source again next tick. Never let
                // a transient BuildCraft capability failure crash the server.
            }
        }

        private boolean isActive()
        {
            return BridgeConfig.BUILDCRAFT_TO_IC2_ENERGY_BRIDGE_ENABLED.get()
                    && !this.target.isRemoved()
                    && this.level.getBlockEntity(this.position) == this.target;
        }

        private long maximumMicroMj()
        {
            if (BridgeConfig.ENERGY_TRANSFER_LIMIT_MODE.get() != EnergyTransferLimitMode.MANUAL)
            {
                return Long.MAX_VALUE;
            }
            return EnergyConversionService.euToMicroMegaJoules(BridgeConfig.ENERGY_TRANSFER_LIMIT_EU_PER_TICK.get());
        }

        private int sourceTier()
        {
            if (!(this.offeredEu > 0.0D))
            {
                return 0;
            }
            // IC2 tier power is 8 * 4^tier EU/t. Use the smallest tier that
            // can carry this tick's actual offer, so cable voltage reflects the
            // converted BuildCraft output instead of a fixed arbitrary tier.
            return Math.max(0, (int) Math.ceil(Math.log(this.offeredEu / 8.0D) / Math.log(4.0D)));
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
