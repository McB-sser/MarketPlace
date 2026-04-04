package de.mcbesser.marketplace.mail;

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
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

public class MailManager {

    private static final int[] ITEM_SLOTS = {0, 1, 2, 3, 9, 10, 11, 12, 18, 19, 20, 21};
    private static final int[] BUTTON_SLOTS = {4, 45, 46, 47, 48, 49, 50, 51, 52, 53};
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
            .withZone(ZoneId.systemDefault());

    private final MarketplacePlugin plugin;
    private final EconomyService economyService;
    private final ClaimStorage claimStorage;
    private final File file;
    private final AtomicInteger nextId = new AtomicInteger(1);
    private final Map<UUID, MailDraft> drafts = new HashMap<>();
    private final Map<UUID, List<MailEntry>> inboxes = new HashMap<>();
    private final Map<UUID, MailViewContext> readReturnTargets = new HashMap<>();
    private final Map<UUID, ItemStack> editingBookSnapshots = new HashMap<>();
    private final Map<UUID, Integer> editingBookSlots = new HashMap<>();
    private final Map<UUID, ItemStack> previousHeldItems = new HashMap<>();

    public MailManager(MarketplacePlugin plugin, EconomyService economyService, ClaimStorage claimStorage) throws IOException {
        this.plugin = plugin;
        this.economyService = economyService;
        this.claimStorage = claimStorage;
        this.file = new File(plugin.getDataFolder(), "mail.yml");
        load();
    }

    public void openPlayerList(Player player) {
        captureBookMessage(player);
        Inventory inventory = Bukkit.createInventory(new MenuHolder(MenuType.MAIL_PLAYERS), 54, "Mail an Spieler");
        List<OfflinePlayer> targets = mailTargets(player);
        for (int slot = 0; slot < Math.min(45, targets.size()); slot++) {
            OfflinePlayer target = targets.get(slot);
            inventory.setItem(slot, GuiItems.playerHead(target, "&a" + safeName(target),
                    List.of(statusLine(target), "&7Klick zum Mail-Entwurf")));
        }
        inventory.setItem(45, GuiItems.button(Material.COMPASS, "&aMarketplace", List.of("&7Zum Hauptmen\u00fc")));
        inventory.setItem(46, GuiItems.button(Material.SPYGLASS, "&eAktualisieren", List.of("&7Spielerliste neu laden")));
        inventory.setItem(49, GuiItems.button(Material.WRITABLE_BOOK, draftButtonTitle(player), List.of(draftButtonLore(player))));
        inventory.setItem(50, GuiItems.button(Material.CHEST, "&ePostfach", List.of("&7Erhaltene Spieler-Mails \u00f6ffnen")));
        player.openInventory(inventory);
    }

    public void handlePlayerListClick(Player player, int rawSlot) {
        List<OfflinePlayer> targets = mailTargets(player);
        if (rawSlot == 45) {
            player.performCommand("marketplace");
            return;
        }
        if (rawSlot == 46) {
            openPlayerList(player);
            return;
        }
        if (rawSlot == 49) {
            if (drafts.containsKey(player.getUniqueId())) {
                openCompose(player);
            }
            return;
        }
        if (rawSlot == 50) {
            openInbox(player, 0);
            return;
        }
        if (rawSlot < 0 || rawSlot >= targets.size() || rawSlot >= 45) {
            return;
        }
        OfflinePlayer target = targets.get(rawSlot);
        drafts.put(player.getUniqueId(), new MailDraft(target.getUniqueId(), safeName(target)));
        openCompose(player);
    }

