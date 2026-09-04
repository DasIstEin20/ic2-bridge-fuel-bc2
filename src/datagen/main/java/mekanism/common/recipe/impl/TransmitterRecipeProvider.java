package mekanism.common.recipe.impl;

import dev.ic2universalenergy.datagen.Ic2RecipeIngredients;
import java.util.function.Consumer;
import mekanism.api.providers.IItemProvider;
import mekanism.common.Mekanism;
import mekanism.common.block.attribute.Attribute;
import mekanism.common.block.interfaces.ITypeBlock;
import mekanism.common.recipe.ISubRecipeProvider;
import mekanism.common.recipe.builder.ExtendedShapedRecipeBuilder;
import mekanism.common.recipe.pattern.Pattern;
import mekanism.common.recipe.pattern.RecipePattern;
import mekanism.common.recipe.pattern.RecipePattern.TripleLine;
import mekanism.common.registration.impl.BlockRegistryObject;
import mekanism.common.registries.MekanismBlocks;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.world.item.Item;

class TransmitterRecipeProvider implements ISubRecipeProvider {

    private static final RecipePattern BASIC_TRANSMITTER_PATTERN = RecipePattern.createPattern(TripleLine.of(Pattern.STEEL, Pattern.CONSTANT, Pattern.STEEL));
    private static final RecipePattern TRANSMITTER_UPGRADE_PATTERN = RecipePattern.createPattern(
          TripleLine.of(Pattern.PREVIOUS, Pattern.PREVIOUS, Pattern.PREVIOUS),
          TripleLine.of(Pattern.PREVIOUS, Pattern.ALLOY, Pattern.PREVIOUS),
          TripleLine.of(Pattern.PREVIOUS, Pattern.PREVIOUS, Pattern.PREVIOUS));

    @Override
    public void addRecipes(Consumer<FinishedRecipe> consumer) {
        String basePath = "transmitter/";
        addLogisticalTransporterRecipes(consumer, basePath + "logistical_transporter/");
        addMechanicalPipeRecipes(consumer, basePath + "mechanical_pipe/");
        addPressurizedTubeRecipes(consumer, basePath + "pressurized_tube/");
        addThermodynamicConductorRecipes(consumer, basePath + "thermodynamic_conductor/");
        addUniversalCableRecipes(consumer, basePath + "universal_cable/");
        //Diversion
        ExtendedShapedRecipeBuilder.shapedRecipe(MekanismBlocks.DIVERSION_TRANSPORTER, 2)
              .pattern(RecipePattern.createPattern(
                    TripleLine.of(Pattern.REDSTONE, Pattern.REDSTONE, Pattern.REDSTONE),
                    TripleLine.of(Pattern.STEEL, Pattern.CONSTANT, Pattern.STEEL),
                    TripleLine.of(Pattern.REDSTONE, Pattern.REDSTONE, Pattern.REDSTONE))
              ).key(Pattern.STEEL, Ic2RecipeIngredients.item("steel_plate"))
              .key(Pattern.REDSTONE, Ic2RecipeIngredients.item("circuit"))
              .key(Pattern.CONSTANT, Ic2RecipeIngredients.item("advanced_circuit"))
              .build(consumer, Mekanism.rl(basePath + "diversion_transporter"));
        //Restrictive
        ExtendedShapedRecipeBuilder.shapedRecipe(MekanismBlocks.RESTRICTIVE_TRANSPORTER, 2)
              .pattern(BASIC_TRANSMITTER_PATTERN)
              .key(Pattern.STEEL, Ic2RecipeIngredients.item("steel_plate"))
              .key(Pattern.CONSTANT, Ic2RecipeIngredients.item("alloy"))
              .build(consumer, Mekanism.rl(basePath + "restrictive_transporter"));
    }

    private void addLogisticalTransporterRecipes(Consumer<FinishedRecipe> consumer, String basePath) {
        addBasicTransmitterRecipe(consumer, basePath, MekanismBlocks.BASIC_LOGISTICAL_TRANSPORTER, Ic2RecipeIngredients.item("circuit"));
        addTransmitterUpgradeRecipe(consumer, basePath, MekanismBlocks.ADVANCED_LOGISTICAL_TRANSPORTER, MekanismBlocks.BASIC_LOGISTICAL_TRANSPORTER, Ic2RecipeIngredients.item("advanced_circuit"));
        addTransmitterUpgradeRecipe(consumer, basePath, MekanismBlocks.ELITE_LOGISTICAL_TRANSPORTER, MekanismBlocks.ADVANCED_LOGISTICAL_TRANSPORTER, Ic2RecipeIngredients.item("carbon_plate"));
        addTransmitterUpgradeRecipe(consumer, basePath, MekanismBlocks.ULTIMATE_LOGISTICAL_TRANSPORTER, MekanismBlocks.ELITE_LOGISTICAL_TRANSPORTER, Ic2RecipeIngredients.item("iridium"));
    }

