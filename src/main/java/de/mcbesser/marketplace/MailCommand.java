package de.mcbesser.marketplace;

import de.mcbesser.marketplace.mail.MailManager;
import de.mcbesser.marketplace.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MailCommand implements CommandExecutor {

    private final MailManager mailManager;

    public MailCommand(MailManager mailManager) {
        this.mailManager = mailManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.send(sender, "Nur Spieler k\u00f6nnen diesen Befehl nutzen.");
            return true;
        }
        mailManager.resumeDraft(player);
        return true;
    }
}
