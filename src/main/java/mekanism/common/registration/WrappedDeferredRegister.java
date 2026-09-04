package mekanism.common.registration;

import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import mekanism.common.Mekanism;
import mekanism.common.TransporterContent;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryBuilder;
import net.minecraftforge.registries.RegistryObject;

public class WrappedDeferredRegister<T> {

    protected final DeferredRegister<T> internal;
    private final String modid;
    private final IForgeRegistry<T> forgeRegistry;
    private final ResourceKey<? extends Registry<T>> registryName;

    protected WrappedDeferredRegister(DeferredRegister<T> internal) {
        this.internal = internal;
        this.modid = Mekanism.MODID;
        this.forgeRegistry = null;
        this.registryName = null;
    }

    protected WrappedDeferredRegister(String modid, IForgeRegistry<T> registry) {
        this.internal = DeferredRegister.create(registry, modid);
        this.modid = modid;
        this.forgeRegistry = registry;
        this.registryName = null;
    }

    /**
     * @apiNote For use with vanilla or custom registries
     */
    protected WrappedDeferredRegister(String modid, ResourceKey<? extends Registry<T>> registryName) {
        this.internal = DeferredRegister.create(registryName, modid);
        this.modid = modid;
        this.forgeRegistry = null;
        this.registryName = registryName;
    }

    protected <I extends T, W extends WrappedRegistryObject<I>> W register(String name, Supplier<? extends I> sup, Function<RegistryObject<I>, W> objectWrapper) {
        ResourceLocation registryLocation = forgeRegistry == null ? registryName == null ? null : registryName.location() : forgeRegistry.getRegistryName();
        if (registryLocation == null || TransporterContent.shouldRegister(registryLocation, name)) {
            return objectWrapper.apply(internal.register(name, sup));
        }
        ResourceLocation id = new ResourceLocation(modid, name);
        RegistryObject<I> reference = forgeRegistry == null ? RegistryObject.create(id, registryName, modid) : RegistryObject.create(id, forgeRegistry);
        return objectWrapper.apply(reference);
    }

    public void register(IEventBus bus) {
        internal.register(bus);
    }

    /**
     * Only call this from mekanism and for custom registries
     */
    public Supplier<IForgeRegistry<T>> createAndRegister(IEventBus bus) {
        return createAndRegister(bus, UnaryOperator.identity());
    }

    /**
     * Only call this from mekanism and for custom chemical registries
     */
    public Supplier<IForgeRegistry<T>> createAndRegisterChemical(IEventBus bus) {
        return createAndRegister(bus, builder -> builder.hasTags().setDefaultKey(Mekanism.rl("empty")));
    }

    /**
     * Only call this from mekanism and for custom registries
     */
    public Supplier<IForgeRegistry<T>> createAndRegister(IEventBus bus, UnaryOperator<RegistryBuilder<T>> builder) {
        Supplier<IForgeRegistry<T>> registry = internal.makeRegistry(() -> builder.apply(new RegistryBuilder<>()));
        register(bus);
        return registry;
    }
}
