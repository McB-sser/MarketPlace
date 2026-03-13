package de.mcbesser.marketplace;

import de.mcbesser.marketplace.market.MarketManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MarketCommand implements CommandExecutor {

    private final MarketManager marketManager;

    public MarketCommand(MarketManager marketManager) {
        this.marketManager = marketManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur Spieler k\u00f6nnen diesen Befehl nutzen.");
            return true;
        }
        marketManager.openMain(player);
        return true;
    }
}


