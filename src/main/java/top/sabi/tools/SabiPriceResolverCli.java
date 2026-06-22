package top.sabi.tools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import top.sabi.SabiPriceRules;

public final class SabiPriceResolverCli {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private SabiPriceResolverCli() {
    }

    public static void main(String[] args) throws IOException {
        Path itemsPath = pathFromEnv("SABI_PRICE_ITEMS");
        Path basePath = pathFromEnv("SABI_PRICE_BASE");
        Path derivedPath = pathFromEnv("SABI_PRICE_DERIVED");
        String output = System.getenv("SABI_PRICE_OUTPUT");

        if (itemsPath == null || basePath == null || derivedPath == null) {
            throw new IllegalArgumentException("SABI_PRICE_ITEMS, SABI_PRICE_BASE and SABI_PRICE_DERIVED must be set.");
        }

        JsonObject result = resolve(readJson(itemsPath), readJson(basePath), readJson(derivedPath));
        if (output == null || output.isBlank()) {
            System.out.println(GSON.toJson(result));
        } else {
            try (Writer writer = Files.newBufferedWriter(Path.of(output), StandardCharsets.UTF_8)) {
                GSON.toJson(result, writer);
                writer.write(System.lineSeparator());
            }
        }
    }

    private static Path pathFromEnv(String name) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? null : Path.of(value);
    }

    private static JsonObject readJson(Path path) throws IOException {
        String text = Files.readString(path, StandardCharsets.UTF_8);
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        return GSON.fromJson(text, JsonObject.class);
    }

    private static JsonObject resolve(JsonObject itemsRoot, JsonObject baseRoot, JsonObject derivedRoot) {
        SabiPriceRules.Resolver resolver = SabiPriceRules.resolver(baseRoot, derivedRoot);
        JsonObject root = new JsonObject();
        JsonArray itemArray = new JsonArray();
        int unresolved = 0;

        for (SabiPriceRules.AllowedItem allowedItem : SabiPriceRules.allowedItems(itemsRoot)) {
            Optional<Integer> price = resolver.resolvedPrice(allowedItem.id());
            if (price.isEmpty()) {
                unresolved++;
            }

            JsonObject item = new JsonObject();
            item.addProperty("id", allowedItem.id());
            item.addProperty("group", allowedItem.groupId());
            item.addProperty("source", resolver.sourceLabel(allowedItem.id()));
            addOptionalInt(item, "pawn_price", price);
            addOptionalInt(item, "redeem_price", price.map(SabiPriceRules::redeemPrice));
            item.add("formulas", formulas(resolver, allowedItem.id()));
            itemArray.add(item);
        }

        root.add("items", itemArray);
        root.addProperty("allowed_count", itemArray.size());
        root.addProperty("unresolved_count", unresolved);
        root.add("direct_dependents", directDependents(resolver));
        return root;
    }

    private static JsonArray formulas(SabiPriceRules.Resolver resolver, String itemId) {
        JsonArray array = new JsonArray();
        for (SabiPriceRules.Formula formula : resolver.formulas(itemId)) {
            JsonObject object = new JsonObject();
            object.addProperty("recipe_id", formula.recipeId());
            object.addProperty("type", formula.type());
            object.addProperty("result_count", formula.resultCount());
            addOptionalInt(object, "price", resolver.formulaPrice(formula));

            JsonArray ingredients = new JsonArray();
            for (SabiPriceRules.Term term : formula.ingredients()) {
                JsonObject termObject = new JsonObject();
                termObject.addProperty("count", term.count());
                JsonArray candidates = new JsonArray();
                for (String candidateId : term.itemIds()) {
                    JsonObject candidate = new JsonObject();
                    candidate.addProperty("id", candidateId);
                    addOptionalInt(candidate, "price", resolver.price(candidateId));
                    candidates.add(candidate);
                }
                termObject.add("candidates", candidates);
                ingredients.add(termObject);
            }
            object.add("ingredients", ingredients);
            array.add(object);
        }
        return array;
    }

    private static JsonObject directDependents(SabiPriceRules.Resolver resolver) {
        Map<String, List<String>> dependents = new TreeMap<>();
        for (Map.Entry<String, List<SabiPriceRules.Formula>> entry : resolver.formulasById().entrySet()) {
            String output = entry.getKey();
            for (SabiPriceRules.Formula formula : entry.getValue()) {
                for (SabiPriceRules.Term term : formula.ingredients()) {
                    for (String ingredient : term.itemIds()) {
                        dependents.computeIfAbsent(ingredient, ignored -> new ArrayList<>()).add(output);
                    }
                }
            }
        }

        JsonObject object = new JsonObject();
        for (Map.Entry<String, List<String>> entry : dependents.entrySet()) {
            JsonArray array = new JsonArray();
            for (String id : distinctSorted(entry.getValue())) {
                array.add(id);
            }
            object.add(entry.getKey(), array);
        }
        return object;
    }

    private static List<String> distinctSorted(List<String> values) {
        Map<String, Boolean> seen = new HashMap<>();
        for (String value : values) {
            seen.put(value, true);
        }
        return seen.keySet().stream().sorted().toList();
    }

    private static void addOptionalInt(JsonObject object, String name, Optional<Integer> value) {
        JsonElement element = value.<JsonElement>map(JsonPrimitive::new).orElse(JsonNull.INSTANCE);
        object.add(name, element);
    }
}
