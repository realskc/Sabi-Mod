#!/usr/bin/env python3
import json
import re
from collections import defaultdict
from pathlib import Path


SCRIPT_PATH = Path(__file__).resolve()
REPO_ROOT = SCRIPT_PATH.parents[1]
CONFIG_DIR = REPO_ROOT / "src" / "main" / "resources" / "data" / "sabi" / "sabi_machine"
ITEMS_PATH = CONFIG_DIR / "items.json"
BASE_PRICES_PATH = CONFIG_DIR / "base_prices.json"
DERIVED_PRICES_PATH = CONFIG_DIR / "derived_prices.json"
REPORT_PATH = CONFIG_DIR / "price_rules_report.md"
VANILLA_DATA_ROOT = REPO_ROOT / "build" / "neoForm" / "neoFormJoined26.2-1" / "steps" / "transformSource" / "transformed" / "data"
VANILLA_RECIPE_DIR = VANILLA_DATA_ROOT / "minecraft" / "recipe"
VANILLA_TAG_DIR = VANILLA_DATA_ROOT / "minecraft" / "tags" / "item"
VANILLA_BLOCK_LOOT_DIR = VANILLA_DATA_ROOT / "minecraft" / "loot_table" / "blocks"
MOD_RECIPE_DIR = REPO_ROOT / "src" / "main" / "resources" / "data" / "sabi" / "recipe"

