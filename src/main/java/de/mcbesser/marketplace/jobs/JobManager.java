package de.mcbesser.marketplace.jobs;

import de.mcbesser.marketplace.EconomyService;
import de.mcbesser.marketplace.MarketplacePlugin;
import de.mcbesser.marketplace.pricing.PriceGuideManager;
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
    private static final int[] JOB_CREATE_ITEM_SLOTS = {11, 12, 13, 14, 15};

    private final MarketplacePlugin plugin;
    private final EconomyService economyService;
    private final ClaimStorage claimStorage;
    private final PriceGuideManager priceGuideManager;
    private final File file;
    private final Map<UUID, PlayerJobProfile> profiles = new HashMap<>();
    private final Map<UUID, JobCreateState> createStates = new HashMap<>();
    private final List<PlayerJob> publicJobs = new ArrayList<>();
    private final List<JobDefinition> definitions = buildDefinitions();

    public JobManager(MarketplacePlugin plugin, EconomyService economyService, ClaimStorage claimStorage,
                      PriceGuideManager priceGuideManager) throws IOException {
        this.plugin = plugin;
        this.economyService = economyService;
        this.claimStorage = claimStorage;
        this.priceGuideManager = priceGuideManager;
        this.file = new File(plugin.getDataFolder(), "jobs.yml");
        load();
    }

    public void openJobs(Player player) {
        openJobs(player, 0);
    }

    public void openJobs(Player player, int page) {
        ensureJobs(player.getUniqueId());
        List<PlayerJob> jobs = visibleJobs(player.getUniqueId());
        int maxPage = jobs.isEmpty() ? 0 : (jobs.size() - 1) / 45;
        int safePage = Math.max(0, Math.min(page, maxPage));
        Inventory inventory = Bukkit.createInventory(new MenuHolder(MenuType.JOBS, safePage, ""), 54, "Jobs");

        int start = safePage * 45;
        long now = System.currentTimeMillis();
        for (int slot = 0; slot < 45; slot++) {
            int index = start + slot;
            if (index >= jobs.size()) {
                break;
            }
            inventory.setItem(slot, createJobDisplay(player, jobs.get(index), now));
        }

        inventory.setItem(45, GuiItems.button(Material.COMPASS, "&aMarketplace", List.of("&7Zum Hauptmen\u00fc")));
        inventory.setItem(46, GuiItems.button(Material.ARROW, "&eZurueck", List.of("&7Vorherige Seite")));
        inventory.setItem(49, GuiItems.button(Material.WRITABLE_BOOK, "&aSpieler-Job erstellen",
                List.of("&7Materialliste und Belohnung festlegen", "&7Belohnung wird direkt reserviert")));
        inventory.setItem(53, GuiItems.button(Material.ARROW, "&eWeiter", List.of("&7Naechste Seite")));
        player.openInventory(inventory);
    }

    public void openCreateMenu(Player player) {
        JobCreateState state = createStates.computeIfAbsent(player.getUniqueId(),
                ignored -> new JobCreateState(defaultRewardFor(player, null)));
        renderCreateMenu(player, state);
    }

    public void handleClick(Player player, InventoryClickEvent event, int page) {
        if (event.getRawSlot() == 45) {
            player.performCommand("marketplace");
            return;
        }
        if (event.getRawSlot() == 46 && page > 0) {
            openJobs(player, page - 1);
            return;
        }
        if (event.getRawSlot() == 49) {
            openCreateMenu(player);
            return;
        }
        if (event.getRawSlot() == 53 && ((page + 1) * 45) < visibleJobs(player.getUniqueId()).size()) {
            openJobs(player, page + 1);
            return;
        }
        if (event.getRawSlot() < 0 || event.getRawSlot() >= 45) {
            return;
        }

        List<PlayerJob> jobs = visibleJobs(player.getUniqueId());
        int index = page * 45 + event.getRawSlot();
        if (index >= jobs.size()) {
            return;
        }

        PlayerJob job = jobs.get(index);
        if (job.isCustom()) {
            handleCustomJobClick(player, job, event.getClick(), page);
            return;
        }
        handlePersonalJobClick(player, job, event.getClick(), page);
    }

    public void handleCreateClick(Player player, InventoryClickEvent event) {
        JobCreateState state = createStates.computeIfAbsent(player.getUniqueId(),
                ignored -> new JobCreateState(defaultRewardFor(player, null)));

        if (event.getRawSlot() == 22 || event.getRawSlot() == 31) {
            adjustSelectedAmount(state, event.getClick().isRightClick() ? -1 : 1);
            renderCreateMenu(player, state);
            return;
        }

        if (handleDraftItemPlacement(event, state)) {
            applySuggestedReward(player, state);
            renderCreateMenu(player, state);
            return;
        }

        int rawSlot = event.getRawSlot();
        int itemIndex = indexOfCreateSlot(rawSlot);
        if (itemIndex >= 0) {
            if (itemIndex < state.getItems().size()) {
                if (event.getClick().isLeftClick() || event.getClick().isRightClick()) {
                    state.setSelectedIndex(itemIndex);
                    adjustSelectedAmount(state, event.getClick().isRightClick() ? -1 : 1);
                } else if (event.getClick().isShiftClick() && (event.getCursor() == null || event.getCursor().getType().isAir())) {
                    state.getItems().remove(itemIndex);
                    if (state.getSelectedIndex() >= state.getItems().size()) {
                        state.setSelectedIndex(Math.max(0, state.getItems().size() - 1));
                    }
                    applySuggestedReward(player, state);
                } else {
                    state.setSelectedIndex(itemIndex);
                }
            }
            renderCreateMenu(player, state);
            return;
        }

        switch (rawSlot) {
            case 23 -> applySuggestedReward(player, state);
            case 28 -> adjustSelectedAmount(state, -10000);
            case 29 -> adjustSelectedAmount(state, -1000);
            case 30 -> adjustSelectedAmount(state, -100);
            case 31 -> adjustSelectedAmount(state, -10);
            case 32 -> adjustSelectedAmount(state, event.getClick().isRightClick() ? -1 : 1);
            case 33 -> adjustSelectedAmount(state, 10);
            case 34 -> adjustSelectedAmount(state, 100);
            case 35 -> adjustSelectedAmount(state, 1000);
            case 36 -> adjustSelectedAmount(state, 10000);
            case 44 -> {
                createPublicJob(player, state);
                return;
            }
            case 45 -> {
                player.performCommand("marketplace");
                return;
            }
            case 46 -> openJobs(player);
            case 47 -> adjustReward(state, -1000, player);
            case 48 -> adjustReward(state, -100, player);
            case 49 -> adjustReward(state, -10, player);
            case 50 -> {
                if (event.getClick().isShiftClick()) {
                    applySuggestedReward(player, state);
                } else {
                    adjustReward(state, event.getClick().isRightClick() ? -1 : 1, player);
                }
            }
            case 51 -> adjustReward(state, 10, player);
            case 52 -> adjustReward(state, 100, player);
            case 53 -> adjustReward(state, 1000, player);
            default -> {
                return;
            }
        }
        renderCreateMenu(player, state);
    }

    public void openStorage(Player player, String instanceId) {
        PlayerJob job = findPersonalJob(player.getUniqueId(), instanceId);
        if (job == null) {
            player.sendMessage("Job nicht gefunden.");
            return;
        }
        Inventory inventory = Bukkit.createInventory(new MenuHolder(MenuType.JOB_STORAGE, 0, instanceId), 27, "Job-Kiste");
        for (int i = 0; i < Math.min(STORAGE_SIZE, job.getStoredItems().size()); i++) {
            inventory.setItem(i, job.getStoredItems().get(i).clone());
        }
        List<String> lore = new ArrayList<>();
        for (JobDefinition.JobRequirement requirement : requirementsFor(job)) {
            lore.add("\u00A77" + displayName(requirement.material()) + ": \u00A7f"
                    + progressFor(player, job, requirement.material()) + "/" + requirement.amount());
        }
        inventory.setItem(18, GuiItems.button(Material.COMPASS, "&aMarketplace", List.of("&7Zum Hauptmen\u00fc")));
        inventory.setItem(22, GuiItems.button(Material.CHEST, "&eNur passende Job-Items", lore));
        inventory.setItem(24, GuiItems.button(Material.ARROW, "&eZur Jobliste", List.of("&7Zur\u00fcck ohne Abgabe")));
        inventory.setItem(26, GuiItems.button(Material.EMERALD, "&aAbgeben", List.of("&7Lagervorrat und Inventar pruefen")));
        player.openInventory(inventory);
    }

    public void handleStorageClick(Player player, InventoryClickEvent event, String instanceId) {
        PlayerJob job = findPersonalJob(player.getUniqueId(), instanceId);
        if (job == null) {
            player.closeInventory();
            return;
        }
        if (event.getRawSlot() == 18) {
            player.performCommand("marketplace");
            return;
        }
        if (event.getRawSlot() == 24) {
            openJobs(player);
            return;
        }
        if (event.getRawSlot() == 26) {
            deliverFromInventoryAndStorage(player, getProfile(player.getUniqueId()), job, true);
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
        PlayerJob job = findPersonalJob(player.getUniqueId(), instanceId);
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
                clearPinned(job.getInstanceId());
            }
            profile.getActiveJobs().removeIf(job -> job.getExpiresAt() <= now);
            ensureJobs(playerId);
        }

        List<PlayerJob> expiredPublic = publicJobs.stream().filter(job -> job.getExpiresAt() <= now).toList();
        for (PlayerJob job : expiredPublic) {
            if (job.getCreatorId() != null) {
                economyService.deposit(job.getCreatorId(), job.getRewardOverride());
                Player creator = Bukkit.getPlayer(job.getCreatorId());
                if (creator != null) {
                    creator.sendMessage("Dein Spieler-Job " + nameFor(job) + " ist abgelaufen. Budget zurueckerstattet.");
                }
            }
            clearPinned(job.getInstanceId());
        }
        publicJobs.removeIf(job -> job.getExpiresAt() <= now);
        save();
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, PlayerJobProfile> entry : profiles.entrySet()) {
            String root = "players." + entry.getKey();
            yaml.set(root + ".pinned", entry.getValue().getPinnedJobInstanceId());
            int index = 0;
            for (PlayerJob job : entry.getValue().getActiveJobs()) {
                saveJob(yaml, root + ".jobs." + index++, job);
            }
            for (Map.Entry<String, Long> cooldown : entry.getValue().getCooldowns().entrySet()) {
                yaml.set(root + ".cooldowns." + cooldown.getKey(), cooldown.getValue());
            }
        }
        for (int i = 0; i < publicJobs.size(); i++) {
            saveJob(yaml, "publicJobs." + i, publicJobs.get(i));
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

    public List<JobDefinition.JobRequirement> requirementsFor(PlayerJob job) {
        return job.isCustom() ? job.getCustomRequirements() : getDefinition(job.getDefinitionId()).requirements();
    }

    public String nameFor(PlayerJob job) {
        return job.isCustom() ? job.getCustomName() : getDefinition(job.getDefinitionId()).name();
    }

    public double rewardFor(PlayerJob job) {
        return job.isCustom() ? job.getRewardOverride() : getDefinition(job.getDefinitionId()).reward();
    }

    public int progressFor(Player player, PlayerJob job, Material material) {
        int delivered = job.getDelivered().getOrDefault(material.name(), 0);
        int stored = job.isCustom() ? 0 : countStored(job, material);
        int inv = countInventory(player, material);
        return delivered + stored + inv;
    }

    public void autoStorePinnedItems(Player player) {
        PlayerJob pinned = getPinnedJob(player.getUniqueId());
        if (pinned == null || pinned.isCustom()) {
            return;
        }
        boolean changed = false;
        for (JobDefinition.JobRequirement requirement : requirementsFor(pinned)) {
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
        if (playersSection != null) {
            for (String playerKey : playersSection.getKeys(false)) {
                UUID playerId = UUID.fromString(playerKey);
                PlayerJobProfile profile = new PlayerJobProfile();
                profile.setPinnedJobInstanceId(yaml.getString("players." + playerKey + ".pinned"));

                ConfigurationSection jobsSection = yaml.getConfigurationSection("players." + playerKey + ".jobs");
                if (jobsSection != null) {
                    for (String key : jobsSection.getKeys(false)) {
                        PlayerJob job = loadJob(yaml, "players." + playerKey + ".jobs." + key);
                        if (job != null) {
                            profile.getActiveJobs().add(job);
                        }
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

        ConfigurationSection publicSection = yaml.getConfigurationSection("publicJobs");
        if (publicSection != null) {
            for (String key : publicSection.getKeys(false)) {
                PlayerJob job = loadJob(yaml, "publicJobs." + key);
                if (job != null) {
                    publicJobs.add(job);
                }
            }
        }
    }

    private void handlePersonalJobClick(Player player, PlayerJob job, ClickType clickType, int page) {
        PlayerJobProfile profile = getProfile(player.getUniqueId());
        if (clickType.isShiftClick()) {
            profile.setPinnedJobInstanceId(job.getInstanceId());
            save();
            openStorage(player, job.getInstanceId());
            return;
        }
        if (clickType.isRightClick()) {
            togglePinned(player, profile, job);
            openJobs(player, page);
            return;
        }
        deliverFromInventoryAndStorage(player, profile, job, true);
        openJobs(player, page);
    }

    private void handleCustomJobClick(Player player, PlayerJob job, ClickType clickType, int page) {
        if (clickType.isShiftClick()) {
            player.sendMessage("Spieler-Jobs haben keine Job-Kiste.");
            return;
        }
        if (clickType.isRightClick()) {
            togglePinned(player, getProfile(player.getUniqueId()), job);
            openJobs(player, page);
            return;
        }
        deliverPublicJob(player, job);
        openJobs(player, page);
    }

    private void togglePinned(Player player, PlayerJobProfile profile, PlayerJob job) {
        if (job.getInstanceId().equals(profile.getPinnedJobInstanceId())) {
            profile.setPinnedJobInstanceId(null);
            player.sendMessage("Job geloest.");
        } else {
            profile.setPinnedJobInstanceId(job.getInstanceId());
            player.sendMessage("Job angepinnt.");
        }
        save();
    }

    private void renderCreateMenu(Player player, JobCreateState state) {
        Inventory inventory = Bukkit.createInventory(new MenuHolder(MenuType.JOB_CREATE), 54, "Spieler-Job");
        for (int slot : JOB_CREATE_ITEM_SLOTS) {
            inventory.setItem(slot, GuiItems.button(Material.GREEN_STAINED_GLASS_PANE, "&aMaterial ziehen",
                    List.of("&7Lege bis zu 5 Items fest", "&7Rechtsklick entfernt den Eintrag")));
        }
        for (int i = 0; i < state.getItems().size() && i < JOB_CREATE_ITEM_SLOTS.length; i++) {
            JobCreateState.JobDraftItem draftItem = state.getItems().get(i);
            List<String> lore = new ArrayList<>();
            lore.add("&7Menge: &f" + draftItem.amount());
            lore.add("&7Klick zum Auswaehlen");
            lore.add(i == state.getSelectedIndex() ? "&aAktiv" : "&7Nicht aktiv");
            inventory.setItem(JOB_CREATE_ITEM_SLOTS[i], GuiItems.button(draftItem.material(), "&a" + displayName(draftItem.material()), lore));
        }

        JobCreateState.JobDraftItem selected = state.ensureSelected();
        double suggestedReward = suggestedReward(player, state);
        inventory.setItem(22, selected == null
                ? GuiItems.button(Material.BOOK, "&eMaterialliste", List.of("&7Lege zuerst ein Item auf das grune Board"))
                : GuiItems.button(selected.material(), "&eAusgewaehlt: " + displayName(selected.material()),
                List.of("&7Menge: &f" + selected.amount(), "&7Links +1 | Rechts -1")));
        inventory.setItem(23, GuiItems.button(Material.PAPER, "&eMarktpreis",
                List.of("&7Vorschlag: " + CurrencyFormatter.shortAmount(suggestedReward),
                        "&7Basierend auf vorhandenen Richtwerten")));
        inventory.setItem(24, GuiItems.button(Material.CHEST, "&eMaterialien: " + state.getItems().size() + "/5",
                List.of("&7Belohnung anpassbar", "&7Budget wird sofort reserviert")));

        inventory.setItem(27, stepButton("-10000"));
        inventory.setItem(28, stepButton("-1000"));
        inventory.setItem(29, stepButton("-100"));
        inventory.setItem(30, stepButton("-10"));
        inventory.setItem(31, GuiItems.button(Material.HOPPER, "&6Mengensteuerung",
                List.of("&7Links +1 | Rechts -1", "&7Grosse Schritte links und rechts")));
        inventory.setItem(32, stepButton("+10"));
        inventory.setItem(33, stepButton("+100"));
        inventory.setItem(34, stepButton("+1000"));
        inventory.setItem(35, stepButton("+10000"));

        inventory.setItem(44, GuiItems.button(Material.EMERALD_BLOCK, "&aJob erstellen",
                List.of("&7Sichtbar fuer alle Spieler", "&7Belohnung: " + CurrencyFormatter.shortAmount(state.getReward()),
                        "&7Budget wird sofort abgezogen")));
        inventory.setItem(45, GuiItems.button(Material.COMPASS, "&aMarketplace", List.of("&7Zum Hauptmen\u00fc")));
        inventory.setItem(46, GuiItems.button(Material.ARROW, "&eZurueck", List.of("&7Zur Jobliste")));
        inventory.setItem(47, stepButton("-1000 CT"));
        inventory.setItem(48, stepButton("-100 CT"));
        inventory.setItem(49, stepButton("-10 CT"));
        inventory.setItem(50, GuiItems.button(Material.SUNFLOWER, "&6Budget: " + CurrencyFormatter.shortAmount(state.getReward()),
                List.of("&7Links +1 | Rechts -1", "&7Shift-Klick: Marktpreis uebernehmen",
                        "&7Maximal verfuegbar: " + CurrencyFormatter.shortAmount(economyService.getBalance(player.getUniqueId())))));
        inventory.setItem(51, stepButton("+10 CT"));
        inventory.setItem(52, stepButton("+100 CT"));
        inventory.setItem(53, stepButton("+1000 CT"));
        player.openInventory(inventory);
    }

    private ItemStack createJobDisplay(Player player, PlayerJob job, long now) {
        List<String> lore = new ArrayList<>();
        for (JobDefinition.JobRequirement requirement : requirementsFor(job)) {
            lore.add("\u00A77" + displayName(requirement.material()) + ": \u00A7f"
                    + progressFor(player, job, requirement.material()) + "/" + requirement.amount());
        }
        lore.add("\u00A77Belohnung: \u00A76" + CurrencyFormatter.shortAmount(rewardFor(job)));
        lore.add("\u00A77Restzeit: \u00A7f" + formatDuration(job.getExpiresAt() - now));
        if (job.isCustom()) {
            lore.add("\u00A77Ersteller: \u00A7f" + job.getCreatorName());
            lore.add("\u00A7aLinksklick: aus Inventar abgeben");
            lore.add("\u00A7eRechtsklick: anpinnen/loesen");
        } else {
            lore.add("\u00A7aLinksklick: aus Inventar/Kiste abgeben");
            lore.add("\u00A7eRechtsklick: anpinnen/loesen");
            lore.add("\u00A7bShift-Klick: Job-Kiste oeffnen");
        }
        if (job.getInstanceId().equals(getProfile(player.getUniqueId()).getPinnedJobInstanceId())) {
            lore.add("\u00A76Aktuell angepinnt");
        }
        return GuiItems.button(iconFor(job), "&a" + nameFor(job), lore);
    }

    private boolean handleDraftItemPlacement(InventoryClickEvent event, JobCreateState state) {
        int rawSlot = event.getRawSlot();
        int targetIndex = indexOfCreateSlot(rawSlot);
        if (targetIndex >= 0) {
            ItemStack cursor = event.getCursor();
            if (cursor != null && !cursor.getType().isAir()) {
                int existing = state.findByMaterial(cursor.getType());
                if (existing >= 0) {
                    state.setSelectedIndex(existing);
                    return true;
                }
                if (targetIndex > state.getItems().size()) {
                    targetIndex = state.getItems().size();
                }
                if (state.getItems().size() >= JOB_CREATE_ITEM_SLOTS.length && targetIndex >= state.getItems().size()) {
                    return false;
                }
                JobCreateState.JobDraftItem next = new JobCreateState.JobDraftItem(cursor.getType(), Math.max(1, cursor.getAmount()));
                if (targetIndex < state.getItems().size()) {
                    state.getItems().set(targetIndex, next);
                } else if (state.getItems().size() < JOB_CREATE_ITEM_SLOTS.length) {
                    state.getItems().add(next);
                }
                state.setSelectedIndex(Math.min(targetIndex, state.getItems().size() - 1));
                return true;
            }
            return false;
        }

        if (rawSlot >= event.getView().getTopInventory().getSize()) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType().isAir()) {
                return false;
            }
            int existing = state.findByMaterial(clicked.getType());
            if (existing >= 0) {
                state.setSelectedIndex(existing);
                return true;
            }
            if (state.getItems().size() >= JOB_CREATE_ITEM_SLOTS.length) {
                return false;
            }
            state.getItems().add(new JobCreateState.JobDraftItem(clicked.getType(), clicked.getAmount()));
            state.setSelectedIndex(state.getItems().size() - 1);
            return true;
        }
        return false;
    }

    private void adjustSelectedAmount(JobCreateState state, int delta) {
        JobCreateState.JobDraftItem selected = state.ensureSelected();
        if (selected == null) {
            return;
        }
        int next = Math.max(1, selected.amount() + delta);
        state.getItems().set(state.getSelectedIndex(), selected.withAmount(next));
    }

    private void adjustReward(JobCreateState state, int delta, Player player) {
        double next = Math.max(1, state.getReward() + delta);
        double balance = economyService.getBalance(player.getUniqueId());
        state.setReward(Math.min(next, Math.max(1, (int) balance)));
    }

    private void applySuggestedReward(Player player, JobCreateState state) {
        state.setReward(suggestedReward(player, state));
    }

    private double suggestedReward(Player player, JobCreateState state) {
        if (state == null || state.getItems().isEmpty()) {
            return defaultRewardFor(player, null);
        }
        double total = 0;
        boolean foundReference = false;
        for (JobCreateState.JobDraftItem item : state.getItems()) {
            var reference = priceGuideManager.getReferencePrice(new ItemStack(item.material()));
            if (reference.isPresent()) {
                total += reference.getAsDouble() * item.amount();
                foundReference = true;
            }
        }
        if (!foundReference) {
            return defaultRewardFor(player, state);
        }
        return clampReward(player, Math.max(1, Math.round(total)));
    }

    private double defaultRewardFor(Player player, JobCreateState state) {
        double balance = economyService.getBalance(player.getUniqueId());
        if (state != null && !state.getItems().isEmpty()) {
            return clampReward(player, 1);
        }
        return clampReward(player, Math.min(100, Math.max(1, Math.floor(balance))));
    }

    private double clampReward(Player player, double reward) {
        double balance = economyService.getBalance(player.getUniqueId());
        if (balance <= 0) {
            return 1;
        }
        return Math.max(1, Math.min(reward, Math.floor(balance)));
    }

    private void createPublicJob(Player player, JobCreateState state) {
        if (state.getItems().isEmpty()) {
            player.sendMessage("Lege mindestens ein Material fuer den Job fest.");
            return;
        }
        double reward = Math.max(1, state.getReward());
        if (economyService.getBalance(player.getUniqueId()) < reward || !economyService.withdraw(player.getUniqueId(), reward)) {
            player.sendMessage("Du hast nicht genug CraftTaler fuer dieses Budget.");
            renderCreateMenu(player, state);
            return;
        }

        long now = System.currentTimeMillis();
        PlayerJob job = new PlayerJob(nextCustomInstanceId(), null, now, now + Duration.ofDays(1).toMillis(),
                player.getUniqueId(), player.getName(), "Spielerauftrag von " + player.getName(), reward);
        for (JobCreateState.JobDraftItem item : state.getItems()) {
            job.getCustomRequirements().add(new JobDefinition.JobRequirement(item.material(), item.amount()));
        }
        publicJobs.add(job);
        save();

        createStates.put(player.getUniqueId(),
                new JobCreateState(defaultRewardFor(player, null)));
        player.sendMessage("Spieler-Job erstellt. " + CurrencyFormatter.shortAmount(reward) + " wurden reserviert.");
        openJobs(player);
    }

    private void deliverPublicJob(Player player, PlayerJob job) {
        int deliveredAny = deliverMaterials(player, job, false);
        if (deliveredAny == 0) {
            player.sendMessage("Keine passenden Items fuer diesen Job gefunden.");
            return;
        }
        if (isCompleted(job)) {
            economyService.deposit(player.getUniqueId(), job.getRewardOverride());
            publicJobs.remove(job);
            clearPinned(job.getInstanceId());
            player.sendMessage("Spieler-Job abgeschlossen. " + CurrencyFormatter.shortAmount(job.getRewardOverride()) + " erhalten.");
            Player creator = Bukkit.getPlayer(job.getCreatorId());
            if (creator != null && !creator.getUniqueId().equals(player.getUniqueId())) {
                creator.sendMessage("Dein Spieler-Job " + nameFor(job) + " wurde abgeschlossen.");
            }
        } else {
            player.sendMessage("Fortschritt aktualisiert.");
        }
        save();
    }

    private void deliverFromInventoryAndStorage(Player player, PlayerJobProfile profile, PlayerJob job, boolean allowStorage) {
        int deliveredAny = deliverMaterials(player, job, allowStorage);
        if (deliveredAny == 0) {
            player.sendMessage("Keine passenden Items fuer diesen Job gefunden.");
            return;
        }
        if (isCompleted(job)) {
            economyService.deposit(player.getUniqueId(), rewardFor(job));
            profile.getActiveJobs().remove(job);
            clearPinned(job.getInstanceId());
            JobDefinition definition = getDefinition(job.getDefinitionId());
            profile.getCooldowns().put(definition.id(), System.currentTimeMillis() + Duration.ofDays(1).toMillis());
            ensureJobs(player.getUniqueId());
            player.sendMessage("Job abgeschlossen. " + CurrencyFormatter.shortAmount(rewardFor(job)) + " erhalten.");
        } else {
            player.sendMessage("Fortschritt aktualisiert.");
        }
        save();
    }

    private int deliverMaterials(Player player, PlayerJob job, boolean allowStorage) {
        int deliveredAny = 0;
        for (JobDefinition.JobRequirement requirement : requirementsFor(job)) {
            int delivered = job.getDelivered().getOrDefault(requirement.material().name(), 0);
            int missing = requirement.amount() - delivered;
            if (missing <= 0) {
                continue;
            }
            if (allowStorage) {
                int fromStorage = removeStored(job, requirement.material(), missing);
                if (fromStorage > 0) {
                    delivered += fromStorage;
                    deliveredAny += fromStorage;
                    job.getDelivered().put(requirement.material().name(), delivered);
                    missing -= fromStorage;
                }
            }
            if (missing > 0) {
                int removed = removeMaterial(player.getInventory().getContents(), requirement.material(), missing);
                if (removed > 0) {
                    job.getDelivered().put(requirement.material().name(), delivered + removed);
                    deliveredAny += removed;
                }
            }
        }
        return deliveredAny;
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
            player.sendMessage("Dieses Item wird fuer den Job nicht mehr benoetigt.");
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
        JobDefinition.JobRequirement req = requirementsFor(job).stream()
                .filter(entry -> entry.material() == material)
                .findFirst()
                .orElse(null);
        if (req == null) {
            return 0;
        }
        int delivered = job.getDelivered().getOrDefault(material.name(), 0);
        int stored = job.isCustom() ? 0 : countStored(job, material);
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

    private boolean isCompleted(PlayerJob job) {
        return requirementsFor(job).stream()
                .allMatch(req -> job.getDelivered().getOrDefault(req.material().name(), 0) >= req.amount());
    }

    private void returnStoredItems(UUID playerId, PlayerJob job) {
        for (ItemStack item : job.getStoredItems()) {
            claimStorage.addClaim(playerId, item, "Job-Kiste Rueckgabe", 0, "Job " + job.getInstanceId() + " ist abgelaufen");
        }
        job.getStoredItems().clear();
    }

    private PlayerJobProfile getProfile(UUID playerId) {
        return profiles.computeIfAbsent(playerId, ignored -> new PlayerJobProfile());
    }

    private PlayerJob findJob(UUID playerId, String instanceId) {
        PlayerJob personal = findPersonalJob(playerId, instanceId);
        if (personal != null) {
            return personal;
        }
        return publicJobs.stream()
                .filter(candidate -> candidate.getInstanceId().equalsIgnoreCase(instanceId))
                .findFirst()
                .orElse(null);
    }

    private PlayerJob findPersonalJob(UUID playerId, String instanceId) {
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
                    .filter(definition -> profile.getActiveJobs().stream().noneMatch(job -> definition.id().equals(job.getDefinitionId())))
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

    private Material iconFor(PlayerJob job) {
        return requirementsFor(job).get(0).material();
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
        list.add(new JobDefinition("sweet_stock", "Suesser Vorrat", List.of(
                new JobDefinition.JobRequirement(Material.HONEY_BOTTLE, 24),
                new JobDefinition.JobRequirement(Material.SUGAR_CANE, 192)
        ), 20, 240, 1440));
        return list;
    }

    private List<PlayerJob> visibleJobs(UUID playerId) {
        List<PlayerJob> jobs = new ArrayList<>(getProfile(playerId).getActiveJobs());
        jobs.addAll(publicJobs);
        return jobs;
    }

    private void clearPinned(String instanceId) {
        for (PlayerJobProfile profile : profiles.values()) {
            if (instanceId.equals(profile.getPinnedJobInstanceId())) {
                profile.setPinnedJobInstanceId(null);
            }
        }
    }

    private void saveJob(YamlConfiguration yaml, String path, PlayerJob job) {
        yaml.set(path + ".instanceId", job.getInstanceId());
        yaml.set(path + ".definitionId", job.getDefinitionId());
        yaml.set(path + ".createdAt", job.getCreatedAt());
        yaml.set(path + ".expiresAt", job.getExpiresAt());
        yaml.set(path + ".creatorId", job.getCreatorId() == null ? null : job.getCreatorId().toString());
        yaml.set(path + ".creatorName", job.getCreatorName());
        yaml.set(path + ".customName", job.getCustomName());
        yaml.set(path + ".rewardOverride", job.getRewardOverride());
        yaml.set(path + ".delivered", job.getDelivered());
        yaml.set(path + ".storedItems", job.getStoredItems());
        for (int i = 0; i < job.getCustomRequirements().size(); i++) {
            JobDefinition.JobRequirement requirement = job.getCustomRequirements().get(i);
            yaml.set(path + ".customRequirements." + i + ".material", requirement.material().name());
            yaml.set(path + ".customRequirements." + i + ".amount", requirement.amount());
        }
    }

    private PlayerJob loadJob(YamlConfiguration yaml, String base) {
        String instanceId = yaml.getString(base + ".instanceId");
        if (instanceId == null) {
            return null;
        }
        String creatorIdString = yaml.getString(base + ".creatorId");
        PlayerJob job = new PlayerJob(
                instanceId,
                yaml.getString(base + ".definitionId"),
                yaml.getLong(base + ".createdAt"),
                yaml.getLong(base + ".expiresAt"),
                creatorIdString == null ? null : UUID.fromString(creatorIdString),
                yaml.getString(base + ".creatorName"),
                yaml.getString(base + ".customName"),
                yaml.getDouble(base + ".rewardOverride")
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
        ConfigurationSection customRequirements = yaml.getConfigurationSection(base + ".customRequirements");
        if (customRequirements != null) {
            for (String key : customRequirements.getKeys(false)) {
                String materialName = yaml.getString(base + ".customRequirements." + key + ".material");
                Material material = materialName == null ? null : Material.matchMaterial(materialName);
                if (material == null) {
                    continue;
                }
                job.getCustomRequirements().add(new JobDefinition.JobRequirement(
                        material,
                        yaml.getInt(base + ".customRequirements." + key + ".amount", 1)
                ));
            }
        }
        return job;
    }

    private String nextCustomInstanceId() {
        String instanceId;
        do {
            instanceId = "JOB" + (1000 + ThreadLocalRandom.current().nextInt(9000));
        } while (containsPublicJobInstance(instanceId));
        return instanceId;
    }

    private boolean containsPublicJobInstance(String instanceId) {
        for (PlayerJob job : publicJobs) {
            if (job.getInstanceId().equals(instanceId)) {
                return true;
            }
        }
        return false;
    }

    private int indexOfCreateSlot(int rawSlot) {
        for (int i = 0; i < JOB_CREATE_ITEM_SLOTS.length; i++) {
            if (JOB_CREATE_ITEM_SLOTS[i] == rawSlot) {
                return i;
            }
        }
        return -1;
    }

    private ItemStack stepButton(String text) {
        return GuiItems.button(Material.GOLD_NUGGET, "&6" + text, List.of("&7Klick zum Anpassen"));
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
