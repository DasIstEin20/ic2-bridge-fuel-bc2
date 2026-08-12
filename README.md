# BuildCraft x IC2 Fuel Bridge

> Adds BuildCraft Community Edition liquid fuels to the IC2 semifluid generator for Minecraft Forge 1.20.1.

BuildCraft CE and IC2 both provide their own power systems and fuel registries. This
small server-side bridge makes BuildCraft's combustion fuels valid input for IC2's
**Semifluid Generator**, while preserving the different energy density of every
BuildCraft fuel.

It does not add blocks, items, pipes, or a new power system. It only registers fuel
properties with IC2 during startup.

## Requirements

- Minecraft 1.20.1
- Forge 47.x
- [IC2](https://github.com/craftorio/ic2)
- [BuildCraft Community Edition](https://www.curseforge.com/minecraft/mc-mods/buildcraft-community-edition)

The bridge targets BuildCraft Community Edition `8.0.13+1.20.1+forge` and IC2's
1.20.1 Forge port. It uses Forge's fluid registry and IC2's public recipe API, so it
does not contain or redistribute code from either dependency.

## Installation

1. Install Forge 1.20.1, IC2, and BuildCraft Community Edition.
2. Copy `bcic2fuelbridge-<version>.jar` to the instance or server `mods` directory.
3. Start the game/server once to create the config file.
4. Restart after changing fuel values.

The fuel registration is performed on the logical server. It therefore works on both
dedicated servers and single-player/integrated servers, provided the mod is installed
where Forge loads the modpack.

## How it works

At startup the bridge waits for IC2 to initialize its semifluid fuel manager, then
looks through Forge's registered fluids. BuildCraft CE fuels receive explicit,
per-fluid profiles taken from BuildCraft CE's combustion-fuel registry:

| BuildCraft CE fluid | BuildCraft energy density |
| --- | ---: |
| `oil`, `fuel_mixed_light` | 30 MJ/mB |
| `oil_distilled` | 37.5 MJ/mB |
| `fuel_gaseous` | 60 MJ/mB |
| `oil_heavy` | 80 MJ/mB |
| `fuel_light` | 90 MJ/mB |
| `fuel_mixed_heavy` | 96 MJ/mB |
| `oil_dense` | 120 MJ/mB |
| `fuel_dense` | 360 MJ/mB |

These numbers are calculated from BuildCraft CE's `powerPerCycle` and
`totalBurningTime` values, rather than applying one shared value to every fluid. The
bridge deliberately excludes `oil_residue` and heated variants because BuildCraft CE
does not register them as combustion-engine fuels.

## IC2 fluid cells

The bridge also adds a filled IC2 cell for each fluid in the table. Use IC2's Canner
with an `Empty Cell` and 1,000 mB of a supported BuildCraft fuel; the same cells can
be emptied into IC2 machines or placed as their fluid source. They appear in IC2's
Tools & Utilities creative tab.

Each cell renders the original animated BuildCraft fluid texture only through the
small central sight window of the IC2 fluid-cell frame. No BuildCraft texture files
are copied into this project, so resource packs and future BuildCraft texture changes
continue to apply normally.

For BuildCraft-like ports or other mods, automatic discovery can also register
non-flowing fluid IDs containing `oil` or `fuel`. Discovery is restricted to configured
namespace tokens, defaulting to `buildcraft`, so unrelated fluids are not picked up by
accident.

## Configuration

This is a Forge **server config**. Start a world once and edit:

```text
<world>/serverconfig/bcic2fuelbridge-server.toml
```

On a dedicated server, put a prepared copy in `defaultconfigs` to apply it to newly
created worlds. A server restart is required after a change: IC2 cannot safely replace
fuel registrations while a world is running.

When hosting an integrated/single-player world, the same settings are available through
Forge's **Mods → BuildCraft x IC2 Fuel Bridge → Config** button. On a dedicated server,
the GUI displays the synchronized values but is deliberately read-only: edit the
server's `serverconfig` file as an administrator.

### BuildCraft MJ to IC2 EU

There is no universal conversion ratio between BuildCraft's MJ and IC2's EU. The
`conversion.buildCraftCommunityEdition8.euPerBuildCraftMj` option makes that choice
explicit. Its default is `0.5`, which keeps the original bridge's intended scale:

```toml
[conversion.buildCraftCommunityEdition8]
    useBuiltInFuelProfiles = true
    euPerBuildCraftMj = 0.5
    cycleAmountMb = 10
```

With the default ratio, crude oil (`30 MJ/mB`) supplies `15 EU/mB`, while dense fuel
(`360 MJ/mB`) supplies `180 EU/mB`. `energyMultiplier` can scale every built-in and
manual conversion for pack-wide balancing.

`cycleAmountMb` controls how much fluid IC2 consumes per burn cycle. It changes the
cycle length only; it does not change the total EU obtained from each mB.

### Individual BuildCraft CE fuels

The Config GUI has a **9 fuels** tab. Every extracted BuildCraft CE fuel has its own
`ON`/`OFF` switch and a conversion mode:

- `AUTO` (the default) uses that fuel's extracted MJ/mB profile and the global
  `euPerBuildCraftMj` value.
- `MANUAL` uses the fuel's own `manualEuPerBuildCraftMj` number instead.
- `OFF` prevents the bridge from registering that particular fuel. This deliberately
  also wins over generic discovery and an exact custom rule, so a pack or player can
  reliably disable an unwanted integration.

The equivalent server-config section is, for example:

~~~toml
[conversion.buildCraftCommunityEdition8.fuelProfiles.oil]
    enabled = true
    mode = "AUTO"
    manualEuPerBuildCraftMj = 0.5
~~~

Set `mode = "MANUAL"` and change `manualEuPerBuildCraftMj` to override only that
fuel's MJ-to-EU conversion. The nine profiles default to `AUTO` and `enabled = true`.

### Generic automatic rules

The generic values are a fallback for oil/fuel fluids that are not recognised as the
known BuildCraft CE profiles:

```toml
[conversion]
    referenceUnitVolumeMb = 1000
    energyMultiplier = 1.0
    oilEnergyEuPerReferenceUnit = 16000.0
    oilCycleAmountMb = 10
    fuelEnergyEuPerReferenceUnit = 32000.0
    fuelCycleAmountMb = 10
```

With `referenceUnitVolumeMb = 1000`, the oil example equals `16 EU/mB` and the fuel
example equals `32 EU/mB`. Set the reference volume to `1` if you prefer entering
these values directly as EU/mB.

### Exact per-fluid overrides

`overrides.customFuelRules` takes priority over built-in and generic automatic rules.
Use it to set a custom value for any fluid, including one from another mod. The only
exception is a known BuildCraft CE profile switched `OFF`; that switch intentionally
prevents every bridge rule for that fuel:

```toml
[overrides]
    customFuelRules = [
        "buildcraftenergy:fuel_dense;720000;1000;10",
        "buildcraftenergy:oil;30000;1000;10"
    ]
```

The syntax is:

```text
fluid_id;energy_EU;volume_mB;cycle_mB
```

The first example registers dense fuel as `720 EU/mB`; the second registers oil as
`30 EU/mB`. `energyMultiplier` is applied to these rules too.

## Troubleshooting

- **A fluid is not accepted by the Semifluid Generator:** check that it is a source
  fluid, not a `_flowing` variant. For non-standard names or namespaces, add an exact
  `customFuelRules` entry.
- **A value seems too high or low:** change `euPerBuildCraftMj` for all built-in
  BuildCraft CE fuels, select `MANUAL` for one entry in the **9 fuels** tab, or add
  an exact override for one fluid.
- **The config edit changed nothing:** restart the server/world. Existing IC2 fuel
  rules remain active for the current session by design.
- **IC2 is not installed:** the bridge logs that IC2's semifluid manager is missing
  and will not register fuels.

## License

This project is released under the [MIT License](LICENSE).