GENERIC_DYE = "sabi:generic_dye"
LAVA = "sabi:lava"
POWDER_SNOW = "sabi:powder_snow"
AXOLOTL = "sabi:axolotl"
TADPOLE = "sabi:tadpole"
HONEY = "sabi:honey"
COAL_FUEL_COUNT = 0.125
BREWING_REAGENT_COUNT = 1 / 3
BREWING_BLAZE_POWDER_COUNT = 1 / 60
FORTUNE_LEVEL = 3
COLOR_DYE_RE = re.compile(r"^minecraft:.+_dye$")
FORTUNE_LOOT_BLOCK_RE = re.compile(
    r"^minecraft:(.+_ore|brown_mushroom_block|red_mushroom_block|amethyst_cluster)$"
)
REPLACE_RECIPE_ITEMS = {
    "minecraft:clock",
    "minecraft:copper_golem_statue",
    "minecraft:honey_bottle",
    "minecraft:honey_block",
    "minecraft:honeycomb",
}
FORCED_BASE_ITEMS = {
    "minecraft:andesite",
    "minecraft:basalt",
    "minecraft:blackstone",
    "minecraft:cobbled_deepslate",
    "minecraft:cobblestone",
    "minecraft:coal",
    "minecraft:charcoal",
    "minecraft:calcite",
    "minecraft:diamond",
    "minecraft:diorite",
    "minecraft:emerald",
    "minecraft:granite",
    "minecraft:lapis_lazuli",
    "minecraft:leather",
    "minecraft:nether_wart_block",
    "minecraft:blaze_rod",
    "minecraft:quartz",
    "minecraft:redstone",
    "minecraft:tuff",
}
BASIC_STONE_ITEMS = {
    "minecraft:andesite",
    "minecraft:diorite",
    "minecraft:granite",
    "minecraft:tuff",
    "minecraft:calcite",
    "minecraft:basalt",
}
COBBLESTONE_EQUIVALENT_ITEMS = {
    "minecraft:cobblestone",
    "minecraft:cobbled_deepslate",
    "minecraft:blackstone",
}
EGG_ITEMS = {
    "minecraft:egg",
    "minecraft:blue_egg",
    "minecraft:brown_egg",
}
FISH_ITEMS = {
    "minecraft:cod",
    "minecraft:salmon",
    "minecraft:tropical_fish",
    "minecraft:pufferfish",
}
RAW_MEAT_ITEMS = {
    "minecraft:porkchop",
    "minecraft:mutton",
    "minecraft:beef",
    "minecraft:chicken",
}
GRASSLIKE_PLANT_ITEMS = {
    "minecraft:short_grass",
    "minecraft:fern",
    "minecraft:large_fern",
    "minecraft:bush",
    "minecraft:dead_bush",
    "minecraft:firefly_bush",
    "minecraft:short_dry_grass",
    "minecraft:tall_dry_grass",
    "minecraft:tall_grass",
    "minecraft:seagrass",
    "minecraft:crimson_roots",
    "minecraft:warped_roots",
    "minecraft:nether_sprouts",
}
NETHER_FUNGI_ITEMS = {
    "minecraft:crimson_fungus",
    "minecraft:warped_fungus",
}
NETHER_NYLIUM_ITEMS = {
    "minecraft:crimson_nylium",
    "minecraft:warped_nylium",
}
NETHER_VINE_ITEMS = {
    "minecraft:weeping_vines",
    "minecraft:twisting_vines",
}
NETHER_WART_BLOCK_ITEMS = {
    "minecraft:nether_wart_block",
    "minecraft:warped_wart_block",
}
AZALEA_BUSH_ITEMS = {
    "minecraft:azalea",
    "minecraft:flowering_azalea",
}
SMALL_FLOWER_ITEMS = {
    "minecraft:dandelion",
    "minecraft:poppy",
    "minecraft:blue_orchid",
    "minecraft:allium",
    "minecraft:azure_bluet",
    "minecraft:red_tulip",
    "minecraft:orange_tulip",
    "minecraft:white_tulip",
    "minecraft:pink_tulip",
    "minecraft:oxeye_daisy",
    "minecraft:cornflower",
    "minecraft:lily_of_the_valley",
    "minecraft:wildflowers",
    "minecraft:pink_petals",
}
EYEBLOSSOM_ITEMS = {
    "minecraft:open_eyeblossom",
    "minecraft:closed_eyeblossom",
}
ANCIENT_FLOWER_ITEMS = {
    "minecraft:torchflower",
    "minecraft:pitcher_plant",
}
WITHER_ROSE_ITEMS = {
    "minecraft:wither_rose",
}
CACTUS_FLOWER_ITEMS = {
    "minecraft:cactus_flower",
}
MUSHROOM_ITEMS = {
    "minecraft:brown_mushroom",
    "minecraft:red_mushroom",
}
HANGING_ROOTLIKE_ITEMS = {
    "minecraft:pale_hanging_moss",
    "minecraft:hanging_roots",
}
MOSS_BLOCK_ITEMS = {
    "minecraft:moss_block",
    "minecraft:pale_moss_block",
}
DRIPLEAF_ITEMS = {
    "minecraft:big_dripleaf",
    "minecraft:small_dripleaf",
}
SOUL_BLOCK_ITEMS = {
    "minecraft:soul_sand",
    "minecraft:soul_soil",
}
MOB_HEAD_ITEMS = {
    "minecraft:skeleton_skull",
    "minecraft:zombie_head",
    "minecraft:creeper_head",
    "minecraft:piglin_head",
}
BASE_GROUP_OVERRIDES = {
    **{item: "basic_stones" for item in BASIC_STONE_ITEMS},
    **{item: "cobblestone_equivalents" for item in COBBLESTONE_EQUIVALENT_ITEMS},
    **{item: "eggs" for item in EGG_ITEMS},
    **{item: "fish" for item in FISH_ITEMS},
    **{item: "raw_meats" for item in RAW_MEAT_ITEMS},
    **{item: "grasslike_plants" for item in GRASSLIKE_PLANT_ITEMS},
    **{item: "nether_fungi" for item in NETHER_FUNGI_ITEMS},
    **{item: "nether_nylium" for item in NETHER_NYLIUM_ITEMS},
    **{item: "nether_vines" for item in NETHER_VINE_ITEMS},
    **{item: "nether_wart_blocks" for item in NETHER_WART_BLOCK_ITEMS},
    **{item: "azalea_bushes" for item in AZALEA_BUSH_ITEMS},
    **{item: "small_flowers" for item in SMALL_FLOWER_ITEMS},
    **{item: "eyeblossoms" for item in EYEBLOSSOM_ITEMS},
    **{item: "ancient_flowers" for item in ANCIENT_FLOWER_ITEMS},
    **{item: "wither_rose" for item in WITHER_ROSE_ITEMS},
    **{item: "cactus_flower" for item in CACTUS_FLOWER_ITEMS},
    **{item: "mushrooms" for item in MUSHROOM_ITEMS},
    **{item: "hanging_rootlike_plants" for item in HANGING_ROOTLIKE_ITEMS},
    **{item: "moss_blocks" for item in MOSS_BLOCK_ITEMS},
    **{item: "dripleaves" for item in DRIPLEAF_ITEMS},
    **{item: "soul_blocks" for item in SOUL_BLOCK_ITEMS},
    **{item: "mob_heads" for item in MOB_HEAD_ITEMS},
}
OXIDIZED_COPPER_EQUIVALENTS = {
    "minecraft:exposed_copper": "minecraft:copper_block",
    "minecraft:weathered_copper": "minecraft:copper_block",
    "minecraft:oxidized_copper": "minecraft:copper_block",
    "minecraft:exposed_lightning_rod": "minecraft:lightning_rod",
    "minecraft:weathered_lightning_rod": "minecraft:lightning_rod",
    "minecraft:oxidized_lightning_rod": "minecraft:lightning_rod",
    "minecraft:exposed_copper_door": "minecraft:copper_door",
    "minecraft:weathered_copper_door": "minecraft:copper_door",
    "minecraft:oxidized_copper_door": "minecraft:copper_door",
    "minecraft:exposed_copper_trapdoor": "minecraft:copper_trapdoor",
    "minecraft:weathered_copper_trapdoor": "minecraft:copper_trapdoor",
    "minecraft:oxidized_copper_trapdoor": "minecraft:copper_trapdoor",
    "minecraft:exposed_copper_chest": "minecraft:copper_chest",
    "minecraft:weathered_copper_chest": "minecraft:copper_chest",
    "minecraft:oxidized_copper_chest": "minecraft:copper_chest",
    "minecraft:exposed_copper_golem_statue": "minecraft:copper_golem_statue",
    "minecraft:weathered_copper_golem_statue": "minecraft:copper_golem_statue",
    "minecraft:oxidized_copper_golem_statue": "minecraft:copper_golem_statue",
}
DISCARDED_RECIPE_IDS = {
    # This stonecutting recipe creates the cycle cobblestone <-> stone. For
    # pawn pricing, cobblestone is the more natural base item and stone should
    # include the smelting fuel cost.
    "minecraft:cobblestone_from_stone_stonecutting",
    # Same idea for deepslate: cobbled deepslate is the natural base drop,
    # while deepslate itself should include the smelting cost.
    "minecraft:cobbled_deepslate_from_deepslate_stonecutting",
    # Storage-block unpacking recipes should not make the raw item depend on
    # the block; the storage block should be derived from the raw item.
    "minecraft:coal",
    "minecraft:diamond",
    "minecraft:emerald",
    "minecraft:gold_ingot_from_gold_block",
    "minecraft:iron_ingot_from_iron_block",
    "minecraft:lapis_lazuli",
    "minecraft:netherite_ingot_from_netherite_block",
    "minecraft:quartz",
    "minecraft:raw_copper",
    "minecraft:raw_gold",
    "minecraft:raw_iron",
    "minecraft:redstone",
    "minecraft:resin_clump",
    "minecraft:slime_ball",
    "minecraft:wheat",
    # The reverse honey recipe consumes empty bottles and is not the natural
    # source of honey bottles for pricing.
    "minecraft:honey_bottle",
    "minecraft:honey_block",
    "minecraft:honeycomb",
}
SUPPORTED_RECIPE_TYPES = {
    "minecraft:crafting_shaped",
    "minecraft:crafting_shapeless",
    "minecraft:crafting_transmute",
    "minecraft:smithing_transform",
    "minecraft:smelting",
    "minecraft:blasting",
    "minecraft:smoking",
    "minecraft:campfire_cooking",
    "minecraft:stonecutting",
}
COOKING_RECIPE_TYPES = {
    "minecraft:smelting",
    "minecraft:blasting",
    "minecraft:smoking",
    "minecraft:campfire_cooking",
}

