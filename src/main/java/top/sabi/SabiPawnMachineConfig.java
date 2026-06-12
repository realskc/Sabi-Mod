package top.sabi;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class SabiPawnMachineConfig {
    private static final Gson GSON = new Gson();
    private static final Identifier CONFIG_ID = Identifier.fromNamespaceAndPath(Sabi.MOD_ID, "sabi_machine/items.json");
    private static final Identifier BASE_PRICES_ID = Identifier.fromNamespaceAndPath(Sabi.MOD_ID, "sabi_machine/base_prices.json");
    private static final Identifier DERIVED_PRICES_ID = Identifier.fromNamespaceAndPath(Sabi.MOD_ID, "sabi_machine/derived_prices.json");

    private SabiPawnMachineConfig() {
    }

    public static Config load(MinecraftServer server) {
        Optional<Resource> resource = server.getResourceManager().getResource(CONFIG_ID);
        if (resource.isEmpty()) {
            return Config.defaults();
        }

        try (Reader reader = resource.get().openAsReader()) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            Optional<JsonObject> basePrices = loadJson(server, BASE_PRICES_ID);
            Optional<JsonObject> derivedPrices = loadJson(server, DERIVED_PRICES_ID);
            return Config.fromPriceRules(root, basePrices.orElse(null), derivedPrices.orElse(null));
        } catch (Exception ignored) {
            return Config.defaults();
        }
    }

    private static Optional<JsonObject> loadJson(MinecraftServer server, Identifier id) {
        Optional<Resource> resource = server.getResourceManager().getResource(id);
        if (resource.isEmpty()) {
            return Optional.empty();
        }

        try (Reader reader = resource.get().openAsReader()) {
            return Optional.ofNullable(GSON.fromJson(reader, JsonObject.class));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    public record Price(int pawn, int redeem) {
        public Price {
            pawn = Math.max(0, pawn);
            redeem = Math.max(0, redeem);
        }

        public Price(int pawn) {
            this(pawn, redeemPrice(pawn));
        }

        private static int redeemPrice(int pawn) {
            int nonNegativePawn = Math.max(0, pawn);
            return (int)Math.min(Integer.MAX_VALUE, (nonNegativePawn * 6L + 4L) / 5L);
        }
    }

    public record Entry(Item item, Price price) {
    }

    public static final class Config {
        private final List<Entry> entries;
        private final Map<String, Price> pricesById;

        private Config(List<Entry> entries, Map<String, Price> pricesById) {
            this.entries = List.copyOf(entries);
            this.pricesById = Map.copyOf(pricesById);
        }

        public static Config defaults() {
            return new Config(List.of(), Map.of());
        }

        public static Config fromPriceRules(JsonObject itemRoot, JsonObject baseRoot, JsonObject derivedRoot) {
            if (itemRoot == null) {
                return defaults();
            }

            PriceResolver resolver = PriceResolver.fromJson(baseRoot, derivedRoot);
            List<Entry> entries = new ArrayList<>();
            Map<String, Price> pricesById = new HashMap<>();
            Set<String> seen = new HashSet<>();
            JsonArray groupArray = itemRoot.getAsJsonArray("groups");
            if (groupArray == null) {
                return defaults();
            }

            for (JsonElement element : groupArray) {
                if (!element.isJsonObject()) {
                    continue;
                }

                JsonObject groupObject = element.getAsJsonObject();
                JsonArray itemArray = groupObject.getAsJsonArray("items");
                if (itemArray == null) {
                    continue;
                }

                for (JsonElement itemElement : itemArray) {
                    if (!itemElement.isJsonPrimitive()) {
                        continue;
                    }

                    Identifier id = Identifier.tryParse(itemElement.getAsString());
                    if (id == null || !seen.add(id.toString())) {
                        continue;
                    }

                    Item item = BuiltInRegistries.ITEM.getValue(id);
                    if (item == null || new ItemStack(item).isEmpty()) {
                        continue;
                    }

                    Optional<Integer> resolvedPrice = resolver.price(id.toString());
                    if (resolvedPrice.isEmpty()) {
                        continue;
                    }

                    Price price = new Price(resolvedPrice.get());
                    entries.add(new Entry(item, price));
                    pricesById.put(id.toString(), price);
                }
            }

            return new Config(entries, pricesById);
        }

        public boolean isAllowed(Item item) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            return id != null && this.pricesById.containsKey(id.toString());
        }

        public Price price(Item item) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if (id == null) {
                return new Price(0, 0);
            }
            return this.pricesById.getOrDefault(id.toString(), new Price(0, 0));
        }

        public List<Item> allowedItems() {
            List<Item> items = new ArrayList<>();
            for (Entry entry : this.entries) {
                items.add(entry.item());
            }
            return items;
        }

        private static int getInt(JsonObject object, String name, int fallback) {
            JsonElement element = object.get(name);
            return element == null ? fallback : element.getAsInt();
        }
    }

    private record Formula(double resultCount, List<Term> ingredients) {
    }

    private record Term(List<String> itemIds, double count) {
    }

    private static final class PriceResolver {
        private final Map<String, Integer> basePrices;
        private final Map<String, List<Formula>> formulasById;
        private final Map<String, Optional<Integer>> cache = new HashMap<>();
        private final Set<String> resolving = new HashSet<>();

        private PriceResolver(Map<String, Integer> basePrices, Map<String, List<Formula>> formulasById) {
            this.basePrices = Map.copyOf(basePrices);
            this.formulasById = Map.copyOf(formulasById);
        }

        static PriceResolver fromJson(JsonObject baseRoot, JsonObject derivedRoot) {
            Map<String, Integer> basePrices = new HashMap<>();
            Map<String, List<Formula>> formulasById = new HashMap<>();
            readBasePrices(baseRoot, basePrices);
            readDerivedPrices(derivedRoot, formulasById);
            return new PriceResolver(basePrices, formulasById);
        }

        Optional<Integer> price(String id) {
            Integer basePrice = this.basePrices.get(id);
            if (basePrice != null) {
                return Optional.of(basePrice);
            }

            Optional<Integer> cached = this.cache.get(id);
            if (cached != null) {
                return cached;
            }

            if (!this.resolving.add(id)) {
                return Optional.empty();
            }

            Optional<Integer> resolved = this.computePrice(id);
            this.resolving.remove(id);
            this.cache.put(id, resolved);
            return resolved;
        }

        private Optional<Integer> computePrice(String id) {
            List<Formula> formulas = this.formulasById.get(id);
            if (formulas == null || formulas.isEmpty()) {
                return Optional.empty();
            }

            Integer best = null;
            for (Formula formula : formulas) {
                Optional<Integer> price = this.computeFormulaPrice(formula);
                if (price.isPresent() && (best == null || price.get() < best)) {
                    best = price.get();
                }
            }
            return best == null ? Optional.empty() : Optional.of(best);
        }

        private Optional<Integer> computeFormulaPrice(Formula formula) {
            double total = 0.0D;
            for (Term term : formula.ingredients()) {
                Integer bestIngredient = null;
                for (String candidate : term.itemIds()) {
                    Optional<Integer> candidatePrice = this.price(candidate);
                    if (candidatePrice.isPresent() && (bestIngredient == null || candidatePrice.get() < bestIngredient)) {
                        bestIngredient = candidatePrice.get();
                    }
                }
                if (bestIngredient == null) {
                    return Optional.empty();
                }
                total += bestIngredient * term.count();
            }

            double resultCount = Math.max(1.0D, formula.resultCount());
            return Optional.of((int)Math.min(Integer.MAX_VALUE, Math.max(0.0D, Math.floor(total / resultCount))));
        }

        private static void readBasePrices(JsonObject root, Map<String, Integer> prices) {
            if (root == null) {
                return;
            }

            JsonArray symbols = root.getAsJsonArray("symbols");
            if (symbols != null) {
                for (JsonElement element : symbols) {
                    if (element.isJsonObject()) {
                        JsonObject symbol = element.getAsJsonObject();
                        String id = getString(symbol, "id");
                        if (id != null) {
                            prices.put(id, Math.max(0, Config.getInt(symbol, "pawn_price", 1)));
                        }
                    }
                }
            }

            JsonArray groups = root.getAsJsonArray("groups");
            if (groups == null) {
                return;
            }

            for (JsonElement element : groups) {
                if (!element.isJsonObject()) {
                    continue;
                }

                JsonObject group = element.getAsJsonObject();
                int price = Math.max(0, Config.getInt(group, "pawn_price", 1));
                JsonArray items = group.getAsJsonArray("items");
                if (items == null) {
                    continue;
                }

                for (JsonElement item : items) {
                    if (item.isJsonPrimitive()) {
                        prices.putIfAbsent(item.getAsString(), price);
                    }
                }
            }
        }

        private static void readDerivedPrices(JsonObject root, Map<String, List<Formula>> formulasById) {
            if (root == null) {
                return;
            }

            JsonArray groups = root.getAsJsonArray("groups");
            if (groups == null) {
                return;
            }

            for (JsonElement element : groups) {
                if (!element.isJsonObject()) {
                    continue;
                }

                JsonObject group = element.getAsJsonObject();
                List<Formula> formulas = readFormulas(group);
                if (formulas.isEmpty()) {
                    continue;
                }

                JsonArray items = group.getAsJsonArray("items");
                if (items == null) {
                    continue;
                }

                for (JsonElement item : items) {
                    if (item.isJsonPrimitive()) {
                        formulasById.computeIfAbsent(item.getAsString(), ignored -> new ArrayList<>()).addAll(formulas);
                    }
                }
            }
        }

        private static List<Formula> readFormulas(JsonObject group) {
            List<Formula> formulas = new ArrayList<>();
            JsonArray recipes = group.getAsJsonArray("recipes");
            if (recipes != null) {
                for (JsonElement recipe : recipes) {
                    if (recipe.isJsonObject()) {
                        readFormula(recipe.getAsJsonObject()).ifPresent(formulas::add);
                    }
                }
                return formulas;
            }

            JsonObject formula = group.getAsJsonObject("formula");
            if (formula != null) {
                readFormula(formula).ifPresent(formulas::add);
            }
            return formulas;
        }

        private static Optional<Formula> readFormula(JsonObject object) {
            double resultCount = getDouble(object, "result_count", 1.0D);
            JsonArray ingredients = object.getAsJsonArray("ingredients");
            if (ingredients == null) {
                return Optional.empty();
            }

            List<Term> terms = new ArrayList<>();
            for (JsonElement element : ingredients) {
                if (!element.isJsonObject()) {
                    continue;
                }

                JsonObject ingredient = element.getAsJsonObject();
                List<String> itemIds = new ArrayList<>();
                String item = getString(ingredient, "item");
                if (item != null) {
                    itemIds.add(item);
                }

                JsonArray alternatives = ingredient.getAsJsonArray("any_of");
                if (alternatives != null) {
                    for (JsonElement alternative : alternatives) {
                        if (alternative.isJsonPrimitive()) {
                            itemIds.add(alternative.getAsString());
                        }
                    }
                }

                if (!itemIds.isEmpty()) {
                    terms.add(new Term(List.copyOf(itemIds), getDouble(ingredient, "count", 1.0D)));
                }
            }

            return terms.isEmpty() ? Optional.empty() : Optional.of(new Formula(resultCount, List.copyOf(terms)));
        }

        private static String getString(JsonObject object, String name) {
            JsonElement element = object.get(name);
            return element == null || !element.isJsonPrimitive() ? null : element.getAsString();
        }

        private static double getDouble(JsonObject object, String name, double fallback) {
            JsonElement element = object.get(name);
            return element == null ? fallback : element.getAsDouble();
        }
    }
}
