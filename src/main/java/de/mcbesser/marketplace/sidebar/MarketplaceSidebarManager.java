package de.mcbesser.marketplace.sidebar;

import de.mcbesser.marketplace.EconomyService;
import de.mcbesser.marketplace.MarketplacePlugin;
import de.mcbesser.marketplace.HandelsblattItem;
import de.mcbesser.marketplace.auction.AuctionManager;
import de.mcbesser.marketplace.jobs.JobDefinition;
import de.mcbesser.marketplace.jobs.JobManager;
import de.mcbesser.marketplace.jobs.PlayerJob;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

public class MarketplaceSidebarManager {
    private static final String OBJECTIVE_NAME = "marketplace";

    private final MarketplacePlugin plugin;
    private final EconomyService economyService;
    private final JobManager jobManager;
    private final AuctionManager auctionManager;

    public MarketplaceSidebarManager(MarketplacePlugin plugin, EconomyService economyService, JobManager jobManager, AuctionManager auctionManager) {
        this.plugin = plugin;
        this.economyService = economyService;
        this.jobManager = jobManager;
        this.auctionManager = auctionManager;
    }

    public void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refresh(player);
        }
    }

    public void refresh(Player player) {
        if (!isHoldingHandelsblatt(player)) {
            if (isShowingOwnBoard(player)) {
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
            return;
        }
        jobManager.autoStorePinnedItems(player);
        updateSidebar(player);
    }

    private void updateSidebar(Player player) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective(OBJECTIVE_NAME, Criteria.DUMMY, "\u00A76Marketplace");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        List<String> lines = new ArrayList<>();
        lines.add("\u00A7eKontostand");
        lines.add("\u00A76" + formatCt(player) + " CT");
        lines.add("\u00A70");
        lines.add("\u00A77Auktion");
        lines.addAll(auctionManager.sidebarLines());

        PlayerJob pinned = jobManager.getPinnedJob(player.getUniqueId());
        if (pinned != null) {
            JobDefinition definition = jobManager.getDefinitionFor(pinned);
            lines.add("\u00A70");
            lines.add("\u00A7eJob: " + cut(definition.name(), 24));
            for (JobDefinition.JobRequirement requirement : definition.requirements()) {
                String name = cut(jobManager.displayName(requirement.material()), 18);
                int progress = Math.min(requirement.amount(), jobManager.progressFor(player, pinned, requirement.material()));
                lines.add("\u00A7f" + name + " " + progress + "/" + requirement.amount());
            }
        }

        int score = lines.size();
        int unique = 0;
        for (String line : lines) {
            objective.getScore(cut(line, 38) + "\u00A7" + Integer.toHexString(unique++)).setScore(score--);
        }
        player.setScoreboard(scoreboard);
    }

    private boolean isHoldingHandelsblatt(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        return HandelsblattItem.isHandelsblatt(plugin, mainHand);
    }

    private boolean isShowingOwnBoard(Player player) {
        Scoreboard scoreboard = player.getScoreboard();
        return scoreboard != null && scoreboard.getObjective(OBJECTIVE_NAME) != null;
    }

    private String cut(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }

    private String formatCt(Player player) {
        return Integer.toString((int) economyService.getBalance(player.getUniqueId()));
    }
}