EXTRA_FORMULAS = {
    "minecraft:chipped_anvil": [
        {
            "recipe_id": "sabi:chipped_anvil_from_anvil_damage",
            "type": "manual_equivalence",
            "result_count": 1,
            "ingredients": [{"item": "minecraft:anvil", "count": 2 / 3}],
        }
    ],
    "minecraft:damaged_anvil": [
        {
            "recipe_id": "sabi:damaged_anvil_from_anvil_damage",
            "type": "manual_equivalence",
            "result_count": 1,
            "ingredients": [{"item": "minecraft:anvil", "count": 1 / 3}],
        }
    ],
    "minecraft:carved_pumpkin": [
        {
            "recipe_id": "sabi:carved_pumpkin_equals_pumpkin",
            "type": "manual_equivalence",
            "result_count": 1,
            "ingredients": [{"item": "minecraft:pumpkin", "count": 1}],
        }
    ],
    "minecraft:written_book": [
        {
            "recipe_id": "sabi:written_book_equals_writable_book",
            "type": "manual_equivalence",
            "result_count": 1,
            "ingredients": [{"item": "minecraft:writable_book", "count": 1}],
        }
    ],
    "minecraft:splash_potion": [
        {
            "recipe_id": "sabi:splash_potion_from_potion_brewing",
            "type": "manual_equivalence",
            "result_count": 1,
            "ingredients": [
                {"item": "minecraft:potion", "count": 1},
                {"item": "minecraft:gunpowder", "count": BREWING_REAGENT_COUNT},
                {"item": "minecraft:blaze_powder", "count": BREWING_BLAZE_POWDER_COUNT},
            ],
        }
    ],
    "minecraft:lingering_potion": [
        {
            "recipe_id": "sabi:lingering_potion_from_potion_brewing",
            "type": "manual_equivalence",
            "result_count": 1,
            "ingredients": [
                {"item": "minecraft:potion", "count": 1},
                {"item": "minecraft:dragon_breath", "count": BREWING_REAGENT_COUNT},
                {"item": "minecraft:blaze_powder", "count": BREWING_BLAZE_POWDER_COUNT},
            ],
        }
    ],
    "minecraft:cod_bucket": [
        {
            "recipe_id": "sabi:cod_bucket_from_bucket_and_cod",
            "type": "manual_equivalence",
            "result_count": 1,
            "ingredients": [
                {"item": "minecraft:bucket", "count": 1},
                {"item": "minecraft:cod", "count": 1},
            ],
        }
    ],
    "minecraft:salmon_bucket": [
        {
            "recipe_id": "sabi:salmon_bucket_from_bucket_and_salmon",
            "type": "manual_equivalence",
            "result_count": 1,
            "ingredients": [
                {"item": "minecraft:bucket", "count": 1},
                {"item": "minecraft:salmon", "count": 1},
            ],
        }
    ],
    "minecraft:tropical_fish_bucket": [
        {
            "recipe_id": "sabi:tropical_fish_bucket_from_bucket_and_tropical_fish",
            "type": "manual_equivalence",
            "result_count": 1,
            "ingredients": [
                {"item": "minecraft:bucket", "count": 1},
                {"item": "minecraft:tropical_fish", "count": 1},
            ],
        }
    ],
    "minecraft:pufferfish_bucket": [
        {
            "recipe_id": "sabi:pufferfish_bucket_from_bucket_and_pufferfish",
            "type": "manual_equivalence",
            "result_count": 1,
            "ingredients": [
                {"item": "minecraft:bucket", "count": 1},
                {"item": "minecraft:pufferfish", "count": 1},
            ],
        }
    ],
    "minecraft:water_bucket": [
        {
            "recipe_id": "sabi:water_bucket_equals_bucket",
            "type": "manual_equivalence",
            "result_count": 1,
            "ingredients": [{"item": "minecraft:bucket", "count": 1}],
        }
    ],
    "minecraft:milk_bucket": [
        {
            "recipe_id": "sabi:milk_bucket_equals_bucket",
            "type": "manual_equivalence",
            "result_count": 1,
            "ingredients": [{"item": "minecraft:bucket", "count": 1}],
        }
    ],
    "minecraft:mud": [
        {
            "recipe_id": "sabi:mud_equals_dirt",
            "type": "manual_equivalence",
            "result_count": 1,
            "ingredients": [{"item": "minecraft:dirt", "count": 1}],
        }
    ],
}


def load_json(path):
    with path.open("r", encoding="utf-8-sig") as handle:
        return json.load(handle)


def write_json(path, data):
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def load_existing_base_prices():
    item_prices = {}
    symbol_prices = {}
    if not BASE_PRICES_PATH.exists():
        return item_prices, symbol_prices

    data = load_json(BASE_PRICES_PATH)
    for symbol in data.get("symbols", []):
        symbol_id = symbol.get("id")
        if symbol_id:
            symbol_prices[symbol_id] = max(0, int(symbol.get("pawn_price", 1)))

    for group in data.get("groups", []):
        price = max(0, int(group.get("pawn_price", 1)))
        for item in group.get("items", []):
            item_prices[item] = price
    return item_prices, symbol_prices


