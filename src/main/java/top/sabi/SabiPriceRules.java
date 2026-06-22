package top.sabi;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class SabiPriceRules {
    private SabiPriceRules() {
    }

    public static int redeemPrice(int pawn) {
        int nonNegativePawn = Math.max(0, pawn);
        return (int)Math.min(Integer.MAX_VALUE, (nonNegativePawn * 6L + 4L) / 5L);
    }

    public static List<AllowedItem> allowedItems(JsonObject itemRoot) {
        if (itemRoot == null) {
            return List.of();
        }

        JsonArray groupArray = itemRoot.getAsJsonArray("groups");
        if (groupArray == null) {
            return List.of();
        }

        List<AllowedItem> items = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonElement element : groupArray) {
            if (!element.isJsonObject()) {
                continue;
            }

            JsonObject group = element.getAsJsonObject();
            String groupId = getString(group, "id");
            JsonArray itemArray = group.getAsJsonArray("items");
            if (itemArray == null) {
                continue;
            }

            for (JsonElement item : itemArray) {
                if (item.isJsonPrimitive()) {
                    String id = item.getAsString();
                    if (seen.add(id)) {
                        items.add(new AllowedItem(id, groupId == null ? "" : groupId));
                    }
                }
            }
        }
        return List.copyOf(items);
    }

    public static Resolver resolver(JsonObject baseRoot, JsonObject derivedRoot) {
        Map<String, Integer> basePrices = new HashMap<>();
        Map<String, String> baseSources = new HashMap<>();
        Map<String, List<Formula>> formulasById = new HashMap<>();
        readBasePrices(baseRoot, basePrices, baseSources);
        readDerivedPrices(derivedRoot, formulasById);
        return new Resolver(basePrices, baseSources, formulasById);
    }

    private static void readBasePrices(JsonObject root, Map<String, Integer> prices, Map<String, String> sources) {
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
                        prices.put(id, Math.max(0, getInt(symbol, "pawn_price", 1)));
                        sources.put(id, "虚拟符号价");
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
            String groupId = getString(group, "id");
            int price = Math.max(0, getInt(group, "pawn_price", 1));
            JsonArray items = group.getAsJsonArray("items");
            if (items == null) {
                continue;
            }

            for (JsonElement item : items) {
                if (item.isJsonPrimitive()) {
                    String id = item.getAsString();
                    if (!prices.containsKey(id)) {
                        prices.put(id, price);
                        sources.put(id, "基础价: " + (groupId == null ? "" : groupId));
                    }
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

        if (terms.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new Formula(
                getString(object, "recipe_id"),
                getString(object, "type"),
                resultCount,
                List.copyOf(terms)
        ));
    }

    private static int getInt(JsonObject object, String name, int fallback) {
        JsonElement element = object.get(name);
        return element == null ? fallback : element.getAsInt();
    }

    private static String getString(JsonObject object, String name) {
        JsonElement element = object.get(name);
        return element == null || !element.isJsonPrimitive() ? null : element.getAsString();
    }

    private static double getDouble(JsonObject object, String name, double fallback) {
        JsonElement element = object.get(name);
        return element == null ? fallback : element.getAsDouble();
    }

    public record AllowedItem(String id, String groupId) {
    }

    public record Formula(String recipeId, String type, double resultCount, List<Term> ingredients) {
        public Formula {
            recipeId = recipeId == null ? "" : recipeId;
            type = type == null ? "" : type;
            ingredients = List.copyOf(ingredients);
        }
    }

    public record Term(List<String> itemIds, double count) {
        public Term {
            itemIds = List.copyOf(itemIds);
        }
    }

    public static final class Resolver {
        private final Map<String, Integer> basePrices;
        private final Map<String, String> baseSources;
        private final Map<String, List<Formula>> formulasById;
        private final Map<String, Optional<Integer>> cache = new HashMap<>();
        private final Set<String> resolving = new HashSet<>();

        private Resolver(Map<String, Integer> basePrices, Map<String, String> baseSources, Map<String, List<Formula>> formulasById) {
            this.basePrices = Map.copyOf(basePrices);
            this.baseSources = Map.copyOf(baseSources);
            this.formulasById = copyFormulaMap(formulasById);
        }

        public Optional<Integer> price(String id) {
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

        public Optional<Integer> resolvedPrice(String id) {
            Optional<Integer> price = this.price(id);
            if (price.isPresent()) {
                return price;
            }

            Integer best = null;
            for (Formula formula : this.formulas(id)) {
                Optional<Integer> formulaPrice = this.formulaPrice(formula);
                if (formulaPrice.isPresent() && (best == null || formulaPrice.get() < best)) {
                    best = formulaPrice.get();
                }
            }
            if (best == null) {
                return Optional.empty();
            }

            Optional<Integer> resolved = Optional.of(best);
            this.cache.put(id, resolved);
            return resolved;
        }

        public String sourceLabel(String id) {
            String source = this.baseSources.get(id);
            if (source != null) {
                return source;
            }
            return this.formulasById.containsKey(id) ? "派生价" : "未定价";
        }

        public List<Formula> formulas(String id) {
            return this.formulasById.getOrDefault(id, List.of());
        }

        public Map<String, List<Formula>> formulasById() {
            return this.formulasById;
        }

        public Optional<Integer> formulaPrice(Formula formula) {
            return this.computeFormulaPrice(formula);
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

        private static Map<String, List<Formula>> copyFormulaMap(Map<String, List<Formula>> source) {
            Map<String, List<Formula>> result = new HashMap<>();
            for (Map.Entry<String, List<Formula>> entry : source.entrySet()) {
                result.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
            return Map.copyOf(result);
        }
    }
}
