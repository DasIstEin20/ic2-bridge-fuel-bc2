package mekanism.common.registries;

import mekanism.common.Mekanism;
import mekanism.common.MekanismLang;
import mekanism.common.registration.impl.CreativeTabDeferredRegister;
import mekanism.common.registration.impl.CreativeTabRegistryObject;

public final class TransporterCreativeTabs {

    public static final CreativeTabDeferredRegister CREATIVE_TABS = new CreativeTabDeferredRegister(Mekanism.MODID);

    public static final CreativeTabRegistryObject TRANSPORTER = CREATIVE_TABS.registerMain(MekanismLang.MEKANISM,
          MekanismBlocks.BASIC_LOGISTICAL_TRANSPORTER, builder -> builder.withSearchBar().displayItems((parameters, output) -> {
              CreativeTabDeferredRegister.addToDisplay(output,
                    MekanismBlocks.BASIC_UNIVERSAL_CABLE, MekanismBlocks.ADVANCED_UNIVERSAL_CABLE,
                    MekanismBlocks.ELITE_UNIVERSAL_CABLE, MekanismBlocks.ULTIMATE_UNIVERSAL_CABLE,
                    MekanismBlocks.BASIC_MECHANICAL_PIPE, MekanismBlocks.ADVANCED_MECHANICAL_PIPE,
                    MekanismBlocks.ELITE_MECHANICAL_PIPE, MekanismBlocks.ULTIMATE_MECHANICAL_PIPE,
                    MekanismBlocks.BASIC_PRESSURIZED_TUBE, MekanismBlocks.ADVANCED_PRESSURIZED_TUBE,
                    MekanismBlocks.ELITE_PRESSURIZED_TUBE, MekanismBlocks.ULTIMATE_PRESSURIZED_TUBE,
                    MekanismBlocks.BASIC_LOGISTICAL_TRANSPORTER, MekanismBlocks.ADVANCED_LOGISTICAL_TRANSPORTER,
                    MekanismBlocks.ELITE_LOGISTICAL_TRANSPORTER, MekanismBlocks.ULTIMATE_LOGISTICAL_TRANSPORTER,
                    MekanismBlocks.RESTRICTIVE_TRANSPORTER, MekanismBlocks.DIVERSION_TRANSPORTER,
                    MekanismBlocks.BASIC_THERMODYNAMIC_CONDUCTOR, MekanismBlocks.ADVANCED_THERMODYNAMIC_CONDUCTOR,
                    MekanismBlocks.ELITE_THERMODYNAMIC_CONDUCTOR, MekanismBlocks.ULTIMATE_THERMODYNAMIC_CONDUCTOR,
                    MekanismItems.CONFIGURATOR);
          }));

    private TransporterCreativeTabs() {
    }
}
