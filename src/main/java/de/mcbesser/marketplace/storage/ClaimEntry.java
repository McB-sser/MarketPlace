package de.mcbesser.marketplace.storage;

import org.bukkit.inventory.ItemStack;

public class ClaimEntry {

    private final int id;
    private final ItemStack item;
    private final String source;
    private final double lastPrice;
    private final String details;
    private final long createdAt;

    public ClaimEntry(int id, ItemStack item, String source, double lastPrice, String details, long createdAt) {
        this.id = id;
        this.item = item;
        this.source = source;
        this.lastPrice = lastPrice;
        this.details = details;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public ItemStack getItem() {
        return item;
    }

    public String getSource() {
        return source;
    }

    public double getLastPrice() {
        return lastPrice;
    }

    public String getDetails() {
        return details;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}


