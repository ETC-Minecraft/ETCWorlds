# ETCWorlds

Plugin **Folia / Paper 1.21.1+** (Java 21) para gestión avanzada de mundos. Reemplaza Multiverse / MultiWorld con énfasis en rendimiento (Folia-aware), templates personalizados, instancias por jugador, world-groups, dimensión-mixta, alturas custom hasta `y=4064` y backups asíncronos.

> Documentación completa: <https://docs.etc-minecraft.dev/etcworlds>

---

## Características (24)

1. 11 templates de generación: NORMAL, FLAT, AMPLIFIED, LARGE_BIOMES, VOID, SKYBLOCK, ONEBLOCK, LAYERED_VOID, FLOATING_ISLANDS, SINGLE_BIOME, CUSTOM_HEIGHT.
2. Carpeta dedicada para los mundos creados (`mundos/` por defecto, configurable).
3. Reglas por mundo en `etcworlds.yml` (PvP, fly, build, gamemode forzado, weather/time fijos, world-border, hambre, fall-damage, mob spawn, filtros de comandos, whitelist/blacklist).
4. Per-world spawn + `/worldspawn`.
5. Enlaces de portales nether/end (`/etcworlds link`) con escala vainilla.
6. Ambient mixto (un mundo NORMAL con sensación nether/end y mobs de ese ambient).
7. Alturas custom (datapack auto-generado con `dimension_type` propio hasta `y=4064`).
8. Pre-warmup de chunks asíncrono al teleportar (Folia-safe via `Bukkit.getRegionScheduler()` y `World.getChunkAtAsync`).
9. Idle-unload de mundos vacíos (con período de gracia y excepciones).
10. Backups rotativos `.zip` totalmente asíncronos.
11. Import/Export (carpeta o `.zip`, con protección zip-slip).
12. World groups estilo PerWorldInventory (inv/armor/enderchest/xp/level/health/food/gamemode compartidos).
13. Plantillas (`isTemplate=true`) clonables con `/etcworlds clone`.
14. Instancias por jugador (`perPlayerInstance=true`) con whitelist automática.
15. Mundo OneBlock (regenera `(0,Y,0)` con pool aleatoria).
16. Skyblock con isla inicial (árbol + cofre).
17. Floating Islands con ruido configurable.
18. Single-biome forzado (chunk generator + biome provider).
19. Whitelist / blacklist por mundo + permiso `etcworlds.bypass`.
20. Filtro de comandos permitidos por mundo.
21. GUI gráfica (`/etcworlds gui`) con click izq / der / shift.
22. Seed presets curados (`/etcworlds seeds`).
23. Hook con **ETCRegionGenerator** (`/etcworlds pregen <world> <radio>`).
24. Hook con **ETCCore** (variables / acciones YAML) y **PlaceholderAPI** (`%etcworlds_*%`).

---

## Build

```bash
mvn -DskipTests clean package
```

Sale `target/ETCWorlds-1.0.0.jar`. Cópialo a `plugins/` (junto a ETCCore si quieres aprovechar las acciones YAML).

## Comandos rápidos

```text
/etcworlds create lobby VOID
/etcworlds set lobby pvp false
/etcworlds set lobby fly true
/etcworlds gui
/world lobby
```

Ver tabla completa de comandos y permisos en la [documentación](https://docs.etc-minecraft.dev/etcworlds).

## Licencia

Propietaria — © ETC-Minecraft.
