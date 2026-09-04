# IC2 Universal Energy Extension — hard project rules

- Target only Minecraft Java 1.20.1 on Forge 47.4.23 and Java 17. Never retarget this project to NeoForge.
- This repository produces one mod and one primary JAR with mod id `ic2universalenergy`. The old bridge and Transporter must not be emitted as separate embedded mods.
- IC2 runtime compatibility is pinned to IndustrialCraft 2: Refactored `2.10.33-ex120`.
- Preserve the existing BuildCraft/IC2 fuel bridges, MJ/EU bridges, Forestry bridge, config UI, and capability-based IC2-to-Forge-Energy bridge.
- `EnergyConversionService` is the single authority for EU/MJ and EU/FE ratios. Do not introduce a second conversion constant in transmitter code.
- Refined Storage and other FE consumers are supported through `ForgeCapabilities.ENERGY`; do not hard-code machine lists or depend on Refined Storage implementation classes.
- Universal Cables accept converted IC2 EU through their original Forge Energy capability. The IC2 EnergyNet adapter must feed that capability, after which the unmodified Mekanism network distributes energy to FE consumers.
- The transmitter source of truth is `../transporter-1.20.1`, itself extracted from `../Mekanism-release-1.20.x/Mekanism-release-1.20.x` (Mekanism 10.4.16).
- Zero cleanroom and zero simplification for Universal Cables, Mechanical Pipes, Pressurized Tubes, Logistical Transporters (including Restrictive and Diversion), Thermodynamic Conductors, their tiers, and Configurator.
- Preserve literal Mekanism networking, capabilities, NBT, packet synchronization, per-side connections, redstone sensitivity, logistical colors/pathfinding, tier transfer behavior, model loader, baked models, BER rendering, decorators, overlays, textures, models, OBJ files, shaders, atlases, GUI icons, translations, blockstates, recipes, and data.
- Changes to Mekanism-derived code/assets are limited to mechanical single-mod integration, namespace/metadata/registry wiring, Forge 1.20.1 compatibility, and concrete bug fixes required for the extracted systems to function.
- Keep Mekanism MIT attribution in `LICENSE`, source distributions, and built artifacts. Do not modify the upstream Mekanism checkout.
- Keep Gradle homes, caches, runs, and build outputs on drive R:. Do not consume drive C: for this project.
- After implementation changes, run at least `gradlew.bat compileJava`; before handoff run the reobfuscated JAR build and proportionate runtime/GameTest checks.
