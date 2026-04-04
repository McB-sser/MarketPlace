package de.mcbesser.marketplace;

import de.mcbesser.marketplace.auction.AuctionManager;
import de.mcbesser.marketplace.gui.MenuHolder;
import de.mcbesser.marketplace.gui.MenuType;
import de.mcbesser.marketplace.jobs.JobManager;
import de.mcbesser.marketplace.lotto.LottoManager;
import de.mcbesser.marketplace.mail.MailManager;
import de.mcbesser.marketplace.market.MarketManager;
import de.mcbesser.marketplace.sidebar.MarketplaceSidebarManager;
import de.mcbesser.marketplace.storage.ClaimStorage;
import de.mcbesser.marketplace.trade.TradeManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.event.player.PlayerPickItemEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

public class InteractionListener implements Listener {

    private final MarketplacePlugin plugin;
    private final MarketplaceMenu marketplaceMenu;
    private final JobManager jobManager;
    private final MarketManager marketManager;
    private final LottoManager lottoManager;
    private final MailManager mailManager;
    private final TradeManager tradeManager;
    private final AuctionManager auctionManager;
    private final ClaimStorage claimStorage;
    private final MarketplaceSidebarManager marketplaceSidebarManager;

    public InteractionListener(MarketplacePlugin plugin, MarketplaceMenu marketplaceMenu, JobManager jobManager, MarketManager marketManager,
                               LottoManager lottoManager, MailManager mailManager, TradeManager tradeManager, AuctionManager auctionManager,
                               ClaimStorage claimStorage, MarketplaceSidebarManager marketplaceSidebarManager) {
        this.plugin = plugin;
        this.marketplaceMenu = marketplaceMenu;
        this.jobManager = jobManager;
        this.marketManager = marketManager;
        this.lottoManager = lottoManager;
        this.mailManager = mailManager;
        this.tradeManager = tradeManager;
        this.auctionManager = auctionManager;
        this.claimStorage = claimStorage;
        this.marketplaceSidebarManager = marketplaceSidebarManager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder)) {
            return;
        }
        switch (holder.getType()) {
            case HUB -> {
                event.setCancelled(true);
                marketplaceMenu.handleClick(player, event.getRawSlot());
            }
            case JOBS -> {
                event.setCancelled(true);
                jobManager.handleClick(player, event, holder.getPage());
            }
            case JOB_CREATE -> {
                event.setCancelled(true);
                jobManager.handleCreateClick(player, event);
            }
            case JOB_STORAGE -> {
                event.setCancelled(true);
                jobManager.handleStorageClick(player, event, holder.getContext());
            }
            case MARKET_MAIN -> {
                event.setCancelled(true);
                marketManager.handleMainClick(player, event.getRawSlot());
            }
            case MARKET_LIST -> {
                event.setCancelled(true);
                marketManager.handleListingClick(player, event.getRawSlot(), holder.getPage());
            }
            case MARKET_SELL -> {
                event.setCancelled(true);
                marketManager.handleSellClick(player, event);
            }
            case LOTTO_MAIN -> {
                event.setCancelled(true);
                lottoManager.handleClick(player, event.getRawSlot());
            }
            case CLAIMS -> {
                event.setCancelled(true);
                claimStorage.handleClaimClick(player, event.getRawSlot(), holder.getPage(), holder.getContext());
            }
            case MAIL_PLAYERS -> {
                event.setCancelled(true);
                mailManager.handlePlayerListClick(player, event.getRawSlot());
            }
            case MAIL_COMPOSE -> mailManager.handleComposeClick(player, event);
            case MAIL_INBOX -> {
                event.setCancelled(true);
                mailManager.handleInboxClick(player, event.getRawSlot(), holder.getPage());
            }
            case TRADE_PLAYERS -> {
                event.setCancelled(true);
                tradeManager.handlePlayerListClick(player, event.getRawSlot());
            }
            case TRADE_SESSION -> tradeManager.handleTradeClick(player, event);
            case AUCTION_MAIN -> {
                event.setCancelled(true);
                auctionManager.handleClick(player, event);
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder)) {
            return;
        }
        if (holder.getType() == MenuType.MARKET_SELL) {
            marketManager.handleClose(player);
        }
        if (holder.getType() == MenuType.JOB_STORAGE) {
            jobManager.handleStorageClose(player, holder.getContext(), event.getInventory());
        }
        if (holder.getType() == MenuType.AUCTION_MAIN) {
            auctionManager.handleClose(player);
        }
        if (holder.getType() == MenuType.TRADE_SESSION) {
            tradeManager.handleClose(player);
        }
        if (holder.getType() == MenuType.MAIL_COMPOSE) {
            mailManager.handleComposeClose(player);
        }
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (!auctionManager.handleChatBid(event.getPlayer(), message)) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onBookEdit(PlayerEditBookEvent event) {
        mailManager.handleBookEdit(event);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!HandelsblattItem.isHandelsblatt(plugin, event.getItem())) {
            return;
        }
        event.setCancelled(true);
        marketplaceMenu.open(event.getPlayer());
    }

    @EventHandler
    public void onPickItem(PlayerPickItemEvent event) {
        Player player = event.getPlayer();
        if (!HandelsblattItem.isHandelsblatt(plugin, player.getInventory().getItemInMainHand())) {
            return;
        }
        if (!(player.getTargetEntity(8) instanceof Player target)) {
            return;
        }
        event.setCancelled(true);
        tradeManager.handleHandelsblattQuickTrade(player, target);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        player.discoverRecipe(HandelsblattItem.createRecipe(plugin).getKey());
        marketplaceSidebarManager.refresh(player);
    }

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, () -> marketplaceSidebarManager.refresh(event.getPlayer()));
    }

    @EventHandler
    public void onSwapHandItems(PlayerSwapHandItemsEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, () -> marketplaceSidebarManager.refresh(event.getPlayer()));
    }
}


