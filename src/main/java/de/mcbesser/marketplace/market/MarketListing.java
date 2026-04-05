package de.mcbesser.marketplace.market;

import java.util.UUID;
import org.bukkit.inventory.ItemStack;

public class MarketListing {

    private final int id;
    private final UUID sellerId;
    private final ItemStack prototype;
    private final double unitPrice;
    private final long createdAt;
    private final long expiresAt;
    private int remainingAmount;

    public MarketListing(int id, UUID sellerId, ItemStack prototype, double unitPrice, int remainingAmount, long createdAt, long expiresAt) {
        this.id = id;
        this.sellerId = sellerId;
        this.prototype = prototype;
        this.unitPrice = unitPrice;
        this.remainingAmount = remainingAmount;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public int getId() {
        return id;
    }

    public UUID getSellerId() {
        return sellerId;
    }

    public ItemStack getPrototype() {
        return prototype;
    }

    public ItemStack getItem() {
        return createDisplayItem();
    }

    public double getUnitPrice() {
        return unitPrice;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public int getRemainingAmount() {
        return remainingAmount;
    }

    public int getDisplayAmount() {
        return Math.min(remainingAmount, Math.max(1, prototype.getMaxStackSize()));
    }

    public double getDisplayPrice() {
        return unitPrice * getDisplayAmount();
    }

    public double getPrice() {
        return getDisplayPrice();
    }

    public ItemStack createDisplayItem() {
        ItemStack item = prototype.clone();
        item.setAmount(getDisplayAmount());
        return item;
    }

    public ItemStack removeDisplayAmount() {
        int amount = getDisplayAmount();
        remainingAmount -= amount;
        ItemStack item = prototype.clone();
        item.setAmount(amount);
        return item;
    }

    public boolean isEmpty() {
        return remainingAmount <= 0;
    }
}