def item_id_from_result(result):
    if isinstance(result, str):
        return result, 1
    if isinstance(result, dict):
        return result.get("id") or result.get("item"), int(result.get("count", 1))
    return None, 1


def tag_path(tag_id):
    namespace, name = tag_id[1:].split(":", 1) if ":" in tag_id else ("minecraft", tag_id[1:])
    if namespace != "minecraft":
        return None
    return VANILLA_TAG_DIR / f"{name}.json"


class TagResolver:
    def __init__(self):
        self.cache = {}

    def resolve(self, tag_id):
        if tag_id in self.cache:
            return self.cache[tag_id]

        path = tag_path(tag_id)
        if path is None or not path.exists():
            self.cache[tag_id] = []
            return []

        values = []
        data = load_json(path)
        for value in data.get("values", []):
            if isinstance(value, str):
                if value.startswith("#"):
                    values.extend(self.resolve(value))
                else:
                    values.append(value)
            elif isinstance(value, dict):
                item = value.get("id")
                if not item:
                    continue
                if item.startswith("#"):
                    values.extend(self.resolve(item))
                else:
                    values.append(item)

        deduped = list(dict.fromkeys(values))
        self.cache[tag_id] = deduped
        return deduped


def ingredient_choices(ingredient, tags):
    if isinstance(ingredient, str):
        if ingredient.startswith("#"):
            return tags.resolve(ingredient)
        return [ingredient]

    if isinstance(ingredient, list):
        choices = []
        for value in ingredient:
            choices.extend(ingredient_choices(value, tags))
        return list(dict.fromkeys(choices))

    if isinstance(ingredient, dict):
        if "item" in ingredient:
            return [ingredient["item"]]
        if "tag" in ingredient:
            tag_id = ingredient["tag"]
            if not tag_id.startswith("#"):
                tag_id = "#" + tag_id
            return tags.resolve(tag_id)

    return []


def normalize_term(output_id, recipe_group, choices, count=1):
    if not choices:
        return None

    if output_id and not COLOR_DYE_RE.match(output_id) and choices and all(COLOR_DYE_RE.match(choice) for choice in choices):
        return {"item": GENERIC_DYE, "count": count}

    choices = list(dict.fromkeys(choices))
    if len(choices) == 1:
        return {"item": choices[0], "count": count}
    return {"any_of": choices, "count": count}


def term_contains_item(term, item_id):
    if term.get("item") == item_id:
        return True
    return item_id in term.get("any_of", [])


def expected_number(value, fallback=1.0):
    if value is None:
        return fallback
    if isinstance(value, (int, float)):
        return float(value)
    if isinstance(value, dict):
        value_type = value.get("type")
        if value_type == "minecraft:uniform":
            min_value = float(value.get("min", fallback))
            max_value = float(value.get("max", fallback))
            return (min_value + max_value) / 2.0
        if value_type == "minecraft:binomial":
            return float(value.get("n", 0)) * float(value.get("p", 0))
    return fallback


def apply_limit_count(count, limit):
    if not isinstance(limit, dict):
        return count
    min_value = limit.get("min")
    max_value = limit.get("max")
    if min_value is not None and max_value is None and min_value == 0:
        return clipped_integer_uniform_expectation(count)
    return count


def clipped_integer_uniform_expectation(count):
    if not isinstance(count, dict) or count.get("type") != "minecraft:uniform":
        return expected_number(count)

    min_value = int(count.get("min", 0))
    max_value = int(count.get("max", 0))
    if min_value > max_value:
        min_value, max_value = max_value, min_value
    values = [max(0, value) for value in range(min_value, max_value + 1)]
    return sum(values) / len(values)


def condition_probability(condition):
    condition_type = condition.get("condition")
    if condition_type == "minecraft:survives_explosion":
        return 1.0
    if condition_type == "minecraft:table_bonus":
        chances = condition.get("chances", [])
        if not chances:
            return 0.0
        return float(chances[min(FORTUNE_LEVEL, len(chances) - 1)])
    if condition_type == "minecraft:random_chance":
        return float(condition.get("chance", 0.0))
    if condition_type == "minecraft:match_tool":
        predicate = condition.get("predicate", {})
        predicate_text = json.dumps(predicate)
        if "minecraft:silk_touch" in predicate_text or "minecraft:shears" in predicate_text:
            return 0.0
        return 1.0
    if condition_type == "minecraft:any_of":
        failure = 1.0
        for term in condition.get("terms", []):
            failure *= 1.0 - condition_probability(term)
        return 1.0 - failure
    if condition_type == "minecraft:inverted":
        return 1.0 - condition_probability(condition.get("term", {}))
    return 1.0


def conditions_probability(conditions):
    probability = 1.0
    for condition in conditions or []:
        probability *= condition_probability(condition)
    return probability


def apply_loot_functions(base_count, functions):
    count = base_count
    count_source = None
    for function in functions or []:
        function_type = function.get("function")
        if function_type == "minecraft:set_count":
            count_source = function.get("count")
            new_count = expected_number(count_source, count)
            count = count + new_count if function.get("add", False) else new_count
        elif function_type == "minecraft:limit_count":
            count = apply_limit_count(count_source, function.get("limit"))
        elif function_type == "minecraft:apply_bonus":
            formula = function.get("formula")
            if formula == "minecraft:ore_drops":
                count *= 2.2
            elif formula == "minecraft:uniform_bonus_count":
                multiplier = float(function.get("parameters", {}).get("bonusMultiplier", 1))
                count += FORTUNE_LEVEL * multiplier / 2.0
            elif formula == "minecraft:binomial_with_bonus_count":
                parameters = function.get("parameters", {})
                count += (FORTUNE_LEVEL + int(parameters.get("extraRounds", 0))) * float(parameters.get("probability", 0))
    return count


