package de.mcbesser.marketplace.mail;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.inventory.ItemStack;

public class MailDraft {

    private final UUID recipientId;
    private final String recipientName;
    private final List<ItemStack> items = new ArrayList<>();
    private double coins;
    private String message = "";
    private boolean awaitingMessage;

    public MailDraft(UUID recipientId, String recipientName) {
        this.recipientId = recipientId;
        this.recipientName = recipientName;
    }

    public UUID getRecipientId() {
        return recipientId;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public List<ItemStack> getItems() {
        return items;
    }

    public double getCoins() {
        return coins;
    }

    public void setCoins(double coins) {
        this.coins = coins;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isAwaitingMessage() {
        return awaitingMessage;
    }

    public void setAwaitingMessage(boolean awaitingMessage) {
        this.awaitingMessage = awaitingMessage;
    }
}
