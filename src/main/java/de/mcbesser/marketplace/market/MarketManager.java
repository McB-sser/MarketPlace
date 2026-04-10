package de.mcbesser.marketplace.market;

import de.mcbesser.marketplace.EconomyService;
import de.mcbesser.marketplace.MarketplacePlugin;
import de.mcbesser.marketplace.gui.GuiItems;
import de.mcbesser.marketplace.gui.MenuHolder;
import de.mcbesser.marketplace.gui.MenuType;
import de.mcbesser.marketplace.pricing.PriceGuideManager;
import de.mcbesser.marketplace.storage.ClaimStorage;
import de.mcbesser.marketplace.util.CurrencyFormatter;
import de.mcbesser.marketplace.util.MessageUtil;
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

    private static final int[] SELL_ITEM_SLOTS = {
            0, 1, 2, 3, 4, 5, 6, 7, 8,
            9, 10, 11, 12, 13, 14, 15, 16, 17,
            18, 19, 20, 21, 22, 23, 24, 25, 26,
            27, 28, 29, 30, 31, 32, 33, 34, 35
    };
    private static final long[] EXPIRY_OPTIONS = {
            0L,
            Duration.ofHours(1).toMillis(),
            Duration.ofHours(6).toMillis(),
            Duration.ofHours(12).toMillis(),
            Duration.ofDays(1).toMillis(),
            Duration.ofDays(3).toMillis(),
            Duration.ofDays(7).toMillis()
    };

    private final MarketplacePlugin plugin;
    private final EconomyService economyService;
    private final ClaimStorage claimStorage;
    private final PriceGuideManager priceGuideManager;
    private final File file;
    private final List<MarketListing> listings = new ArrayList<>();
    private final AtomicInteger nextId = new AtomicInteger(1);
    private final Map<UUID, PendingMarketSale> pendingSales = new HashMap<>();
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
        inventory.setItem(12, GuiItems.button(Material.WRITABLE_BOOK, "&eMeine Angebote",
                List.of("&7Eigene Marktangebote ansehen", "&7und bei Bedarf abbrechen")));
        inventory.setItem(13, GuiItems.button(Material.EMERALD, "&6Preis f\u00fcr Item in Hand: " + priceRange(player),
                List.of("&7Aktueller Marktbereich", "&7f\u00fcr das Item in deiner Hand")));
        inventory.setItem(15, GuiItems.button(Material.CHEST, "&bVerkaufen",
                List.of("&7Mehrere gleiche Items einlegen", "&7Preisart und Laufzeit w\u00e4hlen")));
        inventory.setItem(18, GuiItems.button(Material.COMPASS, "&aMarktplatz", List.of("&7Zur\u00fcck zum Hauptmen\u00fc")));
        inventory.setItem(22, GuiItems.button(Material.BARREL, "&eAbholfach",
                List.of("&7Abgelaufene oder ausgelagerte Items")));
        player.openInventory(inventory);
    }

    public void openListingPage(Player player, int page) {
        Inventory inventory = Bukkit.createInventory(new MenuHolder(MenuType.MARKET_LIST, page, ""), 54, "Marktangebote");
        List<MarketListing> sorted = listings.stream()
                .sorted(Comparator.comparingDouble(MarketListing::getDisplayPrice))
                .toList();
        int start = page * 45;
        for (int slot = 0; slot < 45; slot++) {
            int index = start + slot;
            if (index >= sorted.size()) {
                break;
            }
            inventory.setItem(slot, createListingDisplay(player, sorted.get(index)));
        }
        inventory.setItem(45, GuiItems.button(Material.COMPASS, "&aMarktplatz", List.of("&7Zum Hauptmen\u00fc")));
        inventory.setItem(46, GuiItems.button(Material.ARROW, "&eZur\u00fcck", List.of("&7Vorherige Seite")));
        inventory.setItem(49, GuiItems.button(Material.COMPASS, "&aHauptmen\u00fc", List.of("&7Zur Markt\u00fcbersicht")));
        inventory.setItem(53, GuiItems.button(Material.ARROW, "&eWeiter", List.of("&7N\u00e4chste Seite")));
        player.openInventory(inventory);
    }

    public void openOwnListingPage(Player player, int page) {
        Inventory inventory = Bukkit.createInventory(new MenuHolder(MenuType.MARKET_OWN, page, ""), 54, "Meine Angebote");
        List<MarketListing> ownListings = ownListings(player.getUniqueId());
        int start = page * 45;
        for (int slot = 0; slot < 45; slot++) {
            int index = start + slot;
            if (index >= ownListings.size()) {
                break;
            }
            inventory.setItem(slot, createOwnListingDisplay(ownListings.get(index)));
        }
        inventory.setItem(45, GuiItems.button(Material.COMPASS, "&aMarktplatz", List.of("&7Zum Hauptmen\u00fc")));
        inventory.setItem(46, GuiItems.button(Material.ARROW, "&eZur\u00fcck", List.of("&7Vorherige Seite")));
        inventory.setItem(49, GuiItems.button(Material.BOOK, "&aMarktangebote", List.of("&7Zur allgemeinen Angebotsliste")));
        inventory.setItem(53, GuiItems.button(Material.ARROW, "&eWeiter", List.of("&7N\u00e4chste Seite")));
        player.openInventory(inventory);
    }

    public void openSellMenu(Player player) {
        PendingMarketSale sale = pendingSale(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(new MenuHolder(MenuType.MARKET_SELL), 54, "Marktpreis setzen");
        for (int i = 0; i < Math.min(SELL_ITEM_SLOTS.length, sale.getItems().size()); i++) {
            inventory.setItem(SELL_ITEM_SLOTS[i], sale.getItems().get(i).clone());
        }
        fillSellDecorations(inventory);
        inventory.setItem(36, GuiItems.button(Material.PAPER, "&eVerkauf",
                List.of("&7Items: " + totalAmount(sale) + " St\u00fcck",
                        "&7Preisart: " + sale.getPriceMode().getLabel(),
                        "&7Laufzeit: " + expiryLabel(sale.getExpiryDuration()))));
        inventory.setItem(37, GuiItems.button(Material.GOLD_NUGGET, "&6Preis fein",
                List.of("&7Links: +1", "&7Rechts: -1", "&7Shift+Links: +10", "&7Shift+Rechts: -10")));
        inventory.setItem(38, GuiItems.button(Material.GOLD_INGOT, "&6Preis grob",
                List.of("&7Links: +100", "&7Rechts: -100", "&7Shift+Links: +1000", "&7Shift+Rechts: -1000")));
        inventory.setItem(39, GuiItems.button(Material.COMPARATOR, "&ePreisart: " + sale.getPriceMode().getLabel(),
                List.of("&7Klick wechselt zwischen",
                        "&7Gesamtpreis, Stackpreis, Einzelpreis")));
        inventory.setItem(40, GuiItems.button(Material.CLOCK, "&eLaufzeit: " + expiryLabel(sale.getExpiryDuration()),
                List.of("&7Links: n\u00e4chste Laufzeit",
                        "&7Rechts: vorige Laufzeit",
                        "&7Standard ist da\u00fcrhaft")));
        inventory.setItem(41, GuiItems.button(Material.SUNFLOWER, "&6Preisvorschau", buildPricePreview(player, sale)));
        inventory.setItem(45, GuiItems.button(Material.COMPASS, "&aMarktplatz", List.of("&7Zum Hauptmen\u00fc")));
        inventory.setItem(46, GuiItems.button(Material.BOOK, "&aMarktangebote", List.of("&7Zur allgemeinen Angebotsliste")));
        inventory.setItem(47, GuiItems.button(Material.WRITABLE_BOOK, "&eMeine Angebote", List.of("&7Eigene Angebote anzeigen")));
        inventory.setItem(48, GuiItems.button(Material.BARREL, "&eAbholfach", List.of("&7Abgelaufene oder ausgelagerte Items")));
        inventory.setItem(49, GuiItems.button(Material.EMERALD_BLOCK, sale.getPrice() == null ? "&6Preis fehlt" : "&aAngebot erstellen",
                List.of("&7Preis: " + priceLabel(sale),
                        "&7Verkauf: " + sale.getPriceMode().getLabel(),
                        sale.getPrice() == null ? "&cStelle zuerst einen Preis ein" : "&aKlick zum Einstellen")));
        inventory.setItem(50, GuiItems.button(Material.BARRIER, "&cZur\u00fccksetzen", List.of("&7Eingelegte Items gehen zur\u00fcck")));
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
        if (rawSlot == 18) {
            player.performCommand("marketplace");
        } else if (rawSlot == 11) {
            ignoreNextClose.add(player.getUniqueId());
            openListingPage(player, 0);
        } else if (rawSlot == 12) {
            ignoreNextClose.add(player.getUniqueId());
            openOwnListingPage(player, 0);
        } else if (rawSlot == 15) {
            ignoreNextClose.add(player.getUniqueId());
            openSellMenu(player);
        } else if (rawSlot == 22) {
            ignoreNextClose.add(player.getUniqueId());
            claimStorage.openClaims(player, 0, ClaimStorage.CONTEXT_MARKET);
        }
    }

    public void handleListingClick(Player player, int rawSlot, int page) {
        List<MarketListing> sorted = listings.stream().sorted(Comparator.comparingDouble(MarketListing::getDisplayPrice)).toList();
        if (rawSlot == 45) {
            player.performCommand("marketplace");
            return;
        }
        if (rawSlot == 46 && page > 0) {
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
        MarketListing listing = sorted.get(index);
        if (listing.getSellerId().equals(player.getUniqueId())) {
            cancelOwnListing(player, listing);
        } else {
            buyListing(player, listing.getId());
        }
        ignoreNextClose.add(player.getUniqueId());
        openListingPage(player, page);
    }

    public void handleOwnListingClick(Player player, int rawSlot, int page) {
        List<MarketListing> ownListings = ownListings(player.getUniqueId());
        if (rawSlot == 45) {
            player.performCommand("marketplace");
            return;
        }
        if (rawSlot == 46 && page > 0) {
            ignoreNextClose.add(player.getUniqueId());
            openOwnListingPage(player, page - 1);
            return;
        }
        if (rawSlot == 49) {
            ignoreNextClose.add(player.getUniqueId());
            openListingPage(player, 0);
            return;
        }
        if (rawSlot == 53 && ((page + 1) * 45) < ownListings.size()) {
            ignoreNextClose.add(player.getUniqueId());
            openOwnListingPage(player, page + 1);
            return;
        }
        if (rawSlot < 0 || rawSlot >= 45) {
            return;
        }
        int index = page * 45 + rawSlot;
        if (index >= ownListings.size()) {
            return;
        }
        cancelOwnListing(player, ownListings.get(index));
        ignoreNextClose.add(player.getUniqueId());
        openOwnListingPage(player, page);
    }

    public void handleSellClick(Player player, InventoryClickEvent event) {
        PendingMarketSale sale = pendingSale(player.getUniqueId());
        int rawSlot = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();

        if (rawSlot < topSize && isSellItemSlot(rawSlot)) {
            if (!isAllowedSellPlacement(event.getView().getTopInventory(), rawSlot, event.getCursor())) {
                event.setCancelled(true);
                MessageUtil.send(player, "Du kannst hier nur exakt gleiche Items ablegen.");
                return;
            }
            event.setCancelled(false);
            scheduleSellSync(player);
            return;
        }
        if (rawSlot >= topSize) {
            if (event.getClick().isShiftClick() && !isAllowedShiftInsert(event.getView().getTopInventory(), event.getCurrentItem())) {
                event.setCancelled(true);
                MessageUtil.send(player, "Du kannst hier nur exakt gleiche Items ablegen.");
                return;
            }
            event.setCancelled(false);
            scheduleSellSync(player);
            return;
        }

        event.setCancelled(true);
        syncSaleFromView(sale, event.getView().getTopInventory());
        Double currentValue = sale.getPrice();
        double current = currentValue == null ? 0.0D : currentValue;
        switch (rawSlot) {
            case 37 -> current = Math.max(1, current + resolveStep(event.getClick(), 1, 10));
            case 38 -> current = Math.max(1, current + resolveStep(event.getClick(), 100, 1000));
            case 39 -> sale.setPriceMode(sale.getPriceMode().next());
            case 40 -> sale.setExpiryDuration(cycleExpiry(sale.getExpiryDuration(), event.getClick().isRightClick() ? -1 : 1));
            case 45 -> {
                ignoreNextClose.add(player.getUniqueId());
                player.performCommand("marketplace");
                return;
            }
            case 46 -> {
                ignoreNextClose.add(player.getUniqueId());
                openListingPage(player, 0);
                return;
            }
            case 47 -> {
                ignoreNextClose.add(player.getUniqueId());
                openOwnListingPage(player, 0);
                return;
            }
            case 48 -> {
                ignoreNextClose.add(player.getUniqueId());
                claimStorage.openClaims(player, 0, ClaimStorage.CONTEXT_MARKET);
                return;
            }
            case 49 -> {
                if (sale.getItems().isEmpty()) {
                    MessageUtil.send(player, "Lege zuerst gleiche Verkaufsitems in die oberen Reihen.");
                    ignoreNextClose.add(player.getUniqueId());
                    openSellMenu(player);
                    return;
                }
                if (currentValue == null) {
                    MessageUtil.send(player, "Stelle zuerst einen Preis f\u00fcr dein Angebot ein.");
                    ignoreNextClose.add(player.getUniqueId());
                    openSellMenu(player);
                    return;
                }
                sale.setPrice(current);
                createListing(player, sale);
                ignoreNextClose.add(player.getUniqueId());
                openMain(player);
                return;
            }
            case 50 -> {
                clearPendingSale(player);
                ignoreNextClose.add(player.getUniqueId());
                openSellMenu(player);
                return;
            }
            default -> {
                return;
            }
        }
        sale.setPrice(current);
        ignoreNextClose.add(player.getUniqueId());
        openSellMenu(player);
    }

    public void handleClose(Player player) {
        if (ignoreNextClose.remove(player.getUniqueId())) {
            return;
        }
        returnPendingItems(player);
    }

    public void tick() {
        long now = System.currentTimeMillis();
        boolean changed = listings.removeIf(listing -> {
            if (listing.getExpiresAt() <= 0 || listing.getExpiresAt() > now) {
                return false;
            }
            for (ItemStack item : splitRemainingItems(listing)) {
                claimStorage.addClaim(listing.getSellerId(), item, "Markt abgelaufen", listing.getDisplayPrice(),
                        "Angebot #" + listing.getId() + " ist abgelaufen");
            }
            return true;
        });
        if (changed) {
            save();
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("nextId", nextId.get());
        for (MarketListing listing : listings) {
            String path = "listings." + listing.getId();
            yaml.set(path + ".sellerId", listing.getSellerId().toString());
            yaml.set(path + ".item", listing.getPrototype());
            yaml.set(path + ".unitPrice", listing.getUnitPrice());
            yaml.set(path + ".remainingAmount", listing.getRemainingAmount());
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
            double unitPrice = yaml.contains(path + ".unitPrice")
                    ? yaml.getDouble(path + ".unitPrice")
                    : yaml.getDouble(path + ".price") / Math.max(1, item.getAmount());
            int remainingAmount = yaml.getInt(path + ".remainingAmount", Math.max(1, item.getAmount()));
            ItemStack prototype = item.clone();
            prototype.setAmount(1);
            listings.add(new MarketListing(Integer.parseInt(key), UUID.fromString(sellerId), prototype,
                    unitPrice, remainingAmount, yaml.getLong(path + ".createdAt"), yaml.getLong(path + ".expiresAt")));
        }
    }

    private void createListing(Player player, PendingMarketSale sale) {
        if (sale.getItems().isEmpty()) {
            MessageUtil.send(player, "Lege zuerst gleiche Verkaufsitems in die oberen Reihen.");
            return;
        }
        if (sale.getPrice() == null) {
            MessageUtil.send(player, "Stelle zuerst einen Preis f\u00fcr dein Angebot ein.");
            return;
        }
        ItemStack prototype = normalizePrototype(sale.getItems().get(0));
        int totalAmount = totalAmount(sale);
        double unitPrice = resolveUnitPrice(sale.getPrice(), sale.getPriceMode(), prototype, totalAmount);
        if (!priceGuideManager.isPriceAllowed(prototype, unitPrice)) {
            MessageUtil.send(player, "Preis ausserhalb des erlaubten Bereichs: " + priceGuideManager.allowedRangeText(prototype));
            return;
        }
        MarketListing listing = new MarketListing(
                nextId.getAndIncrement(),
                player.getUniqueId(),
                prototype,
                unitPrice,
                totalAmount,
                System.currentTimeMillis(),
                sale.getExpiryDuration() <= 0 ? 0L : System.currentTimeMillis() + sale.getExpiryDuration()
        );
        listings.add(listing);
        priceGuideManager.registerObservation(prototype, unitPrice);
        pendingSales.remove(player.getUniqueId());
        save();
        MessageUtil.send(player, "Marktangebot #" + listing.getId() + " erstellt.");
    }

    private void cancelOwnListing(Player player, MarketListing listing) {
        listings.remove(listing);
        for (ItemStack item : splitRemainingItems(listing)) {
            Map<Integer, ItemStack> rest = player.getInventory().addItem(item);
            if (!rest.isEmpty()) {
                claimStorage.addClaim(player.getUniqueId(), item, "Marktangebot abgebrochen", listing.getDisplayPrice(),
                        "Abgebrochenes Angebot #" + listing.getId());
            }
        }
        save();
        MessageUtil.send(player, "Angebot #" + listing.getId() + " abgebrochen.");
    }

    private void buyListing(Player player, int listingId) {
        MarketListing listing = listings.stream().filter(entry -> entry.getId() == listingId).findFirst().orElse(null);
        if (listing == null) {
            MessageUtil.send(player, "Angebot nicht gefunden.");
            return;
        }
        double price = listing.getDisplayPrice();
        if (!economyService.withdraw(player.getUniqueId(), price)) {
            MessageUtil.send(player, "Nicht genug CraftTaler.");
            return;
        }
        economyService.deposit(listing.getSellerId(), price);
        ItemStack bought = listing.removeDisplayAmount();
        Map<Integer, ItemStack> rest = player.getInventory().addItem(bought);
        if (!rest.isEmpty()) {
            claimStorage.addClaim(player.getUniqueId(), bought, "Marktkauf", price,
                    "Gekauft von Angebot #" + listing.getId());
        }
        if (listing.isEmpty()) {
            listings.remove(listing);
        }
        save();
        MessageUtil.send(player, "Angebot gekauft.");
    }

    private ItemStack createListingDisplay(Player viewer, MarketListing listing) {
        ItemStack display = listing.createDisplayItem();
        ItemMeta meta = display.getItemMeta();
        List<String> lore = meta != null && meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add(" ");
        lore.add("\u00A77Angebot: \u00A7f#" + listing.getId());
        lore.add("\u00A77Preis jetzt: \u00A76" + CurrencyFormatter.shortAmount(listing.getDisplayPrice()));
        lore.add("\u00A77Einzelpreis: \u00A76" + CurrencyFormatter.shortAmount(listing.getUnitPrice()));
        lore.add("\u00A77Verbleibend: \u00A7f" + listing.getRemainingAmount());
        lore.add("\u00A77Laufzeit: " + listingExpiryLabel(listing));
        if (listing.getSellerId().equals(viewer.getUniqueId())) {
            lore.add("\u00A7eEigenes Angebot");
            lore.add("\u00A7cKlick zum Abbrechen");
        } else {
            lore.add("\u00A7aKlick kauft die sichtbare Menge");
        }
        if (meta != null) {
            meta.setLore(lore);
            display.setItemMeta(meta);
        }
        return display;
    }

    private ItemStack createOwnListingDisplay(MarketListing listing) {
        ItemStack display = listing.createDisplayItem();
        ItemMeta meta = display.getItemMeta();
        List<String> lore = meta != null && meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add(" ");
        lore.add("\u00A77Angebot: \u00A7f#" + listing.getId());
        lore.add("\u00A77Preis jetzt: \u00A76" + CurrencyFormatter.shortAmount(listing.getDisplayPrice()));
        lore.add("\u00A77Einzelpreis: \u00A76" + CurrencyFormatter.shortAmount(listing.getUnitPrice()));
        lore.add("\u00A77Verbleibend: \u00A7f" + listing.getRemainingAmount());
        lore.add("\u00A77Laufzeit: " + listingExpiryLabel(listing));
        lore.add("\u00A7cKlick zum Abbrechen");
        if (meta != null) {
            meta.setLore(lore);
            display.setItemMeta(meta);
        }
        return display;
    }

    private PendingMarketSale pendingSale(UUID playerId) {
        return pendingSales.computeIfAbsent(playerId, ignored -> new PendingMarketSale());
    }

    private void clearPendingSale(Player player) {
        returnPendingItems(player);
        pendingSales.remove(player.getUniqueId());
    }

    private void returnPendingItems(Player player) {
        PendingMarketSale sale = pendingSales.get(player.getUniqueId());
        if (sale == null || sale.getItems().isEmpty()) {
            return;
        }
        for (ItemStack item : sale.getItems()) {
            Map<Integer, ItemStack> rest = player.getInventory().addItem(item);
            if (!rest.isEmpty()) {
                claimStorage.addClaim(player.getUniqueId(), item, "Markt R\u00fcckgabe", 0, "Nicht eingestelltes Verkaufsitem");
            }
        }
        sale.getItems().clear();
        sale.setPrice(null);
        sale.setExpiryDuration(0L);
        sale.setPriceMode(MarketPriceMode.TOTAL);
    }

    private void scheduleSellSync(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder holder)
                    || holder.getType() != MenuType.MARKET_SELL) {
                return;
            }
            syncSaleFromView(pendingSale(player.getUniqueId()), player.getOpenInventory().getTopInventory());
        });
    }

    private void syncSaleFromView(PendingMarketSale sale, Inventory inventory) {
        List<ItemStack> updated = new ArrayList<>();
        ItemStack reference = null;
        for (int slot : SELL_ITEM_SLOTS) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            if (reference == null) {
                reference = normalizePrototype(item);
            }
            if (!reference.isSimilar(normalizePrototype(item))) {
                continue;
            }
            updated.add(item.clone());
        }
        sale.getItems().clear();
        sale.getItems().addAll(updated);
    }

    private boolean isAllowedSellPlacement(Inventory inventory, int rawSlot, ItemStack cursor) {
        if (cursor == null || cursor.getType().isAir()) {
            return true;
        }
        ItemStack reference = sellReference(inventory, rawSlot);
        return reference == null || reference.isSimilar(normalizePrototype(cursor));
    }

    private boolean isAllowedShiftInsert(Inventory inventory, ItemStack currentItem) {
        if (currentItem == null || currentItem.getType().isAir()) {
            return true;
        }
        ItemStack reference = sellReference(inventory, -1);
        return reference == null || reference.isSimilar(normalizePrototype(currentItem));
    }

    private ItemStack sellReference(Inventory inventory, int ignoredSlot) {
        for (int slot : SELL_ITEM_SLOTS) {
            if (slot == ignoredSlot) {
                continue;
            }
            ItemStack item = inventory.getItem(slot);
            if (item != null && !item.getType().isAir()) {
                return normalizePrototype(item);
            }
        }
        return null;
    }

    private ItemStack normalizePrototype(ItemStack item) {
        ItemStack normalized = item.clone();
        normalized.setAmount(1);
        return normalized;
    }

    private List<String> buildPricePreview(Player player, PendingMarketSale sale) {
        List<String> lore = new ArrayList<>();
        int totalAmount = totalAmount(sale);
        if (totalAmount <= 0) {
            lore.add("&7Lege zuerst gleiche Items ein");
            return lore;
        }
        int stackSize = Math.max(1, normalizePrototype(sale.getItems().get(0)).getMaxStackSize());
        int currentOfferAmount = Math.min(totalAmount, stackSize);
        lore.add("&7Gesamtmenge: " + totalAmount);
        lore.add("&7Sichtbare Kaufmenge: " + currentOfferAmount);
        lore.add("&7Marktbereich: " + allowedRange(player));
        if (sale.getPrice() == null) {
            lore.add("&cNoch kein Preis gesetzt");
            return lore;
        }
        double unitPrice = resolveUnitPrice(sale.getPrice(), sale.getPriceMode(), normalizePrototype(sale.getItems().get(0)), totalAmount);
        lore.add("&7Eingabe: " + CurrencyFormatter.shortAmount(sale.getPrice()));
        lore.add("&7Einzelpreis: " + CurrencyFormatter.shortAmount(unitPrice));
        lore.add("&7Kaufpreis jetzt: " + CurrencyFormatter.shortAmount(unitPrice * currentOfferAmount));
        return lore;
    }

    private double resolveUnitPrice(double configuredPrice, MarketPriceMode priceMode, ItemStack prototype, int totalAmount) {
        int stackSize = Math.max(1, prototype.getMaxStackSize());
        return switch (priceMode) {
            case TOTAL -> configuredPrice / Math.max(1, totalAmount);
            case STACK -> configuredPrice / stackSize;
            case SINGLE -> configuredPrice;
        };
    }

    private List<MarketListing> ownListings(UUID playerId) {
        return listings.stream()
                .filter(listing -> listing.getSellerId().equals(playerId))
                .sorted(Comparator.comparingLong(MarketListing::getCreatedAt).reversed())
                .toList();
    }

    private void fillSellDecorations(Inventory inventory) {
        ItemStack glass = GuiItems.button(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 36; slot <= 44; slot++) {
            inventory.setItem(slot, glass);
        }
        for (int slot = 45; slot <= 53; slot++) {
            inventory.setItem(slot, glass);
        }
    }

    private boolean isSellItemSlot(int slot) {
        for (int candidate : SELL_ITEM_SLOTS) {
            if (candidate == slot) {
                return true;
            }
        }
        return false;
    }

    private int totalAmount(PendingMarketSale sale) {
        return sale.getItems().stream().mapToInt(ItemStack::getAmount).sum();
    }

    private List<ItemStack> splitRemainingItems(MarketListing listing) {
        List<ItemStack> items = new ArrayList<>();
        int amount = listing.getRemainingAmount();
        int stackSize = Math.max(1, listing.getPrototype().getMaxStackSize());
        while (amount > 0) {
            int next = Math.min(amount, stackSize);
            ItemStack item = listing.getPrototype().clone();
            item.setAmount(next);
            items.add(item);
            amount -= next;
        }
        return items;
    }

    private String priceRange(Player player) {
        ItemStack reference = currentReference(player);
        if (reference == null) {
            return "keine Daten";
        }
        List<MarketListing> matching = listings.stream()
                .filter(listing -> listing.getPrototype().isSimilar(normalizePrototype(reference)))
                .sorted(Comparator.comparingDouble(MarketListing::getUnitPrice))
                .toList();
        if (matching.isEmpty()) {
            return "keine Daten";
        }
        return CurrencyFormatter.shortAmount(matching.get(0).getUnitPrice()) + " - "
                + CurrencyFormatter.shortAmount(matching.get(matching.size() - 1).getUnitPrice());
    }

    private String allowedRange(Player player) {
        ItemStack reference = currentReference(player);
        if (reference == null) {
            return "frei";
        }
        return priceGuideManager.allowedRangeText(normalizePrototype(reference));
    }

    private ItemStack currentReference(Player player) {
        PendingMarketSale sale = pendingSales.get(player.getUniqueId());
        if (sale != null && !sale.getItems().isEmpty()) {
            return sale.getItems().get(0);
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

    private long cycleExpiry(long current, int direction) {
        int index = 0;
        for (int i = 0; i < EXPIRY_OPTIONS.length; i++) {
            if (EXPIRY_OPTIONS[i] == current) {
                index = i;
                break;
            }
        }
        return EXPIRY_OPTIONS[Math.floorMod(index + direction, EXPIRY_OPTIONS.length)];
    }

    private String priceLabel(PendingMarketSale sale) {
        return sale.getPrice() == null ? "-" : CurrencyFormatter.shortAmount(sale.getPrice());
    }

    private String expiryLabel(long expiryDuration) {
        if (expiryDuration <= 0) {
            return "dauerhaft";
        }
        return formatDuration(expiryDuration);
    }

    private String listingExpiryLabel(MarketListing listing) {
        if (listing.getExpiresAt() <= 0) {
            return "dauerhaft";
        }
        long remaining = Math.max(0L, listing.getExpiresAt() - System.currentTimeMillis());
        return formatDuration(remaining);
    }

    private String formatDuration(long durationMillis) {
        Duration duration = Duration.ofMillis(durationMillis);
        long days = duration.toDays();
        if (days > 0) {
            return days + " Tag" + (days == 1 ? "" : "e");
        }
        long hours = duration.toHours();
        if (hours > 0) {
            return hours + " Stunde" + (hours == 1 ? "" : "n");
        }
        long minutes = Math.max(1L, duration.toMinutes());
        return minutes + " Minute" + (minutes == 1 ? "" : "n");
    }
}
