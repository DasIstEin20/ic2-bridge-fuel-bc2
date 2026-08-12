package dev.bcic2bridge.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.geometry.IGeometryBakingContext;
import net.minecraftforge.client.model.geometry.IUnbakedGeometry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Bakes the normal IC2 cell as the base model and adds the BuildCraft fluid only
 * in the 2x10-pixel transparent sight window of {@code fluid_cell.png}.
 */
public final class FuelCellItemModel implements IUnbakedGeometry<FuelCellItemModel>
{
    private static final float WINDOW_X_START = 7.0F / 16.0F;
    private static final float WINDOW_X_END = 9.0F / 16.0F;
    private static final float WINDOW_Y_START = 3.0F / 16.0F;
    private static final float WINDOW_Y_END = 13.0F / 16.0F;
    private static final float FRONT_LAYER = 7.499F / 16.0F;

    private final ResourceLocation baseModel;
    private final ResourceLocation fluidTexture;

    FuelCellItemModel(ResourceLocation baseModel, ResourceLocation fluidTexture)
    {
        this.baseModel = baseModel;
        this.fluidTexture = fluidTexture;
    }

    @Override
    public BakedModel bake(
            IGeometryBakingContext owner,
            ModelBaker baker,
            Function<Material, TextureAtlasSprite> spriteGetter,
            ModelState modelTransform,
            ItemOverrides overrides,
            ResourceLocation modelLocation
    )
    {
        BakedModel bakedBase = baker.bake(this.baseModel, modelTransform, spriteGetter);
        if (bakedBase == null)
        {
            throw new IllegalStateException("Missing IC2 base cell model " + this.baseModel);
        }

        TextureAtlasSprite fluidSprite = spriteGetter.apply(new Material(TextureAtlas.LOCATION_BLOCKS, this.fluidTexture));
        BakedQuad overlay = createWindowQuad(fluidSprite);
        return new BakedFuelCellModel(bakedBase, overlay);
    }

    private static BakedQuad createWindowQuad(TextureAtlasSprite sprite)
    {
        int[] vertices = new int[32];
        int color = 0xFFFFFFFF;
        int normal = Direction.SOUTH.getStepX() & 0xFF
                | (Direction.SOUTH.getStepY() & 0xFF) << 8
                | (Direction.SOUTH.getStepZ() & 0xFF) << 16;

        putVertex(vertices, 0, WINDOW_X_START, WINDOW_Y_START, FRONT_LAYER, color,
                sprite.getU0(), sprite.getV1(), normal);
        putVertex(vertices, 1, WINDOW_X_END, WINDOW_Y_START, FRONT_LAYER, color,
                sprite.getU1(), sprite.getV1(), normal);
        putVertex(vertices, 2, WINDOW_X_END, WINDOW_Y_END, FRONT_LAYER, color,
                sprite.getU1(), sprite.getV0(), normal);
        putVertex(vertices, 3, WINDOW_X_START, WINDOW_Y_END, FRONT_LAYER, color,
                sprite.getU0(), sprite.getV0(), normal);
        return new BakedQuad(vertices, -1, Direction.SOUTH, sprite, false);
    }

    private static void putVertex(
            int[] vertices,
            int index,
            float x,
            float y,
            float z,
            int color,
            float u,
            float v,
            int normal
    )
    {
        int offset = index * 8;
        vertices[offset] = Float.floatToRawIntBits(x);
        vertices[offset + 1] = Float.floatToRawIntBits(y);
        vertices[offset + 2] = Float.floatToRawIntBits(z);
        vertices[offset + 3] = color;
        vertices[offset + 4] = Float.floatToRawIntBits(u);
        vertices[offset + 5] = Float.floatToRawIntBits(v);
        vertices[offset + 6] = LightTexture.FULL_BRIGHT;
        vertices[offset + 7] = normal;
    }

    private record BakedFuelCellModel(BakedModel base, BakedQuad fluidWindow) implements BakedModel
    {
        @Override
        public @NotNull List<BakedQuad> getQuads(
                @Nullable BlockState state,
                @Nullable Direction side,
                @NotNull RandomSource random,
                @NotNull ModelData extraData,
                @Nullable RenderType renderType
        )
        {
            List<BakedQuad> baseQuads = this.base.getQuads(state, side, random, extraData, renderType);
            if (side != null)
            {
                return baseQuads;
            }
            List<BakedQuad> result = new ArrayList<>(baseQuads.size() + 1);
            result.addAll(baseQuads);
            result.add(this.fluidWindow);
            return result;
        }

        @Override
        public @NotNull List<BakedQuad> getQuads(
                @Nullable BlockState state,
                @Nullable Direction side,
                @NotNull RandomSource random
        )
        {
            return this.getQuads(state, side, random, ModelData.EMPTY, null);
        }

        @Override
        public boolean useAmbientOcclusion()
        {
            return this.base.useAmbientOcclusion();
        }

        @Override
        public boolean isGui3d()
        {
            return this.base.isGui3d();
        }

        @Override
        public boolean usesBlockLight()
        {
            return this.base.usesBlockLight();
        }

        @Override
        public boolean isCustomRenderer()
        {
            return this.base.isCustomRenderer();
        }

        @Override
        public @NotNull TextureAtlasSprite getParticleIcon()
        {
            return this.base.getParticleIcon();
        }

        @Override
        public @NotNull ItemTransforms getTransforms()
        {
            return this.base.getTransforms();
        }

        @Override
        public @NotNull ItemOverrides getOverrides()
        {
            return this.base.getOverrides();
        }
    }
}