    private List<OfflinePlayer> mailTargets(Player player) {
        return java.util.Arrays.stream(Bukkit.getOfflinePlayers())
                .filter(other -> !other.getUniqueId().equals(player.getUniqueId()))
                .filter(other -> other.isOnline() || other.hasPlayedBefore())
                .filter(other -> other.getName() != null && !other.getName().isBlank())
                .sorted(Comparator.comparing(this::safeName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public void openCompose(Player player) {
        captureBookMessage(player);
        MailDraft draft = drafts.get(player.getUniqueId());
        if (draft == null) {
            openPlayerList(player);
            return;
        }
        Inventory inventory = Bukkit.createInventory(new MenuHolder(MenuType.MAIL_COMPOSE), 54, "Mail an " + draft.getRecipientName());
        for (int i = 0; i < Math.min(ITEM_SLOTS.length, draft.getItems().size()); i++) {
            inventory.setItem(ITEM_SLOTS[i], draft.getItems().get(i).clone());
        }
        fillDecorations(inventory);
        inventory.setItem(4, GuiItems.button(Material.PAPER, "&eMail an " + draft.getRecipientName(),
                List.of("&7Geld: " + CurrencyFormatter.shortAmount(draft.getCoins()),
                        "&7Items: " + draft.getItems().size(),
                        "&7Nachricht: " + messagePreview(draft))));
        inventory.setItem(45, GuiItems.button(Material.COMPASS, "&aMarketplace", List.of("&7Zum Hauptmen\u00fc")));
        inventory.setItem(46, GuiItems.button(Material.PLAYER_HEAD, "&eSpielerliste", List.of("&7Anderen Empf\u00e4nger w\u00e4hlen")));
        inventory.setItem(47, GuiItems.button(Material.GOLD_NUGGET, "&6CraftTaler +/-1", List.of("&7Links +1", "&7Rechts -1")));
        inventory.setItem(48, GuiItems.button(Material.GOLD_INGOT, "&6CraftTaler +/-10", List.of("&7Links +10", "&7Rechts -10")));
        inventory.setItem(49, GuiItems.button(Material.GOLD_BLOCK, "&6CraftTaler +/-100", List.of("&7Links +100", "&7Rechts -100")));
        inventory.setItem(50, GuiItems.button(Material.WRITABLE_BOOK, "&eNachricht setzen", List.of(messageInstruction(draft))));
        inventory.setItem(51, GuiItems.button(Material.BARRIER, "&cEntwurf verwerfen", List.of("&7Items gehen zur\u00fcck an dich")));
        inventory.setItem(52, GuiItems.button(Material.CHEST, "&ePostfach", List.of("&7Erhaltene Spieler-Mails \u00f6ffnen")));
        inventory.setItem(53, GuiItems.button(Material.EMERALD_BLOCK, "&aMail senden", List.of("&7Versendet Items, Geld und Nachricht")));
        player.openInventory(inventory);
    }

    public void handleComposeClick(Player player, InventoryClickEvent event) {
        MailDraft draft = drafts.get(player.getUniqueId());
        if (draft == null) {
            player.closeInventory();
            return;
        }
        int rawSlot = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();
        if (rawSlot < topSize && isItemSlot(rawSlot)) {
            event.setCancelled(false);
            return;
        }
        if (rawSlot >= topSize) {
            event.setCancelled(false);
            return;
        }
        event.setCancelled(true);
        syncDraftFromView(draft, event.getView().getTopInventory());
        switch (rawSlot) {
            case 45 -> player.performCommand("marketplace");
            case 46 -> openPlayerList(player);
            case 47 -> {
                changeCoins(player, draft, clickDelta(event, 1));
                openCompose(player);
            }
            case 48 -> {
                changeCoins(player, draft, clickDelta(event, 10));
                openCompose(player);
            }
            case 49 -> {
                changeCoins(player, draft, clickDelta(event, 100));
                openCompose(player);
            }
            case 50 -> beginMessageInput(player, draft);
            case 51 -> cancelDraft(player, true);
            case 52 -> openInbox(player, 0);
            case 53 -> sendDraft(player, draft);
            default -> {
            }
        }
    }

    public void openInbox(Player player, int page) {
        captureBookMessage(player);
        List<MailEntry> entries = inboxes.getOrDefault(player.getUniqueId(), List.of());
        Inventory inventory = Bukkit.createInventory(new MenuHolder(MenuType.MAIL_INBOX, page, ""), 54, "Postfach");
        int start = page * 45;
        for (int slot = 0; slot < 45; slot++) {
            int index = start + slot;
            if (index >= entries.size()) {
                break;
            }
            inventory.setItem(slot, createInboxDisplay(entries.get(index)));
        }
        inventory.setItem(45, GuiItems.button(Material.COMPASS, "&aMarketplace", List.of("&7Zum Hauptmen\u00fc")));
        inventory.setItem(46, GuiItems.button(Material.ARROW, "&eZur\u00fcck", List.of("&7Vorherige Seite")));
        inventory.setItem(49, GuiItems.button(Material.PLAYER_HEAD, "&eMail senden", List.of("&7Neue Spieler-Mail verfassen")));
        inventory.setItem(53, GuiItems.button(Material.ARROW, "&eWeiter", List.of("&7N\u00e4chste Seite")));
        player.openInventory(inventory);
    }

    public void openMailDetail(Player player, int page, int entryId) {
        MailEntry entry = findEntry(player.getUniqueId(), entryId);
        if (entry == null) {
            openInbox(player, page);
            return;
        }
        Inventory inventory = Bukkit.createInventory(new MenuHolder(MenuType.MAIL_VIEW, page, Integer.toString(entryId)), 54,
                "Mail von " + entry.getSenderName());
        for (int i = 0; i < Math.min(ITEM_SLOTS.length, entry.getItems().size()); i++) {
            inventory.setItem(ITEM_SLOTS[i], entry.getItems().get(i).clone());
        }
        fillDetailDecorations(inventory);
        inventory.setItem(4, GuiItems.button(Material.PAPER, "&eDatum",
                List.of("&7" + FORMATTER.format(Instant.ofEpochMilli(entry.getCreatedAt())))));
        inventory.setItem(13, GuiItems.button(Material.SUNFLOWER, "&6CraftTaler: " + CurrencyFormatter.shortAmount(entry.getCoins()),
                List.of("&7Klick zum Auszahlen")));
        inventory.setItem(22, GuiItems.button(Material.WRITTEN_BOOK, "&eMail lesen",
                List.of("&7" + messagePreview(entry.getMessage()), "&7Nachricht im Buch \u00f6ffnen")));
        inventory.setItem(45, GuiItems.button(Material.COMPASS, "&aMarketplace", List.of("&7Zum Hauptmen\u00fc")));
        inventory.setItem(46, GuiItems.button(Material.ARROW, "&eZur\u00fcck", List.of("&7Zur\u00fcck ins Postfach")));
        inventory.setItem(49, GuiItems.button(Material.CHEST, "&aAlles abholen", List.of("&7Items und Geld \u00fcbernehmen")));
        inventory.setItem(53, GuiItems.button(Material.BARRIER, "&cMail l\u00f6schen", List.of("&7Eintrag aus dem Postfach entfernen")));
        player.openInventory(inventory);
    }

    public void handleInboxClick(Player player, int rawSlot, int page) {
        List<MailEntry> entries = inboxes.getOrDefault(player.getUniqueId(), new ArrayList<>());
        if (rawSlot == 45) {
            player.performCommand("marketplace");
            return;
        }
        if (rawSlot == 46 && page > 0) {
            openInbox(player, page - 1);
            return;
        }
        if (rawSlot == 49) {
            if (drafts.containsKey(player.getUniqueId())) {
                openCompose(player);
            } else {
                openPlayerList(player);
            }
            return;
        }
        if (rawSlot == 53 && ((page + 1) * 45) < entries.size()) {
            openInbox(player, page + 1);
            return;
        }
        if (rawSlot < 0 || rawSlot >= 45) {
            return;
        }
        int index = page * 45 + rawSlot;
        if (index >= entries.size()) {
            return;
        }
        openMailDetail(player, page, entries.get(index).getId());
    }

    public void handleMailDetailClick(Player player, InventoryClickEvent event, int page, int entryId) {
        MailEntry entry = findEntry(player.getUniqueId(), entryId);
        if (entry == null) {
            openInbox(player, page);
            return;
        }
        int rawSlot = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();
        if (rawSlot < topSize && isItemSlot(rawSlot)) {
            if (event.getCursor() != null && !event.getCursor().getType().isAir()) {
                event.setCancelled(true);
                return;
            }
            event.setCancelled(false);
            scheduleMailDetailSync(player, entryId);
            return;
        }
        if (rawSlot >= topSize) {
            if (event.getClick().isShiftClick()) {
                event.setCancelled(true);
                return;
            }
            event.setCancelled(false);
            scheduleMailDetailSync(player, entryId);
            return;
        }
        switch (rawSlot) {
            case 45 -> player.performCommand("marketplace");
            case 46 -> openInbox(player, page);
            case 13 -> {
                if (entry.getCoins() <= 0) {
                    return;
                }
                economyService.deposit(player.getUniqueId(), entry.getCoins());
                entry.setCoins(0);
                save();
                MessageUtil.send(player, "CraftTaler aus der Mail erhalten.");
                openMailDetail(player, page, entryId);
            }
            case 22 -> openReadableMail(player, page, entry);
            case 49 -> {
                syncMailDetailFromView(entry, event.getView().getTopInventory());
                deleteEntry(player.getUniqueId(), entryId);
                claimMail(player, entry);
                openInbox(player, page);
            }
            case 53 -> {
                syncMailDetailFromView(entry, event.getView().getTopInventory());
                deleteEntry(player.getUniqueId(), entryId);
                MessageUtil.send(player, "Mail gel\u00f6scht.");
                openInbox(player, page);
            }
            default -> {
            }
        }
    }

    public void handleMailDetailClose(Player player, int entryId, Inventory inventory) {
        MailEntry entry = findEntry(player.getUniqueId(), entryId);
        if (entry == null) {
            return;
        }
        syncMailDetailFromView(entry, inventory);
    }

    public int getUnreadCount(UUID playerId) {
        return inboxes.getOrDefault(playerId, List.of()).size();
    }

    public void sendUnreadReminder(Player player) {
        int unread = getUnreadCount(player.getUniqueId());
        if (unread <= 0) {
            return;
        }
        MessageUtil.sendActions(player, "Du hast " + unread + " Spieler-Mail" + (unread == 1 ? "" : "s") + " im Postfach.",
                MessageUtil.action("Postfach \u00f6ffnen", "mail"),
                MessageUtil.action("Marketplace", "marketplace"));
    }

    public boolean handleBookEdit(PlayerEditBookEvent event) {
        Player player = event.getPlayer();
        MailDraft draft = drafts.get(player.getUniqueId());
        ItemStack snapshot = editingBookSnapshots.get(player.getUniqueId());
        Integer slot = editingBookSlots.get(player.getUniqueId());
        if (draft == null || !draft.isAwaitingMessage() || snapshot == null || slot == null) {
            return false;
        }
        draft.setAwaitingMessage(false);
        String message = String.join("\n", event.getNewBookMeta().getPages()).trim();
        if (message.isBlank()) {
            draft.setMessage("");
            plugin.getServer().getScheduler().runTask(plugin, () -> MessageUtil.send(player, "Mail-Nachricht geleert."));
        } else {
            draft.setMessage(message);
            plugin.getServer().getScheduler().runTask(plugin, () -> MessageUtil.send(player, "Mail-Nachricht gesetzt."));
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            openCompose(player);
            plugin.getServer().getScheduler().runTask(plugin, () -> restoreEditingBook(player));
        });
        return true;
    }

    public boolean hasDraft(UUID playerId) {
        return drafts.containsKey(playerId);
    }

    public void resumeDraft(Player player) {
        captureBookMessage(player);
        MailViewContext viewContext = readReturnTargets.remove(player.getUniqueId());
        if (viewContext != null) {
            openMailDetail(player, viewContext.page(), viewContext.entryId());
            return;
        }
        if (hasDraft(player.getUniqueId())) {
            openCompose(player);
        } else {
            openPlayerList(player);
        }
    }

    public void handleComposeClose(Player player) {
        MailDraft draft = drafts.get(player.getUniqueId());
        if (draft != null && draft.isAwaitingMessage()) {
            plugin.getServer().getScheduler().runTask(plugin, () -> captureBookMessage(player));
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("nextId", nextId.get());
        for (Map.Entry<UUID, List<MailEntry>> inbox : inboxes.entrySet()) {
            String root = "mail." + inbox.getKey();
            int index = 0;
            for (MailEntry entry : inbox.getValue()) {
                String path = root + "." + index++;
                yaml.set(path + ".id", entry.getId());
                yaml.set(path + ".senderId", entry.getSenderId().toString());
                yaml.set(path + ".senderName", entry.getSenderName());
                yaml.set(path + ".message", entry.getMessage());
                yaml.set(path + ".coins", entry.getCoins());
                yaml.set(path + ".createdAt", entry.getCreatedAt());
                yaml.set(path + ".items", entry.getItems());
            }
        }
        try {
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Konnte mail.yml nicht speichern: " + exception.getMessage());
        }
    }

    public void shutdown() {
        for (UUID playerId : new ArrayList<>(editingBookSnapshots.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                restoreEditingBook(player);
            }
        }
        for (Map.Entry<UUID, MailDraft> entry : drafts.entrySet()) {
            returnDraftItems(entry.getKey(), entry.getValue(), "Mail-Entwurf R\u00fcckgabe");
        }
        drafts.clear();
        save();
    }

    private void load() throws IOException {
        if (!file.exists() && !file.createNewFile()) {
            throw new IOException("mail.yml konnte nicht erstellt werden.");
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        nextId.set(yaml.getInt("nextId", 1));
        ConfigurationSection section = yaml.getConfigurationSection("mail");
        if (section == null) {
            return;
        }
        for (String playerKey : section.getKeys(false)) {
            ConfigurationSection inbox = section.getConfigurationSection(playerKey);
            if (inbox == null) {
                continue;
            }
            List<MailEntry> entries = new ArrayList<>();
            for (String key : inbox.getKeys(false)) {
                String path = "mail." + playerKey + "." + key;
                String senderId = yaml.getString(path + ".senderId");
                if (senderId == null) {
                    continue;
                }
                MailEntry entry = new MailEntry(
                        yaml.getInt(path + ".id"),
                        UUID.fromString(senderId),
                        yaml.getString(path + ".senderName", "Unbekannt"),
                        yaml.getString(path + ".message", ""),
                        yaml.getDouble(path + ".coins"),
                        yaml.getLong(path + ".createdAt")
                );
                List<?> items = yaml.getList(path + ".items");
                if (items != null) {
                    for (Object item : items) {
                        if (item instanceof ItemStack itemStack) {
                            entry.getItems().add(itemStack);
                        }
                    }
                }
                entries.add(entry);
            }
            inboxes.put(UUID.fromString(playerKey), entries);
        }
    }

    private void syncDraftFromView(MailDraft draft, Inventory inventory) {
        List<ItemStack> items = new ArrayList<>();
        for (int slot : ITEM_SLOTS) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && !item.getType().isAir()) {
                items.add(item.clone());
            }
        }
        if (!matches(draft.getItems(), items)) {
            draft.getItems().clear();
            draft.getItems().addAll(items);
        }
    }

    private boolean matches(List<ItemStack> first, List<ItemStack> second) {
        if (first.size() != second.size()) {
            return false;
        }
        for (int i = 0; i < first.size(); i++) {
            if (!first.get(i).equals(second.get(i))) {
                return false;
            }
        }
        return true;
    }

    private boolean isItemSlot(int slot) {
        for (int itemSlot : ITEM_SLOTS) {
            if (itemSlot == slot) {
                return true;
            }
        }
        return false;
    }

    private boolean isButtonSlot(int slot) {
        for (int buttonSlot : BUTTON_SLOTS) {
            if (buttonSlot == slot) {
                return true;
            }
        }
        return false;
    }

    private void fillDecorations(Inventory inventory) {
        ItemStack glass = GuiItems.button(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (isItemSlot(slot) || isButtonSlot(slot)) {
                continue;
            }
            inventory.setItem(slot, glass);
        }
    }

    private void fillDetailDecorations(Inventory inventory) {
        ItemStack glass = GuiItems.button(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (isItemSlot(slot) || slot == 4 || slot == 13 || slot == 22 || slot == 45 || slot == 46 || slot == 49 || slot == 53) {
                continue;
            }
            inventory.setItem(slot, glass);
        }
    }

    private void openReadableMail(Player player, int page, MailEntry entry) {
        readReturnTargets.put(player.getUniqueId(), new MailViewContext(page, entry.getId()));
        player.openBook(createReadableBook(entry));
        plugin.getServer().getScheduler().runTask(plugin, () ->
                MessageUtil.sendActions(player, "Nach dem Lesen hier zur\u00fcck zur Mail.",
                        MessageUtil.action("Mail \u00f6ffnen", "mail"),
                        MessageUtil.action("Postfach", "mail")));
    }

    private void changeCoins(Player player, MailDraft draft, double delta) {
        double next = Math.max(0, draft.getCoins() + delta);
        if (next > economyService.getBalance(player.getUniqueId())) {
            MessageUtil.send(player, "Nicht genug CraftTaler f\u00fcr diese Mail.");
            return;
        }
        draft.setCoins(next);
    }

    private double clickDelta(InventoryClickEvent event, int step) {
        if (event.getClick().isLeftClick()) {
            return step;
        }
        if (event.getClick().isRightClick()) {
            return -step;
        }
        return 0;
    }

    private void beginMessageInput(Player player, MailDraft draft) {
        draft.setAwaitingMessage(true);
        player.closeInventory();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            prepareEditableBook(player, draft);
            player.updateInventory();
            MessageUtil.send(player, "Du hast jetzt ein beschreibbares Buch in der Hand.");
            MessageUtil.sendActions(player, "Rechtsklick damit, schreibe deine Nachricht. Danach mit Mail zur\u00fcck.",
                    MessageUtil.action("Mail \u00f6ffnen", "mail"),
                    MessageUtil.action("Marketplace", "marketplace"));
        });
    }

