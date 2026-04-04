package de.mcbesser.marketplace.mail;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.inventory.ItemStack;

public class MailEntry {

    private final int id;
    private final UUID senderId;
    private final String senderName;
    private final String message;
    private final double coins;
    private final long createdAt;
    private final List<ItemStack> items = new ArrayList<>();

    public MailEntry(int id, UUID senderId, String senderName, String message, double coins, long createdAt) {
        this.id = id;
        this.senderId = senderId;
        this.senderName = senderName;
        this.message = message;
        this.coins = coins;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public UUID getSenderId() {
        return senderId;
    }

    public String getSenderName() {
        return senderName;
    }

    public String getMessage() {
        return message;
    }

    public double getCoins() {
        return coins;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public List<ItemStack> getItems() {
        return items;
    }
}
