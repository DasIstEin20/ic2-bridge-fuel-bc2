package dev.bcic2bridge.client;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.model.geometry.IGeometryLoader;

/** Parses the small, clipped BuildCraft-fluid overlay used by IC2 fuel cells. */
public final class FuelCellItemModelLoader implements IGeometryLoader<FuelCellItemModel>
{
    @Override
    public FuelCellItemModel read(JsonObject json, JsonDeserializationContext context)
    {
        ResourceLocation base = ResourceLocation.parse(json.get("base").getAsString());
        ResourceLocation fluid = ResourceLocation.parse(json.get("fluid").getAsString());
        return new FuelCellItemModel(base, fluid);
    }
}
