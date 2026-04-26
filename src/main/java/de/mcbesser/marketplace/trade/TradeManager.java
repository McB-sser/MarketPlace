package de.mcbesser.marketplace.trade;

import de.mcbesser.marketplace.EconomyService;
import de.mcbesser.marketplace.MarketplacePlugin;
import de.mcbesser.marketplace.gui.GuiItems;
import de.mcbesser.marketplace.gui.MenuHolder;
import de.mcbesser.marketplace.gui.MenuType;
import de.mcbesser.marketplace.storage.ClaimStorage;
import de.mcbesser.marketplace.util.CurrencyFormatter;
import de.mcbesser.marketplace.util.MessageUtil;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class TradeManager {

    private static final int[] OWN_TRADE_SLOTS = {0, 1, 2, 3, 9, 10, 11, 12, 18, 19, 20, 21, 27, 28, 29, 30};
    private static final int[] FOREIGN_TRADE_SLOTS = {5, 6, 7, 8, 14, 15, 16, 17, 23, 24, 25, 26, 32, 33, 34, 35};
    private static final int[] BUTTON_SLOTS = {45, 46, 47, 48, 49, 50, 51, 52, 53};
    private static final int TOP_OFFER_SLOT = 4;

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
        inventory.setItem(45, GuiItems.button(Material.COMPASS, "\u00A7aMarktplatz", List.of("\u00A77Zur\u00fcck zum Hauptmen\u00fc")));
        inventory.setItem(46, GuiItems.button(Material.SPYGLASS, "\u00A7eAktualisieren", List.of("\u00A77Online-Spieler neu laden")));
        inventory.setItem(49, GuiItems.button(Material.PAPER, actionLabel(player), List.of(actionLore(player))));
        inventory.setItem(51, GuiItems.button(Material.BARREL, "\u00A7eAbholfach", List.of("\u00A77Handelsgewinne und R\u00fcckgaben")));
        player.openInventory(inventory);
    }

    public void handlePlayerListClick(Player player, int rawSlot) {
        List<? extends Player> targets = Bukkit.getOnlinePlayers().stream()
                .filter(other -> !other.getUniqueId().equals(player.getUniqueId()))
                .sorted(Comparator.comparing(Player::getName))
                .toList();
        if (rawSlot == 45) {
            player.performCommand("marketplace");
            return;
        }
        if (rawSlot == 46) {
            openPlayerList(player);
            return;
        }
        if (rawSlot == 49) {
            resumeOrAccept(player);
            return;
        }
        if (rawSlot == 51) {
            claimStorage.openClaims(player, 0, ClaimStorage.CONTEXT_TRADE);
            return;
        }
        if (rawSlot < 0 || rawSlot >= targets.size() || rawSlot >= 45) {
            return;
        }
        sendRequest(player, targets.get(rawSlot));
    }

    public void handleHandelsblattQuickTrade(Player player, Player target) {
        if (sessions.containsKey(player.getUniqueId())) {
            openTradeView(player);
            return;
        }
        UUID requesterId = pendingRequests.get(player.getUniqueId());
        if (requesterId != null && requesterId.equals(target.getUniqueId())) {
            acceptRequest(player);
            return;
        }
        sendRequest(player, target);
    }

    public void openTradeView(Player player) {
        TradeSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            MessageUtil.send(player, "Kein aktiver Handel.");
            return;
        }
        boolean first = session.getFirstPlayer().equals(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(new MenuHolder(MenuType.TRADE_SESSION), 54, "Direkthandel");
        List<ItemStack> ownItems = first ? session.getFirstItems() : session.getSecondItems();
        List<ItemStack> otherItems = first ? session.getSecondItems() : session.getFirstItems();
        for (int slot : ownTradeDecorationSlots()) {
            inventory.setItem(slot, null);
        }
        for (int i = 0; i < Math.min(OWN_TRADE_SLOTS.length, ownItems.size()); i++) {
            inventory.setItem(OWN_TRADE_SLOTS[i], ownItems.get(i).clone());
        }
        for (int i = 0; i < Math.min(FOREIGN_TRADE_SLOTS.length, otherItems.size()); i++) {
            inventory.setItem(FOREIGN_TRADE_SLOTS[i], decorateForeign(otherItems.get(i)));
        }
        fillForeignEmptySlots(inventory, otherItems.size());
        inventory.setItem(TOP_OFFER_SLOT, GuiItems.button(Material.SUNFLOWER,
                "\u00A7eDein Angebot: " + CurrencyFormatter.shortAmount(first ? session.getFirstCoins() : session.getSecondCoins()),
                List.of("\u00A77Gegenseite: " + CurrencyFormatter.shortAmount(first ? session.getSecondCoins() : session.getFirstCoins()),
                        statusLine(session, first),
                        "\u00A77Links legst du Items aus deinem Inventar ab")));
        fillTradeDecorations(inventory);
        inventory.setItem(45, GuiItems.button(Material.COMPASS, "\u00A7aMarktplatz", List.of("\u00A77Hauptmen\u00fc \u00f6ffnen, Handel bleibt aktiv")));
        inventory.setItem(46, GuiItems.button(Material.PLAYER_HEAD, "\u00A7eSpielerliste", List.of("\u00A77Weitere Anfragen und Partner anzeigen")));
        inventory.setItem(47, GuiItems.button(Material.GOLD_NUGGET, "\u00A76CT +/-1", List.of("\u00A77Linksklick: +1", "\u00A77Rechtsklick: -1")));
        inventory.setItem(48, GuiItems.button(Material.GOLD_INGOT, "\u00A76CT +/-10", List.of("\u00A77Linksklick: +10", "\u00A77Rechtsklick: -10")));
        inventory.setItem(49, GuiItems.button(Material.GOLD_BLOCK, "\u00A76CT +/-100", List.of(
                "\u00A77Linksklick: +100",
                "\u00A77Rechtsklick: -100",
                "\u00A77Ducken + Linksklick: +1000",
                "\u00A77Ducken + Rechtsklick: -1000")));
        inventory.setItem(50, GuiItems.button(Material.BARREL, "\u00A7eAbholfach", List.of("\u00A77Handelsgewinne und R\u00fcckgaben")));
        inventory.setItem(52, GuiItems.button(Material.EMERALD_BLOCK, "\u00A7aBest\u00e4tigen", List.of(statusLine(session, first), "\u00A77Beide Seiten m\u00fcssen best\u00e4tigen")));
        inventory.setItem(53, GuiItems.button(Material.REDSTONE_BLOCK, "\u00A7cAbbrechen", List.of("\u00A77Handel beenden")));
        player.openInventory(inventory);
    }

    public void handleTradeClick(Player player, InventoryClickEvent event) {
        TradeSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            player.closeInventory();
            return;
        }
        int rawSlot = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();
        if (rawSlot < topSize && isOwnTradeSlot(rawSlot)) {
            event.setCancelled(false);
            scheduleTradeSync(player);
            return;
        }
        if (rawSlot >= topSize) {
            event.setCancelled(false);
            scheduleTradeSync(player);
            return;
        }
        event.setCancelled(true);
        syncOwnOfferFromView(player, session, event.getView().getTopInventory());
        switch (rawSlot) {
            case 45 -> {
                player.performCommand("marketplace");
                return;
            }
            case 46 -> {
                openPlayerList(player);
                return;
            }
            case 47 -> changeCoins(player, session, resolveCoinDelta(event, 1));
            case 48 -> changeCoins(player, session, resolveCoinDelta(event, 10));
            case 49 -> changeCoins(player, session, resolveCoinDelta(event, 100, 1000));
            case 50 -> {
                claimStorage.openClaims(player, 0, ClaimStorage.CONTEXT_TRADE);
                return;
            }
            case 52 -> confirm(player, session);
            case 53 -> cancel(player.getUniqueId(), true);
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
        if (sessions.containsKey(player.getUniqueId())) {
            openTradeView(player);
            return;
        }
        if (sessions.containsKey(target.getUniqueId())) {
            MessageUtil.send(player, target.getName() + " ist bereits in einem Handel.");
            return;
        }
        pendingRequests.put(target.getUniqueId(), player.getUniqueId());
        MessageUtil.send(player, "Handelsanfrage an " + target.getName() + " gesendet.");
        MessageUtil.sendActions(target, player.getName() + " m\u00f6chte mit dir handeln.",
                MessageUtil.action("Handel \u00f6ffnen", "trade"),
                MessageUtil.action("Marktplatz", "marketplace"));
    }

    private void acceptRequest(Player player) {
        if (sessions.containsKey(player.getUniqueId())) {
            openTradeView(player);
            return;
        }
        UUID requesterId = pendingRequests.remove(player.getUniqueId());
        if (requesterId == null) {
            MessageUtil.send(player, "Keine offene Anfrage.");
            return;
        }
        Player requester = Bukkit.getPlayer(requesterId);
        if (requester == null) {
            MessageUtil.send(player, "Anfragender Spieler ist offline.");
            return;
        }
        if (sessions.containsKey(requesterId)) {
            MessageUtil.send(player, "Der anfragende Spieler handelt bereits anderweitig.");
            return;
        }
        TradeSession session = new TradeSession(requester.getUniqueId(), player.getUniqueId(), System.currentTimeMillis());
        sessions.put(requester.getUniqueId(), session);
        sessions.put(player.getUniqueId(), session);
        MessageUtil.send(requester, player.getName() + " hat die Handelsanfrage angenommen.");
        MessageUtil.send(player, "Handel mit " + requester.getName() + " gestartet.");
        openTradeView(requester);
        openTradeView(player);
    }

    private void changeCoins(Player player, TradeSession session, double delta) {
        boolean first = session.getFirstPlayer().equals(player.getUniqueId());
        double current = first ? session.getFirstCoins() : session.getSecondCoins();
        double next = Math.max(0, current + delta);
        if (next > economyService.getBalance(player.getUniqueId())) {
            MessageUtil.send(player, "Nicht genug CraftTaler f\u00fcr dieses Angebot.");
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
        Player other = Bukkit.getPlayer(partnerOf(session, player.getUniqueId()));
        if (session.isFirstAccepted() && session.isSecondAccepted()) {
            completeTrade(session);
        } else {
            MessageUtil.send(player, "Best\u00e4tigt. Warte auf den Partner.");
            if (other != null) {
                MessageUtil.send(other, player.getName() + " hat sein Angebot best\u00e4tigt.");
            }
        }
    }

    public boolean hasActiveSession(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    public void handleClose(Player player) {
        if (sessions.containsKey(player.getUniqueId())) {
            MessageUtil.sendActions(player, "Handel bleibt offen.",
                    MessageUtil.action("Handel fortsetzen", "trade"),
                    MessageUtil.action("Marktplatz", "marketplace"));
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
        MessageUtil.send(first, "Handel abgeschlossen.");
        MessageUtil.send(second, "Handel abgeschlossen.");
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
                MessageUtil.send(first, "Handel abgebrochen.");
            }
            first.closeInventory();
        }
        if (second != null) {
            moveItems(second, session.getSecondItems(), "Tausch R\u00fcckgabe");
            if (notify) {
                MessageUtil.send(second, "Handel abgebrochen.");
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

    private void scheduleTradeSync(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            TradeSession current = sessions.get(player.getUniqueId());
            if (current == null) {
                return;
            }
            if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder holder)
                    || holder.getType() != MenuType.TRADE_SESSION) {
                return;
            }
            syncOwnOfferFromView(player, current, player.getOpenInventory().getTopInventory());
            Player other = Bukkit.getPlayer(partnerOf(current, player.getUniqueId()));
            if (other != null) {
                openTradeView(other);
            }
        });
    }

    private void syncOwnOfferFromView(Player player, TradeSession session, Inventory inventory) {
        List<ItemStack> updated = new ArrayList<>();
        for (int slot : OWN_TRADE_SLOTS) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && !item.getType().isAir()) {
                updated.add(item.clone());
            }
        }
        List<ItemStack> target = session.getFirstPlayer().equals(player.getUniqueId()) ? session.getFirstItems() : session.getSecondItems();
        if (!offersMatch(target, updated)) {
            target.clear();
            target.addAll(updated);
            resetConfirmations(session);
        }
    }

    private boolean offersMatch(List<ItemStack> current, List<ItemStack> updated) {
        if (current.size() != updated.size()) {
            return false;
        }
        for (int i = 0; i < current.size(); i++) {
            if (!current.get(i).equals(updated.get(i))) {
                return false;
            }
        }
        return true;
    }

    private void fillTradeDecorations(Inventory inventory) {
        ItemStack glass = GuiItems.button(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 36; slot <= 44; slot++) {
            inventory.setItem(slot, glass);
        }
        inventory.setItem(13, glass);
        inventory.setItem(22, glass);
        inventory.setItem(31, glass);
    }

    private void fillForeignEmptySlots(Inventory inventory, int filled) {
        ItemStack glass = GuiItems.button(Material.RED_STAINED_GLASS_PANE, " ", List.of("\u00A77Freier Slot der Gegenseite"));
        for (int i = filled; i < FOREIGN_TRADE_SLOTS.length; i++) {
            inventory.setItem(FOREIGN_TRADE_SLOTS[i], glass);
        }
    }

    private int[] ownTradeDecorationSlots() {
        return new int[]{13, 22, 31};
    }

    private boolean isOwnTradeSlot(int slot) {
        for (int candidate : OWN_TRADE_SLOTS) {
            if (candidate == slot) {
                return true;
            }
        }
        return false;
    }

    private double resolveCoinDelta(InventoryClickEvent event, int step) {
        return resolveCoinDelta(event, step, step);
    }

    private double resolveCoinDelta(InventoryClickEvent event, int step, int shiftStep) {
        boolean shift = event.getClick().isShiftClick();
        int amount = shift ? shiftStep : step;
        if (event.getClick().isLeftClick()) {
            return amount;
        }
        if (event.getClick().isRightClick()) {
            return -amount;
        }
        return 0;
    }

    private void resumeOrAccept(Player player) {
        if (sessions.containsKey(player.getUniqueId())) {
            openTradeView(player);
            return;
        }
        acceptRequest(player);
    }

    private String actionLabel(Player player) {
        if (sessions.containsKey(player.getUniqueId())) {
            return "\u00A7aAktiven Handel \u00f6ffnen";
        }
        return "\u00A7eOffene Anfrage annehmen";
    }

    private String actionLore(Player player) {
        if (sessions.containsKey(player.getUniqueId())) {
            return "\u00A77Setzt deinen aktuellen Handel fort";
        }
        return "\u00A77Nimmt deine letzte Handelsanfrage an";
    }
}


