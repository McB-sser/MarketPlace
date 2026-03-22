package de.mcbesser.marketplace.auction;

import de.mcbesser.marketplace.EconomyService;
import de.mcbesser.marketplace.MarketplacePlugin;
import de.mcbesser.marketplace.gui.GuiItems;
import de.mcbesser.marketplace.gui.MenuHolder;
import de.mcbesser.marketplace.gui.MenuType;
import de.mcbesser.marketplace.storage.ClaimStorage;
import de.mcbesser.marketplace.util.CurrencyFormatter;
import de.mcbesser.marketplace.util.GermanItemNames;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class AuctionManager {

    private static final int ITEM_SLOT = 13;
    private final MarketplacePlugin plugin;
    private final EconomyService economyService;
    private final ClaimStorage claimStorage;
    private final File file;
    private final Map<UUID, Double> setupPrice = new HashMap<>();
    private final Map<UUID, Integer> setupDuration = new HashMap<>();
    private final Map<UUID, ItemStack> pendingAuctionItem = new HashMap<>();
    private final Set<UUID> ignoreNextClose = new HashSet<>();
    private AuctionState activeAuction;

    public AuctionManager(MarketplacePlugin plugin, EconomyService economyService, ClaimStorage claimStorage) throws IOException {
        this.plugin = plugin;
        this.economyService = economyService;
        this.claimStorage = claimStorage;
        this.file = new File(plugin.getDataFolder(), "auction.yml");
        load();
    }

    public void openMain(Player player) {
        Inventory inventory = Bukkit.createInventory(new MenuHolder(MenuType.AUCTION_MAIN), 27, "Auktion");
        double startPrice = setupPrice.getOrDefault(player.getUniqueId(), 100.0D);
        int seconds = setupDuration.getOrDefault(player.getUniqueId(), 60);
        ItemStack pending = pendingAuctionItem.get(player.getUniqueId());

        inventory.setItem(10, GuiItems.button(Material.GOLD_NUGGET, "&6Preis fein",
                List.of("&7Links: +1", "&7Rechts: -1", "&7Shift+Links: +10", "&7Shift+Rechts: -10")));
        inventory.setItem(11, GuiItems.button(Material.GOLD_INGOT, "&6Preis grob",
                List.of("&7Links: +100", "&7Rechts: -100", "&7Shift+Links: +1000", "&7Shift+Rechts: -1000")));
        inventory.setItem(12, GuiItems.button(Material.CLOCK, "&eDauer: " + seconds + "s",
                List.of("&7Klick: +30 Sekunden", "&7Ab 180 Sekunden wieder auf 30")));
        inventory.setItem(ITEM_SLOT, pending == null
                ? GuiItems.button(Material.HOPPER, "&eItem hier einlegen", List.of("&7Klicke mit Item aus deinem Inventar", "&7auf diesen Slot"))
                : pending.clone());
        inventory.setItem(15, GuiItems.button(Material.EMERALD_BLOCK, "&aAuktion starten",
                List.of("&7Startpreis: " + CurrencyFormatter.shortAmount(startPrice), "&7Dauer: " + seconds + " Sekunden", "&7Gebote laufen im Chat")));
        inventory.setItem(22, activeAuctionDisplay());
        inventory.setItem(26, GuiItems.button(Material.BARREL, "&eAbholfach", List.of("&7Auktionsrueckgaben und Gewinne")));
        player.openInventory(inventory);
    }

    public void handleClick(Player player, InventoryClickEvent event) {
        if (handleItemPlacement(player, event)) {
            ignoreNextClose.add(player.getUniqueId());
            openMain(player);
            return;
        }
        int rawSlot = event.getRawSlot();
        double price = setupPrice.getOrDefault(player.getUniqueId(), 100.0D);
        int seconds = setupDuration.getOrDefault(player.getUniqueId(), 60);
        switch (rawSlot) {
            case 10 -> setupPrice.put(player.getUniqueId(), Math.max(1, price + resolveStep(event.getClick(), 1, 10)));
            case 11 -> setupPrice.put(player.getUniqueId(), Math.max(1, price + resolveStep(event.getClick(), 100, 1000)));
            case 12 -> setupDuration.put(player.getUniqueId(), seconds >= 180 ? 30 : seconds + 30);
            case 15 -> startAuction(player);
            case 26 -> {
                ignoreNextClose.add(player.getUniqueId());
                claimStorage.openClaims(player, 0);
                return;
            }
            default -> {
                return;
            }
        }
        ignoreNextClose.add(player.getUniqueId());
        openMain(player);
    }

    public void handleClose(Player player) {
        if (ignoreNextClose.remove(player.getUniqueId())) {
            return;
        }
        ItemStack pending = pendingAuctionItem.remove(player.getUniqueId());
        if (pending == null) {
            return;
        }
        Map<Integer, ItemStack> rest = player.getInventory().addItem(pending);
        if (!rest.isEmpty()) {
            claimStorage.addClaim(player.getUniqueId(), pending, "Auktion R\u00fcckgabe", 0, "Nicht gestartete Auktion");
        }
    }

    public boolean handleChatBid(Player player, String message) {
        if (activeAuction == null || !message.matches("\\d+")) {
            return false;
        }
        if (activeAuction.getSellerId().equals(player.getUniqueId())) {
            player.sendMessage("Du kannst nicht auf deine eigene Auktion bieten.");
            return true;
        }
        double bid = Double.parseDouble(message);
        if (bid <= activeAuction.getCurrentPrice()) {
            player.sendMessage("Dein Gebot muss ueber " + CurrencyFormatter.shortAmount(activeAuction.getCurrentPrice()) + " liegen.");
            return true;
        }
        if (economyService.getBalance(player.getUniqueId()) < bid) {
            player.sendMessage("Nicht genug CraftTaler fuer dieses Gebot.");
            return true;
        }
        activeAuction.setCurrentPrice(bid);
        activeAuction.setHighestBidderId(player.getUniqueId());
        activeAuction.setHighestBidderName(player.getName());
        Bukkit.broadcastMessage("[Auktion] " + player.getName() + " bietet " + CurrencyFormatter.shortAmount(bid) + " auf "
                + readableName(activeAuction.getItem()) + ".");
        save();
        return true;
    }

    public void tick() {
        if (activeAuction != null && activeAuction.getExpiresAt() <= System.currentTimeMillis()) {
            finishAuction();
        }
    }

    public List<String> sidebarLines() {
        if (activeAuction == null) {
            return List.of("\u00A78keine aktiv");
        }
        long seconds = Math.max(0, (activeAuction.getExpiresAt() - System.currentTimeMillis()) / 1000L);
        return List.of(
                "\u00A7f" + shorten(readableName(activeAuction.getItem()), 22),
                "\u00A76" + CurrencyFormatter.shortAmount(activeAuction.getCurrentPrice()),
                "\u00A77" + seconds + "s"
        );
    }

    public void shutdown() {
        if (activeAuction != null) {
            claimStorage.addClaim(activeAuction.getSellerId(), activeAuction.getItem(), "Auktion unterbrochen",
                    activeAuction.getCurrentPrice(), "Server wurde beendet");
            activeAuction = null;
        }
        for (Map.Entry<UUID, ItemStack> entry : pendingAuctionItem.entrySet()) {
            claimStorage.addClaim(entry.getKey(), entry.getValue(), "Auktion R\u00fcckgabe", 0, "Server wurde beendet");
        }
        pendingAuctionItem.clear();
        save();
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        if (activeAuction != null) {
            yaml.set("auction.sellerId", activeAuction.getSellerId().toString());
            yaml.set("auction.sellerName", activeAuction.getSellerName());
            yaml.set("auction.item", activeAuction.getItem());
            yaml.set("auction.expiresAt", activeAuction.getExpiresAt());
            yaml.set("auction.startPrice", activeAuction.getStartPrice());
            yaml.set("auction.currentPrice", activeAuction.getCurrentPrice());
            yaml.set("auction.highestBidderId",
                    activeAuction.getHighestBidderId() == null ? null : activeAuction.getHighestBidderId().toString());
            yaml.set("auction.highestBidderName", activeAuction.getHighestBidderName());
        }
        try {
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Konnte auction.yml nicht speichern: " + exception.getMessage());
        }
    }

    private void load() throws IOException {
        if (!file.exists() && !file.createNewFile()) {
            throw new IOException("auction.yml konnte nicht erstellt werden.");
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        if (!yaml.contains("auction.item")) {
            return;
        }
        ItemStack item = yaml.getItemStack("auction.item");
        String sellerId = yaml.getString("auction.sellerId");
        String sellerName = yaml.getString("auction.sellerName");
        if (item == null || sellerId == null || sellerName == null) {
            return;
        }
        activeAuction = new AuctionState(UUID.fromString(sellerId), sellerName, item, yaml.getLong("auction.expiresAt"),
                yaml.getDouble("auction.startPrice"));
        activeAuction.setCurrentPrice(yaml.getDouble("auction.currentPrice", activeAuction.getStartPrice()));
        String bidderId = yaml.getString("auction.highestBidderId");
        if (bidderId != null) {
            activeAuction.setHighestBidderId(UUID.fromString(bidderId));
        }
        activeAuction.setHighestBidderName(yaml.getString("auction.highestBidderName"));
    }

    private boolean handleItemPlacement(Player player, InventoryClickEvent event) {
        int rawSlot = event.getRawSlot();
        if (rawSlot == ITEM_SLOT) {
            ItemStack cursor = event.getCursor();
            ItemStack pending = pendingAuctionItem.get(player.getUniqueId());
            if (cursor != null && !cursor.getType().isAir()) {
                pendingAuctionItem.put(player.getUniqueId(), cursor.clone());
                event.setCursor(pending == null ? null : pending.clone());
                return true;
            }
            if (pending != null) {
                event.setCursor(pending.clone());
                pendingAuctionItem.remove(player.getUniqueId());
                return true;
            }
        }
        if (rawSlot >= event.getView().getTopInventory().getSize()) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked != null && !clicked.getType().isAir()) {
                ItemStack oldPending = pendingAuctionItem.put(player.getUniqueId(), clicked.clone());
                event.setCurrentItem(oldPending == null ? new ItemStack(Material.AIR) : oldPending);
                return true;
            }
        }
        return false;
    }

    private void startAuction(Player player) {
        if (activeAuction != null) {
            player.sendMessage("Es laeuft bereits eine Auktion.");
            return;
        }
        ItemStack pending = pendingAuctionItem.remove(player.getUniqueId());
        if (pending == null || pending.getType().isAir()) {
            player.sendMessage("Lege zuerst ein Auktionsitem in den vorgesehenen Slot.");
            return;
        }
        double startPrice = setupPrice.getOrDefault(player.getUniqueId(), 100.0D);
        int seconds = setupDuration.getOrDefault(player.getUniqueId(), 60);
        activeAuction = new AuctionState(player.getUniqueId(), player.getName(), pending.clone(),
                System.currentTimeMillis() + Duration.ofSeconds(seconds).toMillis(), startPrice);
        Bukkit.broadcastMessage("[Auktion] " + player.getName() + " versteigert " + readableName(pending)
                + " ab " + CurrencyFormatter.shortAmount(startPrice) + ". Gebote einfach als Zahl in den Chat schreiben.");
        save();
    }

    private void finishAuction() {
        AuctionState auction = activeAuction;
        activeAuction = null;
        if (auction.getHighestBidderId() == null) {
            claimStorage.addClaim(auction.getSellerId(), auction.getItem(), "Auktion unverkauft",
                    auction.getCurrentPrice(), "Nicht ersteigert");
            Bukkit.broadcastMessage("[Auktion] Kein Gebot. Item geht ins Abholfach von " + auction.getSellerName() + ".");
            save();
            return;
        }
        if (!economyService.withdraw(auction.getHighestBidderId(), auction.getCurrentPrice())) {
            claimStorage.addClaim(auction.getSellerId(), auction.getItem(), "Auktion fehlgeschlagen",
                    auction.getCurrentPrice(), "Hoechstbietender hatte nicht genug CraftTaler");
            Bukkit.broadcastMessage("[Auktion] Gebot konnte nicht eingezogen werden. Item geht zurueck an "
                    + auction.getSellerName() + ".");
            save();
            return;
        }
        economyService.deposit(auction.getSellerId(), auction.getCurrentPrice());
        Player winner = Bukkit.getPlayer(auction.getHighestBidderId());
        if (winner == null || !winner.isOnline()) {
            claimStorage.addClaim(auction.getHighestBidderId(), auction.getItem(), "Auktion gewonnen",
                    auction.getCurrentPrice(), "Gewonnen von " + auction.getSellerName());
        } else {
            Map<Integer, ItemStack> rest = winner.getInventory().addItem(auction.getItem());
            if (!rest.isEmpty()) {
                claimStorage.addClaim(winner.getUniqueId(), auction.getItem(), "Auktion gewonnen",
                        auction.getCurrentPrice(), "Inventar war voll");
            }
        }
        Bukkit.broadcastMessage("[Auktion] " + auction.getHighestBidderName() + " gewinnt f\u00fcr "
                + CurrencyFormatter.shortAmount(auction.getCurrentPrice()) + ".");
        save();
    }

    private ItemStack activeAuctionDisplay() {
        if (activeAuction == null) {
            return GuiItems.button(Material.ANVIL, "&7Keine aktive Auktion", List.of("&7Lege ein Item in Slot 13 und starte eine Auktion"));
        }
        ItemStack display = activeAuction.getItem().clone();
        ItemMeta meta = display.getItemMeta();
        List<String> lore = meta != null && meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add(" ");
        lore.add("\u00A77Verk\u00e4ufer: \u00A7f" + activeAuction.getSellerName());
        lore.add("\u00A77Aktuell: \u00A76" + CurrencyFormatter.shortAmount(activeAuction.getCurrentPrice()));
        lore.add("\u00A77H\u00f6chstbietender: \u00A7f" + (activeAuction.getHighestBidderName() == null ? "-" : activeAuction.getHighestBidderName()));
        lore.add("\u00A77Im Chat als Zahl bieten");
        if (meta != null) {
            meta.setLore(lore);
            display.setItemMeta(meta);
        }
        return display;
    }

    private String readableName(ItemStack item) {
        return GermanItemNames.of(item);
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

    private String shorten(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }
}