    private void addMechanicalPipeRecipes(Consumer<FinishedRecipe> consumer, String basePath) {
        addBasicTransmitterRecipe(consumer, basePath, MekanismBlocks.BASIC_MECHANICAL_PIPE, Ic2RecipeIngredients.item("empty_cell"));
        addTransmitterUpgradeRecipe(consumer, basePath, MekanismBlocks.ADVANCED_MECHANICAL_PIPE, MekanismBlocks.BASIC_MECHANICAL_PIPE, Ic2RecipeIngredients.item("advanced_circuit"));
        addTransmitterUpgradeRecipe(consumer, basePath, MekanismBlocks.ELITE_MECHANICAL_PIPE, MekanismBlocks.ADVANCED_MECHANICAL_PIPE, Ic2RecipeIngredients.item("carbon_plate"));
        addTransmitterUpgradeRecipe(consumer, basePath, MekanismBlocks.ULTIMATE_MECHANICAL_PIPE, MekanismBlocks.ELITE_MECHANICAL_PIPE, Ic2RecipeIngredients.item("iridium"));
    }

    private void addPressurizedTubeRecipes(Consumer<FinishedRecipe> consumer, String basePath) {
        addBasicTransmitterRecipe(consumer, basePath, MekanismBlocks.BASIC_PRESSURIZED_TUBE, Ic2RecipeIngredients.item("reinforced_glass"));
        addTransmitterUpgradeRecipe(consumer, basePath, MekanismBlocks.ADVANCED_PRESSURIZED_TUBE, MekanismBlocks.BASIC_PRESSURIZED_TUBE, Ic2RecipeIngredients.item("advanced_circuit"));
        addTransmitterUpgradeRecipe(consumer, basePath, MekanismBlocks.ELITE_PRESSURIZED_TUBE, MekanismBlocks.ADVANCED_PRESSURIZED_TUBE, Ic2RecipeIngredients.item("carbon_plate"));
        addTransmitterUpgradeRecipe(consumer, basePath, MekanismBlocks.ULTIMATE_PRESSURIZED_TUBE, MekanismBlocks.ELITE_PRESSURIZED_TUBE, Ic2RecipeIngredients.item("iridium"));
    }

    private void addThermodynamicConductorRecipes(Consumer<FinishedRecipe> consumer, String basePath) {
        addBasicTransmitterRecipe(consumer, basePath, MekanismBlocks.BASIC_THERMODYNAMIC_CONDUCTOR, Ic2RecipeIngredients.item("heat_conductor"));
        addTransmitterUpgradeRecipe(consumer, basePath, MekanismBlocks.ADVANCED_THERMODYNAMIC_CONDUCTOR, MekanismBlocks.BASIC_THERMODYNAMIC_CONDUCTOR, Ic2RecipeIngredients.item("advanced_circuit"));
        addTransmitterUpgradeRecipe(consumer, basePath, MekanismBlocks.ELITE_THERMODYNAMIC_CONDUCTOR, MekanismBlocks.ADVANCED_THERMODYNAMIC_CONDUCTOR, Ic2RecipeIngredients.item("carbon_plate"));
        addTransmitterUpgradeRecipe(consumer, basePath, MekanismBlocks.ULTIMATE_THERMODYNAMIC_CONDUCTOR, MekanismBlocks.ELITE_THERMODYNAMIC_CONDUCTOR, Ic2RecipeIngredients.item("iridium"));
    }

    private void addUniversalCableRecipes(Consumer<FinishedRecipe> consumer, String basePath) {
        addBasicTransmitterRecipe(consumer, basePath, MekanismBlocks.BASIC_UNIVERSAL_CABLE, Ic2RecipeIngredients.item("insulated_copper_cable"));
        addTransmitterUpgradeRecipe(consumer, basePath, MekanismBlocks.ADVANCED_UNIVERSAL_CABLE, MekanismBlocks.BASIC_UNIVERSAL_CABLE, Ic2RecipeIngredients.item("advanced_circuit"));
        addTransmitterUpgradeRecipe(consumer, basePath, MekanismBlocks.ELITE_UNIVERSAL_CABLE, MekanismBlocks.ADVANCED_UNIVERSAL_CABLE, Ic2RecipeIngredients.item("carbon_plate"));
        addTransmitterUpgradeRecipe(consumer, basePath, MekanismBlocks.ULTIMATE_UNIVERSAL_CABLE, MekanismBlocks.ELITE_UNIVERSAL_CABLE, Ic2RecipeIngredients.item("iridium"));
    }

    private void addBasicTransmitterRecipe(Consumer<FinishedRecipe> consumer, String basePath, BlockRegistryObject<? extends ITypeBlock, ?> transmitter, Item item) {
        ExtendedShapedRecipeBuilder.shapedRecipe(transmitter, 8)
              .pattern(BASIC_TRANSMITTER_PATTERN)
              .key(Pattern.STEEL, Ic2RecipeIngredients.item("steel_plate"))
              .key(Pattern.CONSTANT, item)
              .build(consumer, Mekanism.rl(basePath + Attribute.getBaseTier(transmitter.getBlock()).getLowerName()));
    }

    private void addTransmitterUpgradeRecipe(Consumer<FinishedRecipe> consumer, String basePath, BlockRegistryObject<? extends ITypeBlock, ?> transmitter,
          IItemProvider previousTransmitter, Item alloy) {
        ExtendedShapedRecipeBuilder.shapedRecipe(transmitter, 8)
              .pattern(TRANSMITTER_UPGRADE_PATTERN)
              .key(Pattern.PREVIOUS, previousTransmitter)
              .key(Pattern.ALLOY, alloy)
              .build(consumer, Mekanism.rl(basePath + Attribute.getBaseTier(transmitter.getBlock()).getLowerName()));
    }
}
