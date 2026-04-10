package de.mcbesser.marketplace.notes;

import de.mcbesser.marketplace.MarketplacePlugin;
import de.mcbesser.marketplace.gui.GuiItems;
import de.mcbesser.marketplace.gui.MenuHolder;
import de.mcbesser.marketplace.gui.MenuType;
import de.mcbesser.marketplace.util.MessageUtil;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.scheduler.BukkitTask;

public class NoteManager {
    private final MarketplacePlugin plugin;
    private final File file;
    private final Map<UUID, String> notes = new HashMap<>();
    private final Map<UUID, ItemStack> editingBookSnapshots = new HashMap<>();
    private final Map<UUID, Integer> editingBookSlots = new HashMap<>();
    private final Map<UUID, ItemStack> previousHeldItems = new HashMap<>();
    private final Map<UUID, Boolean> awaitingInput = new HashMap<>();
    private final Map<UUID, BukkitTask> editWatchTasks = new HashMap<>();

    public NoteManager(MarketplacePlugin plugin) throws IOException {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "notes.yml");
        load();
    }

    public void openMain(Player player) {
        captureBookMessage(player);
        openMainView(player);
    }

    private void openMainView(Player player) {
        Inventory inventory = Bukkit.createInventory(new MenuHolder(MenuType.NOTES), 27, "Notizen");
        inventory.setItem(4, GuiItems.button(Material.PAPER, "&eDeine Notiz",
                List.of("&7Aktuell: " + preview(getNote(player.getUniqueId())),
                        "&7Speichert deinen pers\u00f6nlichen Text")));
        inventory.setItem(18, GuiItems.button(Material.COMPASS, "&aMarktplatz", List.of("&7Zum Hauptmen\u00fc")));
        inventory.setItem(22, GuiItems.button(Material.WRITABLE_BOOK, "&eNotiz schreiben",
                List.of("&7\u00d6ffnet ein beschreibbares Buch")));
        inventory.setItem(26, GuiItems.button(Material.BARRIER, "&cNotiz l\u00f6schen",
                List.of("&7Entfernt den gespeicherten Text")));
        player.openInventory(inventory);
    }

    public void handleClick(Player player, int rawSlot) {
        switch (rawSlot) {
            case 18 -> player.performCommand("marketplace");
            case 22 -> beginMessageInput(player);
            case 26 -> {
                notes.remove(player.getUniqueId());
                save();
                MessageUtil.send(player, "Notiz gel\u00f6scht.");
                openMain(player);
            }
            default -> {
            }
        }
    }

    public boolean handleBookEdit(PlayerEditBookEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        if (!awaitingInput.getOrDefault(playerId, false)
                || !editingBookSlots.containsKey(playerId)
                || !editingBookSnapshots.containsKey(playerId)) {
            return false;
        }
        awaitingInput.put(playerId, false);
        applyNoteMessage(player, String.join("\n", event.getNewBookMeta().getPages()).trim());
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            openMainView(player);
            plugin.getServer().getScheduler().runTask(plugin, () -> restoreEditingBook(player));
        });
        return true;
    }

    public void captureBookMessage(Player player) {
        UUID playerId = player.getUniqueId();
        Integer slot = editingBookSlots.get(playerId);
        if (slot == null) {
            return;
        }
        ItemStack current = player.getInventory().getItem(slot);
        if (!isBook(current)) {
            current = player.getInventory().getItemInMainHand();
        }
        if (current != null && current.getItemMeta() instanceof BookMeta meta) {
            applyNoteMessage(player, String.join("\n", meta.getPages()).trim(), false);
        }
        awaitingInput.put(playerId, false);
        plugin.getServer().getScheduler().runTask(plugin, () -> restoreEditingBook(player));
    }

    public void shutdown() {
        for (BukkitTask task : new ArrayList<>(editWatchTasks.values())) {
            task.cancel();
        }
        editWatchTasks.clear();
        for (UUID playerId : new ArrayList<>(editingBookSlots.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                restoreEditingBook(player);
            }
        }
        save();
    }

    public void resumeNotes(Player player) {
        captureBookMessage(player);
        openMainView(player);
    }

    private String getNote(UUID playerId) {
        return notes.getOrDefault(playerId, "");
    }

    private void beginMessageInput(Player player) {
        awaitingInput.put(player.getUniqueId(), true);
        player.closeInventory();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            prepareEditableBook(player);
            startEditWatcher(player);
            player.updateInventory();
            MessageUtil.send(player, "Du hast jetzt ein beschreibbares Buch f\u00fcr deine Notiz in der Hand.");
            MessageUtil.sendActions(player, "Rechtsklick damit. Danach mit Notizen zur\u00fcck.",
                    MessageUtil.action("Notizen \u00f6ffnen", "notes"),
                    MessageUtil.action("Marktplatz", "marketplace"));
        });
    }

    private void prepareEditableBook(Player player) {
        restoreEditingBook(player);
        awaitingInput.put(player.getUniqueId(), true);
        int slot = player.getInventory().getHeldItemSlot();
        ItemStack previous = player.getInventory().getItem(slot);
        if (previous != null && !previous.getType().isAir()) {
            previousHeldItems.put(player.getUniqueId(), previous.clone());
        } else {
            previousHeldItems.remove(player.getUniqueId());
        }
        ItemStack book = createEditableBook(player.getUniqueId());
        editingBookSnapshots.put(player.getUniqueId(), book.clone());
        editingBookSlots.put(player.getUniqueId(), slot);
        player.getInventory().setItem(slot, book);
        player.updateInventory();
    }

    private ItemStack createEditableBook(UUID playerId) {
        ItemStack book = new ItemStack(Material.WRITABLE_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        String note = getNote(playerId);
        if (note.isBlank()) {
            meta.addPage("");
        } else {
            for (String page : splitPages(note)) {
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
        cancelEditWatcher(playerId);
        Integer slot = editingBookSlots.remove(playerId);
        editingBookSnapshots.remove(playerId);
        awaitingInput.remove(playerId);
        if (slot == null) {
            return;
        }
        ItemStack previous = previousHeldItems.remove(playerId);
        player.getInventory().setItem(slot, previous);
        player.updateInventory();
    }

    private void startEditWatcher(Player player) {
        UUID playerId = player.getUniqueId();
        cancelEditWatcher(playerId);
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) {
                cancelEditWatcher(playerId);
                return;
            }
            if (!awaitingInput.getOrDefault(playerId, false)) {
                cancelEditWatcher(playerId);
                return;
            }
            ItemStack current = currentEditedBook(player);
            ItemStack snapshot = editingBookSnapshots.get(playerId);
            if (current == null || snapshot == null
                    || !(current.getItemMeta() instanceof BookMeta currentMeta)
                    || !(snapshot.getItemMeta() instanceof BookMeta snapshotMeta)) {
                return;
            }
            String message = String.join("\n", currentMeta.getPages()).trim();
            String previous = String.join("\n", snapshotMeta.getPages()).trim();
            if (current.getType() != Material.WRITTEN_BOOK && message.equals(previous)) {
                return;
            }
            awaitingInput.put(playerId, false);
            applyNoteMessage(player, message);
            openMainView(player);
            plugin.getServer().getScheduler().runTask(plugin, () -> restoreEditingBook(player));
        }, 1L, 1L);
        editWatchTasks.put(playerId, task);
    }

    private void cancelEditWatcher(UUID playerId) {
        BukkitTask task = editWatchTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }

    private ItemStack currentEditedBook(Player player) {
        Integer slot = editingBookSlots.get(player.getUniqueId());
        if (slot != null) {
            ItemStack slotItem = player.getInventory().getItem(slot);
            if (isBook(slotItem)) {
                return slotItem;
            }
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (isBook(hand)) {
            return hand;
        }
        return null;
    }

    private void applyNoteMessage(Player player, String message) {
        applyNoteMessage(player, message, true);
    }

    private void applyNoteMessage(Player player, String message, boolean notifyPlayer) {
        UUID playerId = player.getUniqueId();
        if (message.isBlank()) {
            notes.remove(playerId);
            if (notifyPlayer) {
                plugin.getServer().getScheduler().runTask(plugin, () -> MessageUtil.send(player, "Notiz geleert."));
            }
        } else {
            notes.put(playerId, message);
            if (notifyPlayer) {
                plugin.getServer().getScheduler().runTask(plugin, () -> MessageUtil.send(player, "Notiz gespeichert."));
            }
        }
        save();
    }

    private boolean isBook(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        return item.getType() == Material.WRITABLE_BOOK || item.getType() == Material.WRITTEN_BOOK;
    }

    private String preview(String text) {
        if (text == null || text.isBlank()) {
            return "-";
        }
        return text.length() <= 30 ? text : text.substring(0, 30) + "...";
    }

    private void load() throws IOException {
        if (!file.exists() && !file.createNewFile()) {
            throw new IOException("notes.yml konnte nicht erstellt werden.");
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        if (yaml.getConfigurationSection("notes") == null) {
            return;
        }
        for (String key : yaml.getConfigurationSection("notes").getKeys(false)) {
            notes.put(UUID.fromString(key), yaml.getString("notes." + key, ""));
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, String> entry : notes.entrySet()) {
            yaml.set("notes." + entry.getKey(), entry.getValue());
        }
        try {
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Konnte notes.yml nicht speichern: " + exception.getMessage());
        }
    }
}
