package de.mcbesser.marketplace.pricing;

import de.mcbesser.marketplace.MarketplacePlugin;
import de.mcbesser.marketplace.util.GermanItemNames;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class PriceGuideManager {

    private final MarketplacePlugin plugin;
    private final File file;
    private final Map<String, PriceGuideEntry> entries = new HashMap<>();

    public PriceGuideManager(MarketplacePlugin plugin) throws IOException {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "price-guide.yml");
        load();
    }

    public OptionalDouble getReferencePrice(ItemStack item) {
        PriceGuideEntry entry = entries.get(keyOf(item));
        return entry == null ? OptionalDouble.empty() : OptionalDouble.of(entry.getReferencePrice());
    }

    public boolean isPriceAllowed(ItemStack item, double price) {
        OptionalDouble reference = getReferencePrice(item);
        if (reference.isEmpty()) {
            return true;
        }
        double center = reference.getAsDouble();
        double min = Math.max(1, Math.floor(center * 0.9D));
        double max = Math.ceil(center * 1.1D);
        return price >= min && price <= max;
    }

    public String allowedRangeText(ItemStack item) {
        OptionalDouble reference = getReferencePrice(item);
        if (reference.isEmpty()) {
            return "frei, da noch kein Richtwert existiert";
        }
        double center = reference.getAsDouble();
        double min = Math.max(1, Math.floor(center * 0.9D));
        double max = Math.ceil(center * 1.1D);
        return (int) min + " - " + (int) max + " Coins";
    }

    public void registerObservation(ItemStack item, double price) {
        String key = keyOf(item);
        PriceGuideEntry entry = entries.get(key);
        if (entry == null) {
            entries.put(key, new PriceGuideEntry(key, displayName(item), Math.max(1, Math.round(price)), 1));
        } else {
            entry.applyObservation(price);
        }
        save();
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (PriceGuideEntry entry : entries.values()) {
            String path = "entries." + entry.getKey();
            yaml.set(path + ".name", entry.getDisplayName());
            yaml.set(path + ".referencePrice", entry.getReferencePrice());
            yaml.set(path + ".samples", entry.getSamples());
        }
        try {
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Konnte price-guide.yml nicht speichern: " + exception.getMessage());
        }
    }

    public String keyOf(ItemStack item) {
        ItemStack normalized = item.clone();
        normalized.setAmount(1);
        Map<String, Object> serialized = normalized.serialize();
        serialized.remove("amount");
        return Integer.toHexString(serialized.toString().hashCode());
    }

    private void load() throws IOException {
        if (!file.exists() && !file.createNewFile()) {
            throw new IOException("price-guide.yml konnte nicht erstellt werden.");
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("entries");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            String path = "entries." + key;
            entries.put(key, new PriceGuideEntry(
                    key,
                    yaml.getString(path + ".name", key),
                    yaml.getDouble(path + ".referencePrice", 1),
                    yaml.getInt(path + ".samples", 1)
            ));
        }
    }

    private String displayName(ItemStack item) {
        return GermanItemNames.of(item);
    }
}