def add_expected_drop(totals, item_id, count):
    if item_id and count > 0:
        totals[item_id] += count


def evaluate_loot_entry(entry):
    entry_probability = conditions_probability(entry.get("conditions", []))
    if entry_probability <= 0.0:
        return {}

    entry_type = entry.get("type")
    totals = defaultdict(float)
    if entry_type == "minecraft:item":
        count = apply_loot_functions(1.0, entry.get("functions", []))
        add_expected_drop(totals, entry.get("name"), entry_probability * count)
    elif entry_type == "minecraft:alternatives":
        remaining = 1.0
        for child in entry.get("children", []):
            child_probability = conditions_probability(child.get("conditions", []))
            if child_probability <= 0.0:
                continue
            child_totals = evaluate_loot_entry({**child, "conditions": []})
            for item_id, count in child_totals.items():
                add_expected_drop(totals, item_id, entry_probability * remaining * child_probability * count)
            remaining *= 1.0 - child_probability
            if remaining <= 0.0:
                break
    return totals


def fortune_loot_formula(item_id):
    if not FORTUNE_LOOT_BLOCK_RE.match(item_id):
        return None
    namespace, path = item_id.split(":", 1)
    if namespace != "minecraft":
        return None

    loot_path = VANILLA_BLOCK_LOOT_DIR / f"{path}.json"
    if not loot_path.exists():
        return None

    data = load_json(loot_path)
    totals = defaultdict(float)
    for pool in data.get("pools", []):
        pool_probability = conditions_probability(pool.get("conditions", []))
        rolls = expected_number(pool.get("rolls"), 1.0)
        for entry in pool.get("entries", []):
            entry_totals = evaluate_loot_entry(entry)
            for drop_id, count in entry_totals.items():
                if drop_id != item_id:
                    add_expected_drop(totals, drop_id, pool_probability * rolls * count)

    if not totals:
        return None

    ingredients = [
        {"item": drop_id, "count": round(count, 6)}
        for drop_id, count in sorted(totals.items())
        if count > 0
    ]
    if not ingredients:
        return None

    return {
        "output": item_id,
        "recipe_id": f"minecraft:{path}_fortune_iii_loot",
        "type": "fortune_iii_loot",
        "result_count": 1,
        "ingredients": ingredients,
    }


def parse_recipe(path, tags):
    data = load_json(path)
    recipe_type = data.get("type")
    if recipe_type not in SUPPORTED_RECIPE_TYPES:
        return None, recipe_type

    output_id, output_count = item_id_from_result(data.get("result"))
    if not output_id:
        return None, recipe_type

    recipe_id = f"{path.parent.parent.name}:{path.stem}"
    if recipe_id in DISCARDED_RECIPE_IDS:
        return None, recipe_type

    ingredients = []
    recipe_group = data.get("group", "")

    if recipe_type == "minecraft:crafting_shaped":
        counts = defaultdict(int)
        for row in data.get("pattern", []):
            for char in row:
                if char != " ":
                    counts[char] += 1

        keys = data.get("key", {})
        for char, count in counts.items():
            if char not in keys:
                return None, recipe_type
            term = normalize_term(output_id, recipe_group, ingredient_choices(keys[char], tags), count)
            if term is None:
                return None, recipe_type
            ingredients.append(term)

    elif recipe_type == "minecraft:crafting_shapeless":
        for ingredient in data.get("ingredients", []):
            term = normalize_term(output_id, recipe_group, ingredient_choices(ingredient, tags))
            if term is None:
                return None, recipe_type
            ingredients.append(term)

    elif recipe_type == "minecraft:crafting_transmute":
        for name in ("input", "material"):
            term = normalize_term(output_id, recipe_group, ingredient_choices(data.get(name), tags))
            if term is None:
                return None, recipe_type
            ingredients.append(term)

    elif recipe_type == "minecraft:smithing_transform":
        for name in ("template", "base", "addition"):
            term = normalize_term(output_id, recipe_group, ingredient_choices(data.get(name), tags))
            if term is None:
                return None, recipe_type
            ingredients.append(term)

    elif recipe_type in COOKING_RECIPE_TYPES:
        term = normalize_term(output_id, recipe_group, ingredient_choices(data.get("ingredient"), tags))
        if term is None:
            return None, recipe_type
        ingredients.append(term)
        ingredients.append({"item": "minecraft:coal", "count": COAL_FUEL_COUNT})

    elif recipe_type == "minecraft:stonecutting":
        term = normalize_term(output_id, recipe_group, ingredient_choices(data.get("ingredient"), tags))
        if term is None:
            return None, recipe_type
        ingredients.append(term)

    if not ingredients:
        return None, recipe_type

    # Duplication/copy recipes are not useful as a value source because they
    # require the item being priced to already exist.
    if any(term_contains_item(term, output_id) for term in ingredients):
        return None, recipe_type

    return {
        "output": output_id,
        "recipe_id": recipe_id,
        "type": recipe_type.removeprefix("minecraft:"),
        "result_count": max(1, int(output_count)),
        "ingredients": ingredients,
    }, recipe_type


def term_is_resolved(term, resolved):
    if "item" in term:
        return term["item"] in resolved
    return any(item in resolved for item in term.get("any_of", []))


def formula_is_resolved(formula, resolved):
    return all(term_is_resolved(term, resolved) for term in formula["ingredients"])


def item_name_for_group(item_id):
    return item_id.replace(":", "_").replace("/", "_")


