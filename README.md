[![English](https://img.shields.io/badge/README-English-blue.svg?style=for-the-badge)](README.md) 
[![Türkçe](https://img.shields.io/badge/README-Türkçe-red.svg?style=for-the-badge)](README.tr.md)

_Banner image soon to be added._

### **Description**
Piggy is a Minecraft Fabric mod focused on gameplay mechanics and HUD improvements. It can be installed client-side and used with existing vanilla saves without adding new items or blocks. Explore its features, settings, and shortcuts below. [Fabric API](https://modrinth.com/mod/fabric-api) is required to use the mod.

Check here for [Terms of use](LICENSE).

[![Download on Modrinth](https://img.shields.io/badge/Download-Modrinth-1BD96A?style=for-the-badge&logo=modrinth&logoColor=white)](https://modrinth.com/) 
[![Download on CurseForge](https://img.shields.io/badge/Download-CurseForge-F16436?style=for-the-badge&logo=curseforge&logoColor=white)](https://www.curseforge.com/minecraft/mc-mods)
[![GitHub Repository](https://img.shields.io/badge/Source-Github-8957E5?style=for-the-badge&logo=github&logoColor=white)](./)

### **Images**
_Images soon to be added._

## **Development and verification**
Java 25 is required.

```powershell
# Build the project
.\gradlew.bat --gradle-user-home .gradle-user build
# Run client game test
.\gradlew.bat --gradle-user-home .gradle-user runClientGameTest -PclientTest=MigrationSmokeGameTest
# Launch the development client for manual testing
.\gradlew.bat --gradle-user-home .gradle-user runClient
```

Game tests check mod behavior in a running Minecraft client and server. The release JAR does not include test classes.

> The Java package prefix is planned to be changed in the future.

## **Settings and shortcuts**

- **O:** Opens Piggy's category and settings menu. You can change this key in Controls screen.
- **F4:** Toggles the free camera. You can change this key in the Controls screen.
- Configure helpers individually under O → Interface / Interactive. Preferences are stored in `config/piggy-quality.json`.


## **Features**

### F3 HUD
Organizes the following information into categories and hides everything else:
- FPS, memory, CPU, and graphics measurements.
- Coordinates, facing direction, biome, and dimension.
- Ping and actual server TPS when available.
Replaces Minecraft's default **F3** menu.

### Shulker preview
Hover over a shulker box in your inventory to preview it.

### Elytra rocket counters
Shows rocket icons and counts by flight duration while gliding with an elytra.

### Food, hunger, and health preview
Food tooltips in the inventory show nutritional values. The hunger and health bars preview saturation, hunger, and health regeneration.

### Arrow counters
Shows arrow types and counts in firing order while holding a bow or crossbow.

### Speed display
Shows horizontal speed in b/s and hides below 3.5 b/s. You can adjust its position in the settings. No dedicated shortcut by default.


### Bunny hopping
Builds movement momentum through well-timed repeated jumps and midair steering. When auto bunny hop is enabled, you can hold the jump key; otherwise, press it again for each jump.

> Bunny hopping is disabled by default. Change it in the mod settings; the preference is stored in `config/piggy-client.json`.
>
> Server owners can set bunny-hop limits in `config/piggy-server.json`. Changes take effect after restarting the server.

### Faster block placement
Reduces Minecraft's default delay between block placements from 4 ticks to 2 ticks.

### Right-click timing
Applies suitable repeat delays to interactions such as block placement, water buckets, planting, and fireworks while preserving item and server rules.

### Item and block quickswap
Replaces a broken tool, a block used up by placement, or a consumed totem when a replacement is available in the inventory. Tool selection prioritizes the same type and material.

### Anti-AFK
When enabled, generates brief randomized movement inputs. Movement, jump, or sneak input stops it automatically.

### Paired doors
Opens or closes matching adjacent wooden doors together. Iron and redstone-powered doors are excluded. 

### Bridging aid
After the first valid block placement, the bridge stays at the same height and direction until you stop placing blocks.

## Other
These features are disabled on servers that do not allow them.

### Bunny hopping
Builds movement momentum through well-timed repeated jumps and midair steering. When auto bunny hop is enabled, you can hold the jump key; otherwise, press it again for each jump.

> Bunny hopping is disabled by default. Change it in the mod settings; the preference is stored in `config/piggy-client.json`.
>
> Server owners can set bunny-hop limits in `config/piggy-server.json`. Changes take effect after restarting the server.

### See players through walls
Shows other players through blocks without hiding the blocks themselves.

### Free camera
Moves the camera independently while player remains stationary. Hold **Ctrl** to move the camera faster. Camera movement is not sent to the server as player movement.
