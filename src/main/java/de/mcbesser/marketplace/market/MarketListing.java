package de.mcbesser.marketplace.market;

import java.util.UUID;
import org.bukkit.inventory.ItemStack;

public class MarketListing {

    private final int id;
    private final UUID sellerId;
    private final ItemStack item;
    private final double price;
    private final long createdAt;
    private final long expiresAt;

    public MarketListing(int id, UUID sellerId, ItemStack item, double price, long createdAt, long expiresAt) {
        this.id = id;
        this.sellerId = sellerId;
        this.item = item;
        this.price = price;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public int getId() {
        return id;
    }

    public UUID getSellerId() {
        return sellerId;
    }

    public ItemStack getItem() {
        return item;
    }

    public double getPrice() {
        return price;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getExpiresAt() {
        return expiresAt;
    }
}