    private void sendDraft(Player player, MailDraft draft) {
        if (draft.getItems().isEmpty() && draft.getCoins() <= 0 && draft.getMessage().isBlank()) {
            MessageUtil.send(player, "Lege Items, Geld oder eine Nachricht fest.");
            return;
        }
        if (draft.getCoins() > 0 && !economyService.withdraw(player.getUniqueId(), draft.getCoins())) {
            MessageUtil.send(player, "Nicht genug CraftTaler f\u00fcr diese Mail.");
            return;
        }
        MailEntry entry = new MailEntry(nextId.getAndIncrement(), player.getUniqueId(), player.getName(),
                draft.getMessage(), draft.getCoins(), System.currentTimeMillis());
        for (ItemStack item : draft.getItems()) {
            entry.getItems().add(item.clone());
        }
        inboxes.computeIfAbsent(draft.getRecipientId(), ignored -> new ArrayList<>()).add(entry);
        drafts.remove(player.getUniqueId());
        save();
        MessageUtil.send(player, "Mail an " + draft.getRecipientName() + " gesendet.");
        Player recipient = Bukkit.getPlayer(draft.getRecipientId());
        if (recipient != null) {
            MessageUtil.sendActions(recipient, "Du hast neue Spieler-Mail von " + player.getName() + ".",
                    MessageUtil.action("Postfach \u00f6ffnen", "mail"),
                    MessageUtil.action("Marketplace", "marketplace"));
        }
        openPlayerList(player);
    }

