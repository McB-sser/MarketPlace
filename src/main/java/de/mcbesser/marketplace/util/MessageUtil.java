package de.mcbesser.marketplace.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class MessageUtil {

    private static final String PREFIX_TEXT = "[MarketPlace] ";

    private MessageUtil() {
    }

    public static void send(CommandSender sender, String message) {
        sender.sendMessage(prefix().append(Component.text(message, NamedTextColor.WHITE)));
    }

    public static void send(Player player, String message) {
        player.sendMessage(prefix().append(Component.text(message, NamedTextColor.WHITE)));
    }

    public static void sendAction(Player player, String message, String label, String command) {
        player.sendMessage(prefix()
                .append(Component.text(message + " ", NamedTextColor.WHITE))
                .append(actionButton(label, command)));
    }

    public static void sendActions(Player player, String message, ActionButton... buttons) {
        Component line = prefix().append(Component.text(message, NamedTextColor.WHITE));
        for (ActionButton button : buttons) {
            line = line.append(Component.text(" ", NamedTextColor.DARK_GRAY)).append(actionButton(button.label(), button.command()));
        }
        player.sendMessage(line);
    }

    public static void broadcast(org.bukkit.Server server, String message) {
        server.broadcast(prefix().append(Component.text(message, NamedTextColor.WHITE)));
    }

    public static ActionButton action(String label, String command) {
        return new ActionButton(label, command);
    }

    private static Component prefix() {
        return Component.text(PREFIX_TEXT, NamedTextColor.GOLD);
    }

    private static Component actionButton(String label, String command) {
        return Component.text("[" + label + "]", NamedTextColor.YELLOW)
                .clickEvent(ClickEvent.runCommand("/" + command))
                .hoverEvent(HoverEvent.showText(Component.text("/" + command, NamedTextColor.GOLD)));
    }

    public record ActionButton(String label, String command) {
    }
}
