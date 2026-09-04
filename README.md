<p align="center">
  <img src="logo.png" alt="IC2 Universal Energy Extension logo" width="320">
</p>

# IC2 Universal Energy Extension

**IC2 Universal Energy Extension** is a Forge 1.20.1 addon that connects IndustrialCraft 2 EU networks to Forge Energy machines through full-featured Universal Cables. The project started as `ic2-bridge-fuel-bc2`, a focused IC2/BuildCraft fuel and energy bridge, and grew into a broader energy-integration addon with selected Mekanism transmitter systems and the Configurator.

## In game

![An IC2 MFSU powering a Refined Storage network through Universal Cables](docs/images/mfsu-universal-cable-refined-storage.png)

*An IC2 MFSU powering a Refined Storage controller through a Universal Cable network. Energy enters the cable as EU, is converted at the boundary, and is transported as FE.*

## What it provides

- Universal Cables in Basic, Advanced, Elite, and Ultimate tiers.
- Mechanical Pipes, Pressurized Tubes, Logistical Transporters, Restrictive and Diversion Transporters, and Thermodynamic Conductors with their original tiers and behavior.
- Mekanism-derived transmitter networking, side configuration, NBT, packets, rendering, textures, models, overlays, and Configurator behavior.
- IC2 EU to Forge Energy conversion for receiving `IEnergyStorage` implementations, including Refined Storage and other FE machines.
- Demand-aware EU intake: when downstream FE consumers stop accepting energy, the cable endpoint stops requesting EU instead of voiding it.
- IC2 EU ↔ BuildCraft MJ conversion.
- BuildCraft fuel ↔ IC2 Semifluid Generator integration.
- Forestry FE → IC2 support and generic IC2 → Forestry FE delivery.
- Crafting recipes based on IC2 materials.
- English and Polish configuration UI.

## EU through Universal Cables

Use this topology:

```text
IC2 generator/storage -> IC2 cable -> Universal Cable network -> FE machine
```

Each receiving Universal Cable endpoint is exposed to IC2's EnergyNet as a sink. Accepted EU is converted at the boundary by the shared `EnergyConversionService`, then inserted through the cable's Forge Energy capability. The cable network therefore carries FE and retains the original tier limits, routing, rendering, and transfer behavior.

The bridge applies downstream demand and backpressure before requesting EU. A full or missing FE consumer closes the input, while queued converted energy remains accounted for rather than disappearing. Direct IC2-cable-to-FE-machine connections remain supported as well.

Refined Storage integration is capability-based: no Refined Storage classes are bundled or linked. Any machine exposing a receiving `ForgeCapabilities.ENERGY` endpoint can use the same path.

## Conversion configuration

The server configuration is stored per world at:

```text
<world>/serverconfig/ic2universalenergy-server.toml
```

`forgeEnergyPerEu` means **how many FE one EU produces**. Its default is `4.0`:

| Value | Result |
| ---: | :--- |
| `0.2` | 5 EU produces 1 FE |
| `4.0` | 1 EU produces 4 FE |
| `20.0` | 1 EU produces 20 FE |

A higher value therefore consumes less EU for the same FE demand. BuildCraft uses the existing automatic `2.5 EU/MJ` ratio unless manual conversion is selected. Transfer limits and fuel-bridge settings are available from the Forge Mods configuration screen.

## Requirements and compatibility

- Minecraft 1.20.1
- Forge 47.4.23 or a compatible Forge 47 build
- IndustrialCraft 2: Refactored `2.10.33-ex120`
- BuildCraft, Forestry, and Refined Storage are optional integrations

This is a **Forge** mod, not a NeoForge mod.

Remove the old standalone `bcic2fuelbridge` and `transporter` JARs before installing the combined addon. The resulting JAR owns the extracted `mekanism.*` transmitter classes, so it must not be installed alongside full Mekanism 10.4.x.

## Build

Use Java 17 and keep Gradle data on drive R:, for example:

```powershell
$env:GRADLE_USER_HOME = 'R:\Codex-misc\ic2 mods\ic2-bridge-fuel-bc2\.gradle-user-home'
.\gradlew.bat build --no-daemon
```

The primary artifact is named `IC2-Universal-Energy-Extension-1.20.1-<version>.jar`.

## History, source, and license

This repository began as [DasIstEin20/ic2-bridge-fuel-bc2](https://github.com/DasIstEin20/ic2-bridge-fuel-bc2), which bridged IC2 with BuildCraft fuels and energy. It has since evolved into IC2 Universal Energy Extension: a single addon combining those bridges with EU/FE conversion and cable-based distribution.

Selected transmitter and Configurator code, rendering logic, models, textures, and related assets are copied or adapted from Mekanism 10.4.16 in accordance with its MIT License. The original Mekanism copyright notice is retained in this project's `LICENSE`.

- [Mekanism on CurseForge](https://www.curseforge.com/minecraft/mc-mods/mekanism)
- [Mekanism source code](https://github.com/mekanism/Mekanism)

This project is distributed under the MIT License. See [`LICENSE`](LICENSE) for the complete notices and terms.
