# BuildCraft x IC2 Bridge

Compatibility layer for BuildCraft Community Edition, Forestry, and IC2 on Minecraft Forge 1.20.1. The repository name remains `ic2-bridge-fuel-bc2` for continuity, but the in-game name is **BuildCraft x IC2 Bridge**.

## BuildCraft compatibility (0.3.0)

BuildCraft is optional and has no hard minimum version in `mods.toml`. The bridge does not stop Forge from loading just because a BuildCraft fork reports an older, newer, or unfamiliar version.

At runtime `BuildCraftCompatibilityResolver` probes public API features first: the `IMjReceiver` capability, a usable combustion-fuel registry, and BuildCraft fluids in Forge's registry. A version string is diagnostic only. The resolver labels verified CE 8.x and CE 7.99.x APIs when their features are present; an unfamiliar release receives the **Generic / BEST_EFFORT** reflection and registry fallback.

Energy, BuildCraft → IC2 fuels, IC2 → BuildCraft fuels, and fluid discovery are independent capabilities. If one API is absent, the other working capabilities remain enabled and Forge startup continues. The in-game **Compatibility** page reports the selected BuildCraft module ID and version, adapter, mode, and the status of every capability.

## Modules

### Energy Bridge — IC2 → BuildCraft

The bridge discovers BuildCraft's public `IMjReceiver` capability and registers an IC2 EnergyNet sink for each receiver block entity. It therefore covers every current and future BuildCraft MJ consumer through the same integration point; there are no Quarry-, Mining Well-, or machine-specific adapters.

Connect an IC2 cable/emitter directly to a BuildCraft block that receives MJ. The bridge accepts EU from IC2 and inserts the corresponding micro-MJ into the receiver. The transfer is global ON/OFF and can either honor the receiver's own request (`AUTO`) or apply a per-receiver EU/t cap (`MANUAL`).

The bridge also contributes BuildCraft CE's common MJ endpoint blocks to IC2's `forge:cable_connectable` tag. This is a client-visible compatibility detail: IC2 cable arms now extend flush to the machine face instead of visually stopping short. It includes BC engines, Quarry, Builder, Filler, Mining Well, Distiller, Chute, and Laser; the actual power bridge still discovers compatible capabilities dynamically.

### Energy Bridge — BuildCraft → IC2

The bridge discovers BuildCraft's public `IMjPassiveProvider` capability and registers an IC2 EnergyNet source for every provider block entity. Connect the producer's output face to an IC2 cable; the available micro-MJ are converted to EU with the same central ratio and delivered through the normal IC2 grid, so IC2 cable voltage and transformer rules still apply.

Both energy directions can be enabled independently. The shared transfer limit follows the BuildCraft endpoint's requested/offered rate in `AUTO`, or caps each bridged endpoint in EU/t in `MANUAL`.

### Energy Bridge — IC2 → Forge Energy

IC2 emitters and cables can power every non-IC2 block entity that exposes a receiving Forge Energy (`IEnergyStorage`) capability. The bridge is capability-based rather than tied to a fixed mod list, so it includes current FE machines such as **Refined Storage**, Forestry receivers, and future compatible mods.

`energy.ic2ToForgeEnergyEnabled` enables this direction. `energy.forgeEnergyPerEu` controls the conversion; the default is **4 FE per EU**. The existing `AUTO`/`MANUAL` transfer limit also caps each FE endpoint in EU/t. The bridge intentionally does not turn arbitrary FE producers into IC2 sources; Forestry's explicit FE → IC2 support remains available separately.

### BuildCraft transport pipes ↔ IC2 machines

BuildCraft CE transport already uses Forge's common capabilities, which IC2 1.20.1 implements directly. No lossy proxy or per-machine conversion is needed:

- fluid pipes use `ForgeCapabilities.FLUID_HANDLER`, so they can fill and drain IC2 tanks, generators, and fluid machines from their enabled sides;
- item pipes use `ForgeCapabilities.ITEM_HANDLER`, so they can insert into and extract from IC2 inventories according to IC2's slot-side rules;
- power pipes are covered by the MJ ↔ IC2 energy bridge above when their pipe definition exposes an `IMjReceiver` endpoint;
- structure pipes do not carry fluids, items, or energy in BuildCraft itself and therefore have no transport capability to bridge.

`buildcrafttransport` is declared as an optional dependency so the bridge is ordered after the Transport module when it is installed. Do not use the IC2 cable-connectable tag for a generic pipe holder: one holder can contain a fluid or item pipe, so marking all of them as electrical endpoints would create false cable connections.

### Energy Bridge — Forestry ↔ IC2

Forestry 2.10.2 exposes its machines and engines through Forge Energy (`IEnergyStorage`). When Forestry is installed, its receiving machines use the generic IC2 → Forge Energy bridge above, while Forestry engines additionally expose their output as an IC2 EnergyNet source:

- Forestry → IC2: Forestry engines expose their FE output as an IC2 EnergyNet source.

The adapter respects the side on which the FE capability is exposed, so an engine keeps using its configured output face. The shared FE/EU conversion and IC2 transfer limit are also respected.

