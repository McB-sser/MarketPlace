package de.mcbesser.marketplace;

import de.mcbesser.marketplace.auction.AuctionManager;
import de.mcbesser.marketplace.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AuctionCommand implements CommandExecutor {

    private final AuctionManager auctionManager;

    public AuctionCommand(AuctionManager auctionManager) {
        this.auctionManager = auctionManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.send(sender, "Nur Spieler k\u00f6nnen diesen Befehl nutzen.");
            return true;
        }
        auctionManager.openMain(player);
        return true;
    }
}


