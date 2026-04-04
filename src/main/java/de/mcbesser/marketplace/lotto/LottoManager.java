package de.mcbesser.marketplace.lotto;

import de.mcbesser.marketplace.EconomyService;
import de.mcbesser.marketplace.MarketplacePlugin;
import de.mcbesser.marketplace.gui.GuiItems;
import de.mcbesser.marketplace.gui.MenuHolder;
import de.mcbesser.marketplace.gui.MenuType;
import de.mcbesser.marketplace.market.MarketListing;
import de.mcbesser.marketplace.market.MarketManager;
import de.mcbesser.marketplace.pricing.PriceGuideManager;
import de.mcbesser.marketplace.storage.ClaimStorage;
import de.mcbesser.marketplace.util.CurrencyFormatter;
import de.mcbesser.marketplace.util.GermanItemNames;
import de.mcbesser.marketplace.util.MessageUtil;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class LottoManager {

    private final MarketplacePlugin plugin;
    private final EconomyService economyService;
    private final ClaimStorage claimStorage;
    private final MarketManager marketManager;
    private final PriceGuideManager priceGuideManager;
    private final File file;
    private LottoRound currentRound;

    public LottoManager(MarketplacePlugin plugin, EconomyService economyService, ClaimStorage claimStorage,
                        MarketManager marketManager, PriceGuideManager priceGuideManager) throws IOException {
        this.plugin = plugin;
        this.economyService = economyService;
        this.claimStorage = claimStorage;
        this.marketManager = marketManager;
        this.priceGuideManager = priceGuideManager;
        this.file = new File(plugin.getDataFolder(), "lotto.yml");
        load();
    }

    public void openMain(Player player) {
        ensureRound();
        Inventory inventory = Bukkit.createInventory(new MenuHolder(MenuType.LOTTO_MAIN), 27, "Lotto");
        inventory.setItem(13, decoratePrize());
        inventory.setItem(10, GuiItems.button(Material.PAPER, "&a1 Ticket",
                List.of("&7Preis: " + CurrencyFormatter.shortAmount(currentRound.getTicketPrice()))));
        inventory.setItem(11, GuiItems.button(Material.MAP, "&a5 Tickets",
                List.of("&7Preis: " + CurrencyFormatter.shortAmount(currentRound.getTicketPrice() * 5))));
        inventory.setItem(15, GuiItems.button(Material.BOOK, "&a10 Tickets",
                List.of("&7Preis: " + CurrencyFormatter.shortAmount(currentRound.getTicketPrice() * 10))));
        inventory.setItem(18, GuiItems.button(Material.COMPASS, "&aMarktplatz", List.of("&7Zur\u00fcck zum Hauptmen\u00fc")));
        inventory.setItem(22, GuiItems.button(Material.CLOCK, "&eZiehung in " + remainingText(),
                List.of("&7Tickets: " + currentRound.getTotalTickets(),
                        "&7Teilnehmer: " + currentRound.getParticipantCount(),
                        "&7Deine Tickets: " + currentRound.getTickets().getOrDefault(player.getUniqueId(), 0),
                        "&7Mindestens 2 Teilnehmer n\u00f6tig")));
        inventory.setItem(24, GuiItems.button(Material.BARREL, "&eAbholfach", List.of("&7Gewinne und R\u00fcckgaben")));
        player.openInventory(inventory);
    }

    public void handleClick(Player player, int rawSlot) {
        ensureRound();
        switch (rawSlot) {
            case 10 -> buyTickets(player, 1);
            case 11 -> buyTickets(player, 5);
            case 15 -> buyTickets(player, 10);
            case 18 -> {
                player.performCommand("marketplace");
                return;
            }
            case 24 -> {
                claimStorage.openClaims(player, 0, ClaimStorage.CONTEXT_LOTTO);
                return;
            }
            default -> {
                return;
            }
        }
        openMain(player);
    }

    public void tick() {
        ensureRound();
        if (currentRound != null && currentRound.getEndsAt() <= System.currentTimeMillis()) {
            finishRound();
            ensureRound();
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        if (currentRound != null) {
            yaml.set("round.sourceListingId", currentRound.getSourceListingId());
            yaml.set("round.sellerId", currentRound.getSellerId() == null ? null : currentRound.getSellerId().toString());
            yaml.set("round.item", currentRound.getItem());
            yaml.set("round.itemPrice", currentRound.getItemPrice());
            yaml.set("round.ticketPrice", currentRound.getTicketPrice());
            yaml.set("round.endsAt", currentRound.getEndsAt());
            for (Map.Entry<UUID, Integer> ticket : currentRound.getTickets().entrySet()) {
                yaml.set("round.tickets." + ticket.getKey(), ticket.getValue());
            }
        }
        try {
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Konnte lotto.yml nicht speichern: " + exception.getMessage());
        }
    }

    private void load() throws IOException {
        if (!file.exists() && !file.createNewFile()) {
            throw new IOException("lotto.yml konnte nicht erstellt werden.");
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        if (!yaml.contains("round.ticketPrice")) {
            return;
        }
        ItemStack item = yaml.getItemStack("round.item");
        String sellerId = yaml.getString("round.sellerId");
        currentRound = new LottoRound(
                yaml.getInt("round.sourceListingId", -1),
                sellerId == null ? null : UUID.fromString(sellerId),
                item,
                yaml.getDouble("round.itemPrice"),
                yaml.getDouble("round.ticketPrice"),
                yaml.getLong("round.endsAt")
        );
        if (yaml.getConfigurationSection("round.tickets") != null) {
            for (String key : yaml.getConfigurationSection("round.tickets").getKeys(false)) {
                currentRound.getTickets().put(UUID.fromString(key), yaml.getInt("round.tickets." + key));
            }
        }
    }

    private void ensureRound() {
        if (currentRound != null) {
            return;
        }
        MarketListing listing = pickEligibleListing();
        if (listing != null) {
            marketManager.removeListing(listing.getId());
            double ticketPrice = Math.max(1, Math.floor(listing.getPrice() / 10D));
            currentRound = new LottoRound(listing.getId(), listing.getSellerId(), listing.getItem().clone(),
                    listing.getPrice(), ticketPrice, nextDrawTimestamp());
            MessageUtil.broadcast(plugin.getServer(), "Lotto: Tageslotto gestartet: " + readableName(listing.getItem())
                    + " | Ticket: " + CurrencyFormatter.shortAmount(ticketPrice) + ".");
        } else {
            double basePot = plugin.getConfig().getDouble("lotto.base-pot", 250);
            double ticketPrice = plugin.getConfig().getDouble("lotto.ticket-price", 25);
            currentRound = new LottoRound(-1, null, null, basePot, ticketPrice, nextDrawTimestamp());
            MessageUtil.broadcast(plugin.getServer(), "Lotto: Tageslotto gestartet mit Basis-Pot von " + CurrencyFormatter.shortAmount(basePot)
                    + ". Keine g\u00fcltigen Item-Gewinne verf\u00fcgbar.");
        }
        save();
    }

    private MarketListing pickEligibleListing() {
        List<MarketListing> snapshot = marketManager.getListingsSnapshot();
        List<MarketListing> eligible = new ArrayList<>();
        for (MarketListing listing : snapshot) {
            long duplicates = snapshot.stream()
                    .filter(other -> other.getItem().isSimilar(listing.getItem()))
                    .count();
            if (duplicates < 2) {
                continue;
            }
            OptionalDouble reference = priceGuideManager.getReferencePrice(listing.getItem());
            if (reference.isPresent()) {
                double max = Math.ceil(reference.getAsDouble() * 1.1D);
                if (listing.getPrice() > max) {
                    continue;
                }
            }
            eligible.add(listing);
        }
        return eligible.stream()
                .min(java.util.Comparator.comparingDouble(MarketListing::getPrice))
                .orElse(null);
    }

    private void buyTickets(Player player, int amount) {
        double totalPrice = currentRound.getTicketPrice() * amount;
        if (!economyService.withdraw(player.getUniqueId(), totalPrice)) {
            MessageUtil.send(player, "Nicht genug CraftTaler f\u00fcr Lotto-Tickets.");
            return;
        }
        currentRound.getTickets().merge(player.getUniqueId(), amount, Integer::sum);
        MessageUtil.send(player, "Du hast " + amount + " Lotto-Tickets gekauft.");
        save();
    }

    private void finishRound() {
        LottoRound round = currentRound;
        currentRound = null;
        if (round.getParticipantCount() < 2) {
            refundTickets(round);
            if (round.hasItemPrize() && round.getSellerId() != null) {
                claimStorage.addClaim(round.getSellerId(), round.getItem(), "Lotto R\u00fcckgabe", round.getItemPrice(),
                        "Zu wenige Teilnehmer f\u00fcr die Tagesziehung");
            }
            MessageUtil.broadcast(plugin.getServer(), "Lotto: Ziehung ausgefallen. Es werden mindestens 2 Teilnehmer ben\u00f6tigt.");
            save();
            return;
        }

        UUID winnerId = pickWinner(round);
        int ticketPot = (int) (round.getTotalTickets() * round.getTicketPrice());

        if (round.hasItemPrize() && round.getSellerId() != null) {
            economyService.deposit(round.getSellerId(), ticketPot);
            Player winner = Bukkit.getPlayer(winnerId);
            if (winner == null || !winner.isOnline() || !winner.getInventory().addItem(round.getItem()).isEmpty()) {
                claimStorage.addClaim(winnerId, round.getItem(), "Lotto Gewinn", round.getItemPrice(), "Gewonnen im Lotto");
            }
            String winnerName = winner == null ? plugin.getServer().getOfflinePlayer(winnerId).getName() : winner.getName();
            MessageUtil.broadcast(plugin.getServer(), "Lotto: " + winnerName + " gewinnt " + readableName(round.getItem())
                    + ". Verk\u00e4ufer erh\u00e4lt " + CurrencyFormatter.shortAmount(ticketPot) + ".");
        } else {
            int payout = (int) (round.getItemPrice() + ticketPot);
            economyService.deposit(winnerId, payout);
            String winnerName = plugin.getServer().getOfflinePlayer(winnerId).getName();
            MessageUtil.broadcast(plugin.getServer(), "Lotto: " + winnerName + " gewinnt den Basis-Pot von " + CurrencyFormatter.shortAmount(payout) + ".");
        }
        save();
    }

    private void refundTickets(LottoRound round) {
        for (Map.Entry<UUID, Integer> entry : round.getTickets().entrySet()) {
            economyService.deposit(entry.getKey(), entry.getValue() * round.getTicketPrice());
        }
    }

    private UUID pickWinner(LottoRound round) {
        int roll = ThreadLocalRandom.current().nextInt(round.getTotalTickets());
        int cursor = 0;
        for (Map.Entry<UUID, Integer> entry : round.getTickets().entrySet()) {
            cursor += entry.getValue();
            if (roll < cursor) {
                return entry.getKey();
            }
        }
        return round.getTickets().keySet().iterator().next();
    }

    private ItemStack decoratePrize() {
        if (!currentRound.hasItemPrize()) {
            return GuiItems.button(Material.SUNFLOWER, "&6Basis-Pot",
                    List.of("&7Gewinn: " + CurrencyFormatter.shortAmount(currentRound.getItemPrice()),
                            "&7Ticketpreis: " + CurrencyFormatter.shortAmount(currentRound.getTicketPrice()),
                            "&7Ziehung 1x t\u00e4glich"));
        }
        ItemStack display = currentRound.getItem().clone();
        ItemMeta meta = display.getItemMeta();
        List<String> lore = meta != null && meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add(" ");
        lore.add("\u00A77Richtwert: \u00A76" + priceGuideManager.allowedRangeText(currentRound.getItem()));
        lore.add("\u00A77Shoppreis: \u00A76" + CurrencyFormatter.shortAmount(currentRound.getItemPrice()));
        lore.add("\u00A77Ticketpreis: \u00A7e" + CurrencyFormatter.shortAmount(currentRound.getTicketPrice()));
        lore.add("\u00A77Ziehung: \u00A7f1x pro Tag");
        lore.add("\u00A77Restzeit: \u00A7f" + remainingText());
        if (meta != null) {
            meta.setLore(lore);
            display.setItemMeta(meta);
        }
        return display;
    }

    private String remainingText() {
        long minutes = Math.max(0, (currentRound.getEndsAt() - System.currentTimeMillis()) / 60000L);
        long hours = minutes / 60;
        long rest = minutes % 60;
        return hours + "h " + rest + "m";
    }

    private long nextDrawTimestamp() {
        int drawHour = plugin.getConfig().getInt("lotto.draw-hour", 18);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime target = now.withHour(drawHour).withMinute(0).withSecond(0).withNano(0);
        if (!target.isAfter(now)) {
            target = target.plusDays(1);
        }
        return target.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private String readableName(ItemStack item) {
        if (item == null) {
            return "Basis-Pot";
        }
        return GermanItemNames.of(item);
    }
}


