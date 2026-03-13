package de.mcbesser.marketplace.jobs;

import de.mcbesser.marketplace.EconomyService;
import de.mcbesser.marketplace.MarketplacePlugin;
import de.mcbesser.marketplace.gui.GuiItems;
import de.mcbesser.marketplace.gui.MenuHolder;
import de.mcbesser.marketplace.gui.MenuType;
import de.mcbesser.marketplace.storage.ClaimStorage;
import de.mcbesser.marketplace.util.GermanItemNames;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class JobManager {

    private static final int MIN_ACTIVE_JOBS = 3;
    private static final int STORAGE_SIZE = 18;

    private final MarketplacePlugin plugin;
    private final EconomyService economyService;
    private final ClaimStorage claimStorage;
    private final File file;
    private final Map<UUID, PlayerJobProfile> profiles = new HashMap<>();
    private final List<JobDefinition> definitions = buildDefinitions();

    public JobManager(MarketplacePlugin plugin, EconomyService economyService, ClaimStorage claimStorage) throws IOException {
        this.plugin = plugin;
        this.economyService = economyService;
        this.claimStorage = claimStorage;
        this.file = new File(plugin.getDataFolder(), "jobs.yml");
        load();
    }

    public void openJobs(Player player) {
        ensureJobs(player.getUniqueId());
        PlayerJobProfile profile = getProfile(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(new MenuHolder(MenuType.JOBS), 36, "Jobs");
        int slot = 10;
        long now = System.currentTimeMillis();
        for (PlayerJob job : profile.getActiveJobs()) {
            JobDefinition definition = getDefinition(job.getDefinitionId());
            List<String> lore = new ArrayList<>();
            for (JobDefinition.JobRequirement requirement : definition.requirements()) {
                lore.add("\u00A77" + displayName(requirement.material()) + ": \u00A7f"
                        + progressFor(player, job, requirement.material()) + "/" + requirement.amount());
            }
            lore.add("\u00A77Belohnung: \u00A76" + (int) definition.reward() + " Coins");
            lore.add("\u00A77Restzeit: \u00A7f" + formatDuration(job.getExpiresAt() - now));
            lore.add("\u00A7aLinksklick: aus Inventar/Kiste abgeben");
            lore.add("\u00A7eRechtsklick: anpinnen/l\u00f6sen");
            lore.add("\u00A7bShift-Klick: Job-Kiste \u00f6ffnen");
            if (job.getInstanceId().equals(profile.getPinnedJobInstanceId())) {
                lore.add("\u00A76Aktuell angepinnt");
            }
            inventory.setItem(slot++, GuiItems.button(iconFor(definition), "&a" + definition.name(), lore));
        }
        inventory.setItem(31, GuiItems.button(Material.CLOCK, "&eJobsystem", List.of("&73 Jobs pro Spieler", "&71 Tag Cooldown pro Jobtyp")));
        player.openInventory(inventory);
    }

    public void handleClick(Player player, int rawSlot, ClickType clickType) {
        PlayerJobProfile profile = getProfile(player.getUniqueId());
        int index = rawSlot - 10;
        if (index < 0 || index >= profile.getActiveJobs().size()) {
            return;
        }
        PlayerJob job = profile.getActiveJobs().get(index);
        if (clickType.isShiftClick()) {
            profile.setPinnedJobInstanceId(job.getInstanceId());
            save();
            openStorage(player, job.getInstanceId());
            return;
        }
        if (clickType.isRightClick()) {
            if (job.getInstanceId().equals(profile.getPinnedJobInstanceId())) {
                profile.setPinnedJobInstanceId(null);
                player.sendMessage("Job gel\u00f6st.");
            } else {
                profile.setPinnedJobInstanceId(job.getInstanceId());
                player.sendMessage("Job angepinnt.");
            }
            save();
            openJobs(player);
            return;
        }
        deliverFromInventoryAndStorage(player, profile, job.getInstanceId());
        openJobs(player);
    }

    public void openStorage(Player player, String instanceId) {
        PlayerJob job = findJob(player.getUniqueId(), instanceId);
        if (job == null) {
            player.sendMessage("Job nicht gefunden.");
            return;
        }
        Inventory inventory = Bukkit.createInventory(new MenuHolder(MenuType.JOB_STORAGE, 0, instanceId), 27, "Job-Kiste");
        for (int i = 0; i < Math.min(STORAGE_SIZE, job.getStoredItems().size()); i++) {
            inventory.setItem(i, job.getStoredItems().get(i).clone());
        }
        JobDefinition definition = getDefinition(job.getDefinitionId());
        List<String> lore = new ArrayList<>();
        for (JobDefinition.JobRequirement requirement : definition.requirements()) {
            lore.add("\u00A77" + displayName(requirement.material()) + ": \u00A7f"
                    + progressFor(player, job, requirement.material()) + "/" + requirement.amount());
        }
        inventory.setItem(22, GuiItems.button(Material.CHEST, "&eNur passende Job-Items", lore));
        inventory.setItem(26, GuiItems.button(Material.EMERALD, "&aAbgeben", List.of("&7Lagervorrat und Inventar pruefen")));
        player.openInventory(inventory);
    }

    public void handleStorageClick(Player player, InventoryClickEvent event, String instanceId) {
        PlayerJob job = findJob(player.getUniqueId(), instanceId);
        if (job == null) {
            player.closeInventory();
            return;
        }
        if (event.getRawSlot() == 26) {
            deliverFromInventoryAndStorage(player, getProfile(player.getUniqueId()), instanceId);
            openStorage(player, instanceId);
            return;
        }
        if (event.getRawSlot() < STORAGE_SIZE) {
            takeFromStorage(player, event, job);
            syncStorageFromView(job, event.getView().getTopInventory());
            return;
        }
        if (event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            placeIntoStorage(player, event, job);
            syncStorageFromView(job, event.getView().getTopInventory());
        }
    }

    public void handleStorageClose(Player player, String instanceId, Inventory inventory) {
        PlayerJob job = findJob(player.getUniqueId(), instanceId);
        if (job == null) {
            return;
        }
        syncStorageFromView(job, inventory);
        save();
    }

    public void tick() {
        long now = System.currentTimeMillis();
        for (UUID playerId : new ArrayList<>(profiles.keySet())) {
            PlayerJobProfile profile = getProfile(playerId);
            List<PlayerJob> expired = profile.getActiveJobs().stream().filter(job -> job.getExpiresAt() <= now).toList();
            for (PlayerJob job : expired) {
                returnStoredItems(playerId, job);
                if (job.getInstanceId().equals(profile.getPinnedJobInstanceId())) {
                    profile.setPinnedJobInstanceId(null);
                }
            }
            profile.getActiveJobs().removeIf(job -> job.getExpiresAt() <= now);
            ensureJobs(playerId);
        }
        save();
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, PlayerJobProfile> entry : profiles.entrySet()) {
            String root = "players." + entry.getKey();
            yaml.set(root + ".pinned", entry.getValue().getPinnedJobInstanceId());
            int index = 0;
            for (PlayerJob job : entry.getValue().getActiveJobs()) {
                String path = root + ".jobs." + index++;
                yaml.set(path + ".instanceId", job.getInstanceId());
                yaml.set(path + ".definitionId", job.getDefinitionId());
                yaml.set(path + ".createdAt", job.getCreatedAt());
                yaml.set(path + ".expiresAt", job.getExpiresAt());
                yaml.set(path + ".delivered", job.getDelivered());
                yaml.set(path + ".storedItems", job.getStoredItems());
            }
            for (Map.Entry<String, Long> cooldown : entry.getValue().getCooldowns().entrySet()) {
                yaml.set(root + ".cooldowns." + cooldown.getKey(), cooldown.getValue());
            }
        }
        try {
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Konnte jobs.yml nicht speichern: " + exception.getMessage());
        }
    }

    public PlayerJob getPinnedJob(UUID playerId) {
        PlayerJobProfile profile = getProfile(playerId);
        if (profile.getPinnedJobInstanceId() == null) {
            return null;
        }
        return findJob(playerId, profile.getPinnedJobInstanceId());
    }

    public JobDefinition getDefinitionFor(PlayerJob job) {
        return getDefinition(job.getDefinitionId());
    }

    public int progressFor(Player player, PlayerJob job, Material material) {
        int delivered = job.getDelivered().getOrDefault(material.name(), 0);
        int stored = countStored(job, material);
        int inv = countInventory(player, material);
        return delivered + stored + inv;
    }

    public void autoStorePinnedItems(Player player) {
        PlayerJob pinned = getPinnedJob(player.getUniqueId());
        if (pinned == null) {
            return;
        }
        boolean changed = false;
        JobDefinition definition = getDefinition(pinned.getDefinitionId());
        for (JobDefinition.JobRequirement requirement : definition.requirements()) {
            int remaining = remainingAllowed(pinned, requirement.material());
            if (remaining <= 0) {
                continue;
            }
            for (ItemStack item : player.getInventory().getContents()) {
                if (item == null || item.getType() != requirement.material() || item.getAmount() <= 0) {
                    continue;
                }
                int requested = Math.min(remaining, item.getAmount());
                int accepted = addToStoredItems(pinned, copyOf(item, requested));
                if (accepted <= 0) {
                    break;
                }
                item.setAmount(item.getAmount() - accepted);
                remaining -= accepted;
                changed = true;
                if (remaining <= 0) {
                    break;
                }
            }
        }
        if (changed) {
            save();
        }
    }

    private void load() throws IOException {
        if (!file.exists() && !file.createNewFile()) {
            throw new IOException("jobs.yml konnte nicht erstellt werden.");
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection playersSection = yaml.getConfigurationSection("players");
        if (playersSection == null) {
            return;
        }
        for (String playerKey : playersSection.getKeys(false)) {
            UUID playerId = UUID.fromString(playerKey);
            PlayerJobProfile profile = new PlayerJobProfile();
            profile.setPinnedJobInstanceId(yaml.getString("players." + playerKey + ".pinned"));
            ConfigurationSection jobsSection = yaml.getConfigurationSection("players." + playerKey + ".jobs");
            if (jobsSection != null) {
                for (String key : jobsSection.getKeys(false)) {
                    String base = "players." + playerKey + ".jobs." + key;
                    PlayerJob job = new PlayerJob(
                            yaml.getString(base + ".instanceId"),
                            yaml.getString(base + ".definitionId"),
                            yaml.getLong(base + ".createdAt"),
                            yaml.getLong(base + ".expiresAt")
                    );
                    ConfigurationSection delivered = yaml.getConfigurationSection(base + ".delivered");
                    if (delivered != null) {
                        for (String material : delivered.getKeys(false)) {
                            job.getDelivered().put(material, delivered.getInt(material));
                        }
                    }
                    List<?> stored = yaml.getList(base + ".storedItems");
                    if (stored != null) {
                        for (Object entry : stored) {
                            if (entry instanceof ItemStack item) {
                                job.getStoredItems().add(item);
                            }
                        }
                    }
                    profile.getActiveJobs().add(job);
                }
            }
            ConfigurationSection cooldowns = yaml.getConfigurationSection("players." + playerKey + ".cooldowns");
            if (cooldowns != null) {
                for (String key : cooldowns.getKeys(false)) {
                    profile.getCooldowns().put(key, cooldowns.getLong(key));
                }
            }
            profiles.put(playerId, profile);
        }
    }

    private void deliverFromInventoryAndStorage(Player player, PlayerJobProfile profile, String instanceId) {
        PlayerJob job = findJob(player.getUniqueId(), instanceId);
        if (job == null) {
            player.sendMessage("Job nicht gefunden.");
            return;
        }
        JobDefinition definition = getDefinition(job.getDefinitionId());
        int deliveredAny = 0;

        for (JobDefinition.JobRequirement requirement : definition.requirements()) {
            int delivered = job.getDelivered().getOrDefault(requirement.material().name(), 0);
            int missing = requirement.amount() - delivered;
            if (missing <= 0) {
                continue;
            }
            int fromStorage = removeStored(job, requirement.material(), missing);
            if (fromStorage > 0) {
                job.getDelivered().put(requirement.material().name(), delivered + fromStorage);
                deliveredAny += fromStorage;
                delivered += fromStorage;
                missing -= fromStorage;
            }
            if (missing > 0) {
                int removed = removeMaterial(player.getInventory().getContents(), requirement.material(), missing);
                if (removed > 0) {
                    job.getDelivered().put(requirement.material().name(), delivered + removed);
                    deliveredAny += removed;
                }
            }
        }

        if (deliveredAny == 0) {
            player.sendMessage("Keine passenden Items f\u00fcr diesen Job gefunden.");
            return;
        }
        if (isCompleted(job, definition)) {
            economyService.deposit(player.getUniqueId(), definition.reward());
            profile.getActiveJobs().remove(job);
            if (job.getInstanceId().equals(profile.getPinnedJobInstanceId())) {
                profile.setPinnedJobInstanceId(null);
            }
            profile.getCooldowns().put(definition.id(), System.currentTimeMillis() + Duration.ofDays(1).toMillis());
            ensureJobs(player.getUniqueId());
            player.sendMessage("Job abgeschlossen. " + (int) definition.reward() + " Coins erhalten.");
        } else {
            player.sendMessage("Fortschritt aktualisiert.");
        }
        save();
    }

    private void takeFromStorage(Player player, InventoryClickEvent event, PlayerJob job) {
        ItemStack item = event.getView().getTopInventory().getItem(event.getRawSlot());
        if (item == null || item.getType().isAir()) {
            return;
        }
        Map<Integer, ItemStack> rest = player.getInventory().addItem(item.clone());
        if (!rest.isEmpty()) {
            player.sendMessage("Inventar voll.");
            return;
        }
        event.getView().getTopInventory().setItem(event.getRawSlot(), null);
    }

    private void placeIntoStorage(Player player, InventoryClickEvent event, PlayerJob job) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }
        int allowed = remainingAllowed(job, clicked.getType());
        if (allowed <= 0) {
            player.sendMessage("Dieses Item wird f\u00fcr den Job nicht mehr ben\u00f6tigt.");
            return;
        }
        int amount = Math.min(allowed, clicked.getAmount());
        ItemStack transfer = clicked.clone();
        transfer.setAmount(amount);
        int accepted = addToStorage(event.getView().getTopInventory(), transfer);
        if (accepted <= 0) {
            player.sendMessage("Job-Kiste ist voll.");
            return;
        }
        if (clicked.getAmount() == accepted) {
            event.setCurrentItem(null);
        } else {
            clicked.setAmount(clicked.getAmount() - accepted);
            event.setCurrentItem(clicked);
        }
    }

    private int addToStorage(Inventory inventory, ItemStack transfer) {
        int remaining = transfer.getAmount();
        for (int i = 0; i < STORAGE_SIZE; i++) {
            ItemStack slot = inventory.getItem(i);
            if (slot == null || slot.getType().isAir()) {
                ItemStack placed = transfer.clone();
                placed.setAmount(remaining);
                inventory.setItem(i, placed);
                return transfer.getAmount();
            }
            if (slot.isSimilar(transfer) && slot.getAmount() < slot.getMaxStackSize()) {
                int move = Math.min(remaining, slot.getMaxStackSize() - slot.getAmount());
                slot.setAmount(slot.getAmount() + move);
                remaining -= move;
                if (remaining <= 0) {
                    return transfer.getAmount();
                }
            }
        }
        return transfer.getAmount() - remaining;
    }

    private int addToStoredItems(PlayerJob job, ItemStack transfer) {
        int remaining = transfer.getAmount();
        for (ItemStack stored : job.getStoredItems()) {
            if (stored.isSimilar(transfer) && stored.getAmount() < stored.getMaxStackSize()) {
                int move = Math.min(remaining, stored.getMaxStackSize() - stored.getAmount());
                stored.setAmount(stored.getAmount() + move);
                remaining -= move;
                if (remaining <= 0) {
                    return transfer.getAmount();
                }
            }
        }
        while (remaining > 0 && job.getStoredItems().size() < STORAGE_SIZE) {
            ItemStack next = transfer.clone();
            next.setAmount(Math.min(remaining, next.getMaxStackSize()));
            job.getStoredItems().add(next);
            remaining -= next.getAmount();
        }
        return transfer.getAmount() - remaining;
    }

    private void syncStorageFromView(PlayerJob job, Inventory inventory) {
        job.getStoredItems().clear();
        for (int i = 0; i < STORAGE_SIZE; i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && !item.getType().isAir()) {
                job.getStoredItems().add(item.clone());
            }
        }
        save();
    }

    private int remainingAllowed(PlayerJob job, Material material) {
        JobDefinition definition = getDefinition(job.getDefinitionId());
        JobDefinition.JobRequirement req = definition.requirements().stream()
                .filter(entry -> entry.material() == material)
                .findFirst()
                .orElse(null);
        if (req == null) {
            return 0;
        }
        int delivered = job.getDelivered().getOrDefault(material.name(), 0);
        int stored = countStored(job, material);
        return Math.max(0, req.amount() - delivered - stored);
    }

    private int countStored(PlayerJob job, Material material) {
        return job.getStoredItems().stream()
                .filter(item -> item.getType() == material)
                .mapToInt(ItemStack::getAmount)
                .sum();
    }

    private int countInventory(Player player, Material material) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private int removeStored(PlayerJob job, Material material, int amount) {
        int removed = 0;
        List<ItemStack> items = new ArrayList<>(job.getStoredItems());
        for (ItemStack item : items) {
            if (item.getType() != material) {
                continue;
            }
            int take = Math.min(amount - removed, item.getAmount());
            item.setAmount(item.getAmount() - take);
            removed += take;
            if (item.getAmount() <= 0) {
                job.getStoredItems().remove(item);
            }
            if (removed >= amount) {
                break;
            }
        }
        return removed;
    }

    private int removeMaterial(ItemStack[] contents, Material material, int amount) {
        int removed = 0;
        for (ItemStack item : contents) {
            if (item == null || item.getType() != material) {
                continue;
            }
            int take = Math.min(item.getAmount(), amount - removed);
            item.setAmount(item.getAmount() - take);
            removed += take;
            if (removed >= amount) {
                break;
            }
        }
        return removed;
    }

    private boolean isCompleted(PlayerJob job, JobDefinition definition) {
        return definition.requirements().stream()
                .allMatch(req -> job.getDelivered().getOrDefault(req.material().name(), 0) >= req.amount());
    }

    private void returnStoredItems(UUID playerId, PlayerJob job) {
        for (ItemStack item : job.getStoredItems()) {
            claimStorage.addClaim(playerId, item, "Job-Kiste R\u00fcckgabe", 0, "Job " + job.getInstanceId() + " ist abgelaufen");
        }
        job.getStoredItems().clear();
    }

    private PlayerJobProfile getProfile(UUID playerId) {
        return profiles.computeIfAbsent(playerId, ignored -> new PlayerJobProfile());
    }

    private PlayerJob findJob(UUID playerId, String instanceId) {
        return getProfile(playerId).getActiveJobs().stream()
                .filter(candidate -> candidate.getInstanceId().equalsIgnoreCase(instanceId))
                .findFirst()
                .orElse(null);
    }

    private void ensureJobs(UUID playerId) {
        PlayerJobProfile profile = getProfile(playerId);
        long now = System.currentTimeMillis();
        while (profile.getActiveJobs().size() < MIN_ACTIVE_JOBS) {
            List<JobDefinition> candidates = definitions.stream()
                    .filter(definition -> profile.getActiveJobs().stream().noneMatch(job -> job.getDefinitionId().equals(definition.id())))
                    .filter(definition -> profile.getCooldowns().getOrDefault(definition.id(), 0L) <= now)
                    .toList();
            if (candidates.isEmpty()) {
                break;
            }
            JobDefinition chosen = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
            String instanceId = chosen.id().substring(0, Math.min(3, chosen.id().length())).toUpperCase()
                    + (100 + ThreadLocalRandom.current().nextInt(900));
            profile.getActiveJobs().add(new PlayerJob(instanceId, chosen.id(), now,
                    now + Duration.ofMinutes(chosen.durationMinutes()).toMillis()));
        }
    }

    private JobDefinition getDefinition(String definitionId) {
        return definitions.stream().filter(def -> def.id().equals(definitionId)).findFirst().orElseThrow();
    }

    private Material iconFor(JobDefinition definition) {
        return definition.requirements().get(0).material();
    }

    private List<JobDefinition> buildDefinitions() {
        List<JobDefinition> list = new ArrayList<>();
        list.add(new JobDefinition("farmer_mix", "Bauerngut", List.of(
                new JobDefinition.JobRequirement(Material.WHEAT, 160),
                new JobDefinition.JobRequirement(Material.CARROT, 96)
        ), 12, 180, 1440));
        list.add(new JobDefinition("egg_supply", "Frische Eier", List.of(
                new JobDefinition.JobRequirement(Material.EGG, 96)
        ), 8, 120, 1440));
        list.add(new JobDefinition("melon_route", "Sommerladung", List.of(
                new JobDefinition.JobRequirement(Material.MELON_SLICE, 192),
                new JobDefinition.JobRequirement(Material.PUMPKIN, 48)
        ), 18, 240, 1440));
        list.add(new JobDefinition("animal_bundle", "Hoflieferung", List.of(
                new JobDefinition.JobRequirement(Material.LEATHER, 48),
                new JobDefinition.JobRequirement(Material.WHITE_WOOL, 96),
                new JobDefinition.JobRequirement(Material.BEEF, 64)
        ), 24, 300, 1440));
        list.add(new JobDefinition("sweet_stock", "S\u00fc\u00dfer Vorrat", List.of(
                new JobDefinition.JobRequirement(Material.HONEY_BOTTLE, 24),
                new JobDefinition.JobRequirement(Material.SUGAR_CANE, 192)
        ), 20, 240, 1440));
        return list;
    }

    private String formatDuration(long millis) {
        long totalMinutes = Math.max(0, millis / 60000L);
        return (totalMinutes / 60) + "h " + (totalMinutes % 60) + "m";
    }

    public String displayName(Material material) {
        return GermanItemNames.of(material);
    }

    public String fallbackName(Material material) {
        return GermanItemNames.of(material);
    }

    private ItemStack copyOf(ItemStack item, int amount) {
        ItemStack copy = item.clone();
        copy.setAmount(amount);
        return copy;
    }
}


