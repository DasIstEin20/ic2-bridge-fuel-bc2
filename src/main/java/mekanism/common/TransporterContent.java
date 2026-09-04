package mekanism.common;

import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * Runtime registration boundary for the literal Mekanism transmitter extraction.
 *
 * <p>The original implementation classes remain intact. This class only prevents unrelated
 * Mekanism registry entries from becoming content of the Transporter mod.</p>
 */
public final class TransporterContent {

    private static final Set<String> TRANSMITTERS = Set.of(
          "basic_universal_cable", "advanced_universal_cable", "elite_universal_cable", "ultimate_universal_cable",
          "basic_mechanical_pipe", "advanced_mechanical_pipe", "elite_mechanical_pipe", "ultimate_mechanical_pipe",
          "basic_pressurized_tube", "advanced_pressurized_tube", "elite_pressurized_tube", "ultimate_pressurized_tube",
          "basic_logistical_transporter", "advanced_logistical_transporter", "elite_logistical_transporter", "ultimate_logistical_transporter",
          "restrictive_transporter", "diversion_transporter",
          "basic_thermodynamic_conductor", "advanced_thermodynamic_conductor", "elite_thermodynamic_conductor", "ultimate_thermodynamic_conductor"
    );

    private TransporterContent() {
    }

    public static boolean isTransmitter(String name) {
        return TRANSMITTERS.contains(name);
    }

    public static boolean shouldRegister(ResourceLocation registryName, String name) {
        return switch (registryName.toString()) {
            case "minecraft:block", "minecraft:block_entity_type" -> isTransmitter(name);
            case "minecraft:item" -> isTransmitter(name) || name.equals("configurator");
            case "minecraft:creative_mode_tab" -> name.equals(Mekanism.MODID);
            default -> true;
        };
    }
}
