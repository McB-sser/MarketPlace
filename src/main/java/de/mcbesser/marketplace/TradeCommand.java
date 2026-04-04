package de.mcbesser.marketplace;

import de.mcbesser.marketplace.trade.TradeManager;
import de.mcbesser.marketplace.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TradeCommand implements CommandExecutor {

    private final TradeManager tradeManager;

    public TradeCommand(TradeManager tradeManager) {
        this.tradeManager = tradeManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.send(sender, "Nur Spieler k\u00f6nnen diesen Befehl nutzen.");
            return true;
        }
        if (tradeManager.hasActiveSession(player.getUniqueId())) {
            tradeManager.openTradeView(player);
        } else {
            tradeManager.openPlayerList(player);
        }
        return true;
    }
}


