# Compatibility Guide

This guide is for mod authors, modpack makers, and server operators who want to integrate with Building Wands.

## Multi-Tool Support

Building Wands recognizes multi-tools (items that function as multiple tool types at once, like Mekanism's Atomic Disassembler) and allows them in destroy, replace, and use actions across all tool categories.

### How multi-tools are detected

A tool is treated as a multi-tool if **any** of these conditions are true:

1. **Auto-detection** - The item matches 3 or more vanilla tool tags (`minecraft:pickaxes`, `minecraft:axes`, `minecraft:shovels`, `minecraft:hoes`). Most mod multi-tools register themselves in these tags and will work automatically.

2. **Item tag** - The item is in the `wands:multi_tools` item tag. Mod authors or modpack makers can add entries via a datapack:
   ```json
   // data/wands/tags/item/multi_tools.json
   {
     "replace": false,
     "values": [
       "mekanism:atomic_disassembler",
       "#somemod:their_multi_tools"
     ]
   }
   ```

3. **Config** - The item ID is listed in `extra_multi_tools` in `wands.json`:
   ```json
   {
     "extra_multi_tools": [
       "mekanism:atomic_disassembler"
     ]
   }
   ```

### Energy and durability safety

Multi-tools use `getDestroySpeed()` as the primary gate. If a tool returns a speed of 1.0 or below for a given block, the wand will not use it. All known energy/mana-based mods (Mekanism, Thermal, Tinkers' Construct, Industrial Foregoing, Draconic Evolution, etc.) drop destroy speed to 1.0 or below when the tool is depleted, so drained tools are automatically rejected without consuming resources.

## Extra Tools (Per-Type)

For tools that serve a single purpose but aren't recognized by vanilla tool tags, use the per-type config arrays in `wands.json`:

```json
{
  "extra_pickaxes": [{ "item": "somemod:laser_drill", "can_break": true }],
  "extra_axes": [{ "item": "somemod:chainsaw", "can_break": true }],
  "extra_shovels": [{ "item": "somemod:excavator", "can_break": true }],
  "extra_hoes": [{ "item": "somemod:cultivator", "can_break": true }],
  "extra_shears": [{ "item": "somemod:pruner", "can_break": true }]
}
```

Set `can_break` to `true` if the tool should be allowed to lose durability during wand operations (matches the `allow_*_tools_to_break` config behavior).

## Block Allow/Deny Lists

Control which blocks each tool type can interact with:

- `str_pickaxe_allowed`, `str_axe_allowed`, `str_shovel_allowed`, `str_hoe_allowed`, `str_shears_allowed` - Arrays of block IDs or tags that each tool type can break/replace
- `str_denied` - Blocks that wands cannot interact with at all (overrides allow lists)

Multi-tools check the union of all allow lists, so they can interact with any block that at least one tool type is allowed to touch.

## Chunk Claim Integration

Building Wands respects chunk claims from these mods out of the box:

| Mod | Loaders |
|-----|---------|
| FTB Chunks | Fabric, Forge, NeoForge |
| Flan | Fabric, Forge, NeoForge |
| Open Parties and Claims (OPAC) | Fabric, Forge, NeoForge |
| Get Off My Lawn (GOML) | Fabric |

If your claim mod isn't listed, players may be able to use wands in protected areas. Please open an issue on GitHub.

## Server Config Reference (`wands.json`)

The server config file `wands.json` is generated on first run. Key fields for compatibility:

| Field | Type | Description |
|-------|------|-------------|
| `extra_multi_tools` | `string[]` | Item IDs treated as multi-tools |
| `extra_pickaxes` | `object[]` | Additional pickaxe-type tools |
| `extra_axes` | `object[]` | Additional axe-type tools |
| `extra_shovels` | `object[]` | Additional shovel-type tools |
| `extra_hoes` | `object[]` | Additional hoe-type tools |
| `extra_shears` | `object[]` | Additional shear-type tools |
| `str_denied` | `string[]` | Blocks wands cannot interact with |
| `blocks_per_xp` | `float` | XP cost per block (0 = disabled) |
| `disable_destroy_replace` | `boolean` | Disable destroy and replace actions entirely |
| `enable_vein_mode` | `boolean` | Enable/disable vein mining mode |
| `enable_blast_mode` | `boolean` | Enable/disable blast mode |