    private void cancelDraft(Player player, boolean reopenPlayerList) {
        MailDraft draft = drafts.remove(player.getUniqueId());
        if (draft == null) {
            return;
        }
        returnDraftItems(player.getUniqueId(), draft, "Mail-Entwurf R\u00fcckgabe");
        MessageUtil.send(player, "Mail-Entwurf verworfen.");
        if (reopenPlayerList) {
            openPlayerList(player);
        }
    }

    private void returnDraftItems(UUID playerId, MailDraft draft, String source) {
        Player player = Bukkit.getPlayer(playerId);
        for (ItemStack item : draft.getItems()) {
            if (player != null) {
                Map<Integer, ItemStack> rest = player.getInventory().addItem(item);
                if (rest.isEmpty()) {
                    continue;
                }
            }
            claimStorage.addClaim(playerId, item, source, 0, "Items aus nicht versendeter Mail");
        }
    }

    private void claimMail(Player player, MailEntry entry) {
        if (entry.getCoins() > 0) {
            economyService.deposit(player.getUniqueId(), entry.getCoins());
        }
        for (ItemStack item : entry.getItems()) {
            Map<Integer, ItemStack> rest = player.getInventory().addItem(item);
            if (!rest.isEmpty()) {
                claimStorage.addClaim(player.getUniqueId(), item, "Spieler-Mail", entry.getCoins(),
                        "Mail von " + entry.getSenderName());
            }
        }
        MessageUtil.send(player, "Mail von " + entry.getSenderName() + " erhalten.");
    }

