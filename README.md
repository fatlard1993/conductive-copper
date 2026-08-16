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

## Limits

Copper networks are limited to **256 blocks**. Larger networks will still conduct, but pathfinding stops exploring beyond this limit, which may result in lower signal strength at the edges.

## Installation

Install server-side alongside its declared dependencies (see `fabric.mod.json`). Vanilla clients need nothing. Version targets live in `gradle.properties` (Minecraft, loader, Fabric API) and `fabric.mod.json` (Java).

## Building from Source

```bash
./gradlew build
```

The built jar will be in `build/libs/`.

## License

MIT, see [LICENSE](LICENSE).
