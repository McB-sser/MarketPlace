package de.mcbesser.marketplace;

import de.mcbesser.marketplace.auction.AuctionManager;
import de.mcbesser.marketplace.gui.GuiItems;
import de.mcbesser.marketplace.gui.MenuHolder;
import de.mcbesser.marketplace.gui.MenuType;
import de.mcbesser.marketplace.jobs.JobManager;
import de.mcbesser.marketplace.lotto.LottoManager;
import de.mcbesser.marketplace.mail.MailManager;
import de.mcbesser.marketplace.market.MarketManager;
import de.mcbesser.marketplace.storage.ClaimStorage;
import de.mcbesser.marketplace.trade.TradeManager;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public class MarketplaceMenu {

    private final JobManager jobManager;
    private final MarketManager marketManager;
    private final LottoManager lottoManager;
    private final MailManager mailManager;
    private final TradeManager tradeManager;
    private final AuctionManager auctionManager;
    private final ClaimStorage claimStorage;

    public MarketplaceMenu(JobManager jobManager, MarketManager marketManager, LottoManager lottoManager, MailManager mailManager, TradeManager tradeManager,
                      AuctionManager auctionManager, ClaimStorage claimStorage) {
        this.jobManager = jobManager;
        this.marketManager = marketManager;
        this.lottoManager = lottoManager;
        this.mailManager = mailManager;
        this.tradeManager = tradeManager;
        this.auctionManager = auctionManager;
        this.claimStorage = claimStorage;
    }

    public void open(Player player) {
        Inventory inventory = Bukkit.createInventory(new MenuHolder(MenuType.HUB), 27, "Marketplace");
        inventory.setItem(10, GuiItems.button(Material.HAY_BLOCK, "&aJobs", List.of("&7Farm-Jobs ansehen")));
        inventory.setItem(12, GuiItems.button(Material.CHEST, "&6Markt", List.of("&7Marktangebote und Verkauf")));
        inventory.setItem(14, GuiItems.button(Material.NAME_TAG, "&bDirekthandel", List.of("&7Mit Spielern tauschen")));
        inventory.setItem(16, GuiItems.button(Material.GOLD_BLOCK, "&eAuktion", List.of("&7Live-Auktion im Chat")));
        inventory.setItem(20, GuiItems.button(Material.NETHER_STAR, "&6Lotto", List.of("&7Tickets kaufen und Item gewinnen")));
        inventory.setItem(22, GuiItems.button(Material.BARREL, "&dAbholfach", List.of("&7R\u00fcckgaben und Gewinne")));
        inventory.setItem(24, GuiItems.button(Material.WRITABLE_BOOK, "&eSpieler-Mail", List.of("&7Items, Geld und Nachricht senden")));
        player.openInventory(inventory);
    }

    public void handleClick(Player player, int rawSlot) {
        switch (rawSlot) {
            case 10 -> jobManager.openJobs(player);
            case 12 -> marketManager.openMain(player);
            case 14 -> tradeManager.openPlayerList(player);
            case 16 -> auctionManager.openMain(player);
            case 20 -> lottoManager.openMain(player);
            case 22 -> claimStorage.openClaims(player, 0, ClaimStorage.CONTEXT_HUB);
            case 24 -> mailManager.openPlayerList(player);
            default -> {
            }
        }
    }
}


