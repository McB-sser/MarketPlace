package de.mcbesser.marketplace.storage;

import de.mcbesser.marketplace.MarketplacePlugin;
import de.mcbesser.marketplace.gui.GuiItems;
import de.mcbesser.marketplace.gui.MenuHolder;
import de.mcbesser.marketplace.gui.MenuType;
import de.mcbesser.marketplace.util.CurrencyFormatter;
import de.mcbesser.marketplace.util.MessageUtil;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class ClaimStorage {

    public static final String CONTEXT_HUB = "hub";
    public static final String CONTEXT_MARKET = "market";
    public static final String CONTEXT_AUCTION = "auction";
    public static final String CONTEXT_LOTTO = "lotto";
    public static final String CONTEXT_TRADE = "trade";

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
            .withZone(ZoneId.systemDefault());
    private final MarketplacePlugin plugin;
    private final File file;
    private final AtomicInteger nextId = new AtomicInteger(1);
    private final Map<UUID, List<ClaimEntry>> claims = new HashMap<>();

    public ClaimStorage(MarketplacePlugin plugin) throws IOException {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "claims.yml");
        load();
    }

    public void addClaim(UUID playerId, ItemStack item, String source, double lastPrice, String details) {
        claims.computeIfAbsent(playerId, ignored -> new ArrayList<>())
                .add(new ClaimEntry(nextId.getAndIncrement(), item.clone(), source, lastPrice, details, System.currentTimeMillis()));
        save();
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            MessageUtil.sendActions(player, "Ein Item wurde in dein Abholfach gelegt: " + source,
                    MessageUtil.action("Abholfach \u00f6ffnen", "marketplace"));
        }
    }

    public void openClaims(Player player, int page) {
        openClaims(player, page, CONTEXT_HUB);
    }

    public void openClaims(Player player, int page, String context) {
        List<ClaimEntry> entries = claims.getOrDefault(player.getUniqueId(), List.of());
        Inventory inventory = Bukkit.createInventory(new MenuHolder(MenuType.CLAIMS, page, context), 54, "Abholfach");
        int start = page * 45;
        for (int slot = 0; slot < 45; slot++) {
            int index = start + slot;
            if (index >= entries.size()) {
                break;
            }
            ClaimEntry entry = entries.get(index);
            inventory.setItem(slot, createDisplay(entry));
        }
        inventory.setItem(45, GuiItems.button(Material.COMPASS, "&aMarktplatz", List.of("&7Zum Hauptmen\u00fc")));
        inventory.setItem(46, GuiItems.button(Material.ARROW, "&eZur\u00fcck", List.of("&7Vorherige Seite")));
        inventory.setItem(49, GuiItems.button(Material.COMPASS, "&aZur\u00fcck zum Men\u00fc", List.of(contextDescription(context))));
        inventory.setItem(53, GuiItems.button(Material.ARROW, "&eWeiter", List.of("&7N\u00e4chste Seite")));
        player.openInventory(inventory);
    }

    public void handleClaimClick(Player player, int rawSlot, int page, String context) {
        List<ClaimEntry> entries = claims.getOrDefault(player.getUniqueId(), new ArrayList<>());
        if (rawSlot == 45) {
            player.performCommand("marketplace");
            return;
        }
        if (rawSlot == 46 && page > 0) {
            openClaims(player, page - 1, context);
            return;
        }
        if (rawSlot == 49) {
            player.performCommand(switchCommand(context));
            return;
        }
        if (rawSlot == 53 && ((page + 1) * 45) < entries.size()) {
            openClaims(player, page + 1, context);
            return;
        }
        if (rawSlot < 0 || rawSlot >= 45) {
            return;
        }
        int index = page * 45 + rawSlot;
        if (index >= entries.size()) {
            return;
        }
        ClaimEntry entry = entries.remove(index);
        Map<Integer, ItemStack> rest = player.getInventory().addItem(entry.getItem());
        if (!rest.isEmpty()) {
            entries.add(index, entry);
            MessageUtil.send(player, "Dein Inventar ist voll.");
            return;
        }
        claims.put(player.getUniqueId(), entries);
        MessageUtil.send(player, "Item aus dem Abholfach entnommen.");
        save();
        openClaims(player, page, context);
    }

    private String switchCommand(String context) {
        return switch (context == null ? "" : context) {
            case CONTEXT_MARKET -> "market";
            case CONTEXT_AUCTION -> "auction";
            case CONTEXT_LOTTO -> "lotto";
            case CONTEXT_TRADE -> "trade";
            default -> "marketplace";
        };
    }

    private String contextDescription(String context) {
        return switch (context == null ? "" : context) {
            case CONTEXT_MARKET -> "&7Zur Markt\u00fcbersicht";
            case CONTEXT_AUCTION -> "&7Zur Auktions\u00fcbersicht";
            case CONTEXT_LOTTO -> "&7Zur Lotto\u00fcbersicht";
            case CONTEXT_TRADE -> "&7Zur Handels\u00fcbersicht";
            default -> "&7Zum Marktplatz-Hauptmen\u00fc";
        };
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("nextId", nextId.get());
        for (Map.Entry<UUID, List<ClaimEntry>> entry : claims.entrySet()) {
            String root = "claims." + entry.getKey();
            int index = 0;
            for (ClaimEntry claim : entry.getValue()) {
                String path = root + "." + index++;
                yaml.set(path + ".id", claim.getId());
                yaml.set(path + ".item", claim.getItem());
                yaml.set(path + ".source", claim.getSource());
                yaml.set(path + ".lastPrice", claim.getLastPrice());
                yaml.set(path + ".details", claim.getDetails());
                yaml.set(path + ".createdAt", claim.getCreatedAt());
            }
        }
        try {
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Konnte claims.yml nicht speichern: " + exception.getMessage());
        }
    }

    private void load() throws IOException {
        if (!file.exists() && !file.createNewFile()) {
            throw new IOException("claims.yml konnte nicht erstellt werden.");
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        nextId.set(yaml.getInt("nextId", 1));
        ConfigurationSection section = yaml.getConfigurationSection("claims");
        if (section == null) {
            return;
        }
        for (String playerKey : section.getKeys(false)) {
            List<ClaimEntry> list = new ArrayList<>();
            ConfigurationSection playerSection = section.getConfigurationSection(playerKey);
            if (playerSection == null) {
                continue;
            }
            for (String key : playerSection.getKeys(false)) {
                String path = "claims." + playerKey + "." + key;
                ItemStack item = yaml.getItemStack(path + ".item");
                if (item == null) {
                    continue;
                }
                list.add(new ClaimEntry(
                        yaml.getInt(path + ".id"),
                        item,
                        yaml.getString(path + ".source", "Unbekannt"),
                        yaml.getDouble(path + ".lastPrice"),
                        yaml.getString(path + ".details", ""),
                        yaml.getLong(path + ".createdAt")
                ));
            }
            claims.put(UUID.fromString(playerKey), list);
        }
    }

    private ItemStack createDisplay(ClaimEntry entry) {
        ItemStack display = entry.getItem().clone();
        ItemMeta meta = display.getItemMeta();
        List<String> lore = meta != null && meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add(" ");
        lore.add("\u00A77Quelle: \u00A7f" + entry.getSource());
        lore.add("\u00A77Letzter Preis: \u00A76" + CurrencyFormatter.shortAmount(entry.getLastPrice()));
        if (!entry.getDetails().isBlank()) {
            lore.add("\u00A77Info: \u00A7f" + entry.getDetails());
        }
        lore.add("\u00A77Eingelagert: \u00A7f" + FORMATTER.format(Instant.ofEpochMilli(entry.getCreatedAt())));
        lore.add("\u00A7aKlick zum Abholen");
        if (meta != null) {
            meta.setLore(lore);
            display.setItemMeta(meta);
        }
        return display;
    }
}


