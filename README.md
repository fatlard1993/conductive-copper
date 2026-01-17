# Conductive Copper

A Minecraft Fabric mod that makes copper blocks conduct redstone signals.

## Features

- **All copper blocks conduct redstone** - Full blocks, cut, chiseled, grates, stairs, slabs, and bulbs
- **Both waxed and unwaxed copper** - Waxing doesn't insulate, it just prevents oxidation
- **Oxidation-based resistance** - More oxidized copper = more signal loss
- **Copper bulbs work naturally** - Toggle on rising edge, show powered state when receiving power
- **Optimal path finding** - Signals take the lowest-resistance route through copper networks

## Resistance Values

| Oxidation Level | Resistance per Block |
|-----------------|---------------------|
| Unoxidized | 0 (lossless) |
| Exposed | 1 |
| Weathered | 2 |
| Oxidized | 3 |

## Examples

Starting with a lever (power level 15):
- **15 unoxidized copper blocks** → Signal strength 15
- **10 exposed copper blocks** → Signal strength 5 (15 - 10×1)
- **5 weathered copper blocks** → Signal strength 5 (15 - 5×2)
- **5 oxidized copper blocks** → Signal strength 0 (15 - 5×3 = 0)

Mix different oxidation levels to control signal decay. The mod automatically finds the lowest-resistance path through your copper network.

## Installation

1. Install [Fabric Loader](https://fabricmc.net/) (0.16.9 or newer)
2. Install [Fabric API](https://modrinth.com/mod/fabric-api)
3. Download the mod jar and place it in your `mods` folder

## Requirements

- Minecraft 1.21.4
- Fabric Loader 0.16.9+
- Fabric API

## Building from Source

```bash
./gradlew build
```

The built jar will be in `build/libs/`.

## License

CC0 1.0 Universal - See [LICENSE](LICENSE) for details.