def manual_formula(output_id, recipe_id, ingredients, result_count=1):
    return {
        "output": output_id,
        "recipe_id": recipe_id,
        "type": "manual_equivalence",
        "result_count": result_count,
        "ingredients": ingredients,
    }
def should_replace_recipe_item(item_id):
    return (
        item_id in REPLACE_RECIPE_ITEMS
        or item_id == "minecraft:netherite_upgrade_smithing_template"
        or item_id.endswith("_armor_trim_smithing_template")
    )


def extra_formulas_for_item(item_id):
    formulas = []
    if item_id in OXIDIZED_COPPER_EQUIVALENTS:
        base_item = OXIDIZED_COPPER_EQUIVALENTS[item_id]
        formulas.append(manual_formula(
            item_id,
            f"sabi:{item_id.removeprefix('minecraft:')}_equals_{base_item.removeprefix('minecraft:')}",
            [{"item": base_item, "count": 1}],
        ))

    for formula in EXTRA_FORMULAS.get(item_id, []):
        formulas.append({
            "output": item_id,
            "recipe_id": formula["recipe_id"],
            "type": formula["type"],
            "result_count": formula["result_count"],
            "ingredients": formula["ingredients"],
        })

    if item_id.startswith("minecraft:") and item_id.endswith("_bundle") and item_id != "minecraft:bundle":
        formulas.append(manual_formula(
            item_id,
            f"sabi:{item_id.removeprefix('minecraft:')}_equals_empty_bundle",
            [{"item": "minecraft:bundle", "count": 1}],
        ))

    if item_id == "minecraft:enchanted_book":
        formulas.append(manual_formula(
            item_id,
            "sabi:enchanted_book_from_book_and_lapis_lazuli",
            [
                {"item": "minecraft:book", "count": 1},
                {"item": "minecraft:lapis_lazuli", "count": 1},
            ],
        ))

    if item_id == "minecraft:filled_map":
        formulas.append(manual_formula(
            item_id,
            "sabi:filled_map_equals_map",
            [{"item": "minecraft:map", "count": 1}],
        ))

    if item_id == "minecraft:potion":
        formulas.append(manual_formula(
            item_id,
            "sabi:potion_equals_water_bottle",
            [{"item": "minecraft:glass_bottle", "count": 1}],
        ))

    if item_id == "minecraft:tipped_arrow":
        formulas.append(manual_formula(
            item_id,
            "sabi:tipped_arrow_from_arrows_and_lingering_potion",
            [
                {"item": "minecraft:arrow", "count": 8},
                {"item": "minecraft:lingering_potion", "count": 1},
            ],
            result_count=8,
        ))
    if item_id == "minecraft:nether_star":
        formulas.append(manual_formula(
            item_id,
            "sabi:nether_star_from_wither",
            [
                {"item": "minecraft:wither_skeleton_skull", "count": 3},
                {"item": "minecraft:soul_sand", "count": 4},
            ],
        ))

    if item_id == "minecraft:firework_star":
        formulas.append(manual_formula(
            item_id,
            "sabi:firework_star_from_gunpowder_and_generic_dye",
            [
                {"item": "minecraft:gunpowder", "count": 1},
                {"item": GENERIC_DYE, "count": 1},
            ],
        ))

    if item_id == "minecraft:globe_banner_pattern":
        formulas.append(manual_formula(
            item_id,
            "sabi:globe_banner_pattern_from_emeralds",
            [{"item": "minecraft:emerald", "count": 8}],
        ))

    if item_id == "minecraft:netherite_upgrade_smithing_template" or item_id.endswith("_armor_trim_smithing_template"):
        formulas.append(manual_formula(
            item_id,
            f"sabi:{item_id.removeprefix('minecraft:')}_from_diamonds",
            [{"item": "minecraft:diamond", "count": 7}],
        ))
    if item_id == "minecraft:clock":
        formulas.append(manual_formula(
            item_id,
            "sabi:clock_from_emeralds",
            [{"item": "minecraft:emerald", "count": 36}],
        ))

    if item_id == "minecraft:copper_golem_statue":
        formulas.append(manual_formula(
            item_id,
            "sabi:copper_golem_statue_from_copper_block_and_pumpkin",
            [
                {"item": "minecraft:copper_block", "count": 1},
                {"item": "minecraft:pumpkin", "count": 1},
            ],
        ))

    if item_id == "minecraft:honey_bottle":
        formulas.append(manual_formula(
            item_id,
            "sabi:honey_bottle_from_glass_bottle_and_honey",
            [
                {"item": "minecraft:glass_bottle", "count": 1},
                {"item": HONEY, "count": 1},
            ],
        ))

    if item_id == "minecraft:lava_bucket":
        formulas.append(manual_formula(
            item_id,
            "sabi:lava_bucket_from_bucket_and_lava",
            [
                {"item": "minecraft:bucket", "count": 1},
                {"item": LAVA, "count": 1},
            ],
        ))

    if item_id == "minecraft:honeycomb":
        formulas.append(manual_formula(
            item_id,
            "sabi:honeycomb_from_honey",
            [{"item": HONEY, "count": 1}],
            result_count=3,
        ))

    if item_id == "minecraft:honey_block":
        formulas.append(manual_formula(
            item_id,
            "sabi:honey_block_from_honey",
            [{"item": HONEY, "count": 4}],
        ))

    amethyst_bud_cluster_counts = {
        "minecraft:small_amethyst_bud": 1,
        "minecraft:medium_amethyst_bud": 2,
        "minecraft:large_amethyst_bud": 3,
    }
    if item_id in amethyst_bud_cluster_counts:
        formulas.append(manual_formula(
            item_id,
            f"sabi:{item_id.removeprefix('minecraft:')}_from_amethyst_cluster",
            [{"item": "minecraft:amethyst_cluster", "count": amethyst_bud_cluster_counts[item_id]}],
            result_count=4,
        ))
    if item_id == "minecraft:powder_snow_bucket":
        formulas.append(manual_formula(
            item_id,
            "sabi:powder_snow_bucket_from_bucket_and_powder_snow",
            [
                {"item": "minecraft:bucket", "count": 1},
                {"item": POWDER_SNOW, "count": 1},
            ],
        ))

    if item_id == "minecraft:axolotl_bucket":
        formulas.append(manual_formula(
            item_id,
            "sabi:axolotl_bucket_from_bucket_and_axolotl",
            [
                {"item": "minecraft:bucket", "count": 1},
                {"item": AXOLOTL, "count": 1},
            ],
        ))

    if item_id == "minecraft:tadpole_bucket":
        formulas.append(manual_formula(
            item_id,
            "sabi:tadpole_bucket_from_bucket_and_tadpole",
            [
                {"item": "minecraft:bucket", "count": 1},
                {"item": TADPOLE, "count": 1},
            ],
        ))

    if item_id.startswith("minecraft:") and item_id.endswith("_concrete"):
        powder_id = f"{item_id}_powder"
        formulas.append(manual_formula(
            item_id,
            f"sabi:{item_id.removeprefix('minecraft:')}_equals_{powder_id.removeprefix('minecraft:')}",
            [{"item": powder_id, "count": 1}],
        ))

    if item_id.startswith("minecraft:dead_") and item_id.endswith("_coral"):
        live_id = f"minecraft:{item_id.removeprefix('minecraft:dead_')}"
        formulas.append(manual_formula(
            item_id,
            f"sabi:{item_id.removeprefix('minecraft:')}_equals_{live_id.removeprefix('minecraft:')}",
            [{"item": live_id, "count": 1}],
        ))

    if item_id.startswith("minecraft:dead_") and item_id.endswith("_coral_block"):
        live_id = f"minecraft:{item_id.removeprefix('minecraft:dead_')}"
        formulas.append(manual_formula(
            item_id,
            f"sabi:{item_id.removeprefix('minecraft:')}_equals_{live_id.removeprefix('minecraft:')}",
            [{"item": live_id, "count": 1}],
        ))

    if item_id.startswith("minecraft:dead_") and item_id.endswith("_coral_fan"):
        live_id = f"minecraft:{item_id.removeprefix('minecraft:dead_')}"
        formulas.append(manual_formula(
            item_id,
            f"sabi:{item_id.removeprefix('minecraft:')}_equals_{live_id.removeprefix('minecraft:')}",
            [{"item": live_id, "count": 1}],
        ))

    if item_id.startswith("minecraft:") and item_id.endswith("_shulker_box") and item_id != "minecraft:shulker_box":
        formulas.append(manual_formula(
            item_id,
            f"sabi:{item_id.removeprefix('minecraft:')}_from_shulker_box_and_generic_dye",
            [
                {"item": "minecraft:shulker_box", "count": 1},
                {"item": GENERIC_DYE, "count": 1},
            ],
        ))

    return formulas


