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

    private SabiPawnMachineConfig() {
    }

    public static Config load(MinecraftServer server) {
        Optional<Resource> resource = server.getResourceManager().getResource(CONFIG_ID);
        if (resource.isEmpty()) {
            return Config.defaults();
        }

        try (Reader reader = resource.get().openAsReader()) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            return Config.fromJson(root);
        } catch (Exception ignored) {
            return Config.defaults();
        }
    }

    public record Price(int pawn, int redeem) {
        public Price {
            pawn = Math.max(0, pawn);
            redeem = Math.max(0, redeem);
        }
    }

    public static final class Config {
        private final boolean includeAllRegisteredItems;
        private final int defaultPawnPrice;
        private final int defaultRedeemPrice;
        private final Set<String> excludedItems;
        private final Set<String> excludedSuffixes;
        private final Map<String, Price> overrides;

        private Config(boolean includeAllRegisteredItems, int defaultPawnPrice, int defaultRedeemPrice, Set<String> excludedItems, Set<String> excludedSuffixes, Map<String, Price> overrides) {
            this.includeAllRegisteredItems = includeAllRegisteredItems;
            this.defaultPawnPrice = Math.max(0, defaultPawnPrice);
            this.defaultRedeemPrice = Math.max(0, defaultRedeemPrice);
            this.excludedItems = excludedItems;
            this.excludedSuffixes = excludedSuffixes;
            this.overrides = overrides;
        }

        public static Config defaults() {
            return new Config(true, 1, 2, new HashSet<>(), new HashSet<>(), new HashMap<>());
        }

        public static Config fromJson(JsonObject root) {
            if (root == null) {
                return defaults();
            }

            boolean includeAll = getBoolean(root, "include_all_registered_items", true);
            int defaultPawn = getInt(root, "default_pawn_price", 1);
            int defaultRedeem = getInt(root, "default_redeem_price", 2);
            Set<String> excludedItems = stringSet(root.getAsJsonArray("exclude_items"));
            Set<String> excludedSuffixes = stringSet(root.getAsJsonArray("exclude_id_suffixes"));
            Map<String, Price> overrides = new HashMap<>();

            JsonObject overrideRoot = root.getAsJsonObject("price_overrides");
            if (overrideRoot != null) {
                for (Map.Entry<String, JsonElement> entry : overrideRoot.entrySet()) {
                    if (entry.getValue().isJsonObject()) {
                        JsonObject price = entry.getValue().getAsJsonObject();
                        overrides.put(entry.getKey(), new Price(getInt(price, "pawn", defaultPawn), getInt(price, "redeem", defaultRedeem)));
                    }
                }
            }

            return new Config(includeAll, defaultPawn, defaultRedeem, excludedItems, excludedSuffixes, overrides);
        }

        public boolean isAllowed(Item item) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if (id == null || new ItemStack(item).isEmpty()) {
                return false;
            }

            String key = id.toString();
            if (this.excludedItems.contains(key)) {
                return false;
            }
            for (String suffix : this.excludedSuffixes) {
                if (!suffix.isEmpty() && key.endsWith(suffix)) {
                    return false;
                }
            }
            return this.includeAllRegisteredItems || this.overrides.containsKey(key);
        }

        public Price price(Item item) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if (id == null) {
                return new Price(0, 0);
            }
            return this.overrides.getOrDefault(id.toString(), new Price(this.defaultPawnPrice, this.defaultRedeemPrice));
        }

        public List<Item> allowedItems() {
            List<Item> items = new ArrayList<>();
            BuiltInRegistries.ITEM.stream().filter(this::isAllowed).forEach(items::add);
            return items;
        }

        private static boolean getBoolean(JsonObject object, String name, boolean fallback) {
            JsonElement element = object.get(name);
            return element == null ? fallback : element.getAsBoolean();
        }

        private static int getInt(JsonObject object, String name, int fallback) {
            JsonElement element = object.get(name);
            return element == null ? fallback : element.getAsInt();
        }

        private static Set<String> stringSet(JsonArray array) {
            Set<String> result = new HashSet<>();
            if (array == null) {
                return result;
            }
            for (JsonElement element : array) {
                result.add(element.getAsString());
            }
            return result;
        }
    }
}
