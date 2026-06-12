# Sabi Machine Price Rule Report

- Allowed items: 1363
- Base price items: 401
- Derived price items: 962
- Derived recipe entries: 1422
- Fortune III loot formulas: 21

## Forced Base Rules

- Concrete dye items are derived from their recipes when possible; dyed products use `sabi:generic_dye`.
- Fuel items such as `minecraft:coal`, `minecraft:charcoal`, `minecraft:lava_bucket`, and `minecraft:blaze_rod` are base-priced instead of being equated by burn time.
- Mineral drops such as `minecraft:diamond`, `minecraft:emerald`, `minecraft:lapis_lazuli`, `minecraft:quartz`, and `minecraft:redstone` are base-priced; their ore blocks are derived from Fortune III loot expectations.
- Recipe types without fixed ingredient counts are base-priced.
- Fortune III loot formulas are only generated for allowlisted blocks without a normal recipe formula.

## Unresolved Recipe Cycles Or Ambiguous Items Kept As Base

- None

## Discarded Formula Sources

- Recipes whose ingredients include their own output are ignored as copy/duplication recipes.
- The following explicit reverse or unpacking recipes are ignored:
- `minecraft:coal`
- `minecraft:cobbled_deepslate_from_deepslate_stonecutting`
- `minecraft:cobblestone_from_stone_stonecutting`
- `minecraft:diamond`
- `minecraft:emerald`
- `minecraft:gold_ingot_from_gold_block`
- `minecraft:honey_bottle`
- `minecraft:iron_ingot_from_iron_block`
- `minecraft:lapis_lazuli`
- `minecraft:netherite_ingot_from_netherite_block`
- `minecraft:quartz`
- `minecraft:raw_copper`
- `minecraft:raw_gold`
- `minecraft:raw_iron`
- `minecraft:redstone`
- `minecraft:resin_clump`
- `minecraft:slime_ball`
- `minecraft:wheat`

## Skipped Recipe Types

- `minecraft:blasting`: 2
- `minecraft:crafting_decorated_pot`: 1
- `minecraft:crafting_dye`: 6
- `minecraft:crafting_imbue`: 1
- `minecraft:crafting_shaped`: 19
- `minecraft:crafting_shapeless`: 15
- `minecraft:crafting_special_bannerduplicate`: 16
- `minecraft:crafting_special_bookcloning`: 1
- `minecraft:crafting_special_firework_rocket`: 1
- `minecraft:crafting_special_firework_star`: 1
- `minecraft:crafting_special_firework_star_fade`: 1
- `minecraft:crafting_special_mapextending`: 1
- `minecraft:crafting_special_repairitem`: 1
- `minecraft:crafting_special_shielddecoration`: 1
- `minecraft:crafting_transmute`: 33
- `minecraft:smelting`: 3
- `minecraft:smithing_trim`: 18
- `minecraft:stonecutting`: 2
- `sabi:big_sabi_to_giant_sabi`: 1
- `sabi:medium_sabi_to_big_sabi`: 1
- `sabi:small_sabi_to_medium_sabi`: 1