def main():
    if not VANILLA_RECIPE_DIR.exists():
        raise SystemExit(f"Vanilla recipe directory not found: {VANILLA_RECIPE_DIR}")

    items_config = load_json(ITEMS_PATH)
    existing_base_prices, existing_symbol_prices = load_existing_base_prices()
    allowed_items = []
    original_group_by_item = {}
    for group in items_config.get("groups", []):
        group_id = group.get("id", "ungrouped")
        for item in group.get("items", []):
            if item not in original_group_by_item:
                allowed_items.append(item)
                original_group_by_item[item] = group_id

    allowed_set = set(allowed_items)
    tags = TagResolver()
    formulas_by_item = defaultdict(list)
    skipped_recipe_types = defaultdict(int)
    recipe_dirs = [VANILLA_RECIPE_DIR, MOD_RECIPE_DIR]
    for recipe_dir in recipe_dirs:
        if not recipe_dir.exists():
            continue
        for path in sorted(recipe_dir.glob("*.json")):
            formula, recipe_type = parse_recipe(path, tags)
            if formula is None:
                skipped_recipe_types[recipe_type or "unknown"] += 1
                continue
            if formula["output"] in allowed_set:
                formulas_by_item[formula["output"]].append(formula)

    for item in allowed_items:
        extra_formulas = extra_formulas_for_item(item)
        if extra_formulas:
            if should_replace_recipe_item(item):
                formulas_by_item[item].clear()
            formulas_by_item[item].extend(extra_formulas)

    loot_formula_count = 0
    for item in allowed_items:
        if item in formulas_by_item:
            continue
        formula = fortune_loot_formula(item)
        if formula is not None:
            formulas_by_item[item].append(formula)
            loot_formula_count += 1

    forced_base = {
        item for item in allowed_items
        if item not in formulas_by_item or item in FORCED_BASE_ITEMS
    }
    resolved = set(forced_base)
    resolved.add(GENERIC_DYE)
    resolved.add(LAVA)
    resolved.add(POWDER_SNOW)
    resolved.add(AXOLOTL)
    resolved.add(TADPOLE)
    resolved.add(HONEY)

    progress = True
    while progress:
        progress = False
        for item in allowed_items:
            if item in resolved or item not in formulas_by_item:
                continue
            if any(formula_is_resolved(formula, resolved) for formula in formulas_by_item[item]):
                resolved.add(item)
                progress = True

    base_items = []
    derived_items = []
    for item in allowed_items:
        if item in formulas_by_item and item not in forced_base and item in resolved:
            derived_items.append(item)
        else:
            base_items.append(item)

    base_groups = []
    current_group = None
    emitted_override_groups = set()
    for item in base_items:
        group_id = BASE_GROUP_OVERRIDES.get(item, original_group_by_item[item])
        if item in BASE_GROUP_OVERRIDES:
            if group_id in emitted_override_groups:
                continue
            group_items = [
                base_item for base_item in base_items
                if BASE_GROUP_OVERRIDES.get(base_item) == group_id
            ]
            price = next((existing_base_prices[base_item] for base_item in group_items if base_item in existing_base_prices), 1)
            base_groups.append({
                "id": group_id,
                "pawn_price": price,
                "items": group_items,
            })
            emitted_override_groups.add(group_id)
            current_group = None
            continue

        price = existing_base_prices.get(item, 1)
        if current_group is None or current_group["id"] != group_id or current_group["pawn_price"] != price:
            current_group = {
                "id": group_id,
                "pawn_price": price,
                "items": [],
            }
            base_groups.append(current_group)
        current_group["items"].append(item)

    derived_groups = []
    for item in derived_items:
        recipes = []
        for formula in formulas_by_item[item]:
            recipe = {
                "recipe_id": formula["recipe_id"],
                "type": formula["type"],
                "result_count": formula["result_count"],
                "ingredients": formula["ingredients"],
            }
            recipes.append(recipe)
        derived_groups.append({
            "id": item_name_for_group(item),
            "items": [item],
            "recipes": recipes,
        })

    base_config = {
        "comment": "Manual Sabi machine base prices. Prices are in Xiao Sabi. Items listed here are not calculated from recipes, or were intentionally kept as base prices.",
        "symbols": [
            {
                "id": GENERIC_DYE,
                "pawn_price": existing_symbol_prices.get(GENERIC_DYE, 1),
                "comment": "Virtual price used by dyed item formulas instead of a concrete dye item."
            },
            {
                "id": LAVA,
                "pawn_price": existing_symbol_prices.get(LAVA, 1),
                "comment": "Virtual price used by lava bucket formulas instead of a concrete lava item."
            },
            {
                "id": POWDER_SNOW,
                "pawn_price": existing_symbol_prices.get(POWDER_SNOW, 1),
                "comment": "Virtual price used by powder snow bucket formulas instead of a concrete powder snow item."
            },
            {
                "id": AXOLOTL,
                "pawn_price": existing_symbol_prices.get(AXOLOTL, 1),
                "comment": "Virtual price used by axolotl bucket formulas instead of a concrete axolotl item."
            },
            {
                "id": TADPOLE,
                "pawn_price": existing_symbol_prices.get(TADPOLE, 1),
                "comment": "Virtual price used by tadpole bucket formulas instead of a concrete tadpole item."
            },
            {
                "id": HONEY,
                "pawn_price": existing_symbol_prices.get(HONEY, 144),
                "comment": "Virtual price used by honey item formulas instead of a concrete honey item."
            }
        ],
        "groups": base_groups,
    }
    derived_config = {
        "comment": "Calculated Sabi machine prices. Workbench and stonecutter are free; cooking adds 1/8 coal per output; dyed item formulas use sabi:generic_dye.",
        "groups": derived_groups,
    }

    write_json(BASE_PRICES_PATH, base_config)
    write_json(DERIVED_PRICES_PATH, derived_config)

    unresolved_with_formulas = [item for item in base_items if item in formulas_by_item and item not in forced_base]
    symbol_count = len(base_config.get("symbols", []))
    report_lines = [
        "# Sabi Machine Price Rule Report",
        "",
        "This file is a short snapshot of how the Sabi machine price tables are organized. It is not used by the mod at runtime.",
        "",
        "## Current Counts",
        "",
        f"- Allowed items: {len(allowed_items)}",
        f"- Base price items: {len(base_items)}",
        f"- Virtual symbols: {symbol_count}",
        f"- Derived price items: {len(derived_items)}",
        f"- Derived recipe entries: {sum(len(group['recipes']) for group in derived_groups)}",
        f"- Fortune III loot formulas: {loot_formula_count}",
        "",
        "## How Prices Are Split",
        "",
        "- `base_prices.json` holds manually priced items and virtual symbols such as `sabi:generic_dye`, `sabi:lava`, and `sabi:honey`.",
        "- `derived_prices.json` holds calculated prices from recipes, manual equivalences, loot expectations, and symbol-based formulas.",
        "- If multiple formulas exist for an item, the resolver uses the cheapest resolvable formula.",
        "",
        "## Important Manual Rules",
        "",
        "- Dyed items use `sabi:generic_dye`; several bucket contents and honey are virtual symbols.",
        "- Several equivalences intentionally override vanilla recipes, such as concrete from concrete powder, dead coral from live coral, filled maps from maps, clocks and templates from gems, copper golem statues from copper blocks plus pumpkins, and honey items from `sabi:honey`.",
        "- Ore-like drops use Fortune III loot formulas only for selected allowlisted blocks.",
        "- Some raw resources and fuels remain manually base-priced instead of being derived from reverse or burn-time recipes.",
    ]
    if unresolved_with_formulas:
        report_lines.extend(["", "## Base-Priced Despite Available Formulas", ""])
        report_lines.extend(f"- `{item}`" for item in unresolved_with_formulas[:20])
        if len(unresolved_with_formulas) > 20:
            report_lines.append(f"- ... and {len(unresolved_with_formulas) - 20} more")
    REPORT_PATH.write_text("\n".join(report_lines) + "\n", encoding="utf-8")
    print(f"Wrote {BASE_PRICES_PATH}")
    print(f"Wrote {DERIVED_PRICES_PATH}")
    print(f"Wrote {REPORT_PATH}")
    print(f"base={len(base_items)} derived={len(derived_items)}")


if __name__ == "__main__":
    main()