### Fuel Bridge — BuildCraft → IC2

BuildCraft CE combustion fluids are registered as IC2 Semifluid Generator fuels with their individual BuildCraft energy densities. The bridge also supplies filled IC2 cells for the standard CE fluids.

### Fuel Bridge — IC2 → BuildCraft

Accepted IC2 Semifluid Generator fuels are imported into BuildCraft's shared combustion-fuel registry. By default only `ic2` namespace fluids are imported, which currently makes IC2 biogas available to BuildCraft combustion engines. The automatic source, namespace filters, burn time, and exact overrides are configurable.

## One conversion authority

`EnergyConversionService` is the only energy-conversion layer. The BuildCraft energy and fuel bridges use one EU ↔ MJ ratio:

```text
AUTO   2.5 EU / MJ
MANUAL configured manualEuPerBuildCraftMj
```

`AUTO` deliberately has a visible, stable default. Switch to `MANUAL` for a pack-specific ratio; the setting affects subsequent fuel registration after a restart as well as live energy transfers in both directions.

Forge Energy uses its own FE/EU setting; its default is `4.0 FE/EU`.

## Configuration and UI

All settings are Forge **server config** values:

```text
<world>/serverconfig/bcic2fuelbridge-server.toml
```

In an integrated server, open **Mods → BuildCraft x IC2 Bridge → Config**. On a dedicated server the synchronized screen is read-only; edit `serverconfig` as an administrator. The screen has categories for Energy, both fuel directions, individual profiles, Balance, Overrides, Discovery, and Compatibility.

English (`en_us`) and Polish (`pl_pl`) category/title translations are shipped. The Energy fields include tooltips explaining AUTO/MANUAL behavior.

Important server-config sections:

```toml
[energy]
    ic2ToBuildCraftEnabled = true
    buildCraftToIc2Enabled = true
    ic2ToForgeEnergyEnabled = true
    forestryToIc2Enabled = true
    forgeEnergyPerEu = 4.0
    conversionMode = "AUTO"
    manualEuPerBuildCraftMj = 2.5
    transferLimitMode = "AUTO"
    manualTransferLimitEuPerTick = 128.0

[fuels.buildCraftToIc2]
    enabled = true

[fuels.ic2ToBuildCraft]
    enabled = true
    autoDiscovery = true
    namespaceTokens = ["ic2"]
    burnTimeTicks = 10
```

### Exact fuel overrides

`fuels.buildCraftToIc2.overrides.customFuelRules` uses:

```text
fluid_id;energy_EU;reference_volume_mB;cycle_mB
```

`fuels.ic2ToBuildCraft.customFuelRules` uses:

```text
fluid_id;energy_EU_per_mB;burn_time_ticks
```

Exact rules take priority over automatic discovery. Restart the server after changing either fuel direction because existing IC2/BuildCraft fuel registry entries cannot safely be replaced while a world is running.

## Screenshots

### Configuration screen

| Energy | BuildCraft → IC2 |
| --- | --- |
| [![Energy settings](docs/screenshots/01-energy.png)](docs/screenshots/01-energy.png) | [![BuildCraft to IC2 settings](docs/screenshots/02-bc-to-ic2.png)](docs/screenshots/02-bc-to-ic2.png) |
| Fuel profiles | IC2 → BuildCraft |
| [![Fuel profile settings](docs/screenshots/03-fuel-profiles.png)](docs/screenshots/03-fuel-profiles.png) | [![IC2 to BuildCraft settings](docs/screenshots/04-ic2-to-bc.png)](docs/screenshots/04-ic2-to-bc.png) |
| Balance | Overrides |
| [![Balance settings](docs/screenshots/05-balance.png)](docs/screenshots/05-balance.png) | [![Fuel override settings](docs/screenshots/06-overrides.png)](docs/screenshots/06-overrides.png) |
| Discovery | Compatibility |
| [![Discovery settings](docs/screenshots/07-discovery.png)](docs/screenshots/07-discovery.png) | [![Compatibility information](docs/screenshots/08-compatibility.png)](docs/screenshots/08-compatibility.png) |

### In-game fuel bridge

[![Dense Oil registered in the IC2 Semifluid Generator](docs/screenshots/09-semifluid-generator.png)](docs/screenshots/09-semifluid-generator.png)

## Requirements

- Minecraft 1.20.1
- Forge 47.x
- IC2 for Forge 1.20.1
- BuildCraft is optional; BuildCraft CE 7.99.25.0 and CE 8.x are recognized when their public APIs are present. Other releases are attempted in BEST_EFFORT mode.

## Build

```text
gradlew.bat build
```

## Release artifact

The ready-to-install build for this source revision is included as
[`releases/bcic2fuelbridge-0.2.3.jar`](releases/bcic2fuelbridge-0.2.3.jar).
Place that single JAR in the instance's `mods` directory together with IC2 and,
optionally, BuildCraft. Remove older copies of the bridge first so Forge does
not load two versions of the same mod.

## License

MIT — see [LICENSE](LICENSE).