    private MailEntry findEntry(UUID playerId, int entryId) {
        return inboxes.getOrDefault(playerId, List.of()).stream()
                .filter(entry -> entry.getId() == entryId)
                .findFirst()
                .orElse(null);
    }

    private void deleteEntry(UUID playerId, int entryId) {
        List<MailEntry> entries = inboxes.getOrDefault(playerId, new ArrayList<>());
        entries.removeIf(entry -> entry.getId() == entryId);
        inboxes.put(playerId, entries);
        save();
    }

    private void scheduleMailDetailSync(Player player, int entryId) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder holder)
                    || holder.getType() != MenuType.MAIL_VIEW
                    || !holder.getContext().equals(Integer.toString(entryId))) {
                return;
            }
            MailEntry entry = findEntry(player.getUniqueId(), entryId);
            if (entry == null) {
                return;
            }
            syncMailDetailFromView(entry, player.getOpenInventory().getTopInventory());
        });
    }

    private void syncMailDetailFromView(MailEntry entry, Inventory inventory) {
        List<ItemStack> items = new ArrayList<>();
        for (int slot : ITEM_SLOTS) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && !item.getType().isAir()) {
                items.add(item.clone());
            }
        }
        if (!matches(entry.getItems(), items)) {
            entry.getItems().clear();
            entry.getItems().addAll(items);
            save();
        }
    }

    private ItemStack createInboxDisplay(MailEntry entry) {
        Material icon = entry.getItems().isEmpty() ? Material.PAPER : entry.getItems().get(0).getType();
        return GuiItems.button(icon, "&aPost von " + entry.getSenderName(),
                List.of("&7Geld: " + CurrencyFormatter.shortAmount(entry.getCoins()),
                        "&7Items: " + entry.getItems().size(),
                        "&7Nachricht: " + messagePreview(entry.getMessage()),
                        "&7Datum: " + FORMATTER.format(Instant.ofEpochMilli(entry.getCreatedAt())),
                        "&aKlick zum \u00d6ffnen"));
    }

    private ItemStack createReadableBook(MailEntry entry) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.setTitle("Mail von " + entry.getSenderName());
        meta.setAuthor(entry.getSenderName());
        if (entry.getMessage().isBlank()) {
            meta.addPage("Keine Nachricht");
        } else {
            for (String page : splitPages(entry.getMessage())) {
                meta.addPage(page);
            }
        }
        book.setItemMeta(meta);
        return book;
    }

    private record MailViewContext(int page, int entryId) {
    }

    private String draftButtonTitle(Player player) {
        return drafts.containsKey(player.getUniqueId()) ? "&aEntwurf fortsetzen" : "&7Kein Entwurf";
    }

    private String draftButtonLore(Player player) {
        MailDraft draft = drafts.get(player.getUniqueId());
        if (draft == null) {
            return "&7W\u00e4hle zuerst einen Spieler";
        }
        return "&7An: " + draft.getRecipientName();
    }

    private String safeName(OfflinePlayer player) {
        return player.getName() == null ? player.getUniqueId().toString() : player.getName();
    }

    private String statusLine(OfflinePlayer player) {
        return player.isOnline() ? "&aOnline" : "&7Offline";
    }

    private String messageInstruction(MailDraft draft) {
        return draft.getMessage().isBlank()
                ? "&7Noch keine Nachricht gesetzt"
                : "&7Aktuell: " + messagePreview(draft);
    }

    private String messagePreview(MailDraft draft) {
        return messagePreview(draft.getMessage());
    }

    private String messagePreview(String message) {
        if (message == null || message.isBlank()) {
            return "-";
        }
        return message.length() <= 30 ? message : message.substring(0, 30) + "...";
    }

    private void prepareEditableBook(Player player, MailDraft draft) {
        restoreEditingBook(player);
        int slot = player.getInventory().getHeldItemSlot();
        ItemStack previous = player.getInventory().getItem(slot);
        if (previous != null && !previous.getType().isAir()) {
            previousHeldItems.put(player.getUniqueId(), previous.clone());
        } else {
            previousHeldItems.remove(player.getUniqueId());
        }
        ItemStack book = createEditableBook(draft);
        editingBookSnapshots.put(player.getUniqueId(), book.clone());
        editingBookSlots.put(player.getUniqueId(), slot);
        player.getInventory().setItem(slot, book);
        player.updateInventory();
    }

    private ItemStack createEditableBook(MailDraft draft) {
        ItemStack book = new ItemStack(Material.WRITABLE_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        if (draft.getMessage().isBlank()) {
            meta.addPage("");
        } else {
            for (String page : splitPages(draft.getMessage())) {
                meta.addPage(page);
            }
        }
        book.setItemMeta(meta);
        return book;
    }

    private List<String> splitPages(String text) {
        List<String> pages = new ArrayList<>();
        int index = 0;
        while (index < text.length()) {
            int end = Math.min(text.length(), index + 255);
            pages.add(text.substring(index, end));
            index = end;
        }
        if (pages.isEmpty()) {
            pages.add("");
        }
        return pages;
    }

    private void restoreEditingBook(Player player) {
        UUID playerId = player.getUniqueId();
        Integer slot = editingBookSlots.remove(playerId);
        editingBookSnapshots.remove(playerId);
        if (slot == null) {
            return;
        }
        ItemStack previous = previousHeldItems.remove(playerId);
        player.getInventory().setItem(slot, previous);
        player.updateInventory();
    }

    private void captureBookMessage(Player player) {
        UUID playerId = player.getUniqueId();
        MailDraft draft = drafts.get(playerId);
        Integer slot = editingBookSlots.get(playerId);
        if (draft == null || slot == null) {
            return;
        }
        ItemStack current = player.getInventory().getItem(slot);
        if (current != null && (current.getType() == Material.WRITABLE_BOOK || current.getType() == Material.WRITTEN_BOOK)
                && current.getItemMeta() instanceof BookMeta meta) {
            String message = String.join("\n", meta.getPages()).trim();
            draft.setMessage(message);
        }
        draft.setAwaitingMessage(false);
        plugin.getServer().getScheduler().runTask(plugin, () -> restoreEditingBook(player));
    }
}
