package de.mcbesser.marketplace.market;

import de.mcbesser.marketplace.EconomyService;
import de.mcbesser.marketplace.MarketplacePlugin;
import de.mcbesser.marketplace.gui.GuiItems;
import de.mcbesser.marketplace.gui.MenuHolder;
import de.mcbesser.marketplace.gui.MenuType;
import de.mcbesser.marketplace.pricing.PriceGuideManager;
import de.mcbesser.marketplace.storage.ClaimStorage;
import de.mcbesser.marketplace.util.CurrencyFormatter;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class MarketManager {

    private static final int ITEM_SLOT = 13;
    private final MarketplacePlugin plugin;
    private final EconomyService economyService;
    private final ClaimStorage claimStorage;
    private final PriceGuideManager priceGuideManager;
    private final File file;
    private final List<MarketListing> listings = new ArrayList<>();
    private final AtomicInteger nextId = new AtomicInteger(1);
    private final Map<UUID, Double> pendingSellPrice = new HashMap<>();
    private final Map<UUID, ItemStack> pendingSellItem = new HashMap<>();
    private final Set<UUID> ignoreNextClose = new HashSet<>();

    public MarketManager(MarketplacePlugin plugin, EconomyService economyService, ClaimStorage claimStorage,
                         PriceGuideManager priceGuideManager) throws IOException {
        this.plugin = plugin;
        this.economyService = economyService;
        this.claimStorage = claimStorage;
        this.priceGuideManager = priceGuideManager;
        this.file = new File(plugin.getDataFolder(), "market.yml");
        load();
    }

    public void openMain(Player player) {
        Inventory inventory = Bukkit.createInventory(new MenuHolder(MenuType.MARKET_MAIN), 27, "Markt");
        inventory.setItem(11, GuiItems.button(Material.BOOK, "&aAngebote", List.of("&7Aktuelle Marktangebote ansehen")));
        inventory.setItem(13, GuiItems.button(Material.EMERALD, "&6Preis f\u00fcr eingelegtes/Hand-Item: " + priceRange(player),
                List.of("&7Niedrigster und h\u00f6chster Preis", "&7f\u00fcr das eingelegte Item oder dein Hand-Item")));
        inventory.setItem(15, GuiItems.button(Material.CHEST, "&bVerkaufen",
                List.of("&7Lege ein Item in Slot 13", "&7und stelle den Preis ein")));
        inventory.setItem(22, GuiItems.button(Material.BARREL, "&eAbholfach",
                List.of("&7Abgelaufene oder ausgelagerte Items")));
        player.openInventory(inventory);
    }

    public void openListingPage(Player player, int page) {
        Inventory inventory = Bukkit.createInventory(new MenuHolder(MenuType.MARKET_LIST, page, ""), 54, "Marktangebote");
        List<MarketListing> sorted = listings.stream()
                .sorted(Comparator.comparingDouble(MarketListing::getPrice))
                .toList();
        int start = page * 45;
        for (int slot = 0; slot < 45; slot++) {
            int index = start + slot;
            if (index >= sorted.size()) {
                break;
            }
            inventory.setItem(slot, createListingDisplay(sorted.get(index)));
        }
        inventory.setItem(45, GuiItems.button(Material.ARROW, "&eZur\u00fcck", List.of("&7Vorherige Seite")));
        inventory.setItem(49, GuiItems.button(Material.COMPASS, "&aHauptmenue", List.of("&7Zur Markt\u00fcbersicht")));
        inventory.setItem(53, GuiItems.button(Material.ARROW, "&eWeiter", List.of("&7Naechste Seite")));
        player.openInventory(inventory);
    }

    public void openSellMenu(Player player) {
        double price = pendingSellPrice.getOrDefault(player.getUniqueId(), 100.0D);
        Inventory inventory = Bukkit.createInventory(new MenuHolder(MenuType.MARKET_SELL), 27, "Marktpreis setzen");
        ItemStack item = pendingSellItem.get(player.getUniqueId());
        inventory.setItem(ITEM_SLOT, item == null
                ? GuiItems.button(Material.HOPPER, "&eItem hier einlegen", List.of("&7Klicke mit Item aus deinem Inventar", "&7auf diesen Slot"))
                : item.clone());
        inventory.setItem(10, GuiItems.button(Material.GOLD_NUGGET, "&6Preis fein",
                List.of("&7Links: +1", "&7Rechts: -1", "&7Shift+Links: +10", "&7Shift+Rechts: -10")));
        inventory.setItem(11, GuiItems.button(Material.GOLD_INGOT, "&6Preis grob",
                List.of("&7Links: +100", "&7Rechts: -100", "&7Shift+Links: +1000", "&7Shift+Rechts: -1000")));
        inventory.setItem(22, GuiItems.button(Material.EMERALD, "&6Angebot erstellen: " + CurrencyFormatter.shortAmount(price),
                List.of("&7Marktpreis: " + priceRange(player),
                        "&7Erlaubt: " + allowedRange(player),
                        "&7Ohne Richtwert ist der erste Preis frei",
                        "&aKlick zum Einstellen")));
        player.openInventory(inventory);
    }

    public List<MarketListing> getListingsSnapshot() {
        return new ArrayList<>(listings);
    }

    public MarketListing removeListing(int listingId) {
        MarketListing listing = listings.stream().filter(entry -> entry.getId() == listingId).findFirst().orElse(null);
        if (listing != null) {
            listings.remove(listing);
            save();
        }
        return listing;
    }

    public void handleMainClick(Player player, int rawSlot) {
        if (rawSlot == 11) {
            ignoreNextClose.add(player.getUniqueId());
            openListingPage(player, 0);
        } else if (rawSlot == 15) {
            ignoreNextClose.add(player.getUniqueId());
            openSellMenu(player);
        } else if (rawSlot == 22) {
            ignoreNextClose.add(player.getUniqueId());
            claimStorage.openClaims(player, 0);
        }
    }

    public void handleListingClick(Player player, int rawSlot, int page) {
        List<MarketListing> sorted = listings.stream().sorted(Comparator.comparingDouble(MarketListing::getPrice)).toList();
        if (rawSlot == 45 && page > 0) {
            ignoreNextClose.add(player.getUniqueId());
            openListingPage(player, page - 1);
            return;
        }
        if (rawSlot == 49) {
            ignoreNextClose.add(player.getUniqueId());
            openMain(player);
            return;
        }
        if (rawSlot == 53 && ((page + 1) * 45) < sorted.size()) {
            ignoreNextClose.add(player.getUniqueId());
            openListingPage(player, page + 1);
            return;
        }
        if (rawSlot < 0 || rawSlot >= 45) {
            return;
        }
        int index = page * 45 + rawSlot;
        if (index >= sorted.size()) {
            return;
        }
        buyListing(player, sorted.get(index).getId());
        ignoreNextClose.add(player.getUniqueId());
        openListingPage(player, page);
    }

    public void handleSellClick(Player player, InventoryClickEvent event) {
        if (handleItemPlacement(player, event)) {
            ignoreNextClose.add(player.getUniqueId());
            openSellMenu(player);
            return;
        }
        double current = pendingSellPrice.getOrDefault(player.getUniqueId(), 100.0D);
        switch (event.getRawSlot()) {
            case 10 -> current = Math.max(1, current + resolveStep(event.getClick(), 1, 10));
            case 11 -> current = Math.max(1, current + resolveStep(event.getClick(), 100, 1000));
            case 22 -> {
                pendingSellPrice.put(player.getUniqueId(), current);
                createListing(player, current);
                ignoreNextClose.add(player.getUniqueId());
                openMain(player);
                return;
            }
            default -> {
                return;
            }
        }
        pendingSellPrice.put(player.getUniqueId(), current);
        ignoreNextClose.add(player.getUniqueId());
        openSellMenu(player);
    }

    public void handleClose(Player player) {
        if (ignoreNextClose.remove(player.getUniqueId())) {
            return;
        }
        ItemStack pending = pendingSellItem.remove(player.getUniqueId());
        if (pending == null) {
            return;
        }
        Map<Integer, ItemStack> rest = player.getInventory().addItem(pending);
        if (!rest.isEmpty()) {
            claimStorage.addClaim(player.getUniqueId(), pending, "Markt R\u00fcckgabe", 0, "Nicht eingestelltes Verkaufsitem");
        }
    }

    public void tick() {
        long now = System.currentTimeMillis();
        listings.removeIf(listing -> {
            if (listing.getExpiresAt() > now) {
                return false;
            }
            claimStorage.addClaim(listing.getSellerId(), listing.getItem(), "Markt abgelaufen", listing.getPrice(),
                    "Angebot #" + listing.getId() + " ist abgelaufen");
            return true;
        });
        save();
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("nextId", nextId.get());
        for (MarketListing listing : listings) {
            String path = "listings." + listing.getId();
            yaml.set(path + ".sellerId", listing.getSellerId().toString());
            yaml.set(path + ".item", listing.getItem());
            yaml.set(path + ".price", listing.getPrice());
            yaml.set(path + ".createdAt", listing.getCreatedAt());
            yaml.set(path + ".expiresAt", listing.getExpiresAt());
        }
        try {
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Konnte market.yml nicht speichern: " + exception.getMessage());
        }
    }

    private void load() throws IOException {
        if (!file.exists() && !file.createNewFile()) {
            throw new IOException("market.yml konnte nicht erstellt werden.");
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        nextId.set(yaml.getInt("nextId", 1));
        ConfigurationSection section = yaml.getConfigurationSection("listings");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            String path = "listings." + key;
            ItemStack item = yaml.getItemStack(path + ".item");
            String sellerId = yaml.getString(path + ".sellerId");
            if (item == null || sellerId == null) {
                continue;
            }
            listings.add(new MarketListing(Integer.parseInt(key), UUID.fromString(sellerId), item,
                    yaml.getDouble(path + ".price"), yaml.getLong(path + ".createdAt"), yaml.getLong(path + ".expiresAt")));
        }
    }

    private boolean handleItemPlacement(Player player, InventoryClickEvent event) {
        int rawSlot = event.getRawSlot();
        if (rawSlot == ITEM_SLOT) {
            ItemStack cursor = event.getCursor();
            ItemStack pending = pendingSellItem.get(player.getUniqueId());
            if (cursor != null && !cursor.getType().isAir()) {
                pendingSellItem.put(player.getUniqueId(), cursor.clone());
                event.setCursor(pending == null ? null : pending.clone());
                return true;
            }
            if (pending != null) {
                event.setCursor(pending.clone());
                pendingSellItem.remove(player.getUniqueId());
                return true;
            }
        }
        if (rawSlot >= event.getView().getTopInventory().getSize()) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked != null && !clicked.getType().isAir()) {
                ItemStack oldPending = pendingSellItem.put(player.getUniqueId(), clicked.clone());
                event.setCurrentItem(oldPending == null ? new ItemStack(Material.AIR) : oldPending);
                return true;
            }
        }
        return false;
    }

    private void createListing(Player player, double price) {
        ItemStack listed = pendingSellItem.remove(player.getUniqueId());
        if (listed == null || listed.getType().isAir()) {
            player.sendMessage("Lege zuerst ein Verkaufsitem in den vorgesehenen Slot.");
            return;
        }
        if (!priceGuideManager.isPriceAllowed(listed, price)) {
            pendingSellItem.put(player.getUniqueId(), listed);
            player.sendMessage("Preis ausserhalb des erlaubten Bereichs: " + priceGuideManager.allowedRangeText(listed));
            return;
        }
        MarketListing listing = new MarketListing(nextId.getAndIncrement(), player.getUniqueId(), listed.clone(), price,
                System.currentTimeMillis(), System.currentTimeMillis() + Duration.ofHours(24).toMillis());
        listings.add(listing);
        priceGuideManager.registerObservation(listed, price);
        save();
        player.sendMessage("Marktangebot #" + listing.getId() + " fuer " + CurrencyFormatter.shortAmount(price) + " erstellt.");
    }

    private void buyListing(Player player, int listingId) {
        MarketListing listing = listings.stream().filter(entry -> entry.getId() == listingId).findFirst().orElse(null);
        if (listing == null) {
            player.sendMessage("Angebot nicht gefunden.");
            return;
        }
        if (!economyService.withdraw(player.getUniqueId(), listing.getPrice())) {
            player.sendMessage("Nicht genug CraftTaler.");
            return;
        }
        economyService.deposit(listing.getSellerId(), listing.getPrice());
        Map<Integer, ItemStack> rest = player.getInventory().addItem(listing.getItem());
        if (!rest.isEmpty()) {
            claimStorage.addClaim(player.getUniqueId(), listing.getItem(), "Marktkauf", listing.getPrice(),
                    "Gekauft von Angebot #" + listing.getId());
        }
        listings.remove(listing);
        save();
        player.sendMessage("Angebot gekauft.");
    }

    private ItemStack createListingDisplay(MarketListing listing) {
        ItemStack display = listing.getItem().clone();
        ItemMeta meta = display.getItemMeta();
        List<String> lore = meta != null && meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add(" ");
        lore.add("\u00A77Angebot: \u00A7f#" + listing.getId());
        lore.add("\u00A77Preis: \u00A76" + CurrencyFormatter.shortAmount(listing.getPrice()));
        lore.add("\u00A7aKlick zum Kaufen");
        if (meta != null) {
            meta.setLore(lore);
            display.setItemMeta(meta);
        }
        return display;
    }

    private String priceRange(Player player) {
        ItemStack reference = currentReference(player);
        if (reference == null) {
            return "keine Daten";
        }
        List<MarketListing> matching = listings.stream()
                .filter(listing -> listing.getItem().isSimilar(reference))
                .sorted(Comparator.comparingDouble(MarketListing::getPrice))
                .toList();
        if (matching.isEmpty()) {
            return "keine Daten";
        }
        return (int) matching.get(0).getPrice() + " - " + (int) matching.get(matching.size() - 1).getPrice();
    }

    private String allowedRange(Player player) {
        ItemStack reference = currentReference(player);
        if (reference == null) {
            return "frei";
        }
        return priceGuideManager.allowedRangeText(reference);
    }

    private ItemStack currentReference(Player player) {
        ItemStack reference = pendingSellItem.get(player.getUniqueId());
        if (reference != null && !reference.getType().isAir()) {
            return reference;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        return hand == null || hand.getType().isAir() ? null : hand;
    }

    private double resolveStep(ClickType clickType, int normalStep, int shiftStep) {
        int amount = clickType.isShiftClick() ? shiftStep : normalStep;
        if (clickType.isLeftClick()) {
            return amount;
        }
        if (clickType.isRightClick()) {
            return -amount;
        }
        return 0;
    }
}


