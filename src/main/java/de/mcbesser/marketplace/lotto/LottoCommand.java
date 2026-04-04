package de.mcbesser.marketplace.lotto;

import de.mcbesser.marketplace.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class LottoCommand implements CommandExecutor {

    private final LottoManager lottoManager;

    public LottoCommand(LottoManager lottoManager) {
        this.lottoManager = lottoManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.send(sender, "Nur Spieler k\u00f6nnen diesen Befehl nutzen.");
            return true;
        }
        lottoManager.openMain(player);
        return true;
    }
}


