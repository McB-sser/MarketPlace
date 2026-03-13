package de.mcbesser.marketplace;

import de.mcbesser.marketplace.auction.AuctionManager;
import de.mcbesser.marketplace.jobs.JobManager;
import de.mcbesser.marketplace.lotto.LottoManager;
import de.mcbesser.marketplace.market.MarketManager;
import de.mcbesser.marketplace.pricing.PriceGuideManager;
import de.mcbesser.marketplace.sidebar.MarketplaceSidebarManager;
import de.mcbesser.marketplace.storage.ClaimStorage;
import de.mcbesser.marketplace.trade.TradeManager;
import java.io.File;
import java.io.IOException;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.java.JavaPlugin;

public class MarketplacePlugin extends JavaPlugin {

    private EconomyService economyService;
    private ClaimStorage claimStorage;
    private PriceGuideManager priceGuideManager;
    private JobManager jobManager;
    private MarketManager marketManager;
    private LottoManager lottoManager;
    private TradeManager tradeManager;
    private AuctionManager auctionManager;
    private MarketplaceMenu marketplaceMenu;
    private MarketplaceSidebarManager marketplaceSidebarManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            File dataFolder = getDataFolder();
            if (!dataFolder.exists() && !dataFolder.mkdirs()) {
                throw new IOException("Datenordner konnte nicht erstellt werden.");
            }
            economyService = new EconomyService(this);
            claimStorage = new ClaimStorage(this);
            priceGuideManager = new PriceGuideManager(this);
            jobManager = new JobManager(this, economyService, claimStorage);
            marketManager = new MarketManager(this, economyService, claimStorage, priceGuideManager);
            lottoManager = new LottoManager(this, economyService, claimStorage, marketManager, priceGuideManager);
            tradeManager = new TradeManager(this, economyService, claimStorage);
            auctionManager = new AuctionManager(this, economyService, claimStorage);
            marketplaceMenu = new MarketplaceMenu(jobManager, marketManager, lottoManager, tradeManager, auctionManager, claimStorage);
            marketplaceSidebarManager = new MarketplaceSidebarManager(this, jobManager, auctionManager);
        } catch (IOException exception) {
            getLogger().severe("Initialisierung fehlgeschlagen: " + exception.getMessage());
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        ShapelessRecipe recipe = HandelsblattItem.createRecipe(this);
        getServer().addRecipe(recipe);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.discoverRecipe(recipe.getKey());
        }
        getServer().getPluginManager().registerEvents(
                new InteractionListener(this, marketplaceMenu, jobManager, marketManager, lottoManager, tradeManager, auctionManager, claimStorage),
                this
        );

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            jobManager.tick();
            marketManager.tick();
            lottoManager.tick();
            tradeManager.tick();
            auctionManager.tick();
        }, 20L, 20L * 60L);

        Bukkit.getScheduler().runTaskTimer(this, marketplaceSidebarManager::tick, 20L, 20L * 5L);
    }

    @Override
    public void onDisable() {
        if (economyService != null) {
            economyService.save();
        }
        if (jobManager != null) {
            jobManager.save();
        }
        if (marketManager != null) {
            marketManager.save();
        }
        if (lottoManager != null) {
            lottoManager.save();
        }
        if (tradeManager != null) {
            tradeManager.save();
        }
        if (auctionManager != null) {
            auctionManager.shutdown();
        }
        if (claimStorage != null) {
            claimStorage.save();
        }
        if (priceGuideManager != null) {
            priceGuideManager.save();
        }
    }
}


