package dev.ic2universalenergy.datagen;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

/** Resolves mandatory IC2 ingredients while generating Transporter recipes. */
public final class Ic2RecipeIngredients
{
    private Ic2RecipeIngredients()
    {
    }

    public static Item item(String path)
    {
        ResourceLocation id = new ResourceLocation("ic2", path);
        Item item = ForgeRegistries.ITEMS.getValue(id);
        if (item == null || item == Items.AIR)
        {
            throw new IllegalStateException("Required IC2 recipe ingredient is not registered: " + id);
        }
        return item;
    }
}
