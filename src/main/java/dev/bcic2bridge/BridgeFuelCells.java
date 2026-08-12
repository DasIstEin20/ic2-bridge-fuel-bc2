package dev.bcic2bridge;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * IC2-compatible filled cells for BuildCraft CE's combustion fuels.
 *
 * <p>Each item is created as IC2's {@code ItemClassicCell} through reflection.
 * Its constructor registers the filled item in IC2's empty-cell map, allowing
 * IC2's Canner and normal fluid-container handling to fill and empty it.</p>
 */
public final class BridgeFuelCells
{
    private static final String BUILDCRAFT_ENERGY = "buildcraftenergy";
    private static final ResourceLocation IC2_TOOLS_TAB = ResourceLocation.fromNamespaceAndPath("ic2", "tools_and_utilities");
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, BcIc2FuelBridge.MOD_ID);

    public static final RegistryObject<Item> OIL_CELL = registerCell("oil_cell", "oil");
    public static final RegistryObject<Item> OIL_DISTILLED_CELL = registerCell("oil_distilled_cell", "oil_distilled");
    public static final RegistryObject<Item> OIL_HEAVY_CELL = registerCell("oil_heavy_cell", "oil_heavy");
    public static final RegistryObject<Item> OIL_DENSE_CELL = registerCell("oil_dense_cell", "oil_dense");
    public static final RegistryObject<Item> FUEL_GASEOUS_CELL = registerCell("fuel_gaseous_cell", "fuel_gaseous");
    public static final RegistryObject<Item> FUEL_LIGHT_CELL = registerCell("fuel_light_cell", "fuel_light");
    public static final RegistryObject<Item> FUEL_DENSE_CELL = registerCell("fuel_dense_cell", "fuel_dense");
    public static final RegistryObject<Item> FUEL_MIXED_LIGHT_CELL = registerCell("fuel_mixed_light_cell", "fuel_mixed_light");
    public static final RegistryObject<Item> FUEL_MIXED_HEAVY_CELL = registerCell("fuel_mixed_heavy_cell", "fuel_mixed_heavy");

    private static final List<RegistryObject<Item>> ALL_CELLS = List.of(
            OIL_CELL,
            OIL_DISTILLED_CELL,
            OIL_HEAVY_CELL,
            OIL_DENSE_CELL,
            FUEL_GASEOUS_CELL,
            FUEL_LIGHT_CELL,
            FUEL_DENSE_CELL,
            FUEL_MIXED_LIGHT_CELL,
            FUEL_MIXED_HEAVY_CELL
    );

    private BridgeFuelCells()
    {
    }

    public static void register(IEventBus modEventBus)
    {
        ITEMS.register(modEventBus);
        modEventBus.addListener(BridgeFuelCells::addToCreativeTab);
    }

    private static RegistryObject<Item> registerCell(String cellId, String fluidPath)
    {
        return ITEMS.register(cellId, () -> createIc2Cell(ResourceLocation.fromNamespaceAndPath(BUILDCRAFT_ENERGY, fluidPath)));
    }

    private static Item createIc2Cell(ResourceLocation fluidId)
    {
        Fluid fluid = ForgeRegistries.FLUIDS.getValue(fluidId);
        if (fluid == null)
        {
            throw new IllegalStateException(
                    "BuildCraft CE fluid " + fluidId + " was not registered before IC2 fuel cells were created."
            );
        }

        try
        {
            Class<?> cellClass = Class.forName("ic2.core.item.ItemClassicCell", true, BridgeFuelCells.class.getClassLoader());
            Constructor<?> constructor = cellClass.getConstructor(Item.Properties.class, Fluid.class, int.class);
            Object cell = constructor.newInstance(new Item.Properties(), fluid, 1);
            if (cell instanceof Item item)
            {
                return item;
            }
            throw new IllegalStateException("IC2 ItemClassicCell constructor returned " + cell.getClass().getName());
        }
        catch (ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException exception)
        {
            throw new IllegalStateException("Could not construct IC2 fuel cell for " + fluidId + ".", exception);
        }
        catch (InvocationTargetException exception)
        {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            throw new IllegalStateException("IC2 rejected fuel cell for " + fluidId + ".", cause);
        }
    }

    private static void addToCreativeTab(BuildCreativeModeTabContentsEvent event)
    {
        if (!event.getTabKey().location().equals(IC2_TOOLS_TAB))
        {
            return;
        }

        for (RegistryObject<Item> cell : ALL_CELLS)
        {
            event.accept(cell);
        }
    }
}
