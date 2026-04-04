package de.mcbesser.marketplace;

import de.mcbesser.marketplace.jobs.JobManager;
import de.mcbesser.marketplace.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class JobsCommand implements CommandExecutor {

    private final JobManager jobManager;

    public JobsCommand(JobManager jobManager) {
        this.jobManager = jobManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.send(sender, "Nur Spieler k\u00f6nnen diesen Befehl nutzen.");
            return true;
        }
        jobManager.openJobs(player);
        return true;
    }
}


