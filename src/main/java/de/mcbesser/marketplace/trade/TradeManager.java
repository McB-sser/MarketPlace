package de.mcbesser.marketplace.trade;

import de.mcbesser.marketplace.EconomyService;
import de.mcbesser.marketplace.MarketplacePlugin;
import de.mcbesser.marketplace.gui.GuiItems;
import de.mcbesser.marketplace.gui.MenuHolder;
import de.mcbesser.marketplace.gui.MenuType;
import de.mcbesser.marketplace.storage.ClaimStorage;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class TradeManager {

    private final MarketplacePlugin plugin;
    private final EconomyService economyService;
    private final ClaimStorage claimStorage;
    private final File file;
    private final Map<UUID, UUID> pendingRequests = new HashMap<>();
    private final Map<UUID, TradeSession> sessions = new HashMap<>();

    public TradeManager(MarketplacePlugin plugin, EconomyService economyService, ClaimStorage claimStorage) throws IOException {
        this.plugin = plugin;
        this.economyService = economyService;
        this.claimStorage = claimStorage;
        this.file = new File(plugin.getDataFolder(), "trade.yml");
        if (!file.exists() && !file.createNewFile()) {
            throw new IOException("trade.yml konnte nicht erstellt werden.");
        }
    }

    public void openPlayerList(Player player) {
        Inventory inventory = Bukkit.createInventory(new MenuHolder(MenuType.TRADE_PLAYERS), 54, "Handelspartner");
        List<? extends Player> targets = Bukkit.getOnlinePlayers().stream()
                .filter(other -> !other.getUniqueId().equals(player.getUniqueId()))
                .sorted(Comparator.comparing(Player::getName))
                .toList();
        for (int slot = 0; slot < Math.min(45, targets.size()); slot++) {
            Player target = targets.get(slot);
            inventory.setItem(slot, GuiItems.button(Material.PLAYER_HEAD, "\u00A7a" + target.getName(),
                    List.of("\u00A77Klick f\u00fcr Handelsanfrage")));
        }
        inventory.setItem(49, GuiItems.button(Material.PAPER, "\u00A7eOffene Anfrage annehmen", List.of("\u00A77Nimmt deine letzte Anfrage an")));
        player.openInventory(inventory);
    }

    public void handlePlayerListClick(Player player, int rawSlot) {
        List<? extends Player> targets = Bukkit.getOnlinePlayers().stream()
                .filter(other -> !other.getUniqueId().equals(player.getUniqueId()))
                .sorted(Comparator.comparing(Player::getName))
                .toList();
        if (rawSlot == 49) {
            acceptRequest(player);
            return;
        }
        if (rawSlot < 0 || rawSlot >= targets.size() || rawSlot >= 45) {
            return;
        }
        sendRequest(player, targets.get(rawSlot));
    }

    public void openTradeView(Player player) {
        TradeSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            player.sendMessage("Kein aktiver Handel.");
            return;
        }
        boolean first = session.getFirstPlayer().equals(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(new MenuHolder(MenuType.TRADE_SESSION), 54, "Direkthandel");
        List<ItemStack> ownItems = first ? session.getFirstItems() : session.getSecondItems();
        List<ItemStack> otherItems = first ? session.getSecondItems() : session.getFirstItems();
        for (int i = 0; i < Math.min(8, ownItems.size()); i++) {
            inventory.setItem(i, ownItems.get(i).clone());
        }
        for (int i = 0; i < Math.min(8, otherItems.size()); i++) {
            inventory.setItem(9 + i, decorateForeign(otherItems.get(i)));
        }
        inventory.setItem(27, GuiItems.button(Material.HOPPER, "\u00A7aHand-Item hinzufuegen", List.of("\u00A77Nimmt das komplette Stack aus deiner Hand")));
        inventory.setItem(28, GuiItems.button(Material.BARRIER, "\u00A7cLetztes eigenes Item entfernen", List.of("\u00A77Legt dein letztes Angebot zurueck")));
        inventory.setItem(30, GuiItems.button(Material.GOLD_NUGGET, "\u00A76Coins -10", List.of("\u00A77Verringert dein Coin-Angebot")));
        inventory.setItem(31, GuiItems.button(Material.GOLD_INGOT, "\u00A76Coins +10", List.of("\u00A77Erhoeht dein Coin-Angebot")));
        inventory.setItem(32, GuiItems.button(Material.GOLD_BLOCK, "\u00A76Coins +100", List.of("\u00A77Erhoeht dein Coin-Angebot")));
        inventory.setItem(34, GuiItems.button(Material.EMERALD_BLOCK, "\u00A7aBestaetigen", List.of(statusLine(session, first), "\u00A77Beide Seiten m\u00fcssen bestaetigen")));
        inventory.setItem(35, GuiItems.button(Material.REDSTONE_BLOCK, "\u00A7cAbbrechen", List.of("\u00A77Handel beenden")));
        inventory.setItem(40, GuiItems.button(Material.SUNFLOWER,
                "\u00A7eDein Angebot: " + (int) (first ? session.getFirstCoins() : session.getSecondCoins()) + " Coins",
                List.of("\u00A77Gegenseite: " + (int) (first ? session.getSecondCoins() : session.getFirstCoins()) + " Coins")));
        player.openInventory(inventory);
    }

    public void handleTradeClick(Player player, int rawSlot) {
        TradeSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            player.closeInventory();
            return;
        }
        switch (rawSlot) {
            case 27 -> addHandItem(player, session);
            case 28 -> removeLastOwnItem(player, session);
            case 30 -> changeCoins(player, session, -10);
            case 31 -> changeCoins(player, session, 10);
            case 32 -> changeCoins(player, session, 100);
            case 34 -> confirm(player, session);
            case 35 -> cancel(player.getUniqueId(), true);
            default -> {
                return;
            }
        }
        Player other = Bukkit.getPlayer(partnerOf(session, player.getUniqueId()));
        openTradeView(player);
        if (other != null) {
            openTradeView(other);
        }
    }

    public void tick() {
        long cutoff = System.currentTimeMillis() - Duration.ofMinutes(5).toMillis();
        sessions.values().stream()
                .filter(session -> session.getCreatedAt() < cutoff)
                .map(TradeSession::getFirstPlayer)
                .distinct()
                .toList()
                .forEach(playerId -> cancel(playerId, true));
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Konnte trade.yml nicht speichern: " + exception.getMessage());
        }
    }

    private void sendRequest(Player player, Player target) {
        pendingRequests.put(target.getUniqueId(), player.getUniqueId());
        player.sendMessage("Handelsanfrage an " + target.getName() + " gesendet.");
        target.sendMessage(player.getName() + " m\u00f6chte mit dir handeln. Nutze /trade oder klick im Menue auf Annehmen.");
    }

    private void acceptRequest(Player player) {
        UUID requesterId = pendingRequests.remove(player.getUniqueId());
        if (requesterId == null) {
            player.sendMessage("Keine offene Anfrage.");
            return;
        }
        Player requester = Bukkit.getPlayer(requesterId);
        if (requester == null) {
            player.sendMessage("Anfragender Spieler ist offline.");
            return;
        }
        TradeSession session = new TradeSession(requester.getUniqueId(), player.getUniqueId(), System.currentTimeMillis());
        sessions.put(requester.getUniqueId(), session);
        sessions.put(player.getUniqueId(), session);
        requester.sendMessage(player.getName() + " hat die Handelsanfrage angenommen.");
        player.sendMessage("Handel mit " + requester.getName() + " gestartet.");
        openTradeView(requester);
        openTradeView(player);
    }

    private void addHandItem(Player player, TradeSession session) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            player.sendMessage("Halte ein Item in der Hand.");
            return;
        }
        if (session.getFirstPlayer().equals(player.getUniqueId())) {
            session.getFirstItems().add(held.clone());
        } else {
            session.getSecondItems().add(held.clone());
        }
        player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        resetConfirmations(session);
    }

    private void removeLastOwnItem(Player player, TradeSession session) {
        List<ItemStack> items = session.getFirstPlayer().equals(player.getUniqueId()) ? session.getFirstItems() : session.getSecondItems();
        if (items.isEmpty()) {
            player.sendMessage("Du hast kein Item im Handel.");
            return;
        }
        ItemStack item = items.remove(items.size() - 1);
        Map<Integer, ItemStack> rest = player.getInventory().addItem(item);
        if (!rest.isEmpty()) {
            claimStorage.addClaim(player.getUniqueId(), item, "Tausch R\u00fcckgabe", 0, "R\u00fcckgabe aus abgebrochenem/angepasstem Handel");
        }
        resetConfirmations(session);
    }

    private void changeCoins(Player player, TradeSession session, double delta) {
        boolean first = session.getFirstPlayer().equals(player.getUniqueId());
        double current = first ? session.getFirstCoins() : session.getSecondCoins();
        double next = Math.max(0, current + delta);
        if (next > economyService.getBalance(player.getUniqueId())) {
            player.sendMessage("Nicht genug Coins f\u00fcr dieses Angebot.");
            return;
        }
        if (first) {
            session.setFirstCoins(next);
        } else {
            session.setSecondCoins(next);
        }
        resetConfirmations(session);
    }

    private void confirm(Player player, TradeSession session) {
        if (session.getFirstPlayer().equals(player.getUniqueId())) {
            session.setFirstAccepted(true);
        } else {
            session.setSecondAccepted(true);
        }
        if (session.isFirstAccepted() && session.isSecondAccepted()) {
            completeTrade(session);
        } else {
            player.sendMessage("Bestaetigt. Warte auf den Partner.");
        }
    }

    private void completeTrade(TradeSession session) {
        Player first = Bukkit.getPlayer(session.getFirstPlayer());
        Player second = Bukkit.getPlayer(session.getSecondPlayer());
        if (first == null || second == null) {
            cancel(session.getFirstPlayer(), true);
            return;
        }
        if (economyService.getBalance(session.getFirstPlayer()) < session.getFirstCoins()
                || economyService.getBalance(session.getSecondPlayer()) < session.getSecondCoins()) {
            cancel(session.getFirstPlayer(), true);
            return;
        }
        economyService.withdraw(session.getFirstPlayer(), session.getFirstCoins());
        economyService.withdraw(session.getSecondPlayer(), session.getSecondCoins());
        economyService.deposit(session.getFirstPlayer(), session.getSecondCoins());
        economyService.deposit(session.getSecondPlayer(), session.getFirstCoins());
        moveItems(first, session.getSecondItems(), "Tausch erhalten");
        moveItems(second, session.getFirstItems(), "Tausch erhalten");
        first.sendMessage("Handel abgeschlossen.");
        second.sendMessage("Handel abgeschlossen.");
        sessions.remove(session.getFirstPlayer());
        sessions.remove(session.getSecondPlayer());
        first.closeInventory();
        second.closeInventory();
    }

    private void cancel(UUID playerId, boolean notify) {
        TradeSession session = sessions.get(playerId);
        if (session == null) {
            return;
        }
        Player first = Bukkit.getPlayer(session.getFirstPlayer());
        Player second = Bukkit.getPlayer(session.getSecondPlayer());
        if (first != null) {
            moveItems(first, session.getFirstItems(), "Tausch R\u00fcckgabe");
            if (notify) {
                first.sendMessage("Handel abgebrochen.");
            }
            first.closeInventory();
        }
        if (second != null) {
            moveItems(second, session.getSecondItems(), "Tausch R\u00fcckgabe");
            if (notify) {
                second.sendMessage("Handel abgebrochen.");
            }
            second.closeInventory();
        }
        sessions.remove(session.getFirstPlayer());
        sessions.remove(session.getSecondPlayer());
    }

    private void moveItems(Player player, List<ItemStack> items, String source) {
        for (ItemStack item : items) {
            Map<Integer, ItemStack> rest = player.getInventory().addItem(item);
            if (!rest.isEmpty()) {
                claimStorage.addClaim(player.getUniqueId(), item, source, 0, "Item wurde ins Abholfach gelegt");
            }
        }
    }

    private UUID partnerOf(TradeSession session, UUID playerId) {
        return session.getFirstPlayer().equals(playerId) ? session.getSecondPlayer() : session.getFirstPlayer();
    }

    private String statusLine(TradeSession session, boolean first) {
        boolean own = first ? session.isFirstAccepted() : session.isSecondAccepted();
        boolean other = first ? session.isSecondAccepted() : session.isFirstAccepted();
        return "\u00A77Du: " + (own ? "\u00A7aOK" : "\u00A7cOffen") + " \u00A78| \u00A77Partner: " + (other ? "\u00A7aOK" : "\u00A7cOffen");
    }

    private ItemStack decorateForeign(ItemStack item) {
        ItemStack display = item.clone();
        ItemMeta meta = display.getItemMeta();
        List<String> lore = meta != null && meta.hasLore() ? meta.getLore() : new java.util.ArrayList<>();
        lore = new java.util.ArrayList<>(lore);
        lore.add(" ");
        lore.add("\u00A77Angebot der Gegenseite");
        if (meta != null) {
            meta.setLore(lore);
            display.setItemMeta(meta);
        }
        return display;
    }

    private void resetConfirmations(TradeSession session) {
        session.setFirstAccepted(false);
        session.setSecondAccepted(false);
    }
}


