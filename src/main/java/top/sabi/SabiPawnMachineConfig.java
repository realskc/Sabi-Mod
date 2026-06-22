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
            this(pawn, SabiPriceRules.redeemPrice(pawn));
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

            SabiPriceRules.Resolver resolver = SabiPriceRules.resolver(baseRoot, derivedRoot);
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

                    Optional<Integer> resolvedPrice = resolver.resolvedPrice(id.toString());
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
    }
}
