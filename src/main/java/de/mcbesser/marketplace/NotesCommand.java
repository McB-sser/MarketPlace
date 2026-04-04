package de.mcbesser.marketplace;

import de.mcbesser.marketplace.notes.NoteManager;
import de.mcbesser.marketplace.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class NotesCommand implements CommandExecutor {

    private final NoteManager noteManager;

    public NotesCommand(NoteManager noteManager) {
        this.noteManager = noteManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.send(sender, "Nur Spieler k\u00f6nnen diesen Befehl nutzen.");
            return true;
        }
        noteManager.resumeNotes(player);
        return true;
    }
}
