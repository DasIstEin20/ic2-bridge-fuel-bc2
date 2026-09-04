package mekanism.common.registration;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import mekanism.common.Mekanism;
import mekanism.common.TransporterContent;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryObject;

public class DoubleDeferredRegister<PRIMARY, SECONDARY> {

    private final DeferredRegister<PRIMARY> primaryRegister;
    private final DeferredRegister<SECONDARY> secondaryRegister;
    private final String modid;
    private final IForgeRegistry<PRIMARY> primaryForgeRegistry;
    private final IForgeRegistry<SECONDARY> secondaryForgeRegistry;
    private final ResourceKey<? extends Registry<PRIMARY>> primaryRegistryName;
    private final ResourceKey<? extends Registry<SECONDARY>> secondaryRegistryName;

    public DoubleDeferredRegister(DeferredRegister<PRIMARY> primaryRegistry, DeferredRegister<SECONDARY> secondaryRegistry) {
        this.primaryRegister = primaryRegistry;
        this.secondaryRegister = secondaryRegistry;
        this.modid = Mekanism.MODID;
        this.primaryForgeRegistry = null;
        this.secondaryForgeRegistry = null;
        this.primaryRegistryName = null;
        this.secondaryRegistryName = null;
    }

    public DoubleDeferredRegister(String modid, IForgeRegistry<PRIMARY> primaryRegistry, IForgeRegistry<SECONDARY> secondaryRegistry) {
        this.primaryRegister = DeferredRegister.create(primaryRegistry, modid);
        this.secondaryRegister = DeferredRegister.create(secondaryRegistry, modid);
        this.modid = modid;
        this.primaryForgeRegistry = primaryRegistry;
        this.secondaryForgeRegistry = secondaryRegistry;
        this.primaryRegistryName = null;
        this.secondaryRegistryName = null;
    }

    protected DoubleDeferredRegister(String modid, ResourceKey<? extends Registry<PRIMARY>> primaryRegistryName,
          ResourceKey<? extends Registry<SECONDARY>> secondaryRegistryName) {
        this.primaryRegister = DeferredRegister.create(primaryRegistryName, modid);
        this.secondaryRegister = DeferredRegister.create(secondaryRegistryName, modid);
        this.modid = modid;
        this.primaryForgeRegistry = null;
        this.secondaryForgeRegistry = null;
        this.primaryRegistryName = primaryRegistryName;
        this.secondaryRegistryName = secondaryRegistryName;
    }

    public <P extends PRIMARY, S extends SECONDARY, W extends DoubleWrappedRegistryObject<P, S>> W register(String name, Supplier<? extends P> primarySupplier,
          Supplier<? extends S> secondarySupplier, BiFunction<RegistryObject<P>, RegistryObject<S>, W> objectWrapper) {
        if (shouldRegister(name)) {
            return objectWrapper.apply(primaryRegister.register(name, primarySupplier), secondaryRegister.register(name, secondarySupplier));
        }
        return objectWrapper.apply(primaryReference(name), secondaryReference(name));
    }

    public <P extends PRIMARY, S extends SECONDARY, W extends DoubleWrappedRegistryObject<P, S>> W register(String name, Supplier<? extends P> primarySupplier,
          Function<P, S> secondarySupplier, BiFunction<RegistryObject<P>, RegistryObject<S>, W> objectWrapper) {
        return registerAdvanced(name, primarySupplier, secondarySupplier.compose(RegistryObject::get), objectWrapper);
    }

    public <P extends PRIMARY, S extends SECONDARY, W extends DoubleWrappedRegistryObject<P, S>> W registerAdvanced(String name, Supplier<? extends P> primarySupplier,
          Function<RegistryObject<P>, S> secondarySupplier, BiFunction<RegistryObject<P>, RegistryObject<S>, W> objectWrapper) {
        if (!shouldRegister(name)) {
            return objectWrapper.apply(primaryReference(name), secondaryReference(name));
        }
        RegistryObject<P> primaryObject = primaryRegister.register(name, primarySupplier);
        return objectWrapper.apply(primaryObject, secondaryRegister.register(name, () -> secondarySupplier.apply(primaryObject)));
    }

    private boolean shouldRegister(String name) {
        ResourceLocation registryLocation = primaryForgeRegistry == null ? primaryRegistryName == null ? null : primaryRegistryName.location() : primaryForgeRegistry.getRegistryName();
        return registryLocation == null || TransporterContent.shouldRegister(registryLocation, name);
    }

    private <P extends PRIMARY> RegistryObject<P> primaryReference(String name) {
        ResourceLocation id = new ResourceLocation(modid, name);
        return primaryForgeRegistry == null ? RegistryObject.create(id, primaryRegistryName, modid) : RegistryObject.create(id, primaryForgeRegistry);
    }

    private <S extends SECONDARY> RegistryObject<S> secondaryReference(String name) {
        ResourceLocation id = new ResourceLocation(modid, name);
        return secondaryForgeRegistry == null ? RegistryObject.create(id, secondaryRegistryName, modid) : RegistryObject.create(id, secondaryForgeRegistry);
    }

    public void register(IEventBus bus) {
        primaryRegister.register(bus);
        secondaryRegister.register(bus);
    }
}
